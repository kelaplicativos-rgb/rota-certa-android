package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class AgendaPerformance0380Test {
    @Test
    fun concurrentBookingReconcileIsSingleFlightPerTenant(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val executions = AtomicInteger(0)
        val singleFlight = BookingReconcileSingleFlight0380<Int>()

        val first = async {
            singleFlight.execute("tenant-a") {
                executions.incrementAndGet()
                entered.complete(Unit)
                gate.await()
                42
            }
        }
        withTimeout(2_000L) { entered.await() }
        val second = async {
            singleFlight.execute("tenant-a") {
                executions.incrementAndGet()
                99
            }
        }
        delay(75L)
        assertEquals(1, executions.get())
        gate.complete(Unit)

        val firstResult = withTimeout(2_000L) { first.await() }
        val secondResult = withTimeout(2_000L) { second.await() }
        assertEquals(42, firstResult.value)
        assertEquals(42, secondResult.value)
        assertFalse(firstResult.coalesced)
        assertTrue(secondResult.coalesced)
        assertEquals(1, executions.get())
    }

    @Test
    fun differentTenantsDoNotCoalesce(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val entered = AtomicInteger(0)
        val singleFlight = BookingReconcileSingleFlight0380<Int>()
        val left = async {
            singleFlight.execute("tenant-a") {
                entered.incrementAndGet()
                gate.await()
                1
            }
        }
        val right = async {
            singleFlight.execute("tenant-b") {
                entered.incrementAndGet()
                gate.await()
                2
            }
        }
        withTimeout(2_000L) {
            while (entered.get() < 2) delay(10L)
        }
        gate.complete(Unit)
        assertEquals(1, left.await().value)
        assertEquals(2, right.await().value)
        assertEquals(2, entered.get())
    }

    @Test
    fun batchMergeOfFiftyNineImportsNeverDuplicatesIds() {
        fun booking(index: Int) = Booking(
            id = "booking-$index",
            tripId = "trip-${index % 24}",
            passengerName = "Passenger $index",
            boardingStopId = "a",
            dropoffStopId = "b",
            status = BookingStatus.REQUESTED,
        )
        val existing = (0 until 88).map(::booking)
        val updates = (0 until 59).map { index ->
            booking(index).copy(status = BookingStatus.CONFIRMED)
        }
        val once = mergeBookingBatch0380(existing, updates)
        val twice = mergeBookingBatch0380(once, updates)

        assertEquals(88, once.size)
        assertEquals(88, once.map(Booking::id).toSet().size)
        assertEquals(88, twice.size)
        assertEquals(88, twice.map(Booking::id).toSet().size)
        updates.forEach { update ->
            assertEquals(BookingStatus.CONFIRMED, once.single { it.id == update.id }.status)
        }
    }

    @Test
    fun reconcileUsesBatchPersistenceAndPreservesRemoteRevision() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicBookingSync0296.kt").readText()
        val store = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripStore.kt").readText()
        assertTrue(source.contains("BOOKING_RECONCILE_COALESCED"))
        assertTrue(source.contains("store.saveBookingsBatch("))
        assertFalse(source.contains("pendingImports.forEach { mapped ->"))
        assertTrue(store.contains("preserveSourceUpdatedAt"))
        assertTrue(store.contains("mergeBookingBatch0380"))
        assertTrue(store.contains("refreshTripStatusesBatch"))
    }
}
