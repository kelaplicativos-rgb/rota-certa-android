package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaNavigationNotificationsProfiles0416Test {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun drawerHasOnlyDestinationsAndNoCentralHeading() {
        val header = source("AgendaHeaderNavigation0396.kt")
        val visible = header
            .substringAfter("listOf(")
            .substringBefore(").forEach { section ->")
        listOf(
            "AgendaRootSection0396.ALL_TRIPS",
            "AgendaRootSection0396.AUTOMATIC_SYNC",
            "AgendaRootSection0396.PUBLIC_SEARCH",
            "AgendaRootSection0396.PASSENGERS",
            "AgendaRootSection0396.INTEGRATIONS",
            "AgendaRootSection0396.APP_SETTINGS",
        ).forEach { assertTrue(visible.contains(it), "Missing destination: $it") }
        assertFalse(visible.contains("AgendaRootSection0396.ASSISTANT"))
        assertFalse(header.contains("Central do Rota Certa"))
        assertFalse(header.contains("Text(\"Navegação\""))
    }

    @Test
    fun allTripsOverflowContainsOnlyContextualActions() {
        val activity = source("TripsActivity.kt")
        val actions = activity
            .substringAfter("TripScreen.TIMELINE -> listOf(")
            .substringBefore("else -> emptyList()")
        listOf(
            "Nova viagem",
            "Adicionar passageiro",
            "Vagas extra",
            "Próximas / arquivadas",
            "Baixar Timeline",
            "Fixar atalho",
        ).forEach { assertTrue(actions.contains("AgendaHeaderAction0396(\"$it\")"), "Missing action: $it") }
        listOf("Sincronização automática", "Contas e navegadores", "Notificações", "Veículo", "⬇️ Baixar Timeline")
            .forEach { assertFalse(actions.contains("AgendaHeaderAction0396(\"$it\")"), "Misclassified action: $it") }
    }

    @Test
    fun notificationBellUsesAuthoritativeUnreadProjectionAndReactiveInvalidation() {
        val header = source("AgendaHeaderNavigation0396.kt")
        val activity = source("TripsActivity.kt")
        val messaging = source("RotaCertaBookingMessagingService.kt")

        assertTrue(header.contains("Icons.Filled.Notifications"))
        assertTrue(header.contains("if (unread > 0)"))
        assertTrue(header.contains("Badge {"))
        assertTrue(header.contains("\"Notificações, \$unread não lidas\""))
        assertTrue(header.contains("else {\n                \"Notificações\""))
        assertFalse(header.contains("🔔"))

        assertTrue(activity.contains("TripRemoteApi(online).listDriverNotifications()"))
        assertTrue(activity.contains("notificationRefreshMutex0416.withLock"))
        assertTrue(activity.contains("BookingRealtimeEvents0356.changes.collect"))
        assertFalse(activity.contains("delay(15_000L)"))
        assertTrue(messaging.contains("BookingRealtimeEvents0356.notifyChanged()"))

        val openCenter = activity.substringAfter("val openNotifications0396 = {")
            .substringBefore("val headerActions0396")
        assertFalse(openCenter.contains("markAllDriverNotificationsRead()"))
    }

    @Test
    fun accountsBrowsersVehicleGpsAndExtraSeatsAreClassifiedByResponsibility() {
        val automatic = source("AgendaAutomaticSyncUi0397.kt")
        val settings = source("PublicAgendaSettingsUi.kt")
        val activity = source("TripsActivity.kt")
        val timeline = source("TripTimelineUi.kt")
        val background = source("AgendaBackgroundSync0392.kt")

        assertTrue(automatic.contains("BlaBlaAccountsAndBrowsersScreen0399()"))
        val integration = settings.substringAfter("internal fun OnlineSettingsEditor(")
            .substringBefore("@Composable\ninternal fun AgendaAppSettingsScreen0416")
        assertFalse(integration.contains("🚗 Veículo"))
        assertFalse(integration.contains("TripReferenceOriginSettingsCard0416("))
        assertTrue(settings.contains("Text(\"Dados do veículo\""))
        assertTrue(settings.contains("TripReferenceOriginSettingsCard0416("))

        val extra = activity.substringAfter("private fun TripExtraSeatsScreen0416(")
            .substringBefore("@Composable\nprivate fun AgendaPublicSearchRoot0396")
        assertTrue(extra.contains("trip.rotaCertaSeatAllocation"))
        assertTrue(extra.contains("store.saveTrip("))
        assertTrue(extra.contains("TripMutationCoordinator0387"))
        assertTrue(extra.contains("AgendaBackgroundSync0392.enqueueImmediate"))
        assertTrue(timeline.contains("trip.rotaCertaSeatAllocation != null"))
        assertTrue(background.contains("globalFanOut=false"))
        assertFalse(background.contains("TripRemoteApi(online).reconcileAgendaSeatAllocation("))
    }

    @Test
    fun publicSearchRestoresProfilesAsRealFilterWithoutLosingRawAudit() {
        val ui = source("BlaBlaPublicSearchUi.kt")
        val search = source("BlaBlaPublicSearch.kt")
        val activity = source("BlaBlaPublicSearchActivity.kt")
        val audit = source("BlaBlaAuditableCollection.kt")

        assertTrue(ui.contains("label = { Text(\"Perfis\") }"))
        assertTrue(ui.contains("Separe vários perfis por vírgulas."))
        assertTrue(ui.contains("BlaBlaPublicSearchPlanner.parseTargetNames(names)"))
        assertTrue(search.contains("raw.split(',')"))
        assertTrue(search.contains("filterRequestedCards("))
        assertTrue(search.contains("knownProfiles: List<KnownProfile>"))
        assertTrue(search.contains("profileUuid"))
        assertTrue(activity.contains("cards = requestedCards"))
        assertTrue(activity.contains("rawCards = rawCards"))
        assertTrue(audit.contains("response.rawCards.ifEmpty { response.cards }"))
    }
}
