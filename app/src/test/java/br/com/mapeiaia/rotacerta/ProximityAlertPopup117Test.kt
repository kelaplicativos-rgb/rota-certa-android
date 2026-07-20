package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class ProximityAlertPopup117Test {
    @Test
    fun popupAppearsOncePerApproachAndRearmsAfterExit() {
        var now = 100_000L
        val engine = ProximityAlertEngine(
            speechEngine = object : ProximitySpeech {
                override fun speakImportedRadar(radar: ImportedRadar, distanceMeters: Double): Boolean = true
                override fun speakProximityAlert(place: SavedPlace): Boolean = true
                override fun proximityAlertSpeech(place: SavedPlace): String = place.name
            },
            nowProvider = { now },
        )
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

        // Primeiro carregamento ja dentro da zona: aguarda sair, sem popup.
        engine.check(
            alerts = listOf(alert),
            radars = emptyList(),
            coordinate = Coordinate(0.0, 0.0),
            settings = settings,
            onSavedPlacePopup = onPopup,
        ) {}
        assertEquals(0, popupCount)

        // Sai da zona e entra novamente: um popup.
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

        // Continua dentro: nao repete o popup, mesmo depois do intervalo da voz.
        now += 25_000L
        engine.check(
            alerts = listOf(alert),
            radars = emptyList(),
            coordinate = Coordinate(0.0, 0.0),
            settings = settings,
            onSavedPlacePopup = onPopup,
        ) {}
        assertEquals(1, popupCount)

        // Sai e reentra em uma nova aproximacao: popup rearmado.
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
}
