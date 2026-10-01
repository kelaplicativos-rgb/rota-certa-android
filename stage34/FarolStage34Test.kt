package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage34Test {
    private fun serviceSource()=File(System.getProperty("user.dir"),"app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private fun src(name:String)=File(System.getProperty("user.dir"),"app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()
    private fun signal(pkg:String?="com.app99.driver",windowId:Int=10,text:String="Rua A 10 destino Rua B 20")=FarolSemanticCardStage32.Signal(pkg,pkg,pkg,windowId,"card",text,2048)

    @Test fun markerCardLease(){ assertEquals("FAROL_SINGLE_CARD_LEASE_STAGE34",FarolCardLeaseStage34.CONTRACT_MARKER) }
    @Test fun markerFreshness(){ assertEquals("FAROL_SINGLE_FRESHNESS_AUTHORITY_STAGE34",FarolCardLeaseStage34.FRESHNESS_MARKER) }
    @Test fun markerProvenance(){ assertEquals("PACKAGE_WINDOW_PROVENANCE_ONLY_STAGE34",FarolCardLeaseStage34.PROVENANCE_ONLY_MARKER) }
    @Test fun rawEventOpensLease(){ val a=FarolCardLeaseStage34.Authority(); assertEquals(1L,a.observeRawEvent().leaseId) }
    @Test fun repeatedRawEventKeepsLease(){ val a=FarolCardLeaseStage34.Authority(); val x=a.observeRawEvent(); val y=a.observeRawEvent(); assertEquals(x.leaseId,y.leaseId); assertTrue(y.rawRevision>x.rawRevision) }
    @Test fun firstCandidateBindsExistingLease(){ val a=FarolCardLeaseStage34.Authority(); val x=a.observeRawEvent(); val d=a.bindCandidate("Rua A|Rua B 20"); assertEquals(x.leaseId,d.snapshot.leaseId); assertTrue(d.firstBind); assertFalse(d.leaseTransition) }
    @Test fun sameDestinationConfirms(){ val a=FarolCardLeaseStage34.Authority(); a.observeRawEvent(); a.bindCandidate("Rua A|Rua B 20"); val d=a.bindCandidate("Rua X|Rua B 20"); assertTrue(d.sameDestination); assertFalse(d.leaseTransition) }
    @Test fun accentFormattingSameDestination(){ val a=FarolCardLeaseStage34.Authority(); a.observeRawEvent(); val x=a.bindCandidate("Rua A|Avenida São João, 100"); val y=a.bindCandidate("Outra|Avenida Sao Joao 100"); assertEquals(x.snapshot.leaseId,y.snapshot.leaseId) }
    @Test fun abbreviationAvMatchesAvenida(){ val a=FarolCardLeaseStage34.Authority(); a.observeRawEvent(); val x=a.bindCandidate("Rua A|Av. Professor Luiz Ignácio Anhaia Mello, 9531"); val y=a.bindCandidate("Outra|Avenida Professor Luiz Ignacio Anhaia Mello 9531"); assertEquals(x.snapshot.leaseId,y.snapshot.leaseId) }
    @Test fun abbreviationRMatchesRua(){ assertEquals(FarolCardLeaseStage34.canonicalDestination("R. Sálvia, 66"),FarolCardLeaseStage34.canonicalDestination("Rua Salvia 66")) }
    @Test fun differentDestinationNewLease(){ val a=FarolCardLeaseStage34.Authority(); a.observeRawEvent(); val x=a.bindCandidate("Rua A|Rua B 20"); val y=a.bindCandidate("Rua A|Rua C 30"); assertTrue(y.leaseTransition); assertNotEquals(x.snapshot.leaseId,y.snapshot.leaseId) }
    @Test fun readingOffClearsLease(){ val a=FarolCardLeaseStage34.Authority(); a.observeRawEvent(); a.markReadingOff(); assertNull(a.snapshot()) }
    @Test fun destinationExtractionUsesFinalAddress(){ assertEquals("rua b 20",FarolCardLeaseStage34.destinationFromAddressSignature("Rua A 10|Rua B 20")) }
    @Test fun helperHasNoPackageWindowLeaseFields(){ val s=src("FarolCardLeaseStage34.kt"); assertFalse(s.contains("windowId:")); assertFalse(s.contains("ownerPackage:")) }
    @Test fun helperNoPollingSleepTimer(){ val s=src("FarolCardLeaseStage34.kt"); listOf("Thread.sleep(","SystemClock.sleep(","Timer(","scheduleAtFixedRate(").forEach{assertFalse(s.contains(it))} }

    @Test fun semanticOwnerTransitionPreservesLease(){ val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); val l=g.lease(); g.observe(signal(pkg="com.ubercab.driver")); assertTrue(g.isFresh(l)) }
    @Test fun semanticWindowTransitionPreservesLease(){ val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); val l=g.lease(); g.observe(signal(windowId=20)); assertTrue(g.isFresh(l)) }
    @Test fun semanticRawTextChangePreservesUnconfirmedLease(){ val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); val l=g.lease(); g.observe(signal(text="Rua C 30 destino Rua D 40")); assertTrue(g.isFresh(l)) }
    @Test fun semanticBlankNoisePreservesLease(){ val g=FarolSemanticCardStage32.SemanticGate(); val x=g.observe(signal()); val y=g.observe(signal(text="")); assertEquals(x.generation,y.generation); assertFalse(y.mutation) }
    @Test fun semanticFirstCandidateSameLease(){ val g=FarolSemanticCardStage32.SemanticGate(); val x=g.observe(signal()); val y=g.observeCandidate("Rua A|Rua B 20"); assertEquals(x.generation,y.generation); assertFalse(y.mutation) }
    @Test fun semanticDifferentDestinationMutates(){ val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); g.observeCandidate("Rua A|Rua B 20"); assertTrue(g.observeCandidate("Rua A|Rua C 30").mutation) }
    @Test fun semanticAbbreviationDoesNotMutate(){ val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); g.observeCandidate("Rua A|Av. Paulista 100"); assertFalse(g.observeCandidate("Outra|Avenida Paulista, 100").mutation) }
    @Test fun semanticReadingOffInvalidates(){ val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); val l=g.lease(); g.markReadingOff(); assertFalse(g.isFresh(l)) }

    @Test fun screenshotFirstStarts(){ assertTrue(FarolSemanticCardStage32.ScreenshotRateGate().request(1000,1).startNow) }
    @Test fun screenshotBusyCoalesces(){ val g=FarolSemanticCardStage32.ScreenshotRateGate(); g.request(1000,1); assertEquals(FarolSemanticCardStage32.ScreenshotDecisionKind.COALESCE_BUSY,g.request(1100,1).kind) }
    @Test fun screenshotSameLeaseReacquiresAfterInterval(){ val g=FarolSemanticCardStage32.ScreenshotRateGate(); g.request(1000,1); g.complete(1,true); assertEquals(FarolSemanticCardStage32.ScreenshotDecisionKind.START,g.request(1500,1).kind) }
    @Test fun screenshotExactly333StillWaits(){ val g=FarolSemanticCardStage32.ScreenshotRateGate(); g.request(1000,1); g.complete(1,true); assertEquals(FarolSemanticCardStage32.ScreenshotDecisionKind.RATE_LIMITED_PENDING,g.request(1333,1).kind) }

    @Test fun precollectBlankFirstWindowSkipped(){ val g=FarolReadingActivationStage26.PreCollectGate(); val d=g.admit(true,FarolReadingActivationStage26.CheapVisualSignal(false,"10:uber","")); assertFalse(d.heavyCollect) }
    @Test fun precollectBlankWindowChurnSkipped(){ val g=FarolReadingActivationStage26.PreCollectGate(); g.admit(true,FarolReadingActivationStage26.CheapVisualSignal(false,"10:uber","")); val d=g.admit(true,FarolReadingActivationStage26.CheapVisualSignal(false,"7:systemui","")); assertFalse(d.heavyCollect) }
    @Test fun precollectFirstAddressEvidenceCollects(){ val g=FarolReadingActivationStage26.PreCollectGate(); val d=g.admit(true,FarolReadingActivationStage26.CheapVisualSignal(false,"10:uber","Rua A 10")); assertTrue(d.heavyCollect) }
    @Test fun precollectSameAddressAcrossWindowSkipped(){ val g=FarolReadingActivationStage26.PreCollectGate(); g.admit(true,FarolReadingActivationStage26.CheapVisualSignal(false,"10:uber","Rua A 10")); val d=g.admit(true,FarolReadingActivationStage26.CheapVisualSignal(false,"11:uber","Rua A 10")); assertFalse(d.heavyCollect) }
    @Test fun precollectChangedAddressCollects(){ val g=FarolReadingActivationStage26.PreCollectGate(); g.admit(true,FarolReadingActivationStage26.CheapVisualSignal(false,"10:uber","Rua A 10")); assertTrue(g.admit(true,FarolReadingActivationStage26.CheapVisualSignal(false,"10:uber","Rua B 20")).heavyCollect) }
    @Test fun precollectPriceNoiseNormalized(){ val g=FarolReadingActivationStage26.PreCollectGate(); g.admit(true,FarolReadingActivationStage26.CheapVisualSignal(false,"10:uber","Rua A 10 R$ 10,00")); assertFalse(g.admit(true,FarolReadingActivationStage26.CheapVisualSignal(false,"10:uber","Rua A 10 R$ 15,00")).heavyCollect) }

    @Test fun bindingRawWindowChurnIgnored(){ val b=FarolUniversalVisualPipelineStage19.Binding(1,1,10,"sig"); assertTrue(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(b,9,8,999,"sig",false)) }
    @Test fun bindingDifferentAddressRejected(){ val b=FarolUniversalVisualPipelineStage19.Binding(1,1,10,"sig-a"); assertFalse(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(b,1,1,10,"sig-b",false)) }
    @Test fun bindingPendingVerificationRejected(){ val b=FarolUniversalVisualPipelineStage19.Binding(1,1,10,"sig"); assertFalse(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(b,1,1,10,"sig",true)) }

    @Test fun screenshotRuntimeImplementationPreserved(){ val s=serviceSource(); assertTrue(s.contains("takeScreenshot(")); assertTrue(s.contains("ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT")) }
    @Test fun googleRuntimePreserved(){ assertTrue(serviceSource().contains("drivingDistancesFromAddressKm(")) }
    @Test fun stage30PresencePreserved(){ assertTrue(serviceSource().contains("FarolPresenceAuthorityStage30")) }
    @Test fun blackBoxAndPrintPreserved(){ val s=serviceSource(); assertTrue(s.contains("FarolForensicCardBlackBoxStage32")); assertTrue(s.contains("FAROL_REAL_PRINT_MEDIASTORE_STAGE32")) }
    @Test fun reportExportsStage34(){ assertTrue(src("ManualTechnicalReportBuilder.kt").contains("FarolCardLeaseStage34.Metrics.exportReport()")) }
    @Test fun versionStage34(){ val s=File(System.getProperty("user.dir"),"app/build.gradle.kts").readText(); assertTrue(s.contains("versionCode = 5492")); assertTrue(s.contains("versionName = \"0.1.208\"")) }
}
