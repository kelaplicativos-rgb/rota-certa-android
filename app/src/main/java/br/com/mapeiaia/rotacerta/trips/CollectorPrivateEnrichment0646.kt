package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.time.LocalDate

/**
 * 0.1.646 collector contract:
 *
 * HTML remains the only authority allowed to create/replace a BlaBlaCar trip/card.
 * Collector data may only fill private passenger fields on an already-existing
 * canonical booking with the same strong trip identity. It can never create a trip,
 * create/remove a passenger booking, change seats/status/segment identity, tombstone
 * siblings, or replace the HTML external snapshot.
 */
internal data class CollectorPrivateEnrichmentResult0646(
    val consideredSources: Int = 0,
    val matchedCanonicalTrips: Int = 0,
    val enrichedTrips: Int = 0,
    val enrichedBookings: Int = 0,
    val skippedOutsideScope: Int = 0,
    val skippedIdentity: Int = 0,
)

internal object CollectorPrivateEnrichment0646 {
    internal val readOnlyPassengerScripts: List<String> = listOf(
        BlaBlaBrowserRequest.SESSION_IDENTITY,
        BlaBlaBrowserRequest.TRIP_OPEN,
        BlaBlaBrowserRequest.TRIP_DETAIL,
        BlaBlaBrowserRequest.TRIP_ITINERARY,
        BlaBlaBrowserRequest.PASSENGER_ROSTER,
        BlaBlaBrowserRequest.PASSENGER_OPEN,
        BlaBlaBrowserRequest.PASSENGER_IDENTITY,
        BlaBlaBrowserRequest.PASSENGER_CONTACT,
        BlaBlaBrowserRequest.PASSENGER_FARE,
        BlaBlaBrowserRequest.PASSENGER_SEGMENT,
        BlaBlaBrowserRequest.PASSENGER_ADDRESSES,
    ).map(BlaBlaBrowserRequest::name)

    fun needsPrivateEnrichment(
        bookings: List<Booking>,
    ): Boolean = bookings
        .asSequence()
        .filter { booking ->
            booking.source == BookingSource.BLABLACAR &&
                booking.capacityClaimType == CapacityClaimType.EXTERNAL_OCCUPANCY &&
                booking.status !in setOf(BookingStatus.REJECTED, BookingStatus.CANCELLED, BookingStatus.EXPIRED)
        }
        .any { booking ->
            booking.passengerContact.isBlank() ||
                booking.fareMinorUnits == null ||
                booking.boardingAddress.isBlank()
        }

    fun enrichExactTarget(
        context: Context,
        store: TripStore,
        target: BlaBlaTripTarget0407,
        source: BlaBlaCollectorTrip?,
    ): CollectorPrivateEnrichmentResult0646 {
        val candidate = source
            ?.takeIf {
                it.profile_uuid.trim().equals(target.profileUuid.trim(), ignoreCase = true) &&
                    it.trip_id?.trim() == target.tripId
            }
            ?: return CollectorPrivateEnrichmentResult0646(skippedIdentity = 1)
        return enrich(
            context = context,
            store = store,
            sources = listOf(candidate),
            allowedProfileUuid = target.profileUuid,
            allowedTripId = target.tripId,
        )
    }

    fun enrich(
        context: Context,
        store: TripStore,
        sources: List<BlaBlaCollectorTrip>,
        allowedDates: Set<LocalDate>? = null,
        allowedProfileUuid: String? = null,
        allowedTripId: String? = null,
    ): CollectorPrivateEnrichmentResult0646 {
        val app = context.applicationContext
        val canonicalTrips = store.trips()
        val identityStore = PassengerIdentityStore(app)
        val allowedProfile = allowedProfileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
        val allowedTrip = allowedTripId?.trim()?.takeIf(String::isNotEmpty)
        var considered = 0
        var matched = 0
        var enrichedTrips = 0
        var enrichedBookings = 0
        var outsideScope = 0
        var skippedIdentity = 0

        sources.forEach { source ->
            considered++
            val profile = source.profile_uuid.trim().lowercase()
            val tripId = source.trip_id?.trim().orEmpty()
            if (
                profile.isBlank() ||
                tripId.isBlank() ||
                source.identity_conflict ||
                (allowedProfile != null && profile != allowedProfile) ||
                (allowedTrip != null && tripId != allowedTrip)
            ) {
                skippedIdentity++
                return@forEach
            }

            if (!allowedDates.isNullOrEmpty()) {
                val date = runCatching { LocalDate.parse(source.date.trim()) }.getOrNull()
                if (date == null || date !in allowedDates) {
                    outsideScope++
                    return@forEach
                }
            }

            val canonicalMatches = canonicalTrips.filter { trip ->
                !trip.deleted &&
                    trip.blablaProfileUuid?.trim()?.lowercase() == profile &&
                    trip.blablaTripId?.trim() == tripId
            }
            if (canonicalMatches.size != 1) {
                skippedIdentity++
                UnifiedDebugEventStore.record(
                    "COLLECTOR_PRIVATE_ENRICHMENT_SKIPPED_0646",
                    app.packageName,
                    "profileKey=" + seatSyncDiagnosticKey(profile) +
                        " tripId=" + seatSyncDiagnosticKey(tripId) +
                        " canonicalMatches=" + canonicalMatches.size +
                        " reason=STRONG_IDENTITY_NOT_UNIQUE action=NO_WRITE",
                )
                return@forEach
            }

            val canonical = canonicalMatches.single()
            val before = store.bookingsFor(canonical.id)
            if (before.isEmpty()) return@forEach
            matched++

            // Reuse the already-audited private mirror merger. Its semantics are monotonic:
            // non-empty canonical values win; collector/private metadata only fill blanks.
            val enriched = PublicAgendaAutoSync0300.externalPrivateMirrorBookings0511(
                source = source,
                bookings = before,
                metadataLookup = identityStore::externalMetadata,
            )
            val changed = enriched.filterIndexed { index, booking ->
                val previous = before.getOrNull(index)
                previous != null && previous.copy(updatedAtMillis = 0L) != booking.copy(updatedAtMillis = 0L)
            }
            if (changed.isEmpty()) return@forEach

            store.saveBookingsBatch(
                bookingsToSave = changed,
                preserveSourceUpdatedAt = false,
            )
            enrichedTrips++
            enrichedBookings += changed.size

            UnifiedDebugEventStore.recordAlways(
                "COLLECTOR_CANONICAL_PRIVATE_ENRICHMENT_COMMITTED_0646",
                app.packageName,
                "canonicalTripId=" + seatSyncDiagnosticKey(canonical.id) +
                    " profileKey=" + seatSyncDiagnosticKey(profile) +
                    " tripId=" + seatSyncDiagnosticKey(tripId) +
                    " bookings=" + changed.size +
                    " whitelist=passengerId,phone,fare,address,coordinates" +
                    " tripWrite=false rosterCreate=false rosterDelete=false seatsWrite=false statusWrite=false siblingWrite=false",
            )
        }

        if (enrichedBookings > 0) {
            BookingRealtimeEvents0356.notifyChanged()
            BlaBlaTripControlEvents0407.notifyChanged()
            TripWidgetProvider.updateAll(app)
        }

        return CollectorPrivateEnrichmentResult0646(
            consideredSources = considered,
            matchedCanonicalTrips = matched,
            enrichedTrips = enrichedTrips,
            enrichedBookings = enrichedBookings,
            skippedOutsideScope = outsideScope,
            skippedIdentity = skippedIdentity,
        )
    }
}
