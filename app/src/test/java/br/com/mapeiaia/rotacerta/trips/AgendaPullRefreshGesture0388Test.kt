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
    fun downwardDragStartedAwayFromTopPreservesShortScrollThenAcceptsDeliberatePull() {
        val gate = gate()
        gate.onDown(Offset.Zero, canRefreshAtStart = false, refreshRunningAtStart = false)

        assertNull(gate.onMove(Offset(0f, 30f)), "short reverse scroll away from top must remain available")
        val decision = gate.onMove(Offset(0f, 50f))

        assertEquals(AgendaPullRefreshOutcome0388.ACCEPTED, decision?.outcome)
        assertTrue(decision?.accepted == true)
        assertFalse(decision?.eligibleAtStart == true)
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
            val armed = gate.onMove(Offset(0f, 50f))?.accepted == true
            if (shouldDispatchAgendaRefreshOnRelease0388(armed, refreshRunningAtStart)) {
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

    @Test
    fun fullRefreshAdmissionRequiresTimelineAndNoRunningCycle() {
        assertTrue(shouldStartAgendaFullRefresh0388(timelineActive = true, refreshAllRunning = false))
        assertFalse(shouldStartAgendaFullRefresh0388(timelineActive = false, refreshAllRunning = false))
        assertFalse(shouldStartAgendaFullRefresh0388(timelineActive = true, refreshAllRunning = true))
    }
}
