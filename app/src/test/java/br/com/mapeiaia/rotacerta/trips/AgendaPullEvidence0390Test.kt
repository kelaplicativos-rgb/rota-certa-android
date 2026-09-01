package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaPullEvidence0390Test {
    @Test
    fun pullOwnerExposesDownAndEndWithoutChangingAcceptanceGate() {
        val gesture = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaPullRefreshGesture0388.kt").readText()
        assertTrue(gesture.contains("onPointerDown: (Offset, Boolean, Boolean) -> Unit"))
        assertTrue(gesture.contains("onPointerEnd: (Offset, Boolean) -> Unit"))
        assertTrue(gesture.contains("latestOnPointerDown(down.position, canRefreshAtStart, refreshRunningAtStart)"))
        assertTrue(gesture.contains("latestOnPointerEnd(change.position, accepted)"))
        assertTrue(gesture.contains("if (decision.accepted)"))
        assertTrue(gesture.contains("change.consume()"))
        assertTrue(gesture.contains("latestOnRefresh()"))
        assertFalse(gesture.contains("latestOnRefresh()\n                } else"))
    }

    @Test
    fun timelineRecordsEveryResolvedOutcomeAndRealListState() {
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
        assertTrue(activity.contains("AGENDA_PULL_GESTURE_DOWN_0390"))
        assertTrue(activity.contains("AGENDA_PULL_GESTURE_DECISION_0390"))
        assertTrue(activity.contains("AGENDA_PULL_GESTURE_END_0390"))
        assertTrue(activity.contains("outcome=${decision.outcome.name}"))
        assertTrue(activity.contains("firstVisibleItemIndex=${timelineListState.firstVisibleItemIndex}"))
        assertTrue(activity.contains("firstVisibleItemScrollOffset=${timelineListState.firstVisibleItemScrollOffset}"))
        assertTrue(activity.contains("canScrollBackward=${timelineListState.canScrollBackward}"))
        assertTrue(activity.contains(""AGENDA_PULL_GESTURE_RECOGNIZED""))
        assertTrue(activity.contains("onRefresh = requestFullTimelineRefresh"))
    }

    @Test
    fun tripDetailWatchdogFrom0389IsRetained() {
        val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        assertTrue(dynamic.contains("BLABLA_TRIP_DETAIL_CAPTURE_TIMEOUT_MS_0389 = 10_000L"))
        assertTrue(dynamic.contains("timeoutMs = blaBlaDynamicCollectionTimeoutMs0389(request)"))
    }
}
