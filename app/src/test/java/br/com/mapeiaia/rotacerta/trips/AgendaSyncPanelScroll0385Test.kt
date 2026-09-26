package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaSyncPanelScroll0385Test {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun blaBlaSyncPanelOpensInsideReachableScrollableModal() {
        val timeline = source("TripTimelineUi.kt")
        val start = timeline.indexOf("if (showSync) {")
        val end = timeline.indexOf("OutlinedTextField(", start)
        assertTrue(start >= 0 && end > start)
        val block = timeline.substring(start, end)

        assertTrue(block.contains("AlertDialog("))
        assertTrue(block.contains("title = { Text(\"Sincronizar BlaBlaCar\") }"))
        assertTrue(block.contains(".heightIn(max = 650.dp)"))
        assertTrue(block.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(block.contains("BlaBlaCollectorPanel("))
        assertTrue(block.contains("source=modal_dismiss"))
        assertTrue(block.contains("source=modal_button"))
        assertFalse(block.contains("if (showSync) {\n        BlaBlaCollectorPanel("))
    }

    @Test
    fun syncModalRemainsIndependentFromTheSingleTimelinePullOwner() {
        val activity = source("TripsActivity.kt")
        val timeline = source("TripTimelineUi.kt")
        val gesture = source("AgendaPullRefreshGesture0388.kt")

        assertFalse(activity.contains("PullToRefreshBox("))
        assertFalse(timeline.contains("PullToRefreshBox("))
        assertTrue(activity.contains("TimelineRefreshGestureSurface0388("))
        assertTrue(timeline.contains("modifier = listModifier.fillMaxWidth()"))
        assertTrue(timeline.contains("state = listState"))
        assertTrue(gesture.contains("if (decision.accepted)"))
        assertTrue(gesture.contains("change.consume()"))
    }
}
