package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteSupportNotification0744ContractTest {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun remoteCollectorPushesShowVisibleSamsungNotification() {
        val messaging = source("RotaCertaBookingMessagingService.kt")
        assertTrue(messaging.contains("RemoteSupportNotification0744.show("))
        assertTrue(messaging.contains("isStandaloneCoversRemoteEvent0736(event)"))
        assertTrue(messaging.contains("isBlaBlaRemoteTripQueryEvent0737(event)"))
    }

    @Test
    fun notificationOffersOnlyExplicitConsentActions() {
        val notification = source("RemoteSupportNotification0744.kt")
        assertTrue(notification.contains("\"ACEITAR\""))
        assertTrue(notification.contains("\"RECUSAR\""))
        assertTrue(notification.contains("RemoteSupportConsentReceiver0746.ACTION_ACCEPT"))
        assertTrue(notification.contains("RemoteSupportConsentReceiver0746.ACTION_DECLINE"))
        assertTrue(notification.contains("VISIBILITY_PRIVATE"))
        assertFalse(notification.contains("TripActions.ACTION_OPEN_NOTIFICATIONS"))
        assertFalse(notification.contains("setContentIntent"))
        assertFalse(notification.contains("putExtra(TripActions.EXTRA_BOOKING_ID"))
        assertFalse(notification.contains("putExtra(TripActions.EXTRA_REMOTE_TRIP_ID"))
    }

    @Test
    fun remoteRequestKeepsOrangeAttentionUntilConnectionRevalidation() {
        val attention = source("RemoteSupportAttention0743.kt")
        val notification = source("RemoteSupportNotification0744.kt")
        assertTrue(attention.contains("status = \"REQUESTED\""))
        assertTrue(attention.contains("REMOTE_REQUEST_RECEIVED"))
        assertTrue(notification.contains("RemoteSupportAttention0743.markRequestPending"))
    }

    @Test
    fun notificationPermissionFailureIsDiagnosableAndSecretsStayOutOfLogs() {
        val notification = source("RemoteSupportNotification0744.kt")
        assertTrue(notification.contains("REMOTE_SUPPORT_NOTIFICATION_PERMISSION_MISSING_0744"))
        assertTrue(notification.contains("UnifiedDebugEventStore.recordAlways"))
        assertFalse(notification.contains("accessToken"))
        assertFalse(notification.contains("refreshUrl"))
    }
}
