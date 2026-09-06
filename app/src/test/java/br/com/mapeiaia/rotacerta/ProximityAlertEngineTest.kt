package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class ProximityAlertEngineTest {
    @Before
    fun setUp() {
        DiagnosticRuntimeGate.endManualCapture()
        DiagnosticLogStore.clear()
        DiagnosticRuntimeGate.beginManualCapture(
            durationMillis = 10_000L,
            nowMillis = Long.MAX_VALUE / 2,
        )
    }

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
    fun speechFailureDoesNotProduceContinuousProductionLog() {
        val speech = FakeProximitySpeech(importedResult = false)
        val engine = ProximityAlertEngine(speech) { 100_000L }
        val radar = importedRadar("radar-1", Coordinate(0.0, 0.0005))

        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) {}

        assertEquals(1, speech.importedCalls)
        assertFalse(DiagnosticLogStore.dump().isNotBlank())
    }

    @Test
    fun diagnosticReasonStaysFocusedWithoutGlobalProductionLog() {
        val speech = FakeProximitySpeech()
        val engine = ProximityAlertEngine(speech) { 100_000L }
        val radar = importedRadar("radar-1", Coordinate(0.0, 0.0005))
        var diagnostic: ProximityAlertDiagnostic? = null

        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0), settings()) { diagnostic = it }

        val reason = requireNotNull(diagnostic).reason
        assertEquals(true, reason.contains("Radar importado falado"))
        assertFalse(reason.contains("--- LOG GLOBAL ---"))
        assertFalse(DiagnosticLogStore.dump().isNotBlank())
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
    fun singleDirectionImportedRadarSpeaksWhenTravelBearingMatches() {
        val speech = FakeProximitySpeech()
        val engine = ProximityAlertEngine(speech) { 100_000L }
        val radar = importedRadar("eastbound", Coordinate(0.0, 0.0), directionType = 1, direction = 90)

        engine.check(emptyList(), listOf(radar), Coordinate(0.0, -0.0030), settings()) {}
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, -0.0015), settings()) {}

        assertEquals(listOf("eastbound"), speech.importedRadarIds)
    }

    @Test
    fun singleDirectionImportedRadarIgnoresOppositeTravelBearing() {
        val speech = FakeProximitySpeech()
        val engine = ProximityAlertEngine(speech) { 100_000L }
        val radar = importedRadar("eastbound", Coordinate(0.0, 0.0), directionType = 1, direction = 90)

        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0030), settings()) {}
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0015), settings()) {}

        assertEquals(0, speech.importedCalls)
        assertFalse(DiagnosticLogStore.dump().isNotBlank())
    }

    @Test
    fun doubleDirectionImportedRadarAcceptsInverseTravelBearing() {
        val speech = FakeProximitySpeech()
        val engine = ProximityAlertEngine(speech) { 100_000L }
        val radar = importedRadar("two-way", Coordinate(0.0, 0.0), directionType = 2, direction = 90)

        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0030), settings()) {}
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, 0.0015), settings()) {}

        assertEquals(listOf("two-way"), speech.importedRadarIds)
    }

    @Test
    fun importedRadarDoesNotRetrySpeechWhileDistanceIsIncreasing() {
        val speech = FakeProximitySpeech(importedResult = false)
        val engine = ProximityAlertEngine(speech) { 100_000L }
        val radar = importedRadar("radar-1", Coordinate(0.0, 0.0))

        engine.check(emptyList(), listOf(radar), Coordinate(0.0, -0.0010), settings()) {}
        engine.check(emptyList(), listOf(radar), Coordinate(0.0, -0.0015), settings()) {}

        assertEquals(1, speech.importedCalls)
        assertFalse(DiagnosticLogStore.dump().isNotBlank())
    }

    @Test
    fun savedPlaceAlertDoesNotSpeakWhenFirstSeenAlreadyInsideZone() {
        val speech = FakeProximitySpeech()
        val engine = ProximityAlertEngine(speech) { 100_000L }
        val alert = proximityAlert()

        engine.check(listOf(alert), emptyList(), Coordinate(0.0, 0.0), settings()) {}
        engine.check(listOf(alert), emptyList(), Coordinate(0.0, 0.0), settings()) {}

        assertEquals(0, speech.proximityCalls)
        assertFalse(DiagnosticLogStore.dump().isNotBlank())
    }

    @Test
    fun savedPlaceAlertSpeaksAfterLeavingAndReEnteringZone() {
        var now = 100_000L
        val speech = FakeProximitySpeech()
        val engine = ProximityAlertEngine(speech) { now }
        val alert = proximityAlert()

        engine.check(listOf(alert), emptyList(), Coordinate(0.0, 0.0), settings()) {}
        engine.check(listOf(alert), emptyList(), Coordinate(0.0, 0.0045), settings()) {}
        now += 25_000L
        engine.check(listOf(alert), emptyList(), Coordinate(0.0, 0.0), settings()) {}

        assertEquals(1, speech.proximityCalls)
    }

    @Test
    fun savedPlaceAlertCanRepeatDuringSameApproach() {
        var now = 100_000L
        val speech = FakeProximitySpeech()
        val engine = ProximityAlertEngine(speech) { now }
        val alert = proximityAlert()

        engine.check(listOf(alert), emptyList(), Coordinate(0.0, 0.0045), settings()) {}
        engine.check(listOf(alert), emptyList(), Coordinate(0.0, 0.0), settings()) {}
        now += 25_000L
        engine.check(listOf(alert), emptyList(), Coordinate(0.0, 0.0), settings()) {}

        assertEquals(2, speech.proximityCalls)
    }

    private fun settings(): AppSettings = AppSettings(proximityAlertDistanceMeters = 200)

    private fun proximityAlert(): SavedPlace = SavedPlace(
        id = "alert-1",
        name = "Alerta",
        type = SavedPlaceType.ProximityAlert,
        coordinate = Coordinate(0.0, 0.0005),
        alertDistanceMeters = 200,
    )

    private fun importedRadar(
        id: String,
        coordinate: Coordinate,
        directionType: Int? = null,
        direction: Int? = null,
    ): ImportedRadar = ImportedRadar(
        id = id,
        coordinate = coordinate,
        type = 1,
        directionType = directionType,
        direction = direction,
    )

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
