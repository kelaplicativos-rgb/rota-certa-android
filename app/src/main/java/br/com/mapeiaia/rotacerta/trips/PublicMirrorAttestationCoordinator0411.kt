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
        if (!force && current.publicMirrorAttestationCurrent0411()) {
            return PublicMirrorAttestationBatch0411(expected = 1, validated = 1)
        }

        val traceId = current.publicationEventId
            .ifBlank { current.lastCollectionRunId }
            .ifBlank { "trip-" + seatSyncDiagnosticKey(current.id) }
            .take(120)
        val evidenceId = publicationEvidenceId0421(traceId, current.canonicalRevision)
        val resolvedCanonicalTripId0453 = remote.canonicalTripId.trim()
            .ifBlank { current.tripKey.trim().ifBlank { current.id } }
        val transportEvidence0421 = RemotePublicationEvidenceContext0421(
            evidenceId = evidenceId,
            traceId = traceId,
            canonicalTripId = resolvedCanonicalTripId0453,
            logicalRevision = current.canonicalRevision,
            transportRevision = current.publicationRevision,
            mutationId = "",
            idempotencyKey = "",
        )

        fun evidence(stage: String, status: String, reason: String, extra: String = "") {
            UnifiedDebugEventStore.record(
                "PUBLIC_EVIDENCE_0421",
                context.applicationContext.packageName,
                buildString {
                    append("evidenceId=").append(evidenceId)
                    append(" traceId=").append(traceId)
                    append(" correlationId=").append(traceId)
                    append(" stage=").append(stage)
                    append(" status=").append(status)
                    append(" reasonCode=").append(reason)
                    append(" canonicalTripId=").append(seatSyncDiagnosticKey(resolvedCanonicalTripId0453))
                    append(" logicalRevision=").append(current.canonicalRevision)
                    append(" transportRevision=").append(current.publicationRevision)
                    append(" canonicalStateHash=").append(current.canonicalStateHash.takeLast(24))
                    if (extra.isNotBlank()) append(' ').append(extra)
                },
            )
        }

        evidence(
            stage = "CANONICAL_READ",
            status = "OK",
            reason = "CANONICAL_SNAPSHOT_READY",
            extra = "publicIdentityExpected=" + remote.remoteTripId +
                " remoteLogicalRevisionHint=" + remote.canonicalRevision +
                " remoteTransportRevisionHint=" + remote.publicationRevision,
        )
        val publicIdentity0453 = remote.remoteTripId.trim()
        evidence(
            stage = "PUBLIC_IDENTITY_RESOLUTION",
            status = if (publicIdentity0453.isNotBlank()) "OK" else "FAILED",
            reason = if (publicIdentity0453.isNotBlank()) "PUBLIC_IDENTITY_RESOLVED" else "PUBLIC_IDENTITY_UNRESOLVED",
            extra = "publicIdentityExpected=" + publicIdentity0453 +
                " previousStage=SERVER_ACK nextStage=" + if (publicIdentity0453.isNotBlank()) "PUBLIC_READBACK_REQUEST" else "STOP",
        )
        if (publicIdentity0453.isBlank()) {
            store.recordPublicMirrorAttestation0411(
                canonicalTripId = current.id,
                expectedCanonicalRevision = current.canonicalRevision,
                expectedPublicationRevision = current.publicationRevision,
                state = PublicMirrorAttestationState0411.PENDING,
                expectedHash = current.publicMirrorExpectedHash0411,
                readbackHash = "",
                mismatchFields = listOf("publicIdentity"),
                reason = "PUBLIC_IDENTITY_UNRESOLVED",
                readbackLatencyMillis = 0L,
                evidenceId0421 = evidenceId,
                traceId0421 = traceId,
                failedStage0421 = "PUBLIC_IDENTITY_RESOLUTION",
            )
            evidence(
                stage = "ATTESTATION",
                status = "DENIED",
                reason = "PUBLIC_IDENTITY_UNRESOLVED",
                extra = "httpSendAttempted=false requestBytes=0",
            )
            return PublicMirrorAttestationBatch0411(expected = 1, pending = 1, invalidIdentity = 1)
        }
        evidence(
            stage = "PUBLIC_READBACK_REQUEST",
            status = "START",
            reason = "INDEPENDENT_PUBLIC_READBACK",
            extra = "publicIdentityExpected=" + publicIdentity0453,
        )

        val startedNs = System.nanoTime()
        val readback = try {
            api.readPublicTripProjection0411(
                remoteTripId = publicIdentity0453,
                evidence0421 = transportEvidence0421,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val elapsed = (System.nanoTime() - startedNs) / 1_000_000L
            val remoteError = generateSequence(error) { it.cause }
                .filterIsInstance<TripRemoteApiException>()
                .firstOrNull()
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
                publicIdentityFromReadback0421 = remote.remoteTripId,
                evidenceId0421 = evidenceId,
                traceId0421 = traceId,
                httpStatus0421 = remoteError?.httpStatus ?: 0,
                backendErrorCode0421 = remoteError?.backendErrorCode.orEmpty(),
                failedStage0421 = "PUBLIC_READBACK_RESPONSE",
                networkCallId0421 = remoteError?.networkCallId.orEmpty(),
                requestBytes0421 = remoteError?.requestBytes ?: 0,
                responseBytes0421 = remoteError?.responseBytes ?: 0,
                requestHash0421 = remoteError?.requestSha256.orEmpty(),
                responseHash0421 = remoteError?.responseSha256.orEmpty(),
                readbackAtMillis0421 = nowMillis,
            )
            evidence(
                stage = "PUBLIC_READBACK_RESPONSE",
                status = "FAILED",
                reason = remoteError?.backendErrorCode?.ifBlank { "PUBLIC_READBACK_FAILED" } ?: "PUBLIC_READBACK_FAILED",
                extra = "httpStatus=" + (remoteError?.httpStatus ?: 0) +
                    " requestBytes=" + (remoteError?.requestBytes ?: 0) +
                    " responseBytes=" + (remoteError?.responseBytes ?: 0) +
                    " requestHash=" + remoteError?.requestSha256.orEmpty() +
                    " responseHash=" + remoteError?.responseSha256.orEmpty() +
                    " durationMs=" + elapsed,
            )
            evidence(
                stage = "ATTESTATION",
                status = "DENIED",
                reason = "PUBLIC_READBACK_FAILED",
                extra = "nextStageExpected=PUBLIC_READBACK_REQUEST",
            )
            return PublicMirrorAttestationBatch0411(
                expected = 1,
                pending = 1,
                readbackFailures = 1,
                readbackLatencyMillis = elapsed,
            )
        }
        val elapsed = (System.nanoTime() - startedNs) / 1_000_000L
        evidence(
            stage = "PUBLIC_READBACK_RESPONSE",
            status = "OK",
            reason = "READBACK_RECEIVED",
            extra = "publicIdentityActual=" + readback.remoteTripId +
                " persistedAtMillis=" + readback.persistedAtMillis +
                " durationMs=" + elapsed,
        )

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
            canonicalTripId = resolvedCanonicalTripId0453,
        )
        val diff = compareCanonicalPublicBytes0421(expected, readback.payload)
        evidence(
            stage = "CANONICAL_SERIALIZATION",
            status = "OK",
            reason = "CANONICAL_JSON_UTF8",
            extra = "charset=UTF-8 canonicalization=public-trip-v2 expectedLength=" + diff.expectedLength +
                " expectedBytesHash=" + diff.expectedSha256,
        )
        evidence(
            stage = "PUBLIC_TRIP_LOCATE",
            status = if (readback.remoteTripId == remote.remoteTripId) "OK" else "MISMATCH",
            reason = if (readback.remoteTripId == remote.remoteTripId) "PUBLIC_TRIP_FOUND" else "PUBLIC_IDENTITY_CHANGED",
            extra = "publicIdentityExpected=" + remote.remoteTripId +
                " publicIdentityActual=" + readback.remoteTripId,
        )

        val decision = evaluatePublicMirrorReadback0411(expected, readback)
        evidence(
            stage = "IDENTITY_COMPARE",
            status = if (decision.identityValid) "MATCH" else "MISMATCH",
            reason = if (decision.identityValid) "IDENTITY_MATCH" else "IDENTITY_MISMATCH",
            extra = "publicIdentityExpected=" + remote.remoteTripId +
                " publicIdentityActual=" + readback.remoteTripId,
        )
        evidence(
            stage = "REVISION_COMPARE",
            status = if (decision.revisionValid) "MATCH" else "MISMATCH",
            reason = if (decision.revisionValid) "LOGICAL_REVISION_MATCH" else "STALE_LOGICAL_REVISION",
            extra = "logicalRevisionExpected=" + expected.canonicalRevision +
                " logicalRevisionActual=" + readback.payload.canonicalRevision +
                " transportRevisionSent=" + current.publicationRevision +
                " transportRevisionReadback=" + readback.payload.publicationRevision,
        )
        evidence(
            stage = "PUBLIC_AGENDA_VISIBILITY",
            status = if (readback.agendaVisible) "MATCH" else "MISMATCH",
            reason = readback.agendaVisibilityReason.ifBlank {
                if (readback.agendaVisible) "PUBLIC_AGENDA_VISIBLE" else "PUBLIC_AGENDA_NOT_VISIBLE_0466"
            },
            extra = "agendaVisible=" + readback.agendaVisible +
                " sameVisibilityPredicate=true",
        )
        val stateBytesMatch =
            diff.expectedLength == diff.actualLength &&
                diff.firstDifferentByteOffset == -1 &&
                diff.differentByteRanges.isEmpty() &&
                diff.fieldDiffs.isEmpty() &&
                decision.expectedHash == decision.readbackHash &&
                readback.publicProjectionHash == decision.readbackHash
        val publishedWithoutUrl0465 =
            readback.agendaVisible &&
                decision.state == PublicMirrorAttestationState0411.UNPROVEN &&
                decision.reason == "BLABLACAR_PUBLIC_URL_PENDING_AGENDA_VISIBLE_0466" &&
                decision.mismatchFields.isNotEmpty() &&
                decision.mismatchFields.all { it == "blablaPublicUrl" } &&
                stateBytesMatch

        evidence(
            stage = "STATE_COMPARE",
            status = if (stateBytesMatch) "MATCH" else "MISMATCH",
            reason = if (stateBytesMatch) "CANONICAL_STATE_MATCH" else "CANONICAL_STATE_MISMATCH",
            extra = "comparisonType=CANONICAL_JSON_UTF8 expectedHash=" + decision.expectedHash +
                " actualHash=" + decision.readbackHash +
                " expectedLength=" + diff.expectedLength +
                " actualLength=" + diff.actualLength +
                " firstDifferentByteOffset=" + diff.firstDifferentByteOffset +
                " differentByteRanges=" + diff.differentByteRanges.joinToString(",") +
                " mismatchFields=" + decision.mismatchFields.joinToString(",").take(240) +
                " fieldDiffPaths=" + diff.fieldDiffs.joinToString(",") { it.fieldPath }.take(240),
        )
        evidence(
            stage = "PUBLIC_URL_ENRICHMENT",
            status = when {
                decision.linkValid -> "MATCH"
                publishedWithoutUrl0465 -> "OPTIONAL_PENDING"
                else -> "FAILED"
            },
            reason = if (decision.linkValid) "BLABLACAR_PUBLIC_URL_RESOLVED" else decision.reason,
            extra = "linkRequired=" + expected.blablaTripId.isNotBlank() +
                " linkValid=" + decision.linkValid +
                " canonicalBindingRequired=true",
        )

        var reportError: Throwable? = null
        val serverReport = try {
            api.reportPublicTripAttestation0417(
                remoteTripId = remote.remoteTripId,
                request = DriverPublicAttestationRequest0417(
                    state = when {
                        decision.state == PublicMirrorAttestationState0411.VALIDATED -> "VERIFIED"
                        publishedWithoutUrl0465 -> "PUBLISHED"
                        else -> decision.state.name
                    },
                    canonicalRevision = current.canonicalRevision,
                    publicationRevision = current.publicationRevision,
                    canonicalStateHash = current.canonicalStateHash,
                    expectedHash = decision.expectedHash,
                    readbackHash = decision.readbackHash,
                    mismatchFields = decision.mismatchFields,
                    reason = decision.reason,
                    correlationId = traceId.take(100),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            reportError = error
            UnifiedDebugEventStore.record(
                "PUBLIC_MIRROR_ATTESTATION_REPORT_FAILED_0417",
                context.applicationContext.packageName,
                "evidenceId=$evidenceId traceId=$traceId canonicalTripId=" +
                    seatSyncDiagnosticKey(current.id) +
                    " transportRevision=" + current.publicationRevision +
                    " error=" + error::class.java.simpleName +
                    " message=" + error.message.orEmpty().take(180),
            )
            null
        }
        val serverConfirmed = decision.state == PublicMirrorAttestationState0411.VALIDATED &&
            serverPublicAttestationConfirmed0433(
                expectedCanonicalRevision = current.canonicalRevision,
                expectedPublicationRevision = current.publicationRevision,
                response = serverReport,
            )
        val finalDecision = if (
            decision.state == PublicMirrorAttestationState0411.VALIDATED &&
            !serverConfirmed
        ) {
            decision.copy(
                state = PublicMirrorAttestationState0411.PENDING,
                mismatchFields = (decision.mismatchFields + "serverAttestationCommit").distinct(),
                reason = if (reportError != null) {
                    "PUBLIC_ATTESTATION_REPORT_FAILED"
                } else {
                    "PUBLIC_ATTESTATION_NOT_COMMITTED"
                },
            )
        } else {
            decision
        }

        evidence(
            stage = "ATTESTATION_REPORT",
            status = when {
                decision.state != PublicMirrorAttestationState0411.VALIDATED -> "REPORTED"
                serverConfirmed -> "CONFIRMED"
                else -> "DENIED"
            },
            reason = finalDecision.reason,
            extra = "serverVerified=" + (serverReport?.verified == true) +
                " serverState=" + serverReport?.state.orEmpty() +
                " canonicalRevisionExpected=" + current.canonicalRevision +
                " canonicalRevisionServer=" + (serverReport?.canonicalRevision ?: 0L) +
                " transportRevisionExpected=" + current.publicationRevision +
                " transportRevisionServer=" + (serverReport?.publicationRevision ?: 0L) +
                " serverAckRequired=true",
        )
        if (
            decision.state == PublicMirrorAttestationState0411.VALIDATED &&
            !serverConfirmed
        ) {
            UnifiedDebugEventStore.record(
                "PUBLIC_ATTESTATION_NOT_COMMITTED_0433",
                context.applicationContext.packageName,
                "evidenceId=$evidenceId traceId=$traceId canonicalTripId=" +
                    seatSyncDiagnosticKey(current.id) +
                    " canonicalRevisionExpected=" + current.canonicalRevision +
                    " canonicalRevisionServer=" + (serverReport?.canonicalRevision ?: 0L) +
                    " transportRevisionExpected=" + current.publicationRevision +
                    " transportRevisionServer=" + (serverReport?.publicationRevision ?: 0L) +
                    " serverVerified=" + (serverReport?.verified == true) +
                    " reportFailed=" + (reportError != null) +
                    " publicVisibilityBlocked=true",
            )
        }

        store.recordPublicMirrorAttestation0411(
            canonicalTripId = current.id,
            expectedCanonicalRevision = current.canonicalRevision,
            expectedPublicationRevision = current.publicationRevision,
            state = finalDecision.state,
            expectedHash = finalDecision.expectedHash,
            readbackHash = finalDecision.readbackHash,
            mismatchFields = finalDecision.mismatchFields,
            reason = finalDecision.reason,
            readbackLatencyMillis = elapsed,
            publicUrlFromReadback = readbackPublicUrl,
            publicIdentityFromReadback0421 = readback.remoteTripId,
            readbackCanonicalRevision0421 = readback.payload.canonicalRevision,
            readbackPublicationRevision0421 = readback.payload.publicationRevision,
            evidenceId0421 = evidenceId,
            traceId0421 = traceId,
            expectedBytes0421 = diff.expectedLength,
            actualBytes0421 = diff.actualLength,
            firstDifferentByteOffset0421 = diff.firstDifferentByteOffset,
            differentByteRanges0421 = diff.differentByteRanges,
            fieldDiffs0422 = diff.fieldDiffs.map(::publicProjectionFieldDiffJson0422),
            failedStage0421 = when {
                !finalDecision.identityValid -> "IDENTITY_COMPARE"
                !finalDecision.revisionValid -> "REVISION_COMPARE"
                !readback.agendaVisible -> "PUBLIC_AGENDA_VISIBILITY"
                publishedWithoutUrl0465 -> ""
                !finalDecision.linkValid -> "PUBLIC_URL_ENRICHMENT"
                finalDecision.state == PublicMirrorAttestationState0411.PENDING -> "ATTESTATION_REPORT"
                finalDecision.state != PublicMirrorAttestationState0411.VALIDATED -> "STATE_COMPARE"
                else -> ""
            },
            readbackAtMillis0421 = nowMillis,
            nowMillis = nowMillis,
        )

        evidence(
            stage = "ATTESTATION",
            status = when {
                finalDecision.state == PublicMirrorAttestationState0411.VALIDATED -> "CONFIRMED"
                publishedWithoutUrl0465 -> "PUBLISHED"
                else -> "DENIED"
            },
            reason = finalDecision.reason,
            extra = "agendaMatch=" + (finalDecision.state == PublicMirrorAttestationState0411.VALIDATED) +
                " agendaVisible=" + readback.agendaVisible +
                " linkValid=" + finalDecision.linkValid +
                " serverAck=" + serverConfirmed +
                " attestationFresh=true durationMs=" + elapsed,
        )
        UnifiedDebugEventStore.record(
            "PUBLIC_MIRROR_ATTESTATION_0411",
            context.applicationContext.packageName,
            "evidenceId=$evidenceId traceId=$traceId canonicalTripId=" +
                seatSyncDiagnosticKey(current.id) +
                " blablaTripId=" + current.blablaTripId.orEmpty() +
                " canonicalRevision=" + current.canonicalRevision +
                " publicCanonicalRevision=" + readback.payload.canonicalRevision +
                " transportRevisionLocal=" + current.publicationRevision +
                " transportRevisionPublic=" + readback.payload.publicationRevision +
                " canonicalHash=" + finalDecision.expectedHash.takeLast(24) +
                " publicHash=" + finalDecision.readbackHash.takeLast(24) +
                " identity=" + finalDecision.identityValid +
                " agendaVisible=" + readback.agendaVisible +
                " link=" + finalDecision.linkValid +
                " revision=" + finalDecision.revisionValid +
                " serverAck=" + serverConfirmed +
                " result=" + finalDecision.state.name +
                " mismatch=" + finalDecision.mismatchFields.joinToString(",").take(240) +
                " readback=true durationMs=" + elapsed,
        )

        return when (finalDecision.state) {
            PublicMirrorAttestationState0411.VALIDATED -> PublicMirrorAttestationBatch0411(
                expected = 1,
                validated = 1,
                invalidLink = if (finalDecision.linkValid) 0 else 1,
                readbackLatencyMillis = elapsed,
            )
            PublicMirrorAttestationState0411.PENDING -> PublicMirrorAttestationBatch0411(
                expected = 1,
                pending = 1,
                readbackLatencyMillis = elapsed,
            )
            PublicMirrorAttestationState0411.UNPROVEN -> PublicMirrorAttestationBatch0411(
                expected = 1,
                invalidLink = if (finalDecision.linkValid) 0 else 1,
                readbackLatencyMillis = elapsed,
            )
            else -> PublicMirrorAttestationBatch0411(
                expected = 1,
                divergent = 1,
                invalidIdentity = if (finalDecision.identityValid) 0 else 1,
                invalidLink = if (finalDecision.linkValid) 0 else 1,
                staleRevision = if (finalDecision.revisionValid) 0 else 1,
                readbackLatencyMillis = elapsed,
            )
        }
    }
}
