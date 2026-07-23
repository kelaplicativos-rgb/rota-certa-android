package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionalProximityAlertEngineChecklist5Test {
    @Test
    fun `radar a frente mostra contagem e fala uma vez`() {
        val speech = FakeSpeech()
        val engine = DirectionalProximityAlertEngine(speech) { NOW }
        val radar = eastboundRadar()
        var visual: DirectionalAlertVisual? = null

        engine.check(emptyList(), listOf(radar), fix(longitude = -0.0015, heading = 90.0), settings(), { visual = it })
        engine.check(emptyList(), listOf(radar), fix(longitude = -0.0010, heading = 90.0), settings(), { visual = it })

        assertNotNull(visual)
        assertTrue(requireNotNull(visual).status.contains("sentido confirmado"))
        assertFalse(requireNotNull(visual).shouldClose)
        assertEquals(1, speech.radarCalls)
    }

    @Test
    fun `radar no sentido contrario nao aparece nem fala`() {
        val speech = FakeSpeech()
        val engine = DirectionalProximityAlertEngine(speech) { NOW }
        val radar = eastboundRadar()
        var visual: DirectionalAlertVisual? = null

        engine.check(emptyList(), listOf(radar), fix(longitude = 0.0015, heading = 270.0), settings(), { visual = it })
        engine.check(emptyList(), listOf(radar), fix(longitude = 0.0010, heading = 270.0), settings(), { visual = it })

        assertEquals(null, visual)
        assertEquals(0, speech.radarCalls)
    }

    @Test
    fun `painel pede fechamento depois de ultrapassar radar`() {
        val speech = FakeSpeech()
        val engine = DirectionalProximityAlertEngine(speech) { NOW }
        val radar = eastboundRadar()
        val visuals = mutableListOf<DirectionalAlertVisual?>()

        listOf(-0.0015, -0.0010, -0.0002, 0.0001, 0.0004, 0.0007).forEach { longitude ->
            engine.check(
                alerts = emptyList(),
                radars = listOf(radar),
                fix = fix(longitude = longitude, heading = 90.0),
                settings = settings(),
                onVisual = visuals::add,
            )
        }

        assertTrue(visuals.filterNotNull().any { it.shouldClose && it.status.contains("ultrapassado") })
    }

    private fun eastboundRadar(): ImportedRadar = ImportedRadar(
        id = "radar-east",
        coordinate = Coordinate(0.0, 0.0),
        type = 1,
        speedKmh = 50,
        directionType = 1,
        direction = 90,
    )

    private fun fix(longitude: Double, heading: Double): PreciseNavigationFix = PreciseNavigationFix(
        coordinate = Coordinate(0.0, longitude),
        accuracyMeters = 6.0,
        speedMetersPerSecond = 12.0,
        headingDegrees = heading,
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
        var radarCalls = 0
        override fun speakImportedRadar(radar: ImportedRadar, distanceMeters: Double): Boolean {
            radarCalls += 1
            return true
        }

        override fun speakProximityAlert(place: SavedPlace): Boolean = true
        override fun proximityAlertSpeech(place: SavedPlace): String = place.name
    }

    private companion object {
        const val NOW = 100_000L
    }
}
