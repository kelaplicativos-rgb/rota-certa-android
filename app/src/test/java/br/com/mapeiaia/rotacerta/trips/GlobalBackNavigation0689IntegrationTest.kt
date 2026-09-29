package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobalBackNavigation0689IntegrationTest {
    private val activity =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
    private val header =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaHeaderNavigation0396.kt").readText()

    @Test
    fun fixedArrowAndAndroidBackShareOneNavigationPath() {
        assertTrue(activity.contains("BackHandler(enabled = true)"))
        assertTrue(activity.contains("navigateBack0689()"))
        assertTrue(activity.contains("onNavigationClick = navigateBack0689"))
        assertTrue(activity.contains("navigationHistory0689"))
        assertTrue(activity.contains("GlobalBackNavigation0689.pop(navigationHistory0689)"))
    }

    @Test
    fun rootKeepsDrawerWithoutReplacingBackArrow() {
        assertTrue(header.contains("text = \"←\""))
        assertTrue(header.contains("if (root && onMenuClick0689 != null)"))
        assertTrue(header.contains("text = \"☰\""))
        assertFalse(header.contains("text = if (root) \"☰\" else \"←\""))
        assertTrue(activity.contains("onMenuClick0689 = if (headerIsRoot0396)"))
    }

    @Test
    fun rootBackCannotAccidentallyFinishTripsActivity() {
        assertTrue(activity.contains("BackHandler(enabled = true)"))
        assertTrue(activity.contains("screen != TripScreen.TIMELINE"))
        assertTrue(activity.contains("else -> Unit"))
        assertFalse(activity.contains("activity.finish()"))
    }
}
