package br.com.mapeiaia.rotacerta.trips

/**
 * 0.1.564 — pure contracts for the multi-account operational browser.
 *
 * These helpers deliberately contain no WebView state. They make the visibility and
 * navigation decisions independently testable and prevent the UI from silently
 * dropping a trip or claiming success before the final BlaBlaCar destination is known.
 */
internal enum class OperationalTripDecisionReason0564 {
    PROFILE_UUID_MISSING,
    ACCOUNT_NOT_CONNECTED,
    IDENTITY_CONFLICT,
    TRIP_ID_MISSING,
    TRIP_HREF_MISSING,
    TARGET_UNRESOLVED,
}

internal data class OperationalTripDecision0564(
    val entry: TripTimelineEntry,
    val included: Boolean,
    val reason: OperationalTripDecisionReason0564? = null,
)

internal data class OperationalTripSelection0564(
    val includedEntries: List<TripTimelineEntry>,
    val decisions: List<OperationalTripDecision0564>,
)

/**
 * Preserves the 0.1.563 visible-list contract: only trips belonging to a currently
 * connected profile enter the list. Entries that are visible but cannot be opened are
 * retained and receive an explicit reason so the UI/report can fail closed visibly.
 */
internal fun operationalConnectedSelection0564(
    entries: List<TripTimelineEntry>,
    accounts: List<BlaBlaDynamicAccount>,
): OperationalTripSelection0564 {
    val connectedProfiles = accounts
        .mapNotNull { account -> account.profileUuid?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
        .toSet()

    val decisions = entries.map { entry ->
        val profile = entry.blablaProfileUuid?.trim()?.lowercase().orEmpty()
        when {
            profile.isBlank() -> OperationalTripDecision0564(
                entry = entry,
                included = false,
                reason = OperationalTripDecisionReason0564.PROFILE_UUID_MISSING,
            )
            profile !in connectedProfiles -> OperationalTripDecision0564(
                entry = entry,
                included = false,
                reason = OperationalTripDecisionReason0564.ACCOUNT_NOT_CONNECTED,
            )
            TripTimelineIssue.EXTERNAL_IDENTITY_CONFLICT in entry.issues -> OperationalTripDecision0564(
                entry = entry,
                included = true,
                reason = OperationalTripDecisionReason0564.IDENTITY_CONFLICT,
            )
            entry.blablaTripId.isNullOrBlank() -> OperationalTripDecision0564(
                entry = entry,
                included = true,
                reason = OperationalTripDecisionReason0564.TRIP_ID_MISSING,
            )
            entry.blablaTripHref.isNullOrBlank() -> OperationalTripDecision0564(
                entry = entry,
                included = true,
                reason = OperationalTripDecisionReason0564.TRIP_HREF_MISSING,
            )
            else -> OperationalTripDecision0564(entry = entry, included = true)
        }
    }

    val included = decisions
        .asSequence()
        .filter(OperationalTripDecision0564::included)
        .map(OperationalTripDecision0564::entry)
        .sortedWith(
            compareBy<TripTimelineEntry> { it.departureAtMillis }
                .thenBy { it.blablaProfileUuid.orEmpty().lowercase() }
                .thenBy { it.blablaTripId.orEmpty() }
                .thenBy { it.tripId },
        )
        .toList()

    return OperationalTripSelection0564(
        includedEntries = included,
        decisions = decisions,
    )
}

internal fun operationalTripDecisionDiagnosticKey0564(entry: TripTimelineEntry): String =
    sha256TripPublication0387(
        listOf(
            entry.blablaProfileUuid.orEmpty().trim().lowercase(),
            entry.blablaTripId.orEmpty().trim(),
            entry.tripId,
        ).joinToString("|"),
    ).take(16)

internal enum class OperationalBrowserNavigationResult0564 {
    CONFIRMED,
    REDIRECTED,
    AUTH_REQUIRED,
    IDENTITY_MISMATCH,
    INVALID_DESTINATION,
    UNVERIFIED,
}

/**
 * Pure final-destination classifier. CONFIRMED is intentionally possible only when the
 * final administrative URL resolves to the exact trip requested and the live account
 * identity still matches the expected profile.
 */
internal fun operationalBrowserNavigationResult0564(
    requestedTripId: String,
    finalTripId: String,
    profileMatches: Boolean,
    authRequired: Boolean,
    finalDestinationAllowed: Boolean,
): OperationalBrowserNavigationResult0564 = when {
    !profileMatches -> OperationalBrowserNavigationResult0564.IDENTITY_MISMATCH
    authRequired -> OperationalBrowserNavigationResult0564.AUTH_REQUIRED
    !finalDestinationAllowed -> OperationalBrowserNavigationResult0564.INVALID_DESTINATION
    requestedTripId.isBlank() -> OperationalBrowserNavigationResult0564.UNVERIFIED
    finalTripId.isBlank() -> OperationalBrowserNavigationResult0564.REDIRECTED
    finalTripId != requestedTripId -> OperationalBrowserNavigationResult0564.REDIRECTED
    else -> OperationalBrowserNavigationResult0564.CONFIRMED
}

/**
 * 0.1.566 — temporal partition used by Todas as viagens.
 *
 * A departure remains active at the exact departure instant and becomes archived only
 * after its departure timestamp is strictly in the past. Active items are chronological;
 * archived items are most-recent-first so the latest completed departure is easiest to find.
 */
internal data class OperationalArchiveSelection0566<T>(
    val active: List<T>,
    val archived: List<T>,
)

internal fun <T> operationalArchiveSelection0566(
    items: List<T>,
    nowMillis: Long,
    departureAtMillis: (T) -> Long,
): OperationalArchiveSelection0566<T> {
    val active = items
        .asSequence()
        .filter { item -> departureAtMillis(item) >= nowMillis }
        .sortedBy(departureAtMillis)
        .toList()
    val archived = items
        .asSequence()
        .filter { item -> departureAtMillis(item) < nowMillis }
        .sortedByDescending(departureAtMillis)
        .toList()
    return OperationalArchiveSelection0566(
        active = active,
        archived = archived,
    )
}
