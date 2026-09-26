package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaTimelineAccess0393Test {
    @Test
    fun agendaActionsUseOneHorizontalRowInsteadOfTenFixedHeightButtons() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/ResponsiveTripActions.kt").readText()
        val agendaBranch = source
            .substringAfter("if (agendaToolbar) {\n                // The Agenda has many actions.")
            .substringBefore("            } else {\n                val narrow")

        assertTrue(agendaBranch.contains(".horizontalScroll(rememberScrollState())"))
        assertTrue(agendaBranch.contains("Modifier.widthIn(min = 156.dp)"))
        assertTrue(source.contains("listOf(\n            ResponsiveTripAction(\n                label = if (showPublicSearch)"))
        assertFalse(agendaBranch.contains("Column(verticalArrangement = Arrangement.spacedBy(7.dp)"))
        assertFalse(agendaBranch.contains("modifier = Modifier.fillMaxWidth()) { Text(action.label"))
    }

    @Test
    fun publicSearchAndExportLiveInScrollableModalOutsideTimelineHeightBudget() {
        val actions = File("src/main/java/br/com/mapeiaia/rotacerta/trips/ResponsiveTripActions.kt").readText()
        val publicSearch = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaPublicSearchUi.kt").readText()
        val modal = actions
            .substringAfter("if (agendaToolbar && showPublicSearch) {")
            .substringBeforeLast("\n}")

        assertTrue(modal.contains("AlertDialog("))
        assertTrue(modal.contains(".heightIn(max = 650.dp)"))
        assertTrue(modal.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(modal.contains("BlaBlaPublicSearchPanel("))
        assertTrue(modal.contains("TextButton(onClick = { showPublicSearch = false })"))
        assertTrue(publicSearch.contains("BlaBlaAuditableCollectionActions("))
    }

    @Test
    fun timelineStillOwnsWeightedLazyListAndPullRefreshSurface() {
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()

        assertTrue(activity.contains("TimelineRefreshGestureSurface0388("))
        assertTrue(activity.contains("modifier = Modifier.weight(1f).fillMaxWidth()"))
        assertTrue(activity.contains("listModifier = Modifier.weight(1f)"))
        assertTrue(timeline.contains("Box(\n        modifier = listModifier.fillMaxWidth()"))
        assertTrue(timeline.contains("LazyColumn("))
        assertTrue(timeline.contains("modifier = Modifier.fillMaxSize()"))
    }

    @Test
    fun fixDoesNotReintroduceAutomaticBlablacarWriteOrGlobalTimelineScroll() {
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
        val actions = File("src/main/java/br/com/mapeiaia/rotacerta/trips/ResponsiveTripActions.kt").readText()

        assertFalse(activity.contains("BlaBlaReliableSeatSyncActivity"))
        assertFalse(activity.contains("BlaBlaManualSeatSyncActivity"))
        assertTrue(activity.contains("if (screen == TripScreen.TIMELINE)"))
        val timelineParent = activity
            .substringAfter("modifier = if (screen == TripScreen.TIMELINE) {")
            .substringBefore("} else {")
        assertFalse(timelineParent.contains("verticalScroll"))
        assertTrue(actions.contains("if (agendaToolbar)"))
    }
}
