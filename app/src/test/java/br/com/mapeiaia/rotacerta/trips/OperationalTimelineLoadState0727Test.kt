package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals

class OperationalTimelineLoadState0727Test {
    @Test
    fun emptyRowsBeforeSnapshotAreLoadingNotEmpty0727() {
        assertEquals(
            OperationalTimelineLoadState0727.LOADING,
            operationalTimelineLoadState0727(localSnapshotLoaded = false, rowCount = 0),
        )
    }

    @Test
    fun emptyRowsAfterSnapshotAreTrueCanonicalEmpty0727() {
        assertEquals(
            OperationalTimelineLoadState0727.EMPTY,
            operationalTimelineLoadState0727(localSnapshotLoaded = true, rowCount = 0),
        )
    }

    @Test
    fun rowsAfterSnapshotAreContent0727() {
        assertEquals(
            OperationalTimelineLoadState0727.CONTENT,
            operationalTimelineLoadState0727(localSnapshotLoaded = true, rowCount = 57),
        )
    }

    @Test
    fun rowsCannotPromoteFirstFrameBeforeSnapshotCompletes0727() {
        assertEquals(
            OperationalTimelineLoadState0727.LOADING,
            operationalTimelineLoadState0727(localSnapshotLoaded = false, rowCount = 57),
        )
    }
}
