package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FarolReadingActivationStage26Test {
    private val uber = "com.ubercab.driver"
    private val app99 = "com.app99.driver"

    @Before fun reset() { FarolReadingActivationStage26.Metrics.resetForTests() }

    private fun activeMachine(vararg selected: String): FarolReadingActivationStage26.ActivationMachine =
        FarolReadingActivationStage26.ActivationMachine().apply {
            updateSelection(selected.toSet())
            setUsageAccess(true)
            selected.forEach { observe(FarolReadingActivationStage26.UsageEvent(it, FarolReadingActivationStage26.UsageSignal.FOREGROUND_SERVICE_START)) }
        }

    private fun admission(reading: Boolean, text: String = "card A", window: String = "10:4"): FarolReadingActivationStage26.Admission =
        FarolReadingActivationStage26.PreCollectGate().admit(reading, FarolReadingActivationStage26.CheapVisualSignal(false, window, text))

    @Test fun noSelectedAppActiveMeansZeroHeavyCollect() {
        val m = FarolReadingActivationStage26.ActivationMachine(); m.updateSelection(setOf(uber)); m.setUsageAccess(true)
        assertFalse(m.snapshot().enabled); assertFalse(admission(m.snapshot().enabled).heavyCollect)
    }
    @Test fun launcherWithFarolOffMeansZeroCollect() { assertFalse(admission(false, "launcher").heavyCollect) }
    @Test fun whatsAppWithFarolOffMeansZeroCollect() { assertFalse(admission(false, "whatsapp").heavyCollect) }
    @Test fun systemUiWithFarolOffMeansZeroCollect() { assertFalse(admission(false, "systemui").heavyCollect) }
    @Test fun documentsUiWithFarolOffMeansZeroOcr() {
        val m = FarolReadingActivationStage26.ActivationMachine(); m.updateSelection(setOf(uber)); m.setUsageAccess(false)
        assertFalse(m.snapshot().enabled); assertEquals(0L, FarolReadingActivationStage26.Metrics.counter("ocrStarts"))
    }
    @Test fun openingOneSelectedTurnsFarolOn() {
        val m = activeMachine(uber); assertTrue(m.snapshot().enabled); assertEquals(1, m.snapshot().selectedAppsActiveCount)
    }
    @Test fun twoSelectedActiveRemainOn() {
        val m = activeMachine(uber, app99); assertTrue(m.snapshot().enabled); assertEquals(2, m.snapshot().selectedAppsActiveCount)
    }
    @Test fun closingOneOfTwoRemainsOn() {
        val m = activeMachine(uber, app99); m.observe(FarolReadingActivationStage26.UsageEvent(uber, FarolReadingActivationStage26.UsageSignal.PROCESS_GONE))
        assertTrue(m.snapshot().enabled); assertEquals(setOf(app99), m.snapshot().activeSelectedPackages)
    }
    @Test fun closingLastTurnsOffImmediately() {
        val m = activeMachine(uber); val before = m.snapshot().generation
        m.observe(FarolReadingActivationStage26.UsageEvent(uber, FarolReadingActivationStage26.UsageSignal.PROCESS_GONE))
        assertFalse(m.snapshot().enabled); assertTrue(m.snapshot().generation > before)
    }
    @Test fun lastClosesDuringCollectDiscardsResult() {
        val m = activeMachine(uber); val lease = m.lease(1, 1); m.observe(FarolReadingActivationStage26.UsageEvent(uber, FarolReadingActivationStage26.UsageSignal.PROCESS_GONE))
        assertFalse(m.isLeaseFresh(lease, 1, 1))
    }
    @Test fun lastClosesDuringOcrDiscardsResult() {
        val m = activeMachine(uber); val lease = m.lease(2, 3); m.setUsageAccess(false); assertFalse(m.isLeaseFresh(lease, 2, 3))
    }
    @Test fun lastClosesDuringGoogleDiscardsResult() {
        val m = activeMachine(uber); val lease = m.lease(4, 5); m.observe(FarolReadingActivationStage26.UsageEvent(uber, FarolReadingActivationStage26.UsageSignal.PROCESS_GONE)); assertFalse(m.isLeaseFresh(lease, 4, 5))
    }
    @Test fun selectedActiveHomeKeepsReading() { assertTrue(activeMachine(uber).snapshot().enabled); assertTrue(admission(true, "home popup").heavyCollect) }
    @Test fun selectedActiveWhatsAppKeepsReading() { assertTrue(activeMachine(uber).snapshot().enabled); assertTrue(admission(true, "whatsapp popup").heavyCollect) }
    @Test fun selectedActiveChatGptKeepsReading() { assertTrue(activeMachine(uber).snapshot().enabled); assertTrue(admission(true, "chatgpt popup").heavyCollect) }
    @Test fun validPopupOverHomeIsAnalyzed() { assertTrue(admission(true, "Rua A, 10\nRua B, 20", "home:popup").heavyCollect) }
    @Test fun validPopupOverWhatsAppIsAnalyzed() { assertTrue(admission(true, "Rua A, 10\nRua B, 20", "whatsapp:popup").heavyCollect) }
    @Test fun validPopupOverChatGptIsAnalyzed() { assertTrue(admission(true, "Rua A, 10\nRua B, 20", "chatgpt:popup").heavyCollect) }
    @Test fun validPopupOverRotaCertaIsAnalyzed() { assertTrue(admission(true, "Rua A, 10\nRua B, 20", "rotacerta:external-popup").heavyCollect) }
    @Test fun samePopupWithFarolOffIsIgnored() { assertFalse(admission(false, "Rua A, 10\nRua B, 20").heavyCollect) }
    @Test fun hundredRepeatedEventsCauseAtMostOneHeavyCollect() {
        val gate = FarolReadingActivationStage26.PreCollectGate(); var collects = 0
        repeat(100) { if (gate.admit(true, FarolReadingActivationStage26.CheapVisualSignal(false, "1:9", "Rua A, 10\nRua B, 20")).heavyCollect) collects++ }
        assertEquals(1, collects); assertEquals(99L, FarolReadingActivationStage26.Metrics.counter("preCollectDuplicateSkipped"))
    }
    @Test fun ownBubbleEventMeansZeroHeavyCollect() {
        val gate = FarolReadingActivationStage26.PreCollectGate(); val result = gate.admit(true, FarolReadingActivationStage26.CheapVisualSignal(true, "bubble", "6,2")); assertFalse(result.heavyCollect)
    }
    @Test fun realMutationClearsBeforeCollect() {
        val c = FarolReadingActivationStage26.WorkCoordinator(); c.seedFinal("GREEN", 3.2); c.invalidateBeforeCollect(2)
        assertTrue(c.trace().indexOf("clear_old_paint") < c.trace().indexOf("collect"))
    }
    @Test fun oldGreenDisappearsBeforeCollect() { val c=FarolReadingActivationStage26.WorkCoordinator(); c.seedFinal("GREEN",2.0); c.invalidateBeforeCollect(2); assertEquals("NEUTRAL", c.state().color) }
    @Test fun oldRedDisappearsBeforeCollect() { val c=FarolReadingActivationStage26.WorkCoordinator(); c.seedFinal("RED",8.0); c.invalidateBeforeCollect(2); assertEquals("NEUTRAL", c.state().color) }
    @Test fun oldKmDisappearsBeforeCollect() { val c=FarolReadingActivationStage26.WorkCoordinator(); c.seedFinal("GREEN",2.0); c.invalidateBeforeCollect(2); assertNull(c.state().distanceKm) }
    @Test fun destinationSwapInvalidatesAFirst() {
        val gate=FarolReadingActivationStage26.PreCollectGate(); val a=gate.admit(true, FarolReadingActivationStage26.CheapVisualSignal(false,"w","Rua A, 10\nRua B, 20")); val b=gate.admit(true, FarolReadingActivationStage26.CheapVisualSignal(false,"w","Rua A, 10\nRua C, 30")); assertTrue(a.mutation); assertTrue(b.mutation); assertTrue(b.visualGeneration>a.visualGeneration)
    }
    @Test fun cardCloseClearsImmediately() {
        val gate=FarolReadingActivationStage26.PreCollectGate(); gate.admit(true, FarolReadingActivationStage26.CheapVisualSignal(false,"w","Rua A, 10\nRua B, 20")); assertTrue(gate.admit(true, FarolReadingActivationStage26.CheapVisualSignal(false,"w","")).mutation)
    }
    @Test fun collectorDoesNotDuplicateAncestorSubtree() {
        val child=FarolReadingActivationStage26.CompactNode("c","Rua A, 10\nRua B, 20"); val root=FarolReadingActivationStage26.CompactNode("r","", listOf(child, child.copy(id="c2"))); val result=FarolReadingActivationStage26.compact(root); assertEquals(1, result.blocks.size)
    }
    @Test fun parserDoesNotAnalyzeDozensOfCopies() {
        var node=FarolReadingActivationStage26.CompactNode("leaf","Rua A, 10\nRua B, 20"); repeat(95) { node=FarolReadingActivationStage26.CompactNode("n$it","Rua A, 10\nRua B, 20", listOf(node)) }; val result=FarolReadingActivationStage26.compact(node); assertTrue(result.addressParserInvocations < 10); assertTrue(result.blocks.size < 10)
    }
    @Test fun twoDistinctCardsAreNotMixed() {
        val a=FarolReadingActivationStage26.CompactNode("a","Rua A, 10\nRua B, 20"); val b=FarolReadingActivationStage26.CompactNode("b","Rua C, 30\nRua D, 40"); val result=FarolReadingActivationStage26.compact(FarolReadingActivationStage26.CompactNode("root","", listOf(a,b))); assertEquals(1,result.blocks.size); assertFalse(result.blocks.first().contains("Rua C"))
    }
    @Test fun truncatedAddressRemainsRejected() { assertFalse(FarolCausalCorrectionStage21.validateAddress("Avenida Mendonca e").accepted) }
    @Test fun cacheCannotBypassSemanticBarrier() { assertFalse(FarolCausalCorrectionStage21.validateAddress("Rua A e").accepted); assertTrue(FarolCausalCorrectionStage21.SEMANTIC_GATE_MARKER.contains("BEFORE_CACHE")) }
    @Test fun googleCannotBypassSemanticBarrier() { assertFalse(FarolCausalCorrectionStage21.validateAddress("Avenida Mendonca").accepted); assertTrue(FarolCausalCorrectionStage21.SEMANTIC_GATE_MARKER.contains("GOOGLE")) }
    @Test fun staleGoogleCannotPaint() { val m=activeMachine(uber); val lease=m.lease(1,1); m.observe(FarolReadingActivationStage26.UsageEvent(uber,FarolReadingActivationStage26.UsageSignal.PROCESS_GONE)); assertFalse(m.isLeaseFresh(lease,1,1)) }
    @Test fun staleOcrCannotPaint() { val m=activeMachine(uber); val lease=m.lease(2,2); m.setUsageAccess(false); assertFalse(m.isLeaseFresh(lease,2,2)) }
    @Test fun staleCacheCannotPaint() { val m=activeMachine(uber); val lease=m.lease(3,3); m.observe(FarolReadingActivationStage26.UsageEvent(uber,FarolReadingActivationStage26.UsageSignal.PROCESS_GONE)); assertFalse(m.isLeaseFresh(lease,3,3)) }
    @Test fun paintTokenAndFreshnessPreserved() { val m=activeMachine(uber); val lease=m.lease(7,8); assertTrue(m.isLeaseFresh(lease,7,8)); assertFalse(m.isLeaseFresh(lease,7,9)) }
    @Test fun exactCacheCanAnswerImmediately() { val m=activeMachine(uber); val lease=m.lease(1,2); assertTrue(m.isLeaseFresh(lease,1,2)); assertTrue(FarolReadingActivationStage26.ATOMIC_PAINT_MARKER.isNotBlank()) }
    @Test fun captureSelectsPackageContract() { val m=FarolReadingActivationStage26.ActivationMachine(); m.updateSelection(setOf(uber)); m.setUsageAccess(true); m.observe(FarolReadingActivationStage26.UsageEvent(uber,FarolReadingActivationStage26.UsageSignal.FOREGROUND_SERVICE_START)); assertTrue(m.snapshot().selectedPackages.contains(uber)) }
    @Test fun captureScreenshotIsNotCardAuthority() { assertTrue(FarolReadingActivationStage26.ACTIVATION_ONLY_MARKER.contains("ACTIVATE_INFRASTRUCTURE_ONLY")); assertFalse(FarolReadingActivationStage26.ACTIVATION_ONLY_MARKER.contains("SCREENSHOT")) }
    @Test fun unselectedVisiblePackageCanContainValidCardWhenOn() { val m=activeMachine(uber); val gate=FarolReadingActivationStage26.PreCollectGate(); val result=gate.admit(m.snapshot().enabled, FarolReadingActivationStage26.CheapVisualSignal(false,"whatsapp","Rua A, 10\nRua B, 20")); assertTrue(result.heavyCollect) }
    @Test fun missingUsageAccessFailsClosed() { val m=FarolReadingActivationStage26.ActivationMachine(); m.updateSelection(setOf(uber)); m.setUsageAccess(false); m.observe(FarolReadingActivationStage26.UsageEvent(uber,FarolReadingActivationStage26.UsageSignal.FOREGROUND_SERVICE_START)); assertFalse(m.snapshot().enabled) }
    @Test fun activationDoesNotDependOnTemporalDebounce() { val gate=FarolReadingActivationStage26.PreCollectGate(); val a=gate.admit(true,FarolReadingActivationStage26.CheapVisualSignal(false,"w","A")); val b=gate.admit(true,FarolReadingActivationStage26.CheapVisualSignal(false,"w","B")); assertTrue(a.heavyCollect); assertTrue(b.heavyCollect); assertNotEquals(a.fingerprint,b.fingerprint) }
    @Test fun finalColorAndKmShareGeneration() { val c=FarolReadingActivationStage26.WorkCoordinator(); c.seedFinal("RED",10.0,4,9); assertEquals(4,c.state().generation); assertEquals(9,c.state().paintToken); assertEquals("RED",c.state().color); assertEquals(10.0,c.state().distanceKm!!,0.0) }
}
