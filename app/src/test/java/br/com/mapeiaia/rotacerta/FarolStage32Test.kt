package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage32Test {
    private fun signal(
        pkg: String? = "com.app99.driver",
        source: String? = pkg,
        window: String? = pkg,
        windowId: Int = 10,
        slot: String = "card",
        text: String = "99 Negocia Rua A 10 destino Rua B 20",
        eventType: Int = 2048,
    ) = FarolSemanticCardStage32.Signal(pkg, source, window, windowId, slot, text, eventType)

    @Test fun stage32MarkersExist() {
        assertEquals("FAROL_SEMANTIC_CARD_GENERATION_STAGE32", FarolSemanticCardStage32.CONTRACT_MARKER)
        assertEquals("FAROL_OCR_IDENTITY_PRESERVATION_STAGE32", FarolSemanticCardStage32.OCR_IDENTITY_MARKER)
        assertEquals("FAROL_GLOBAL_COLLECTION_LAST_RESORT_STAGE32", FarolSemanticCardStage32.GLOBAL_LAST_RESORT_MARKER)
        assertEquals("FAROL_REAL_GOOGLE_PRESERVED_STAGE32", FarolSemanticCardStage32.GOOGLE_MARKER)
    }

    @Test fun firstSemanticSourceOpensStableCardLeaseWithoutRawMutation() {
        val g=FarolSemanticCardStage32.SemanticGate(); val d=g.observe(signal())
        assertFalse(d.mutation); assertEquals(1L,d.generation)
    }

    @Test fun identicalSourceDoesNotChangeGeneration() {
        val g = FarolSemanticCardStage32.SemanticGate(); g.observe(signal())
        val d = g.observe(signal())
        assertFalse(d.mutation); assertEquals(1L, d.generation)
    }

    @Test fun blankNoiseNewSlotDoesNotKillSemanticIdentity() {
        val g = FarolSemanticCardStage32.SemanticGate(); val first = g.observe(signal())
        val d = g.observe(signal(slot="noise", text=""))
        assertFalse(d.mutation); assertEquals(first.generation, d.generation)
    }

    @Test fun samePackageBlankContentEventPreservesOcrLease() {
        val g = FarolSemanticCardStage32.SemanticGate(); g.observe(signal())
        val lease = g.lease(); g.observe(signal(slot="focus", text=""))
        assertTrue(g.isFresh(lease))
    }

    @Test fun rawSourceTextChangePreservesLeaseStage34() {
        val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); val l=g.lease(); g.observe(signal(text="99 Negocia Rua C 30 destino Rua D 40")); assertTrue(g.isFresh(l))
    }

    @Test fun windowTransitionIsProvenanceOnlyStage34() {
        val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); val l=g.lease(); g.observe(signal(windowId=11)); assertTrue(g.isFresh(l))
    }

    @Test fun ownerTransitionIsProvenanceOnlyStage34() {
        val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); val l=g.lease(); g.observe(signal(pkg="com.ubercab.driver",source="com.ubercab.driver",window="com.ubercab.driver")); assertTrue(g.isFresh(l))
    }

    @Test fun sourceSlotClearIsRawNoiseStage34() {
        val g=FarolSemanticCardStage32.SemanticGate(); val a=g.observe(signal()); val b=g.observe(signal(text="")); assertFalse(b.mutation); assertEquals(a.generation,b.generation)
    }

    @Test fun firstCandidateBindsWithoutCreatingArtificialNewGeneration() {
        val g = FarolSemanticCardStage32.SemanticGate(); val d = g.observe(signal())
        val c = g.observeCandidate("visual|rua b 20")
        assertFalse(c.mutation); assertEquals(d.generation, c.generation)
    }

    @Test fun sameCandidateSignatureDoesNotMutate() {
        val g = FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); g.observeCandidate("sig-a")
        assertFalse(g.observeCandidate("sig-a").mutation)
    }

    @Test fun changedCandidateSignatureMutates() {
        val g = FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); g.observeCandidate("sig-a")
        assertTrue(g.observeCandidate("sig-b").mutation)
    }

    @Test fun readingOffInvalidatesSemanticLease() {
        val g = FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); val l = g.lease(); g.markReadingOff()
        assertFalse(g.isFresh(l))
    }

    @Test fun screenshotFirstRequestStarts() {
        val gate = FarolSemanticCardStage32.ScreenshotRateGate()
        assertTrue(gate.request(1000,1).startNow)
    }

    @Test fun screenshotBusyCoalesces() {
        val gate = FarolSemanticCardStage32.ScreenshotRateGate(); gate.request(1000,1)
        assertEquals(FarolSemanticCardStage32.ScreenshotDecisionKind.COALESCE_BUSY, gate.request(1100,2).kind)
    }

    @Test fun screenshotRequestBefore333msIsNotStarted() {
        val gate = FarolSemanticCardStage32.ScreenshotRateGate(); gate.request(1000,1); gate.complete(1,true)
        val d = gate.request(1200,2)
        assertEquals(FarolSemanticCardStage32.ScreenshotDecisionKind.RATE_LIMITED_PENDING, d.kind)
    }

    @Test fun explicitPrintRequestCanCaptureSameSemanticGenerationAfterPlatformInterval() {
        val gate = FarolSemanticCardStage32.ScreenshotRateGate(); gate.request(1000,1); gate.complete(1,true)
        assertTrue(gate.requestExplicit(1334,1).startNow)
    }

    @Test fun screenshotRequestAfter333msStarts() {
        val gate = FarolSemanticCardStage32.ScreenshotRateGate(); gate.request(1000,1); gate.complete(1,true)
        assertTrue(gate.request(1334,2).startNow)
    }

    @Test fun screenshotExactly333msStillWaitsBecauseFrameworkUsesLessOrEqual() {
        val gate = FarolSemanticCardStage32.ScreenshotRateGate(); gate.request(1000,1); gate.complete(1,true)
        assertFalse(gate.request(1333,2).startNow)
    }

    @Test fun screenshotError3QueuesEventDrivenRetry() {
        val gate = FarolSemanticCardStage32.ScreenshotRateGate(); gate.request(1000,1); gate.markIntervalShort(1)
        assertTrue(gate.hasPending()); assertFalse(gate.pendingEligible(1200,1)); assertTrue(gate.pendingEligible(1334,1))
    }

    @Test fun pendingDifferentSemanticGenerationIsDropped() {
        val gate = FarolSemanticCardStage32.ScreenshotRateGate(); gate.request(1000,1); gate.markIntervalShort(1)
        assertFalse(gate.pendingEligible(1500,2)); assertFalse(gate.hasPending())
    }

    @Test fun completedSameCardLeaseCanReacquireFrameStage34() {
        val g=FarolSemanticCardStage32.ScreenshotRateGate(); g.request(1000,1); g.complete(1,true); assertEquals(FarolSemanticCardStage32.ScreenshotDecisionKind.START,g.request(1500,1).kind)
    }

    @Test fun direct99ProvenanceRequiresMatchingEvidence() {
        val p = FarolSemanticCardStage32.resolveProvenance("com.app99.driver","com.app99.driver","com.app99.driver",setOf("com.app99.driver"))
        assertEquals("com.app99.driver",p.ownerPackage); assertEquals(FarolSemanticCardStage32.OwnerConfidence.DIRECT,p.confidence)
    }

    @Test fun directUberProvenanceWorks() {
        val p = FarolSemanticCardStage32.resolveProvenance("com.ubercab.driver","com.ubercab.driver",null,setOf("com.ubercab.driver"))
        assertEquals(FarolSemanticCardStage32.OwnerConfidence.DIRECT,p.confidence)
    }

    @Test fun singleSelectedTriggerIsDerivedNotInventedDirect() {
        val p = FarolSemanticCardStage32.resolveProvenance("sinet.startup.indriver",null,"com.whatsapp",setOf("sinet.startup.indriver"))
        assertEquals(FarolSemanticCardStage32.OwnerConfidence.DERIVED,p.confidence)
    }

    @Test fun conflictingRidePackagesBecomeUnknown() {
        val p = FarolSemanticCardStage32.resolveProvenance("com.app99.driver","com.ubercab.driver",null,setOf("com.app99.driver","com.ubercab.driver"))
        assertEquals(FarolSemanticCardStage32.OwnerConfidence.UNKNOWN,p.confidence); assertNull(p.ownerPackage)
    }

    @Test fun whatsappVisualPackageIsNotClaimedAsRideOwner() {
        val p = FarolSemanticCardStage32.resolveProvenance("com.whatsapp","com.whatsapp","com.whatsapp",setOf("com.app99.driver"))
        assertEquals(FarolSemanticCardStage32.OwnerConfidence.UNKNOWN,p.confidence)
    }

    @Test fun blackBoxCreatesStableCase() {
        FarolForensicCardBlackBoxStage32.resetForTests()
        val g=FarolSemanticCardStage32.SemanticGate(); val d=g.observe(signal())
        val id=FarolForensicCardBlackBoxStage32.observeEvent(1,1,d,"com.app99.driver","com.app99.driver","com.app99.driver",FarolSemanticCardStage32.Provenance("com.app99.driver",FarolSemanticCardStage32.OwnerConfidence.DIRECT,"test"),setOf("com.app99.driver"),1)
        assertEquals("CASE-000001",id)
    }

    @Test fun sameSemanticCaseReusesId() {
        FarolForensicCardBlackBoxStage32.resetForTests(); val g=FarolSemanticCardStage32.SemanticGate(); val d1=g.observe(signal())
        val p=FarolSemanticCardStage32.Provenance("com.app99.driver",FarolSemanticCardStage32.OwnerConfidence.DIRECT,"test")
        val a=FarolForensicCardBlackBoxStage32.observeEvent(1,1,d1,"com.app99.driver",null,null,p,setOf("com.app99.driver"),1)
        val b=FarolForensicCardBlackBoxStage32.observeEvent(2,2,g.observe(signal()),"com.app99.driver",null,null,p,setOf("com.app99.driver"),1)
        assertEquals(a,b)
    }

    @Test fun confirmedDestinationChangeIsSemanticMutationStage34() {
        val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); g.observeCandidate("Rua A|Rua B 20"); assertTrue(g.observeCandidate("Rua A|Rua D 40").mutation)
    }

    @Test fun userCaptureMarksCurrentCase() {
        FarolForensicCardBlackBoxStage32.resetForTests(); val g=FarolSemanticCardStage32.SemanticGate(); val d=g.observe(signal())
        FarolForensicCardBlackBoxStage32.observeEvent(1,1,d,"com.app99.driver",null,null,FarolSemanticCardStage32.Provenance(null,FarolSemanticCardStage32.OwnerConfidence.UNKNOWN,"test"),emptySet(),1)
        val id=FarolForensicCardBlackBoxStage32.userMark(2,2,"com.app99.driver",g.snapshot())
        assertEquals("CASE-000001",id); assertTrue(FarolForensicCardBlackBoxStage32.currentSnapshot()!!.userMarked)
    }

    @Test fun ocrSuccessClosesAsReadSuccessOcr() {
        FarolForensicCardBlackBoxStage32.resetForTests(); val g=FarolSemanticCardStage32.SemanticGate(); val d=g.observe(signal())
        FarolForensicCardBlackBoxStage32.observeEvent(1,1,d,"com.app99.driver",null,null,FarolSemanticCardStage32.Provenance(null,FarolSemanticCardStage32.OwnerConfidence.UNKNOWN,"test"),emptySet(),1)
        FarolForensicCardBlackBoxStage32.recordCandidate(2,"Ocr","Rua A 10","Rua B 20","sig")
        FarolForensicCardBlackBoxStage32.recordFinal(3,3,"Red",10.0,"OCR")
        assertEquals(FarolForensicCardBlackBoxStage32.Outcome.READ_SUCCESS_OCR,FarolForensicCardBlackBoxStage32.snapshots().last().outcome)
    }

    @Test fun exactCacheSuccessHasDistinctOutcome() {
        FarolForensicCardBlackBoxStage32.resetForTests(); val g=FarolSemanticCardStage32.SemanticGate(); val d=g.observe(signal())
        FarolForensicCardBlackBoxStage32.observeEvent(1,1,d,null,null,null,FarolSemanticCardStage32.Provenance(null,FarolSemanticCardStage32.OwnerConfidence.UNKNOWN,"test"),emptySet(),1)
        FarolForensicCardBlackBoxStage32.recordCacheHit(2); FarolForensicCardBlackBoxStage32.recordFinal(3,3,"Green",2.0,"CACHE")
        assertEquals(FarolForensicCardBlackBoxStage32.Outcome.READ_SUCCESS_CACHE,FarolForensicCardBlackBoxStage32.snapshots().last().outcome)
    }

    @Test fun screenshotFailureCanCloseUnreadCase() {
        FarolForensicCardBlackBoxStage32.resetForTests(); val g=FarolSemanticCardStage32.SemanticGate(); val d=g.observe(signal())
        FarolForensicCardBlackBoxStage32.observeEvent(1,1,d,null,null,null,FarolSemanticCardStage32.Provenance(null,FarolSemanticCardStage32.OwnerConfidence.UNKNOWN,"test"),emptySet(),1)
        FarolForensicCardBlackBoxStage32.markScreenshotFailure(2,2,5,true)
        assertEquals(FarolForensicCardBlackBoxStage32.Outcome.UNREAD_OCR_SCREENSHOT_FAILED,FarolForensicCardBlackBoxStage32.snapshots().last().outcome)
    }

    @Test fun screenshotError3PendingDoesNotPrematurelyCloseCase() {
        FarolForensicCardBlackBoxStage32.resetForTests(); val g=FarolSemanticCardStage32.SemanticGate(); val d=g.observe(signal())
        FarolForensicCardBlackBoxStage32.observeEvent(1,1,d,null,null,null,FarolSemanticCardStage32.Provenance(null,FarolSemanticCardStage32.OwnerConfidence.UNKNOWN,"test"),emptySet(),1)
        FarolForensicCardBlackBoxStage32.markScreenshotFailure(2,2,3,false)
        assertEquals(FarolForensicCardBlackBoxStage32.Outcome.OPEN,FarolForensicCardBlackBoxStage32.currentSnapshot()!!.outcome)
    }

    @Test fun ocrNoCandidateClosesWithExplicitReason() {
        FarolForensicCardBlackBoxStage32.resetForTests(); val g=FarolSemanticCardStage32.SemanticGate(); val d=g.observe(signal())
        FarolForensicCardBlackBoxStage32.observeEvent(1,1,d,null,null,null,FarolSemanticCardStage32.Provenance(null,FarolSemanticCardStage32.OwnerConfidence.UNKNOWN,"test"),emptySet(),1)
        FarolForensicCardBlackBoxStage32.markOcrNoCandidate(2,2)
        assertEquals(FarolForensicCardBlackBoxStage32.Outcome.UNREAD_OCR_NO_TWO_ADDRESSES,FarolForensicCardBlackBoxStage32.snapshots().last().outcome)
    }

    @Test fun reportContainsOwnerOutcomeAndTimeline() {
        FarolForensicCardBlackBoxStage32.resetForTests(); val g=FarolSemanticCardStage32.SemanticGate(); val d=g.observe(signal())
        FarolForensicCardBlackBoxStage32.observeEvent(1,1,d,"com.app99.driver","com.app99.driver","com.app99.driver",FarolSemanticCardStage32.Provenance("com.app99.driver",FarolSemanticCardStage32.OwnerConfidence.DIRECT,"proof"),setOf("com.app99.driver"),1)
        val report=FarolForensicCardBlackBoxStage32.exportReport()
        assertTrue(report.contains("CASE-000001")); assertTrue(report.contains("owner=com.app99.driver")); assertTrue(report.contains("CASE_OPEN"))
    }

    @Test fun printFilenameContainsCaseOwnerAndTimestampSafeCharacters() {
        val name=FarolPrintStoreStage32.buildDisplayName("CASE-000123","99",0L)
        assertTrue(name.startsWith("RotaCerta_CASE-000123_99_")); assertTrue(name.endsWith(".png")); assertFalse(name.contains(":"))
    }

    @Test fun printSourceUsesMediaStorePicturesAndPng() {
        val s=File("src/main/java/br/com/mapeiaia/rotacerta/FarolPrintStoreStage32.kt").readText()
        assertTrue(s.contains("MediaStore.Images.Media.EXTERNAL_CONTENT_URI")); assertTrue(s.contains("Environment.DIRECTORY_PICTURES")); assertTrue(s.contains("image/png")); assertTrue(s.contains("IS_PENDING"))
    }

    @Test fun printIsExplicitNotAutomaticCaseSideEffect() {
        val s=File("src/main/java/br/com/mapeiaia/rotacerta/FarolForensicCardBlackBoxStage32.kt").readText()
        assertFalse(s.contains("MediaStore")); assertFalse(s.contains("savePng("))
    }

    @Test fun stage32DoesNotContainSleepTimerOrPollingPrimitives() {
        val s=File("src/main/java/br/com/mapeiaia/rotacerta/FarolSemanticCardStage32.kt").readText()
        listOf("Thread.sleep(","SystemClock.sleep(","Timer(","scheduleAtFixedRate(","while (").forEach { assertFalse("forbidden $it",s.contains(it)) }
    }

    @Test fun packageProvenanceNeverActsAsReadGateInSemanticHelper() {
        val s=File("src/main/java/br/com/mapeiaia/rotacerta/FarolSemanticCardStage32.kt").readText()
        assertTrue(s.contains("never authorizes visible content")); assertFalse(s.contains("MAX_ROUTE_KM")); assertFalse(s.contains("MAX_DISTANCE"))
    }

    @Test fun androidScreenshotIntervalConstantMatchesFrameworkContract() {
        assertEquals(333L,FarolSemanticCardStage32.ANDROID_SCREENSHOT_MIN_INTERVAL_MS)
    }

    @Test fun semanticFingerprintChangesOnConfirmedDestinationStage34() {
        val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); val a=g.observeCandidate("Rua A|Rua B 20").fingerprint; val b=g.observeCandidate("Rua A|Rua C 30").fingerprint; assertNotEquals(a,b)
    }

}
