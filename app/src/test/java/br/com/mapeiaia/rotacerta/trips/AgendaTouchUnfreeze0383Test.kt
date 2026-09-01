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
        assertTrue(activity.contains("Scaffold(modifier = Modifier.fillMaxSize())"))
        assertTrue(activity.contains("refreshing = refreshAllRunning"))
        assertTrue(activity.contains("onRefresh = requestFullTimelineRefresh"))
        assertTrue(activity.contains("listModifier = Modifier.weight(1f)"))
    }

    @Test
    fun pullToRefreshLivesOnlyInTimelineDataRegionAfterToolbarControls() {
        val timeline = source("TripTimelineUi.kt")
        val actions = timeline.indexOf("ResponsiveTripActions(")
        val search = timeline.indexOf("label = { Text(\"Buscar na Timeline\") }", actions)
        val refresh = timeline.indexOf("PullToRefreshBox(", search)
        val list = timeline.indexOf("LazyColumn(", refresh)

        assertTrue(actions >= 0, "Timeline toolbar actions missing")
        assertTrue(search > actions, "Search control must remain after toolbar actions")
        assertTrue(refresh > search, "Pull-to-refresh must start only below toolbar/search controls")
        assertTrue(list > refresh, "Timeline list must be inside pull-to-refresh region")
        assertTrue(timeline.contains("isRefreshing = refreshing"))
        assertTrue(timeline.contains("onRefresh = onRefresh"))
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
                timeline.indexOf("PullToRefreshBox(", timeline.indexOf("val timelineEmptyMessage = when")),
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
    fun allVisibleAgendaToolbarActionsRemainRealButtons() {
        val timeline = source("TripTimelineUi.kt")
        listOf(
            "👥 Passageiros",
            "➕ Adicionar a uma viagem",
            "🛣️ Nova viagem",
            "Publicar agenda",
            "Fixar atalho",
            "Integração online",
            "Sincronizar BlaBlaCar",
            "Limpar Timeline",
            "Ver arquivadas",
        ).forEach { label ->
            assertTrue(timeline.contains(label), "Missing Agenda toolbar action: $label")
        }
    }
}
