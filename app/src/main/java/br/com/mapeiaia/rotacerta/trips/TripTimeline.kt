package br.com.mapeiaia.rotacerta.trips

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Source-neutral timeline model. Local Agenda trips and collector snapshots
 * share this model so conflict/continuity rules remain centralized.
 */
data class TripTimelineEntry(
    val tripId: String,
    val profileId: String,
    val profileLabel: String,
    val departureAtMillis: Long,
    val arrivalAtMillis: Long?,
    val origin: String,
    val destination: String,
    val status: TripStatus,
    val capacity: Int,
    val minimumOccupiedSeats: Int,
    val maximumOccupiedSeats: Int,
    val sourcePassengerSeats: Map<BookingSource, Int>,
    val localTripId: String? = null,
    val blablaTripId: String? = null,
    val blablaTripHref: String? = null,
    val blablaProfileUuid: String? = null,
    val blablaPrice: String? = null,
    val blablaAvailability: String? = null,
    val blablaPassengers: List<BlaBlaCollectorPassenger> = emptyList(),
    val issues: Set<TripTimelineIssue> = emptySet(),
) {
    val minimumAvailableSeats: Int
        get() = (capacity - maximumOccupiedSeats).coerceAtLeast(0)

    val maximumAvailableSeats: Int
        get() = (capacity - minimumOccupiedSeats).coerceAtLeast(0)
}

enum class TripTimelineIssue {
    DUPLICATE,
    PHYSICAL_CONFLICT,
    PROFILE_CONTINUITY,
    OVERBOOKING,
    VALIDATION_PENDING,
}

object TripTimelineEngine {
    fun fromLocalAgenda(
        trips: List<Trip>,
        bookings: List<Booking>,
        localProfileId: String = "local",
        localProfileLabel: String = "Agenda",
        nowMillis: Long = System.currentTimeMillis(),
    ): List<TripTimelineEntry> {
        val base = trips
            .filterNot { it.status == TripStatus.CANCELLED }
            .mapNotNull { trip ->
                val stops = trip.stops.sortedBy(TripStop::order)
                if (stops.size < 2) return@mapNotNull null
                val tripBookings = bookings.filter { it.tripId == trip.id }
                val loads = SeatAvailabilityEngine.segmentLoads(trip, tripBookings, nowMillis)
                val occupied = loads.map(SegmentLoad::occupiedSeats)
                TripTimelineEntry(
                    tripId = trip.id,
                    profileId = localProfileId,
                    profileLabel = localProfileLabel,
                    departureAtMillis = trip.departureAtMillis,
                    arrivalAtMillis = stops.last().plannedArrivalMillis,
                    origin = stops.first().name,
                    destination = stops.last().name,
                    status = trip.status,
                    capacity = trip.capacity,
                    minimumOccupiedSeats = occupied.minOrNull() ?: 0,
                    maximumOccupiedSeats = occupied.maxOrNull() ?: 0,
                    sourcePassengerSeats = passengerSeatsBySource(tripBookings, nowMillis),
                    localTripId = trip.id,
                )
            }
            .sortedBy(TripTimelineEntry::departureAtMillis)
        return annotate(base)
    }

    fun annotate(entries: List<TripTimelineEntry>): List<TripTimelineEntry> {
        if (entries.isEmpty()) return emptyList()
        val issues = entries.associate { it.tripId to it.issues.toMutableSet() }.toMutableMap()

        entries.forEach { entry ->
            if (entry.capacity > 0 && entry.maximumOccupiedSeats > entry.capacity) {
                issues.getValue(entry.tripId) += TripTimelineIssue.OVERBOOKING
            }
        }

        entries.groupBy { duplicateKey(it) }.values
            .filter { it.size > 1 }
            .forEach { group -> group.forEach { issues.getValue(it.tripId) += TripTimelineIssue.DUPLICATE } }

        val chronological = entries.sortedBy(TripTimelineEntry::departureAtMillis)
        chronological.zipWithNext().forEach { (previous, next) ->
            if (normalizePlace(previous.destination) != normalizePlace(next.origin)) {
                issues.getValue(next.tripId) += TripTimelineIssue.PROFILE_CONTINUITY
            }
        }

        val ordered = entries.sortedBy(TripTimelineEntry::departureAtMillis)
        for (leftIndex in ordered.indices) {
            val left = ordered[leftIndex]
            val leftEnd = left.arrivalAtMillis
            for (rightIndex in leftIndex + 1 until ordered.size) {
                val right = ordered[rightIndex]
                if (left.profileId == right.profileId && left.tripId == right.tripId) continue
                if (leftEnd != null) {
                    if (right.departureAtMillis >= leftEnd) break
                    issues.getValue(left.tripId) += TripTimelineIssue.PHYSICAL_CONFLICT
                    issues.getValue(right.tripId) += TripTimelineIssue.PHYSICAL_CONFLICT
                } else if (left.departureAtMillis == right.departureAtMillis && left.tripId != right.tripId) {
                    issues.getValue(left.tripId) += TripTimelineIssue.PHYSICAL_CONFLICT
                    issues.getValue(right.tripId) += TripTimelineIssue.PHYSICAL_CONFLICT
                }
            }
        }

        return entries.sortedBy(TripTimelineEntry::departureAtMillis).map { entry ->
            entry.copy(issues = issues.getValue(entry.tripId).toSet())
        }
    }

    private fun passengerSeatsBySource(bookings: List<Booking>, nowMillis: Long): Map<BookingSource, Int> {
        val activePassengers = bookings.filter { booking ->
            booking.capacityClaimType == CapacityClaimType.PASSENGER && when (booking.status) {
                BookingStatus.CONFIRMED -> true
                BookingStatus.HELD -> booking.holdExpiresAtMillis == null || booking.holdExpiresAtMillis > nowMillis
                BookingStatus.REQUESTED,
                BookingStatus.CANCELLED,
                BookingStatus.EXPIRED,
                -> false
            }
        }
        val grouped = activePassengers.groupBy { it.occupancyGroupId?.trim()?.takeIf(String::isNotEmpty) }
        val result = mutableMapOf<BookingSource, Int>()
        grouped[null].orEmpty().forEach { booking ->
            result[booking.source] = (result[booking.source] ?: 0) + booking.seats
        }
        grouped.filterKeys { it != null }.values.forEach { group ->
            val representative = group.maxByOrNull(Booking::seats) ?: return@forEach
            result[representative.source] = (result[representative.source] ?: 0) + representative.seats
        }
        return result.toMap()
    }

    private fun duplicateKey(entry: TripTimelineEntry): String = listOf(
        entry.departureAtMillis.toString(),
        normalizePlace(entry.origin),
        normalizePlace(entry.destination),
    ).joinToString("|")

    private fun normalizePlace(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}

/**
 * Pure presentation filter for the Timeline. It must be called only after the
 * physical agenda has already been merged/consolidated/validated.
 */
internal fun filterTimelineEntries(
    entries: List<TripTimelineEntry>,
    trips: List<Trip>,
    bookings: List<Booking>,
    query: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): List<TripTimelineEntry> {
    val terms = timelineSearchTerms(query)
    if (terms.isEmpty()) return entries

    val tripsById = trips.associateBy(Trip::id)
    val bookingsByTrip = bookings
        .asSequence()
        .filter { booking ->
            booking.capacityClaimType == CapacityClaimType.PASSENGER && booking.seats > 0 && when (booking.status) {
                BookingStatus.CONFIRMED -> true
                BookingStatus.HELD -> booking.holdExpiresAtMillis == null || booking.holdExpiresAtMillis > nowMillis
                BookingStatus.REQUESTED,
                BookingStatus.CANCELLED,
                BookingStatus.EXPIRED,
                -> false
            }
        }
        .groupBy(Booking::tripId)

    return entries.filter { entry ->
        val localTrip = entry.localTripId?.let(tripsById::get) ?: tripsById[entry.tripId]
        val stopsById = localTrip?.stops.orEmpty().associateBy(TripStop::id)
        val localPassengerParts = localTrip?.let { trip ->
            bookingsByTrip[trip.id].orEmpty().flatMap { booking ->
                listOfNotNull(
                    booking.passengerName,
                    booking.passengerContact,
                    stopsById[booking.boardingStopId]?.name,
                    stopsById[booking.dropoffStopId]?.name,
                    timelineSourceLabel(booking.source),
                    booking.source.name,
                )
            }
        }.orEmpty()
        val externalPassengerParts = entry.blablaPassengers.flatMap { passenger ->
            listOfNotNull(
                passenger.name,
                passenger.phone,
                passenger.boarding,
                passenger.dropoff,
                "BlaBlaCar",
            )
        }
        val dateParts = timelineDateSearchParts(entry.departureAtMillis, zoneId, locale)
        val haystack = normalizeTimelineSearchText(
            buildList {
                add(entry.profileLabel)
                add(entry.profileId)
                entry.blablaProfileUuid?.let(::add)
                add(entry.origin)
                add(entry.destination)
                addAll(dateParts)
                addAll(externalPassengerParts)
                addAll(localPassengerParts)
            }.joinToString(" ")
        )
        terms.all(haystack::contains)
    }
}

internal fun timelineSearchTerms(query: String): List<String> = normalizeTimelineSearchText(query)
    .split(' ')
    .filter(String::isNotBlank)
    .distinct()

internal fun normalizeTimelineSearchText(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun timelineDateSearchParts(epochMillis: Long, zoneId: ZoneId, locale: Locale): List<String> {
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
    return listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd", locale).format(dateTime),
        DateTimeFormatter.ofPattern("dd/MM/yyyy", locale).format(dateTime),
        DateTimeFormatter.ofPattern("dd/MM", locale).format(dateTime),
        DateTimeFormatter.ofPattern("HH:mm", locale).format(dateTime),
        DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm", locale).format(dateTime),
        DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy HH:mm", locale).format(dateTime),
    )
}

private fun timelineSourceLabel(source: BookingSource): String = when (source) {
    BookingSource.BLABLACAR -> "BlaBlaCar"
    BookingSource.PRIVATE -> "Particular"
    BookingSource.ROTA_CERTA -> "Rota Certa"
    BookingSource.OTHER -> "Outro"
}
