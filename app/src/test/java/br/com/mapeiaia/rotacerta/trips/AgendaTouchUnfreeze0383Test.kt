package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaTouchUnfreeze0383Test {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun topLevelAgendaControlsAreOutsidePullToRefreshGestureOwner() {
        val activity = source("TripsActivity.kt")
        assertFalse(
            activity.contains("PullToRefreshBox("),
            "TripsActivity must not wrap header/actions in a full-screen pull-to-refresh recognizer",
        )
        assertTrue(activity.contains("topBar = {"))
        assertTrue(activity.contains("AgendaModuleHeader0396("))
        assertTrue(activity.contains("TimelineRefreshGestureSurface0388("))
        assertTrue(activity.contains("refreshing = false"))
        assertTrue(activity.contains("onRefresh = requestTimelineVisualReload"))
        assertTrue(activity.contains("networkSync=false automaticSyncOnly=true"))
        assertTrue(activity.contains("modifier = Modifier.weight(1f).fillMaxWidth()"))
        assertTrue(activity.contains("listModifier = Modifier.weight(1f)"))
    }

    @Test
    fun timelineListUsesExplicitStateAndNoSecondPullRecognizer() {
        val timeline = source("TripTimelineUi.kt")
        val activity = source("TripsActivity.kt")
        val search = timeline.indexOf("label = { Text(\"Buscar na Timeline\") }")
        val emptyState = timeline.indexOf("val timelineEmptyMessage = when", search)
        val region = timeline.indexOf("Box(", emptyState)
        val list = timeline.indexOf("LazyColumn(", region)

        assertTrue(activity.contains("AgendaModuleHeader0396("), "Module header must remain outside the Timeline gesture owner")
        assertTrue(search >= 0, "Timeline search control missing")
        assertTrue(region > search, "Timeline data region must remain after the search control")
        assertTrue(list > region, "Timeline list must remain inside the weighted data region")
        assertFalse(timeline.contains("PullToRefreshBox("), "There must be only one canonical pull owner")
        assertTrue(timeline.contains("state = listState"))
        assertTrue(timeline.contains("modifier = listModifier.fillMaxWidth()"))
        assertTrue(timeline.contains("modifier = Modifier.fillMaxSize()"))
    }

    @Test
    fun emptyTimelineStillOwnsARefreshableDataRegionWithoutCoveringToolbar() {
        val timeline = source("TripTimelineUi.kt")
        assertTrue(timeline.contains("val timelineEmptyMessage = when"))
        assertTrue(timeline.contains("entries.isEmpty() && publicResponseForTimeline == null"))
        assertTrue(timeline.contains("if (timelineEmptyMessage != null)"))
        assertTrue(timeline.contains("Column(modifier = Modifier.fillMaxSize())"))
        assertFalse(
            timeline.substring(
                timeline.indexOf("val timelineEmptyMessage = when"),
                timeline.indexOf("Box(", timeline.indexOf("val timelineEmptyMessage = when")),
            ).contains("return"),
            "Empty Timeline must not return before installing its refreshable data region",
        )
    }

    @Test
    fun responsiveButtonsStillRecordSemanticActionBeforeClickCallback() {
        val actions = source("ResponsiveTripActions.kt")
        val traced = actions.indexOf("val tracedClick = {")
        val trace = actions.indexOf("AgendaTrace.action(context, action.traceKey, action.label)", traced)
        val click = actions.indexOf("action.onClick()", trace)
        assertTrue(traced >= 0)
        assertTrue(trace > traced)
        assertTrue(click > trace, "Semantic action evidence must be recorded before invoking the button callback")
        assertTrue(actions.contains("modifier = Modifier.fillMaxWidth()"))
        assertTrue(actions.contains("enabled = action.enabled"))
    }

    @Test
    fun agendaHeaderAndContextActionsRemainRealTouchTargets() {
        val activity = source("TripsActivity.kt")
        val header = source("AgendaHeaderNavigation0396.kt")

        listOf(
            "Nova viagem",
            "Adicionar passageiro",
            "Vagas extra",
            "Próximas / arquivadas",
            "Baixar Timeline",
            "Fixar atalho",
        ).forEach { label ->
            assertTrue(activity.contains("AgendaHeaderAction0396(\"$label\""), "Missing Agenda contextual action: $label")
        }
        listOf("Sincronizar agora", "Publicar agenda", "Sincronizar BlaBlaCar", "Limpar Timeline").forEach { label ->
            assertFalse(activity.contains("AgendaHeaderAction0396(\"$label\""), "Obsolete sync/clear action returned: $label")
        }
        assertTrue(header.contains("INTEGRATIONS(\"Integrações\")"), "Persistent integration navigation must remain a real drawer destination")
        assertTrue(header.contains("AUTOMATIC_SYNC(\"BlaBlaCar\")"), "BlaBlaCar collection must remain a real drawer destination")
        assertFalse(activity.contains("AgendaHeaderAction0396(\"Integração online\""), "Persistent navigation must not return to the contextual overflow")
        assertTrue(header.contains("IconButton("), "Header navigation/overflow must be real touch targets")
        assertTrue(header.contains("NavigationDrawerItem("), "Drawer destinations must be real navigation touch targets")
        assertTrue(header.contains("DropdownMenuItem("), "Overflow actions must be real menu touch targets")
        assertTrue(header.contains("enabled = action.enabled"))
        assertTrue(header.contains("action.onClick()"))
        assertFalse(header.contains("horizontalScroll"), "Root navigation must not return to a horizontal gesture strip")
    }
}
