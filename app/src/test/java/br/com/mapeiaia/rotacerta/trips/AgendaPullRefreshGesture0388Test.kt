package br.com.mapeiaia.rotacerta.trips

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgendaPullRefreshGesture0388Test {
    private fun gate(slop: Float = 10f) = AgendaPullRefreshGestureGate0388(
        touchSlopPx = slop,
        verticalDominanceRatio = 1.15f,
    )

    @Test
    fun validDownwardGestureIsAcceptedExactlyOncePerPointerSequence() {
        val gate = gate()
        gate.onDown(Offset(100f, 100f), canRefreshAtStart = true, refreshRunningAtStart = false)

        assertNull(gate.onMove(Offset(100f, 106f)))
        val accepted = gate.onMove(Offset(102f, 132f))

        assertEquals(AgendaPullRefreshOutcome0388.ACCEPTED, accepted?.outcome)
        assertTrue(accepted?.accepted == true)
        assertNull(gate.onMove(Offset(100f, 180f)), "same pointer sequence must never dispatch twice")
    }

    @Test
    fun movementInsideTouchSlopRemainsPotentialTap() {
        val gate = gate(slop = 12f)
        gate.onDown(Offset.Zero, canRefreshAtStart = true, refreshRunningAtStart = false)

        assertNull(gate.onMove(Offset(2f, 4f)))
        assertNull(gate.onMove(Offset(3f, 8f)))
    }

    @Test
    fun upwardHorizontalAndHorizontalDominantDiagonalAreRejected() {
        listOf(
            Offset(0f, -30f),
            Offset(40f, 0f),
            Offset(50f, 20f),
        ).forEach { target ->
            val gate = gate()
            gate.onDown(Offset.Zero, canRefreshAtStart = true, refreshRunningAtStart = false)
            val decision = gate.onMove(target)
            assertEquals(AgendaPullRefreshOutcome0388.REJECTED_DIRECTION, decision?.outcome, "target=$target")
            assertFalse(decision?.accepted == true)
        }
    }

    @Test
    fun downwardDragStartedAwayFromTopRemainsNormalScrollForWholeSequence() {
        val gate = gate()
        gate.onDown(Offset.Zero, canRefreshAtStart = false, refreshRunningAtStart = false)

        val decision = gate.onMove(Offset(0f, 40f))

        assertEquals(AgendaPullRefreshOutcome0388.BLOCKED_NOT_AT_TOP, decision?.outcome)
        assertNull(gate.onMove(Offset(0f, 100f)), "reaching top later in the same drag must not become a refresh")
    }

    @Test
    fun refreshAlreadyRunningBlocksNewGestureBeforeCallback() {
        val gate = gate()
        gate.onDown(Offset.Zero, canRefreshAtStart = true, refreshRunningAtStart = true)

        val decision = gate.onMove(Offset(0f, 40f))

        assertEquals(AgendaPullRefreshOutcome0388.BLOCKED_REFRESH_RUNNING, decision?.outcome)
        assertFalse(decision?.accepted == true)
    }

    @Test
    fun upOrCancelResetsGateForTheNextIndependentGesture() {
        val gate = gate()
        gate.onDown(Offset.Zero, canRefreshAtStart = true, refreshRunningAtStart = false)
        assertEquals(AgendaPullRefreshOutcome0388.ACCEPTED, gate.onMove(Offset(0f, 40f))?.outcome)

        gate.onUpOrCancel()
        gate.onDown(Offset.Zero, canRefreshAtStart = true, refreshRunningAtStart = false)

        assertEquals(AgendaPullRefreshOutcome0388.ACCEPTED, gate.onMove(Offset(0f, 40f))?.outcome)
    }

    @Test
    fun acceptedGestureRoutesThroughTheSameFullRefreshSingleFlightRule() {
        var refreshAllRunning = false
        var fullRefreshRequests = 0
        var userSyncAllEvents = 0
        var pullRequestedEvents = 0

        fun requestFullTimelineRefresh() {
            if (shouldStartAgendaFullRefresh0388(timelineActive = true, refreshAllRunning = refreshAllRunning)) {
                userSyncAllEvents++
                pullRequestedEvents++
                fullRefreshRequests++
                refreshAllRunning = true
            }
        }

        fun dispatchGesture(refreshRunningAtStart: Boolean) {
            val gate = gate()
            gate.onDown(
                Offset.Zero,
                canRefreshAtStart = true,
                refreshRunningAtStart = refreshRunningAtStart,
            )
            if (gate.onMove(Offset(0f, 50f))?.accepted == true) {
                requestFullTimelineRefresh()
            }
        }

        dispatchGesture(refreshRunningAtStart = false)
        dispatchGesture(refreshRunningAtStart = refreshAllRunning)

        assertEquals(1, fullRefreshRequests)
        assertEquals(1, userSyncAllEvents)
        assertEquals(1, pullRequestedEvents)
        assertTrue(refreshAllRunning)
    }

    @Test
    fun fullRefreshAdmissionRequiresTimelineAndNoRunningCycle() {
        assertTrue(shouldStartAgendaFullRefresh0388(timelineActive = true, refreshAllRunning = false))
        assertFalse(shouldStartAgendaFullRefresh0388(timelineActive = false, refreshAllRunning = false))
        assertFalse(shouldStartAgendaFullRefresh0388(timelineActive = true, refreshAllRunning = true))
    }
}
