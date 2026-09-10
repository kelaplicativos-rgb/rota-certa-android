package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PassengerTimelineSnapshotState0532Test {
    @Test
    fun `refresh preserves complete current snapshot when immediate seed is absent`() {
        assertEquals(
            "complete-old",
            preservePassengerTimelineSnapshotDuringRefresh0532(
                current = "complete-old",
                immediateCanonical = null,
            ),
        )
    }

    @Test
    fun `refresh preserves complete current snapshot instead of downgrading to immediate seed`() {
        assertEquals(
            "complete-old",
            preservePassengerTimelineSnapshotDuringRefresh0532(
                current = "complete-old",
                immediateCanonical = "immediate-partial",
            ),
        )
    }

    @Test
    fun `first render seeds from persisted canonical snapshot`() {
        assertEquals(
            "persisted-canonical",
            preservePassengerTimelineSnapshotDuringRefresh0532(
                current = null,
                immediateCanonical = "persisted-canonical",
            ),
        )
    }

    @Test
    fun `first render without any canonical snapshot stays absent instead of inventing loading content`() {
        assertNull(
            preservePassengerTimelineSnapshotDuringRefresh0532<String>(
                current = null,
                immediateCanonical = null,
            ),
        )
    }
}
