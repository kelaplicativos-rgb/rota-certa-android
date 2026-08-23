package br.com.mapeiaia.rotacerta.trips

internal enum class BlaBlaDirectRosterState {
    UNKNOWN,
    COMPLETE_EMPTY,
    COMPLETE_WITH_PASSENGERS,
}

internal fun blaBlaDirectRosterState(
    passengerCount: Int,
    rosterComplete: Boolean,
    explicitEmpty: Boolean,
): BlaBlaDirectRosterState = when {
    explicitEmpty && passengerCount == 0 -> BlaBlaDirectRosterState.COMPLETE_EMPTY
    rosterComplete && passengerCount > 0 -> BlaBlaDirectRosterState.COMPLETE_WITH_PASSENGERS
    else -> BlaBlaDirectRosterState.UNKNOWN
}

internal enum class BlaBlaDirectPassengerStep {
    RESERVATION_URL,
    PASSENGER_CARD,
    SKIP,
    FINISH,
}

internal fun blaBlaDirectPassengerStep(
    passengerPresent: Boolean,
    hasBookingHref: Boolean,
    needsReservationPage: Boolean,
    hasPassengerCard: Boolean,
): BlaBlaDirectPassengerStep = when {
    !passengerPresent -> BlaBlaDirectPassengerStep.FINISH
    hasBookingHref && needsReservationPage -> BlaBlaDirectPassengerStep.RESERVATION_URL
    !hasBookingHref && hasPassengerCard -> BlaBlaDirectPassengerStep.PASSENGER_CARD
    else -> BlaBlaDirectPassengerStep.SKIP
}

internal fun blaBlaDirectCollectorStatus(
    accountCount: Int,
    verifiedAccountCount: Int,
    identityConflictCount: Int,
    rosterIncompleteCount: Int,
    skippedCount: Int,
): String = when {
    accountCount <= 0 -> "empty"
    verifiedAccountCount <= 0 -> "blocked"
    identityConflictCount > 0 || rosterIncompleteCount > 0 || skippedCount > 0 -> "partial"
    verifiedAccountCount == accountCount -> "validated"
    else -> "partial"
}

internal fun blaBlaDirectCoverageComplete(
    accountCount: Int,
    verifiedAccountCount: Int,
    identityConflictCount: Int,
    rosterIncompleteCount: Int,
    skippedCount: Int,
): Boolean = blaBlaDirectCollectorStatus(
    accountCount = accountCount,
    verifiedAccountCount = verifiedAccountCount,
    identityConflictCount = identityConflictCount,
    rosterIncompleteCount = rosterIncompleteCount,
    skippedCount = skippedCount,
) == "validated"

internal fun blaBlaDirectCallbackMatches(
    expectedSyncGeneration: Long,
    expectedNavigationGeneration: Long,
    expectedCandidateIndex: Int,
    expectedTripId: String?,
    currentSyncGeneration: Long,
    currentNavigationGeneration: Long,
    currentCandidateIndex: Int,
    currentTripId: String?,
): Boolean =
    expectedSyncGeneration == currentSyncGeneration &&
        expectedNavigationGeneration == currentNavigationGeneration &&
        expectedCandidateIndex == currentCandidateIndex &&
        !expectedTripId.isNullOrBlank() &&
        expectedTripId == currentTripId
