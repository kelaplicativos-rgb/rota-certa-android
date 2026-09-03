package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaPublicTimelineReflection0425Test {
    private val exactHash = "public-v2:" + "a".repeat(64)

    @Test
    fun equalCapacityRevisionOnlyNoOpsWhenPublicProjectionHashAlsoMatches() {
        val remote = DriverTripSyncState0402(
            remoteTripId = "remote-0425",
            capacityReliable = true,
            capacitySnapshotRevision = "capacity-0425",
            publicProjectionHash = exactHash,
        )

        assertTrue(
            remotePublicProjectionMatches0425(
                remote = remote,
                expectedPublicProjectionHash = exactHash,
                snapshotRevision = "capacity-0425",
            ),
        )
        assertFalse(
            remotePublicProjectionMatches0425(
                remote = remote,
                expectedPublicProjectionHash = "public-v2:" + "b".repeat(64),
                snapshotRevision = "capacity-0425",
            ),
        )
        assertFalse(
            remotePublicProjectionMatches0425(
                remote = remote,
                expectedPublicProjectionHash = "",
                snapshotRevision = "capacity-0425",
            ),
        )
        assertFalse(
            remotePublicProjectionMatches0425(
                remote = remote.copy(capacityReliable = false),
                expectedPublicProjectionHash = exactHash,
                snapshotRevision = "capacity-0425",
            ),
        )
        assertFalse(
            remotePublicProjectionMatches0425(
                remote = remote,
                expectedPublicProjectionHash = exactHash,
                snapshotRevision = "another-capacity-revision",
            ),
        )
    }

    @Test
    fun publicSyncCarriesByteProofAndPreservesCanonicalTimezone() {
        val sync = java.io.File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt",
        ).readText()
        val api = java.io.File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt",
        ).readText()
        val attestation = java.io.File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PublicMirrorAttestation0411.kt",
        ).readText()

        assertTrue(sync.contains("PUBLIC_CAPACITY_REMOTE_REVISION_REPAIR_REQUIRED_0425"))
        assertTrue(sync.contains("expectedPublicProjectionHash0425 = expectedPublicProjectionHash0425()"))
        assertTrue(sync.contains("expectedPublicProjectionJson0434"))
        assertTrue(sync.contains("if (response.stale)"))
        assertTrue(attestation.contains("timezoneId = trip.publicTimezoneId0411.trim()"))
        assertFalse(attestation.contains("ZoneId.systemDefault"))
        assertTrue(api.contains("val publicProjectionHash: String = \"\""))
        assertTrue(api.contains("val expectedPublicProjectionHash0425: String = \"\""))
        assertTrue(api.contains("val expectedPublicProjectionJson0434: String = \"\""))
    }
}
