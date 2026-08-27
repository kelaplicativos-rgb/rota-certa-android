package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.TenantStorageScope
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TripStore(context: Context) {
    private val appContext = context.applicationContext
    private val tenantScope = RotaCertaTenantRegistry(appContext).activeScope()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val tripsKey = tenantScope.key(KEY_TRIPS)
    private val bookingsKey = tenantScope.key(KEY_BOOKINGS)
    private val onlineKey = tenantScope.key(KEY_ONLINE)
    private val publicExternalBindingsKey = tenantScope.key(KEY_PUBLIC_EXTERNAL_BINDINGS)
    private val secretStore = TripSecretStore(appContext, tenantScope)
    private val publicAgendaLinkStore = PublicAgendaLinkStore(appContext, tenantScope)
    private val passengerIdentityStore = PassengerIdentityStore(appContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun trips(): List<Trip> = decode<List<Trip>>(prefs.getString(tripsKey, null)).orEmpty()
        .sortedByDescending(Trip::departureAtMillis)

    fun bookings(): List<Booking> = decode<List<Booking>>(prefs.getString(bookingsKey, null)).orEmpty()

    fun bookingsFor(tripId: String): List<Booking> = bookings().filter { it.tripId == tripId }

    fun getTrip(id: String): Trip? = trips().firstOrNull { it.id == id }

    fun publicExternalBindings(): List<PublicExternalTripBinding> =
        decode<List<PublicExternalTripBinding>>(prefs.getString(publicExternalBindingsKey, null)).orEmpty()
            .sortedByDescending(PublicExternalTripBinding::departureAtMillis)

    fun publicExternalBinding(remoteTripId: String): PublicExternalTripBinding? =
        publicExternalBindings().firstOrNull { it.remoteTripId == remoteTripId }

    fun publicExternalBindingFor(entry: TripTimelineEntry): PublicExternalTripBinding? =
        publicExternalBindings().firstOrNull { it.matches(entry) }

    fun savePublicExternalBinding(binding: PublicExternalTripBinding): PublicExternalTripBinding {
        val normalized = binding.copy(updatedAtMillis = System.currentTimeMillis())
        val current = publicExternalBindings().filterNot { it.remoteTripId == normalized.remoteTripId }
        prefs.edit().putString(publicExternalBindingsKey, json.encodeToString(listOf(normalized) + current)).apply()
        return normalized
    }

    fun saveTrip(trip: Trip): Trip {
        val normalized = trip.copy(updatedAtMillis = System.currentTimeMillis())
        val current = trips().filterNot { it.id == normalized.id }
        prefs.edit().putString(tripsKey, json.encodeToString(listOf(normalized) + current)).apply()
        return normalized
    }

    fun deleteTrip(id: String) {
        prefs.edit()
            .putString(tripsKey, json.encodeToString(trips().filterNot { it.id == id }))
            .putString(bookingsKey, json.encodeToString(bookings().filterNot { it.tripId == id }))
            .apply()
    }

    fun clearTimelineLocalData(): Pair<Int, Int> {
        val tripsRemoved = trips().size
        val bookingsRemoved = bookings().size
        prefs.edit()
            .remove(tripsKey)
            .remove(bookingsKey)
            .apply()
        return tripsRemoved to bookingsRemoved
    }

    fun saveBooking(booking: Booking): Booking {
        val all = bookings()
        val existing = all.firstOrNull { it.id == booking.id }
        val withPreservedLocalMetadata = if (
            existing?.localMetadataTouched == true && !booking.localMetadataTouched
        ) {
            booking.copy(
                passengerId = existing.passengerId,
                fareMinorUnits = existing.fareMinorUnits,
                fareCurrencyCode = existing.fareCurrencyCode,
                boardingAddress = existing.boardingAddress,
                dropoffAddress = existing.dropoffAddress,
                localMetadataTouched = true,
            )
        } else {
            booking
        }
        val identity = passengerIdentityStore.ensureLocalBookingProfile(withPreservedLocalMetadata)
        val normalized = withPreservedLocalMetadata.copy(
            passengerId = identity?.id ?: withPreservedLocalMetadata.passengerId,
            updatedAtMillis = System.currentTimeMillis(),
        )
        val current = all.filterNot { it.id == normalized.id }
        prefs.edit().putString(bookingsKey, json.encodeToString(listOf(normalized) + current)).apply()
        refreshTripStatus(normalized.tripId)
        return normalized
    }

    fun deleteBooking(id: String) {
        val booking = bookings().firstOrNull { it.id == id }
        prefs.edit().putString(bookingsKey, json.encodeToString(bookings().filterNot { it.id == id })).apply()
        booking?.let { refreshTripStatus(it.tripId) }
    }

    fun onlineSettings(): TripOnlineSettings {
        val publicSettings = decode<TripOnlineSettings>(prefs.getString(onlineKey, null)) ?: TripOnlineSettings()
        val stablePublicAgendaToken = publicAgendaLinkStore.currentOrMigrate(publicSettings.publicCalendarToken)
        return publicSettings.copy(
            driverToken = secretStore.driverToken(),
            publicCalendarToken = stablePublicAgendaToken,
        )
    }

    fun saveOnlineSettings(settings: TripOnlineSettings) {
        secretStore.saveDriverToken(settings.driverToken)
        val stablePublicAgendaToken = publicAgendaLinkStore.currentOrMigrate(settings.publicCalendarToken)
        val withoutAdministrativeSecret = settings.copy(
            driverToken = "",
            publicCalendarToken = stablePublicAgendaToken,
        )
        prefs.edit().putString(onlineKey, json.encodeToString(withoutAdministrativeSecret)).apply()
    }

    fun replacePublicAgendaLinkAfterConfirmedRotation(
        expectedCurrent: String,
        replacement: String,
    ): Boolean = publicAgendaLinkStore.replaceAfterConfirmedRotation(expectedCurrent, replacement)

    fun clearOnlineCredentials() {
        secretStore.clear()
        val current = decode<TripOnlineSettings>(prefs.getString(onlineKey, null)) ?: TripOnlineSettings()
        prefs.edit().putString(onlineKey, json.encodeToString(current.copy(driverToken = ""))).apply()
    }

    fun nextPublishedTrip(nowMillis: Long = System.currentTimeMillis()): Trip? = trips()
        .asSequence()
        .filter { it.departureAtMillis >= nowMillis }
        .filter { it.status in setOf(TripStatus.PUBLISHED, TripStatus.FULL, TripStatus.STARTING) }
        .minByOrNull(Trip::departureAtMillis)

    private fun refreshTripStatus(tripId: String) {
        val trip = getTrip(tripId) ?: return
        val status = SeatAvailabilityEngine.suggestedStatus(trip, bookingsFor(tripId))
        if (status != trip.status) saveTrip(trip.copy(status = status))
    }

    private inline fun <reified T> decode(value: String?): T? = runCatching {
        if (value.isNullOrBlank()) null else json.decodeFromString<T>(value)
    }.getOrNull()

    companion object {
        private const val PREFS = "rota_certa_trips_stage47"
        private const val KEY_TRIPS = "trips"
        private const val KEY_BOOKINGS = "bookings"
        private const val KEY_ONLINE = "online_settings"
        private const val KEY_PUBLIC_EXTERNAL_BINDINGS = "public_external_bindings_v1"
    }
}

@kotlinx.serialization.Serializable
data class PublicExternalTripBinding(
    val remoteTripId: String,
    val publicToken: String,
    val bookingTripId: String,
    val profileUuid: String = "",
    val blablaTripId: String = "",
    val blablaTripHref: String = "",
    val title: String,
    val departureAtMillis: Long,
    val capacity: Int,
    val stops: List<TripStop>,
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    fun matches(entry: TripTimelineEntry): Boolean {
        val entryProfile = entry.blablaProfileUuid.orEmpty().trim().lowercase()
        val entryTripId = entry.blablaTripId.orEmpty().trim()
        if (profileUuid.isNotBlank() && blablaTripId.isNotBlank() &&
            entryProfile == profileUuid.trim().lowercase() && entryTripId == blablaTripId.trim()
        ) return true

        val leftHref = blablaTripHref.substringBefore("&search_uuid=").trim()
        val rightHref = entry.blablaTripHref.orEmpty().substringBefore("&search_uuid=").trim()
        if (profileUuid.isNotBlank() && leftHref.isNotBlank() &&
            entryProfile == profileUuid.trim().lowercase() && leftHref == rightHref
        ) return true

        return kotlin.math.abs(entry.departureAtMillis - departureAtMillis) <= 45L * 60L * 1000L &&
            normalizeBindingPlace(entry.origin) == normalizeBindingPlace(stops.minByOrNull(TripStop::order)?.name.orEmpty()) &&
            normalizeBindingPlace(entry.destination) == normalizeBindingPlace(stops.maxByOrNull(TripStop::order)?.name.orEmpty())
    }

    fun asTrip(): Trip = Trip(
        id = bookingTripId,
        title = title,
        departureAtMillis = departureAtMillis,
        capacity = capacity,
        status = TripStatus.PUBLISHED,
        stops = stops,
        publicToken = publicToken,
        remoteId = remoteTripId,
        publicBookingEnabled = true,
    )
}

private fun normalizeBindingPlace(value: String): String = java.text.Normalizer
    .normalize(value.substringBefore(',').trim(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

@kotlinx.serialization.Serializable
data class TripOnlineSettings(
    val apiBaseUrl: String = "",
    val publicBaseUrl: String = "",
    val driverToken: String = "",
    val publicCalendarToken: String = "",
    val driverDisplayName: String = "",
    val driverUsername: String = "",
    val driverWhatsapp: String = "",
    val driverPhotoUrl: String = "",
    val driverPublicAbout: String = "",
    val driverPublicRating: String = "",
    val driverPublicReviewCount: Int = 0,
    val driverPublicBadge: String = "",
    val vehicleMakeModel: String = "",
    val vehicleColor: String = "",
    val vehicleAmenities: String = "",
    val driverPreferences: String = "",
    val paymentInstructions: String = "",
    val googleCalendarPublicUrl: String = "",
    val publicProfileMode: PublicDriverProfileMode = PublicDriverProfileMode.MANUAL,
    val selectedPublicProfileAccountId: String = "",
    val publicProfileOverrideFields: Set<String> = emptySet(),
) {
    val configured: Boolean
        get() = apiBaseUrl.startsWith("https://") && driverToken.isNotBlank()

    val publicAgendaUrl: String?
        get() = publicBaseUrl.takeIf { it.startsWith("https://") }?.trimEnd('/')?.let { base ->
            val username = driverUsername.takeIf(DriverIdentityRules::isValidUsername) ?: return@let null
            publicCalendarToken.takeIf { it.length >= 16 }?.let { token -> "$base/?motorista=$username&agenda=$token" }
        }

    val publicCalendarUrl: String?
        get() = publicBaseUrl.takeIf { it.startsWith("https://") }?.trimEnd('/')?.let { base ->
            publicCalendarToken.takeIf { it.length >= 16 }?.let { token ->
                if (DriverIdentityRules.isValidUsername(driverUsername)) "$base/calendar/$driverUsername/$token.ics" else "$base/calendar/$token.ics"
            }
        }

    val googleCalendarMirrorUrl: String?
        get() = googleCalendarPublicUrl.trim().takeIf { it.startsWith("https://") }
}

internal class PublicAgendaLinkStore(
    context: Context,
    tenantScope: TenantStorageScope,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val valueKey = tenantScope.key(KEY_VALUE)
    private val generationKey = tenantScope.key(KEY_GENERATION)

    @Synchronized
    fun currentOrMigrate(legacyValue: String = ""): String {
        val current = prefs.getString(valueKey, "").orEmpty().trim()
        if (current.isNotBlank()) return current
        val candidate = normalize(legacyValue)
        if (candidate.length < MIN_LENGTH) return ""
        prefs.edit().putString(valueKey, candidate).apply()
        return candidate
    }

    @Synchronized
    fun replaceAfterConfirmedRotation(expectedCurrentRaw: String, replacementRaw: String): Boolean {
        val expected = normalize(expectedCurrentRaw)
        val replacement = normalize(replacementRaw)
        if (expected.length < MIN_LENGTH || replacement.length < MIN_LENGTH || replacement == expected) return false
        val current = prefs.getString(valueKey, "").orEmpty().trim()
        if (current != expected) return false
        return prefs.edit()
            .putString(valueKey, replacement)
            .putLong(generationKey, generation() + 1L)
            .commit()
    }

    fun generation(): Long = prefs.getLong(generationKey, 1L).coerceAtLeast(1L)

    private fun normalize(value: String): String =
        value.trim().filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(120)

    companion object {
        private const val PREFS = "rota_certa_public_agenda_link_v1"
        private const val KEY_VALUE = "public_agenda_identifier"
        private const val KEY_GENERATION = "generation"
        private const val MIN_LENGTH = 16
    }
}

private class TripSecretStore(
    context: Context,
    private val tenantScope: TenantStorageScope,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val ciphertextKey = tenantScope.key(KEY_CIPHERTEXT)
    private val ivKey = tenantScope.key(KEY_IV)
    private val keyAlias = tenantScope.keyAlias(KEY_ALIAS_BASE)

    fun saveDriverToken(token: String) {
        val value = token.trim()
        if (value.isBlank()) {
            prefs.edit().remove(ciphertextKey).remove(ivKey).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(ciphertextKey, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun driverToken(): String {
        val ciphertext = prefs.getString(ciphertextKey, null)?.let(::decode) ?: return ""
        val iv = prefs.getString(ivKey, null)?.let(::decode) ?: return ""
        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val key = keyStore.getKey(keyAlias, null) as? SecretKey ?: return@runCatching ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun clear() {
        prefs.edit().remove(ciphertextKey).remove(ivKey).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun decode(value: String): ByteArray? = runCatching {
        Base64.decode(value, Base64.NO_WRAP)
    }.getOrNull()

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_BASE = "rota_certa_stage47_driver_token_aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS = "rota_certa_trip_secrets_stage47"
        private const val KEY_CIPHERTEXT = "driver_token_ciphertext"
        private const val KEY_IV = "driver_token_iv"
    }
}
