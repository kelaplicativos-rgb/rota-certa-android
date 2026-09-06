package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeActionShortcutsContract0184Test {
    private val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt").readText()

    @Test
    fun homeAddsSpecificActionsAndGridExecutesThemDirectly() {
        assertTrue(main.contains("ShortcutActionCatalog0184.actionsForModule"))
        assertTrue(main.contains("Adicionar à grade"))
        assertTrue(main.contains("Remover da grade"))
        assertTrue(service.contains("executeShortcutModule(entry0180.spec)"))
        assertFalse(overlay.contains("shortcut_add_0179"))
    }

    @Test
    fun backupCreateAndRestoreRemainSeparate() {
        assertTrue(main.contains("EXTRA_CREATE_BACKUP_0184"))
        assertTrue(main.contains("EXTRA_RESTORE_BACKUP_0184"))
        assertTrue(service.contains("openBackupFileAction0184(create = true)"))
        assertTrue(service.contains("openBackupFileAction0184(create = false)"))
    }

    @Test
    fun manualRadarStoresHeadingWhenAvailable() {
        assertTrue(service.contains("capturedHeading0184"))
        assertTrue(service.contains("directionType = capturedHeading0184?.let { 1 }"))
        assertTrue(service.contains("direction = capturedHeading0184"))
    }
}
