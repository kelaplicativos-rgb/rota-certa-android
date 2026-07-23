package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityAlertAutoClose128Test {
    @Test
    fun `popup atualiza distancia e fecha depois de passar`() {
        var now = 1_000L
        val engine = ProximityAlertEngine(FakeSpeech(), nowProvider = { now })
        val alert = SavedPlace(
            id = "a1",
            name = "Buraco",
            type = SavedPlaceType.ProximityAlert,
            coordinate = Coordinate(0.0, 0.0),
        )
        val settings = AppSettings(
            proximityAlertDistanceMeters = 500,
            proximityPopupAutoCloseEnabled = true,
        )
        val states = mutableListOf<ProximityAlertPopupState>()
        fun check(latitude: Double) {
            now += 2_100L
            engine.check(
                alerts = listOf(alert),
                radars = emptyList(),
                coordinate = Coordinate(latitude, 0.0),
                settings = settings,
                onSavedPlacePopupState = states::add,
                onDiagnostic = {},
            )
        }

        check(0.0060) // fora da zona e armado
        check(0.0040)
        check(0.0020)
        check(0.0001)
        check(0.0003)
        check(0.0005)

        assertTrue(states.any { it is ProximityAlertPopupState.Visible })
        assertTrue(states.any { it is ProximityAlertPopupState.Hidden && it.reason == "ponto_ultrapassado" })
    }

    private class FakeSpeech : ProximitySpeech {
        override fun speakImportedRadar(radar: ImportedRadar, distanceMeters: Double) = false
        override fun speakProximityAlert(place: SavedPlace) = false
        override fun proximityAlertSpeech(place: SavedPlace) = place.name
    }
}
