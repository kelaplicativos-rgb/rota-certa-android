package br.com.mapeiaia.rotacerta.trips

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StandaloneRemoteAccessProvisioning0739Test {
    private fun validAccess() = StandaloneCoversRemoteAccess0736(
        refreshUrl = "https://example.test/refresh",
        latestUrl = "https://example.test/latest",
        tripQueryBaseUrl = "https://example.test/trip-query",
        expiresAtMillis = System.currentTimeMillis() + 60_000,
    )

    @Test fun createsAccessBeforeRequestingPushToken() = runBlocking<Unit> {
        val calls = mutableListOf<String>()
        val result = provisionStandaloneRemoteAccess0739(
            { calls.add("access"); validAccess() }, { calls.add("push"); true },
        )
        assertEquals(listOf("access", "push"), calls)
        assertTrue(result.pushRegistered)
        assertTrue(result.message.contains("ainda precisa ser confirmada"))
    }

    @Test fun missingPushTokenDoesNotHideOrPreventPrivateAccess() = runBlocking<Unit> {
        val result = provisionStandaloneRemoteAccess0739({ validAccess() }, { false })
        assertTrue(result.access.configured)
        assertFalse(result.pushRegistered)
        assertTrue(result.message.contains("pendente"))
        assertTrue(standaloneRemoteClipboardText0739(result.access).contains("Consulta HTML por viagem:"))
    }

    @Test fun pushFailureStillAllowsCopyAndDoesNotClaimConnectionConfirmed() = runBlocking<Unit> {
        val result = provisionStandaloneRemoteAccess0739({ validAccess() }, { error("FCM unavailable") })
        assertTrue(result.access.configured)
        assertFalse(result.pushRegistered)
        assertTrue(result.message.contains("ainda não está confirmado"))
    }

    @Test fun deniedAccessDoesNotProceedToPushRegistration() = runBlocking<Unit> {
        var pushCalled = false
        assertFailsWith<IllegalStateException> {
            provisionStandaloneRemoteAccess0739({ error("access denied") }, { pushCalled = true; true })
        }
        assertFalse(pushCalled)
    }

    @Test fun expiredAccessCannotBeCopiedOrAdvertisedAsReady() = runBlocking<Unit> {
        val expired = validAccess().copy(expiresAtMillis = 1)
        assertFailsWith<IllegalStateException> { standaloneRemoteClipboardText0739(expired) }
        assertFailsWith<IllegalStateException> { provisionStandaloneRemoteAccess0739({ expired }, { true }) }
    }

    @Test fun legacyAccessWithoutDetailedQueryIsRenewedInsteadOfCopied() = runBlocking<Unit> {
        val legacy = validAccess().copy(tripQueryBaseUrl = "")
        assertFailsWith<IllegalStateException> { standaloneRemoteClipboardText0739(legacy) }
        assertFailsWith<IllegalStateException> { provisionStandaloneRemoteAccess0739({ legacy }, { true }) }
    }

    @Test fun leavingScreenCancelsRegistrationRatherThanDisplayingFailure() = runBlocking<Unit> {
        assertFailsWith<CancellationException> {
            provisionStandaloneRemoteAccess0739({ validAccess() }, { throw CancellationException("screen closed") })
        }
    }

    @Test fun clipboardContainsAllThreeAccessPaths() {
        val access = validAccess()
        val text = standaloneRemoteClipboardText0739(access)
        assertTrue(text.contains(access.refreshUrl))
        assertTrue(text.contains(access.latestUrl))
        assertTrue(text.contains(access.tripQueryBaseUrl))
        assertEquals(4, text.lines().size)
    }
}
