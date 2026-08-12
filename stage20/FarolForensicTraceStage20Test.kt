package br.com.mapeiaia.rotacerta

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolForensicTraceStage20Test {
    @After fun tearDown() = FarolForensicTraceStage20.resetForTest()

    private fun binding(sg: Long = 1, wg: Long = 1, hash: Int = 10, sig: String = "visual|dest") =
        FarolForensicTraceStage20.BindingSnapshot(sg, wg, hash, sig)

    private fun bind(now: Long = 1_000_000L, b: FarolForensicTraceStage20.BindingSnapshot = binding()): String {
        val cycle = FarolForensicTraceStage20.beginCycle(now - 10_000L, "pkg", 2048, 3)
        return FarolForensicTraceStage20.bindCandidate(now, cycle, b, "Accessibility", "Rua Destino, 1", "block")
    }

    @Test fun markerIsStable() = assertEquals("FAROL_FORENSIC_CAUSALITY_STAGE20", FarolForensicTraceStage20.CONTRACT_MARKER)
    @Test fun nanosClockMarkerIsStable() = assertEquals("ELAPSED_REALTIME_NANOS_STAGE20", FarolForensicTraceStage20.CLOCK_MARKER)
    @Test fun cycleIdsAreUnique() {
        val a = FarolForensicTraceStage20.beginCycle(1, "a", 1, 1)
        val b = FarolForensicTraceStage20.beginCycle(2, "b", 1, 1)
        assertNotEquals(a, b)
    }
    @Test fun sameBindingReusesTrace() {
        val b = binding()
        val t1 = bind(100, b)
        val t2 = FarolForensicTraceStage20.bindCandidate(200, null, b, "Ocr", "Rua Destino, 1", "other")
        assertEquals(t1, t2)
    }
    @Test fun packageDoesNotChangeTraceIdentity() {
        val b = binding()
        val c1 = FarolForensicTraceStage20.beginCycle(1, "com.app99.driver", 1, 1)
        val t1 = FarolForensicTraceStage20.bindCandidate(2, c1, b, "Accessibility", "Rua D, 1", "b")
        val c2 = FarolForensicTraceStage20.beginCycle(3, "com.ubercab.driver", 1, 1)
        val t2 = FarolForensicTraceStage20.bindCandidate(4, c2, b, "Accessibility", "Rua D, 1", "b")
        assertEquals(t1, t2)
    }
    @Test fun newGenerationCreatesNewTrace() = assertNotEquals(bind(b = binding(1)), bind(b = binding(2)))
    @Test fun newWindowGenerationCreatesNewTrace() = assertNotEquals(bind(b = binding(wg = 1)), bind(b = binding(wg = 2)))
    @Test fun newHashCreatesNewTrace() = assertNotEquals(bind(b = binding(hash = 1)), bind(b = binding(hash = 2)))
    @Test fun newSignatureCreatesNewTrace() = assertNotEquals(bind(b = binding(sig = "a")), bind(b = binding(sig = "b")))
    @Test fun traceLookupByBindingWorks() {
        val b = binding(); val t = bind(b = b); assertEquals(t, FarolForensicTraceStage20.traceFor(b))
    }
    @Test fun routeJobIdsAreUnique() {
        val t = bind(); assertNotEquals(FarolForensicTraceStage20.routeJobStarted(t, 2), FarolForensicTraceStage20.routeJobStarted(t, 3))
    }
    @Test fun cacheHitAppearsInSummary() {
        val t = bind(); FarolForensicTraceStage20.cacheLookupStarted(t, 2_000_000); FarolForensicTraceStage20.cacheLookupFinished(t, 2_010_000, true)
        assertTrue(FarolForensicTraceStage20.exportReport().contains("cache=true"))
    }
    @Test fun cacheMissAppearsInSummary() {
        val t = bind(); FarolForensicTraceStage20.cacheLookupStarted(t, 2_000_000); FarolForensicTraceStage20.cacheLookupFinished(t, 2_010_000, false)
        assertTrue(FarolForensicTraceStage20.exportReport().contains("cache=false"))
    }
    @Test fun routeDurationIsMeasuredInMicroseconds() {
        val t = bind(); val r = FarolForensicTraceStage20.routeJobStarted(t, 2_000_000); FarolForensicTraceStage20.routeCallStarted(t, r, 2_000_000, "x"); FarolForensicTraceStage20.routeCallFinished(t, r, 3_500_000, "[1]")
        val report = FarolForensicTraceStage20.exportReport()
        assertTrue(report.contains("route_ns=1500000"))
        assertTrue(report.contains("route_us=1500"))
    }
    @Test fun decisionDurationIsMeasured() {
        val t = bind(); FarolForensicTraceStage20.decisionStarted(t, "r", 2_000_000); FarolForensicTraceStage20.decisionFinished(t, "r", 2_050_000, "GoodRide", 4.0)
        val report = FarolForensicTraceStage20.exportReport()
        assertTrue(report.contains("decision_ns=50000"))
        assertTrue(report.contains("decision_us=50"))
    }
    @Test fun unscopedFinalPaintIsCritical() {
        FarolForensicTraceStage20.overlayRequested(null, 1, "Red", 8.0, binding(), "legacy")
        assertTrue(FarolForensicTraceStage20.criticalForTest() > 0)
        assertTrue(FarolForensicTraceStage20.exportReport().contains("unscoped_final_paint"))
    }
    @Test fun orangeWithoutTokenIsNotCritical() {
        FarolForensicTraceStage20.overlayRequested(null, 1, "Orange", null, binding(), "waiting")
        assertEquals(0, FarolForensicTraceStage20.criticalForTest())
    }
    @Test fun staleBindingAtPaintIsCritical() {
        val t = bind(); val p = FarolForensicTraceStage20.preparePaint(t, "r", binding(), "Green", 4.0, 2)
        FarolForensicTraceStage20.overlayRequested(p, 3, "Green", 4.0, binding(sg = 2), "apply")
        assertTrue(FarolForensicTraceStage20.exportReport().contains("binding_mismatch"))
    }
    @Test fun paintColorMismatchIsCritical() {
        val t = bind(); val p = FarolForensicTraceStage20.preparePaint(t, "r", binding(), "Green", 4.0, 2)
        FarolForensicTraceStage20.overlayRequested(p, 3, "Red", 4.0, binding(), "apply")
        assertTrue(FarolForensicTraceStage20.exportReport().contains("color_mismatch"))
    }
    @Test fun paintDistanceMismatchIsCritical() {
        val t = bind(); val p = FarolForensicTraceStage20.preparePaint(t, "r", binding(), "Green", 4.0, 2)
        FarolForensicTraceStage20.overlayRequested(p, 3, "Green", 7.0, binding(), "apply")
        assertTrue(FarolForensicTraceStage20.exportReport().contains("distance_mismatch"))
    }
    @Test fun tinyDistanceRoundoffIsAccepted() {
        val t = bind(); val p = FarolForensicTraceStage20.preparePaint(t, "r", binding(), "Green", 4.000, 2)
        FarolForensicTraceStage20.overlayRequested(p, 3, "Green", 4.004, binding(), "apply")
        assertEquals(0, FarolForensicTraceStage20.criticalForTest())
    }
    @Test fun routeToPaintOver100msIsCritical() {
        val t = bind(); val r = FarolForensicTraceStage20.routeJobStarted(t, 1_000_000); FarolForensicTraceStage20.routeCallFinished(t, r, 2_000_000, "[4]")
        val p = FarolForensicTraceStage20.preparePaint(t, r, binding(), "Green", 4.0, 2_010_000)
        FarolForensicTraceStage20.overlayRequested(p, 102_100_001, "Green", 4.0, binding(), "apply")
        val report = FarolForensicTraceStage20.exportReport()
        assertTrue(report.contains("route_to_paint_delay"))
        assertTrue(report.contains("route_to_paint_ns=100100001"))
    }
    @Test fun routeToPaintAtThresholdIsNotCritical() {
        val t = bind(); val r = FarolForensicTraceStage20.routeJobStarted(t, 1_000_000); FarolForensicTraceStage20.routeCallFinished(t, r, 2_000_000, "[4]")
        val p = FarolForensicTraceStage20.preparePaint(t, r, binding(), "Green", 4.0, 2_010_000)
        FarolForensicTraceStage20.overlayRequested(p, 102_000_000, "Green", 4.0, binding(), "apply")
        assertEquals(0, FarolForensicTraceStage20.criticalForTest())
    }
    @Test fun overlayApplyOver50msIsCritical() {
        val t = bind(); val p = FarolForensicTraceStage20.preparePaint(t, "r", binding(), "Green", 4.0, 1)
        FarolForensicTraceStage20.overlayRequested(p, 2_000_000, "Green", 4.0, binding(), "req")
        FarolForensicTraceStage20.overlayApplied(p, 52_000_001, "Green", 4.0, binding(), "applied")
        assertTrue(FarolForensicTraceStage20.criticalForTest() > 0)
        assertTrue(FarolForensicTraceStage20.exportReport().contains("request_to_apply_ns=50000001"))
    }
    @Test fun bindingFreshEventIsRecorded() {
        val t = bind(); FarolForensicTraceStage20.bindingCheck(t, "r", 2, "AFTER_ROUTE", binding(), binding(), true, false)
        assertTrue(FarolForensicTraceStage20.exportReport().contains("S20_BINDING_FRESH_AFTER_ROUTE"))
    }
    @Test fun staleBlockedIsRecordedButNotCritical() {
        val t = bind(); FarolForensicTraceStage20.bindingCheck(t, "r", 2, "AFTER_ROUTE", binding(), binding(sg = 2), false, false)
        assertTrue(FarolForensicTraceStage20.exportReport().contains("S20_STALE_RESULT_BLOCKED_AFTER_ROUTE"))
        assertEquals(0, FarolForensicTraceStage20.criticalForTest())
    }
    @Test fun invalidationIsRecorded() {
        val b = binding(); bind(b = b); FarolForensicTraceStage20.visualInvalidated(3, b, binding(sg = 2), "new_visual")
        assertTrue(FarolForensicTraceStage20.exportReport().contains("S20_VISUAL_INVALIDATED"))
    }
    @Test fun routeCancellationIsRecorded() {
        val t = bind(); FarolForensicTraceStage20.routeCancelled(t, "r", 3, "new_visual")
        assertTrue(FarolForensicTraceStage20.exportReport().contains("S20_ROUTE_JOB_CANCELLED"))
    }
    @Test fun ocrLifecycleIsRecorded() {
        FarolForensicTraceStage20.ocrStage(1, 7, "REQUEST"); FarolForensicTraceStage20.ocrStage(2, 7, "EXTRACT_END", details = "blocks=5")
        val r = FarolForensicTraceStage20.exportReport(); assertTrue(r.contains("S20_OCR_REQUEST")); assertTrue(r.contains("op=ocr-7"))
    }
    @Test fun idempotentOverlaySkipIsRecorded() {
        FarolForensicTraceStage20.overlayIdempotentSkipped(null, 1, "Green", 4.0, binding(), "showOverlay")
        assertTrue(FarolForensicTraceStage20.exportReport().contains("S20_OVERLAY_IDEMPOTENT_SKIP"))
    }
    @Test fun callSiteFiltersRecorderFrames() {
        val s = FarolForensicTraceStage20.callSite(arrayOf(StackTraceElement("x.FarolForensicTraceStage20", "a", "X.kt", 1), StackTraceElement("x.Service", "paint", "S.kt", 9)))
        assertEquals("Service.paint:9", s)
    }
    @Test fun reportSaysNoFinalDecisionWhenNonePainted() {
        bind(); assertTrue(FarolForensicTraceStage20.exportReport().contains("verdict=NO_FINAL_DECISION_CAPTURED"))
    }
    @Test fun reportBecomesTraceableAfterCleanPaint() {
        val t = bind(); val p = FarolForensicTraceStage20.preparePaint(t, "r", binding(), "Green", 4.0, 1)
        FarolForensicTraceStage20.overlayRequested(p, 2, "Green", 4.0, binding(), "a"); FarolForensicTraceStage20.overlayApplied(p, 3, "Green", 4.0, binding(), "a")
        assertTrue(FarolForensicTraceStage20.exportReport().contains("verdict=TRACEABLE_NO_CRITICAL_CAUSALITY_ANOMALY"))
    }
    @Test fun criticalPaintChangesVerdict() {
        FarolForensicTraceStage20.overlayRequested(null, 1, "Red", 4.0, binding(), "legacy")
        assertTrue(FarolForensicTraceStage20.exportReport().contains("verdict=FAIL_CRITICAL_PAINT_CAUSALITY"))
    }
}
