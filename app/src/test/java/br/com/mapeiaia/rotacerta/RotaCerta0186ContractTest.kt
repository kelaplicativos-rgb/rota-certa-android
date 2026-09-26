package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotaCerta0186ContractTest {
    private val source = File("src/main/java/br/com/mapeiaia/rotacerta")
    private fun read(name: String) = File(source, name).readText()

    @Test
    fun gridConsumesOutsideTouchAndHasNoTripleTapWindow() {
        val overlay = read("BubbleShortcutOverlayController.kt")
        val service = read("LiveRideAccessibilityService.kt")
        assertTrue(overlay.contains("MATCH_PARENT"))
        assertTrue(overlay.contains("Fechar grade de atalhos"))
        assertTrue(overlay.contains("ShortcutInteractionPolicy0186.HOLD_MILLIS"))
        assertTrue(overlay.contains("cancelShortcutGestures0186"))
        assertTrue(overlay.contains("isAttachedToWindow"))
        assertFalse(overlay.contains("tapCount0180"))
        assertFalse(overlay.contains("900L"))
        assertFalse(service.contains("SHORTCUT_TRIPLE_TAP_OPEN_EDITOR_0180"))
    }

    @Test
    fun homeAudioLinksAndTextCorrectionAreExplicitAndLocal() {
        val main = read("MainActivity.kt")
        val links = read("QuickLinksActivity.kt")
        val service = read("LiveRideAccessibilityService.kt")
        assertTrue(main.contains("EXTRA_HOME_LAUNCH_MODE_0186"))
        assertTrue(main.contains("removeExtra(EXTRA_TEXT_CORRECTION_INITIAL_0186)"))
        assertTrue(main.contains("removeExtra(EXTRA_TEXT_REPLACEMENT_TOKEN_0186)"))
        assertTrue(main.contains("SpeechOutputMode0186.values()"))
        assertTrue(links.contains("QuickLinkSearchPolicy0186.filter"))
        assertTrue(links.contains("Link copiado"))
        assertTrue(service.contains("TextReplacementSession0186.create"))
        val correction = read("TextCorrectionEngine0186.kt")
        assertTrue(correction.contains("OFFLINE_TEXT_CORRECTION_0186"))
        assertTrue(correction.contains("protectSpans"))
        assertFalse(correction.contains("java.net"))
        assertFalse(correction.contains("okhttp"))
        assertFalse(correction.contains("retrofit"))
    }
}
