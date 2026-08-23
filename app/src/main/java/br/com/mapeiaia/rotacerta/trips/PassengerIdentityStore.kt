package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Canonical Rota Certa passenger identity. A booking references this id; it is never inferred from a name. */
@Serializable
data class PassengerProfile(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val whatsapp: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

/**
 * Metadata that belongs to an external reservation but must not create another capacity claim.
 * Only stable external reservation references receive one of these records.
 */
@Serializable
data class ExternalPassengerMetadata(
    val reservationKey: String,
    val passengerId: String = "",
    val fareMinorUnits: Long? = null,
    val fareCurrencyCode: String = "",
    val boardingAddress: String = "",
    val dropoffAddress: String = "",
    /** Exact pickup coordinate captured from stable external reservation evidence, never inferred from city/name text. */
    val boardingLatitude: Double? = null,
    val boardingLongitude: Double? = null,
    /** Optional source accuracy when the external evidence exposes it. */
    val boardingAccuracyMeters: Double? = null,
    /** Short provenance marker, for example blablacar_booking_structured_pickup. */
    val boardingLocationSource: String = "",
    /** Local capture time for the coordinate evidence. */
    val boardingLocationCollectedAtMillis: Long? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    val hasBoardingCoordinates: Boolean
        get() = validLatitude(boardingLatitude) != null && validLongitude(boardingLongitude) != null
}

class PassengerIdentityStore(context: Context) {
    private val appContext = context.applicationContext
    private val tenantScope = RotaCertaTenantRegistry(appContext).activeScope()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val profilesKey = tenantScope.key(KEY_PROFILES)
    private val externalMetadataKey = tenantScope.key(KEY_EXTERNAL_METADATA)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun profiles(): List<PassengerProfile> = decode<List<PassengerProfile>>(prefs.getString(profilesKey, null))
        .orEmpty()
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

    fun profile(id: String?): PassengerProfile? {
        val canonical = id?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return profiles().firstOrNull { it.id == canonical }
    }

    fun saveProfile(profile: PassengerProfile): PassengerProfile {
        val now = System.currentTimeMillis()
        val normalized = profile.copy(
            displayName = profile.displayName.trim().take(120),
            whatsapp = profile.whatsapp.trim().take(40),
            updatedAtMillis = now,
        )
        require(normalized.displayName.isNotBlank()) { "Informe o nome do passageiro." }
        val current = profiles().filterNot { it.id == normalized.id }
        prefs.edit().putString(profilesKey, json.encodeToString(listOf(normalized) + current)).apply()
        return normalized
    }

    fun createProfile(name: String, whatsapp: String): PassengerProfile = saveProfile(
        PassengerProfile(
            displayName = name.trim(),
            whatsapp = whatsapp.trim(),
        ),
    )

    /** Exact contact lookup is only a discovery aid. Callers must still require an explicit reuse action. */
    fun exactContactMatches(raw: String): List<PassengerProfile> {
        val key = passengerContactKey(raw)
        if (key.isBlank()) return emptyList()
        return profiles().filter { passengerContactKey(it.whatsapp) == key }
    }

    fun externalMetadata(reservationKey: String?): ExternalPassengerMetadata? {
        val key = reservationKey?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return externalMetadata().firstOrNull { it.reservationKey == key }
    }

    fun saveExternalMetadata(metadata: ExternalPassengerMetadata): ExternalPassengerMetadata {
        require(metadata.reservationKey.isNotBlank()) { "Referência externa inválida." }
        val latitude = validLatitude(metadata.boardingLatitude)
        val longitude = validLongitude(metadata.boardingLongitude)
        val hasCoordinatePair = latitude != null && longitude != null
        val normalized = metadata.copy(
            passengerId = metadata.passengerId.trim(),
            fareCurrencyCode = metadata.fareCurrencyCode.trim().uppercase().take(3),
            boardingAddress = metadata.boardingAddress.trim().take(500),
            dropoffAddress = metadata.dropoffAddress.trim().take(500),
            boardingLatitude = latitude.takeIf { hasCoordinatePair },
            boardingLongitude = longitude.takeIf { hasCoordinatePair },
            boardingAccuracyMeters = metadata.boardingAccuracyMeters
                ?.takeIf { hasCoordinatePair && it.isFinite() && it >= 0.0 && it <= 100_000.0 },
            boardingLocationSource = metadata.boardingLocationSource.trim().take(80).takeIf { hasCoordinatePair }.orEmpty(),
            boardingLocationCollectedAtMillis = metadata.boardingLocationCollectedAtMillis.takeIf { hasCoordinatePair },
            updatedAtMillis = System.currentTimeMillis(),
        )
        val current = externalMetadata().filterNot { it.reservationKey == normalized.reservationKey }
        prefs.edit().putString(externalMetadataKey, json.encodeToString(listOf(normalized) + current)).apply()
        return normalized
    }

    private fun externalMetadata(): List<ExternalPassengerMetadata> =
        decode<List<ExternalPassengerMetadata>>(prefs.getString(externalMetadataKey, null)).orEmpty()

    private inline fun <reified T> decode(raw: String?): T? = raw
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    companion object {
        private const val PREFS = "rota_certa_passenger_identity_v1"
        private const val KEY_PROFILES = "passenger_profiles"
        private const val KEY_EXTERNAL_METADATA = "external_passenger_metadata"
    }
}

internal fun validLatitude(value: Double?): Double? = value?.takeIf { it.isFinite() && it in -90.0..90.0 }

internal fun validLongitude(value: Double?): Double? = value?.takeIf { it.isFinite() && it in -180.0..180.0 }

internal fun passengerContactKey(raw: String?): String {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return ""
    val digits = value.filter(Char::isDigit)
    if (digits.length !in 8..15) return ""
    return if (value.startsWith("+")) "+$digits" else "local:$digits"
}

/** Stable metadata identity only when the collector exposed a stable booking reference. */
internal fun externalPassengerReservationKey(profileUuid: String?, bookingHref: String?): String? {
    val profile = profileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return null
    val href = bookingHref?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$profile\n$href".toByteArray(Charsets.UTF_8))
        .take(16)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "blablacar:$digest"
}
