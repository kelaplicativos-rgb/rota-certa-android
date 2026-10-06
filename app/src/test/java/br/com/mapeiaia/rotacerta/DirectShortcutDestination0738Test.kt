package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectShortcutDestination0738Test {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val catalog = File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt").readText()
    private val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()

    @Test
    fun moduleShortcutsNeverUseCollapsedHomeAsTheirPrimaryDestination() {
        assertFalse(service.contains("-> openHomeCollapsed0186()"))
        assertTrue(service.contains("-> openShortcutModule0171(spec)"))
    }

    @Test
    fun tripAgendaKeepsIdentityRouteWithoutExpandingFarolEnum() {
        assertTrue(catalog.contains("id = \"trip_agenda\""))
        assertTrue(catalog.contains("action = BubbleShortcutAction.OpenSettings"))
        assertTrue(service.contains(".putExtra(EXTRA_OPEN_SHORTCUT_MODULE_0171, spec.id)"))
        assertTrue(main.contains("highlightedShortcutModule0171 == \"trip_agenda\""))
        assertTrue(main.contains("TripsActivity::class.java"))
        assertTrue(main.contains("TripActions.ACTION_OPEN_TRIPS"))
    }

    @Test
    fun directInPlaceActionsRemainDirect() {
        assertTrue(service.contains("BubbleShortcutAction.SaveScreenPrint -> saveScreenPrintStage32()"))
        assertTrue(service.contains("BubbleShortcutAction.CaptureCurrentAppAndScreen -> captureCurrentAppAndScreen138()"))
        assertTrue(service.contains("BubbleShortcutAction.ClearClipboard -> clearClipboardFromBubble()"))
        assertTrue(service.contains("BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp118()"))
        assertTrue(service.contains("\"action_record_audio\" -> { toggleSafetyRecorder0666"))
        assertTrue(service.contains("\"action_record_video\" -> { toggleSafetyRecorder0666"))
    }
}
