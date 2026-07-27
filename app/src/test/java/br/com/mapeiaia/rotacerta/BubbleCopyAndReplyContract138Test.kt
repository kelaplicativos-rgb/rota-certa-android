package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleCopyAndReplyContract138Test {
    @Test fun `copia completa usa acessibilidade e OCR apenas como fallback`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        assertTrue(source.contains("collectAllVisibleTextForCopy138"))
        assertTrue(source.contains("requestFullScreenCopyOcr138"))
        assertTrue(source.contains("Texto completo copiado"))
    }

    @Test fun `duplo toque em respostas abre editor novo`() {
        val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/QuickRepliesActivity.kt").readText()
        assertTrue(service.contains("openQuickRepliesFromBubble(createNew = true)"))
        assertTrue(activity.contains("EXTRA_QUICK_REPLY_CREATE"))
        assertTrue(activity.contains("mutableStateOf(startCreating)"))
    }
}
