package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FarolCaptureFabric0751ContractTest {
    private fun source(relative: String): String =
        File(System.getProperty("user.dir"), relative).readText()

    @Test
    fun accessibility_service_exposes_full_selected_window_surface_and_screenshot_capability() {
        val xml = source("src/main/res/xml/rota_certa_accessibility.xml")
        assertTrue(xml.contains("flagRetrieveInteractiveWindows"))
        assertTrue(xml.contains("flagIncludeNotImportantViews"))
        assertTrue(xml.contains("android:canRetrieveWindowContent=\"true\""))
        assertTrue(xml.contains("android:canTakeScreenshot=\"true\""))
    }

    @Test
    fun empty_accessibility_escalates_to_exact_window_ocr_on_android_14_plus() {
        val live = source("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
        assertTrue(live.contains("FAROL_EMPTY_ACCESSIBILITY_WINDOW_OCR_0751"))
        assertTrue(live.contains("takeFarolScreenshot0751"))
        assertTrue(live.contains("takeScreenshotOfWindow"))
        assertTrue(live.contains("surfaceTokenStage46.windowId"))
        assertTrue(live.contains("FAROL_SELECTED_WINDOW_RECOVERED_0751"))
    }

    @Test
    fun confirmed_app_switch_still_cancels_stale_route_before_new_authority() {
        val live = source("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
        val switchIndex = live.indexOf("BUBBLE_APP_IDENTITY_SWITCH_STAGE18")
        assertTrue(switchIndex >= 0)
        val slice = live.substring(switchIndex, (switchIndex + 1800).coerceAtMost(live.length))
        assertTrue(slice.contains("driverCardSessionGate0162.invalidate()"))
        assertTrue(slice.contains("universalRouteJob?.cancel()"))
        assertTrue(slice.contains("hardClearUniversalTwoAddress"))
    }

    @Test
    fun geocode_hot_path_is_offline_then_hedged_android_google_without_public_nominatim_delay() {
        val maps = source("src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt")
        val start = maps.indexOf("suspend fun resolveFarolCoordinateResilient0697")
        val end = maps.indexOf("suspend fun drivingDistanceKm", start)
        assertTrue(start >= 0 && end > start)
        val hot = maps.substring(start, end)
        assertTrue(hot.contains("OFFLINE_GEOCODE_BUDGET_MS"))
        assertTrue(hot.contains("GEOCODE_HEDGE_STARTED_MARKER"))
        assertTrue(hot.contains("\"android\" to async"))
        assertTrue(hot.contains("\"google\" to async"))
        assertTrue(hot.contains("if (apiKey.isNotBlank())"))
        assertTrue(hot.contains("osmOnlyWithoutGoogle"))
        assertFalse(hot.contains("from=android; to=osm"))
        assertFalse(hot.contains("from=osm; to=google"))
    }

    @Test
    fun direct_map_download_allows_cellular_after_explicit_user_button_request() {
        val downloader = source("src/main/java/br/com/mapeiaia/rotacerta/OrganicMapsDirectMapDownloader0750.kt")
        assertTrue(downloader.contains("nativeIsDownloadOn3gEnabled"))
        assertTrue(downloader.contains("nativeEnableDownloadOn3g"))
        assertTrue(downloader.contains("ORGANIC_MAPS_MOBILE_DATA_DOWNLOAD_ENABLED_0751"))
    }
}
