package br.com.mapeiaia.rotacerta.trips

import android.content.Context

internal data class PassengerCompletionResult(
    val profile: PassengerProfile,
    val record: PassengerRideRecord,
    val newlyCompleted: Boolean,
)

/**
 * Single authority for the per-passenger ✅ VIAJOU action.
 * Completion is persistent and idempotent by canonical passenger + physical trip/segment identity.
 */
internal class PassengerCompletionService(context: Context) {
    private val store = PassengerIdentityStore(context.applicationContext)

    fun occurrenceKey(entry: TripTimelineEntry, row: EnhancedPassengerCardRow): String {
        row.localBookingId?.trim()?.takeIf(String::isNotEmpty)?.let { return "local:$it" }
        val externalId = stableExternalPassengerId(row.externalPassengerId)
        if (externalId != null) {
            return externalPassengerOccurrenceKey(
                driverProfileUuid = row.externalProfileUuid ?: entry.blablaProfileUuid,
                externalTripId = entry.blablaTripId,
                reservationKey = row.externalReservationKey,
                externalPassengerId = externalId,
            )
        }
        val physicalTrip = listOf(
            entry.localTripId.orEmpty(),
            entry.blablaTripId.orEmpty(),
            entry.tripId,
            entry.departureAtMillis.toString(),
            normalizePassengerSearch(entry.origin),
            normalizePassengerSearch(entry.destination),
        ).firstOrNull(String::isNotBlank).orEmpty()
        val segment = listOf(
            normalizePassengerSearch(row.boarding.orEmpty()),
            normalizePassengerSearch(row.dropoff.orEmpty()),
        ).joinToString("→")
        return "physical:$physicalTrip:$segment"
    }

    fun resolvedProfile(row: EnhancedPassengerCardRow): PassengerProfile? = store.resolveCanonicalPassenger(
        passengerId = row.passengerId,
        externalPassengerId = row.externalPassengerId,
        whatsapp = row.phone,
    )

    fun isCompleted(entry: TripTimelineEntry, row: EnhancedPassengerCardRow): Boolean {
        val profile = resolvedProfile(row) ?: return false
        return store.rideRecord(profile.id, occurrenceKey(entry, row))?.status == PassengerOccurrenceStatus.COMPLETED
    }

    fun confirm(entry: TripTimelineEntry, row: EnhancedPassengerCardRow): PassengerCompletionResult? {
        val profile = resolvedProfile(row)
            ?: stableExternalPassengerId(row.externalPassengerId)?.let { externalId ->
                store.observeExternalPassenger(
                    displayName = row.name,
                    whatsapp = row.phone,
                    externalPassengerId = externalId,
                    reservationKey = row.externalReservationKey,
                    externalTripId = entry.blablaTripId,
                    driverProfileUuid = row.externalProfileUuid ?: entry.blablaProfileUuid,
                )
            }
            ?: row.name.trim().takeIf(String::isNotEmpty)?.let { name ->
                // No name-only merge: this creates a new canonical person only when no strong identity resolved.
                store.createProfile(name, row.phone.orEmpty())
            }
            ?: return null

        val key = occurrenceKey(entry, row)
        val alreadyCompleted = store.rideRecord(profile.id, key)?.status == PassengerOccurrenceStatus.COMPLETED
        val record = store.recordOccurrence(
            passengerId = profile.id,
            rideKey = key,
            status = PassengerOccurrenceStatus.COMPLETED,
            tripId = entry.localTripId ?: entry.tripId,
            externalTripId = entry.blablaTripId.orEmpty(),
            driverProfileUuid = (row.externalProfileUuid ?: entry.blablaProfileUuid).orEmpty(),
            source = row.sources.joinToString("+") { it.name },
            reservationKey = row.externalReservationKey.orEmpty(),
            departureAtMillis = entry.departureAtMillis,
            origin = entry.origin,
            destination = entry.destination,
            boarding = row.boarding.orEmpty(),
            dropoff = row.dropoff.orEmpty(),
            seats = row.seats,
            completedAtMillis = System.currentTimeMillis(),
        ) ?: return null
        return PassengerCompletionResult(profile, record, newlyCompleted = !alreadyCompleted)
    }
}
