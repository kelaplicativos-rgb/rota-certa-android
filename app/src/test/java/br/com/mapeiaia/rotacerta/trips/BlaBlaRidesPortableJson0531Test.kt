package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaRidesPortableJson0531Test {
    private val profileUuid = "7371f028-9c55-4903-8444-308015823efd"
    private val trips = listOf("trip_admin_0001", "trip_admin_0002")

    private fun validProfile(): RotaCertaBlaBlaRidesProfile0531 =
        RotaCertaBlaBlaRidesProfile0531(
            accountKey = "0123456789abcdef",
            displayName = "Ezequiel",
            expectedProfileUuid = profileUuid,
            authenticatedProfileUuid = profileUuid,
            identityConfirmed = true,
            status = BlaBlaRidesSnapshotStatus0526.COMPLETE,
            capturedAt = "2026-09-10T10:00:00Z",
            rideCount = trips.size,
            tripIds = trips.sorted(),
            tripIdsSha256 = tripSetSha2560528(trips),
            duplicateCount = 0,
            earliestDate = "2026-09-10",
            latestDate = "2026-09-20",
            reachedEnd = true,
            stabilized = true,
        )

    private fun validPayload(): RotaCertaBlaBlaRidesJson0531 =
        RotaCertaBlaBlaRidesJson0531(
            captureId = "2026-09-10T10-09-50.586325Z_37bc992e",
            captureCompletedAt = "2026-09-10T10:10:17Z",
            sourceManifestSchemaVersion = "blablacar-rides-snapshot-v2",
            sourceAppVersion = "0.1.531",
            sourceVersionCode = 5823,
            sourceCommitSha = "0123456789abcdef0123456789abcdef01234567",
            sourceBranch = "agent/blablacar-rides-portable-json-0.1.531",
            result = BlaBlaRidesSnapshotStatus0526.COMPLETE,
            totalProfiles = 1,
            totalRides = trips.size,
            profiles = listOf(validProfile()),
        )

    @Test
    fun strictRoundTripProvesRotaCertaCanReadItsPortableJson() {
        val expected = validPayload()
        val raw = BlaBlaRidesPortableJson0531.encode(expected)
        val decoded = BlaBlaRidesPortableJson0531.decode(raw)

        assertEquals(expected, decoded)
        assertTrue(raw.contains("\"schemaVersion\": \"$SCHEMA_VERSION_0531\""))
        assertTrue(raw.contains("\"kind\": \"$KIND_0531\""))
        assertTrue(raw.contains("\"totalRides\": 2"))
    }

    @Test
    fun unknownFieldsAreRejectedInsteadOfSilentlyMisunderstood() {
        val raw = BlaBlaRidesPortableJson0531.encode(validPayload())
        val withUnknownField = raw.replaceFirst("{", "{\n  \"unexpected\": true,")

        assertThrows(Exception::class.java) {
            BlaBlaRidesPortableJson0531.decode(withUnknownField)
        }
    }

    @Test
    fun mismatchedProfileIdentityIsRejected() {
        val otherUuid = "175a7068-50d8-40c3-a27a-214b9c6e0461"
        val invalid = validPayload().copy(
            profiles = listOf(validProfile().copy(authenticatedProfileUuid = otherUuid)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            BlaBlaRidesPortableJson0531.encode(invalid)
        }
    }

    @Test
    fun mismatchedTripHashIsRejected() {
        val invalid = validPayload().copy(
            profiles = listOf(validProfile().copy(tripIdsSha256 = "0".repeat(64))),
        )

        assertThrows(IllegalArgumentException::class.java) {
            BlaBlaRidesPortableJson0531.encode(invalid)
        }
    }

    @Test
    fun incompleteCaptureIsRejected() {
        val invalid = validPayload().copy(result = "PARTIAL_SUCCESS")

        assertThrows(IllegalArgumentException::class.java) {
            BlaBlaRidesPortableJson0531.encode(invalid)
        }
    }

    @Test
    fun portableJsonNeverCarriesRawForensicOrReusableAuthMaterial() {
        val raw = BlaBlaRidesPortableJson0531.encode(validPayload()).lowercase()

        assertFalse(raw.contains("suas-viagens.html"))
        assertFalse(raw.contains("suas-viagens.mhtml"))
        assertFalse(raw.contains("set-cookie"))
        assertFalse(raw.contains("authorization: bearer"))
        assertFalse(raw.contains("access_token"))
        assertFalse(raw.contains("refresh_token"))
    }
}
