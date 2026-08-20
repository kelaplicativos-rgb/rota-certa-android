package br.com.mapeiaia.rotacerta

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutModuleFocusContract0177Test {
    private fun source(path: String): String =
        String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)

    @Test
    fun floatingGridSendsModuleIdentityAndHomeBringsPanelIntoView() {
        val service = source("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
        val activity = source("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
        assertTrue(service.contains("openShortcutModule0171(moduleSpec0186)"))
        assertTrue(service.contains("HomeLaunchPolicy0186.MODE_MODULE"))
        assertFalse(service.contains("openResourceGroup(requireNotNull(spec.targetGroup), requireNotNull(spec.targetTab))"))
        assertTrue(activity.contains("navigationRequestKey0177 = System.identityHashCode(launchIntent)"))
        assertTrue(activity.contains("@OptIn(ExperimentalFoundationApi::class)"))
        assertTrue(activity.contains("BringIntoViewRequester()"))
        assertTrue(activity.contains("Modifier.bringIntoViewRequester(rowFocusRequester0177)"))
        assertTrue(activity.contains("moduleFocusRequesters0177[requestedModuleId0177]?.bringIntoView()"))
        assertTrue(activity.contains("SHORTCUT_MODULE_IDENTITY_FOCUS_0177"))
    }
}
