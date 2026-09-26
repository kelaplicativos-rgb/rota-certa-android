package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaScriptPendingPolicy0561Test {
    private fun execution(state: AgendaTripScriptState0558): AgendaTripExecution0558 {
        val instruction = AgendaTripInstruction0558(
            index = 1,
            profileUuid = "profile-test",
            date = "2026-09-20",
            departureTime = "11:30",
            origin = "Origem",
            destination = "Destino",
            seats = 4,
            publish = true,
        )
        return AgendaTripExecution0558(
            executionId = "exec-test",
            scriptId = "script-test",
            scriptHash = "hash-test",
            startedAtMillis = 1L,
            updatedAtMillis = 1L,
            cancelled = true,
            rawScript = "{}",
            items = listOf(
                AgendaTripExecutionItem0558(
                    instruction = instruction,
                    fingerprint = "fp-test",
                    state = state,
                    accountId = "account-test",
                ),
            ),
        )
    }

    @Test fun creatingCannotReleaseActiveSlotBeforePublisherReturns() {
        assertEquals(
            AgendaScriptCancelDisposition0561.KEEP_ACTIVE_EXTERNAL_IN_FLIGHT,
            AgendaScriptPendingPolicy0561.cancellationDisposition(execution(AgendaTripScriptState0558.CREATING)),
        )
    }

    @Test fun ambiguousPublicationMovesToDetachedReconciliationQueue() {
        assertEquals(
            AgendaScriptCancelDisposition0561.DEFER_RECONCILIATION,
            AgendaScriptPendingPolicy0561.cancellationDisposition(execution(AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS)),
        )
        assertEquals(
            AgendaScriptCancelDisposition0561.DEFER_RECONCILIATION,
            AgendaScriptPendingPolicy0561.cancellationDisposition(execution(AgendaTripScriptState0558.CONFIRMED)),
        )
    }

    @Test fun terminalCancelledExecutionCanArchiveAndFreeActiveSlot() {
        assertEquals(
            AgendaScriptCancelDisposition0561.ARCHIVE_TERMINAL,
            AgendaScriptPendingPolicy0561.cancellationDisposition(execution(AgendaTripScriptState0558.CANCELLED_NOT_STARTED)),
        )
        assertEquals(
            AgendaScriptCancelDisposition0561.ARCHIVE_TERMINAL,
            AgendaScriptPendingPolicy0561.cancellationDisposition(execution(AgendaTripScriptState0558.SYNCED)),
        )
    }

    @Test fun publisherFailureBeforeSubmitIsRetryableButPostSubmitFailureIsAmbiguous() {
        assertEquals(
            AgendaScriptPublisherReturn0561.FAILED_BEFORE_SUBMIT,
            AgendaScriptPublisherReturnPolicy0561.classify(resultOk = false, submitAttempted = false),
        )
        assertEquals(
            AgendaScriptPublisherReturn0561.NEEDS_RECONCILIATION,
            AgendaScriptPublisherReturnPolicy0561.classify(resultOk = false, submitAttempted = true),
        )
        assertEquals(
            AgendaScriptPublisherReturn0561.NEEDS_RECONCILIATION,
            AgendaScriptPublisherReturnPolicy0561.classify(resultOk = true, submitAttempted = true),
        )
    }

    @Test fun freshMissingRequiresCompleteCoverageOfExactProfile() {
        assertTrue(
            AgendaScriptFreshMissingPolicy0561.canRetryAfterFreshMissing(
                state = AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS,
                profileUuid = "Profile-A",
                completeProfileUuids = setOf("profile-a"),
                freshGeneration = 9L,
            ),
        )
        assertFalse(
            AgendaScriptFreshMissingPolicy0561.canRetryAfterFreshMissing(
                state = AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS,
                profileUuid = "profile-a",
                completeProfileUuids = emptySet(),
                freshGeneration = 9L,
            ),
        )
        assertFalse(
            AgendaScriptFreshMissingPolicy0561.canRetryAfterFreshMissing(
                state = AgendaTripScriptState0558.CONFIRMED,
                profileUuid = "profile-a",
                completeProfileUuids = setOf("profile-a"),
                freshGeneration = 9L,
            ),
        )
        assertFalse(
            AgendaScriptFreshMissingPolicy0561.canRetryAfterFreshMissing(
                state = AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS,
                profileUuid = "profile-a",
                completeProfileUuids = setOf("profile-a"),
                freshGeneration = 0L,
            ),
        )
    }
}
