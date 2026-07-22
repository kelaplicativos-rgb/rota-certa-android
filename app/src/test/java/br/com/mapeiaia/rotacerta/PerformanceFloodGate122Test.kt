package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceFloodGate122Test {
    @Test
    fun discoveryEventsAreThrottledInsteadOfFloodingTheService() {
        val gate = AccessibilityEventFloodGate(
            fastDebounceMillis = 90L,
            discoveryDebounceMillis = 900L,
            windowDiscoveryDebounceMillis = 250L,
            candidateTtlMillis = 4_000L,
        )

        var accepted = 0
        repeat(100) { index ->
            val mode = gate.classify(
                packageName = "com.openai.chatgpt",
                eventType = AccessibilityEventFloodGate.TYPE_WINDOW_CONTENT_CHANGED,
                monitoredPackage = false,
                nowMillis = index * 10L,
            )
            if (mode != AccessibilityEventMode.Ignore) accepted += 1
        }

        assertEquals(2, accepted)
    }

    @Test
    fun monitoredPackageKeepsFastResponseButCoalescesDuplicates() {
        val gate = AccessibilityEventFloodGate()
        val first = gate.classify(
            packageName = "sinet.startup.indriver",
            eventType = AccessibilityEventFloodGate.TYPE_WINDOW_CONTENT_CHANGED,
            monitoredPackage = true,
            nowMillis = 1_000L,
        )
        val duplicate = gate.classify(
            packageName = "sinet.startup.indriver",
            eventType = AccessibilityEventFloodGate.TYPE_WINDOW_CONTENT_CHANGED,
            monitoredPackage = true,
            nowMillis = 1_030L,
        )
        val next = gate.classify(
            packageName = "sinet.startup.indriver",
            eventType = AccessibilityEventFloodGate.TYPE_WINDOW_CONTENT_CHANGED,
            monitoredPackage = true,
            nowMillis = 1_100L,
        )

        assertEquals(AccessibilityEventMode.Fast, first)
        assertEquals(AccessibilityEventMode.Ignore, duplicate)
        assertEquals(AccessibilityEventMode.Fast, next)
    }

    @Test
    fun discoveredCandidateTemporarilyEntersFastPath() {
        val gate = AccessibilityEventFloodGate(candidateTtlMillis = 4_000L)
        gate.markCandidate("com.example.overlay", nowMillis = 2_000L)

        assertTrue(gate.isCandidate("com.example.overlay", nowMillis = 5_999L))
        assertFalse(gate.isCandidate("com.example.overlay", nowMillis = 6_001L))
    }

    @Test
    fun radarIndexReturnsOnlyNearbyCellsFromLargeDatabase() {
        val farRadars = (0 until 18_000).map { index ->
            ImportedRadar(
                id = "far-$index",
                coordinate = Coordinate(
                    latitude = -30.0 + (index % 300) * 0.02,
                    longitude = -60.0 + (index / 300) * 0.02,
                ),
                type = 1,
            )
        }
        val target = ImportedRadar(
            id = "target",
            coordinate = Coordinate(-23.6000, -46.4800),
            type = 1,
        )
        val source = farRadars + target
        val index = ImportedRadarSpatialIndex()

        val first = index.query(
            source = source,
            center = Coordinate(-23.6003, -46.4802),
            radiusMeters = 700.0,
        )
        val second = index.query(
            source = source,
            center = Coordinate(-23.6003, -46.4802),
            radiusMeters = 700.0,
        )

        assertTrue(first.rebuilt)
        assertFalse(second.rebuilt)
        assertTrue(first.radars.any { it.id == "target" })
        assertTrue(first.radars.size < 100)
    }
}
