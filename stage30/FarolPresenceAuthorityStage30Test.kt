package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FarolPresenceAuthorityStage30Test {
    private val uber = "com.ubercab.driver"
    private val app99 = "com.app99.driver"
    private val indrive = "sinet.startup.indriver"
    private val whatsapp = "com.whatsapp"
    private val chatgpt = "com.openai.chatgpt"
    private val start = 1_000_000L

    @Before fun reset() {
        FarolPresenceAuthorityStage30.Metrics.resetForTests()
        FarolPresenceAuthorityStage30.Diagnostics.resetForTests()
        FarolCausalLatencyStage28.Metrics.resetForTests()
    }

    private fun authority(selected: Set<String> = setOf(uber, app99, indrive)) =
        FarolPresenceAuthorityStage30.Authority(start).apply {
            updateSelection(selected)
            setUsageAccess(true)
        }

    private fun ev(pkg: String, signal: FarolPresenceAuthorityStage30.UsageSignal, at: Long = start + 10) =
        FarolPresenceAuthorityStage30.UsageEvidence(pkg, signal, at)

    @Test fun noSelectedEvidenceMeansOff() { assertFalse(authority().snapshot().enabled) }
    @Test fun directUberAccessibilityMeansOn() { val a=authority(); assertTrue(a.observeAccessibility(uber,1,start+1).enabled) }
    @Test fun direct99AccessibilityMeansOn() { val a=authority(); assertTrue(a.observeAccessibility(app99,1,start+1).enabled) }
    @Test fun directIndriveAccessibilityMeansOn() { val a=authority(); assertTrue(a.observeAccessibility(indrive,1,start+1).enabled) }
    @Test fun stage29UberReplayWorksWithEmptyProcessShadow() { val a=authority(); a.observeAccessibility(uber,1,start+1); assertTrue(a.updateProcessShadow(emptySet()).enabled) }
    @Test fun stage29App99ReplayWorksWithEmptyProcessShadow() { val a=authority(); a.observeAccessibility(app99,1,start+1); assertTrue(a.updateProcessShadow(emptySet()).enabled) }
    @Test fun stage29IndriveReplayWorksWithEmptyProcessShadow() { val a=authority(); a.observeAccessibility(indrive,1,start+1); assertTrue(a.updateProcessShadow(emptySet()).enabled) }
    @Test fun processShadowFalseNegativeCannotTurnOffDirectEvidence() { val a=authority(); a.observeAccessibility(uber); a.updateProcessShadow(emptySet()); assertTrue(a.snapshot().enabled) }
    @Test fun processShadowAloneCannotTurnOn() { val a=authority(); a.updateProcessShadow(setOf(uber)); assertFalse(a.snapshot().enabled) }
    @Test fun processShadowFalsePositiveIsMeasured() { val a=authority(); a.updateProcessShadow(setOf(uber)); assertEquals(1L,FarolPresenceAuthorityStage30.Metrics.counter("processShadowWouldFalsePositive")) }
    @Test fun processShadowFalseNegativeIsMeasured() { val a=authority(); a.observeAccessibility(uber); a.updateProcessShadow(emptySet()); assertEquals(1L,FarolPresenceAuthorityStage30.Metrics.counter("processShadowFalseNegative")) }
    @Test fun oldUsageBeforeSessionCannotTurnOn() { val a=authority(); a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START,start-1))); assertFalse(a.snapshot().enabled) }
    @Test fun oldUsageBeforeSessionIsCountedIgnored() { val a=authority(); a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_RESUMED,start-10))); assertEquals(1L,FarolPresenceAuthorityStage30.Metrics.counter("usageEvidenceBeforeSessionIgnored")) }
    @Test fun currentSessionActivityResumedMeansOn() { val a=authority(); assertTrue(a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_RESUMED))).enabled) }
    @Test fun currentSessionForegroundServiceMeansOn() { val a=authority(); assertTrue(a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START))).enabled) }
    @Test fun activityPausedRemovesResumedAuthority() { val a=authority(); a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_RESUMED))); assertFalse(a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_PAUSED,start+20))).enabled) }
    @Test fun activityStoppedRemovesResumedAuthority() { val a=authority(); a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_RESUMED))); assertFalse(a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_STOPPED,start+20))).enabled) }
    @Test fun foregroundServiceStopRemovesServiceAuthority() { val a=authority(); a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START))); assertFalse(a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_STOP,start+20))).enabled) }
    @Test fun twoSelectedCurrentServicesMeansTwoActive() { val a=authority(); val s=a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START),ev(app99,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START,start+11))); assertEquals(2,s.authoritativeActivePackages.size) }
    @Test fun closeOneOfTwoKeepsOn() { val a=authority(); a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START),ev(app99,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START,start+11))); assertTrue(a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_STOP,start+20))).enabled) }
    @Test fun closeLastOfTwoTurnsOff() { val a=authority(); a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START),ev(app99,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START,start+11))); a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_STOP,start+20))); assertFalse(a.applyUsageEvidence(listOf(ev(app99,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_STOP,start+21))).enabled) }
    @Test fun nonSelectedAccessibilityCannotTurnOn() { val a=authority(); assertFalse(a.observeAccessibility(whatsapp).enabled) }
    @Test fun packageNormalizationStillMatchesSelected() { val a=authority(); assertTrue(a.observeAccessibility(" COM.UBERCAB.DRIVER ").enabled) }
    @Test fun selectionRemovalTurnsOffIfLastAuthorityRemoved() { val a=authority(setOf(uber)); a.observeAccessibility(uber); assertFalse(a.updateSelection(emptySet()).enabled) }
    @Test fun usagePermissionRevocationTurnsOff() { val a=authority(); a.observeAccessibility(uber); assertFalse(a.setUsageAccess(false).enabled) }
    @Test fun usagePermissionRestoredDoesNotResurrectOldDirectWitness() { val a=authority(); a.observeAccessibility(uber); a.setUsageAccess(false); a.setUsageAccess(true); assertFalse(a.snapshot().enabled) }
    @Test fun windowBoundaryToWhatsappClearsOnlyVisibleWitness() { val a=authority(); a.observeAccessibility(uber); assertFalse(a.observeWindowBoundary(whatsapp).enabled) }
    @Test fun genericNonSelectedAccessibilityDoesNotClearVisibleWitness() { val a=authority(); a.observeAccessibility(uber); a.observeAccessibility(whatsapp); assertTrue(a.snapshot().enabled) }
    @Test fun windowBoundaryAwayKeepsOnWhenForegroundServiceCurrent() { val a=authority(); a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START))); a.observeAccessibility(uber); assertTrue(a.observeWindowBoundary(whatsapp).enabled) }
    @Test fun windowBoundaryAwayKeepsOnWhenAnotherSelectedServiceCurrent() { val a=authority(); a.applyUsageEvidence(listOf(ev(app99,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START))); a.observeAccessibility(uber); assertTrue(a.observeWindowBoundary(chatgpt).enabled) }
    @Test fun currentAccessibilityWinsOverProcessShadowEmpty() { val a=authority(); a.updateProcessShadow(emptySet()); assertTrue(a.observeAccessibility(uber).enabled) }
    @Test fun currentUsageWinsOverProcessShadowEmpty() { val a=authority(); a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START))); a.updateProcessShadow(emptySet()); assertTrue(a.snapshot().enabled) }
    @Test fun directActivationIncrementsGeneration() { val a=authority(); val g=a.snapshot().generation; a.observeAccessibility(uber); assertTrue(a.snapshot().generation>g) }
    @Test fun repeatedDirectEventDoesNotChurnGeneration() { val a=authority(); a.observeAccessibility(uber); val g=a.snapshot().generation; a.observeAccessibility(uber); assertEquals(g,a.snapshot().generation) }
    @Test fun offTransitionIncrementsGeneration() { val a=authority(); a.observeAccessibility(uber); val g=a.snapshot().generation; a.observeWindowBoundary(whatsapp); assertTrue(a.snapshot().generation>g) }
    @Test fun authoritativeSetExcludesProcessShadow() { val a=authority(); a.updateProcessShadow(setOf(uber)); assertTrue(a.snapshot().authoritativeActivePackages.isEmpty()) }
    @Test fun accessibilitySetContainsDirectSelectedOnly() { val a=authority(); a.observeAccessibility(uber); assertEquals(setOf(uber),a.snapshot().accessibilityActivePackages) }
    @Test fun usageSetContainsSessionSignalsOnly() { val a=authority(); a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START),ev(app99,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_RESUMED))); assertEquals(setOf(uber,app99),a.snapshot().usageActivePackages) }
    @Test fun usagePausedDoesNotStopIndependentForegroundService() { val a=authority(); a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START),ev(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_RESUMED,start+11),ev(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_PAUSED,start+12))); assertTrue(a.snapshot().enabled) }
    @Test fun foregroundServiceStopDoesNotStopIndependentResumedActivity() { val a=authority(); a.applyUsageEvidence(listOf(ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START),ev(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_RESUMED,start+11),ev(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_STOP,start+12))); assertTrue(a.snapshot().enabled) }
    @Test fun stage28OldWorkStillCannotPaintAfterReadingOff() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); val l=c.lease(); c.readingOff(); assertFalse(c.applyFinalIfFresh(l,"GREEN",2.0)) }
    @Test fun stage28OldGoogleStillStaleAfterVisualChange() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); val l=c.lease(); c.visualChanged(); assertFalse(c.isFresh(l)) }
    @Test fun stage28OcrCoalescenceStillWorks() { val g=FarolCausalLatencyStage28.OcrGate(); assertTrue(g.request(1)); assertFalse(g.request(1)) }
    @Test fun stage28RouteDedupStillWorks() { val g=FarolCausalLatencyStage28.RouteGate(); val k=FarolCausalLatencyStage28.RouteKey(1,1,"Rua B, 20"); assertTrue(g.begin(k)); assertFalse(g.begin(k)) }
    @Test fun universalVisualPackageMarkerRemainsExplicit() { assertTrue(FarolPresenceAuthorityStage30.UNIVERSAL_VISUAL_MARKER.contains("ONLY_TURNS_READING_ON_OFF")) }
    @Test fun noPollingContractIsExplicit() { assertEquals("NO_POLLING_NO_SLEEP_NO_DEBOUNCE_STAGE30",FarolPresenceAuthorityStage30.NO_POLLING_MARKER) }
    @Test fun physicalReplayMarkerIsExplicit() { assertEquals("PHYSICAL_FAILURE_REPLAY_STAGE30",FarolPresenceAuthorityStage30.REPLAY_MARKER) }
    @Test fun shadowReportNamesAllAuthorities() { val a=authority(); a.observeAccessibility(uber); a.updateProcessShadow(emptySet()); val r=FarolPresenceAuthorityStage30.Diagnostics.export(); assertTrue(r.contains("authoritative=")); assertTrue(r.contains("accessibility=")); assertTrue(r.contains("usage=")); assertTrue(r.contains("process=")) }
}
