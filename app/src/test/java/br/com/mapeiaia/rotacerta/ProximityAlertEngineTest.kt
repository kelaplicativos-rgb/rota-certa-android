package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class ProximityAlertEngineTest {
    @Test
    fun doesNotConsumeImportedRadarCounterWhenSpeechFails() {
        var now = 100_000L
        val speech = FakeProximitySpeech(importedResult = false)
        val engine = ProximityAlertEngine(speech) { now }
        val radar = importedRadar("radar-1", Coordinate(0.0, 0.0005))

        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) {}
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) {}

        assertEquals(2, speech.importedCalls)

        speech.importedResult = true
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) {}
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) {}

        assertEquals(3, speech.importedCalls)
        now += 20_000L
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) {}

        assertEquals(3, speech.importedCalls)
    }

    @Test
    fun resetsImportedRadarCounterAfterLeavingAlertZone() {
        val speech = FakeProximitySpeech()
        val engine = ProximityAlertEngine(speech) { 100_000L }
        val radar = importedRadar("radar-1", Coordinate(0.0, 0.0005))

        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) {}
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) {}
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0045), settings()) {}
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) {}

        assertEquals(2, speech.importedCalls)
    }

    @Test
    fun speaksOnlyNearestImportedRadarInsideRadius() {
        val speech = FakeProximitySpeech()
        val engine = ProximityAlertEngine(speech) { 100_000L }
        val farther = importedRadar("farther", Coordinate(0.0, 0.0010))
        val nearer = importedRadar("nearer", Coordinate(0.0, 0.0004))

        engine.check(emptyList(), listOf(farther, nearer), Coordinate(0.0, 0.0), settings()) {}

        assertEquals(listOf("nearer"), speech.importedRadarIds)
    }

    @Test
    fun doesNotRepeatImportedRadarDuringSameApproachAfterRepeatGap() {
        var now = 100_000L
        val speech = FakeProximitySpeech()
        val engine = ProximityAlertEngine(speech) { now }
        val radar = importedRadar("radar-1", Coordinate(0.0, 0.0005))

        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) {}
        now += 25_000L
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) {}

        assertEquals(1, speech.importedCalls)
    }

    @Test
    fun importedRadarSpeaksAgainAfterLeavingAndApproachingAgain() {
        var now = 100_000L
        val speech = FakeProximitySpeech()
        val engine = ProximityAlertEngine(speech) { now }
        val radar = importedRadar("radar-1", Coordinate(0.0, 0.0005))

        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) {}
        now += 25_000L
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0045), settings()) {}
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) {}

        assertEquals(2, speech.importedCalls)
    }

    @Test
    fun savedPlaceAlertCanRepeatDuringSameApproach() {
        var now = 100_000L
        val speech = FakeProximitySpeech()
        val engine = ProximityAlertEngine(speech) { now }
        val alert = SavedPlace(
            id = "alert-1",
            name = "Alerta",
            type = SavedPlaceType.ProximityAlert,
            coordinate = Coordinate(0.0, 0.0005),
            alertDistanceMeters = 200,
        )

        engine.check(listOf(alert), emptyList(), Coordinate(0.0, 0.0), settings()) {}
        now += 25_000L
        engine.check(listOf(alert), emptyList(), Coordinate(0.0, 0.0), settings()) {}

        assertEquals(2, speech.proximityCalls)
    }

    private fun settings(): AppSettings = AppSettings(proximityAlertDistanceMeters = 200)

    private fun importedRadar(id: String, coordinate: Coordinate): ImportedRadar =
        ImportedRadar(id = id, coordinate = coordinate, type = 1)

    private class FakeProximitySpeech(
        var importedResult: Boolean = true,
    ) : ProximitySpeech {
        var importedCalls = 0
        var proximityCalls = 0
        val importedRadarIds = mutableListOf<String>()

        override fun speakImportedRadar(radar: ImportedRadar, distanceMeters: Double): Boolean {
            importedCalls += 1
            importedRadarIds += radar.id
            return importedResult
        }

        override fun speakProximityAlert(place: SavedPlace): Boolean {
            proximityCalls += 1
            return true
        }

        override fun proximityAlertSpeech(place: SavedPlace): String = place.name
    }
}
