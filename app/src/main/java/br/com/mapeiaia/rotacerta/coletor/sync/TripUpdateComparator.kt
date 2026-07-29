package br.com.mapeiaia.rotacerta.coletor.sync

import br.com.mapeiaia.rotacerta.coletor.model.Booking
import br.com.mapeiaia.rotacerta.coletor.model.BookingStatus
import br.com.mapeiaia.rotacerta.coletor.model.Trip

sealed interface TripChange {
    data class NewBooking(val booking: Booking) : TripChange
    data class CancelledBooking(val previous: Booking, val current: Booking) : TripChange
    data class BookingChanged(
        val previous: Booking,
        val current: Booking,
        val changedFields: Set<String>,
        val protectedConflicts: Set<String>,
    ) : TripChange
    data class TripChanged(val changedFields: Set<String>) : TripChange
}

data class TripUpdatePreview(
    val changes: List<TripChange>,
    val hasChanges: Boolean = changes.isNotEmpty(),
)

object TripUpdateComparator {
    fun compare(saved: Trip, remote: Trip): TripUpdatePreview {
        require(saved.externalTripId == null || remote.externalTripId == null || saved.externalTripId == remote.externalTripId) {
            "A viagem remota não corresponde à viagem salva"
        }

        val changes = mutableListOf<TripChange>()
        val tripFields = buildSet {
            if (saved.date != remote.date) add("date")
            if (saved.departureAt != remote.departureAt) add("departureAt")
            if (saved.arrivalAt != remote.arrivalAt) add("arrivalAt")
            if (saved.origin != remote.origin) add("origin")
            if (saved.finalDestination != remote.finalDestination) add("finalDestination")
            if (saved.intermediateStops != remote.intermediateStops) add("intermediateStops")
            if (saved.offeredSeats != remote.offeredSeats) add("offeredSeats")
            if (saved.status != remote.status) add("status")
        }
        if (tripFields.isNotEmpty()) changes += TripChange.TripChanged(tripFields)

        val savedByKey = saved.bookings.associateBy(::bookingKey)
        remote.bookings.forEach { current ->
            val previous = savedByKey[bookingKey(current)]
            if (previous == null) {
                changes += TripChange.NewBooking(current)
                return@forEach
            }

            val changedFields = buildSet {
                if (previous.seats != current.seats) add("seats")
                if (previous.boarding != current.boarding) add("boarding")
                if (previous.dropOff != current.dropOff) add("dropOff")
                if (previous.grossAmount != current.grossAmount) add("grossAmount")
                if (previous.netAmount != current.netAmount) add("netAmount")
                if (previous.paymentMethod != current.paymentMethod) add("paymentMethod")
                if (previous.paymentStatus != current.paymentStatus) add("paymentStatus")
                if (previous.bookingStatus != current.bookingStatus) add("bookingStatus")
                if (previous.passenger.phone != current.passenger.phone) add("phone")
                if (previous.conversationUrl != current.conversationUrl) add("conversationUrl")
            }
            if (changedFields.isEmpty()) return@forEach

            if (previous.bookingStatus != BookingStatus.CANCELLED && current.bookingStatus == BookingStatus.CANCELLED) {
                changes += TripChange.CancelledBooking(previous, current)
            } else {
                changes += TripChange.BookingChanged(
                    previous = previous,
                    current = current,
                    changedFields = changedFields,
                    protectedConflicts = changedFields.intersect(previous.manuallyLockedFields),
                )
            }
        }

        return TripUpdatePreview(changes)
    }

    private fun bookingKey(booking: Booking): String =
        booking.externalBookingId
            ?: listOf(
                booking.passenger.externalProfileId.orEmpty(),
                booking.passenger.name.trim().lowercase(),
                booking.boarding.address.trim().lowercase(),
                booking.dropOff.address.trim().lowercase(),
            ).joinToString("|")
}
