package br.com.mapeiaia.rotacerta.trips

import java.util.UUID

/** A compact request used by the timeline and the existing trip screen. */
data class QuickPassengerRequest(
    val passengerName: String,
    val passengerContact: String = "",
    val passengerId: String = "",
    val boardingStopId: String,
    val dropoffStopId: String,
    val seats: Int = 1,
    val fareMinorUnits: Long? = null,
    val fareCurrencyCode: String = "",
    val boardingAddress: String = "",
    val dropoffAddress: String = "",
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
        require(request.fareMinorUnits == null || request.fareMinorUnits > 0L) { "Valor da reserva inválido." }
        require(request.fareCurrencyCode.isBlank() || request.fareCurrencyCode.matches(Regex("[A-Za-z]{3}"))) {
            "Moeda da reserva inválida."
        }
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
        val canonicalPassengerId = request.passengerId.trim().takeIf(String::isNotEmpty) ?: idFactory()
        val bookingId = idFactory()
        val passenger = Booking(
            id = bookingId,
            tripId = trip.id,
            passengerName = request.passengerName.trim(),
            passengerContact = request.passengerContact.trim(),
            passengerId = canonicalPassengerId,
            boardingStopId = request.boardingStopId,
            dropoffStopId = request.dropoffStopId,
            seats = request.seats,
            fareMinorUnits = request.fareMinorUnits,
            fareCurrencyCode = request.fareCurrencyCode.trim().uppercase(),
            boardingAddress = request.boardingAddress.trim(),
            dropoffAddress = request.dropoffAddress.trim(),
            localMetadataTouched = true,
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

        val projected = existingBookings
            .filterNot { existing -> linkedUpdate != null && existing.id == linkedUpdate.id }
            .plus(plan.writes())
        val projectedLoads = SeatAvailabilityEngine.segmentLoads(trip, projected, nowMillis)
        require(projectedLoads.none { it.occupiedSeats > trip.capacity }) {
            "A inclusão ultrapassaria a capacidade física do veículo."
        }
        return plan
    }

    fun updateManualBooking(
        trip: Trip,
        existingBookings: List<Booking>,
        booking: Booking,
        boardingStopId: String,
        dropoffStopId: String,
        seats: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Booking {
        require(booking.tripId == trip.id) { "A reserva pertence a outra viagem." }
        require(booking.source in setOf(BookingSource.PRIVATE, BookingSource.OTHER)) { "Somente passageiro manual pode ser editado aqui." }
        require(booking.capacityClaimType == CapacityClaimType.PASSENGER) { "A reserva não representa um passageiro." }
        require(seats in 1..trip.capacity) { "Quantidade de lugares inválida." }
        val availability = SeatAvailabilityEngine.availability(
            trip = trip,
            bookings = existingBookings.filterNot { it.id == booking.id },
            boardingStopId = boardingStopId,
            dropoffStopId = dropoffStopId,
            requestedSeats = seats,
            nowMillis = nowMillis,
        )
        require(availability.canBook) { "Somente ${availability.availableSeats} vaga(s) disponível(is) nesse trecho." }
        return booking.copy(
            boardingStopId = boardingStopId,
            dropoffStopId = dropoffStopId,
            seats = seats,
            status = BookingStatus.CONFIRMED,
            updatedAtMillis = nowMillis,
        )
    }

    fun activeReservedSeatLinks(bookings: List<Booking>, nowMillis: Long = System.currentTimeMillis()): List<Booking> =
        bookings.filter { booking ->
            booking.capacityClaimType == CapacityClaimType.RESERVED_SEAT &&
                (booking.status == BookingStatus.CONFIRMED ||
                    (booking.status == BookingStatus.HELD && (booking.holdExpiresAtMillis == null || booking.holdExpiresAtMillis > nowMillis)))
        }
}
