package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaCarSessionKeeper0552Test {
    private val account = BlaBlaDynamicAccount(
        id = "account-test",
        label = "Conta de teste",
        webProfileName = "rota_certa_test_profile",
        profileUuid = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        profileName = "Perfil Teste",
    )

    @Test
    fun explicitLoginPageOverridesHistoricalVerifiedUuid() {
        val snapshot = BlaBlaDynamicSessionSnapshot(
            accountId = account.id,
            profileUuid = account.profileUuid,
            profileLabel = account.displayLabel,
            identityVerified = true,
            lastUrl = "https://www.blablacar.com.br/login",
            lastValidSyncAtMillis0426 = System.currentTimeMillis(),
        )

        val health = BlaBlaCarSessionKeeper0552.health(account, snapshot)

        assertEquals(BlaBlaSessionState0552.LOGIN_REQUIRED, health.state)
        assertTrue(health.blocksAuthenticatedOperation)
        assertFalse(
            BlaBlaCarSessionKeeper0552.canReuseRecentVerifiedSnapshotWhenIdentityNotObservable0555(
                account,
                snapshot,
            ),
        )
    }

    @Test
    fun oldVerifiedSnapshotIsNotPresentedAsCurrentlyConnectedAndBlocksFreshAuthenticatedWork() {
        val now = 2_000_000_000_000L
        val snapshot = BlaBlaDynamicSessionSnapshot(
            accountId = account.id,
            profileUuid = account.profileUuid,
            profileLabel = account.displayLabel,
            identityVerified = true,
            lastUrl = "https://www.blablacar.com.br/rides",
            lastValidSyncAtMillis0426 = now - 31L * 60L * 1000L,
        )

        val health = BlaBlaCarSessionKeeper0552.health(account, snapshot, now)

        assertEquals(BlaBlaSessionState0552.SUSPECTED, health.state)
        assertTrue(health.blocksAuthenticatedOperation)
        assertFalse(
            BlaBlaCarSessionKeeper0552.canReuseRecentVerifiedSnapshotWhenIdentityNotObservable0555(
                account,
                snapshot,
                now,
            ),
        )
    }

    @Test
    fun onlyValidStateAuthorizesAuthenticatedOperation() {
        BlaBlaSessionState0552.entries.forEach { state ->
            val health = BlaBlaSessionHealth0552(state = state)
            assertEquals(
                state != BlaBlaSessionState0552.VALID,
                health.blocksAuthenticatedOperation,
                "state=$state",
            )
        }
    }

    @Test
    fun profileMismatchIsFailClosed() {
        val snapshot = BlaBlaDynamicSessionSnapshot(
            accountId = account.id,
            profileUuid = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            profileLabel = "Outro perfil",
            identityVerified = true,
            lastUrl = "https://www.blablacar.com.br/rides",
            lastValidSyncAtMillis0426 = System.currentTimeMillis(),
        )

        val health = BlaBlaCarSessionKeeper0552.health(account, snapshot)

        assertEquals(BlaBlaSessionState0552.PROFILE_MISMATCH, health.state)
        assertTrue(health.blocksAuthenticatedOperation)
        assertFalse(
            BlaBlaCarSessionKeeper0552.canReuseRecentVerifiedSnapshotWhenIdentityNotObservable0555(
                account,
                snapshot,
            ),
        )
    }

    @Test
    fun recentExactVerifiedSnapshotRemainsValidWhenThereIsNoContradictingRuntimeObservation() {
        val now = 2_000_000_000_000L
        val snapshot = BlaBlaDynamicSessionSnapshot(
            accountId = account.id,
            profileUuid = account.profileUuid,
            profileLabel = account.displayLabel,
            identityVerified = true,
            lastUrl = "https://www.blablacar.com.br/rides",
            lastValidSyncAtMillis0426 = now - 1_000L,
        )

        val health = BlaBlaCarSessionKeeper0552.health(account, snapshot, now)

        assertEquals(BlaBlaSessionState0552.VALID, health.state)
        assertFalse(health.blocksAuthenticatedOperation)
        assertTrue(
            BlaBlaCarSessionKeeper0552.canReuseRecentVerifiedSnapshotWhenIdentityNotObservable0555(
                account,
                snapshot,
                now,
            ),
        )
    }

    @Test
    fun identityEvidenceHasThreeExplicitStates() {
        assertEquals(
            BlaBlaIdentityEvidenceState0555.MATCH,
            BlaBlaCarSessionKeeper0552.classifyIdentityEvidence0555(
                expectedProfileUuid = account.profileUuid,
                actualProfileUuid = account.profileUuid,
            ),
        )
        assertEquals(
            BlaBlaIdentityEvidenceState0555.CONFLICT,
            BlaBlaCarSessionKeeper0552.classifyIdentityEvidence0555(
                expectedProfileUuid = account.profileUuid,
                actualProfileUuid = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            ),
        )
        assertEquals(
            BlaBlaIdentityEvidenceState0555.NOT_OBSERVABLE,
            BlaBlaCarSessionKeeper0552.classifyIdentityEvidence0555(
                expectedProfileUuid = account.profileUuid,
                actualProfileUuid = null,
            ),
        )
    }

    @Test
    fun notObservableIdentityCanReuseOnlyRecentVerifiedSameProfileProof() {
        val now = 2_000_000_000_000L
        val recentSameProfile = BlaBlaDynamicSessionSnapshot(
            accountId = account.id,
            profileUuid = account.profileUuid,
            profileLabel = account.displayLabel,
            identityVerified = true,
            lastUrl = "https://www.blablacar.com.br/rides",
            lastValidSyncAtMillis0426 = now - 10_000L,
        )
        val unverified = recentSameProfile.copy(identityVerified = false)
        val wrongProfile = recentSameProfile.copy(profileUuid = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

        assertTrue(
            BlaBlaCarSessionKeeper0552.canReuseRecentVerifiedSnapshotWhenIdentityNotObservable0555(
                account,
                recentSameProfile,
                now,
            ),
        )
        assertFalse(
            BlaBlaCarSessionKeeper0552.canReuseRecentVerifiedSnapshotWhenIdentityNotObservable0555(
                account,
                unverified,
                now,
            ),
        )
        assertFalse(
            BlaBlaCarSessionKeeper0552.canReuseRecentVerifiedSnapshotWhenIdentityNotObservable0555(
                account,
                wrongProfile,
                now,
            ),
        )
        assertFalse(
            BlaBlaCarSessionKeeper0552.canReuseRecentVerifiedSnapshotWhenIdentityNotObservable0555(
                account,
                null,
                now,
            ),
        )
    }

    @Test
    fun temporaryRestrictionNeverBecomesImplicitAuthenticationProof() {
        val now = 2_000_000_000_000L
        val snapshot = BlaBlaDynamicSessionSnapshot(
            accountId = account.id,
            profileUuid = account.profileUuid,
            profileLabel = account.displayLabel,
            identityVerified = true,
            lastUrl = "https://www.blablacar.com.br/rides",
            lastValidSyncAtMillis0426 = now - 10_000L,
            sourceAccessStatus0426 = BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED,
        )

        assertFalse(
            BlaBlaCarSessionKeeper0552.canReuseRecentVerifiedSnapshotWhenIdentityNotObservable0555(
                account,
                snapshot,
                now,
            ),
        )
    }

    @Test
    fun lookalikeDomainIsNeverClassifiedAsOfficialLogin() {
        assertFalse(BlaBlaCarSessionKeeper0552.isExplicitLoginUrl("https://blablacar.com.br.evil.example/login"))
        assertTrue(BlaBlaCarSessionKeeper0552.isExplicitLoginUrl("https://www.blablacar.com.br/login"))
    }
}
