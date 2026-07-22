package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RotaCerta121RegressionTest {
    @Test
    fun indrivePickupStartingWithPassagemActivatesTwoAddressTrigger() {
        val snapshot = """
            Pedido de viagem
            Embarque
            Passagem Santa Helena, 55
            Destino
            Rua das Flores, 120
            Aceitar por R$ 28,00
        """.trimIndent()

        val addresses = UniversalScreenAddressParser.findAddresses(snapshot)
        val trigger = UniversalAddressTrigger.evaluate(snapshot)

        assertEquals(listOf("Passagem Santa Helena, 55", "Rua das Flores, 120"), addresses)
        assertTrue(trigger.active)
        assertEquals("Passagem Santa Helena, 55", trigger.pickup)
        assertEquals("Rua das Flores, 120", trigger.destination)
    }

    @Test
    fun importedRadarSignalFiresOnceAndRearmsAfterExit() {
        val speech = RecordingProximitySpeech()
        var now = 1_000L
        val engine = ProximityAlertEngine(speechEngine = speech, nowProvider = { now })
        val radar = ImportedRadar(
            id = "radar-1",
            coordinate = Coordinate(0.0, 0.0),
            type = 1,
            speedKmh = 50,
        )
        val settings = AppSettings(proximityAlertDistanceMeters = 500)
        var signals = 0

        fun check(coordinate: Coordinate) {
            engine.check(
                alerts = emptyList(),
                radars = listOf(radar),
                coordinate = coordinate,
                settings = settings,
                onImportedRadarDetected = { _, _ -> signals += 1 },
                onDiagnostic = {},
            )
            now += 2_000L
        }

        check(Coordinate(0.0, 0.0040))
        check(Coordinate(0.0, 0.0035))
        assertEquals(1, signals)

        check(Coordinate(0.0, 0.0070))
        check(Coordinate(0.0, 0.0040))
        assertEquals(2, signals)
    }

    @Test
    fun workSummaryFiltersDayAndAccumulatesRouteDistance() {
        val points = listOf(
            WorkTrackPoint(Coordinate(-23.5500, -46.6330), 900L),
            WorkTrackPoint(Coordinate(-23.5500, -46.6330), 1_100L),
            WorkTrackPoint(Coordinate(-23.5510, -46.6330), 2_100L),
            WorkTrackPoint(Coordinate(-23.5520, -46.6330), 3_100L),
            WorkTrackPoint(Coordinate(-23.5530, -46.6330), 10_100L),
        )

        val summary = buildWorkTrackingSummary(points, 1_000L, 10_000L)

        assertEquals(3, summary.points.size)
        assertEquals(1_100L, summary.startedAtMillis)
        assertEquals(3_100L, summary.endedAtMillis)
        assertTrue(summary.distanceMeters in 210.0..230.0)
    }

    @Test
    fun longGpsGapDoesNotCreateFalseStraightLine() {
        val points = listOf(
            WorkTrackPoint(Coordinate(-23.5500, -46.6330), 1_000L),
            WorkTrackPoint(Coordinate(-22.9000, -43.2000), 700_001L),
        )

        val summary = buildWorkTrackingSummary(points, 0L, 1_000_000L)

        assertEquals(0.0, summary.distanceMeters, 0.0)
    }

    private class RecordingProximitySpeech : ProximitySpeech {
        override fun speakImportedRadar(radar: ImportedRadar, distanceMeters: Double): Boolean = true
        override fun speakProximityAlert(place: SavedPlace): Boolean = true
        override fun proximityAlertSpeech(place: SavedPlace): String = place.name
    }
}
