package br.com.mapeiaia.rotacerta.trips

internal enum class BlaBlaDirectRosterState {
    UNKNOWN,
    COMPLETE_EMPTY,
    COMPLETE_WITH_PASSENGERS,
}

internal enum class BlaBlaDirectPassengerStep {
    RESERVATION_URL,
    PASSENGER_CARD,
    SKIP,
    FINISH,
}

/** Passenger roster, navigation and monotonic merge decisions. */
internal object BlaBlaCollectorPassengerModule {
    fun rosterState(
        passengerCount: Int,
        rosterComplete: Boolean,
        explicitEmpty: Boolean,
    ): BlaBlaDirectRosterState = when {
        explicitEmpty && passengerCount == 0 -> BlaBlaDirectRosterState.COMPLETE_EMPTY
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
        explicitEmpty && passengerCount == 0 -> true
        structurallyComplete && passengerCount > 0 -> true
        passengerCount > 0 -> stablePasses >= 2
        else -> stablePasses >= 3
    }

    fun nextStep(
        passengerPresent: Boolean,
        hasBookingHref: Boolean,
        needsReservationPage: Boolean,
        hasPassengerCard: Boolean,
    ): BlaBlaDirectPassengerStep = when {
        !passengerPresent -> BlaBlaDirectPassengerStep.FINISH
        hasPassengerCard -> BlaBlaDirectPassengerStep.PASSENGER_CARD
        hasBookingHref && needsReservationPage -> BlaBlaDirectPassengerStep.RESERVATION_URL
        else -> BlaBlaDirectPassengerStep.SKIP
    }

    /** An incomplete read may enrich, but can never erase confirmed rows. */
    fun mergeMonotonic(
        previous: BlaBlaCollectorTrip?,
        current: BlaBlaCollectorTrip,
    ): BlaBlaCollectorTrip {
        if (previous == null || !current.passenger_roster_complete) {
            return BlaBlaPassengerRosterReconciler.reconcile(previous, current)
        }
        if (current.passengers.isEmpty()) {
            return current.copy(booked_seats = 0)
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
        return current.copy(
            passengers = enriched,
            booked_seats = maxOf(current.booked_seats, occupied),
        )
    }
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

internal fun blaBlaDirectPassengerStep(
    passengerPresent: Boolean,
    hasBookingHref: Boolean,
    needsReservationPage: Boolean,
    hasPassengerCard: Boolean,
): BlaBlaDirectPassengerStep = BlaBlaCollectorPassengerModule.nextStep(
    passengerPresent,
    hasBookingHref,
    needsReservationPage,
    hasPassengerCard,
)
