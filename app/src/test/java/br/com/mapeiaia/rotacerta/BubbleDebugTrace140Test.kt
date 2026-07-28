package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class BubbleDebugTrace140Test {
    @Test
    fun captures_complete_bubble_pipeline() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        listOf(
            "BUBBLE_EVENT_RESOLVED",
            "BUBBLE_PACKAGE_BLOCKED",
            "BUBBLE_TEXT_COLLECTED",
            "BUBBLE_SCREEN_CHANGED",
            "BUBBLE_ANALYSIS_STARTED",
            "BUBBLE_PROCESS_ENTER",
            "BUBBLE_ADDRESS_EVALUATION",
            "BUBBLE_CARD_STATE",
            "BUBBLE_DUPLICATE_SKIPPED",
            "BUBBLE_CACHE_HIT",
            "BUBBLE_ROUTE_REQUESTED",
            "BUBBLE_ROUTE_CALL_START",
            "BUBBLE_ROUTE_CALL_END",
            "BUBBLE_DECISION_READY",
            "BUBBLE_DECISION_PAINTED",
            "BUBBLE_CLEAR_REQUEST",
            "BUBBLE_CLEAR_PAINTED",
        ).forEach { assertContains(source, it) }
    }
}
