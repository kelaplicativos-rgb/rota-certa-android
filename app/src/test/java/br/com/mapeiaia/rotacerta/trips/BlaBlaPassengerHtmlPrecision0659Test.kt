package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaPassengerHtmlPrecision0659Test {
    @Test
    fun fareScriptAcceptsCurrencyBeforeOrAfterAmount() {
        val fare = File("src/main/assets/blablacar/scripts/passenger_fare.js").readText()
        val contact = File("src/main/assets/blablacar/scripts/passenger_contact.js").readText()

        assertTrue(fare.contains("moneyToken"))
        assertTrue(fare.contains("collectMoney"))
        assertTrue(fare.contains("R\\\\$"))
        assertTrue(contact.contains("labeledFare"))
    }

    @Test
    fun passengerRevealIsSemanticAndCannotOpenGenericCountrySelector() {
        val prepare = File("src/main/assets/blablacar/scripts/passenger_prepare.js").readText()

        assertTrue(prepare.contains("privateDomain"))
        assertTrue(prepare.contains("globalControl"))
        assertTrue(prepare.contains("rootInert"))
        assertFalse(prepare.contains("(node.getAttribute&&node.getAttribute('aria-expanded')==='false') ||"))
    }

    @Test
    fun readinessAndTripMapGeoArePartOfTheAuthoritativeJoin() {
        val ready = File("src/main/assets/blablacar/scripts/passenger_ready.js").readText()
        val detail = File("src/main/assets/blablacar/scripts/trip_detail.js").readText()
        val capture = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()

        assertTrue(ready.contains("rootInert"))
        assertTrue(ready.contains("document.readyState"))
        assertTrue(detail.contains("zoomOn"))
        assertTrue(detail.contains("stopLocations"))
        assertTrue(capture.contains("passengerTripStopLocation0659"))
        assertTrue(capture.contains("dropoffLatitude = dropoffTripStop0659.latitude"))
        assertTrue(capture.contains("blablacar_trip_map_zoomOn_0659"))
        assertTrue(capture.contains("BLABLACAR_HTML_PASSENGER_PAGE_NOT_READY_0659"))
    }
}
