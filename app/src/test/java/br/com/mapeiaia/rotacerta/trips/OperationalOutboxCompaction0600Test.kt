package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalOutboxCompaction0600Test {
    private fun event(
        trip: String,
        revision: Long,
        status: TripPublicationStatus0387,
        createdAt: Long = revision,
    ) = TripPublicationOutboxEvent0387(
        id = "$trip-$revision-$status",
        tenantId = "tenant-test",
        canonicalTripId = trip,
        revision = revision,
        operation = TripPublicationOperation0387.UPSERT_LOCAL,
        mutationType = "TEST",
        source = "TEST",
        snapshot = TripPublicationSnapshot0387(
            semanticSignature = "$trip-signature-$revision",
            seatAllocationVersion = revision,
        ),
        status = status,
        createdAtMillis = createdAt,
        updatedAtMillis = createdAt,
    )

    @Test
    fun keepsOnlyNewestDurableRevisionPerTrip0609() {
        val pending = event("trip-a", 5, TripPublicationStatus0387.PENDING)
        val retry = event("trip-b", 7, TripPublicationStatus0387.FAILED_RETRYABLE)
        val finalFailure = event("trip-c", 9, TripPublicationStatus0387.FAILED_FINAL)
        val input = listOf(
            event("trip-a", 1, TripPublicationStatus0387.DELIVERED),
            event("trip-a", 2, TripPublicationStatus0387.PENDING),
            event("trip-a", 3, TripPublicationStatus0387.DELIVERED),
            pending,
            event("trip-b", 4, TripPublicationStatus0387.DELIVERED),
            event("trip-b", 6, TripPublicationStatus0387.PENDING),
            retry,
            finalFailure,
        )

        val output = compactTripPublicationOutbox0600(input)

        assertEquals(3, output.size)
        assertTrue(pending in output)
        assertTrue(retry in output)
        assertTrue(finalFailure in output)
        assertEquals(listOf(5L), output.filter { it.canonicalTripId == "trip-a" }.map { it.revision })
        assertEquals(listOf(7L), output.filter { it.canonicalTripId == "trip-b" }.map { it.revision })
    }

    @Test
    fun burstOfRevisionsForSameTripCollapsesBeforeSerialization0609() {
        val burst = (1L..600L).map { revision ->
            event("trip-burst", revision, TripPublicationStatus0387.PENDING)
        }
        val output = compactTripPublicationOutbox0600(burst, targetMaxEvents = 32)
        assertEquals(1, output.size)
        assertEquals(600L, output.single().revision)
    }


    @Test
    fun newerTerminalRevisionWinsOlderFailure0609() {
        val output = compactTripPublicationOutbox0600(
            listOf(
                event("trip-terminal", 7, TripPublicationStatus0387.FAILED_FINAL),
                event("trip-terminal", 8, TripPublicationStatus0387.DELIVERED),
            ),
        )
        assertEquals(1, output.size)
        assertEquals(8L, output.single().revision)
        assertEquals(TripPublicationStatus0387.DELIVERED, output.single().status)
    }

    @Test
    fun softTargetNeverDropsActionableWork() {
        val active = (1L..520L).map { revision ->
            event("trip-$revision", revision, TripPublicationStatus0387.PENDING)
        }
        val output = compactTripPublicationOutbox0600(active, targetMaxEvents = 32)
        assertEquals(active.size, output.size)
        assertTrue(output.all { it.status == TripPublicationStatus0387.PENDING })
    }
}
