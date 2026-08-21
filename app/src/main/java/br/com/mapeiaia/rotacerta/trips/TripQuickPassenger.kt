package br.com.mapeiaia.rotacerta.trips

import java.util.UUID

/** A compact request used by the timeline and the existing trip screen. */
data class QuickPassengerRequest(
    val passengerName: String,
    val passengerContact: String = "",
    val boardingStopId: String,
    val dropoffStopId: String,
    val seats: Int = 1,
    val source: BookingSource = BookingSource.PRIVATE,
    val sourceReference: String = "",
    /** Optional source where the same physical seat is already blocked/reserved. */
    val mirrorSource: BookingSource? = null,
    /** Optional existing RESERVED_SEAT booking to link instead of creating a new mirror. */
    val linkReservedSeatBookingId: String? = null,
)

data class QuickPassengerPlan(
    val passenger: Booking,
    val mirror: Booking? = null,
    val linkedReservedSeatUpdate: Booking? = null,
) {
    fun writes(): List<Booking> = buildList {
        add(passenger)
        mirror?.let(::add)
        linkedReservedSeatUpdate?.let(::add)
    }
}

object QuickPassengerEngine {
    fun build(
        trip: Trip,
        existingBookings: List<Booking>,
        request: QuickPassengerRequest,
        nowMillis: Long = System.currentTimeMillis(),
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): QuickPassengerPlan {
        require(request.passengerName.isNotBlank()) { "Informe o nome do passageiro." }
        require(request.seats in 1..trip.capacity) { "Quantidade de vagas inválida." }
        require(request.mirrorSource == null || request.mirrorSource != request.source) {
            "A vaga espelho deve usar outra origem."
        }
        require(request.mirrorSource == null || request.linkReservedSeatBookingId == null) {
            "Escolha uma vaga espelho nova ou uma vaga existente, não as duas."
        }

        val linked = request.linkReservedSeatBookingId?.let { id ->
            existingBookings.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Vaga reservada para vínculo não encontrada.")
        }
        if (linked != null) {
            require(linked.tripId == trip.id) { "A vaga vinculada pertence a outra viagem." }
            require(linked.capacityClaimType == CapacityClaimType.RESERVED_SEAT) {
                "Somente uma vaga reservada pode ser vinculada."
            }
            require(linked.status == BookingStatus.CONFIRMED ||
                (linked.status == BookingStatus.HELD && (linked.holdExpiresAtMillis == null || linked.holdExpiresAtMillis > nowMillis))) {
                "A vaga vinculada não está ativa."
            }
            require(linked.boardingStopId == request.boardingStopId && linked.dropoffStopId == request.dropoffStopId) {
                "A vaga vinculada precisa usar o mesmo trecho."
            }
            require(linked.seats == request.seats) { "A vaga vinculada precisa ter a mesma quantidade de lugares." }
        }

        // If the passenger is naming an already blocked RESERVED_SEAT, that seat is
        // temporarily removed from the availability check because the plan will link
        // both records into the same physical occupancy group instead of adding a seat.
        val availabilityBase = if (linked == null) existingBookings else existingBookings.filterNot { it.id == linked.id }
        val availability = SeatAvailabilityEngine.availability(
            trip = trip,
            bookings = availabilityBase,
            boardingStopId = request.boardingStopId,
            dropoffStopId = request.dropoffStopId,
            requestedSeats = request.seats,
            nowMillis = nowMillis,
        )
        require(availability.canBook) {
            "Somente ${availability.availableSeats} vaga(s) disponível(is) nesse trecho."
        }

        val groupId = when {
            linked != null -> linked.occupancyGroupId?.trim()?.takeIf(String::isNotEmpty) ?: "seat-${idFactory()}"
            request.mirrorSource != null -> "seat-${idFactory()}"
            else -> null
        }
        val passengerId = idFactory()
        val passenger = Booking(
            id = passengerId,
            tripId = trip.id,
            passengerName = request.passengerName.trim(),
            passengerContact = request.passengerContact.trim(),
            boardingStopId = request.boardingStopId,
            dropoffStopId = request.dropoffStopId,
            seats = request.seats,
            status = BookingStatus.CONFIRMED,
            source = request.source,
            capacityClaimType = CapacityClaimType.PASSENGER,
            sourceReference = request.sourceReference.trim(),
            occupancyGroupId = groupId,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )

        val mirror = request.mirrorSource?.let { mirrorSource ->
            Booking(
                id = idFactory(),
                tripId = trip.id,
                passengerName = "Vaga espelho — ${request.passengerName.trim()}",
                passengerContact = "",
                boardingStopId = request.boardingStopId,
                dropoffStopId = request.dropoffStopId,
                seats = request.seats,
                status = BookingStatus.CONFIRMED,
                source = mirrorSource,
                capacityClaimType = CapacityClaimType.RESERVED_SEAT,
                sourceReference = request.sourceReference.trim(),
                occupancyGroupId = groupId,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
            )
        }
        val linkedUpdate = linked?.copy(occupancyGroupId = groupId, updatedAtMillis = nowMillis)
        val plan = QuickPassengerPlan(passenger, mirror, linkedUpdate)

        // Final guard: the whole plan must still fit the physical vehicle. Mirror/link
        // records share the same occupancyGroupId and therefore cannot double count.
        val projected = existingBookings
            .filterNot { existing -> linkedUpdate != null && existing.id == linkedUpdate.id }
            .plus(plan.writes())
        val projectedLoads = SeatAvailabilityEngine.segmentLoads(trip, projected, nowMillis)
        require(projectedLoads.none { it.occupiedSeats > trip.capacity }) {
            "A inclusão ultrapassaria a capacidade física do veículo."
        }
        return plan
    }

    fun activeReservedSeatLinks(bookings: List<Booking>, nowMillis: Long = System.currentTimeMillis()): List<Booking> =
        bookings.filter { booking ->
            booking.capacityClaimType == CapacityClaimType.RESERVED_SEAT &&
                (booking.status == BookingStatus.CONFIRMED ||
                    (booking.status == BookingStatus.HELD && (booking.holdExpiresAtMillis == null || booking.holdExpiresAtMillis > nowMillis)))
        }
}
