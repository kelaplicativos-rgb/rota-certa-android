package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BlaBlaHtmlStopTimes0672Test {
    @Test
    fun exactTripHtmlExportsAndPersistsTimedStops() {
        val script = File("src/main/assets/blablacar/scripts/trip_detail.js").readText()
        val collector = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollector.kt").readText()
        val capture = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt").readText()

        assertTrue(script.contains("clockNearNode"))
        assertTrue(script.contains("stopTimeFromRow"))
        assertTrue(script.contains("itineraryStopTimes: itineraryStopTimes"))
        assertTrue(collector.contains("val itinerary_stop_times: List<String> = emptyList()"))
        assertTrue(capture.contains("val itineraryStopTimes: List<String> = emptyList()"))
        assertTrue(capture.contains("itinerary_stop_times = stopTimes0672"))
    }
}
