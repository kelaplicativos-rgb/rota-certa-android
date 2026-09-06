package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaSeatQueueLoop0367Test {
    @Test
    fun firstSuccessfulContinuationCanLaunchExactlyOnce() {
        assertTrue(
            shouldLaunchAutomaticSeatQueueContinuation(
                continuationToken = 1,
                handledContinuationToken = 0,
                manualSeatSyncing = false,
                accountCount = 2,
            ),
        )
        assertFalse(
            shouldLaunchAutomaticSeatQueueContinuation(
                continuationToken = 1,
                handledContinuationToken = 1,
                manualSeatSyncing = false,
                accountCount = 2,
            ),
        )
    }

    @Test
    fun pendingResultCannotRelaunchSameContinuationToken() {
        val continuationToken = 7
        val handledAfterLaunch = 7
        assertFalse(
            shouldLaunchAutomaticSeatQueueContinuation(
                continuationToken = continuationToken,
                handledContinuationToken = handledAfterLaunch,
                manualSeatSyncing = false,
                accountCount = 1,
            ),
        )
    }

    @Test
    fun nextSuccessCreatesOneNewContinuationBudget() {
        assertTrue(
            shouldLaunchAutomaticSeatQueueContinuation(
                continuationToken = 8,
                handledContinuationToken = 7,
                manualSeatSyncing = false,
                accountCount = 1,
            ),
        )
    }

    @Test
    fun noAccountOrInFlightSyncCannotLaunchContinuation() {
        assertFalse(
            shouldLaunchAutomaticSeatQueueContinuation(
                continuationToken = 1,
                handledContinuationToken = 0,
                manualSeatSyncing = true,
                accountCount = 1,
            ),
        )
        assertFalse(
            shouldLaunchAutomaticSeatQueueContinuation(
                continuationToken = 1,
                handledContinuationToken = 0,
                manualSeatSyncing = false,
                accountCount = 0,
            ),
        )
    }
}
