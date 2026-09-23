package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolEdge0637Test {
    private fun root(): File {
        val cwd = File(System.getProperty("user.dir"))
        return if (File(cwd, "app/src/main/java").isDirectory) cwd
        else if (cwd.name == "app" && File(cwd, "src/main/java").isDirectory) cwd.parentFile
        else cwd
    }

    private fun src(name: String): String =
        File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/" + name).readText()

    @Test
    fun foreign_address_never_owns_a_ride_card() {
        val result = FarolRideCardOwnershipStage47.evaluate(
            packageName = "com.google.android.apps.maps",
            selectedPackages = setOf("com.ubercab.driver"),
            text = "Rua A, 10\nAvenida B, 20\nR$ 35\n8 min",
            locationCount = 2,
        )
        assertFalse(result.owned)
    }

    @Test
    fun selected_map_navigation_surface_is_still_rejected_without_ride_semantics() {
        val pkg = "com.example.driver"
        val result = FarolRideCardOwnershipStage47.evaluate(
            packageName = pkg,
            selectedPackages = setOf(pkg),
            text = "Google Maps\nBarra de pesquisa\nRua A, 10\nContinue por 300 m\nVire a esquerda",
            locationCount = 1,
        )
        assertFalse(result.owned)
    }

    @Test
    fun real_99_style_card_is_owned_without_ocr_wait() {
        val pkg = "com.app99.driver"
        val result = FarolRideCardOwnershipStage47.evaluate(
            packageName = pkg,
            selectedPackages = setOf(pkg),
            text = "R$2,08/km\n4,76 - 493 corridas\nPerfil Essencial\n9min (1,3km)\nAvenida Mateo Bei, 2651\n9min (2,9km)\nRua Ana Santesso, 373",
            locationCount = 2,
            structuralSignature = "android.widget.FrameLayout:card",
        )
        assertTrue(result.owned)
    }

    @Test
    fun explicit_accept_card_can_own_single_destination() {
        val pkg = "sinet.startup.indriver"
        val result = FarolRideCardOwnershipStage47.evaluate(
            packageName = pkg,
            selectedPackages = setOf(pkg),
            text = "Pedido de viagem\nRua Flores, 100\nR$ 42\nAceitar por R$ 42",
            locationCount = 1,
        )
        assertTrue(result.owned)
    }

    @Test
    fun bubble_touch_path_has_no_stage38_record_and_single_tap_is_immediate() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private inner class BubbleTouchListener")
        val end = live.indexOf("private fun dp", start)
        assertTrue(start >= 0 && end > start)
        val listener = live.substring(start, end)
        assertFalse(listener.contains("FarolMaximumForensicsStage38.record"))
        assertFalse(listener.contains("pendingSingleTapJob"))
        assertTrue(listener.contains("view.performClick()"))
        assertTrue(listener.contains("Choreographer"))
        assertTrue(listener.contains("bubbleMoveEventsStage637.incrementAndGet()"))
    }

    @Test
    fun paint_is_deferred_while_finger_owns_the_bubble() {
        val live = src("LiveRideAccessibilityService.kt")
        assertTrue(live.contains("if (bubbleGestureActive && !forcePhysicalCommitStage43)"))
        assertTrue(live.contains("deferredBubblePaintStage637 = DeferredBubblePaintStage637"))
        assertTrue(live.contains("flushDeferredBubblePaintStage637()"))
    }

    @Test
    fun local_color_hides_km_until_traffic_aware_road_result() {
        val live = src("LiveRideAccessibilityService.kt")
        assertTrue(live.contains("applyUniversalPreliminaryColorStage637"))
        assertTrue(live.contains("showOverlay(colorStage637, null)"))
        assertTrue(live.contains("cachedTrafficAwareDrivingDistancesFromAddressKm"))
        assertTrue(live.contains("trafficAwareDrivingDistancesFromAddressKm"))
        val maps = src("GoogleMapsService.kt")
        assertTrue(maps.contains("\"routingPreference\": \"TRAFFIC_AWARE\""))
        assertTrue(maps.contains("PERSISTENT_TRAFFIC_ADDRESS_ROUTE_PREFIX"))
        assertTrue(maps.indexOf("trafficAwareAddressRouteMatrixBody") < maps.indexOf("requestOpenStreetMapAddressRoutes", maps.indexOf("trafficAwareDrivingDistancesFromAddressKm")))
    }

    @Test
    fun release_metadata_is_0637_5928() {
        val gradle = File(root(), "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("releaseVersionName = \"0.1.637\""))
        assertTrue(gradle.contains("releaseVersionCode = 5_928"))
    }
}
