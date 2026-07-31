package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolRealtimeCriticalPathContract0167Test {
    @Test
    fun `checkpoint constroi snapshot somente dentro do executor de IO`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/FarolFlightRecorder0163.kt").readText()
        val method = source.substringAfter("private fun scheduleCheckpoint(reason: String)")
            .substringBefore("private fun snapshotText")
        val executorIndex = method.indexOf("ioExecutor.execute")
        val snapshotIndex = method.indexOf("val snapshot = snapshotText")
        assertTrue(executorIndex >= 0)
        assertTrue(snapshotIndex > executorIndex)
        assertTrue("checkpoint_snapshot_fully_off_main_0_1_167" in method)
    }

    @Test
    fun `gate ocorre antes da coleta completa e telemetria do evento`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        val eventMethod = source.substringAfter("override fun onAccessibilityEvent")
            .substringBefore("override fun onInterrupt")
        val gateIndex = eventMethod.indexOf("farolRealtimeEventGate0167.shouldCollect")
        val eventLogIndex = eventMethod.indexOf("stage = \"ACCESSIBILITY_EVENT\"")
        val treeIndex = eventMethod.indexOf("collectImmediateVisibleTextChecklist13()")
        assertTrue(gateIndex >= 0)
        assertTrue(eventLogIndex > gateIndex)
        assertTrue(treeIndex > gateIndex)
    }

    @Test
    fun `coleta de arvore tem limites rigidos`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        assertTrue("MAX_ACCESSIBILITY_NODES_0167 = 768" in source)
        assertTrue("MAX_ACCESSIBILITY_TEXT_CHARS_0167 = 24_000" in source)
        assertTrue("bounded_allocation_light_accessibility_tree_0_1_167" in source)
    }
}
