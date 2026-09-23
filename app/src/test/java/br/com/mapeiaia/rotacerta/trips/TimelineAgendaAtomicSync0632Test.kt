package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineAgendaAtomicSync0632Test {
    private fun booking(
        source: BookingSource,
        status: BookingStatus = BookingStatus.CONFIRMED,
        operationalStatus: PassengerOperationalStatus = PassengerOperationalStatus.CONFIRMED,
    ) = Booking(
        id = "booking-0632",
        tripId = "trip-0632",
        passengerId = "passenger-0632",
        passengerName = "Passageiro",
        passengerContact = "+5511999999999",
        boardingStopId = "a",
        dropoffStopId = "b",
        seats = 1,
        status = status,
        operationalStatus = operationalStatus,
        source = source,
        capacityClaimType = CapacityClaimType.PASSENGER,
        occupancyGroupId = "group-0632",
    )

    @Test
    fun rotaCertaMutationsUseProtectedCanonicalBackendRoutes() {
        val active = booking(BookingSource.ROTA_CERTA)

        assertEquals(
            PassengerMutationTransport0632.PROTECTED_UPDATE,
            passengerMutationTransport0632(active, "RESERVATION_EDITED_0631"),
        )
        assertEquals(
            PassengerMutationTransport0632.PROTECTED_DECISION_APPROVE,
            passengerMutationTransport0632(
                active.copy(status = BookingStatus.REQUESTED),
                "RESERVATION_APPROVED",
            ),
        )
        assertEquals(
            PassengerMutationTransport0632.PROTECTED_DECISION_REJECT,
            passengerMutationTransport0632(
                active.copy(status = BookingStatus.REQUESTED),
                "RESERVATION_REJECTED",
            ),
        )
        assertEquals(
            PassengerMutationTransport0632.PROTECTED_OPERATIONAL,
            passengerMutationTransport0632(active, "PASSENGER_STATUS_IN_CAR"),
        )
        assertEquals(
            PassengerMutationTransport0632.PROTECTED_CANCEL,
            passengerMutationTransport0632(
                active.copy(
                    status = BookingStatus.CANCELLED,
                    operationalStatus = PassengerOperationalStatus.CANCELLED,
                ),
                "BOOKING_CANCELLED_BY_DRIVER",
            ),
        )
    }

    @Test
    fun nonProtectedPassengerEditUsesDriverUpsertAndStatusUsesAtomicStatusRoute() {
        val manual = booking(BookingSource.PRIVATE)
        assertEquals(
            PassengerMutationTransport0632.DRIVER_UPSERT,
            passengerMutationTransport0632(manual, "BOOKING_CHANGED_BY_DRIVER"),
        )
        assertEquals(
            PassengerMutationTransport0632.PROTECTED_OPERATIONAL,
            passengerMutationTransport0632(manual, "PASSENGER_STATUS_AT_LOCATION"),
        )
    }

    @Test
    fun androidCommitsPublishedMutationOnlyAfterRemoteAck() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        val start = source.indexOf("internal suspend fun persistCanonicalPassengerMutation0582(")
        val end = source.indexOf("internal fun passengerOperationalMutation0582(", start)
        assertTrue(start >= 0 && end > start)
        val function = source.substring(start, end)

        assertTrue(function.contains("settings.configured && remoteTripId != null"))
        assertTrue(function.contains("publishPassengerMutationRemote0632("))
        assertTrue(function.contains("ack.booking.toLocalBooking("))
        assertTrue(function.contains("localCommittedAfterAck=true"))
        assertTrue(function.contains("projectionAtomic=true"))
        assertTrue(function.indexOf("publishPassengerMutationRemote0632(") < function.indexOf("store.saveBooking("))
        assertTrue(function.contains("origin == TripRecordOrigin.EXTERNAL_BACKING"))
        assertTrue(function.contains("localWrite=false"))
    }

    @Test
    fun backendMutationRecalculatesCapacityAndRejectsOverbookingInSameTransaction() {
        val backend = File("../trip-platform/functions/index.js").readText()
        val start = backend.indexOf("async function mutateProtectedBooking(")
        val end = backend.indexOf("async function updatePassengerBooking(", start)
        assertTrue(start >= 0 && end > start)
        val fn = backend.substring(start, end)

        assertTrue(fn.contains("db.runTransaction"))
        assertTrue(fn.contains("reconciledSegmentCapacity"))
        assertTrue(fn.contains("assertNoOverbooking"))
        assertTrue(fn.contains("canonicalCapacityPersistence"))
        assertTrue(fn.contains("canonicalServerProjectionPatch0468"))
        assertTrue(fn.contains("publicationRevision: entityRevision"))
    }

    @Test
    fun publicAgendaUsesCanonicalChangeChannelAndNeverShowsContradictoryOccupancy() {
        val shell = File("../trip-platform/public/public-agenda-shell-0569.js").readText()
        assertTrue(shell.contains("watchAgendaCanonicalChanges0632"))
        assertTrue(shell.contains("/changes?since="))
        assertTrue(shell.contains("changeCursor0495"))
        assertTrue(shell.contains("rawPassengerSeats + availableSeats === capacity"))
        assertTrue(shell.contains("derivedPassengerSeats"))
        assertTrue(shell.contains("projectionAdjusted0632"))
        assertTrue(shell.contains("}, 15_000);"))
    }
}
