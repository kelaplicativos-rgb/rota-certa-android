package br.com.mapeiaia.rotacerta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FarolOneSecondVisualAuthority0711Test {
    @Test
    fun noObservationPreservesConfirmedPublicResult() {
        assertEquals(
            FarolOneSecondVisualAuthority0711.Action.KEEP,
            FarolOneSecondVisualAuthority0711.decide(
                currentAddressSignature = "ride|rua a 10",
                observedAddressSignature = null,
                hasPublicResult = true,
                nowElapsedMillis = 1_000L,
                lastConfirmedElapsedMillis = 900L,
            ),
        )
    }

    @Test
    fun noObservationWithoutAnyKnownStateRemainsIdle() {
        assertEquals(
            FarolOneSecondVisualAuthority0711.Action.CLEAR_IDLE,
            FarolOneSecondVisualAuthority0711.decide(
                currentAddressSignature = null,
                observedAddressSignature = null,
                hasPublicResult = false,
                nowElapsedMillis = 1_000L,
                lastConfirmedElapsedMillis = 0L,
            ),
        )
    }

    @Test
    fun sameAddressKeepsFreshPublicResult() {
        assertEquals(
            FarolOneSecondVisualAuthority0711.Action.KEEP,
            FarolOneSecondVisualAuthority0711.decide(
                currentAddressSignature = "ride|rua a 10",
                observedAddressSignature = "ride|rua a 10",
                hasPublicResult = true,
                nowElapsedMillis = 1_500L,
                lastConfirmedElapsedMillis = 900L,
            ),
        )
    }

    @Test
    fun changedAddressRevokesBeforeProcessingNewOne() {
        assertEquals(
            FarolOneSecondVisualAuthority0711.Action.CLEAR_THEN_PROCESS,
            FarolOneSecondVisualAuthority0711.decide(
                currentAddressSignature = "ride|rua antiga 10",
                observedAddressSignature = "ride|rua nova 20",
                hasPublicResult = true,
                nowElapsedMillis = 2_000L,
                lastConfirmedElapsedMillis = 1_900L,
            ),
        )
    }

    @Test
    fun publicLeaseExpiresAtOneSecond() {
        assertFalse(FarolOneSecondVisualAuthority0711.expired(1_999L, 1_000L))
        assertTrue(FarolOneSecondVisualAuthority0711.expired(2_000L, 1_000L))
        assertTrue(FarolOneSecondVisualAuthority0711.HEARTBEAT_MILLIS < FarolOneSecondVisualAuthority0711.RESULT_TTL_MILLIS)
    }

    @Test
    fun organicMapsRejectsWrongStateAndAcceptsVisibleLocality() {
        val query = "Rua Vicente Lopes, 8 (Cidade Satélite Santa Bárbara, São Paulo - SP)"
        val correct = OrganicMapsAddressValidation0711.scoreFields(
            query = query,
            name = "Rua Vicente Lopes",
            region = "Cidade Satélite Santa Bárbara, São Paulo, São Paulo, Brasil",
        )
        val wrong = OrganicMapsAddressValidation0711.scoreFields(
            query = query,
            name = "Rua Vicente Lopes",
            region = "Centro, Belo Horizonte, Minas Gerais, Brasil",
        )
        assertTrue(correct.accepted)
        assertFalse(wrong.accepted)
    }

    @Test
    fun exactOrganicMapsVersionMatchesUploadedMapGeneration() {
        assertEquals("2026.08.27-18-android", OrganicMapsEmbeddedRuntime0711.EXACT_UPSTREAM_TAG)
        assertEquals("260826", OrganicMapsEmbeddedRuntime0711.COMPATIBLE_DATA_VERSION_FOLDER)
    }
}
