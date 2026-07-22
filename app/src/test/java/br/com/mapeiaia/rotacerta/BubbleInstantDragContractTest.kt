package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleInstantDragContractTest {
    @Test
    fun dragOwnsMainThreadAndPausesAnalysisDuringGesture() {
        val sourceFile = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
        ).firstOrNull(File::exists) ?: error("LiveRideAccessibilityService.kt nao encontrado")

        val source = sourceFile.readText()
        assertTrue("Contrato 0.1.116 ausente", "bubble_instant_drag_0_1_116" in source)
        assertTrue("Gesto precisa pausar acessibilidade", "bubble_drag_accessibility_pause_0_1_116" in source)
        assertTrue("Gesto precisa pausar screenshot", "bubble_drag_screenshot_pause_0_1_116" in source)
        assertTrue("Gesto precisa pausar processamento", "bubble_drag_process_pause_0_1_116" in source)
        assertTrue("Loop precisa respeitar o gesto", "bubble_drag_scan_pause_0_1_116" in source)
        assertTrue("OCR precisa sair do thread principal", "bubble_drag_ocr_background_0_1_116" in source)
        assertTrue(
            "ACTION_DOWN precisa assumir o gesto",
            "bubbleGestureActive = true" in source || "bubbleGestureActive = (true)" in source,
        )
        assertTrue("Analise pendente precisa ser cancelada", "analyzeJob?.cancel()" in source)

        val listenerStart = source.indexOf("private inner class BubbleTouchListener")
        val listenerEnd = source.indexOf("private fun dp", listenerStart)
        assertTrue("Listener de arraste nao encontrado", listenerStart >= 0 && listenerEnd > listenerStart)
        val listener = source.substring(listenerStart, listenerEnd)
        assertTrue("Movimento precisa atualizar WindowManager", "manager.updateViewLayout(view, params)" in listener)
        assertTrue("Movimento precisa usar touch slop nativo", "scaledTouchSlop" in listener)
        assertFalse("Arraste nao pode atualizar menu removido", "updateActionMenuPosition()" in listener)

        val beforeUp = listener.substringBefore("MotionEvent.ACTION_UP")
        assertFalse("Nao pode existir espera antes de mover", "delay(" in beforeUp)
    }
}
