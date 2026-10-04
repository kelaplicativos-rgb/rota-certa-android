package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals

class OperationalTimelineLoadingState0727Test {
    @Test
    fun emptyListsAreLoadingUntilInitialSnapshotCompletes0727() {
        assertEquals(
            OperationalTimelineContentState0727.LOADING,
            operationalTimelineContentState0727(
                initialLoadComplete = false,
                rowCount = 0,
            ),
        )
    }

    @Test
    fun canonicalEmptyIsOnlyShownAfterInitialSnapshotCompletes0727() {
        assertEquals(
            OperationalTimelineContentState0727.EMPTY,
            operationalTimelineContentState0727(
                initialLoadComplete = true,
                rowCount = 0,
            ),
        )
    }

    @Test
    fun contentIsRenderedAfterCompletedSnapshotWithRows0727() {
        assertEquals(
            OperationalTimelineContentState0727.CONTENT,
            operationalTimelineContentState0727(
                initialLoadComplete = true,
                rowCount = 57,
            ),
        )
    }

    @Test
    fun loadingStateWinsUntilSnapshotCompletionEvenIfRowsArePreseeded0727() {
        assertEquals(
            OperationalTimelineContentState0727.LOADING,
            operationalTimelineContentState0727(
                initialLoadComplete = false,
                rowCount = 57,
            ),
        )
    }
}
