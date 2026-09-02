package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaTimelineSurface0415Test {
    private val header = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaHeaderNavigation0396.kt").readText()
    private val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
    private val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
    private val automaticSync = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaAutomaticSyncUi0397.kt").readText()
    private val assistant = File("src/main/java/br/com/mapeiaia/rotacerta/trips/RotaCertaAssistantUi0410.kt").readText()

    @Test
    fun timelineCompositionContainsOnlyTimelineControlsAndTripProjection() {
        assertTrue(timeline.contains("label = { Text(\"Buscar na Timeline\") }"))
        assertTrue(timeline.contains("LazyColumn("))
        assertTrue(timeline.contains("TimelineEntryCard("))
        assertFalse(timeline.contains("RotaCertaAssistantPanel0410("))
        assertFalse(timeline.contains("AgendaAutomaticSyncTimelineStatus0398("))
        assertFalse(timeline.contains("Sincronização externa pendente ⚠️"))
        assertFalse(timeline.contains("Sincronização automática desligada"))
        assertFalse(timeline.contains("Última execução:"))
    }

    @Test
    fun assistantIsMovedNotDuplicatedAndKeepsTheCanonicalExecutor() {
        assertTrue(header.contains("ASSISTANT(\"Assistente Rota Certa\")"))
        assertTrue(activity.contains("TripScreen.ASSISTANT -> RotaCertaAssistantPanel0410("))
        assertTrue(assistant.contains("internal fun RotaCertaAssistantPanel0410("))
        assertTrue(assistant.contains("RotaCertaCommandRegistry0410"))
        assertTrue(assistant.contains("interpretAssistant0410("))
        assertFalse(timeline.contains("RotaCertaAssistantPanel0410("))
    }

    @Test
    fun synchronizationStatusLivesOnlyInItsExistingCentralSurface() {
        assertTrue(activity.contains("TripScreen.AUTO_SYNC -> AgendaAutomaticSyncScreen0397()"))
        assertTrue(automaticSync.contains("Esta é a única central de sincronização"))
        assertTrue(automaticSync.contains("AgendaBackgroundSyncConfig0392.status(context)"))
        assertFalse(timeline.contains("AgendaAutomaticSyncTimelineStatus0398("))
        assertFalse(timeline.contains("AgendaBackgroundSyncConfig0392.status(context)"))
    }

    @Test
    fun notificationsUseHeaderBellAndDedicatedCenterInsteadOfTimelineCard() {
        assertTrue(header.contains("Icons.Filled.Notifications"))
        assertTrue(header.contains("notificationUnreadCount"))
        assertTrue(header.contains("notificationUnreadCount"))
        assertTrue(activity.contains("TripScreen.NOTIFICATIONS -> {"))
        assertTrue(activity.contains("DriverNotificationProjection0416.state.collectAsState()"))
        val messaging = File("src/main/java/br/com/mapeiaia/rotacerta/trips/RotaCertaBookingMessagingService.kt").readText()
        assertTrue(messaging.contains("TripRemoteApi(online).listDriverNotifications()"))
        assertTrue(activity.contains("markAllDriverNotificationsRead()"))
        assertTrue(activity.contains("markDriverNotificationRead(item.id)"))
        assertFalse(activity.contains("notificationsExpanded"))
        val timelineBranch = activity
            .substringAfter("TripScreen.TIMELINE -> TimelineRefreshGestureSurface0388(")
            .substringBefore("TripScreen.ASSISTANT -> RotaCertaAssistantPanel0410(")
        assertFalse(timelineBranch.contains("driverNotifications.take("))
        assertFalse(timelineBranch.contains("Text(\"Notificações\""))
    }

    @Test
    fun globalMessageCardsCannotPrependTheTimeline() {
        assertTrue(activity.contains("screen != TripScreen.TIMELINE"))
        assertTrue(activity.contains("screen != TripScreen.ASSISTANT"))
        assertTrue(activity.contains("screen != TripScreen.NOTIFICATIONS"))
    }

    @Test
    fun pendingFilterEngineRemainsInternalButIsNotMisclassifiedAsHeaderAction() {
        assertTrue(header.contains("TOGGLE_SYNC_PENDING"))
        assertFalse(activity.contains("AgendaHeaderAction0396(\"Filtrar pendências das viagens\")"))
        assertTrue(timeline.contains("AgendaTimelineCommand0396.TOGGLE_SYNC_PENDING"))
        assertTrue(timeline.contains("externalSyncStateIsPending"))
        assertFalse(timeline.contains("Sincronização externa pendente ⚠️"))
    }
}
