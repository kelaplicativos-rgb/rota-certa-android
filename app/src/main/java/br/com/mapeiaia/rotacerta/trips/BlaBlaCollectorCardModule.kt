package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate

internal enum class BlaBlaTodayScopeStopDecision0660 {
    CONTINUE,
    COMPLETE_TARGET_RANGE,
    COMPLETE_TARGET_ABSENT,
    FAIL_DATE_EVIDENCE,
}

/** Pure card-list decisions. WebView navigation remains in the activity. */
internal object BlaBlaCollectorCardModule {
    fun firstUnresolvedVisibleKey(
        visibleKeysInUiOrder: List<String>,
        resolvedKeys: Set<String>,
    ): String? = visibleKeysInUiOrder.firstOrNull { key ->
        key.isNotBlank() && key !in resolvedKeys
    }

    fun candidateDate(
        candidate: BlaBlaDomRideCandidate,
        today: LocalDate = LocalDate.now(),
    ): LocalDate? = BlaBlaDomNormalizer.parseDate(
        listOf(candidate.dateText, candidate.text).joinToString(" | "),
        today,
    )

    fun candidatesOnDate(
        candidates: List<BlaBlaDomRideCandidate>,
        targetDate: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): List<BlaBlaDomRideCandidate> = candidatesOnDates(candidates, setOf(targetDate), today)

    fun candidatesOnDates(
        candidates: List<BlaBlaDomRideCandidate>,
        targetDates: Collection<LocalDate>,
        today: LocalDate = LocalDate.now(),
    ): List<BlaBlaDomRideCandidate> {
        val scope = targetDates.toSet()
        if (scope.isEmpty()) return emptyList()
        return candidates.filter { candidate -> candidateDate(candidate, today) in scope }
    }

    /**
     * TODAY_ONLY is intentionally bounded: once the chronological /rides list proves that
     * the requested day has ended (or that the first current/future date is already after it),
     * the collector must stop instead of walking the rest of the account.
     *
     * Unreadable or non-monotonic date evidence fails closed. The caller must not keep scrolling
     * because doing so would silently turn TODAY_ONLY back into a full-account traversal.
     */
    fun todayScopeStopDecision0660(
        candidates: List<BlaBlaDomRideCandidate>,
        targetDate: LocalDate,
        targetObservedEarlier: Boolean,
        today: LocalDate = LocalDate.now(),
    ): BlaBlaTodayScopeStopDecision0660 {
        if (candidates.isEmpty()) return BlaBlaTodayScopeStopDecision0660.CONTINUE
        val parsed = candidates.map { candidateDate(it, today) }
        if (parsed.any { it == null }) return BlaBlaTodayScopeStopDecision0660.FAIL_DATE_EVIDENCE
        val dates = parsed.filterNotNull()
        if (dates.isEmpty()) return BlaBlaTodayScopeStopDecision0660.FAIL_DATE_EVIDENCE

        val nonDecreasing = dates.zipWithNext().all { (left, right) -> !right.isBefore(left) }
        val nonIncreasing = dates.zipWithNext().all { (left, right) -> !right.isAfter(left) }
        if (!nonDecreasing && !nonIncreasing) return BlaBlaTodayScopeStopDecision0660.FAIL_DATE_EVIDENCE

        val targetObserved = targetObservedEarlier || targetDate in dates
        if (targetObserved) {
            val lastTargetIndex = dates.indexOfLast { it == targetDate }
            val tail = if (lastTargetIndex >= 0) dates.drop(lastTargetIndex + 1) else dates
            val crossedAscending = nonDecreasing && tail.any { it.isAfter(targetDate) } && tail.none { it.isBefore(targetDate) }
            val strictlyDescending = dates.size >= 2 && dates.last().isBefore(dates.first())
            val crossedDescending = strictlyDescending && nonIncreasing && tail.any { it.isBefore(targetDate) } && tail.none { it.isAfter(targetDate) }
            return if (crossedAscending || crossedDescending) {
                BlaBlaTodayScopeStopDecision0660.COMPLETE_TARGET_RANGE
            } else {
                BlaBlaTodayScopeStopDecision0660.CONTINUE
            }
        }

        // /rides presents current/future rides chronologically. If its first proven date is
        // already after the requested day, that day is absent and no scrolling is justified.
        if (nonDecreasing && dates.first().isAfter(targetDate)) {
            return BlaBlaTodayScopeStopDecision0660.COMPLETE_TARGET_ABSENT
        }
        val strictlyDescending = dates.size >= 2 && dates.last().isBefore(dates.first())
        if (strictlyDescending && nonIncreasing && dates.first().isBefore(targetDate)) {
            return BlaBlaTodayScopeStopDecision0660.COMPLETE_TARGET_ABSENT
        }
        return BlaBlaTodayScopeStopDecision0660.CONTINUE
    }

    fun canAdvance(currentCardComplete: Boolean, currentCardQuarantined: Boolean): Boolean =
        currentCardComplete || currentCardQuarantined

    fun shouldScrollForMore(
        unresolvedVisibleCardExists: Boolean,
        atBottom: Boolean,
    ): Boolean = !unresolvedVisibleCardExists && !atBottom

    /** A zero-card result may delete old cards only with explicit page evidence. */
    fun emptyListIsAuthoritative(explicitEmptyList: Boolean): Boolean = explicitEmptyList
}

/** Compatibility entry points kept for existing Stage47 regression tests. */
internal fun blaBlaFirstUncompletedVisibleKey(
    visibleKeysInUiOrder: List<String>,
    resolvedKeys: Set<String>,
): String? = BlaBlaCollectorCardModule.firstUnresolvedVisibleKey(visibleKeysInUiOrder, resolvedKeys)

internal fun blaBlaCanAdvanceToNextCard(currentCardComplete: Boolean, currentCardQuarantined: Boolean): Boolean =
    BlaBlaCollectorCardModule.canAdvance(currentCardComplete, currentCardQuarantined)

internal fun blaBlaShouldScrollForMore(
    unresolvedVisibleCardExists: Boolean,
    atBottom: Boolean,
): Boolean = BlaBlaCollectorCardModule.shouldScrollForMore(unresolvedVisibleCardExists, atBottom)
