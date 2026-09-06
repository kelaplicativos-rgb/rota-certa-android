package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DirectionalRadarDismiss0178Test {
    @Test
    fun closeDoesNotReopenUntilExitAndWorksAgainOnNextApproach() {
        val speech = FakeSpeech()
        val engine = DirectionalProximityAlertEngine(speech) { NOW }
        val radar = ImportedRadar(
            id = "radar-east",
            coordinate = Coordinate(0.0, 0.0),
            type = 1,
            speedKmh = 80,
            directionType = 1,
            direction = 90,
        )
        var visual: DirectionalAlertVisual? = null

        engine.check(emptyList(), listOf(radar), fix(-0.0018), settings(), { visual = it })
        engine.check(emptyList(), listOf(radar), fix(-0.0014), settings(), { visual = it })
        engine.check(emptyList(), listOf(radar), fix(-0.0010), settings(), { visual = it })
        val targetId = requireNotNull(visual).targetId
        assertNotNull(visual)
        assertEquals(1, speech.calls)

        engine.dismissUntilExit(targetId)
        engine.check(emptyList(), listOf(radar), fix(-0.0009), settings(), { visual = it })
        assertNull(visual)
        assertEquals(1, speech.calls)

        engine.check(emptyList(), listOf(radar), fix(-0.0040), settings(), { visual = it })
        engine.check(emptyList(), listOf(radar), fix(-0.0018), settings(), { visual = it })
        engine.check(emptyList(), listOf(radar), fix(-0.0014), settings(), { visual = it })
        engine.check(emptyList(), listOf(radar), fix(-0.0010), settings(), { visual = it })
        assertNotNull(visual)
        assertEquals(2, speech.calls)
    }

    private fun fix(longitude: Double): PreciseNavigationFix = PreciseNavigationFix(
        coordinate = Coordinate(0.0, longitude),
        accuracyMeters = 6.0,
        speedMetersPerSecond = 12.0,
        headingDegrees = 90.0,
        headingSource = NavigationHeadingSource.GpsAndCompass,
        timestampMillis = NOW,
        provider = "gps",
    )

    private fun settings(): AppSettings = AppSettings(
        appEnabled = true,
        proximityAlertsEnabled = true,
        proximityAlertDistanceMeters = 200,
    )

    private class FakeSpeech : ProximitySpeech {
        var calls = 0
        override fun speakImportedRadar(radar: ImportedRadar, distanceMeters: Double): Boolean {
            calls += 1
            return true
        }
        override fun speakProximityAlert(place: SavedPlace): Boolean = true
        override fun proximityAlertSpeech(place: SavedPlace): String = place.name
    }

    private companion object {
        const val NOW = 100_000L
    }
}
