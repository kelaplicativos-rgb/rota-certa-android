package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassengerTouchIsolation0712Test {
    @Test
    fun trackingShortcutOwnsTapAndLongPressWithoutManualPointerCompetition() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt",
        ).readText()
        val shortcut = source
            .substringAfter("private fun PassengerTrackingShortcut0676(")
            .substringBefore("@Composable\nprivate fun SegmentVacancyLine0671(")

        assertContains(shortcut, ".combinedClickable(")
        assertContains(shortcut, "onLongClick = if (active0676) onLongPress0676 else null")
        assertFalse(shortcut.contains(".pointerInput("))
        assertFalse(shortcut.contains("awaitFirstDown"))
        assertFalse(shortcut.contains("withTimeout"))
    }

    @Test
    fun passengerShareNeverMarksWorkTrackingActiveAndStopReconcilesLocationCore() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt",
        ).readText()
        val publish = source
            .substringAfter("fun publishPassengerTracking0668(")
            .substringBefore("val trackingPermissionLauncher0668")
        val stop = source
            .substringAfter("fun stopPassengerTracking0676(")
            .substringBefore("fun publishPassengerTracking0668(")

        assertContains(publish, "ACTION_ENSURE_LOCATION_CORE_0681")
        assertFalse(publish.contains("ACTION_START"))
        assertContains(stop, "ACTION_RECONCILE_LOCATION_CORE_0681")
        assertContains(stop, "trackingStopInFlight0676")
    }

    @Test
    fun passengerRemoteAckMustPrecedeLocalDeactivation() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/LiveTrackingShare0668.kt",
        ).readText()
        val closePassenger = source
            .substringAfter("suspend fun closePassengerShare(passengerKey: String)")
            .substringBefore("suspend fun closeActiveSession()")

        assertContains(closePassenger, "val response = TrackingRemoteClient0668(settings).closeShare")
        assertContains(closePassenger, "check(response.ok)")
        assertTrue(closePassenger.indexOf("check(response.ok)") < closePassenger.indexOf("repository.save"))
    }

    @Test
    fun operationalCardShellNoLongerCompetesWithEmbeddedShortcuts() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt",
        ).readText()

        assertContains(source, "OPERATIONAL_CARD_OPEN_SURFACE_0712")
        assertFalse(source.contains(".clickable(enabled = manageable0633, onClick = onOpen)"))
        assertContains(source, "onClickLabel = \"Abrir viagem\"")
    }

    @Test
    fun timelineCardShellNoLongerCompetesWithEmbeddedShortcuts() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt",
        ).readText()

        assertContains(source, "TIMELINE_CARD_OPEN_SURFACE_0712")
        assertContains(source, "val toggleTimelineCard0712: () -> Unit")
        assertFalse(
            source.contains(
                ".padding(vertical = 8.dp)\n            .clickable(\n                onClickLabel = if (expanded)",
            ),
        )
    }
}
