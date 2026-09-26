package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaAdminObservability0417Test {
    @Test
    fun updateNowUsesCollectorReconcileAndFullButtonUsesFullReconcile() {
        assertEquals(
            AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE,
            agendaBackgroundSyncMode0392("admin_update_now:operation-123"),
        )
        assertEquals(
            AgendaBackgroundSyncMode0392.FULL_RECONCILE,
            agendaBackgroundSyncMode0392("admin_full_reconcile:operation-456"),
        )
    }

    @Test
    fun adminIntervalRespectsWorkManagerSafeBounds() {
        assertEquals(15L, agendaBackgroundSyncIntervalMinutes0392(1L))
        assertEquals(15L, agendaBackgroundSyncIntervalMinutes0392(15L))
        assertEquals(24L * 60L, agendaBackgroundSyncIntervalMinutes0392(99_999L))
    }

    @Test
    fun regularBookingPushKeepsExistingEventMode() {
        assertEquals(
            AgendaBackgroundSyncMode0392.BOOKING_EVENT,
            agendaBackgroundSyncMode0392("booking_push:reservation_changed"),
        )
    }
}
