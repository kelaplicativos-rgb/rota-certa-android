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
    fun keepsEveryActionableEventAndOnlyLatestTerminalProofPerTrip() {
        val pending = event("trip-a", 5, TripPublicationStatus0387.PENDING)
        val retry = event("trip-b", 7, TripPublicationStatus0387.FAILED_RETRYABLE)
        val finalFailure = event("trip-c", 9, TripPublicationStatus0387.FAILED_FINAL)
        val input = listOf(
            event("trip-a", 1, TripPublicationStatus0387.DELIVERED),
            event("trip-a", 2, TripPublicationStatus0387.SUPERSEDED),
            event("trip-a", 3, TripPublicationStatus0387.DELIVERED),
            pending,
            event("trip-b", 4, TripPublicationStatus0387.DELIVERED),
            event("trip-b", 6, TripPublicationStatus0387.SUPERSEDED),
            retry,
            finalFailure,
        )

        val output = compactTripPublicationOutbox0600(input)

        assertTrue(pending in output)
        assertTrue(retry in output)
        assertTrue(finalFailure in output)
        val terminalA = output.filter {
            it.canonicalTripId == "trip-a" &&
                it.status in setOf(TripPublicationStatus0387.DELIVERED, TripPublicationStatus0387.SUPERSEDED)
        }
        val terminalB = output.filter {
            it.canonicalTripId == "trip-b" &&
                it.status in setOf(TripPublicationStatus0387.DELIVERED, TripPublicationStatus0387.SUPERSEDED)
        }
        assertEquals(listOf(3L), terminalA.map { it.revision })
        assertEquals(listOf(6L), terminalB.map { it.revision })
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
