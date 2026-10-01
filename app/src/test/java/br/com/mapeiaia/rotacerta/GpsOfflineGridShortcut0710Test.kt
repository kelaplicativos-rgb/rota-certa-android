package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GpsOfflineGridShortcut0710Test {
    @Test
    fun gpsOfflineIsARealShortcutModule() {
        val spec = BubbleShortcutCatalog.findSpec(GpsOfflineBubbleShortcutModule0710.SHORTCUT_ID)
        requireNotNull(spec)
        assertEquals("GPS Offline", spec.displayLabel)
        assertEquals("🧭", spec.emoji)
        assertEquals(BubbleShortcutAction.OpenOfflineNavigation, spec.action)
        assertTrue(
            ShortcutActionCatalog0184.actionsForModule(GpsOfflineBubbleShortcutModule0710.SHORTCUT_ID)
                .any { it.id == GpsOfflineBubbleShortcutModule0710.SHORTCUT_ID },
        )
    }

    @Test
    fun upgradeMigrationAddsGpsOfflineWhenThereIsCapacity() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt").readText()
        assertTrue(source.contains("applyGpsOffline0710Migration"))
        assertTrue(source.contains("KEY_GPS_OFFLINE_0710_MIGRATED"))
        assertTrue(source.contains("GpsOfflineBubbleShortcutModule0710.SHORTCUT_ID"))
    }

    @Test
    fun floatingGridDispatchesDirectlyToGpsActivity() {
        val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/OfflineNavigationActivity0708.kt").readText()
        assertTrue(service.contains("openOfflineNavigation0710()"))
        assertTrue(service.contains("Intent(this, OfflineNavigationActivity0708::class.java)"))
        assertTrue(service.contains("EXTRA_FOCUS_DESTINATION_0710"))
        assertTrue(activity.contains("GPS_OFFLINE_GRID_LAUNCH_0710"))
        assertTrue(activity.contains("Text(\"GPS Offline\""))
    }
}
