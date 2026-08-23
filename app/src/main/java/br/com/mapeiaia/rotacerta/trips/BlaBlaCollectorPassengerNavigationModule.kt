package br.com.mapeiaia.rotacerta.trips

internal enum class BlaBlaDirectPassengerStep {
    RESERVATION_URL,
    PASSENGER_CARD,
    SKIP,
    FINISH,
}

/** Decides how the collector enters one passenger without owning roster data. */
internal object BlaBlaCollectorPassengerNavigationModule {
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
}

/** Compatibility entry point kept for existing Stage47 regression tests. */
internal fun blaBlaDirectPassengerStep(
    passengerPresent: Boolean,
    hasBookingHref: Boolean,
    needsReservationPage: Boolean,
    hasPassengerCard: Boolean,
): BlaBlaDirectPassengerStep = BlaBlaCollectorPassengerNavigationModule.nextStep(
    passengerPresent,
    hasBookingHref,
    needsReservationPage,
    hasPassengerCard,
)
