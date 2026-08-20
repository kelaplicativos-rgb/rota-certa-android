package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage40AuthorityRedContractTest {
    private val uber = "com.ubercab.driver"
    private fun root(): File {
        val c = File(System.getProperty("user.dir"))
        return if (File(c, "app/src/main/java").isDirectory) c else if (c.name == "app") c.parentFile else c
    }
    private fun src(name: String) = File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()
    private fun service() = src("LiveRideAccessibilityService.kt")
    private fun authority() = FarolRuntimeAuthorityStage36.Authority(1_000L).also {
        it.updateSelection(setOf(uber)); it.setUsageAccess(true)
    }
    private fun usage(signal: FarolPresenceAuthorityStage30.UsageSignal, at: Long = 2_000L) =
        FarolPresenceAuthorityStage30.UsageEvidence(uber, signal, at)

    @Test fun selectionEmptyIsAbsoluteOff() {
        val a = authority(); a.observeAccessibility(uber)
        assertFalse(a.updateSelection(emptySet()).enabled)
    }

    @Test fun nonSelectedVisualPackageIsProvenanceOnlyWhileReadingIsOn() {
        val a = authority(); a.observeAccessibility(uber)
        assertTrue(a.observeWindowBoundary("com.openai.chatgpt").enabled)
    }

    @Test fun runningAppProcessesRemainsShadowOnly() {
        assertTrue(src("FarolPresenceAuthorityStage30.kt").contains("RUNNING_APP_PROCESSES_SHADOW_ONLY_STAGE30"))
    }

    @Test fun googleDrivingRouteRemainsDistanceAuthority() {
        assertTrue(service().contains("drivingDistancesFromAddressKm("))
    }

    @Test fun orangeCannotBePublicFarolState() {
        assertFalse(service().contains("showOverlay(RadarColor.Orange"))
        assertFalse(FarolVisualStateAuthorityStage40.PublicState.entries.any { it.name == "ORANGE" })
    }

    @Test fun oneFinalVisualAuthorityOwnsPublicPaint() {
        val s = service()
        assertTrue(s.contains("FarolVisualStateAuthorityStage40.decide("))
        assertTrue(s.contains("renderOverlayStage40("))
    }

    @Test fun positivePresenceCannotRemainArmedForever() {
        val a = authority(); a.observeAccessibility(uber); assertTrue(a.snapshot().enabled)
        assertFalse(a.expireStalePresence(emptySet()).enabled)
    }

    @Test fun selectedAccessibilityWindowKeepsReadingOn() {
        val a = authority(); a.observeAccessibility(uber)
        assertTrue(a.expireStalePresence(setOf(uber)).enabled)
    }

    @Test fun foregroundServiceAloneCannotKeepReadingOn() {
        val a = authority(); a.applyUsageEvidence(listOf(usage(FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START)))
        assertTrue(a.snapshot().enabled)
        assertFalse(a.expireStalePresence(emptySet()).enabled)
    }

    @Test fun resumedActivityKeepsReadingOnWithoutSelectedWindow() {
        val a = authority(); a.applyUsageEvidence(listOf(usage(FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_RESUMED)))
        assertTrue(a.expireStalePresence(emptySet()).enabled)
    }

    @Test fun pausedActivityWithoutSelectedWindowTurnsReadingOffOnReconciliation() {
        val a = authority()
        a.applyUsageEvidence(listOf(usage(FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_RESUMED)))
        a.applyUsageEvidence(listOf(usage(FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_PAUSED, 2_100L)))
        assertFalse(a.expireStalePresence(emptySet()).enabled)
    }

    @Test fun readingOffAlwaysForcesGrayAndNoDistance() {
        val d = FarolVisualStateAuthorityStage40.decide(false, "Green", 4.2)
        assertEquals(FarolVisualStateAuthorityStage40.PublicState.GRAY, d.state)
        assertNull(d.distanceKm)
    }

    @Test fun intermediateOrangeRequestBecomesYellow() {
        val d = FarolVisualStateAuthorityStage40.decide(true, "Orange", null)
        assertEquals(FarolVisualStateAuthorityStage40.PublicState.YELLOW, d.state)
        assertNull(d.distanceKm)
    }

    @Test fun greenWithoutFinalDistanceIsRejectedToYellow() {
        val d = FarolVisualStateAuthorityStage40.decide(true, "Green", null)
        assertEquals(FarolVisualStateAuthorityStage40.PublicState.YELLOW, d.state)
    }

    @Test fun freshGreenWithRealDistanceIsAllowed() {
        val d = FarolVisualStateAuthorityStage40.decide(true, "Green", 3.7)
        assertEquals(FarolVisualStateAuthorityStage40.PublicState.GREEN, d.state)
        assertEquals(3.7, d.distanceKm!!, 0.0)
    }

    @Test fun freshRedWithRealDistanceIsAllowed() {
        val d = FarolVisualStateAuthorityStage40.decide(true, "Red", 8.4)
        assertEquals(FarolVisualStateAuthorityStage40.PublicState.RED, d.state)
        assertEquals(8.4, d.distanceKm!!, 0.0)
    }

    @Test fun twoVisibleAddressesRemainMinimumForFinalCandidate() {
        assertTrue(src("FarolCausalCorrectionStage21.kt").contains("addresses.size >= UniversalAddressTrigger.MINIMUM_VISIBLE_ADDRESSES"))
    }

    @Test fun stage40AddsNoPollingTimer() {
        val p = src("FarolRuntimeAuthorityStage36.kt") + src("FarolVisualStateAuthorityStage40.kt")
        listOf("Thread.sleep(", "SystemClock.sleep(", "scheduleAtFixedRate(", "fixedRateTimer(").forEach { assertFalse(p.contains(it)) }
    }
}
