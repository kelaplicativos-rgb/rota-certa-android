package br.com.mapeiaia.rotacerta.trips

/** Pure card-list decisions. WebView navigation remains in the activity. */
internal object BlaBlaCollectorCardModule {
    fun firstUnresolvedVisibleKey(
        visibleKeysInUiOrder: List<String>,
        resolvedKeys: Set<String>,
    ): String? = visibleKeysInUiOrder.firstOrNull { key ->
        key.isNotBlank() && key !in resolvedKeys
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
