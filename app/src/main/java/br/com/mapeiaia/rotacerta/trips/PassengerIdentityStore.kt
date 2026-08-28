package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import java.security.MessageDigest
import java.text.Normalizer
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
    /** Stable Rota Certa/public-portal identities explicitly linked to this canonical profile. */
    val onlineIdentityIds: Set<String> = emptySet(),
    /** Public portal access is independent from the local persona-non-grata flag. */
    val publicAccessStatus: String = "",
    val referredByContact: String = "",
    val creditBalanceCents: Long = 0L,
    val creditEarnedCents: Long = 0L,
    val creditSpentCents: Long = 0L,
    /** Local driver preference/persona non grata. This flag never blocks by name or phone similarity. */
    val blocked: Boolean = false,
    val blockedReason: String = "",
    /** Soft archive only. Passenger history is never physically deleted by Timeline cleanup. */
    val archived: Boolean = false,
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

data class PassengerRideHistory(
    /** Explicitly confirmed physical rides only. Capture/reservation never increments this value. */
    val totalRides: Int,
    val ridesByDriverProfile: Map<String, Int>,
)

@Serializable
enum class PassengerOccurrenceStatus {
    OBSERVED,
    CAPTURED,
    RESERVED,
    CANCELLED,
    COMPLETED,
}

@Serializable
data class PassengerIdentityObservation(
    val id: String = UUID.randomUUID().toString(),
    val passengerId: String,
    val displayName: String = "",
    val whatsapp: String = "",
    val photoUrl: String = "",
    val source: String = "",
    val externalPassengerId: String = "",
    val observedAtMillis: Long = System.currentTimeMillis(),
)

@Serializable
data class PassengerRideRecord(
    val id: String = UUID.randomUUID().toString(),
    val passengerId: String,
    /** Stable physical occurrence key. Reused when OBSERVED/RESERVED becomes COMPLETED. */
    val rideKey: String,
    /** Missing in 0.1.316 data, therefore legacy records migrate safely to OBSERVED. */
    val status: PassengerOccurrenceStatus = PassengerOccurrenceStatus.OBSERVED,
    val tripId: String = "",
    val externalTripId: String = "",
    val driverProfileUuid: String = "",
    val source: String = "",
    val reservationKey: String = "",
    val departureAtMillis: Long? = null,
    val origin: String = "",
    val destination: String = "",
    val boarding: String = "",
    val dropoff: String = "",
    val seats: Int = 1,
    val observedAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

data class PassengerPersistentHistory(
    val profile: PassengerProfile,
    val observations: List<PassengerIdentityObservation>,
    val rides: List<PassengerRideRecord>,
) {
    val completedRides: List<PassengerRideRecord> get() = rides.filter { it.status == PassengerOccurrenceStatus.COMPLETED }
    val totalRides: Int get() = completedRides.size
    val totalOccurrences: Int get() = rides.size
    val firstSeenAtMillis: Long get() = listOfNotNull(
        profile.createdAtMillis,
        observations.minOfOrNull(PassengerIdentityObservation::observedAtMillis),
        rides.minOfOrNull(PassengerRideRecord::observedAtMillis),
    ).minOrNull() ?: profile.createdAtMillis
    val lastSeenAtMillis: Long get() = listOfNotNull(
        profile.updatedAtMillis,
        observations.maxOfOrNull(PassengerIdentityObservation::observedAtMillis),
        rides.maxOfOrNull(PassengerRideRecord::updatedAtMillis),
    ).maxOrNull() ?: profile.updatedAtMillis
}

class PassengerIdentityStore(context: Context) {
    private val appContext = context.applicationContext
    private val tenantScope = RotaCertaTenantRegistry(appContext).activeScope()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val profilesKey = tenantScope.key(KEY_PROFILES)
    private val externalMetadataKey = tenantScope.key(KEY_EXTERNAL_METADATA)
    private val observationsKey = tenantScope.key(KEY_OBSERVATIONS)
    private val rideRecordsKey = tenantScope.key(KEY_RIDE_RECORDS)
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

    fun profileByOnlineIdentityId(raw: String?): PassengerProfile? {
        val onlineId = stableExternalPassengerId(raw) ?: return null
        return profiles().singleOrNull { profile -> onlineId in profile.onlineIdentityIds }
    }

    /**
     * Canonical linking priority. Names are deliberately absent: similarity alone must never merge people.
     */
    fun resolveCanonicalPassenger(
        passengerId: String? = null,
        externalPassengerId: String? = null,
        onlineIdentityId: String? = null,
        whatsapp: String? = null,
    ): PassengerProfile? {
        val allProfiles = profiles()
        val historicalContacts = allProfiles.associate { profile ->
            profile.id to observations(profile.id).map(PassengerIdentityObservation::whatsapp).toSet()
        }
        return selectCanonicalPassenger(
            profiles = allProfiles,
            historicalContactsByProfile = historicalContacts,
            passengerId = passengerId,
            externalPassengerId = externalPassengerId,
            onlineIdentityId = onlineIdentityId,
            whatsapp = whatsapp,
        )
    }

    fun saveProfile(profile: PassengerProfile): PassengerProfile {
        val now = System.currentTimeMillis()
        val normalized = profile.copy(
            displayName = profile.displayName.trim().take(120),
            whatsapp = profile.whatsapp.trim().take(40),
            externalPassengerIds = profile.externalPassengerIds.mapNotNull(::stableExternalPassengerId).toSet(),
            onlineIdentityIds = profile.onlineIdentityIds.mapNotNull(::stableExternalPassengerId).toSet(),
            publicAccessStatus = profile.publicAccessStatus.trim().uppercase().take(24),
            referredByContact = profile.referredByContact.trim().take(40),
            creditBalanceCents = profile.creditBalanceCents.coerceAtLeast(0L),
            creditEarnedCents = profile.creditEarnedCents.coerceAtLeast(0L),
            creditSpentCents = profile.creditSpentCents.coerceAtLeast(0L),
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

    fun linkOnlineIdentityId(profileId: String, onlineIdentityId: String): PassengerProfile? {
        val onlineId = stableExternalPassengerId(onlineIdentityId) ?: return null
        val target = profile(profileId) ?: return null
        val alreadyOwned = profileByOnlineIdentityId(onlineId)
        if (alreadyOwned != null && alreadyOwned.id != target.id) return null
        return saveProfile(target.copy(onlineIdentityIds = target.onlineIdentityIds + onlineId))
    }

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


    fun setArchived(profileId: String, archived: Boolean): PassengerProfile? {
        val target = profile(profileId) ?: return null
        return saveProfile(target.copy(archived = archived))
    }

    /**
     * Every local/private/Rota Certa booking receives a durable canonical passenger identity.
     * Reuse is allowed only by existing passengerId or one exact unique normalized WhatsApp.
     * A booking is an occurrence/reservation, never proof that the passenger travelled.
     */
    fun ensureLocalBookingProfile(booking: Booking): PassengerProfile? {
        if (booking.capacityClaimType != CapacityClaimType.PASSENGER) return null
        val name = booking.passengerName.trim().take(120)
        if (name.isBlank()) return null
        val phone = booking.passengerContact.trim().take(40)
        val existing = resolveCanonicalPassenger(
            passengerId = booking.passengerId,
            whatsapp = phone,
        )
        val base = existing ?: PassengerProfile(
            id = booking.passengerId.trim().takeIf(String::isNotEmpty) ?: UUID.randomUUID().toString(),
            displayName = name,
            whatsapp = phone,
            createdAtMillis = booking.createdAtMillis,
        )
        val saved = saveProfile(
            base.copy(
                displayName = name,
                whatsapp = phone.ifBlank { base.whatsapp },
            ),
        )
        observeIdentity(
            passengerId = saved.id,
            displayName = name,
            whatsapp = phone,
            source = "LOCAL_${booking.source.name}",
        )
        val occurrenceStatus = when (booking.status) {
            BookingStatus.CANCELLED, BookingStatus.EXPIRED -> PassengerOccurrenceStatus.CANCELLED
            BookingStatus.REQUESTED, BookingStatus.HELD, BookingStatus.CONFIRMED -> PassengerOccurrenceStatus.RESERVED
        }
        recordOccurrence(
            passengerId = saved.id,
            rideKey = "local:${booking.id}",
            status = occurrenceStatus,
            tripId = booking.tripId,
            source = booking.source.name,
            seats = booking.seats,
        )
        return saved
    }

    /**
     * BlaBlaCar identity is automatically persisted only from a strong stable passenger UUID/id.
     * Name, phone and future photo changes become observations; they never create a new person
     * while the same external UUID is present.
     */
    fun observeExternalPassenger(
        displayName: String,
        whatsapp: String?,
        externalPassengerId: String?,
        reservationKey: String?,
        externalTripId: String?,
        driverProfileUuid: String?,
        photoUrl: String? = null,
    ): PassengerProfile? {
        val externalId = stableExternalPassengerId(externalPassengerId) ?: return null
        val name = displayName.trim().take(120).ifBlank { "Passageiro" }
        val phone = whatsapp.orEmpty().trim().take(40)
        val existing = profileByExternalPassengerId(externalId)
            ?: exactContactMatches(phone).singleOrNull()
        val base = existing ?: PassengerProfile(
            displayName = name,
            whatsapp = phone,
            externalPassengerIds = setOf(externalId),
        )
        val saved = saveProfile(
            base.copy(
                displayName = name,
                whatsapp = phone.ifBlank { base.whatsapp },
                externalPassengerIds = base.externalPassengerIds + externalId,
            ),
        )
        observeIdentity(
            passengerId = saved.id,
            displayName = name,
            whatsapp = phone,
            photoUrl = photoUrl.orEmpty(),
            source = "BLABLACAR",
            externalPassengerId = externalId,
        )
        reservationKey?.trim()?.takeIf(String::isNotEmpty)?.let { key ->
            val current = externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
            saveExternalMetadata(
                current.copy(
                    passengerId = saved.id,
                    externalPassengerId = externalId,
                    externalTripId = stableExternalPassengerId(externalTripId).orEmpty(),
                    externalProfileUuid = driverProfileUuid.orEmpty().trim().lowercase(),
                ),
            )
        }
        val driverProfile = driverProfileUuid.orEmpty().trim().lowercase()
        val trip = stableExternalPassengerId(externalTripId).orEmpty()
        recordOccurrence(
            passengerId = saved.id,
            rideKey = externalPassengerOccurrenceKey(
                driverProfileUuid = driverProfile,
                externalTripId = trip,
                reservationKey = reservationKey.orEmpty(),
                externalPassengerId = externalId,
            ),
            status = PassengerOccurrenceStatus.CAPTURED,
            tripId = trip,
            externalTripId = trip,
            driverProfileUuid = driverProfile,
            source = "BLABLACAR",
            reservationKey = reservationKey.orEmpty(),
        )
        return saved
    }

    fun observations(profileId: String): List<PassengerIdentityObservation> =
        decode<List<PassengerIdentityObservation>>(prefs.getString(observationsKey, null))
            .orEmpty()
            .filter { it.passengerId == profileId }
            .sortedByDescending(PassengerIdentityObservation::observedAtMillis)

    fun rideRecords(profileId: String): List<PassengerRideRecord> =
        decode<List<PassengerRideRecord>>(prefs.getString(rideRecordsKey, null))
            .orEmpty()
            .filter { it.passengerId == profileId }
            .distinctBy(PassengerRideRecord::rideKey)
            .sortedByDescending(PassengerRideRecord::observedAtMillis)

    fun persistentHistory(profileId: String): PassengerPersistentHistory? {
        val target = profile(profileId) ?: return null
        return PassengerPersistentHistory(target, observations(profileId), rideRecords(profileId))
    }

    private fun observeIdentity(
        passengerId: String,
        displayName: String,
        whatsapp: String,
        photoUrl: String = "",
        source: String,
        externalPassengerId: String = "",
    ) {
        val name = displayName.trim().take(120)
        val phone = whatsapp.trim().take(40)
        val photo = photoUrl.trim().take(500)
        val externalId = stableExternalPassengerId(externalPassengerId).orEmpty()
        val all = decode<List<PassengerIdentityObservation>>(prefs.getString(observationsKey, null)).orEmpty()
        val last = all.filter { it.passengerId == passengerId }.maxByOrNull(PassengerIdentityObservation::observedAtMillis)
        if (last != null &&
            last.displayName == name &&
            last.whatsapp == phone &&
            last.photoUrl == photo &&
            last.externalPassengerId == externalId
        ) return
        val next = PassengerIdentityObservation(
            passengerId = passengerId,
            displayName = name,
            whatsapp = phone,
            photoUrl = photo,
            source = source.take(80),
            externalPassengerId = externalId,
        )
        prefs.edit().putString(observationsKey, json.encodeToString(listOf(next) + all)).apply()
    }

    fun rideRecord(profileId: String, rideKey: String): PassengerRideRecord? =
        rideRecords(profileId).firstOrNull { it.rideKey == rideKey.trim() }

    fun completedRideRecords(profileId: String): List<PassengerRideRecord> =
        rideRecords(profileId).filter { it.status == PassengerOccurrenceStatus.COMPLETED }

    /**
     * Idempotent physical occurrence upsert. Capture/reservation may enrich the same occurrence,
     * but only an explicit COMPLETED transition counts as a travelled ride.
     */
    fun recordOccurrence(
        passengerId: String,
        rideKey: String,
        status: PassengerOccurrenceStatus,
        tripId: String = "",
        externalTripId: String = "",
        driverProfileUuid: String = "",
        source: String = "",
        reservationKey: String = "",
        departureAtMillis: Long? = null,
        origin: String = "",
        destination: String = "",
        boarding: String = "",
        dropoff: String = "",
        seats: Int = 1,
        completedAtMillis: Long? = null,
    ): PassengerRideRecord? {
        val canonicalPassengerId = passengerId.trim().takeIf(String::isNotEmpty) ?: return null
        val key = rideKey.trim().takeIf(String::isNotEmpty) ?: return null
        val all = decode<List<PassengerRideRecord>>(prefs.getString(rideRecordsKey, null)).orEmpty()
        val existing = all.firstOrNull { it.passengerId == canonicalPassengerId && it.rideKey == key }
        val now = System.currentTimeMillis()
        val mergedStatus = mergePassengerOccurrenceStatus(existing?.status, status)
        val next = (existing ?: PassengerRideRecord(
            passengerId = canonicalPassengerId,
            rideKey = key,
            observedAtMillis = now,
        )).copy(
            status = mergedStatus,
            tripId = tripId.trim().take(160).ifBlank { existing?.tripId.orEmpty() },
            externalTripId = externalTripId.trim().take(160).ifBlank { existing?.externalTripId.orEmpty() },
            driverProfileUuid = driverProfileUuid.trim().lowercase().take(80).ifBlank { existing?.driverProfileUuid.orEmpty() },
            source = source.trim().take(80).ifBlank { existing?.source.orEmpty() },
            reservationKey = reservationKey.trim().take(200).ifBlank { existing?.reservationKey.orEmpty() },
            departureAtMillis = departureAtMillis ?: existing?.departureAtMillis,
            origin = origin.trim().take(160).ifBlank { existing?.origin.orEmpty() },
            destination = destination.trim().take(160).ifBlank { existing?.destination.orEmpty() },
            boarding = boarding.trim().take(160).ifBlank { existing?.boarding.orEmpty() },
            dropoff = dropoff.trim().take(160).ifBlank { existing?.dropoff.orEmpty() },
            seats = seats.coerceAtLeast(1),
            completedAtMillis = when (mergedStatus) {
                PassengerOccurrenceStatus.COMPLETED -> existing?.completedAtMillis ?: completedAtMillis ?: now
                else -> null
            },
            updatedAtMillis = now,
        )
        val withoutSamePhysicalOccurrence = all.filterNot {
            it.passengerId == canonicalPassengerId && it.rideKey == key
        }
        prefs.edit().putString(rideRecordsKey, json.encodeToString(listOf(next) + withoutSamePhysicalOccurrence)).apply()
        return next
    }

    fun rideHistory(profileId: String): PassengerRideHistory {
        if (profile(profileId) == null) return PassengerRideHistory(0, emptyMap())
        val completed = completedRideRecords(profileId)
        return PassengerRideHistory(
            totalRides = completed.size,
            ridesByDriverProfile = completed
                .groupingBy {
                    it.driverProfileUuid.ifBlank {
                        if (it.source == "BLABLACAR") "Perfil não identificado" else "Particular / Rota Certa"
                    }
                }
                .eachCount(),
        )
    }

    /**
     * Exact unique normalized WhatsApp is a canonical identity signal. Historical phones are
     * searchable too, so changing the current number does not orphan the old identity.
     */
    fun exactContactMatches(raw: String): List<PassengerProfile> {
        val key = passengerContactKey(raw)
        if (key.isBlank()) return emptyList()
        return profiles().filter { profile ->
            passengerContactKey(profile.whatsapp) == key ||
                observations(profile.id).any { observation -> passengerContactKey(observation.whatsapp) == key }
        }.distinctBy(PassengerProfile::id)
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
        private const val KEY_OBSERVATIONS = "passenger_identity_observations_v1"
        private const val KEY_RIDE_RECORDS = "passenger_ride_records_v1"
    }
}

internal fun stableExternalPassengerId(raw: String?): String? = raw
    ?.trim()
    ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,160}")) }

internal fun validLatitude(value: Double?): Double? = value?.takeIf { it.isFinite() && it in -90.0..90.0 }

internal fun validLongitude(value: Double?): Double? = value?.takeIf { it.isFinite() && it in -180.0..180.0 }

internal fun passengerContactKey(raw: String?): String {
    val digits = raw?.filter(Char::isDigit).orEmpty()
    if (digits.length !in 8..15) return ""
    // Brazilian WhatsApp is commonly observed both as +55XXXXXXXXXXX and XXXXXXXXXXX.
    // Normalize formatting/country prefix only; never use partial-name/contact similarity for identity.
    return if (digits.startsWith("55") && digits.length in 12..13) digits.drop(2) else digits
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

/**
 * Pure canonical selector used by persistence and unit tests. Priority is strict and names are
 * intentionally excluded so name similarity can never merge identities.
 */
internal fun selectCanonicalPassenger(
    profiles: List<PassengerProfile>,
    historicalContactsByProfile: Map<String, Set<String>> = emptyMap(),
    passengerId: String? = null,
    externalPassengerId: String? = null,
    onlineIdentityId: String? = null,
    whatsapp: String? = null,
): PassengerProfile? {
    passengerId?.trim()?.takeIf(String::isNotEmpty)?.let { id ->
        profiles.firstOrNull { it.id == id }?.let { return it }
    }
    stableExternalPassengerId(externalPassengerId)?.let { externalId ->
        profiles.singleOrNull { externalId in it.externalPassengerIds }?.let { return it }
    }
    stableExternalPassengerId(onlineIdentityId)?.let { onlineId ->
        profiles.singleOrNull { onlineId in it.onlineIdentityIds }?.let { return it }
    }
    val contactKey = passengerContactKey(whatsapp)
    if (contactKey.isBlank()) return null
    return profiles.filter { profile ->
        passengerContactKey(profile.whatsapp) == contactKey ||
            historicalContactsByProfile[profile.id].orEmpty().any { passengerContactKey(it) == contactKey }
    }.singleOrNull()
}

/** Shared canonical passenger lookup used by every passenger creation surface. */
class PassengerRepository(context: Context) {
    private val store = PassengerIdentityStore(context.applicationContext)

    fun search(raw: String, limit: Int = 12): List<PassengerProfile> {
        val profiles = store.profiles()
        val observations = profiles.associate { profile -> profile.id to store.observations(profile.id) }
        return searchCanonicalPassengers(profiles, observations, raw, limit)
    }

    fun resolve(
        passengerId: String? = null,
        externalPassengerId: String? = null,
        onlineIdentityId: String? = null,
        whatsapp: String? = null,
    ): PassengerProfile? = store.resolveCanonicalPassenger(passengerId, externalPassengerId, onlineIdentityId, whatsapp)
}


internal fun mergePassengerOccurrenceStatus(
    existing: PassengerOccurrenceStatus?,
    incoming: PassengerOccurrenceStatus,
): PassengerOccurrenceStatus = when {
    existing == PassengerOccurrenceStatus.COMPLETED -> PassengerOccurrenceStatus.COMPLETED
    incoming == PassengerOccurrenceStatus.COMPLETED -> PassengerOccurrenceStatus.COMPLETED
    existing != null && incoming in setOf(PassengerOccurrenceStatus.OBSERVED, PassengerOccurrenceStatus.CAPTURED) -> existing
    else -> incoming
}

internal fun searchCanonicalPassengers(
    profiles: List<PassengerProfile>,
    observationsByProfile: Map<String, List<PassengerIdentityObservation>>,
    raw: String,
    limit: Int = 12,
): List<PassengerProfile> {
    val query = normalizePassengerSearch(raw)
    val rawDigits = raw.filter(Char::isDigit)
    if (query.isBlank() && rawDigits.length < 4) return emptyList()
    val exactPhoneQuery = passengerContactKey(raw)
    val phoneFragment = rawDigits.takeLast(8).takeIf { rawDigits.length >= 4 }.orEmpty()
    return profiles.mapNotNull { profile ->
        val observations = observationsByProfile[profile.id].orEmpty()
        val names = buildList {
            add(profile.displayName)
            addAll(observations.map(PassengerIdentityObservation::displayName))
        }.map(::normalizePassengerSearch).filter(String::isNotBlank)
        val contacts = buildList {
            add(profile.whatsapp)
            addAll(observations.map(PassengerIdentityObservation::whatsapp))
        }.map(::passengerContactKey).filter(String::isNotBlank)
        val score = when {
            exactPhoneQuery.isNotBlank() && contacts.any { it == exactPhoneQuery } -> 0
            query.isNotBlank() && names.any { it == query } -> 0
            query.isNotBlank() && names.any { it.startsWith(query) } -> 1
            query.isNotBlank() && names.any { it.contains(query) } -> 2
            phoneFragment.isNotBlank() && contacts.any { it.contains(phoneFragment) } -> 3
            else -> Int.MAX_VALUE
        }
        score.takeIf { it != Int.MAX_VALUE }?.let { score to profile }
    }.sortedWith(
        compareBy<Pair<Int, PassengerProfile>> { it.first }
            .thenByDescending { it.second.updatedAtMillis }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.second.displayName },
    ).map { it.second }
        .distinctBy(PassengerProfile::id)
        .take(limit.coerceIn(1, 50))
}

internal fun externalPassengerOccurrenceKey(
    driverProfileUuid: String?,
    externalTripId: String?,
    reservationKey: String?,
    externalPassengerId: String?,
): String = listOf(
    "blablacar",
    driverProfileUuid.orEmpty().trim().lowercase().ifBlank { "profile?" },
    stableExternalPassengerId(externalTripId).orEmpty().ifBlank { reservationKey.orEmpty().trim().ifBlank { "trip?" } },
    stableExternalPassengerId(externalPassengerId).orEmpty().ifBlank { "passenger?" },
).joinToString(":")

internal fun normalizePassengerSearch(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
