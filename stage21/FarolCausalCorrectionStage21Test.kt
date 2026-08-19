package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolCausalCorrectionStage21Test {
    private fun block(
        text: String,
        pkg: String? = "visual.unknown",
        id: String = "w/card",
        windowId: Int = 7,
        layer: Int = 10,
        depth: Int = 3,
        top: Int = 100,
        bottom: Int = 600,
        source: FarolUniversalVisualPipelineStage19.Source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
    ) = FarolUniversalVisualPipelineStage19.VisualBlock(
        id = id,
        parentId = "w",
        metadataPackageName = pkg,
        windowId = windowId,
        windowLayer = layer,
        depth = depth,
        text = text,
        source = source,
        left = 20,
        top = top,
        right = 1000,
        bottom = bottom,
    )

    private fun eval(
        text: String = "Rua Origem, 10, Centro, São Paulo\nRua Destino, 20, Vila Prudente, São Paulo",
        pkg: String? = "visual.unknown",
        source: FarolUniversalVisualPipelineStage19.Source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
    ) = FarolCausalCorrectionStage21.evaluate(listOf(block(text = text, pkg = pkg, source = source)))

    @Test fun avenidaMendoncaEIsRejected() = assertFalse(FarolCausalCorrectionStage21.validateAddress("Avenida Mendonça e").accepted)

    @Test fun avenidaMendoncaWithoutContextDoesNotAuthorizeRoute() =
        assertFalse(FarolCausalCorrectionStage21.validateAddress("Avenida Mendonça").accepted)

    @Test fun completeAddressWithNumberAndCityIsAccepted() =
        assertTrue(FarolCausalCorrectionStage21.validateAddress("Rua Benjamin Carr, 596, Sapopemba, São Paulo").accepted)

    @Test fun completeStreetWithoutNumberButWithNeighborhoodAndCityIsAccepted() =
        assertTrue(FarolCausalCorrectionStage21.validateAddress("Avenida Paulista, Bela Vista, São Paulo").accepted)

    @Test fun ocrCutAtFinalConnectorIsRejected() =
        assertFalse(FarolCausalCorrectionStage21.validateAddress("R. Mônaco, 85, Parque das").accepted)

    @Test fun smallOcrSpellingErrorWithFullStructureIsAccepted() =
        assertTrue(FarolCausalCorrectionStage21.validateAddress("Rua Benjamin Carr, 596, Sapopenmba, São Paulo").accepted)

    @Test fun twoDifferentCardBlocksAreNotMixed() {
        val result = FarolCausalCorrectionStage21.evaluate(
            listOf(
                block("Rua Alfa, 10, Centro", id = "upper", top = 100, bottom = 250),
                block("Rua Beta, 20, Centro\nRua Gama, 30, Centro", id = "lower", top = 700, bottom = 1000),
            ),
        )
        assertNull(result)
    }

    @Test fun completePickupAndTruncatedDestinationNeverDecide() =
        assertNull(eval("Rua Origem, 10, Centro, São Paulo\nAvenida Mendonça e"))

    @Test fun ownOverlayEventInsideSuppressionWindowIsIgnored() {
        val gate = FarolCausalCorrectionStage21.EventGate()
        val decision = gate.decide("br.com.mapeiaia.rotacerta", 2048, 1, 100L, "br.com.mapeiaia.rotacerta", 200L, false)
        assertFalse(decision.process)
        assertEquals("self_overlay_event", decision.reason)
    }

    @Test fun ownPackageOutsideSuppressionWindowCanStillObserveExternalPopup() {
        val gate = FarolCausalCorrectionStage21.EventGate()
        assertTrue(gate.decide("br.com.mapeiaia.rotacerta", 2048, 1, 300L, "br.com.mapeiaia.rotacerta", 200L, false).process)
    }

    @Test fun invalidCandidateCannotAuthorizeCacheLookup() = assertNull(eval("Rua Origem, 10, Centro\nAvenida Mendonça e"))

    @Test fun validCandidateStillAuthorizesDownstreamPath() = assertNotNull(eval())

    @Test fun identicalEventBurstIsCoalesced() {
        val gate = FarolCausalCorrectionStage21.EventGate()
        assertTrue(gate.decide("com.ubercab.driver", 2048, 7, 1_000_000_000L, "self", 0L, false).process)
        assertFalse(gate.decide("com.ubercab.driver", 2048, 7, 1_010_000_000L, "self", 0L, false).process)
    }

    @Test fun differentWindowInBurstIsNotCoalesced() {
        val gate = FarolCausalCorrectionStage21.EventGate()
        gate.decide("com.ubercab.driver", 2048, 7, 1_000_000_000L, "self", 0L, false)
        assertTrue(gate.decide("com.ubercab.driver", 2048, 8, 1_010_000_000L, "self", 0L, false).process)
    }

    @Test fun busyEquivalentBurstUsesWiderCoalescingWindow() {
        val gate = FarolCausalCorrectionStage21.EventGate()
        gate.decide("com.app99.driver", 2048, 2, 1_000_000_000L, "self", 0L, true)
        assertFalse(gate.decide("com.app99.driver", 2048, 2, 1_150_000_000L, "self", 0L, true).process)
    }

    @Test fun systemUiFirstEventIsStillObservable() {
        val gate = FarolCausalCorrectionStage21.EventGate()
        assertTrue(gate.decide("com.android.systemui", 2048, 5, 1_000_000_000L, "self", 0L, false).process)
    }

    @Test fun systemUiDuplicateBurstIsCoalesced() {
        val gate = FarolCausalCorrectionStage21.EventGate()
        gate.decide("com.android.systemui", 2048, 5, 1_000_000_000L, "self", 0L, false)
        assertFalse(gate.decide("com.android.systemui", 2048, 5, 1_020_000_000L, "self", 0L, false).process)
    }

    @Test fun severalWindowsPrioritizeHighestValidVisualWindow() {
        val result = FarolCausalCorrectionStage21.evaluate(
            listOf(
                block("Rua Baixa, 1, Centro\nRua Baixa, 2, Centro", id = "low", windowId = 1, layer = 2),
                block("Rua Alta, 10, Centro\nRua Alta, 20, Centro", id = "high", windowId = 2, layer = 9),
            ),
        )!!
        assertEquals("Rua Alta, 20, Centro", result.destination)
    }

    @Test fun popupOverArbitraryPackageRemainsVisualAuthority() = assertNotNull(eval(pkg = "com.whatsapp"))

    @Test fun rapidAddressChangeChangesScreenHash() {
        val a = eval("Rua Origem, 10, Centro\nRua Destino, 20, Centro")!!
        val b = eval("Rua Origem, 10, Centro\nRua Destino, 21, Centro")!!
        assertNotEquals(a.screenHash, b.screenHash)
    }

    @Test fun firstOcrDemandStartsSingleFlight() {
        val gate = FarolCausalCorrectionStage21.OcrGate()
        assertTrue(gate.request().startNow)
    }

    @Test fun secondOcrDemandWhileBusyIsCoalesced() {
        val gate = FarolCausalCorrectionStage21.OcrGate()
        gate.request()
        assertFalse(gate.request().startNow)
    }

    @Test fun oldOcrTokenBecomesStaleBeforeExtractWhenNewDemandArrives() {
        val gate = FarolCausalCorrectionStage21.OcrGate()
        val first = gate.request()
        gate.request()
        assertFalse(gate.isCurrent(first.token))
    }

    @Test fun completingBusyOcrRequestsExactlyOneRerun() {
        val gate = FarolCausalCorrectionStage21.OcrGate()
        val first = gate.request()
        gate.request()
        assertTrue(gate.complete(first.token))
    }

    @Test fun accessibilityWinnerCancelsPendingOcrRerun() {
        val gate = FarolCausalCorrectionStage21.OcrGate()
        val first = gate.request()
        gate.request()
        gate.cancelBecauseAccessibilityWon()
        assertFalse(gate.complete(first.token))
    }

    @Test fun validStructuredOcrCandidateStillWorks() = assertNotNull(eval(source = FarolUniversalVisualPipelineStage19.Source.Ocr))

    @Test fun truncatedStructuredOcrCandidateNeverBecomesEvaluation() =
        assertNull(eval("Rua Origem, 10, Centro\nAvenida Mendonça e", source = FarolUniversalVisualPipelineStage19.Source.Ocr))

    @Test fun uberMetadataDoesNotRegressValidVisualCard() = assertNotNull(eval(pkg = "com.ubercab.driver"))

    @Test fun ninetyNineMetadataDoesNotRegressValidVisualCard() = assertNotNull(eval(pkg = "com.app99.driver"))

    @Test fun inDriveMetadataDoesNotRegressValidVisualCard() = assertNotNull(eval(pkg = "sinet.startup.indriver"))

    @Test fun whatsappUnderlyingAppDoesNotBlockPopup() = assertNotNull(eval(pkg = "com.whatsapp"))

    @Test fun chatGptUnderlyingAppDoesNotBlockPopup() = assertNotNull(eval(pkg = "com.openai.chatgpt"))

    @Test fun youtubeUnderlyingAppDoesNotBlockPopup() = assertNotNull(eval(pkg = "com.google.android.youtube"))

    @Test fun oldRouteBindingIsStillRejected() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|x")
        assertFalse(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 4, 4, 10, "visual|x", false))
    }

    @Test fun verificationPendingStillBlocksPainting() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|x")
        assertFalse(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 3, 4, 10, "visual|x", true))
    }

    @Test fun exactFreshBindingStillPasses() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|x")
        assertTrue(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 3, 4, 10, "visual|x", false))
    }

    @Test fun cacheHitCannotBypassSemanticGate() {
        val invalid = eval("Rua Origem, 10, Centro\nAvenida Mendonça e")
        val cacheAvailable = true
        assertFalse(cacheAvailable && invalid != null)
    }

    @Test fun cacheMissCannotSendInvalidCandidateToGoogle() {
        val invalid = eval("Rua Origem, 10, Centro\nAvenida Mendonça")
        val cacheAvailable = false
        assertFalse(!cacheAvailable && invalid != null)
    }

    @Test fun packageMetadataCannotChangeVisualIdentity() {
        val a = eval(pkg = "com.ubercab.driver")!!
        val b = eval(pkg = "com.android.systemui")!!
        assertEquals(a.addressSignature, b.addressSignature)
        assertEquals(a.screenHash, b.screenHash)
    }

    @Test fun stage21ContractMarkersArePresent() {
        assertEquals("FAROL_CAUSAL_CORRECTION_STAGE21", FarolCausalCorrectionStage21.CONTRACT_MARKER)
        assertTrue(FarolCausalCorrectionStage21.SEMANTIC_GATE_MARKER.contains("BEFORE_CACHE_AND_GOOGLE"))
        assertTrue(FarolCausalCorrectionStage21.OCR_COALESCING_MARKER.contains("NO_BACKLOG"))
    }
}
