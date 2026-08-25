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
    /** Stable external passenger/member IDs explicitly associated with this canonical profile. */
    val externalPassengerIds: Set<String> = emptySet(),
    /** Local driver preference. This flag never blocks by name or phone similarity. */
    val blocked: Boolean = false,
    val blockedReason: String = "",
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
    /** Canonical local PassengerProfile id. Never populated from a BlaBlaCar passenger id. */
    val passengerId: String = "",
    /** Strong external passenger/member id captured from exact booking/network evidence. */
    val externalPassengerId: String = "",
    val externalTripId: String = "",
    val externalProfileUuid: String = "",
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

internal data class PassengerRideHistory(
    val totalRides: Int,
    val ridesByDriverProfile: Map<String, Int>,
)

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

    fun profileByExternalPassengerId(raw: String?): PassengerProfile? {
        val externalId = stableExternalPassengerId(raw) ?: return null
        return profiles().singleOrNull { profile -> externalId in profile.externalPassengerIds }
    }

    fun saveProfile(profile: PassengerProfile): PassengerProfile {
        val now = System.currentTimeMillis()
        val normalized = profile.copy(
            displayName = profile.displayName.trim().take(120),
            whatsapp = profile.whatsapp.trim().take(40),
            externalPassengerIds = profile.externalPassengerIds.mapNotNull(::stableExternalPassengerId).toSet(),
            blockedReason = profile.blockedReason.trim().take(240),
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

    fun linkExternalPassengerId(profileId: String, externalPassengerId: String): PassengerProfile? {
        val externalId = stableExternalPassengerId(externalPassengerId) ?: return null
        val target = profile(profileId) ?: return null
        val alreadyOwned = profileByExternalPassengerId(externalId)
        if (alreadyOwned != null && alreadyOwned.id != target.id) return null
        val saved = saveProfile(target.copy(externalPassengerIds = target.externalPassengerIds + externalId))
        val metadata = externalMetadata()
        var changed = false
        val linked = metadata.map { item ->
            if (item.externalPassengerId == externalId && item.passengerId != saved.id) {
                changed = true
                item.copy(passengerId = saved.id)
            } else {
                item
            }
        }
        if (changed) saveExternalMetadataList(linked)
        return saved
    }

    fun setBlocked(profileId: String, blocked: Boolean, reason: String = ""): PassengerProfile? {
        val target = profile(profileId) ?: return null
        return saveProfile(target.copy(blocked = blocked, blockedReason = if (blocked) reason else ""))
    }

    fun rideHistory(profileId: String): PassengerRideHistory {
        val target = profile(profileId) ?: return PassengerRideHistory(0, emptyMap())
        val externalIds = target.externalPassengerIds
        if (externalIds.isEmpty()) return PassengerRideHistory(0, emptyMap())
        val rides = externalMetadata()
            .filter { it.externalPassengerId in externalIds }
            .distinctBy { metadata ->
                listOf(
                    metadata.externalProfileUuid.ifBlank { "profile?" },
                    metadata.externalTripId.ifBlank { metadata.reservationKey },
                ).joinToString("|")
            }
        return PassengerRideHistory(
            totalRides = rides.size,
            ridesByDriverProfile = rides
                .groupingBy { it.externalProfileUuid.ifBlank { "Perfil não identificado" } }
                .eachCount(),
        )
    }

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

    fun externalMetadataByPassengerId(raw: String?): List<ExternalPassengerMetadata> {
        val externalId = stableExternalPassengerId(raw) ?: return emptyList()
        return externalMetadata().filter { it.externalPassengerId == externalId }
    }

    fun saveExternalMetadata(metadata: ExternalPassengerMetadata): ExternalPassengerMetadata {
        require(metadata.reservationKey.isNotBlank()) { "Referência externa inválida." }
        val latitude = validLatitude(metadata.boardingLatitude)
        val longitude = validLongitude(metadata.boardingLongitude)
        val hasCoordinatePair = latitude != null && longitude != null
        val normalized = metadata.copy(
            passengerId = metadata.passengerId.trim(),
            externalPassengerId = stableExternalPassengerId(metadata.externalPassengerId).orEmpty(),
            externalTripId = stableExternalPassengerId(metadata.externalTripId).orEmpty(),
            externalProfileUuid = metadata.externalProfileUuid.trim().lowercase().take(80),
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
        saveExternalMetadataList(listOf(normalized) + current)
        return normalized
    }

    private fun saveExternalMetadataList(value: List<ExternalPassengerMetadata>) {
        prefs.edit().putString(externalMetadataKey, json.encodeToString(value)).apply()
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

internal fun stableExternalPassengerId(raw: String?): String? = raw
    ?.trim()
    ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,160}")) }

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
