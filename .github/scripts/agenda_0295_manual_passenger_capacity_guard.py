from pathlib import Path

ROOT = Path('.')


def read(path):
    return (ROOT / path).read_text()


def write(path, text):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 anchor, found {count}')
    return text.replace(old, new, 1)

# Version: 0.1.294 / 5587 -> 0.1.295 / 5588.
build_path = 'app/build.gradle.kts'
build = read(build_path)
build = replace_once(build, 'versionCode = 5587', 'versionCode = 5588', 'versionCode')
build = replace_once(build, 'versionName = "0.1.294"', 'versionName = "0.1.295"', 'versionName')
write(build_path, build)

# Fail closed only at the external-card preparation boundary. Local/manual trips
# continue to use the existing SeatAvailabilityEngine normally.
flow_path = 'app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripGlobalPassengerFlow0256.kt'
flow = read(flow_path)
anchor = '''internal fun timelineExternalBackingTripId(entry: TripTimelineEntry): String? =
    timelineStrongExternalTripKey(entry)?.let { "timeline-ext-${sha256Short0256(it, 24)}" }
'''
replacement = anchor + '''
internal fun timelineManualPassengerOccupancyKnown(entry: TripTimelineEntry): Boolean =
    timelineStrongExternalTripKey(entry) == null || entry.blablaPassengerRosterComplete == true
'''
flow = replace_once(flow, anchor, replacement, 'occupancy authority helper')
old_prepare = '''internal fun prepareTimelineTripForPassenger(
    entry: TripTimelineEntry,
    store: TripStore,
): TimelinePassengerTripPreparation {
    val strongExternal = timelineStrongExternalTripKey(entry)
'''
new_prepare = '''internal fun prepareTimelineTripForPassenger(
    entry: TripTimelineEntry,
    store: TripStore,
): TimelinePassengerTripPreparation {
    val strongExternal = timelineStrongExternalTripKey(entry)
    require(timelineManualPassengerOccupancyKnown(entry)) {
        "A ocupação BlaBlaCar deste card ainda não foi lida por completo. Sincronize este card antes de adicionar passageiro por fora."
    }
'''
flow = replace_once(flow, old_prepare, new_prepare, 'prepare fail-closed guard')
write(flow_path, flow)

# Focused regression for the exact requested contract: selected segment first,
# no over-capacity manual addition, free later segment remains bookable, and
# incomplete external roster cannot authorize a new manual passenger.
test_path = 'app/src/test/java/br/com/mapeiaia/rotacerta/trips/ManualPassengerCapacityGuard0295Test.kt'
test_source = r'''package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualPassengerCapacityGuard0295Test {
    private fun trip() = Trip(
        id = "trip-295",
        title = "A → C",
        departureAtMillis = 1_800_000_000_000L,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
            TripStop(id = "c", order = 2, name = "C"),
        ),
    )

    private fun externalEntry(rosterComplete: Boolean?) = TripTimelineEntry(
        tripId = "blablacar:295",
        profileId = "11111111-1111-4111-8111-111111111111",
        profileLabel = "Perfil externo",
        departureAtMillis = 1_800_000_000_000L,
        arrivalAtMillis = 1_800_003_600_000L,
        origin = "A",
        destination = "C",
        status = TripStatus.PUBLISHED,
        capacity = 4,
        minimumOccupiedSeats = 0,
        maximumOccupiedSeats = 0,
        blablaTripId = "publication-295",
        blablaTripHref = "https://www.blablacar.com.br/rides/offer/publication-295",
        blablaProfileUuid = "11111111-1111-4111-8111-111111111111",
        blablaPassengerRosterComplete = rosterComplete,
    )

    @Test
    fun fullFirstSegmentBlocksCrossingPassengerButLaterFreeSegmentRemainsBookable() {
        val trip = trip()
        val occupiedFirstSegment = Booking(
            id = "full-a-b",
            tripId = trip.id,
            passengerName = "Quatro lugares A-B",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 4,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.BLABLACAR,
        )

        val crossing = SeatAvailabilityEngine.availability(
            trip = trip,
            bookings = listOf(occupiedFirstSegment),
            boardingStopId = "a",
            dropoffStopId = "c",
            requestedSeats = 1,
        )
        val later = SeatAvailabilityEngine.availability(
            trip = trip,
            bookings = listOf(occupiedFirstSegment),
            boardingStopId = "b",
            dropoffStopId = "c",
            requestedSeats = 1,
        )

        assertFalse(crossing.canBook)
        assertTrue(later.canBook)
        assertThrows(IllegalArgumentException::class.java) {
            QuickPassengerEngine.build(
                trip = trip,
                existingBookings = listOf(occupiedFirstSegment),
                request = QuickPassengerRequest(
                    passengerName = "Não cabe",
                    boardingStopId = "a",
                    dropoffStopId = "c",
                    seats = 1,
                ),
            )
        }
        assertTrue(
            QuickPassengerEngine.build(
                trip = trip,
                existingBookings = listOf(occupiedFirstSegment),
                request = QuickPassengerRequest(
                    passengerName = "Cabe depois",
                    boardingStopId = "b",
                    dropoffStopId = "c",
                    seats = 1,
                ),
            ).passenger.seats == 1,
        )
    }

    @Test
    fun externalCardFailsClosedUntilPassengerRosterIsComplete() {
        assertFalse(timelineManualPassengerOccupancyKnown(externalEntry(false)))
        assertFalse(timelineManualPassengerOccupancyKnown(externalEntry(null)))
        assertTrue(timelineManualPassengerOccupancyKnown(externalEntry(true)))
    }

    @Test
    fun localTripDoesNotDependOnExternalRosterCompleteness() {
        val localOnly = externalEntry(false).copy(
            blablaProfileUuid = null,
            blablaTripId = null,
            blablaTripHref = null,
        )
        assertTrue(timelineManualPassengerOccupancyKnown(localOnly))
    }
}
'''
write(test_path, test_source)
