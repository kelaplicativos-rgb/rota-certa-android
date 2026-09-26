package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutInPlaceContract0181Test {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun namedPlaceAndAlertActionsOpenTheRealNamePopup() {
        assertTrue(service.contains("action_create_alert_here"))
        assertTrue(service.contains("saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)"))
        assertTrue(service.contains("action_save_place_here"))
        assertTrue(service.contains("saveCurrentPlaceFromBubble(SavedPlaceType.Place)"))
        assertTrue(service.contains("showSavePlacePopup"))
    }

    @Test
    fun backupAndCleaningActionsAreIndependent() {
        assertTrue(service.contains("action_create_backup"))
        assertTrue(service.contains("action_restore_backup"))
        assertTrue(service.contains("action_clear_cache"))
        assertTrue(service.contains("clearOwnCache0183"))
    }
}
