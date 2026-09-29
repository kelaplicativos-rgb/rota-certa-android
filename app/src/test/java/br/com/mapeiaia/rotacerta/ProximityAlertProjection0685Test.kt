package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ProximityAlertProjection0685Test {
    @Test
    fun `popup is fixed by default and only supported timeout values are accepted`() {
        assertEquals(0L, ProximityAlertProjection0685.popupTimeoutMillis(AppSettings()))
        assertEquals(
            15_000L,
            ProximityAlertProjection0685.popupTimeoutMillis(AppSettings(proximityPopupTimeoutSeconds = 15)),
        )
        assertEquals(
            20_000L,
            ProximityAlertProjection0685.popupTimeoutMillis(AppSettings(proximityPopupTimeoutSeconds = 20)),
        )
        assertEquals(
            30_000L,
            ProximityAlertProjection0685.popupTimeoutMillis(AppSettings(proximityPopupTimeoutSeconds = 30)),
        )
        assertEquals(
            0L,
            ProximityAlertProjection0685.popupTimeoutMillis(AppSettings(proximityPopupTimeoutSeconds = 99)),
        )
    }

    @Test
    fun `shared core fix updates do not clear pinned visual`() {
        ProximityAlertProjection0685.clearAll()
        val firstFix = fix(longitude = -46.50)
        val secondFix = fix(longitude = -46.49)
        val visual = visual("radar-123")

        ProximityAlertProjection0685.publishVisual(
            visual = visual,
            fix = firstFix,
            popupTimeoutMillis = 0L,
        )
        ProximityAlertProjection0685.publishFix(secondFix)

        val state = ProximityAlertProjection0685.state.value
        assertSame(visual, state.visual)
        assertEquals(secondFix, state.latestFix)
        assertEquals(0L, state.popupTimeoutMillis)
    }

    @Test
    fun `only acknowledgement for active target clears popup`() {
        ProximityAlertProjection0685.clearAll()
        val visual = visual("radar-active")
        ProximityAlertProjection0685.publishVisual(visual, fix(-46.50), 20_000L)

        ProximityAlertProjection0685.clearVisual("radar-other")
        assertSame(visual, ProximityAlertProjection0685.state.value.visual)

        ProximityAlertProjection0685.clearVisual("radar-active")
        assertNull(ProximityAlertProjection0685.state.value.visual)
        assertEquals(0L, ProximityAlertProjection0685.state.value.popupTimeoutMillis)
    }

    private fun fix(longitude: Double) = PreciseNavigationFix(
        coordinate = Coordinate(-23.55, longitude),
        accuracyMeters = 6.0,
        speedMetersPerSecond = 12.0,
        headingDegrees = 90.0,
        headingSource = NavigationHeadingSource.GpsBearing,
        timestampMillis = 100_000L,
        provider = "gps",
    )

    private fun visual(targetId: String) = DirectionalAlertVisual(
        targetId = targetId,
        kind = DirectionalAlertKind.ImportedRadar,
        title = "Radar 50",
        distanceMeters = 180.0,
        thresholdMeters = 500,
        accuracyMeters = 6.0,
        speedKilometersPerHour = 43.2,
        headingSource = NavigationHeadingSource.GpsBearing,
        status = "Aproximando",
        gpsReliable = true,
        shouldClose = false,
        radarId = "123",
        speedLimitKmh = 50,
    )
}
