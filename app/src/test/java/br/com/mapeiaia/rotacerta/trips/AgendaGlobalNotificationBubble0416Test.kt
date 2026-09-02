package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaGlobalNotificationBubble0416Test {
    private fun trips(name: String) = File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()
    private fun root(name: String) = File("src/main/java/br/com/mapeiaia/rotacerta/$name").readText()

    @Test
    fun centralBellAndGlobalBubbleShareOneAuthoritativeProjection() {
        val messaging = trips("RotaCertaBookingMessagingService.kt")
        val activity = trips("TripsActivity.kt")
        val farol = root("LiveRideAccessibilityService.kt")

        assertTrue(messaging.contains("internal object DriverNotificationProjection0416"))
        assertTrue(messaging.contains("TripRemoteApi(online).listDriverNotifications()"))
        assertTrue(messaging.contains("val tenantId = RotaCertaTenantRegistry(appContext).activeScope().tenantId"))
        assertTrue(messaging.contains("unreadCount = response.unreadCount.coerceAtLeast(0)"))
        assertTrue(messaging.contains("private val refreshMutex = Mutex()"))

        assertTrue(activity.contains("DriverNotificationProjection0416.state.collectAsState()"))
        assertFalse(activity.contains("var driverUnreadCount by remember"))
        assertFalse(activity.contains("var driverNotifications by remember"))
        assertTrue(farol.contains("DriverNotificationProjection0416.state.collect"))
        assertTrue(farol.contains("projection.tenantId == activeTenant"))
    }

    @Test
    fun pushInvalidatesProjectionImmediatelyWithoutPolling() {
        val messaging = trips("RotaCertaBookingMessagingService.kt")
        val activity = trips("TripsActivity.kt")
        val farol = root("LiveRideAccessibilityService.kt")

        assertTrue(messaging.contains("BookingRealtimeEvents0356.notifyChanged()"))
        assertTrue(messaging.contains("DriverNotificationProjection0416.refresh(this@RotaCertaBookingMessagingService)"))
        assertTrue(activity.contains("BookingRealtimeEvents0356.changes.collect"))
        assertTrue(farol.contains("BookingRealtimeEvents0356.changes.collect"))
        assertFalse(activity.contains("delay(15_000L)"))
    }

    @Test
    fun unreadAttentionPreservesFarolFillAndUsesAccessibleOrangeRing() {
        val farol = root("LiveRideAccessibilityService.kt")

        assertTrue(farol.contains("applyAgendaNotificationDecoration0416"))
        assertTrue(farol.contains("setColor(color.argb(currentSettings))"))
        assertTrue(farol.contains("Color.argb(alpha, 255, 152, 0)"))
        assertTrue(farol.contains("setStroke(dp(if (unread > 0) 4 else 3), strokeColor)"))
        assertTrue(farol.contains("\"Rota Certa, Agenda com $unread notificações não lidas\""))
        assertTrue(farol.contains("if (agendaUnreadCount0416 != unread)"))
    }

    @Test
    fun openingNotificationCenterDoesNotMarkEverythingRead() {
        val activity = trips("TripsActivity.kt")
        val openCenter = activity.substringAfter("val openNotifications0396 = {")
            .substringBefore("val headerActions0396")

        assertFalse(openCenter.contains("markAllDriverNotificationsRead()"))
        assertTrue(activity.contains("markDriverNotificationRead(item.id)"))
        assertTrue(activity.contains("markAllDriverNotificationsRead()"))
    }
}
