package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaTripControl0407Test {
    private val target = BlaBlaTripTarget0407(
        tenantId = "tenant-test",
        accountId = "account-test",
        profileUuid = "11111111-1111-4111-8111-111111111111",
        tripId = "trip_test_123456",
        tripHref = "https://www.blablacar.com.br/trip/trip_test_123456",
    )

    @Test
    fun staticOnlyCapabilitiesAreNotExposedAsProductionActions() {
        val snapshot = BlaBlaCapabilityRegistry0407.snapshot(target = target)
        val palette = buildBlaBlaTripActionPalette0407(snapshot, hasPublicationHref = true)

        assertTrue(BlaBlaTripAction0407.REVERIFY in palette.primary)
        assertTrue(BlaBlaTripAction0407.OPEN_PUBLICATION in palette.overflow)
        assertFalse(snapshot.canShow(BlaBlaTripCapability0407.SET_TRIP_BOOST))
        assertEquals(
            BlaBlaCapabilityEvidence0407.DISCOVERED_STATIC,
            snapshot.state(BlaBlaTripCapability0407.SET_TRIP_BOOST)?.evidence,
        )
    }

    @Test
    fun missingStrongTargetFailsClosed() {
        val snapshot = BlaBlaCapabilityRegistry0407.snapshot(target = null)
        val palette = buildBlaBlaTripActionPalette0407(snapshot, hasPublicationHref = true)

        assertFalse(snapshot.canShow(BlaBlaTripCapability0407.REVERIFY_TRIP))
        assertTrue(palette.primary.isEmpty())
        assertTrue(palette.overflow.isEmpty())
    }

    @Test
    fun seatsBecomeWritableOnlyAfterVerifiedReadbackState() {
        val before = BlaBlaCapabilityRegistry0407.snapshot(
            target = target,
            seatSyncState = BlaBlaPublicationSeatSyncState(
                profileUuid = target.profileUuid,
                tripId = target.tripId,
                lastObservedPublishedSeats = 3,
                state = BlaBlaPublicationSeatSyncVisualState.AVAILABLE,
            ),
        )
        assertFalse(before.state(BlaBlaTripCapability0407.SET_TRIP_SEATS)?.writable == true)

        val after = BlaBlaCapabilityRegistry0407.snapshot(
            target = target,
            seatSyncState = BlaBlaPublicationSeatSyncState(
                profileUuid = target.profileUuid,
                tripId = target.tripId,
                desiredPublishedSeats = 2,
                lastObservedPublishedSeats = 2,
                state = BlaBlaPublicationSeatSyncVisualState.SYNCED,
            ),
        )
        assertTrue(after.state(BlaBlaTripCapability0407.SET_TRIP_SEATS)?.writable == true)
        assertEquals(
            BlaBlaCapabilityEvidence0407.WRITE_VERIFIED,
            after.state(BlaBlaTripCapability0407.SET_TRIP_SEATS)?.evidence,
        )
    }

    @Test
    fun commandIdentityKeepsTripAndAccountAndIsIdempotencyScoped() {
        val first = BlaBlaCommand0407.forTarget(
            target = target,
            operation = BlaBlaTripCapability0407.REVERIFY_TRIP,
            origin = "CARD",
        )
        val second = BlaBlaCommand0407.forTarget(
            target = target,
            operation = BlaBlaTripCapability0407.REVERIFY_TRIP,
            origin = "CARD",
        )

        assertEquals(target.tenantId, first.tenantId)
        assertEquals(target.accountId, first.accountId)
        assertEquals(target.profileUuid, first.profileUuid)
        assertEquals(target.tripId, first.tripId)
        assertNotEquals(first.commandId, second.commandId)
        assertNotEquals(first.idempotencyKey, second.idempotencyKey)
    }

    @Test
    fun networkFirstWaitIsBoundedBeforeDomFallback() {
        assertTrue(shouldAwaitNetworkTripSource0407(sourcePresent = false, readAttempts = 0, maxReadAttempts = 2))
        assertTrue(shouldAwaitNetworkTripSource0407(sourcePresent = false, readAttempts = 1, maxReadAttempts = 2))
        assertFalse(shouldAwaitNetworkTripSource0407(sourcePresent = false, readAttempts = 2, maxReadAttempts = 2))
        assertFalse(shouldAwaitNetworkTripSource0407(sourcePresent = true, readAttempts = 0, maxReadAttempts = 2))
    }
    @Test
    fun targetedReasonUsesCollectorReconcileWithoutFullCollectorSemantics() {
        assertEquals(
            AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE,
            agendaBackgroundSyncMode0392("trip_reverify"),
        )
        assertEquals("TRIP_REVERIFY", agendaBackgroundSyncTrigger0397("trip_reverify"))
        assertFalse(agendaBackgroundSyncRefreshesCoverageCheckpoint0403("trip_reverify"))
    }
}
