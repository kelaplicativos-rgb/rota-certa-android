package br.com.mapeiaia.rotacerta.trips

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
