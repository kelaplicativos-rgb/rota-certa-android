package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal fun shouldStartAgendaFullRefresh0388(
    timelineActive: Boolean,
    refreshAllRunning: Boolean,
): Boolean = timelineActive && !refreshAllRunning

internal fun shouldDispatchAgendaRefreshOnRelease0388(
    armed: Boolean,
    refreshRunningAtStart: Boolean,
): Boolean = armed && !refreshRunningAtStart

internal enum class AgendaPullRefreshOutcome0388 {
    ACCEPTED,
    BLOCKED_NOT_AT_TOP,
    BLOCKED_REFRESH_RUNNING,
    REJECTED_DIRECTION,
}

internal data class AgendaPullRefreshDecision0388(
    val outcome: AgendaPullRefreshOutcome0388,
    val deltaX: Float,
    val deltaY: Float,
    val eligibleAtStart: Boolean,
    val refreshingAtStart: Boolean,
) {
    val accepted: Boolean
        get() = outcome == AgendaPullRefreshOutcome0388.ACCEPTED
}

/**
 * Pointer-sequence gate for the Agenda Timeline pull-to-refresh.
 *
 * It snapshots list position and refresh state on DOWN. At the top, the existing touch-slop
 * threshold remains immediate. Away from the top, a larger deliberate downward pull is required
 * so ordinary short reverse scrolling still works. A gesture that starts while a full refresh is
 * running can never start a second cycle.
 */
internal class AgendaPullRefreshGestureGate0388(
    private val touchSlopPx: Float,
    private val verticalDominanceRatio: Float = 1.15f,
    private val awayFromTopTriggerMultiplier: Float = 4f,
) {
    private var started = false
    private var resolved = false
    private var start = Offset.Zero
    private var eligibleAtStart = false
    private var refreshingAtStart = false

    fun onDown(
        position: Offset,
        canRefreshAtStart: Boolean,
        refreshRunningAtStart: Boolean,
    ) {
        started = true
        resolved = false
        start = position
        eligibleAtStart = canRefreshAtStart
        refreshingAtStart = refreshRunningAtStart
    }

    fun pullProgress(position: Offset): Float {
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

    fun onMove(position: Offset): AgendaPullRefreshDecision0388? {
        if (!started || resolved) return null
        val dx = position.x - start.x
        val dy = position.y - start.y
        val distanceSquared = dx * dx + dy * dy
        val slop = touchSlopPx.coerceAtLeast(0f)
        val verticalDown = dy > 0f && abs(dy) > abs(dx) * verticalDominanceRatio
        val outcome = when {
            !verticalDown -> {
                if (distanceSquared <= slop * slop) return null
                AgendaPullRefreshOutcome0388.REJECTED_DIRECTION
            }
            refreshingAtStart -> {
                if (distanceSquared <= slop * slop) return null
                AgendaPullRefreshOutcome0388.BLOCKED_REFRESH_RUNNING
            }
            else -> {
                val triggerPx = if (eligibleAtStart) {
                    slop
                } else {
                    slop * awayFromTopTriggerMultiplier.coerceAtLeast(1f)
                }
                if (dy <= triggerPx) return null
                AgendaPullRefreshOutcome0388.ACCEPTED
            }
        }
        resolved = true
        return AgendaPullRefreshDecision0388(
            outcome = outcome,
            deltaX = dx,
            deltaY = dy,
            eligibleAtStart = eligibleAtStart,
            refreshingAtStart = refreshingAtStart,
        )
    }

    fun onUpOrCancel() {
        started = false
        resolved = false
        start = Offset.Zero
        eligibleAtStart = false
        refreshingAtStart = false
    }
}

/**
 * Non-destructive ancestor observer for the Timeline.
 *
 * A simple tap is never consumed. Only after the gate has positively recognized an eligible
 * downward vertical drag do we consume pointer changes, cancelling the child click/scroll for
 * that gesture and dispatching exactly one refresh callback.
 */
@Composable
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

@Composable
internal fun Modifier.agendaPullRefreshGestureOwner0388(
    refreshing: Boolean,
    canRefreshAtGestureStart: () -> Boolean,
    onRefresh: () -> Unit,
    onDecision: (AgendaPullRefreshDecision0388) -> Unit = {},
    onPointerDown: (Offset, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onPointerEnd: (Offset, Boolean) -> Unit = { _, _ -> },
    onPullProgress: (Float, Boolean) -> Unit = { _, _ -> },
): Modifier {
    val latestRefreshing by rememberUpdatedState(refreshing)
    val latestCanRefresh by rememberUpdatedState(canRefreshAtGestureStart)
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val latestOnDecision by rememberUpdatedState(onDecision)
    val latestOnPointerDown by rememberUpdatedState(onPointerDown)
    val latestOnPointerEnd by rememberUpdatedState(onPointerEnd)
    val latestOnPullProgress by rememberUpdatedState(onPullProgress)
    val touchSlop = LocalViewConfiguration.current.touchSlop

    return pointerInput(touchSlop) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            val gate = AgendaPullRefreshGestureGate0388(touchSlopPx = touchSlop)
            val canRefreshAtStart = latestCanRefresh()
            val refreshRunningAtStart = latestRefreshing
            gate.onDown(
                position = down.position,
                canRefreshAtStart = canRefreshAtStart,
                refreshRunningAtStart = refreshRunningAtStart,
            )
            latestOnPointerDown(down.position, canRefreshAtStart, refreshRunningAtStart)
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
        }
    }
}
