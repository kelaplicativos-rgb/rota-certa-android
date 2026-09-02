package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import kotlinx.coroutines.CancellationException

internal data class PublicMirrorAttestationBatch0411(
    val expected: Int = 0,
    val validated: Int = 0,
    val pending: Int = 0,
    val divergent: Int = 0,
    val invalidIdentity: Int = 0,
    val invalidLink: Int = 0,
    val staleRevision: Int = 0,
    val readbackFailures: Int = 0,
    val readbackLatencyMillis: Long = 0L,
) {
    val complete: Boolean
        get() = expected > 0 &&
            validated == expected &&
            pending == 0 &&
            divergent == 0 &&
            invalidIdentity == 0 &&
            invalidLink == 0 &&
            staleRevision == 0 &&
            readbackFailures == 0
}

internal object PublicMirrorAttestationCoordinator0411 {
    suspend fun attest(
        context: Context,
        store: TripStore,
        api: TripRemoteApi,
        trip: Trip,
        remote: DriverTripSyncState0402,
        force: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    ): PublicMirrorAttestationBatch0411 {
        val current = store.getTrip(trip.id) ?: return PublicMirrorAttestationBatch0411(
            expected = 1,
            pending = 1,
        )
        val expectedPublicationRevision = remote.publicationRevision.coerceAtLeast(0L)
        if (expectedPublicationRevision <= 0L) {
            store.recordPublicMirrorAttestation0411(
                canonicalTripId = current.id,
                expectedCanonicalRevision = current.canonicalRevision,
                expectedPublicationRevision = current.publicationRevision,
                state = PublicMirrorAttestationState0411.DIVERGENT,
                expectedHash = "",
                readbackHash = "",
                mismatchFields = listOf("revision"),
                reason = "PUBLICATION_REVISION_MISSING",
                readbackLatencyMillis = 0L,
            )
            return PublicMirrorAttestationBatch0411(expected = 1, divergent = 1, staleRevision = 1)
        }

        if (current.publicationRevision != expectedPublicationRevision) {
            store.recordPublicMirrorAttestation0411(
                canonicalTripId = current.id,
                expectedCanonicalRevision = current.canonicalRevision,
                expectedPublicationRevision = current.publicationRevision,
                state = PublicMirrorAttestationState0411.DIVERGENT,
                expectedHash = "",
                readbackHash = "",
                mismatchFields = listOf("revision"),
                reason = "LOCAL_PUBLICATION_REVISION_NOT_COMMITTED",
                readbackLatencyMillis = 0L,
            )
            return PublicMirrorAttestationBatch0411(expected = 1, divergent = 1, staleRevision = 1)
        }

        if (!force && current.publicMirrorAttestationCurrent0411()) {
            return PublicMirrorAttestationBatch0411(expected = 1, validated = 1)
        }

        val startedNs = System.nanoTime()
        val readback = try {
            api.readPublicTripProjection0411(remote.remoteTripId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val elapsed = (System.nanoTime() - startedNs) / 1_000_000L
            store.recordPublicMirrorAttestation0411(
                canonicalTripId = current.id,
                expectedCanonicalRevision = current.canonicalRevision,
                expectedPublicationRevision = current.publicationRevision,
                state = PublicMirrorAttestationState0411.PENDING,
                expectedHash = current.publicMirrorExpectedHash0411,
                readbackHash = "",
                mismatchFields = emptyList(),
                reason = "PUBLIC_READBACK_FAILED",
                readbackLatencyMillis = elapsed,
            )
            UnifiedDebugEventStore.record(
                "PUBLIC_MIRROR_ATTESTATION_0411",
                context.applicationContext.packageName,
                "canonicalTripId=${seatSyncDiagnosticKey(current.id)} blablaTripId=${current.blablaTripId.orEmpty()} canonicalRevision=${current.canonicalRevision} publicationRevision=${current.publicationRevision} result=PENDING readback=false retry=true durationMs=$elapsed error=${error::class.java.simpleName} message=${error.message.orEmpty().take(180)}",
            )
            return PublicMirrorAttestationBatch0411(
                expected = 1,
                pending = 1,
                readbackFailures = 1,
                readbackLatencyMillis = elapsed,
            )
        }
        val elapsed = (System.nanoTime() - startedNs) / 1_000_000L

        val readbackPublicUrl = readback.payload.publicUrl.trim()
        val expectedTrip = if (current.publicUrl.isNullOrBlank() && readbackPublicUrl.isNotBlank()) {
            current.copy(publicUrl = readbackPublicUrl)
        } else {
            current
        }
        val expected = canonicalPublicProjectionPayload0411(
            trip = expectedTrip,
            bookings = store.bookingsFor(current.id),
            publicationRevision = current.publicationRevision,
            nowMillis = nowMillis,
        )
        val decision = evaluatePublicMirrorReadback0411(expected, readback)

        store.recordPublicMirrorAttestation0411(
            canonicalTripId = current.id,
            expectedCanonicalRevision = current.canonicalRevision,
            expectedPublicationRevision = current.publicationRevision,
            state = decision.state,
            expectedHash = decision.expectedHash,
            readbackHash = decision.readbackHash,
            mismatchFields = decision.mismatchFields,
            reason = decision.reason,
            readbackLatencyMillis = elapsed,
            publicUrlFromReadback = readbackPublicUrl,
            nowMillis = nowMillis,
        )
        UnifiedDebugEventStore.record(
            "PUBLIC_MIRROR_ATTESTATION_0411",
            context.applicationContext.packageName,
            "canonicalTripId=${seatSyncDiagnosticKey(current.id)} blablaTripId=${current.blablaTripId.orEmpty()} profileUuidPresent=${!current.blablaProfileUuid.isNullOrBlank()} canonicalRevision=${current.canonicalRevision} publicationRevision=${current.publicationRevision} canonicalHash=${decision.expectedHash.takeLast(16)} publicHash=${decision.readbackHash.takeLast(16)} identity=${decision.identityValid} link=${decision.linkValid} revision=${decision.revisionValid} result=${decision.state.name} mismatch=${decision.mismatchFields.joinToString(",").take(240)} readback=true durationMs=$elapsed",
        )

        return if (decision.state == PublicMirrorAttestationState0411.VALIDATED) {
            PublicMirrorAttestationBatch0411(
                expected = 1,
                validated = 1,
                readbackLatencyMillis = elapsed,
            )
        } else {
            PublicMirrorAttestationBatch0411(
                expected = 1,
                divergent = 1,
                invalidIdentity = if (decision.identityValid) 0 else 1,
                invalidLink = if (decision.linkValid) 0 else 1,
                staleRevision = if (decision.revisionValid) 0 else 1,
                readbackLatencyMillis = elapsed,
            )
        }
    }
}
