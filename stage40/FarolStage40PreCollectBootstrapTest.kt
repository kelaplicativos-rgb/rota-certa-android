package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage40PreCollectBootstrapTest {
    private fun root(): File {
        val c = File(System.getProperty("user.dir"))
        return if (File(c, "app/src/main/java").isDirectory) c else if (c.name == "app" && File(c, "src/main/java").isDirectory) c.parentFile else c
    }
    private fun src(name: String) = File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()
    private fun signal(
        window: String = "10:visual",
        address: String = "",
        structure: String = "com.example:2048:0:0:22:48:root",
        bootstrap: String = "",
        eligible: Boolean = true,
        own: Boolean = false,
        eventType: Int = 2048,
        change: Int = 1,
    ) = FarolReadingActivationStage26.CheapVisualSignal(
        ownOverlay = own,
        windowSignature = window,
        sourceText = address,
        sourceSlot = "slot:full",
        contentChangeTypes = change,
        eventType = eventType,
        structuralSignature = structure,
        bootstrapText = bootstrap,
        bootstrapEligible = eligible,
    )

    @Test fun smallBlankLeafDoesNotBootstrap() {
        val d = FarolReadingActivationStage26.PreCollectGate().admit(true, signal(eligible=false, structure=""))
        assertFalse(d.heavyCollect); assertEquals("stage40_bootstrap_not_eligible", d.reason)
    }

    @Test fun eligibleLargeBlankSurfaceBootstrapsOnce() {
        val g = FarolReadingActivationStage26.PreCollectGate()
        val a = g.admit(true, signal())
        val b = g.admit(true, signal())
        assertTrue(a.heavyCollect); assertEquals("stage40_structural_bootstrap", a.reason)
        assertFalse(b.heavyCollect); assertEquals("stage40_bootstrap_duplicate_coalesced", b.reason)
    }

    @Test fun differentStableStructureGetsOneNewBootstrap() {
        val g = FarolReadingActivationStage26.PreCollectGate()
        assertTrue(g.admit(true, signal(structure="surface:a")).heavyCollect)
        assertTrue(g.admit(true, signal(structure="surface:b")).heavyCollect)
        assertFalse(g.admit(true, signal(structure="surface:b")).heavyCollect)
    }

    @Test fun rawWindowIdDoesNotMakeSameStructureNew() {
        val g = FarolReadingActivationStage26.PreCollectGate()
        assertTrue(g.admit(true, signal(window="100:uber", structure="surface:stable")).heavyCollect)
        assertFalse(g.admit(true, signal(window="101:uber", structure="surface:stable")).heavyCollect)
    }

    @Test fun meaningfulBootstrapTextChangeRetriggersSameStructure() {
        val g = FarolReadingActivationStage26.PreCollectGate()
        assertTrue(g.admit(true, signal(bootstrap="Pedido de viagem Jessica Viana")).heavyCollect)
        val d = g.admit(true, signal(bootstrap="Pedido de viagem Carlos Silva"))
        assertTrue(d.heavyCollect); assertEquals("stage40_bootstrap_content_changed", d.reason)
    }

    @Test fun volatilePriceTimeDistanceDoNotRetrigger() {
        val g = FarolReadingActivationStage26.PreCollectGate()
        assertTrue(g.admit(true, signal(bootstrap="Pedido de viagem 2 min R$ 18 ~624 m")).heavyCollect)
        assertFalse(g.admit(true, signal(bootstrap="Pedido de viagem 3 min R$ 25 ~800 m")).heavyCollect)
    }

    @Test fun addressEvidenceTriggersEvenWhenBootstrapNotEligible() {
        val d = FarolReadingActivationStage26.PreCollectGate().admit(true, signal(address="Rua Angelo Malanga 262", eligible=false, structure=""))
        assertTrue(d.heavyCollect); assertEquals("stage40_first_address_evidence", d.reason)
    }

    @Test fun changedAddressRetriggersImmediately() {
        val g = FarolReadingActivationStage26.PreCollectGate()
        val a = g.admit(true, signal(address="Rua A 10", eligible=false))
        val b = g.admit(true, signal(address="Rua B 20", eligible=false))
        assertTrue(a.heavyCollect); assertTrue(b.heavyCollect); assertTrue(b.visualGeneration > a.visualGeneration)
    }

    @Test fun sameAddressAcrossWindowChurnCoalesces() {
        val g = FarolReadingActivationStage26.PreCollectGate()
        assertTrue(g.admit(true, signal(window="10:uber", address="Rua A 10", eligible=false)).heavyCollect)
        assertFalse(g.admit(true, signal(window="11:systemui", address="Rua A 10", eligible=false)).heavyCollect)
    }

    @Test fun addressClearSameContextForcesVerification() {
        val g = FarolReadingActivationStage26.PreCollectGate()
        g.admit(true, signal(address="Rua A 10", eligible=false))
        val d = g.admit(true, signal(address="", eligible=false))
        assertTrue(d.heavyCollect); assertEquals("stage40_same_context_content_cleared_verify", d.reason)
    }

    @Test fun readingOffNeverBootstraps() {
        val d = FarolReadingActivationStage26.PreCollectGate().admit(false, signal())
        assertFalse(d.heavyCollect); assertEquals("reading_off", d.reason)
    }

    @Test fun ownOverlayNeverBootstraps() {
        val d = FarolReadingActivationStage26.PreCollectGate().admit(true, signal(own=true))
        assertFalse(d.heavyCollect); assertEquals("own_overlay", d.reason)
    }

    @Test fun universalScreenPackageCanBootstrapWhenReadingOn() {
        val g = FarolReadingActivationStage26.PreCollectGate()
        assertTrue(g.admit(true, signal(window="3254:com.openai.chatgpt", structure="com.openai.chatgpt:surface")).heavyCollect)
    }

    @Test fun historicalThreeArgumentBlankSignalStillSkips() {
        val d = FarolReadingActivationStage26.PreCollectGate().admit(true, FarolReadingActivationStage26.CheapVisualSignal(false,"w",""))
        assertFalse(d.heavyCollect); assertEquals("stage40_bootstrap_not_eligible", d.reason)
    }

    @Test fun historicalThreeArgumentAddressSignalStillWorks() {
        val g = FarolReadingActivationStage26.PreCollectGate()
        val a = g.admit(true, FarolReadingActivationStage26.CheapVisualSignal(false,"w","Rua A 10"))
        val b = g.admit(true, FarolReadingActivationStage26.CheapVisualSignal(false,"w","Rua A 10"))
        assertTrue(a.heavyCollect); assertFalse(b.heavyCollect); assertNotEquals(0L, a.visualGeneration)
    }

    @Test fun cheapBuilderScansPastSixthEventFragment() {
        val s = src("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("eventStage26.text }.getOrDefault(emptyList()).take(16)"))
        assertFalse(s.contains("eventStage26.text }.getOrDefault(emptyList()).take(6).forEach(::addStage28)"))
    }

    @Test fun bootstrapEligibilityRequiresWindowOrLargeSurface() {
        val s = src("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED"))
        assertTrue(s.contains("AccessibilityEvent.TYPE_WINDOWS_CHANGED"))
        assertTrue(s.contains("AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> sourceLargeStage40 || parentLargeStage40"))
        assertTrue(s.contains("screenWidthStage40 * 55")); assertTrue(s.contains("screenHeightStage40 * 25"))
    }

    @Test fun addressAndBootstrapEvidenceAreSeparated() {
        val s = src("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("val addressStage40 = LinkedHashSet<String>(8)"))
        assertTrue(s.contains("val bootstrapStage40 = LinkedHashSet<String>(16)"))
        assertTrue(s.contains("sourceText = addressStage40.sorted()"))
        assertTrue(s.contains("bootstrapText = bootstrapStage40.sorted()"))
    }

    @Test fun normalStructuralSignatureDoesNotUseRawWindowId() {
        val s = src("LiveRideAccessibilityService.kt")
        val a = s.indexOf("val structuralSignatureStage40 = when")
        val b = s.indexOf("val ownEventStage40", a)
        val block = s.substring(a,b)
        assertTrue(block.contains("window-transition:${'$'}eventWindowIdStage26"))
        assertFalse(block.contains("append(eventWindowIdStage26)"))
    }

    @Test fun bootstrapCoalescerIsBoundedAndHasNoPolling() {
        val s = src("FarolReadingActivationStage26.kt")
        val a = s.indexOf("class PreCollectGate")
        val b = s.indexOf("data class PaintState", a)
        val gate = s.substring(a,b)
        listOf("Thread.sleep(", "SystemClock.sleep(", "Timer(", "scheduleAtFixedRate(", "delay(").forEach { assertFalse(gate.contains(it)) }
        assertTrue(gate.contains("bootstrapValueByStructure.size > 32"))
    }

    @Test fun directAcquisitionStillSatisfiesScheduledDemand() {
        val s = src("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("stage23ScheduleGate.satisfyDirect("))
    }

    @Test fun forensicRecordExplainsBoundedBootstrapDecision() {
        val s = src("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("reason=${'$'}{admissionStage26.reason}"))
        assertTrue(s.contains("bootstrapEligible=${'$'}{cheapSignalStage26.bootstrapEligible}"))
        assertTrue(s.contains("structural=${'$'}{cheapSignalStage26.structuralSignature.take(500)}"))
        assertTrue(s.contains("bootstrapText=${'$'}{cheapSignalStage26.bootstrapText.take(900)}"))
    }
}
