package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class ProximityAlertPopup117Test {
    @Test
    fun popupAppearsOncePerApproachAndRearmsAfterExit() {
        var now = 100_000L
        val speech = CountingSpeech()
        val engine = ProximityAlertEngine(speechEngine = speech, nowProvider = { now })
        val alert = SavedPlace(
            id = "alert-popup-1",
            name = "Buraco",
            type = SavedPlaceType.ProximityAlert,
            coordinate = Coordinate(0.0, 0.0005),
            alertDistanceMeters = 200,
        )
        val settings = AppSettings(proximityAlertDistanceMeters = 200)
        var popupCount = 0
        val onPopup: (SavedPlace, Double) -> Unit = { place, _ ->
            assertEquals("Buraco", place.name)
            popupCount += 1
        }

        engine.check(
            alerts = listOf(alert),
            radars = emptyList(),
            coordinate = Coordinate(0.0, 0.0),
            settings = settings,
            onSavedPlacePopup = onPopup,
        ) {}
        assertEquals(0, popupCount)

        engine.check(
            alerts = listOf(alert),
            radars = emptyList(),
            coordinate = Coordinate(0.0, 0.0045),
            settings = settings,
            onSavedPlacePopup = onPopup,
        ) {}
        now += 25_000L
        engine.check(
            alerts = listOf(alert),
            radars = emptyList(),
            coordinate = Coordinate(0.0, 0.0),
            settings = settings,
            onSavedPlacePopup = onPopup,
        ) {}
        assertEquals(1, popupCount)

        now += 25_000L
        engine.check(
            alerts = listOf(alert),
            radars = emptyList(),
            coordinate = Coordinate(0.0, 0.0),
            settings = settings,
            onSavedPlacePopup = onPopup,
        ) {}
        assertEquals(1, popupCount)

        engine.check(
            alerts = listOf(alert),
            radars = emptyList(),
            coordinate = Coordinate(0.0, 0.0045),
            settings = settings,
            onSavedPlacePopup = onPopup,
        ) {}
        now += 25_000L
        engine.check(
            alerts = listOf(alert),
            radars = emptyList(),
            coordinate = Coordinate(0.0, 0.0),
            settings = settings,
            onSavedPlacePopup = onPopup,
        ) {}
        assertEquals(2, popupCount)
    }

    @Test
    fun savedLocalNeverSpeaksOrShowsProximityPopup() {
        val speech = CountingSpeech()
        val engine = ProximityAlertEngine(speechEngine = speech, nowProvider = { 100_000L })
        val parking = SavedPlace(
            id = "parking-1",
            name = "Estacionamento",
            type = SavedPlaceType.Place,
            coordinate = Coordinate(0.0, 0.0005),
        )
        val settings = AppSettings(proximityAlertDistanceMeters = 200)
        var popupCount = 0

        engine.check(
            alerts = listOf(parking),
            radars = emptyList(),
            coordinate = Coordinate(0.0, 0.0045),
            settings = settings,
            onSavedPlacePopup = { _, _ -> popupCount += 1 },
        ) {}
        engine.check(
            alerts = listOf(parking),
            radars = emptyList(),
            coordinate = Coordinate(0.0, 0.0),
            settings = settings,
            onSavedPlacePopup = { _, _ -> popupCount += 1 },
        ) {}

        assertEquals(0, popupCount)
        assertEquals(0, speech.proximityCalls)
    }

    private class CountingSpeech : ProximitySpeech {
        var proximityCalls = 0

        override fun speakImportedRadar(radar: ImportedRadar, distanceMeters: Double): Boolean = true

        override fun speakProximityAlert(place: SavedPlace): Boolean {
            proximityCalls += 1
            return true
        }

        override fun proximityAlertSpeech(place: SavedPlace): String = place.name
    }
}
