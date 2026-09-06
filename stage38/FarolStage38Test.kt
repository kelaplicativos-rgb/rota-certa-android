package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage38Test {
    private fun record(stage: String, ns: Long, details: String = "") =
        FarolMaximumForensicsStage38.record(
            atNs = ns,
            wallMs = 1_800_000_000_000L + ns / 1_000_000L,
            stage = stage,
            packageName = "com.app99.driver",
            details = details,
            threadName = "test",
        )

    @Test fun markersAreExplicit() {
        assertEquals("FAROL_MAXIMUM_FORENSICS_STAGE38", FarolMaximumForensicsStage38.CONTRACT_MARKER)
        assertEquals("ELAPSED_REALTIME_NANOS_STAGE38", FarolMaximumForensicsStage38.CLOCK_MARKER)
    }

    @Test fun markerSaysEventDrivenNoPolling() {
        assertEquals("EVENT_DRIVEN_NO_POLLING_STAGE38", FarolMaximumForensicsStage38.EVENT_DRIVEN_MARKER)
    }

    @Test fun markerSaysDiagnosticOnly() {
        assertEquals("DIAGNOSTIC_ONLY_NO_BEHAVIOR_AUTHORITY_STAGE38", FarolMaximumForensicsStage38.DIAGNOSTIC_ONLY_MARKER)
    }

    @Test fun causalChainMarkerExists() {
        assertEquals("EVENT_TO_PIXEL_TO_OCR_TO_ADDRESS_TO_ROUTE_TO_PAINT_STAGE38", FarolMaximumForensicsStage38.CAUSAL_CHAIN_MARKER)
    }

    @Test fun exactNanosecondsArePreserved() {
        FarolMaximumForensicsStage38.resetForTests()
        record("A", 10_000_001L)
        record("B", 10_000_777L)
        val events = FarolMaximumForensicsStage38.snapshot().events
        assertEquals(10_000_001L, events[0].atNs)
        assertEquals(10_000_777L, events[1].atNs)
    }

    @Test fun sequenceIsStrictlyIncreasing() {
        FarolMaximumForensicsStage38.resetForTests()
        record("A", 1L); record("B", 2L); record("C", 3L)
        assertEquals(listOf(1L, 2L, 3L), FarolMaximumForensicsStage38.snapshot().events.map { it.seq })
    }

    @Test fun reportExportsNanosecondDelta() {
        FarolMaximumForensicsStage38.resetForTests()
        record("A", 1_000_000L); record("B", 1_001_234L)
        val report = FarolMaximumForensicsStage38.exportReport()
        assertTrue(report.contains("delta_ns=1234"))
        assertTrue(report.contains("delta_us=1"))
    }

    @Test fun reportExportsObserverOverhead() {
        FarolMaximumForensicsStage38.resetForTests()
        record("A", 100L)
        val report = FarolMaximumForensicsStage38.exportReport()
        assertTrue(report.contains("observerOverheadTotal_ns="))
        assertTrue(report.contains("observerOverheadP95_ns="))
    }

    @Test fun detailsKeepContentOnSinglePhysicalLine() {
        FarolMaximumForensicsStage38.resetForTests()
        record("OCR", 100L, "Rua Alfa\nRua Beta")
        val report = FarolMaximumForensicsStage38.exportReport()
        assertTrue(report.contains("Rua Alfa\\nRua Beta"))
    }

    @Test fun stageCountersAreExported() {
        FarolMaximumForensicsStage38.resetForTests()
        record("OCR", 1L); record("OCR", 2L); record("ROUTE", 3L)
        val report = FarolMaximumForensicsStage38.exportReport()
        assertTrue(report.contains("OCR=2"))
        assertTrue(report.contains("ROUTE=1"))
    }

    @Test fun packageTraceCycleAndOperationArePreserved() {
        FarolMaximumForensicsStage38.resetForTests()
        FarolMaximumForensicsStage38.record(5L, 10L, "X", "com.ubercab.driver", 7L, "T20-1", "ocr-9", "ok", "main")
        val e = FarolMaximumForensicsStage38.snapshot().events.single()
        assertEquals("com.ubercab.driver", e.packageName)
        assertEquals(7L, e.cycleId)
        assertEquals("T20-1", e.traceId)
        assertEquals("ocr-9", e.operationId)
    }

    @Test fun ringBufferDropsOldestWhenCapacityExceeded() {
        FarolMaximumForensicsStage38.resetForTests()
        repeat(FarolMaximumForensicsStage38.MAX_EVENTS + 1) { index -> record("E", index.toLong() + 1L) }
        val snap = FarolMaximumForensicsStage38.snapshot()
        assertEquals(FarolMaximumForensicsStage38.MAX_EVENTS, snap.events.size)
        assertEquals(1L, snap.dropped)
        assertEquals(2L, snap.events.first().atNs)
    }

    @Test fun parserForensicsExplainsTwoAcceptedAddresses() {
        val steps = UniversalScreenAddressParser.forensicExplainStage38(
            "Rua Alfa, 10 - Centro, São Paulo - SP\nRua Beta, 20 - Centro, São Paulo - SP",
        )
        assertTrue(steps.any { it.contains("decision=ADDRESS_ACCEPT") })
        assertTrue(steps.last().contains("parser_final_count=2"))
    }

    @Test fun parserForensicsNamesTransactionNoise() {
        val steps = UniversalScreenAddressParser.forensicExplainStage38("R$ 25,00 aceitar corrida")
        assertTrue(steps.any { it.contains("transaction_noise") })
    }

    @Test fun parserForensicsReportsNoRecognizedLead() {
        val steps = UniversalScreenAddressParser.forensicExplainStage38("Texto qualquer sem endereço")
        assertTrue(steps.any { it.contains("no_recognized_address_lead") })
    }

    @Test fun evaluationForensicsCanSeeValidPair() {
        val block = FarolUniversalVisualPipelineStage19.VisualBlock(
            id = "ocr-1", windowId = 2, windowLayer = 9, depth = 1,
            text = "Rua Alfa, 10 - Centro, São Paulo - SP\nRua Beta, 20 - Centro, São Paulo - SP",
            source = FarolUniversalVisualPipelineStage19.Source.Ocr,
            left = 10, top = 20, right = 900, bottom = 500,
        )
        val steps = FarolCausalCorrectionStage21.forensicExplainEvaluationStage38(listOf(block))
        assertTrue(steps.any { it.contains("semantic_validation=true") })
        assertTrue(steps.any { it.contains("diagnostic_expected_candidate=true") })
    }

    @Test fun evaluationForensicsExplainsSingleAddressFailure() {
        val block = FarolUniversalVisualPipelineStage19.VisualBlock(
            id = "ocr-1", windowId = 2, windowLayer = 9, depth = 1,
            text = "Rua Alfa, 10 - Centro, São Paulo - SP",
            source = FarolUniversalVisualPipelineStage19.Source.Ocr,
        )
        val steps = FarolCausalCorrectionStage21.forensicExplainEvaluationStage38(listOf(block))
        assertTrue(steps.any { it.contains("after_clean=1") })
        assertTrue(steps.any { it.contains("no_candidate_with_two_addresses") || it.contains("no_layer_produced_candidate") })
    }

    @Test fun evaluationForensicsReportsNoBlocks() {
        assertEquals(listOf("evaluation=NO_BLOCKS"), FarolCausalCorrectionStage21.forensicExplainEvaluationStage38(emptyList()))
    }

    @Test fun parserForensicsDoesNotChangeAuthoritativeParserResult() {
        val text = "Rua Alfa, 10 - Centro, São Paulo - SP\nRua Beta, 20 - Centro, São Paulo - SP"
        val before = UniversalScreenAddressParser.findAddresses(text)
        UniversalScreenAddressParser.forensicExplainStage38(text)
        val after = UniversalScreenAddressParser.findAddresses(text)
        assertEquals(before, after)
    }

    @Test fun resetClearsAllEvents() {
        FarolMaximumForensicsStage38.resetForTests()
        record("A", 1L)
        assertFalse(FarolMaximumForensicsStage38.snapshot().events.isEmpty())
        FarolMaximumForensicsStage38.resetForTests()
        assertTrue(FarolMaximumForensicsStage38.snapshot().events.isEmpty())
    }
}
