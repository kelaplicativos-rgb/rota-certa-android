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
        // A canonical reservation URL is stronger and more stable evidence than a DOM-only
        // passenger card click. Network-first can already provide this URL even when the
        // corresponding visual card is temporarily not clickable.
        hasBookingHref -> BlaBlaDirectPassengerStep.RESERVATION_URL
        hasPassengerCard -> BlaBlaDirectPassengerStep.PASSENGER_CARD
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
