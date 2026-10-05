package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanonicalPassengerReadYourWrite0730Test {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun canonicalAddPersistsRemoteAckBeforeRealtimeRefresh() {
        val ui = source("br/com/mapeiaia/rotacerta/trips/TripQuickPassengerUi.kt")
        val persist = ui.indexOf("store.persistRemoteConfirmedBooking0730(confirmedCache0730)")
        val notify = ui.indexOf("BookingRealtimeEvents0356.notifyChanged()", persist)
        assertTrue(persist >= 0)
        assertTrue(notify > persist)
        assertTrue(ui.contains("readYourWrite0730=true"))
    }

    @Test
    fun canonicalCancelAlsoPersistsConfirmedCancellationBeforeRefresh() {
        val ui = source("br/com/mapeiaia/rotacerta/trips/TripQuickPassengerUi.kt")
        assertTrue(ui.contains("val cancelledCache0730 = booking.copy("))
        assertTrue(ui.contains("store.persistRemoteConfirmedBooking0730(cancelledCache0730)"))
    }

    @Test
    fun readYourWriteCacheDoesNotMintTripRevisionOrPublication() {
        val store = source("br/com/mapeiaia/rotacerta/trips/TripStore.kt")
        val start = store.indexOf("internal fun persistRemoteConfirmedBooking0730(")
        val end = store.indexOf("internal fun saveBookingsBatch(", start)
        assertTrue(start >= 0 && end > start)
        val body = store.substring(start, end)
        assertTrue(body.contains("putString(bookingsKey"))
        assertTrue(body.contains("tripRevisionPreserved=true"))
        assertFalse(body.contains("refreshCanonicalTripStateBatch0395("))
        assertFalse(body.contains("saveTrip("))
        assertFalse(body.contains("recordPublicationCommitted0411("))
    }

    @Test
    fun trackingStatusUsesOneSnapshotInsteadOfPerRowPreferenceDecode() {
        val ui = source("br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt")
        val tracking = source("br/com/mapeiaia/rotacerta/LiveTrackingShare0668.kt")
        assertTrue(ui.contains("activePassengerTrackingKeys0730"))
        assertTrue(ui.contains("activePassengerKeysSnapshot0730()"))
        assertTrue(ui.contains("passengerTimelineRowKey0394(row0676) in activePassengerTrackingKeys0730"))
        assertTrue(tracking.contains("fun activePassengerKeysSnapshot0730("))
    }
}
