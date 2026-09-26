package br.com.mapeiaia.rotacerta.trips

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BlaBlaTripBoost0562Test {
    private val profileUuid = "7371f028-9c55-4903-8444-308015823efd"
    private val tripId = "boost-test-trip-123"

    @Test
    fun parserAcceptsExplicitOnAndOffWithoutToggleSemantics() {
        val on = BlaBlaTripBoostCommandParser0562.parse(json("ON"))
        val off = BlaBlaTripBoostCommandParser0562.parse(json("OFF"))
        assertEquals(BlaBlaTripBoostDesiredState0562.ENABLED, on.desiredState)
        assertEquals(BlaBlaTripBoostDesiredState0562.DISABLED, off.desiredState)
        assertNotEquals(on.desiredState, off.desiredState)
    }

    @Test
    fun parserRejectsToggleAndUnknownExecutableFields() {
        expectFailure { BlaBlaTripBoostCommandParser0562.parse(json("TOGGLE")) }
        expectFailure {
            BlaBlaTripBoostCommandParser0562.parse(
                """{"schemaVersion":"1.0","action":"SET_TRIP_BOOST","mode":"EXECUTE","tripReference":"$tripId","freeTextValue":"ON","javascript":"alert(1)"}""",
            )
        }
        expectFailure {
            BlaBlaTripBoostCommandParser0562.parse(
                """{"schemaVersion":"1.0","action":"SET_TRIP_BOOST","mode":"EXECUTE","tripReference":"$tripId","freeTextValue":"ON","shell":"rm -rf /"}""",
            )
        }
    }

    @Test
    fun parserRejectsMissingStrongTripReferenceAndUnsupportedMode() {
        expectFailure {
            BlaBlaTripBoostCommandParser0562.parse(
                """{"schemaVersion":"1.0","action":"SET_TRIP_BOOST","mode":"EXECUTE","tripReference":"","freeTextValue":"ON"}""",
            )
        }
        expectFailure {
            BlaBlaTripBoostCommandParser0562.parse(
                """{"schemaVersion":"1.0","action":"SET_TRIP_BOOST","mode":"AUTO","tripReference":"$tripId","freeTextValue":"ON"}""",
            )
        }
    }

    @Test
    fun targetResolverRequiresStrongProfileTripAndManageUrlBinding() {
        val target = BlaBlaTripBoostTargetResolver0562.resolve(
            command = BlaBlaTripBoostCommandParser0562.parse(json("ON")),
            trips = listOf(strongTrip()),
            accounts = listOf(account(profileUuid)),
            zoneId = ZoneId.of("America/Sao_Paulo"),
        )
        assertEquals(BlaBlaTripBoostTargetCode0562.OK, target.code)
        assertEquals(tripId, target.trip?.blablaTripId)
        assertEquals(profileUuid, target.account?.profileUuid)
    }

    @Test
    fun targetResolverFailsClosedWhenProfileSessionDoesNotMatch() {
        val target = BlaBlaTripBoostTargetResolver0562.resolve(
            command = BlaBlaTripBoostCommandParser0562.parse(json("OFF")),
            trips = listOf(strongTrip()),
            accounts = listOf(account("175a7068-50d8-40c3-a27a-214b9c6e0461")),
            zoneId = ZoneId.of("America/Sao_Paulo"),
        )
        assertEquals(BlaBlaTripBoostTargetCode0562.SESSION_NOT_FOUND, target.code)
    }

    @Test
    fun targetResolverRejectsManageUrlWithoutAdministrativeTripId() {
        val target = BlaBlaTripBoostTargetResolver0562.resolve(
            command = BlaBlaTripBoostCommandParser0562.parse(json("ON")),
            trips = listOf(strongTrip().copy(blablaManageUrl = "https://www.blablacar.com.br/rides/offer/edit/other-trip")),
            accounts = listOf(account(profileUuid)),
            zoneId = ZoneId.of("America/Sao_Paulo"),
        )
        assertEquals(BlaBlaTripBoostTargetCode0562.MISSING_STRONG_MANAGE_URL, target.code)
    }

    @Test
    fun beforeWriteSkipsWhenAlreadyDesiredAndWritesOnlyOppositeKnownState() {
        val skip = BlaBlaTripBoostPolicy0562.beforeWrite(
            BlaBlaTripBoostObservedState0562.ENABLED,
            BlaBlaTripBoostDesiredState0562.ENABLED,
            identityVerified = true,
            screenVerified = true,
        )
        assertFalse(skip.shouldWrite)
        assertEquals(BlaBlaTripBoostSyncState0562.SKIPPED_ALREADY_IN_DESIRED_STATE, skip.terminalState)

        val write = BlaBlaTripBoostPolicy0562.beforeWrite(
            BlaBlaTripBoostObservedState0562.DISABLED,
            BlaBlaTripBoostDesiredState0562.ENABLED,
            identityVerified = true,
            screenVerified = true,
        )
        assertTrue(write.shouldWrite)
    }

    @Test
    fun unknownOrUnverifiedPreReadNeverAuthorizesWrite() {
        val unknown = BlaBlaTripBoostPolicy0562.beforeWrite(
            BlaBlaTripBoostObservedState0562.UNKNOWN,
            BlaBlaTripBoostDesiredState0562.ENABLED,
            identityVerified = true,
            screenVerified = true,
        )
        assertFalse(unknown.shouldWrite)
        assertEquals(BlaBlaTripBoostSyncState0562.FAILED_BLOCKING, unknown.terminalState)

        val wrongIdentity = BlaBlaTripBoostPolicy0562.beforeWrite(
            BlaBlaTripBoostObservedState0562.DISABLED,
            BlaBlaTripBoostDesiredState0562.ENABLED,
            identityVerified = false,
            screenVerified = true,
        )
        assertFalse(wrongIdentity.shouldWrite)
        assertEquals(BlaBlaTripBoostSyncState0562.FAILED_BLOCKING, wrongIdentity.terminalState)
    }

    @Test
    fun postSaveRequiresExactReadback() {
        assertEquals(
            BlaBlaTripBoostSyncState0562.WRITE_VERIFIED,
            BlaBlaTripBoostPolicy0562.afterWriteReadback(
                BlaBlaTripBoostObservedState0562.ENABLED,
                BlaBlaTripBoostDesiredState0562.ENABLED,
            ),
        )
        assertEquals(
            BlaBlaTripBoostSyncState0562.WRITE_AMBIGUOUS,
            BlaBlaTripBoostPolicy0562.afterWriteReadback(
                BlaBlaTripBoostObservedState0562.DISABLED,
                BlaBlaTripBoostDesiredState0562.ENABLED,
            ),
        )
        assertEquals(
            BlaBlaTripBoostSyncState0562.WRITE_AMBIGUOUS,
            BlaBlaTripBoostPolicy0562.afterWriteReadback(
                BlaBlaTripBoostObservedState0562.UNKNOWN,
                BlaBlaTripBoostDesiredState0562.ENABLED,
            ),
        )
    }

    @Test
    fun browserRegistrySeparatesReadsNavigationAndWrites() {
        assertEquals(BlaBlaBrowserOperation.CAPTURE, BlaBlaBrowserRequest.BOOST_STATE.operation)
        assertEquals(BlaBlaBrowserOperation.NAVIGATION, BlaBlaBrowserRequest.BOOST_OPEN_EDIT.operation)
        assertEquals(BlaBlaBrowserOperation.NAVIGATION, BlaBlaBrowserRequest.BOOST_OPEN_SECTION.operation)
        assertEquals(BlaBlaBrowserOperation.REMOTE_WRITE, BlaBlaBrowserRequest.BOOST_SET_STATE.operation)
        assertEquals(BlaBlaBrowserOperation.REMOTE_WRITE, BlaBlaBrowserRequest.BOOST_SAVE.operation)
        assertTrue(BlaBlaBrowserRequest.BOOST_SET_STATE.mutatesRemoteState)
        assertFalse(BlaBlaBrowserRequest.BOOST_STATE.mutatesRemoteState)
    }

    private fun json(state: String): String =
        """{"schemaVersion":"1.0","action":"SET_TRIP_BOOST","mode":"EXECUTE","tripReference":"$tripId","freeTextValue":"$state"}"""

    private fun strongTrip(): Trip = Trip(
        id = "local-boost-trip",
        title = "Teste Boost",
        departureAtMillis = Instant.parse("2026-09-20T14:30:00Z").toEpochMilli(),
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(order = 0, name = "Origem"),
            TripStop(order = 1, name = "Destino"),
        ),
        blablaProfileUuid = profileUuid,
        blablaTripId = tripId,
        blablaManageUrl = "https://www.blablacar.com.br/rides/offer/edit/$tripId",
        canonicalRevision = 7L,
    )

    private fun account(uuid: String): BlaBlaDynamicAccount = BlaBlaDynamicAccount(
        id = "account-$uuid",
        label = "Perfil teste",
        webProfileName = "boost_test_profile",
        profileUuid = uuid,
    )

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected validation failure")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
