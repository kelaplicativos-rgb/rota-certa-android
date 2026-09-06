package br.com.mapeiaia.rotacerta

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolFlightRecorder0163Test {
    @After
    fun tearDown() = FarolFlightRecorder0163.resetForTest()

    @Test
    fun preservesEveryRealEventWithoutDeduplicationAndKeepsMonotonicDelta() {
        FarolFlightRecorder0163.resetForTest()
        FarolFlightRecorder0163.recordAtForTest("ACCESSIBILITY_EVENT", "com.app99.driver", "same", 1_000L, 10_000L)
        FarolFlightRecorder0163.recordAtForTest("ACCESSIBILITY_EVENT", "com.app99.driver", "same", 1_001L, 14_500L)

        val report = FarolFlightRecorder0163.snapshotForTest()
        assertTrue(report.contains("seq=1"))
        assertTrue(report.contains("seq=2"))
        assertTrue(report.contains("delta_us=4"))
        assertEquals(2, Regex("stage=ACCESSIBILITY_EVENT").findAll(report).count())
    }

    @Test
    fun masksPhoneAndEmailButKeepsAddressAndFailureContext() {
        FarolFlightRecorder0163.resetForTest()
        FarolFlightRecorder0163.recordAtForTest(
            stage = "BUBBLE_ADDRESS_EVALUATION",
            packageName = "com.ubercab.driver",
            details = "destino=Rua das Flores 123; telefone=11999998888; email=a@b.com",
            wallTimeMillis = 2_000L,
            elapsedRealtimeNanos = 20_000L,
        )

        val report = FarolFlightRecorder0163.snapshotForTest()
        assertTrue(report.contains("Rua das Flores 123"))
        assertTrue(report.contains("[telefone mascarado]"))
        assertTrue(report.contains("[email mascarado]"))
        assertFalse(report.contains("11999998888"))
        assertFalse(report.contains("a@b.com"))
    }

    @Test
    fun keepsMemoryBoundarySuitableForLowEndDevices() {
        FarolFlightRecorder0163.resetForTest()
        repeat(FarolFlightRecorder0163.MAX_MEMORY_EVENTS + 7) { index ->
            FarolFlightRecorder0163.recordAtForTest(
                stage = "EVENT_$index",
                packageName = "sinet.startup.indriver",
                details = "d=$index",
                wallTimeMillis = index.toLong() + 1L,
                elapsedRealtimeNanos = index.toLong() + 1L,
            )
        }

        val report = FarolFlightRecorder0163.snapshotForTest()
        assertFalse(report.contains("stage=EVENT_0 "))
        assertTrue(report.contains("stage=EVENT_${FarolFlightRecorder0163.MAX_MEMORY_EVENTS + 6}"))
        assertTrue(report.contains("dropped_events=7"))
    }
}
