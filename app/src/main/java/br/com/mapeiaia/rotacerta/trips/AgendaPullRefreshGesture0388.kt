package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import kotlin.math.abs

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
 * It deliberately snapshots list eligibility and refresh state on DOWN. A gesture that starts
 * while the list is scrolled away from the top remains a normal list scroll even if that same
 * drag eventually reaches the top. Likewise, a gesture that starts while a full refresh is
 * running can never start a second cycle.
 */
internal class AgendaPullRefreshGestureGate0388(
    private val touchSlopPx: Float,
    private val verticalDominanceRatio: Float = 1.15f,
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

    fun onMove(position: Offset): AgendaPullRefreshDecision0388? {
        if (!started || resolved) return null
        val dx = position.x - start.x
        val dy = position.y - start.y
        val distanceSquared = dx * dx + dy * dy
        val slop = touchSlopPx.coerceAtLeast(0f)
        if (distanceSquared <= slop * slop) return null

        val verticalDown = dy > 0f && abs(dy) > abs(dx) * verticalDominanceRatio
        val outcome = when {
            !verticalDown -> AgendaPullRefreshOutcome0388.REJECTED_DIRECTION
            refreshingAtStart -> AgendaPullRefreshOutcome0388.BLOCKED_REFRESH_RUNNING
            !eligibleAtStart -> AgendaPullRefreshOutcome0388.BLOCKED_NOT_AT_TOP
            else -> AgendaPullRefreshOutcome0388.ACCEPTED
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
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.agendaPullRefreshGestureOwner0388(
            refreshing = refreshing,
            canRefreshAtGestureStart = canRefreshAtGestureStart,
            onRefresh = onRefresh,
            onDecision = onDecision,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun Modifier.agendaPullRefreshGestureOwner0388(
    refreshing: Boolean,
    canRefreshAtGestureStart: () -> Boolean,
    onRefresh: () -> Unit,
    onDecision: (AgendaPullRefreshDecision0388) -> Unit = {},
): Modifier {
    val latestRefreshing by rememberUpdatedState(refreshing)
    val latestCanRefresh by rememberUpdatedState(canRefreshAtGestureStart)
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val latestOnDecision by rememberUpdatedState(onDecision)
    val touchSlop = LocalViewConfiguration.current.touchSlop

    return pointerInput(touchSlop) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            val gate = AgendaPullRefreshGestureGate0388(touchSlopPx = touchSlop)
            gate.onDown(
                position = down.position,
                canRefreshAtStart = latestCanRefresh(),
                refreshRunningAtStart = latestRefreshing,
            )

            var accepted = false
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
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
        }
    }
}
