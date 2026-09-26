package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CentralPassengerControls0593Test {
    @Test
    fun centralDayEmbedsTheCanonicalPassengerOperatorInsteadOfReadOnlyRows() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt").readText()

        assertTrue(source.contains("EnhancedPassengerTimelineSection("))
        assertTrue(source.contains("compactEmbeddedControls0593 = true"))
        assertTrue(source.contains("canonicalBookings0494 = bookings.filter"))
        assertTrue(source.contains("onRefreshLocal()"))
    }

    @Test
    fun compactOperatorExposesStatusAndRightSideEmojiShortcuts() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        val start = source.indexOf("if (compactEmbeddedControls0593)")
        val end = source.indexOf("if (!compactEmbeddedControls0593)", start)
        assertTrue(start >= 0 && end > start)
        val compact = source.substring(start, end)

        assertTrue(compact.contains("statusMenuOpen = true"))
        assertTrue(compact.contains("selectOperationalStatus(\"CONFIRMED\")"))
        assertTrue(compact.contains("selectOperationalStatus(\"AT_LOCATION\")"))
        assertTrue(compact.contains("selectOperationalStatus(\"IN_CAR\")"))
        assertTrue(compact.contains("selectOperationalStatus(\"PAID\")"))
        assertTrue(compact.contains("selectOperationalStatus(\"COMPLETED\")"))
        assertTrue(compact.contains("selectOperationalStatus(\"CANCELLED\")"))
        assertTrue(compact.contains("ic_whatsapp_action"))
        assertTrue(compact.contains("Text(\"📍\""))
        assertTrue(compact.contains("Text(\"🏁\""))
        assertTrue(compact.contains("Text(\"💰\""))
        assertTrue(compact.contains("Text(\"💬\""))
        assertTrue(compact.contains("horizontalArrangement = Arrangement.End"))
        assertFalse(compact.contains("onOpenTimeline"))
    }

    @Test
    fun compactOperatorEmitsCentralDayContextualDiagnostics() {
        val centralSource = File("src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt").readText()
        val passengerSource = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()

        assertTrue(centralSource.contains("CENTRAL_DAY_RENDER_READY_0594"))
        assertTrue(centralSource.contains("CENTRAL_DAY_PASSENGER_PANEL_TOGGLE_0594"))
        assertTrue(centralSource.contains("DiagnosticModule0507.CENTRAL_DAY"))
        assertTrue(passengerSource.contains("CENTRAL_DAY_PASSENGER_STATUS_REQUEST_0594"))
        assertTrue(passengerSource.contains("CENTRAL_DAY_PASSENGER_STATUS_RESULT_0594"))
        assertTrue(passengerSource.contains("CENTRAL_DAY_PASSENGER_SHORTCUT_0594"))
    }

    @Test
    fun compactOperatorReusesCanonicalMutationPath() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        assertTrue(source.contains("persistCanonicalPassengerMutation0582("))
        assertTrue(source.contains("PASSENGER_STATUS_"))
        assertTrue(source.contains("BookingRealtimeEvents0356.notifyChanged()"))
        assertTrue(source.contains("PASSENGER_IN_CAR_NOT_CANCELABLE"))
    }
}
