package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaHeaderNavigation0396Test {
    private val header = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaHeaderNavigation0396.kt").readText()
    private val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
    private val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
    private val passengers = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerAdminUi.kt").readText()
    private val publicSearch = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaPublicSearchUi.kt").readText()

    @Test
    fun rootDrawerContainsOnlyRealAgendaDestinationsAndHighlightsSelection() {
        assertTrue(header.contains("ALL_TRIPS(\"Todas as viagens\")"))
        val drawer = header.substringAfter("ModalDrawerSheet").substringBefore("@Composable\ninternal fun AgendaModuleHeader0396")
        assertTrue(drawer.contains("AgendaRootSection0396.ALL_TRIPS"))
        assertFalse(drawer.contains("AgendaRootSection0396.ASSISTANT"))
        assertTrue(header.contains("AUTOMATIC_SYNC(\"BlaBlaCar\")"))
        assertTrue(header.contains("SCRIPTS(\"Scripts\")"))
        assertTrue(drawer.contains("AgendaRootSection0396.SCRIPTS"))
        assertTrue(drawer.contains("Text(\"Abrir Agenda Pública\""))
        assertTrue(drawer.contains("if (publicAgendaEnabled)"))
        assertTrue(drawer.contains("onOpenPublicAgenda()"))
        assertTrue(header.contains("PUBLIC_SEARCH(\"Consulta pública\")"))
        assertTrue(header.contains("PASSENGERS(\"Passageiros\")"))
        assertTrue(header.contains("NavigationDrawerItem("))
        assertTrue(header.contains("selected = section == currentSection"))
        assertFalse(header.contains("horizontalScroll"))
        assertFalse(header.contains("Nova viagem"))
        assertFalse(header.contains("Sincronizar BlaBlaCar"))
    }

    @Test
    fun timelineRootIsVisibleAndCollectorRemainsASeparateDestination() {
        val drawer = header.substringAfter("ModalDrawerSheet").substringBefore("@Composable\ninternal fun AgendaModuleHeader0396")
        assertTrue(drawer.contains("AgendaRootSection0396.ALL_TRIPS"))
        assertTrue(drawer.contains("AgendaRootSection0396.AUTOMATIC_SYNC"))
        assertTrue(activity.contains("parentRootScreen0396 = TripScreen.TIMELINE"))
        assertTrue(activity.contains("onBack = { screen = TripScreen.TIMELINE }"))
        assertFalse(activity.contains("Abra a Área Administrativa para operar a viagem desta notificação."))
    }

    @Test
    fun headerSeparatesRootNavigationContextAndOverflow() {
        assertTrue(header.contains("text = if (root) \"☰\" else \"←\""))
        assertTrue(header.contains("Text(\"⋮\""))
        assertTrue(header.contains("Text(\n                        \"Rota Certa\""))
        assertTrue(header.contains("sectionLabel"))
        assertTrue(header.contains("contentDescription = navigationDescription"))
        assertTrue(header.contains("\"Notificações, \$unread não lidas\""))
        assertTrue(header.contains("Icons.Filled.Notifications"))
        assertTrue(header.contains("contentDescription = \"Mais ações desta tela\""))
        assertTrue(header.contains("maxLines = 1"))
        assertTrue(header.contains("TextOverflow.Ellipsis"))
    }

    @Test
    fun agendaNoLongerUsesHorizontalStripOrRedundantTextBack() {
        assertTrue(activity.contains("AgendaModuleDrawer0396("))
        assertTrue(activity.contains("AgendaModuleHeader0396("))
        assertTrue(activity.contains("TripScreen.PUBLIC_SEARCH -> AgendaPublicSearchRoot0396("))
        assertTrue(activity.contains("showHeader = false"))
        assertFalse(activity.contains("Rota Certa • viagens, vagas por trecho e calendário"))
        assertFalse(activity.contains("Voltar à Timeline"))

        assertFalse(timeline.contains("ResponsiveTripAction(\"👥 Passageiros\""))
        assertFalse(timeline.contains("source=timeline_header"))
        assertFalse(timeline.contains("publicSearchClearToken"))
        assertFalse(timeline.contains("Text(if (showArchived) \"Arquivadas\" else \"Todas as viagens\""))
    }

    @Test
    fun overflowContainsContextActionsButNotDrawerDestinations() {
        val overflow = activity
            .substringAfter("val headerActions0396 = when (screen) {")
            .substringBefore("val currentRootScreen0396")

        assertTrue(overflow.contains("AgendaHeaderAction0396(\"Nova viagem\")"))
        assertTrue(overflow.contains("AgendaHeaderAction0396(\"Adicionar passageiro\")"))
        assertTrue(overflow.contains("AgendaHeaderAction0396(\"Vagas extra\")"))
        assertTrue(overflow.contains("AgendaHeaderAction0396(\"Próximas / arquivadas\")"))
        assertTrue(overflow.contains("AgendaHeaderAction0396(\"Baixar Timeline\")"))
        assertTrue(overflow.contains("TripScreen.SCRIPTS -> listOf("))
        assertTrue(overflow.contains("AgendaHeaderAction0396(\"Novo script\")"))
        assertTrue(overflow.contains("AgendaHeaderAction0396(\"Restaurar seleção padrão\")"))
        assertFalse(overflow.contains("Contas e navegadores"))
        assertFalse(overflow.contains("AgendaHeaderAction0396(\"Sincronização automática\")"))
        assertFalse(overflow.contains("AgendaHeaderAction0396(\"Sincronizar agora\")"))
        assertFalse(overflow.contains("AgendaHeaderAction0396(\"Publicar agenda\")"))
        assertFalse(overflow.contains("AgendaHeaderAction0396(\"Sincronizar BlaBlaCar\")"))
        assertFalse(overflow.contains("AgendaHeaderAction0396(\"Limpar Timeline\")"))
        assertFalse(overflow.contains("AgendaHeaderAction0396(\"Todas as viagens\")"))
        assertFalse(overflow.contains("AgendaHeaderAction0396(\"Consulta pública\")"))
        assertFalse(overflow.contains("AgendaHeaderAction0396(\"Passageiros\")"))
    }

    @Test
    fun existingSectionsAreReusedWithoutDuplicateRootTitles() {
        assertTrue(activity.contains("PassengerAdminScreen("))
        assertTrue(activity.contains("BlaBlaPublicSearchPanel("))
        assertTrue(passengers.contains("showHeader: Boolean = true"))
        assertTrue(passengers.contains("if (showHeader)"))
        assertFalse(passengers.contains("TextButton(onClick = onBack) { Text(\"Voltar\") }"))
        assertTrue(publicSearch.contains("showTitle: Boolean = true"))
        assertTrue(publicSearch.contains("if (showTitle)"))
        assertTrue(activity.contains("showTitle = false"))
        assertTrue(activity.contains("TripScreen.ASSISTANT -> RotaCertaAssistantPanel0410("))
        assertTrue(activity.contains("TripScreen.AUTO_SYNC -> AgendaAutomaticSyncScreen0397("))
        assertTrue(activity.contains("TripScreen.SCRIPTS -> BlaBlaScriptsScreen0486("))
        assertTrue(activity.contains("TripScreen.NOTIFICATIONS -> {"))
    }

    @Test
    fun timelineDataAndPerTripMutationPathsRemainIntact() {
        assertTrue(timeline.contains("label = { Text(\"Buscar na Timeline\") }"))
        assertTrue(timeline.contains("\"Nenhuma viagem sincronizada.\""))
        assertTrue(timeline.contains("LazyColumn("))
        assertTrue(timeline.contains("TimelineEntryCard("))
        assertTrue(timeline.contains("TripMutationCoordinator0387"))
        assertTrue(timeline.contains("canonicalTripId ="))
        assertTrue(timeline.contains("ResponsiveTripActions("))
        assertTrue(activity.contains("listModifier = Modifier.weight(1f)"))
        assertTrue(activity.contains("TimelineRefreshGestureSurface0388("))
        assertTrue(activity.contains("networkSync=false automaticSyncOnly=true"))
    }

    @Test
    fun rootStateSurvivesRecompositionAndSubscreensReturnHierarchically() {
        assertTrue(activity.contains("var screen by rememberSaveable"))
        assertTrue(activity.contains("var parentRootScreen0396 by rememberSaveable"))
        assertTrue(activity.contains("val passengerSubscreenActive0396"))
        assertTrue(activity.contains("root = headerIsRoot0396"))
        assertTrue(activity.contains("\"Histórico do passageiro\""))
        assertTrue(activity.contains("passengerExternalBackToken0396 += 1"))
        assertTrue(activity.contains("onHierarchyChanged = { passengerSubscreenOpen0396 = it }"))
        assertTrue(activity.contains("screen = parentRootScreen0396"))
        assertTrue(activity.contains("uiCommand0396 = timelineUiCommand0396"))
        assertTrue(timeline.contains("LaunchedEffect(uiCommandToken0396, uiCommand0396)"))
        assertTrue(passengers.contains("externalBackToken: Int = 0"))
        assertTrue(passengers.contains("onHierarchyChanged(historyProfileId != null)"))
    }
}
