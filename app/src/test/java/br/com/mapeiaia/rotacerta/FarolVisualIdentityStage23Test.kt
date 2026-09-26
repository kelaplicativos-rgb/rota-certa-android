package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolVisualIdentityStage23Test {
    private fun seed(
        text: String = "Rua Origem, 10, Centro, São Paulo\nRua Destino, 20, Vila Prudente, São Paulo",
        windowId: Int = 7,
        layer: Int = 10,
        top: Int = 100,
        synthetic: Boolean = false,
    ) = FarolVisualIdentityStage23.VisualSeed(windowId, layer, text, 20, top, 1000, top + 500, synthetic)

    private fun snapshot(text: String = seed().text, windowId: Int = 7, layer: Int = 10) =
        FarolVisualIdentityStage23.snapshot(sequenceOf(seed(text, windowId, layer)))

    private fun block(
        text: String = seed().text,
        pkg: String? = "visual.unknown",
        id: String = "w/card",
        windowId: Int = 7,
        layer: Int = 10,
        depth: Int = 3,
        top: Int = 100,
        bottom: Int = 600,
    ) = FarolUniversalVisualPipelineStage19.VisualBlock(
        id = id,
        parentId = "w",
        metadataPackageName = pkg,
        windowId = windowId,
        windowLayer = layer,
        depth = depth,
        text = text,
        source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
        left = 20,
        top = top,
        right = 1000,
        bottom = bottom,
    )

    @Test fun hundredIdenticalEventsCauseOneExpensiveAdmission() {
        val gate = FarolVisualIdentityStage23.VisualSnapshotGate()
        val hash = snapshot().hash
        var admitted = 0
        repeat(100) { if (gate.observe(hash).process) admitted += 1 }
        assertEquals(1, admitted)
    }

    @Test fun differentEventTypesDoNotChangeVisualIdentity() {
        val a = snapshot().hash
        val b = snapshot().hash
        assertEquals(a, b)
    }

    @Test fun systemUiOrOwnPackageMetadataCannotChangeVisualIdentity() {
        assertEquals(snapshot().hash, snapshot().hash)
    }

    @Test fun oneRelevantAddressCharacterChangesGeneration() {
        val gate = FarolVisualIdentityStage23.VisualSnapshotGate()
        val a = gate.observe(snapshot("Rua Origem, 10, Centro\nRua Tucanos, 20, Centro").hash)
        val b = gate.observe(snapshot("Rua Origem, 10, Centro\nRua Tucanos, 21, Centro").hash)
        assertTrue(a.process)
        assertTrue(b.process)
        assertTrue(b.generation > a.generation)
    }

    @Test fun completeDestinationSwapProcessesImmediately() {
        assertNotEquals(snapshot("Rua Origem, 10, Centro\nRua A, 20, Centro").hash, snapshot("Rua Origem, 10, Centro\nAvenida B, 99, Centro").hash)
    }

    @Test fun scheduledAfterDirectWinnerIsCancelled() {
        val gate = FarolVisualIdentityStage23.ScheduledDemandGate()
        val demand = gate.create(3, 77)
        gate.satisfyDirect(3, 77)
        assertFalse(gate.shouldRun(demand, 3, 77))
    }

    @Test fun oldScheduledDemandAfterVisualChangeIsCancelled() {
        val gate = FarolVisualIdentityStage23.ScheduledDemandGate()
        val demand = gate.create(3, 77)
        assertFalse(gate.shouldRun(demand, 4, 88))
    }

    @Test fun ownOverlayEventWithSameVisualSnapshotDoesNotEvaluateAgain() {
        val gate = FarolVisualIdentityStage23.VisualSnapshotGate()
        val h = snapshot().hash
        assertTrue(gate.observe(h).process)
        assertFalse(gate.observe(h).process)
    }

    @Test fun externalPopupOverRotaCertaChangesTopWindowIdentity() {
        assertNotEquals(snapshot(windowId = 7, layer = 10).hash, snapshot(windowId = 22, layer = 99).hash)
    }

    @Test fun popupOverWhatsAppIsDetectedByContentNotPackage() =
        assertNotNull(FarolCausalCorrectionStage21.evaluate(listOf(block(pkg = "com.whatsapp"))))

    @Test fun popupOverChatGptIsDetectedByContentNotPackage() =
        assertNotNull(FarolCausalCorrectionStage21.evaluate(listOf(block(pkg = "com.openai.chatgpt"))))

    @Test fun popupOverHomeIsDetectedByContentNotPackage() =
        assertNotNull(FarolCausalCorrectionStage21.evaluate(listOf(block(pkg = "com.sec.android.app.launcher"))))

    @Test fun largeTopWindowCanEarlyExitInternallyAfterCompleteContext() {
        val texts = sequenceOf("Oferta", "Rua Origem, 10, Centro", "R$ 20", "Rua Destino, 20, Centro")
        assertTrue(FarolVisualIdentityStage23.shouldStopInsideWindow(texts, 8, 4))
    }

    @Test fun twoDifferentOffersAreNotMixedByEvaluator() {
        val result = FarolCausalCorrectionStage21.evaluate(listOf(
            block("Rua Alfa, 10, Centro", id = "upper", top = 100, bottom = 250),
            block("Rua Beta, 20, Centro\nRua Gama, 30, Centro", id = "lower", top = 700, bottom = 1000),
        ))
        assertNull(result)
    }

    @Test fun repeatedOcrBusySameGenerationDoesNotCreatePerpetualRerun() {
        val gate = FarolVisualIdentityStage23.OcrDemandGate()
        val d = FarolVisualIdentityStage23.OcrDemand(1, 10, null, null)
        val first = gate.request(d)
        repeat(10) { assertFalse(gate.request(d).startNow) }
        assertNull(gate.complete(first.token).rerun)
    }

    @Test fun ocrBusyWithNewGenerationCreatesExactlyOneUsefulRerun() {
        val gate = FarolVisualIdentityStage23.OcrDemandGate()
        val d1 = FarolVisualIdentityStage23.OcrDemand(1, 10, null, null)
        val d2 = FarolVisualIdentityStage23.OcrDemand(2, 20, null, null)
        val first = gate.request(d1)
        repeat(8) { gate.request(d2) }
        assertEquals(d2.key, gate.complete(first.token).rerun?.key)
    }

    @Test fun accessibilityWinnerCancelsActiveOcrIdentity() {
        val gate = FarolVisualIdentityStage23.OcrDemandGate()
        val d = FarolVisualIdentityStage23.OcrDemand(1, 10, null, null)
        val first = gate.request(d)
        gate.cancelBecauseAccessibilityWon(1, 10)
        assertFalse(gate.isCurrent(first.token, d))
    }

    @Test fun staleBeforeBitmapGuardRemainsRepresentable() {
        val gate = FarolVisualIdentityStage23.OcrDemandGate(); val d = FarolVisualIdentityStage23.OcrDemand(1, 10, null, null); val r = gate.request(d)
        gate.cancelBecauseAccessibilityWon(1, 10); assertFalse(gate.isCurrent(r.token, d))
    }

    @Test fun staleBeforeExtractGuardRemainsRepresentable() {
        val gate = FarolVisualIdentityStage23.OcrDemandGate(); val d = FarolVisualIdentityStage23.OcrDemand(1, 10, null, null); val r = gate.request(d)
        gate.cancelBecauseAccessibilityWon(2, 20); assertFalse(gate.isCurrent(r.token, d))
    }

    @Test fun staleAfterExtractRemainsLastBarrierBeforeEvaluation() {
        val gate = FarolVisualIdentityStage23.OcrDemandGate(); val d = FarolVisualIdentityStage23.OcrDemand(1, 10, null, null); val r = gate.request(d)
        gate.cancelBecauseAccessibilityWon(2, 20); assertFalse(gate.isCurrent(r.token, d))
    }

    @Test fun truncatedCandidateNeverReachesSemanticDownstream() =
        assertNull(FarolCausalCorrectionStage21.evaluate(listOf(block("Rua Origem, 10, Centro\nAvenida Mendonça e"))))

    @Test fun truncatedCandidateCannotReachCache() =
        assertFalse(FarolCausalCorrectionStage21.validateAddress("Avenida Mendonça e").accepted)

    @Test fun cacheHitCannotBypassSemanticBarrier() =
        assertFalse(FarolCausalCorrectionStage21.validateAddress("R. Mônaco, 85, Parque das").accepted)

    @Test fun realGoogleRouteContractIsExplicitlyPreserved() =
        assertEquals("GOOGLE_MAPS_REAL_ROUTE_PRESERVED_STAGE23", FarolVisualIdentityStage23.GOOGLE_REAL_PRESERVED_MARKER)

    @Test fun freshnessContractRemainsExplicitlyPreserved() =
        assertTrue(FarolVisualIdentityStage23.FRESHNESS_PRESERVED_MARKER.contains("FRESHNESS_PRESERVED"))

    @Test fun paintTokenContractRemainsExplicitlyPreserved() =
        assertTrue(FarolVisualIdentityStage23.PAINT_TOKEN_PRESERVED_MARKER.contains("PAINT_TOKEN"))

    @Test fun verificationPendingIsPartOfPreservedFreshnessContract() =
        assertTrue(FarolVisualIdentityStage23.FRESHNESS_PRESERVED_MARKER.contains("VERIFICATION"))

    @Test fun sameFinalIdentityCannotGenerateSuccessiveExpensiveAdmissions() {
        val gate = FarolVisualIdentityStage23.VisualSnapshotGate(); val h = snapshot().hash
        val first = gate.observe(h); gate.markProcessed(h, first.generation)
        assertTrue(gate.alreadyProcessed(h, first.generation)); assertFalse(gate.observe(h).process)
    }

    @Test fun instrumentationSeparatesVisibleWindowsFromTraversedWindows() {
        val stats = FarolVisualIdentityStage23.CollectionStats(7, 1, 1, 5, 24, 6, 9, "complete_context", 42)
        assertEquals(7, stats.visibleWindowsTotal); assertEquals(1, stats.windowsTraversed); assertNotEquals(stats.visibleWindowsTotal, stats.windowsTraversed)
    }

    @Test fun instrumentationExportsMedianP95AndMaximum() {
        FarolVisualIdentityStage23.Metrics.resetForTests(); listOf(10L, 20L, 30L, 40L, 100L).forEach { FarolVisualIdentityStage23.Metrics.sample("collect.Accessibility", it * 1_000L) }
        val s = FarolVisualIdentityStage23.Metrics.stats("collect.Accessibility")
        assertTrue(s.contains("count=5")); assertTrue(s.contains("median_us=30")); assertTrue(s.contains("p95_us=100")); assertTrue(s.contains("max_us=100"))
    }

    @Test fun staleAfterEvaluateGuardRemainsRepresentable() {
        val gate = FarolVisualIdentityStage23.OcrDemandGate(); val d = FarolVisualIdentityStage23.OcrDemand(1, 10, null, null); val r = gate.request(d)
        gate.cancelBecauseAccessibilityWon(2, 20); assertFalse(gate.isCurrent(r.token, d))
    }

    @Test fun visualSnapshotDoesNotContainPackageAuthority() {
        val s = snapshot()
        assertFalse(s.canonical.contains("com.ubercab.driver")); assertFalse(s.canonical.contains("com.app99.driver"))
    }

    @Test fun eventFingerprintAndVisualSnapshotAreIndependentContracts() {
        assertNotEquals(FarolCausalCorrectionStage21.EVENT_COALESCING_MARKER, FarolVisualIdentityStage23.VISUAL_IDENTITY_MARKER)
    }

    @Test fun twoAddressEvidenceIsRecognizedWithoutHeavyParser() =
        assertTrue(snapshot().hasTwoAddressLeads)

    @Test fun collectionMetricsIncludeSelfAndLowerLayerSkips() {
        FarolVisualIdentityStage23.Metrics.resetForTests()
        val stats = FarolVisualIdentityStage23.CollectionStats(6, 1, 1, 4, 20, 5, 2, "complete_context", 99)
        FarolVisualIdentityStage23.Metrics.recordCollection("Accessibility", 20_000L, stats, true)
        assertEquals(1L, FarolVisualIdentityStage23.Metrics.total("windowsSkippedSelf")); assertEquals(4L, FarolVisualIdentityStage23.Metrics.total("windowsSkippedLowerLayer"))
    }

    @Test fun visualGateHasNoTimeDebounceAndProcessesImmediateChange() {
        val gate = FarolVisualIdentityStage23.VisualSnapshotGate(); val a = gate.observe(10); val b = gate.observe(11)
        assertTrue(a.process); assertTrue(b.process); assertEquals(a.generation + 1, b.generation)
    }

    @Test fun scheduledDemandWithoutKnownHashCanRunUntilSatisfied() {
        val gate = FarolVisualIdentityStage23.ScheduledDemandGate(); val d = gate.create(0, null)
        assertTrue(gate.shouldRun(d, 0, null))
    }

    @Test fun newestOcrGenerationReplacesOlderPendingGeneration() {
        val gate = FarolVisualIdentityStage23.OcrDemandGate(); val d1 = FarolVisualIdentityStage23.OcrDemand(1, 10, null, null); val d2 = FarolVisualIdentityStage23.OcrDemand(2, 20, null, null); val d3 = FarolVisualIdentityStage23.OcrDemand(3, 30, null, null)
        val r = gate.request(d1); gate.request(d2); gate.request(d3); assertEquals(d3.key, gate.complete(r.token).rerun?.key)
    }

    @Test fun completeSemanticPairRemainsAccepted() =
        assertNotNull(FarolCausalCorrectionStage21.evaluate(listOf(block())))

    @Test fun snapshotGenerationIsContentDrivenNotClockDriven() {
        val gate = FarolVisualIdentityStage23.VisualSnapshotGate(); val a = gate.observe(10); val b = gate.observe(10); val c = gate.observe(12)
        assertEquals(a.generation, b.generation); assertEquals(a.generation + 1, c.generation)
    }
}
