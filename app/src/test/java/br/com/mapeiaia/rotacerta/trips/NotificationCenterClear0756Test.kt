package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationCenterClear0756Test {
    private fun source(name: String) =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun overflowOwnsClearNotificationsAction() {
        val activity = source("TripsActivity.kt")
        assertTrue(activity.contains("TripScreen.NOTIFICATIONS -> listOf("))
        assertTrue(activity.contains("label = \"Limpar notificações\""))
        assertTrue(activity.contains("markAllDriverNotificationsRead()"))
        assertFalse(activity.contains("Text(\"Marcar todas como lidas\")"))
    }

    @Test
    fun bellOrangeIndicatorMeansUnreadNotificationOnly() {
        val header = source("AgendaHeaderNavigation0396.kt")
        assertTrue(header.contains("if (unread > 0)"))
        assertTrue(header.contains("Color(0xFFFF9800)"))
        assertFalse(header.contains("if (unread > 0 || remoteAttentionNeeded0743)"))
        assertFalse(header.contains("remote-support-orange-pulse-0743"))
    }

    @Test
    fun remoteSupportAttentionRemainsVisibleInsideNotificationCenter() {
        val activity = source("TripsActivity.kt")
        assertTrue(activity.contains("remoteSupportAttention0743.ready"))
        assertTrue(activity.contains("Verificar conexão remota"))
    }
}
