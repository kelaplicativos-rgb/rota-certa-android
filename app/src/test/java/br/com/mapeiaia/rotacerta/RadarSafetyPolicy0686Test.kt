package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarSafetyPolicy0686Test {
    @Test
    fun `effective threshold grows with road speed but never shrinks configured radius`() {
        assertEquals(
            500,
            RadarSafetyPolicy0686.effectiveThresholdMeters(
                configuredThresholdMeters = 500,
                speedMetersPerSecond = 20.0,
                accuracyMeters = 6.0,
            ),
        )
        assertTrue(
            RadarSafetyPolicy0686.effectiveThresholdMeters(
                configuredThresholdMeters = 200,
                speedMetersPerSecond = 38.9,
                accuracyMeters = 6.0,
            ) >= 419,
        )
    }

    @Test
    fun `segment bridge catches radar passed between two outside fixes`() {
        val target = Coordinate(0.0, 0.0)
        assertTrue(
            RadarSafetyPolicy0686.segmentCrossesTarget(
                previous = Coordinate(0.0, -0.004),
                current = Coordinate(0.0, 0.004),
                target = target,
                thresholdMeters = 200.0,
            ),
        )
    }

    @Test
    fun `segment bridge does not fire when one endpoint is already inside zone`() {
        val target = Coordinate(0.0, 0.0)
        assertFalse(
            RadarSafetyPolicy0686.segmentCrossesTarget(
                previous = Coordinate(0.0, -0.001),
                current = Coordinate(0.0, 0.004),
                target = target,
                thresholdMeters = 200.0,
            ),
        )
    }

    @Test
    fun `shared persistence stays at five seconds while local GPS may be faster`() {
        assertTrue(RadarSafetyPolicy0686.shouldPersistSharedPoint(0L, 1_000L))
        assertFalse(RadarSafetyPolicy0686.shouldPersistSharedPoint(1_000L, 4_999L))
        assertTrue(RadarSafetyPolicy0686.shouldPersistSharedPoint(1_000L, 6_000L))
        assertEquals(1_000L, RadarSafetyPolicy0686.LOCAL_LOCATION_INTERVAL_MS)
        assertEquals(5_000L, RadarSafetyPolicy0686.SHARED_POINT_INTERVAL_MS)
    }
}
