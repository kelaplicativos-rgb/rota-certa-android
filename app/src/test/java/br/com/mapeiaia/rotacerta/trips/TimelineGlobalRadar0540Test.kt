package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelineGlobalRadar0540Test {
    private fun audit(
        pending: Boolean,
        status: BlaBlaCommandStatus0407 = if (pending) {
            BlaBlaCommandStatus0407.QUEUED
        } else {
            BlaBlaCommandStatus0407.VERIFIED_SUCCESS
        },
    ) = BlaBlaCommandAuditSnapshot0407(
        commandId = if (pending) "pending-command" else "terminal-command",
        status = status,
        requestedAtMillis = 1L,
        finishedAtMillis = if (pending) 0L else 2L,
        pending = pending,
    )

    @Test
    fun globalRadarStaysLockedWhileAnyTargetIsPending() {
        assertTrue(
            timelineGlobalRadarBatchBusy0540(
                listOf(audit(pending = false), audit(pending = true), audit(pending = false)),
            ),
        )
    }

    @Test
    fun globalRadarUnlocksOnlyWhenEveryTargetIsTerminal() {
        assertFalse(
            timelineGlobalRadarBatchBusy0540(
                listOf(audit(pending = false), audit(pending = false)),
            ),
        )
        assertFalse(timelineGlobalRadarBatchBusy0540(emptyList()))
    }
}
