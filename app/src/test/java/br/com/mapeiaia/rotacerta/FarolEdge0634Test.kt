package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolEdge0634Test {
    private fun root(): File {
        val cwd = File(System.getProperty("user.dir"))
        return if (File(cwd, "app/src/main/java").isDirectory) cwd
        else if (cwd.name == "app" && File(cwd, "src/main/java").isDirectory) cwd.parentFile
        else cwd
    }

    private fun src(name: String): String =
        File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/" + name).readText()

    @Test
    fun haversine_zero_and_known_latitude_degree() {
        val a = Coordinate(0.0, 0.0)
        assertEquals(0.0, GeoDistance.kilometers(a, a), 0.000001)
        assertEquals(111.195, GeoDistance.kilometers(a, Coordinate(1.0, 0.0)), 0.25)
    }

    @Test
    fun radius_boundary_is_inclusive_and_exclusive_above() {
        val engine = DecisionEngine()
        val settings = AppSettings(homeRadiusKm = 10.0)
        val fields = RideFields(destination = "Rua Exemplo, 100")
        assertEquals(
            Recommendation.GoodRide,
            engine.decideWorkRegion(fields, settings, "x", true, 10.0, emptyList()).recommendation,
        )
        assertEquals(
            Recommendation.GoodRide,
            engine.decideWorkRegion(fields, settings, "x", true, 9.999, emptyList()).recommendation,
        )
        assertEquals(
            Recommendation.OutsideRadius,
            engine.decideWorkRegion(fields, settings, "x", true, 10.001, emptyList()).recommendation,
        )
    }

    @Test
    fun canonical_address_normalization_is_stable() {
        assertEquals(
            "avenida sao joao 100",
            FarolRuntimeAuthorityStage36.canonicalDestination("Av. São João, 100 - Brasil"),
        )
    }

    @Test
    fun latest_lease_rejects_stale_ocr_geo_cache_and_paint_result() {
        val authority = FarolRuntimeAuthorityStage36.Authority(0L)
        authority.updateSelection(setOf("com.example.driver"))
        authority.setUsageAccess(true)
        authority.observeAccessibility("com.example.driver")
        authority.observeVisualEvidence()

        val cardA = authority.captureDestinationToken("Origem|Rua A 10")!!
        val leaseA = cardA.leaseId
        val cardB = authority.captureDestinationToken("Origem|Rua B 20")!!
        assertNotEquals(leaseA, cardB.leaseId)
        assertFalse(authority.isFresh(cardA))
        assertTrue(authority.isFresh(cardB))
        assertFalse(authority.markProcessing(cardA, FarolRuntimeAuthorityStage36.ProcessingState.COORDINATE))
        assertFalse(authority.markPaint(cardA, FarolRuntimeAuthorityStage36.PaintState.RED))
        assertTrue(authority.markProcessing(cardB, FarolRuntimeAuthorityStage36.ProcessingState.DISTANCE))
        assertTrue(authority.markPaint(cardB, FarolRuntimeAuthorityStage36.PaintState.GREEN))
    }

    @Test
    fun contaminated_ui_text_cannot_become_destination() {
        val dirty = listOf(
            "Aceitar R$ 32,50 • 12 min • 4 km",
            "Limite 50 km/h • passageiro Joao • aceitar",
            "R$ 45 8 min cancelar fechar oferta",
        )
        dirty.forEach { value ->
            assertTrue(UniversalScreenAddressParser.findAddresses(value).isEmpty())
        }
    }

    @Test
    fun meaningful_event_mutation_is_not_coalesced_as_duplicate() {
        val gate = FarolRealtimeEventGate0167(duplicateWindowMillis = 100L)
        assertTrue(gate.shouldCollect("com.example.driver", "com.example.driver", 7, 16, "View", 1_000L, eventSemanticHash = 100))
        assertFalse(gate.shouldCollect("com.example.driver", "com.example.driver", 7, 16, "View", 1_020L, eventSemanticHash = 100))
        assertTrue(gate.shouldCollect("com.example.driver", "com.example.driver", 7, 16, "View", 1_021L, eventSemanticHash = 101))
    }

    @Test
    fun critical_path_has_no_remote_road_route_call() {
        val live = src("LiveRideAccessibilityService.kt")
        assertFalse(live.contains("googleMapsService.drivingDistancesFromAddressKm("))
        assertFalse(live.contains("googleMapsService.cachedDrivingDistancesFromAddressKm("))
        assertTrue(live.contains("cachedFarolCoordinate("))
        assertTrue(live.contains("resolveFarolCoordinate("))
        assertTrue(live.contains("GeoDistance.kilometers("))
        assertTrue(live.contains("staleResultsDropped"))
    }

    @Test
    fun ocr_recognizer_is_warm_and_closed_at_service_teardown() {
        val android = src("AndroidServices.kt")
        val live = src("LiveRideAccessibilityService.kt")
        assertTrue(android.contains("private val recognizer = TextRecognition.getClient"))
        assertTrue(android.contains("fun close()"))
        assertTrue(live.contains("ocrService.close()"))
    }

    @Test
    fun capture_store_is_private_bounded_and_24h() {
        val capture = src("FailedCardCaptureStore0161.kt")
        assertTrue(capture.contains("File(context.filesDir, DIRECTORY)"))
        assertTrue(capture.contains("MAX_CAPTURES = 80"))
        assertTrue(capture.contains("24L * 60L * 60L * 1_000L"))
        assertFalse(capture.contains("MediaStore"))
    }

    @Test
    fun radar_monitor_remains_independent_from_farol_reading() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("    private fun startProximityAlertMonitor()")
        val end = live.indexOf("    private fun checkDirectionalProximityAlertsChecklist5", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)
        assertFalse(block.contains("liveReadingEnabled"))
        assertFalse(block.contains("WorkModePolicy0162.isEnabled"))
    }

    @Test
    fun local_math_benchmark_reports_p50_p95_p99() {
        val samples = LongArray(2_000)
        val a = Coordinate(-23.5505, -46.6333)
        val b = Coordinate(-23.5617, -46.6559)
        repeat(100) { GeoDistance.kilometers(a, b) }
        repeat(samples.size) { i ->
            samples[i] = measureNanoTime { GeoDistance.kilometers(a, b) }
        }
        samples.sort()
        fun p(q: Double): Long = samples[((samples.size * q).toInt() - 1).coerceIn(0, samples.lastIndex)]
        val p50 = p(.50)
        val p95 = p(.95)
        val p99 = p(.99)
        println("FAROL_EDGE_LOCAL_MATH p50_us=" + (p50 / 1000) + " p95_us=" + (p95 / 1000) + " p99_us=" + (p99 / 1000))
        assertTrue(p99 < 20_000_000L)
    }
}
