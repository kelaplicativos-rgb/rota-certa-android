package br.com.mapeiaia.rotacerta

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FarolMaximumForensicsStage38Test {
    @Before
    fun setUp() {
        FarolMaximumForensicsStage38.resetForTests()
        FarolMaximumForensicsStage38.setMode(FarolMaximumForensicsStage38.Mode.FORENSIC_MAX)
    }

    @After
    fun tearDown() {
        FarolMaximumForensicsStage38.resetForTests()
    }

    @Test
    fun eventChainIsAppendOnlyAndLinked() {
        FarolMaximumForensicsStage38.record(
            atNs = 1_000_000_000L,
            wallMs = 10_000L,
            stage = "S38_OCR_REQUEST",
            packageName = "com.example.ride",
            traceId = "trace-a",
            details = "candidate=true",
        )
        FarolMaximumForensicsStage38.record(
            atNs = 1_020_000_000L,
            wallMs = 10_020L,
            stage = "S38_SCREENSHOT_SUCCESS",
            packageName = "com.example.ride",
            traceId = "trace-a",
            details = "width=1080; height=2340",
        )

        val snapshot = FarolMaximumForensicsStage38.snapshot()
        assertEquals(2, snapshot.events.size)
        assertTrue(snapshot.traceComplete)
        assertTrue(FarolMaximumForensicsStage38.verifyRetainedChain(snapshot.events))
        assertEquals(snapshot.events[0].eventHash, snapshot.events[1].previousHash)
        assertEquals(snapshot.events.last().eventHash, snapshot.rootHash)
        assertNotEquals(snapshot.events[0].eventHash, snapshot.events[1].eventHash)
        assertTrue(snapshot.events.all { it.detailsSha256.length == 64 })
        assertTrue(snapshot.events.all { it.probeId?.startsWith("probe-") == true })
    }

    @Test
    fun maxModeRecordsObservedHeartbeatLagWithoutTimer() {
        FarolMaximumForensicsStage38.record(
            atNs = 2_000_000_000L,
            wallMs = 20_000L,
            stage = "S38_OCR_REQUEST",
            packageName = "com.example.ride",
            traceId = "trace-heartbeat",
        )
        FarolMaximumForensicsStage38.record(
            atNs = 2_220_000_000L,
            wallMs = 20_220L,
            stage = "S38_OCR_EXTRACT",
            packageName = "com.example.ride",
            traceId = "trace-heartbeat",
        )

        val heartbeat = FarolMaximumForensicsStage38.snapshot().events.single { it.stage == "S38_HEARTBEAT_LAG" }
        assertTrue(heartbeat.details.contains("observed_on_next_real_event=true"))
        assertTrue(heartbeat.details.contains("interval_ns=220000000"))
        assertEquals(FarolMaximumForensicsStage38.Priority.CRITICAL, heartbeat.priority)
    }

    @Test
    fun byteEnvelopeAndDiffRangesAreDeterministicWithoutPersistingPayload() {
        val expected = "abcdef".toByteArray()
        val actual = "abXdeYZ".toByteArray()
        val envelope1 = FarolMaximumForensicsStage38.byteEnvelope(expected, "text/plain", "utf-8")
        val envelope2 = FarolMaximumForensicsStage38.byteEnvelope(expected, "text/plain", "utf-8")

        assertEquals(expected.size, envelope1.length)
        assertEquals(64, envelope1.sha256.length)
        assertEquals(envelope1, envelope2)
        assertEquals("2,5-6", FarolMaximumForensicsStage38.diffByteRanges(expected, actual))
    }

    @Test
    fun deterministicSnapshotIgnoresMapIterationOrder() {
        val first = linkedMapOf("window" to "7", "package" to "com.example", "text" to "A=B")
        val second = linkedMapOf("text" to "A=B", "package" to "com.example", "window" to "7")

        assertEquals(
            FarolMaximumForensicsStage38.deterministicSnapshot(first),
            FarolMaximumForensicsStage38.deterministicSnapshot(second),
        )
        assertEquals(
            FarolMaximumForensicsStage38.deterministicSnapshotHash(first),
            FarolMaximumForensicsStage38.deterministicSnapshotHash(second),
        )
    }

    @Test
    fun gapDetectorReportsMissingOcrEvidenceBeforeDecision() {
        FarolMaximumForensicsStage38.record(
            atNs = 3_000_000_000L,
            wallMs = 30_000L,
            stage = "S38_OCR_REQUEST",
            packageName = "com.example.ride",
            traceId = "trace-gap",
        )
        FarolMaximumForensicsStage38.record(
            atNs = 3_050_000_000L,
            wallMs = 30_050L,
            stage = "S38_DECISION_CREATED",
            packageName = "com.example.ride",
            traceId = "trace-gap",
        )

        val report = FarolMaximumForensicsStage38.exportReport()
        assertTrue(report.contains("FORENSIC_GAP"))
        assertTrue(report.contains("expected=SCREENSHOT_OR_OCR_EXTRACT"))
    }

    @Test
    fun exportMasksSensitiveTextButKeepsHashesAndIdentity() {
        FarolMaximumForensicsStage38.record(
            atNs = 4_000_000_000L,
            wallMs = 40_000L,
            stage = "S38_OCR_REQUEST",
            packageName = "com.example.ride",
            traceId = "trace-private",
            details = "eventText=Joao 11999998888; email=person@example.com; token=super-secret",
        )

        val report = FarolMaximumForensicsStage38.exportReport()
        assertFalse(report.contains("11999998888"))
        assertFalse(report.contains("person@example.com"))
        assertFalse(report.contains("super-secret"))
        assertTrue(report.contains("[telefone mascarado]"))
        assertTrue(report.contains("details_sha256="))
        assertTrue(report.contains("rootHash="))
    }

    @Test
    fun offModeNeverRecordsOrCreatesBehaviorAuthority() {
        FarolMaximumForensicsStage38.setMode(FarolMaximumForensicsStage38.Mode.OFF)
        val result = FarolMaximumForensicsStage38.record(
            atNs = 5_000_000_000L,
            wallMs = 50_000L,
            stage = "S38_ROUTE_REQUEST",
            packageName = "com.example.ride",
        )

        assertEquals(-1L, result)
        assertTrue(FarolMaximumForensicsStage38.snapshot().events.isEmpty())
    }
}
