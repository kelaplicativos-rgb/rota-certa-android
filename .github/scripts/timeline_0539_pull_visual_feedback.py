from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GESTURE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaPullRefreshGesture0388.kt"
GESTURE_TEST = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/trips/AgendaPullRefreshGesture0388Test.kt"
UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
PASSENGER_UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt"
BUILD = ROOT / "app/build.gradle.kts"


def fail(message: str) -> None:
    raise SystemExit(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8")


def replace_once(content: str, old: str, new: str, label: str) -> str:
    count = content.count(old)
    if count != 1:
        fail(f"{label}: expected exactly one baseline anchor, found {count}")
    return content.replace(old, new, 1)


build = read(BUILD)
gesture = read(GESTURE)
gesture_test = read(GESTURE_TEST)
ui = read(UI)
passenger_ui = read(PASSENGER_UI)

if 'versionCode = 5830' not in build or 'versionName = "0.1.538"' not in build:
    fail("Unexpected version baseline; refusing to materialize 0.1.539")
for marker in [
    'TIMELINE_PULL_REFRESH_ALL_COLLECTOR_0538',
    'AgendaBackgroundSync0392.enqueueTripCollectorRefresh0517(',
    'origin = BlaBlaCommandOrigin0407.CARD',
    'origin = BlaBlaCommandOrigin0407.SYSTEM_RECONCILIATION',
]:
    if marker not in ui:
        fail(f"Required 0.1.538 invariant missing: {marker}")
if "Carregando passageiros" in passenger_ui:
    fail("Visible passenger loading regression exists in baseline")

old_imports = '''import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
'''
new_imports = '''import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
'''
gesture = replace_once(gesture, old_imports, new_imports, "pull indicator imports")

helper_anchor = '''internal fun shouldStartAgendaFullRefresh0388(
    timelineActive: Boolean,
    refreshAllRunning: Boolean,
): Boolean = timelineActive && !refreshAllRunning
'''
helper_new = helper_anchor + '''
internal fun shouldDispatchAgendaRefreshOnRelease0388(
    armed: Boolean,
    refreshRunningAtStart: Boolean,
): Boolean = armed && !refreshRunningAtStart
'''
gesture = replace_once(gesture, helper_anchor, helper_new, "release dispatch helper")

progress_anchor = '''    fun onMove(position: Offset): AgendaPullRefreshDecision0388? {
'''
progress_method = '''    fun pullProgress(position: Offset): Float {
        if (!started || resolved || refreshingAtStart) return 0f
        val dx = position.x - start.x
        val dy = position.y - start.y
        if (dy <= 0f || abs(dy) <= abs(dx) * verticalDominanceRatio) return 0f
        val slop = touchSlopPx.coerceAtLeast(0f)
        val triggerPx = if (eligibleAtStart) {
            slop
        } else {
            slop * awayFromTopTriggerMultiplier.coerceAtLeast(1f)
        }
        if (triggerPx <= 0f) return 1f
        return (dy / triggerPx).coerceIn(0f, 1f)
    }

''' + progress_anchor
gesture = replace_once(gesture, progress_anchor, progress_method, "pull progress helper")

old_surface = '''@Composable
internal fun TimelineRefreshGestureSurface0388(
    modifier: Modifier = Modifier,
    refreshing: Boolean,
    canRefreshAtGestureStart: () -> Boolean,
    onRefresh: () -> Unit,
    onDecision: (AgendaPullRefreshDecision0388) -> Unit = {},
    onPointerDown: (Offset, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onPointerEnd: (Offset, Boolean) -> Unit = { _, _ -> },
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.agendaPullRefreshGestureOwner0388(
            refreshing = refreshing,
            canRefreshAtGestureStart = canRefreshAtGestureStart,
            onRefresh = onRefresh,
            onDecision = onDecision,
            onPointerDown = onPointerDown,
            onPointerEnd = onPointerEnd,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
'''
new_surface = '''@Composable
internal fun TimelineRefreshGestureSurface0388(
    modifier: Modifier = Modifier,
    refreshing: Boolean,
    canRefreshAtGestureStart: () -> Boolean,
    onRefresh: () -> Unit,
    onDecision: (AgendaPullRefreshDecision0388) -> Unit = {},
    onPointerDown: (Offset, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onPointerEnd: (Offset, Boolean) -> Unit = { _, _ -> },
    content: @Composable ColumnScope.() -> Unit,
) {
    var pullProgress0388 by remember { mutableStateOf(0f) }
    var pullArmed0388 by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .agendaPullRefreshGestureOwner0388(
                    refreshing = refreshing,
                    canRefreshAtGestureStart = canRefreshAtGestureStart,
                    onRefresh = onRefresh,
                    onDecision = onDecision,
                    onPointerDown = onPointerDown,
                    onPointerEnd = onPointerEnd,
                    onPullProgress = { progress, armed ->
                        pullProgress0388 = progress
                        pullArmed0388 = armed
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )

        if (refreshing || pullArmed0388 || pullProgress0388 > 0.05f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Atualizando viagens…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text(
                            text = if (pullArmed0388) "↑" else "↓",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = if (pullArmed0388) "Solte para atualizar" else "Puxe para atualizar",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
'''
gesture = replace_once(gesture, old_surface, new_surface, "visible pull indicator surface")

old_signature = '''    onDecision: (AgendaPullRefreshDecision0388) -> Unit = {},
    onPointerDown: (Offset, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onPointerEnd: (Offset, Boolean) -> Unit = { _, _ -> },
): Modifier {
'''
new_signature = '''    onDecision: (AgendaPullRefreshDecision0388) -> Unit = {},
    onPointerDown: (Offset, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onPointerEnd: (Offset, Boolean) -> Unit = { _, _ -> },
    onPullProgress: (Float, Boolean) -> Unit = { _, _ -> },
): Modifier {
'''
gesture = replace_once(gesture, old_signature, new_signature, "pull progress callback signature")

old_state = '''    val latestOnDecision by rememberUpdatedState(onDecision)
    val latestOnPointerDown by rememberUpdatedState(onPointerDown)
    val latestOnPointerEnd by rememberUpdatedState(onPointerEnd)
    val touchSlop = LocalViewConfiguration.current.touchSlop
'''
new_state = '''    val latestOnDecision by rememberUpdatedState(onDecision)
    val latestOnPointerDown by rememberUpdatedState(onPointerDown)
    val latestOnPointerEnd by rememberUpdatedState(onPointerEnd)
    val latestOnPullProgress by rememberUpdatedState(onPullProgress)
    val touchSlop = LocalViewConfiguration.current.touchSlop
'''
gesture = replace_once(gesture, old_state, new_state, "remember pull progress callback")

old_loop = '''            latestOnPointerDown(down.position, canRefreshAtStart, refreshRunningAtStart)

            var accepted = false
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    latestOnPointerEnd(change.position, accepted)
                    gate.onUpOrCancel()
                    break
                }

                if (accepted) {
                    change.consume()
                    continue
                }

                val decision = gate.onMove(change.position) ?: continue
                latestOnDecision(decision)
                if (decision.accepted) {
                    accepted = true
                    change.consume()
                    latestOnRefresh()
                }
            }
'''
new_loop = '''            latestOnPointerDown(down.position, canRefreshAtStart, refreshRunningAtStart)
            latestOnPullProgress(0f, false)

            var accepted = false
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    latestOnPointerEnd(change.position, accepted)
                    if (shouldDispatchAgendaRefreshOnRelease0388(accepted, refreshRunningAtStart)) {
                        latestOnRefresh()
                    }
                    latestOnPullProgress(0f, false)
                    gate.onUpOrCancel()
                    break
                }

                if (accepted) {
                    change.consume()
                    latestOnPullProgress(1f, true)
                    continue
                }

                val progress = gate.pullProgress(change.position)
                latestOnPullProgress(progress, progress >= 1f)
                val decision = gate.onMove(change.position) ?: continue
                latestOnDecision(decision)
                if (decision.accepted) {
                    accepted = true
                    change.consume()
                    latestOnPullProgress(1f, true)
                } else {
                    latestOnPullProgress(0f, false)
                }
            }
'''
gesture = replace_once(gesture, old_loop, new_loop, "release-to-refresh pointer loop")

old_test_dispatch = '''            if (gate.onMove(Offset(0f, 50f))?.accepted == true) {
                requestFullTimelineRefresh()
            }
'''
new_test_dispatch = '''            val armed = gate.onMove(Offset(0f, 50f))?.accepted == true
            if (shouldDispatchAgendaRefreshOnRelease0388(armed, refreshRunningAtStart)) {
                requestFullTimelineRefresh()
            }
'''
gesture_test = replace_once(gesture_test, old_test_dispatch, new_test_dispatch, "release dispatch regression model")

insert_before = '''    @Test
    fun fullRefreshAdmissionRequiresTimelineAndNoRunningCycle() {
'''
new_tests = '''    @Test
    fun pullProgressTracksThresholdAtTopAndAwayFromTop() {
        val topGate = gate(slop = 10f)
        topGate.onDown(Offset.Zero, canRefreshAtStart = true, refreshRunningAtStart = false)
        assertEquals(0.5f, topGate.pullProgress(Offset(0f, 5f)))
        assertEquals(1f, topGate.pullProgress(Offset(0f, 12f)))

        val awayGate = gate(slop = 10f)
        awayGate.onDown(Offset.Zero, canRefreshAtStart = false, refreshRunningAtStart = false)
        assertEquals(0.5f, awayGate.pullProgress(Offset(0f, 20f)))
        assertEquals(1f, awayGate.pullProgress(Offset(0f, 45f)))
    }

    @Test
    fun refreshDispatchRequiresReleaseAfterArmingAndNoRunningCycle() {
        assertFalse(shouldDispatchAgendaRefreshOnRelease0388(armed = false, refreshRunningAtStart = false))
        assertTrue(shouldDispatchAgendaRefreshOnRelease0388(armed = true, refreshRunningAtStart = false))
        assertFalse(shouldDispatchAgendaRefreshOnRelease0388(armed = true, refreshRunningAtStart = true))
    }

''' + insert_before
gesture_test = replace_once(gesture_test, insert_before, new_tests, "visual pull and release tests")

build = replace_once(build, 'versionCode = 5830', 'versionCode = 5831', "versionCode")
build = replace_once(build, 'versionName = "0.1.538"', 'versionName = "0.1.539"', "versionName")

write(GESTURE, gesture)
write(GESTURE_TEST, gesture_test)
write(BUILD, build)

final_gesture = read(GESTURE)
for marker in [
    'Solte para atualizar',
    'Puxe para atualizar',
    'Atualizando viagens…',
    'CircularProgressIndicator(',
    'shouldDispatchAgendaRefreshOnRelease0388(accepted, refreshRunningAtStart)',
    'latestOnPullProgress(1f, true)',
]:
    if marker not in final_gesture:
        fail(f"Visual pull marker missing after materialization: {marker}")
if 'latestOnRefresh()\n                }\n            }' in final_gesture:
    pass

print("Timeline 0.1.539 pull visual feedback materialized successfully")
