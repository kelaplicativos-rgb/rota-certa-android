package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.text.Normalizer

internal enum class BlaBlaDirectRosterState {
    UNKNOWN,
    COMPLETE_EMPTY,
    COMPLETE_WITH_PASSENGERS,
}

/** Passenger roster, names and monotonic merge decisions. */
internal object BlaBlaCollectorPassengerModule {
    fun normalizePhone(raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val hasPlus = value.startsWith("+")
        val digits = value.filter(Char::isDigit)
        if (digits.length !in 8..15) return null
        return if (hasPlus) "+$digits" else digits
    }

    fun rosterState(
        passengerCount: Int,
        rosterComplete: Boolean,
        explicitEmpty: Boolean,
    ): BlaBlaDirectRosterState = when {
        explicitEmpty && !rosterComplete -> BlaBlaDirectRosterState.UNKNOWN
        rosterComplete && passengerCount == 0 -> BlaBlaDirectRosterState.COMPLETE_EMPTY
        rosterComplete && passengerCount > 0 -> BlaBlaDirectRosterState.COMPLETE_WITH_PASSENGERS
        else -> BlaBlaDirectRosterState.UNKNOWN
    }

    /**
     * Accepts a rendered roster without depending on one brittle data-testid.
     * A positive roster needs two identical terminal observations; an unmarked
     * empty roster needs three. A visible expansion control keeps it open.
     */
    fun rosterCompleteAfterStableProbe(
        passengerCount: Int,
        structurallyComplete: Boolean,
        explicitEmpty: Boolean,
        hasMore: Boolean,
        terminalEvidence: Boolean,
        stablePasses: Int,
    ): Boolean = when {
        passengerCount < 0 || hasMore || !terminalEvidence -> false
        explicitEmpty && passengerCount == 0 -> stablePasses >= 3
        structurallyComplete && passengerCount > 0 -> true
        passengerCount > 0 -> stablePasses >= 2
        else -> stablePasses >= 3
    }

    fun shouldAwaitNetworkBeforeEmptyRoster(
        networkResolved: Boolean,
        passengerCount: Int,
        readAttempts: Int,
        maxReadAttempts: Int,
    ): Boolean = !networkResolved && passengerCount == 0 && readAttempts < maxReadAttempts

    /**
     * Collapses two DOM observations of the same visible booking without
     * inventing extra occupied seats. Distinct strong passenger URLs or phone
     * numbers remain separate even when the visible name is the same.
     */
    fun coalesceDuplicateEvidence(
        passengers: List<BlaBlaCollectorPassenger>,
    ): List<BlaBlaCollectorPassenger> {
        if (passengers.size < 2) return passengers
        val merged = mutableListOf<BlaBlaCollectorPassenger>()
        passengers.forEach { incoming ->
            val normalizedIncoming = incoming.copy(seats = incoming.seats.coerceAtLeast(1))
            val index = merged.indexOfFirst { existing -> duplicateEvidenceMatches(existing, normalizedIncoming) }
            if (index < 0) {
                merged += normalizedIncoming
            } else {
                val existing = merged[index]
                merged[index] = existing.copy(
                    name = normalizedIncoming.name.ifBlank { existing.name },
                    seats = maxOf(existing.seats, normalizedIncoming.seats),
                    boarding = normalizedIncoming.boarding?.takeIf(String::isNotBlank) ?: existing.boarding,
                    dropoff = normalizedIncoming.dropoff?.takeIf(String::isNotBlank) ?: existing.dropoff,
                    phone = normalizedIncoming.phone?.takeIf(String::isNotBlank) ?: existing.phone,
                    booking_href = normalizedIncoming.booking_href?.takeIf(String::isNotBlank) ?: existing.booking_href,
                )
            }
        }
        return merged
    }

    /** An incomplete read may enrich, but can never erase confirmed rows. */
    fun mergeMonotonic(
        previous: BlaBlaCollectorTrip?,
        current: BlaBlaCollectorTrip,
    ): BlaBlaCollectorTrip {
        if (previous == null) {
            return preserveStableTripMetadata(null, current)
        }
        if (!current.passenger_roster_complete) {
            val merged = preserveStableTripMetadata(
                previous,
                BlaBlaPassengerRosterReconciler.reconcile(previous, current),
            )
            // A partial roster read itself cannot prove completeness. However, when
            // the snapshot positively enriches another field and the reconciled
            // occupancy is unchanged, roster fields were effectively unobserved by
            // that partial enrichment and the last confirmed completeness is kept.
            val preservePreviouslyConfirmedCompleteness =
                previous.passenger_roster_complete &&
                    sameRosterOccupancy(previous, merged) &&
                    hasPositiveNonRosterEnrichment(previous, current)
            return merged.copy(
                passenger_roster_complete = preservePreviouslyConfirmedCompleteness,
            )
        }
        if (current.passengers.isEmpty()) {
            return preserveStableTripMetadata(previous, current.copy(booked_seats = 0))
        }
        val enriched = current.passengers.map { incoming ->
            val confirmed = previous.passengers.firstOrNull { prior ->
                BlaBlaPassengerRosterReconciler.matches(prior, incoming)
            }
            if (confirmed == null) {
                incoming
            } else {
                incoming.copy(
                    name = incoming.name.ifBlank { confirmed.name },
                    boarding = incoming.boarding?.takeIf(String::isNotBlank) ?: confirmed.boarding,
                    dropoff = incoming.dropoff?.takeIf(String::isNotBlank) ?: confirmed.dropoff,
                    phone = incoming.phone?.takeIf(String::isNotBlank) ?: confirmed.phone,
                    booking_href = incoming.booking_href?.takeIf(String::isNotBlank) ?: confirmed.booking_href,
                )
            }
        }
        val occupied = enriched.sumOf { it.seats.coerceAtLeast(1) }
        return preserveStableTripMetadata(
            previous,
            current.copy(
                passengers = enriched,
                booked_seats = maxOf(current.booked_seats, occupied),
            ),
        )
    }

    private fun preserveStableTripMetadata(
        previous: BlaBlaCollectorTrip?,
        current: BlaBlaCollectorTrip,
    ): BlaBlaCollectorTrip {
        val expectedTripId = current.trip_id?.trim()?.takeIf(String::isNotEmpty)
            ?: previous?.trip_id?.trim()?.takeIf(String::isNotEmpty)
        val currentRawHref = current.public_trip_href?.trim()?.takeIf(String::isNotEmpty)
        val previousRawHref = previous?.public_trip_href?.trim()?.takeIf(String::isNotEmpty)
        val currentHref = BlaBlaCollectorUrlModule.publicTripForCollectorState(
            currentRawHref,
            expectedTripId,
            current.public_trip_href_binding,
        )
        val previousHref = BlaBlaCollectorUrlModule.publicTripForCollectorState(
            previousRawHref,
            expectedTripId,
            previous?.public_trip_href_binding,
        )
        val keepsCurrentHref = currentHref != null
        if (currentRawHref != null && currentHref == null) {
            UnifiedDebugEventStore.record(
                "PUBLIC_TRIP_LINK_REJECTED",
                "br.com.mapeiaia.rotacerta",
                "tripId=" + expectedTripId.orEmpty() +
                    " source=" + current.public_trip_href_source.ifBlank { "collector_snapshot" } +
                    " reason=invalid_or_unbound_observation previousValid=" + (previousHref != null) +
                    " action=" + if (previousHref != null) "preserve_previous" else "keep_unavailable",
            )
        }
        fun observed(currentValue: String?, previousValue: String?): String? =
            currentValue?.takeIf(String::isNotBlank) ?: previousValue?.takeIf(String::isNotBlank)

        val previousItinerary = previous?.itinerary_stops.orEmpty()
        val effectiveItinerary = current.itinerary_stops.takeIf { it.isNotEmpty() } ?: previousItinerary
        val sameObservedItinerary = previous != null &&
            effectiveItinerary.map(::normalizeEvidence) == previousItinerary.map(::normalizeEvidence)
        val effectiveAvailability = current.availability
            .takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            ?: previous?.availability
                ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            ?: current.availability
        val effectiveValidation = current.uuid_validation
            .takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            ?: previous?.uuid_validation
                ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            ?: current.uuid_validation

        return current.copy(
            profile_name = current.profile_name.takeIf(String::isNotBlank) ?: previous?.profile_name.orEmpty(),
            departure_time = observed(current.departure_time, previous?.departure_time),
            arrival_time = observed(current.arrival_time, previous?.arrival_time),
            search_from = observed(current.search_from, previous?.search_from),
            search_to = observed(current.search_to, previous?.search_to),
            actual_departure = observed(current.actual_departure, previous?.actual_departure),
            actual_arrival = observed(current.actual_arrival, previous?.actual_arrival),
            price = observed(current.price, previous?.price),
            availability = effectiveAvailability,
            trip_href = observed(current.trip_href, previous?.trip_href),
            trip_id = observed(current.trip_id, previous?.trip_id),
            uuid_validation = effectiveValidation,
            itinerary_stops = effectiveItinerary,
            itinerary_authoritative = current.itinerary_authoritative ||
                (sameObservedItinerary && previous?.itinerary_authoritative == true),
            public_trip_href = currentHref ?: previousHref,
            public_trip_href_source = if (keepsCurrentHref) {
                current.public_trip_href_source
            } else {
                previous?.public_trip_href_source.orEmpty()
            },
            public_trip_href_binding = if (keepsCurrentHref) {
                current.public_trip_href_binding
            } else {
                previous?.public_trip_href_binding.orEmpty()
            },
            // A partial/reloaded snapshot cannot erase the last seat-editor value
            // confirmed for this same strong trip identity.
            published_seats = current.published_seats ?: previous?.published_seats,
        )
    }

    private fun hasPositiveNonRosterEnrichment(
        previous: BlaBlaCollectorTrip,
        current: BlaBlaCollectorTrip,
    ): Boolean {
        fun changedString(previousValue: String?, currentValue: String?): Boolean {
            val currentObserved = currentValue?.trim()?.takeIf(String::isNotEmpty) ?: return false
            return currentObserved != previousValue?.trim().orEmpty()
        }

        return changedString(previous.profile_name, current.profile_name) ||
            changedString(previous.departure_time, current.departure_time) ||
            changedString(previous.arrival_time, current.arrival_time) ||
            changedString(previous.search_from, current.search_from) ||
            changedString(previous.search_to, current.search_to) ||
            changedString(previous.actual_departure, current.actual_departure) ||
            changedString(previous.actual_arrival, current.actual_arrival) ||
            changedString(previous.price, current.price) ||
            (
                current.availability.isNotBlank() &&
                    !current.availability.equals("unknown", ignoreCase = true) &&
                    current.availability != previous.availability
                ) ||
            changedString(previous.trip_href, current.trip_href) ||
            changedString(previous.trip_id, current.trip_id) ||
            (
                current.uuid_validation.isNotBlank() &&
                    !current.uuid_validation.equals("unknown", ignoreCase = true) &&
                    current.uuid_validation != previous.uuid_validation
                ) ||
            (
                current.itinerary_stops.isNotEmpty() &&
                    current.itinerary_stops != previous.itinerary_stops
                ) ||
            current.published_seats?.let { it != previous.published_seats } == true ||
            changedString(previous.public_trip_href, current.public_trip_href)
    }

    private fun sameRosterOccupancy(
        previous: BlaBlaCollectorTrip,
        current: BlaBlaCollectorTrip,
    ): Boolean {
        if (previous.booked_seats != current.booked_seats || previous.passengers.size != current.passengers.size) {
            return false
        }
        val unmatched = current.passengers.toMutableList()
        return previous.passengers.all { prior ->
            val index = unmatched.indexOfFirst { candidate ->
                prior.seats.coerceAtLeast(1) == candidate.seats.coerceAtLeast(1) &&
                    BlaBlaPassengerRosterReconciler.matches(prior, candidate)
            }
            if (index < 0) {
                false
            } else {
                unmatched.removeAt(index)
                true
            }
        }
    }

    private fun duplicateEvidenceMatches(
        left: BlaBlaCollectorPassenger,
        right: BlaBlaCollectorPassenger,
    ): Boolean {
        val leftHref = left.booking_href?.trim().orEmpty()
        val rightHref = right.booking_href?.trim().orEmpty()
        val leftUuid = passengerUuid(leftHref)
        val rightUuid = passengerUuid(rightHref)
        if (leftUuid != null && rightUuid != null && leftUuid == rightUuid) return true
        if (leftHref.isNotBlank() && rightHref.isNotBlank()) {
            return BlaBlaCollectorUrlModule.samePassengerPage(leftHref, rightHref)
        }

        val leftPhone = normalizePhone(left.phone)
        val rightPhone = normalizePhone(right.phone)
        if (leftPhone != null && rightPhone != null) return leftPhone == rightPhone

        val leftName = normalizeEvidence(left.name)
        val rightName = normalizeEvidence(right.name)
        if (leftName.isBlank() || leftName != rightName) return false
        return compatiblePlace(left.boarding, right.boarding) &&
            compatiblePlace(left.dropoff, right.dropoff)
    }

    private fun passengerUuid(raw: String): String? {
        if (raw.isBlank()) return null
        val identity = BlaBlaCollectorUrlModule.passengerIdentityKey(raw).lowercase()
        return identity.takeIf(PASSENGER_UUID::matches)
    }

    private fun compatiblePlace(left: String?, right: String?): Boolean {
        val first = normalizeEvidence(left.orEmpty())
        val second = normalizeEvidence(right.orEmpty())
        return first.isBlank() || second.isBlank() || first == second
    }

    private fun normalizeEvidence(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private val PASSENGER_UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
}

internal fun blaBlaDirectRosterState(
    passengerCount: Int,
    rosterComplete: Boolean,
    explicitEmpty: Boolean,
): BlaBlaDirectRosterState = BlaBlaCollectorPassengerModule.rosterState(
    passengerCount,
    rosterComplete,
    explicitEmpty,
)

internal fun blaBlaDirectRosterCompleteAfterStableProbe(
    passengerCount: Int,
    structurallyComplete: Boolean,
    explicitEmpty: Boolean,
    hasMore: Boolean,
    terminalEvidence: Boolean,
    stablePasses: Int,
): Boolean = BlaBlaCollectorPassengerModule.rosterCompleteAfterStableProbe(
    passengerCount,
    structurallyComplete,
    explicitEmpty,
    hasMore,
    terminalEvidence,
    stablePasses,
)
