package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPopupTimeout0647Test {
    private val directional = File(
        "src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt",
    ).readText()
    private val legacy = File(
        "src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt",
    ).readText()

    @Test
    fun `directional popup owns exactly one twenty second timer per target`() {
        assertTrue(directional.contains("const val ALERT_TIMEOUT_MILLIS_0647 = 20_000L"))
        assertTrue(directional.contains("val targetChanged0647 = activeTargetId != visual.targetId"))
        assertTrue(directional.contains("if (targetChanged0647 || activeTimeout0647 == null)"))
        assertTrue(directional.contains("scheduleActiveTimeout0647(visual.targetId)"))
        assertTrue(directional.contains("starts_once_per_target=true"))
        assertFalse(directional.contains("PASSED_CLOSE_DELAY_MILLIS"))
        assertFalse(directional.contains("const val ALERT_TIMEOUT_MILLIS_0647 = 3_000L"))
    }

    @Test
    fun `passing point and engine idle cannot close before the twenty second deadline`() {
        val show = directional
            .substringAfter("fun showOrUpdate(")
            .substringBefore("fun hideFromEngineIdle()")
        val idle = directional
            .substringAfter("fun hideFromEngineIdle()")
            .substringBefore("fun hide()")

        assertTrue(show.contains("ALERT_OVERLAY_PASSED_PINNED_0647"))
        assertTrue(show.contains("auto_close_on_pass=false"))
        assertFalse(show.contains("handler.postDelayed(close"))
        assertTrue(idle.contains("ALERT_OVERLAY_ENGINE_IDLE_PINNED_0647"))
        assertFalse(idle.contains("removeView0647()"))
    }

    @Test
    fun `only action buttons dismiss early and body touch is ignored`() {
        assertTrue(directional.contains("ALERT_OVERLAY_NON_ACTION_TOUCH_IGNORED_0647"))
        assertTrue(directional.contains("dismissByAction0647(action = \"CLOSE\")"))
        assertTrue(directional.contains("dismissByAction0647(action = \"EDIT\")"))
        assertTrue(directional.contains("dismissByAction0647(action = \"DELETE\")"))
        assertTrue(directional.contains("setOnTouchListener"))
        assertTrue(directional.contains("dismissed=false"))
        assertTrue(directional.contains("timer_restarted=false"))
    }

    @Test
    fun `timeout and buttons both acknowledge until exit`() {
        assertTrue(directional.contains("val acknowledge = activeDismissAction0647"))
        assertTrue(directional.contains("runCatching { acknowledge?.invoke() }"))
        assertTrue(directional.contains("dismiss_until_exit=true"))
        assertTrue(directional.contains("ALERT_OVERLAY_TIMEOUT_DISMISSED_0647"))
        assertTrue(directional.contains("ALERT_OVERLAY_BUTTON_"))
    }

    @Test
    fun `legacy alert and radar overlays also timeout after twenty seconds`() {
        assertTrue(legacy.contains("const val ALERT_POPUP_TIMEOUT_MILLIS_0647 = 20_000L"))
        assertTrue(legacy.contains("LEGACY_ALERT_POPUP_TIMEOUT_STARTED_0647"))
        assertTrue(legacy.contains("LEGACY_ALERT_POPUP_TIMEOUT_DISMISSED_0647"))
        assertTrue(legacy.contains("mainHandler.postDelayed(timeout, ALERT_POPUP_TIMEOUT_MILLIS_0647)"))
        assertTrue(legacy.contains("alertPopupTargetId0647 = \"saved-"))
        assertTrue(legacy.contains("alertPopupTargetId0647 = \"radar-"))
    }

    @Test
    fun `legacy body has no dismiss touch listener and destructive confirmation remains`() {
        val savedAlert = legacy
            .substringAfter("fun showProximityAlert(")
            .substringBefore("fun showImportedRadarAlert(")
        assertFalse(savedAlert.contains("setOnTouchListener"))
        assertTrue(savedAlert.contains("showDeleteConfirmation(alert, actions, scale)"))
        assertTrue(savedAlert.contains("cancelAlertPopupTimeout0647(\"DELETE_BUTTON\")"))
    }
}
