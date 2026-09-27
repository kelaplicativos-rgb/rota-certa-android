package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassengerQuickOps0673Test {
    private fun row(
        name: String,
        boarding: Int?,
        dropoff: Int?,
        operational: PassengerOperationalStatus = PassengerOperationalStatus.PENDING,
        lastSelection: String = "",
    ) = EnhancedPassengerCardRow(
        name = name,
        phone = "+5511999999999",
        seats = 1,
        boarding = "A",
        dropoff = "D",
        sources = setOf(BookingSource.BLABLACAR),
        boardingStopIndex = boarding,
        dropoffStopIndex = dropoff,
        operationalStatus = operational,
        lastDriverSelection = lastSelection,
    )

    @Test
    fun passengerNameIsRenderedOnlyAtTheRealBoardingSegment() {
        val ana = row("Ana", 0, 3)
        val bruno = row("Bruno", 1, 3)
        val rows = listOf(ana, bruno)

        assertEquals(listOf("Ana"), passengerRowsBoardingAtSegment0673(rows, 0).map { it.name })
        assertEquals(listOf("Bruno"), passengerRowsBoardingAtSegment0673(rows, 1).map { it.name })
        assertTrue(passengerActiveOnSegment0671(ana, 1))
        assertTrue(passengerActiveOnSegment0671(bruno, 2))
        assertFalse(passengerRowsBoardingAtSegment0673(rows, 2).any())
    }

    @Test
    fun unresolvedBoardingRemainsVisibleInFallbackInsteadOfDisappearing() {
        val unknown = row("Sem índice", null, null)
        assertEquals(listOf("Sem índice"), passengerRowsWithoutBoardingStop0673(listOf(unknown)).map { it.name })
    }

    @Test
    fun implicitConfirmedStateIsPendingUntilDriverExplicitlyConfirmsContact() {
        assertEquals(
            PassengerOperationalStatus.PENDING,
            effectivePassengerOperationalStatus0673(PassengerOperationalStatus.CONFIRMED, ""),
        )
        assertEquals(
            PassengerOperationalStatus.PENDING,
            effectivePassengerOperationalStatus0673(PassengerOperationalStatus.CONFIRMED, "APPROVE"),
        )
        assertEquals(
            PassengerOperationalStatus.CONFIRMED,
            effectivePassengerOperationalStatus0673(PassengerOperationalStatus.CONFIRMED, "CONFIRMED"),
        )
    }

    @Test
    fun pendingIsARealOperationalMutationWithoutCancellingTheReservation() {
        val booking = Booking(
            id = "b-0673",
            tripId = "t-0673",
            passengerName = "Emily",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            operationalStatus = PassengerOperationalStatus.CONFIRMED,
            lastDriverSelection = "CONFIRMED",
            source = BookingSource.BLABLACAR,
        )
        val pending = passengerOperationalMutation0582(booking, "PENDING")
        assertEquals(BookingStatus.CONFIRMED, pending.status)
        assertEquals(PassengerOperationalStatus.PENDING, pending.operationalStatus)
        assertEquals("PENDING", pending.lastDriverSelection)
    }

    @Test
    fun compactRowAndCentralKeepTheRequestedOneTapActions() {
        val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        assertTrue(ui.contains("PassengerQuickActionLine0673"))
        assertTrue(ui.contains("compactSegmentMode0673"))
        assertTrue(ui.contains("passengerRowsBoardingAtSegment0673"))
        assertTrue(ui.contains("R.drawable.ic_whatsapp_action"))
        assertTrue(ui.contains("Text(\"💬\""))
        assertTrue(ui.contains("Text(\"📍\""))
        assertTrue(ui.contains("Text(\"🏁\""))
        assertTrue(ui.contains("Text(\"🛰️\""))
        assertTrue(ui.contains("Text(\"🚦\""))
        assertFalse(ui.contains("Text(\"👆\""))
        assertTrue(ui.contains("PASSENGER_QUICK_TRACKING_0674"))
        assertTrue(ui.contains("statusShortcutRow0673"))
        assertTrue(ui.contains("GPS embarque"))
        assertTrue(ui.contains("GPS desembarque"))
        assertTrue(ui.contains("Editar telefone"))
        assertTrue(ui.contains("Histórico"))
        assertTrue(ui.contains("Text(\"Pendente\")"))
    }

    @Test
    fun backendAcceptsPendingWithoutFakingPassengerCompletion() {
        val backend = File("../trip-platform/functions/index.js").readText()
        assertTrue(backend.contains(
            "new Set([\"PENDING\", \"CONFIRMED\", \"AT_LOCATION\", \"IN_CAR\", \"PAID\", \"COMPLETED\", \"CANCELLED\"])",
        ))
        assertTrue(backend.contains("selection === \"PENDING\" ? \"PASSENGER_STATUS_PENDING\""))
        assertTrue(backend.contains("passengerRecipients: selection === \"PENDING\" ? []"))
        assertTrue(backend.contains("operationalStatus: \"PENDING\""))
        assertFalse(
            backend.substringAfter("async function mutateDriverPassengerOperationalStatus(")
                .substringBefore("\nasync function ")
                .contains("selection === \"PENDING\" ? \"PASSENGER_COMPLETED\""),
        )
    }
}
