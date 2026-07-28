package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthorizedAppsCards146ContractTest {
    private val root = File(System.getProperty("user.dir"))
    private fun source(path: String) = File(root, path).readText()

    @Test fun cardsAreComplementaryAndGroupedByPackage() {
        val store = source("src/main/java/br/com/mapeiaia/rotacerta/ManualAppScreenCaptureStore.kt")
        assertTrue(store.contains("readForPackage"))
        assertTrue(store.contains("removePackage"))
        assertTrue(store.contains("Nunca participa da decisão do farol"))
    }

    @Test fun captureUsesShortTapForManagerAndLongPressForCapture() {
        val module = source("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt")
        val overlay = source("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt")
        assertTrue(module.contains("OpenAuthorizedAppsAndCards"))
        assertTrue(module.contains("CaptureCurrentAppAndScreen"))
        assertTrue(overlay.contains("postDelayed(longPressAction, 1_500L)"))
        assertFalse(overlay.contains("onDoubleTap(event"))
    }

    @Test fun visibleSaveHomeButtonIsAbsent() {
        val main = source("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
        assertFalse(main.contains("Text(\"Salvar Casa\")"))
        assertTrue(main.contains("onValueChangeFinished = onSave"))
    }
}
