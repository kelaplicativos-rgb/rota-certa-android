package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage36Test {
    private val uber="com.ubercab.driver"
    private val driver99="com.app99.driver"
    private val indrive="sinet.startup.indriver"
    private fun a()=FarolRuntimeAuthorityStage36.Authority(1000L).also{it.updateSelection(setOf(uber,driver99,indrive));it.setUsageAccess(true)}
    private fun e(pkg:String,signal:FarolPresenceAuthorityStage30.UsageSignal,at:Long=2000L)=FarolPresenceAuthorityStage30.UsageEvidence(pkg,signal,at)

    @Test fun markerRuntime(){assertEquals("FAROL_SINGLE_RUNTIME_AUTHORITY_STAGE36",FarolRuntimeAuthorityStage36.CONTRACT_MARKER)}
    @Test fun markerActivation(){assertEquals("SELECTED_APP_ARMS_READING_SESSION_STAGE36",FarolRuntimeAuthorityStage36.ACTIVATION_MARKER)}
    @Test fun markerWindow(){assertEquals("WINDOW_PACKAGE_PROVENANCE_ONLY_STAGE36",FarolRuntimeAuthorityStage36.WINDOW_MARKER)}
    @Test fun markerFreshness(){assertEquals("READING_EPOCH_CARD_LEASE_ONLY_FRESHNESS_STAGE36",FarolRuntimeAuthorityStage36.FRESHNESS_MARKER)}
    @Test fun markerOcr(){assertEquals("OCR_SURVIVES_RAW_WINDOW_SERIAL_CHURN_STAGE36",FarolRuntimeAuthorityStage36.OCR_MARKER)}
    @Test fun markerRoute(){assertEquals("ROUTE_SURVIVES_RAW_WINDOW_CHURN_STAGE36",FarolRuntimeAuthorityStage36.ROUTE_MARKER)}
    @Test fun markerPaint(){assertEquals("PAINT_REQUIRES_CURRENT_LEASE_STAGE36",FarolRuntimeAuthorityStage36.PAINT_MARKER)}
    @Test fun selectedAccessibilityTurnsOn(){assertTrue(a().observeAccessibility(uber).enabled)}
    @Test fun nonSelectedDoesNotTurnOn(){assertFalse(a().observeAccessibility("com.whatsapp").enabled)}
    @Test fun homeBoundaryKeepsOn(){val x=a();x.observeAccessibility(uber);x.observeWindowBoundary("com.sec.android.app.launcher");assertTrue(x.snapshot().enabled)}
    @Test fun systemUiBoundaryKeepsOn(){val x=a();x.observeAccessibility(driver99);x.observeWindowBoundary("com.android.systemui");assertTrue(x.snapshot().enabled)}
    @Test fun whatsappBoundaryKeepsOn(){val x=a();x.observeAccessibility(indrive);x.observeWindowBoundary("com.whatsapp");assertTrue(x.snapshot().enabled)}
    @Test fun pauseKeepsOn(){val x=a();x.observeAccessibility(uber);x.applyUsageEvidence(listOf(e(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_PAUSED)));assertTrue(x.snapshot().enabled)}
    @Test fun stoppedAloneKeepsOn(){val x=a();x.observeAccessibility(uber);x.applyUsageEvidence(listOf(e(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_STOPPED)));assertTrue(x.snapshot().enabled)}
    @Test fun fgsStartTurnsOn(){val x=a();x.applyUsageEvidence(listOf(e(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START)));assertTrue(x.snapshot().enabled)}
    @Test fun activityStopWhileFgsKeepsOn(){val x=a();x.applyUsageEvidence(listOf(e(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START),e(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_STOPPED,2100)));assertTrue(x.snapshot().enabled)}
    @Test fun fgsStopAloneKeepsOn(){val x=a();x.applyUsageEvidence(listOf(e(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START),e(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_STOP,2100)));assertTrue(x.snapshot().enabled)}
    @Test fun terminalPairTurnsOff(){val x=a();x.applyUsageEvidence(listOf(e(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_START),e(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_STOPPED,2100),e(uber,FarolPresenceAuthorityStage30.UsageSignal.FOREGROUND_SERVICE_STOP,2200)));assertFalse(x.snapshot().enabled)}
    @Test fun oldUsageIgnored(){val x=a();x.applyUsageEvidence(listOf(e(uber,FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_RESUMED,500)));assertFalse(x.snapshot().enabled)}
    @Test fun selectionRemovalTurnsOff(){val x=a();x.observeAccessibility(uber);x.updateSelection(setOf(driver99));assertFalse(x.snapshot().enabled)}
    @Test fun accessRevokedTurnsOff(){val x=a();x.observeAccessibility(uber);x.setUsageAccess(false);assertFalse(x.snapshot().enabled)}
    @Test fun trueOffChangesEpoch(){val x=a();val on=x.observeAccessibility(uber);val off=x.setUsageAccess(false);assertTrue(off.readingEpoch>on.readingEpoch)}
    @Test fun visualOpensLease(){val x=a();x.observeAccessibility(uber);assertTrue(x.observeVisualEvidence().leaseId>0)}
    @Test fun repeatedRawSameLease(){val x=a();x.observeAccessibility(uber);val p=x.observeVisualEvidence().leaseId;val q=x.observeVisualEvidence().leaseId;assertEquals(p,q)}
    @Test fun differentDestinationNewLease(){val x=a();x.observeAccessibility(uber);x.observeVisualEvidence();val p=x.bindDestination("Rua A|Rua B 20");val q=x.bindDestination("Rua A|Rua C 30");assertNotEquals(p.leaseId,q.leaseId)}
}
