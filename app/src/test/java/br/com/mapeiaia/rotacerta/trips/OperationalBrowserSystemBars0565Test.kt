package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class OperationalBrowserSystemBars0565Test {
    @Test
    fun operationalBrowserKeepsWebContentInsideAndroidSystemBars() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalTripBrowserActivity0564.kt",
        ).readText()

        assertTrue(source.contains("WindowCompat.setDecorFitsSystemWindows(window, false)"))
        assertTrue(source.contains("WindowInsetsCompat.Type.systemBars()"))
        assertTrue(source.contains("WindowInsetsCompat.Type.displayCutout()"))
        assertTrue(source.contains("setInsetAwareContent0565(webView)"))
        assertTrue(source.contains("isAppearanceLightStatusBars = true"))
        assertTrue(source.contains("isAppearanceLightNavigationBars = true"))
    }
}
