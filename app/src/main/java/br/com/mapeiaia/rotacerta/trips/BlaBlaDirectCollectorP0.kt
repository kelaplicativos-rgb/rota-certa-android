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
    rosterComplete && passengerCount == 0 -> BlaBlaDirectRosterState.COMPLETE_EMPTY
    rosterComplete && passengerCount > 0 -> BlaBlaDirectRosterState.COMPLETE_WITH_PASSENGERS
    else -> BlaBlaDirectRosterState.UNKNOWN
}

/**
 * Accepts a rendered roster without depending on one brittle data-testid. A
 * positive roster needs two identical terminal observations; an unmarked empty
 * roster needs three. A visible expansion control always keeps the result open.
 */
internal fun blaBlaDirectRosterCompleteAfterStableProbe(
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

internal enum class BlaBlaDirectPassengerStep {
    RESERVATION_URL,
    PASSENGER_CARD,
    SKIP,
    FINISH,
}

/**
 * The authenticated collector follows the same order the driver sees on the trip
 * page. When the passenger row is available we click that row first, even if a
 * reservation href was already discovered. A direct href is only a fallback for
 * layouts where the visible passenger row cannot be addressed deterministically.
 */
internal fun blaBlaDirectPassengerStep(
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

/** First unresolved card in the exact order currently exposed by the UI. */
internal fun blaBlaFirstUncompletedVisibleKey(
    visibleKeysInUiOrder: List<String>,
    resolvedKeys: Set<String>,
): String? = visibleKeysInUiOrder.firstOrNull { key ->
    key.isNotBlank() && key !in resolvedKeys
}

/**
 * A following card is legal only after the current card reached a terminal
 * result. Quarantined cards are not published, but they must not starve later
 * cards in the driver's visual order.
 */
internal fun blaBlaCanAdvanceToNextCard(currentCardComplete: Boolean, currentCardQuarantined: Boolean): Boolean =
    currentCardComplete || currentCardQuarantined

/** More list content is requested only after every visible card is already done. */
internal fun blaBlaShouldScrollForMore(
    unresolvedVisibleCardExists: Boolean,
    atBottom: Boolean,
): Boolean = !unresolvedVisibleCardExists && !atBottom

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
