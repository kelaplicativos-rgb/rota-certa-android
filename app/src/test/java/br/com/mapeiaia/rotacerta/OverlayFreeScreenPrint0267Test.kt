package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayFreeScreenPrint0267Test {
    @Test
    fun activeApplicationWindowWinsOverBubbleAndShortcutPanel() {
        val selected = OverlayFreePrintWindowPolicy0267.select(
            listOf(
                window(id = 30, layer = 30, active = true, application = false, pkg = "br.com.mapeiaia.rotacerta"),
                window(id = 20, layer = 20, focused = true, application = false, pkg = "br.com.mapeiaia.rotacerta"),
                window(id = 10, layer = 10, active = true, focused = true, application = true, pkg = "com.target.app"),
            ),
        )

        assertEquals(10, selected?.id)
        assertEquals("com.target.app", selected?.packageName)
    }

    @Test
    fun highestVisibleApplicationWindowIsSelectedDeterministically() {
        val selected = OverlayFreePrintWindowPolicy0267.select(
            listOf(
                window(id = 10, layer = 10, application = true),
                window(id = 11, layer = 11, focused = true, application = true),
                window(id = 12, layer = 12, active = true, application = true),
            ),
        )

        assertEquals(12, selected?.id)
    }

    @Test
    fun noApplicationWindowFailsClosedInsteadOfCapturingTheDisplay() {
        assertNull(
            OverlayFreePrintWindowPolicy0267.select(
                listOf(window(id = 30, layer = 30, active = true, application = false)),
            ),
        )
    }

    @Test
    fun printShortcutUsesWindowCaptureAndDoesNotDispatchLegacyDisplayPrintOnAndroid16() {
        val controller = File(
            "src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt",
        ).readText()
        val capture = File(
            "src/main/java/br/com/mapeiaia/rotacerta/OverlayFreeScreenPrint0267.kt",
        ).readText()
        val service = File(
            "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt",
        ).readText()

        assertTrue(controller.contains("OverlayFreeScreenPrint0267.captureIfSupported(context, trace)"))
        assertTrue(controller.contains("if (!handledAsCleanPrint0267) onShortcut(entry0179)"))
        assertTrue(
            service.contains(
                "shortcutOverlayController = BubbleShortcutOverlayController(\n" +
                    "            context = this,",
            ),
        )
        assertTrue(capture.contains("takeScreenshotOfWindow("))
        assertTrue(capture.contains("AccessibilityWindowInfo.TYPE_APPLICATION"))
        assertTrue(capture.contains("overlaysExcluded=true"))
        assertFalse(capture.contains("takeScreenshot(Display.DEFAULT_DISPLAY"))
    }

    private fun window(
        id: Int,
        layer: Int,
        active: Boolean = false,
        focused: Boolean = false,
        application: Boolean,
        pkg: String? = null,
    ) = OverlayFreePrintWindow0267(
        id = id,
        layer = layer,
        active = active,
        focused = focused,
        packageName = pkg,
        applicationWindow = application,
    )
}
