package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationalBrowserAutoDiscovery0567Test {
    @Test
    fun lifecycleAutoDiscoveryIsDisabledForEveryState0616() {
        assertFalse(
            operationalAutoDiscoveryShouldRequest0567(
                connectedProfileCount = 0,
                nowMillis = 10_000L,
                lastRequestedAtMillis = 0L,
            ),
        )
        assertFalse(
            operationalAutoDiscoveryShouldRequest0567(
                connectedProfileCount = 2,
                nowMillis = 15_000L,
                lastRequestedAtMillis = 10_000L,
                minIntervalMillis = 5_000L,
            ),
        )
    }

    @Test
    fun tripsResumeCannotEnqueueImmediateSynchronization0616() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalBrowserAutoDiscovery0567.kt",
        ).readText()

        assertTrue(source.contains("activity !is TripsActivity"))
        assertTrue(source.contains("OPERATIONAL_BROWSER_AUTO_DISCOVERY_DISABLED_0616"))
        assertTrue(source.contains("syncRequested=false"))
        assertFalse(source.contains("AgendaBackgroundSync0392.enqueueImmediate"))
        assertFalse(source.contains("admin_update_now:operational_browser_auto_discovery_0567"))
        assertFalse(source.contains("OPERATIONAL_BROWSER_AUTO_DISCOVERY_REQUESTED_0567"))
    }

    @Test
    fun manifestKeepsNonExportedCompatibilityProvider() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".trips.OperationalBrowserAutoDiscoveryProvider0567"))
        assertTrue(manifest.contains("${applicationId}.operational.browser.auto.discovery"))
        val providerBlock = manifest.substringAfter(".trips.OperationalBrowserAutoDiscoveryProvider0567")
            .substringBefore("/>")
        assertTrue(providerBlock.contains("android:exported=\"false\""))
    }
}
