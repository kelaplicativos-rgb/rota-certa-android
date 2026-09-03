package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.security.MessageDigest
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal enum class TripPublicationOperation0387 { UPSERT_LOCAL, UPSERT_EXTERNAL, TOMBSTONE }

@Serializable
internal enum class TripPublicationStatus0387 {
    PENDING, PROCESSING, DELIVERED, FAILED_RETRYABLE, FAILED_FINAL, SUPERSEDED,
}

@Serializable
internal data class TripPublicationSnapshot0387(
    val trip: Trip? = null,
    val bookings: List<Booking> = emptyList(),
    val externalTrip: BlaBlaCollectorTrip? = null,
    val externalAccountId: String = "",
    val configuredRotaCertaSeatAllocation: Int = 0,
    val seatAllocationVersion: Long = 0L,
    val semanticSignature: String,
)

@Serializable
internal data class TripPublicationOutboxEvent0387(
    val id: String,
    val tenantId: String,
    val canonicalTripId: String,
    val revision: Long,
    val operation: TripPublicationOperation0387,
    val mutationType: String,
    val source: String,
    val destination: String = "PUBLIC_AGENDA",
    val snapshot: TripPublicationSnapshot0387,
    val status: TripPublicationStatus0387 = TripPublicationStatus0387.PENDING,
    val attempts: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val lastError: String = "",
    val nextAttemptAtMillis: Long = 0L,
)

internal fun shouldDeduplicatePublicationEvent0410(
    latest: TripPublicationOutboxEvent0387?,
    operation: TripPublicationOperation0387,
    snapshot: TripPublicationSnapshot0387,
    @Suppress("UNUSED_PARAMETER") remoteProjectionDivergenceObserved: Boolean = false,
): Boolean {
    if (latest == null || latest.operation != operation) return false
    if (latest.snapshot.semanticSignature != snapshot.semanticSignature) return false
    if (latest.snapshot.seatAllocationVersion != snapshot.seatAllocationVersion) return false
    // Same logical snapshot never creates a new revision. Projection repair replays the
    // delivered event in enqueue(); deterministic final failures stay final until the
    // canonical snapshot itself changes.
    return true
}

internal class TripPublicationOutbox0387(context: Context) {
    private val appContext = context.applicationContext
    private val tenantScope = RotaCertaTenantRegistry(appContext).activeScope()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val eventsKey = tenantScope.key(KEY_EVENTS)
    private val revisionsKey = tenantScope.key(KEY_REVISIONS)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val tenantId: String get() = tenantScope.tenantId

    fun ensureRevisionAtLeast(canonicalTripId: String, revision: Long) {
        if (canonicalTripId.isBlank() || revision <= 0L) return
        synchronized(LOCK) {
            val revisions = readRevisions().toMutableMap()
            if ((revisions[canonicalTripId] ?: 0L) < revision) {
                revisions[canonicalTripId] = revision
                require(prefs.edit().putString(revisionsKey, json.encodeToString(revisions)).commit()) {
                    "Falha ao persistir revisão canônica."
                }
            }
        }
    }

    fun enqueue(
        canonicalTripId: String,
        operation: TripPublicationOperation0387,
        mutationType: String,
        source: String,
        snapshot: TripPublicationSnapshot0387,
        remoteProjectionDivergenceObserved: Boolean = false,
    ): TripPublicationOutboxEvent0387? {
        require(canonicalTripId.isNotBlank()) { "canonicalTripId obrigatório." }
        require(snapshot.semanticSignature.isNotBlank()) { "Assinatura semântica obrigatória." }
        synchronized(LOCK) {
            val events = recoverInterrupted(readEvents()).toMutableList()
            val latest = events.filter { it.canonicalTripId == canonicalTripId }.maxByOrNull { it.revision }
            val sameLogicalSnapshot = latest != null &&
                latest.operation == operation &&
                latest.snapshot.semanticSignature == snapshot.semanticSignature &&
                latest.snapshot.seatAllocationVersion == snapshot.seatAllocationVersion
            if (
                remoteProjectionDivergenceObserved &&
                sameLogicalSnapshot &&
                latest?.status == TripPublicationStatus0387.DELIVERED
            ) {
                val now = System.currentTimeMillis()
                val replay = latest.copy(
                    status = TripPublicationStatus0387.PENDING,
                    attempts = 0,
                    updatedAtMillis = now,
                    lastError = "projection_replay_same_logical_revision",
                    nextAttemptAtMillis = 0L,
                )
                val normalized = events.map { event -> if (event.id == replay.id) replay else event }
                writeEvents(normalized)
                return replay
            }
            if (
                shouldDeduplicatePublicationEvent0410(
                    latest = latest,
                    operation = operation,
                    snapshot = snapshot,
                    remoteProjectionDivergenceObserved = remoteProjectionDivergenceObserved,
                )
            ) return null

            val revisions = readRevisions().toMutableMap()
            val nextRevision = maxOf(revisions[canonicalTripId] ?: 0L, latest?.revision ?: 0L) + 1L
            val now = System.currentTimeMillis()
            val event = TripPublicationOutboxEvent0387(
                id = publicationEventId0387(tenantScope.tenantId, canonicalTripId, nextRevision),
                tenantId = tenantScope.tenantId,
                canonicalTripId = canonicalTripId,
                revision = nextRevision,
                operation = operation,
                mutationType = mutationType.take(80),
                source = source.take(80),
                snapshot = snapshot,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            revisions[canonicalTripId] = nextRevision
            events += event
            require(
                prefs.edit()
                    .putString(eventsKey, json.encodeToString(compact(events)))
                    .putString(revisionsKey, json.encodeToString(revisions))
                    .commit(),
            ) { "Falha ao persistir evento da outbox." }
            return event
        }
    }

    fun recordDeliveredAtRevision(
        canonicalTripId: String,
        revision: Long,
        operation: TripPublicationOperation0387,
        mutationType: String,
        source: String,
        snapshot: TripPublicationSnapshot0387,
    ): TripPublicationOutboxEvent0387? {
        if (canonicalTripId.isBlank() || revision <= 0L || snapshot.semanticSignature.isBlank()) return null
        synchronized(LOCK) {
            val now = System.currentTimeMillis()
            val events = recoverInterrupted(readEvents()).toMutableList()
            val revisions = readRevisions().toMutableMap()
            val eventId = publicationEventId0387(tenantScope.tenantId, canonicalTripId, revision)
            val authoritative = TripPublicationOutboxEvent0387(
                id = eventId,
                tenantId = tenantScope.tenantId,
                canonicalTripId = canonicalTripId,
                revision = revision,
                operation = operation,
                mutationType = mutationType.take(80),
                source = source.take(80),
                snapshot = snapshot,
                status = TripPublicationStatus0387.DELIVERED,
                attempts = 1,
                createdAtMillis = events.firstOrNull { it.id == eventId }?.createdAtMillis ?: now,
                updatedAtMillis = now,
                lastError = "",
                nextAttemptAtMillis = 0L,
            )
            val normalized = events.map { event ->
                when {
                    event.id == eventId -> authoritative
                    event.canonicalTripId == canonicalTripId &&
                        event.revision < revision &&
                        event.status in setOf(
                            TripPublicationStatus0387.PENDING,
                            TripPublicationStatus0387.PROCESSING,
                            TripPublicationStatus0387.FAILED_RETRYABLE,
                        ) -> event.copy(
                            status = TripPublicationStatus0387.SUPERSEDED,
                            updatedAtMillis = now,
                            lastError = "remote_applied_revision_$revision",
                            nextAttemptAtMillis = 0L,
                        )
                    else -> event
                }
            }.toMutableList()
            if (normalized.none { it.id == eventId }) normalized += authoritative
            revisions[canonicalTripId] = maxOf(revisions[canonicalTripId] ?: 0L, revision)
            require(
                prefs.edit()
                    .putString(eventsKey, json.encodeToString(compact(normalized)))
                    .putString(revisionsKey, json.encodeToString(revisions))
                    .commit(),
            ) { "Falha ao registrar revisão remota já aplicada." }
            return authoritative
        }
    }

    fun pending(nowMillis: Long = System.currentTimeMillis(), limit: Int = 32): List<TripPublicationOutboxEvent0387> =
        synchronized(LOCK) {
            val recovered = recoverInterrupted(readEvents())
            val newestByTrip = recovered.groupBy(TripPublicationOutboxEvent0387::canonicalTripId)
                .mapValues { (_, values) -> values.maxOf(TripPublicationOutboxEvent0387::revision) }
            var changed = false
            val normalized = recovered.map { event ->
                val newest = newestByTrip[event.canonicalTripId] ?: event.revision
                if (
                    event.revision < newest &&
                    event.status in setOf(
                        TripPublicationStatus0387.PENDING,
                        TripPublicationStatus0387.PROCESSING,
                        TripPublicationStatus0387.FAILED_RETRYABLE,
                    )
                ) {
                    changed = true
                    event.copy(
                        status = TripPublicationStatus0387.SUPERSEDED,
                        updatedAtMillis = nowMillis,
                        lastError = "superseded_by_revision_$newest",
                    )
                } else event
            }
            if (changed || normalized != recovered) writeEvents(normalized)
            normalized.asSequence()
                .filter {
                    it.status in setOf(TripPublicationStatus0387.PENDING, TripPublicationStatus0387.FAILED_RETRYABLE) &&
                        it.nextAttemptAtMillis <= nowMillis
                }
                .sortedWith(compareBy(TripPublicationOutboxEvent0387::createdAtMillis, TripPublicationOutboxEvent0387::revision))
                .take(limit.coerceIn(1, 128))
                .toList()
        }

    fun markProcessing(id: String): TripPublicationOutboxEvent0387? = update(id) { event ->
        event.copy(
            status = TripPublicationStatus0387.PROCESSING,
            attempts = event.attempts + 1,
            updatedAtMillis = System.currentTimeMillis(),
            lastError = "",
        )
    }

    fun markDelivered(id: String) {
        update(id) { event ->
            event.copy(
                status = TripPublicationStatus0387.DELIVERED,
                updatedAtMillis = System.currentTimeMillis(),
                lastError = "",
                nextAttemptAtMillis = 0L,
            )
        }
    }

    fun markFailure(id: String, error: Throwable, retryable: Boolean) {
        update(id) { event ->
            val now = System.currentTimeMillis()
            event.copy(
                status = if (retryable) TripPublicationStatus0387.FAILED_RETRYABLE else TripPublicationStatus0387.FAILED_FINAL,
                updatedAtMillis = now,
                lastError = failureSummary0387(error),
                nextAttemptAtMillis = if (retryable) now + retryDelayMillis(event.attempts.coerceAtLeast(1)) else 0L,
            )
        }
    }

    fun rebase(id: String, remoteRevision: Long): TripPublicationOutboxEvent0387? = synchronized(LOCK) {
        val events = readEvents().toMutableList()
        val index = events.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized null
        val target = events[index]
        val revisions = readRevisions().toMutableMap()
        val newest = events.filter { it.canonicalTripId == target.canonicalTripId }.maxByOrNull { it.revision }
        val now = System.currentTimeMillis()
        if (newest != null && newest.id != target.id && newest.revision > target.revision) {
            events[index] = target.copy(
                status = TripPublicationStatus0387.SUPERSEDED,
                updatedAtMillis = now,
                lastError = "superseded_during_remote_rebase_${newest.revision}",
            )
            revisions[target.canonicalTripId] = maxOf(
                revisions[target.canonicalTripId] ?: 0L,
                remoteRevision,
                newest.revision,
            )
            require(
                prefs.edit()
                    .putString(eventsKey, json.encodeToString(compact(events)))
                    .putString(revisionsKey, json.encodeToString(revisions))
                    .commit(),
            ) { "Falha ao persistir rebase superseded." }
            return@synchronized null
        }

        val nextRevision = maxOf(
            remoteRevision,
            revisions[target.canonicalTripId] ?: 0L,
            newest?.revision ?: 0L,
        ) + 1L
        val rebased = target.copy(
            id = publicationEventId0387(target.tenantId, target.canonicalTripId, nextRevision),
            revision = nextRevision,
            status = TripPublicationStatus0387.PENDING,
            attempts = 0,
            createdAtMillis = now,
            updatedAtMillis = now,
            lastError = "",
            nextAttemptAtMillis = 0L,
        )
        events[index] = target.copy(
            status = TripPublicationStatus0387.SUPERSEDED,
            updatedAtMillis = now,
            lastError = "rebased_from_${target.revision}_to_${nextRevision}_remote_$remoteRevision",
        )
        events += rebased
        revisions[target.canonicalTripId] = nextRevision
        require(
            prefs.edit()
                .putString(eventsKey, json.encodeToString(compact(events)))
                .putString(revisionsKey, json.encodeToString(revisions))
                .commit(),
        ) { "Falha ao persistir rebase da outbox." }
        rebased
    }

    internal fun snapshot(): List<TripPublicationOutboxEvent0387> = synchronized(LOCK) { readEvents() }

    private fun update(
        id: String,
        transform: (TripPublicationOutboxEvent0387) -> TripPublicationOutboxEvent0387,
    ): TripPublicationOutboxEvent0387? = synchronized(LOCK) {
        var result: TripPublicationOutboxEvent0387? = null
        val updated = readEvents().map { event ->
            if (event.id == id) transform(event).also { result = it } else event
        }
        writeEvents(updated)
        result
    }

    private fun recoverInterrupted(events: List<TripPublicationOutboxEvent0387>): List<TripPublicationOutboxEvent0387> {
        val now = System.currentTimeMillis()
        return events.map { event ->
            if (event.status == TripPublicationStatus0387.PROCESSING && now - event.updatedAtMillis > PROCESSING_LEASE_MILLIS) {
                event.copy(
                    status = TripPublicationStatus0387.FAILED_RETRYABLE,
                    updatedAtMillis = now,
                    lastError = "processing_lease_expired",
                    nextAttemptAtMillis = now,
                )
            } else event
        }
    }

    private fun compact(events: List<TripPublicationOutboxEvent0387>): List<TripPublicationOutboxEvent0387> {
        if (events.size <= MAX_EVENTS) return events
        val durable = events.filter {
            it.status !in setOf(TripPublicationStatus0387.DELIVERED, TripPublicationStatus0387.SUPERSEDED)
        }
        val room = (MAX_EVENTS - durable.size).coerceAtLeast(0)
        val history = events.asReversed()
            .filter { it.status in setOf(TripPublicationStatus0387.DELIVERED, TripPublicationStatus0387.SUPERSEDED) }
            .take(room).asReversed()
        return (durable + history).sortedBy(TripPublicationOutboxEvent0387::createdAtMillis).takeLast(MAX_EVENTS)
    }

    private fun readEvents(): List<TripPublicationOutboxEvent0387> = runCatching {
        json.decodeFromString<List<TripPublicationOutboxEvent0387>>(prefs.getString(eventsKey, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    private fun readRevisions(): Map<String, Long> = runCatching {
        json.decodeFromString<Map<String, Long>>(prefs.getString(revisionsKey, "{}") ?: "{}")
    }.getOrDefault(emptyMap())

    private fun writeEvents(events: List<TripPublicationOutboxEvent0387>) {
        require(prefs.edit().putString(eventsKey, json.encodeToString(compact(events))).commit()) {
            "Falha ao atualizar outbox."
        }
    }

    companion object {
        private const val PREFS = "rota_certa_trip_publication_outbox_0387"
        private const val KEY_EVENTS = "events"
        private const val KEY_REVISIONS = "revisions"
        private const val PROCESSING_LEASE_MILLIS = 2L * 60L * 1000L
        private const val MAX_EVENTS = 512
        private val LOCK = Any()
    }
}

internal class TripMutationCoordinator0387(
    context: Context,
    private val store: TripStore,
) {
    private val appContext = context.applicationContext
    private val outbox = TripPublicationOutbox0387(appContext)

    fun recordLocalMutation(
        canonicalTripId: String,
        mutationType: String,
        source: String,
        configuredRotaCertaSeatAllocation: Int? = null,
        reconcileBookingInventory: Boolean = true,
        remoteProjectionDivergenceObserved: Boolean = false,
    ): TripPublicationOutboxEvent0387? {
        if (canonicalTripId.isBlank() || !store.onlineSettings().configured) return null
        if (reconcileBookingInventory) store.reconcileBookingDerivedInventory(setOf(canonicalTripId))
        val original = store.getTrip(canonicalTripId) ?: return null
        if (original.status == TripStatus.DRAFT) return null
        if (!original.isCanonicalLocalPublishSource()) return null
        if (original.status == TripStatus.CANCELLED) {
            return recordTombstone(
                canonicalTripId = canonicalTripId,
                mutationType = mutationType,
                source = source,
            )
        }
        val bookings = store.bookingsFor(canonicalTripId)
        val allocation = configuredRotaCertaSeatAllocation?.takeIf { it in 0..999 }
            ?: original.rotaCertaSeatAllocation?.takeIf { it in 0..999 } ?: 0
        val allocated = original.copy(rotaCertaSeatAllocation = allocation)
        val publicTrip = allocated.copy(capacity = operationalInventoryCapacity(allocated, bookings))
        val signature = PublicAgendaAutoSync0300.localCapacitySnapshotRevision(publicTrip, bookings, allocation) +
            "|state:" + publicTrip.canonicalStateHash
        outbox.ensureRevisionAtLeast(canonicalTripId, (publicTrip.canonicalRevision - 1L).coerceAtLeast(0L))
        return outbox.enqueue(
            canonicalTripId = canonicalTripId,
            operation = TripPublicationOperation0387.UPSERT_LOCAL,
            mutationType = mutationType,
            source = source,
            snapshot = TripPublicationSnapshot0387(
                trip = publicTrip,
                bookings = bookings,
                seatAllocationVersion = publicTrip.seatAllocationVersionUsed,
                semanticSignature = signature,
            ),
            remoteProjectionDivergenceObserved = remoteProjectionDivergenceObserved,
        )?.also { event ->
            recordEvent(
                "TRIP_MUTATION_OUTBOX_ENQUEUED",
                event,
                "previousRevision=${event.revision - 1} resultingRevision=${event.revision}",
            )
        }
    }

    fun recordRemoteAppliedLocal(
        canonicalTripId: String,
        revision: Long,
        mutationType: String,
        source: String,
        reconcileBookingInventory: Boolean = true,
    ): TripPublicationOutboxEvent0387? {
        if (canonicalTripId.isBlank() || revision <= 0L || !store.onlineSettings().configured) return null
        if (reconcileBookingInventory) store.reconcileBookingDerivedInventory(setOf(canonicalTripId))
        var original = store.getTrip(canonicalTripId) ?: return null
        if (!original.isCanonicalLocalPublishSource()) return null
        if (revision > original.canonicalRevision) {
            original = store.saveTrip(original.copy(canonicalRevision = revision))
        }
        val bookings = store.bookingsFor(canonicalTripId)
        val allocation = original.rotaCertaSeatAllocation?.takeIf { it in 0..999 } ?: 0
        val publicTrip = original.copy(
            capacity = operationalInventoryCapacity(original, bookings),
        )
        val signature = PublicAgendaAutoSync0300.localCapacitySnapshotRevision(
            publicTrip,
            bookings,
            allocation,
        )
        val delivered = outbox.recordDeliveredAtRevision(
            canonicalTripId = canonicalTripId,
            revision = revision,
            operation = if (original.status == TripStatus.CANCELLED) {
                TripPublicationOperation0387.TOMBSTONE
            } else {
                TripPublicationOperation0387.UPSERT_LOCAL
            },
            mutationType = mutationType,
            source = source,
            snapshot = TripPublicationSnapshot0387(
                trip = publicTrip,
                bookings = bookings,
                seatAllocationVersion = publicTrip.seatAllocationVersionUsed,
                semanticSignature = if (original.status == TripStatus.CANCELLED) {
                    "tombstone-v1:" + sha256TripPublication0387(
                        listOf(canonicalTripId, original.remoteId.orEmpty(), original.publicToken, "CANCELLED").joinToString("|"),
                    )
                } else {
                    signature
                },
            ),
        )
        if (delivered != null) {
            store.recordPublicationCommitted0411(
                canonicalTripId = canonicalTripId,
                publicationRevision = revision,
                publicationEventId = delivered.id,
                tombstone = original.status == TripStatus.CANCELLED,
            )
            recordEvent(
                "TRIP_MUTATION_REMOTE_APPLIED",
                delivered,
                "publicationResult=already_applied_remote resultingRevision=${delivered.revision}",
            )
        }
        return delivered
    }

    fun recordExternalManualMutation(
        sourceTrip: BlaBlaCollectorTrip,
        configuredRotaCertaSeatAllocation: Int? = null,
        mutationType: String = "BLABLACAR_MANUAL_CARD_SYNC",
    ): TripPublicationOutboxEvent0387? = recordExternalMutation(
        sourceTrip = sourceTrip,
        configuredRotaCertaSeatAllocation = configuredRotaCertaSeatAllocation,
        mutationType = mutationType,
        eventSource = "BLABLACAR_MANUAL_CARD",
    )

    fun recordExternalCollectionMutation(
        sourceTrip: BlaBlaCollectorTrip,
        configuredRotaCertaSeatAllocation: Int,
        seatAllocationVersion: Long = 0L,
        remoteProjectionDivergenceObserved: Boolean = false,
    ): TripPublicationOutboxEvent0387? = recordExternalMutation(
        sourceTrip = sourceTrip,
        configuredRotaCertaSeatAllocation = configuredRotaCertaSeatAllocation,
        seatAllocationVersion = seatAllocationVersion,
        mutationType = if (remoteProjectionDivergenceObserved) {
            "CANONICAL_REMOTE_PROJECTION_REPAIR"
        } else {
            "BLABLACAR_EXTERNAL_COLLECTION_DELTA"
        },
        eventSource = if (remoteProjectionDivergenceObserved) "PROJECTION_RECONCILER" else "EXTERNAL_COLLECTION",
        remoteProjectionDivergenceObserved = remoteProjectionDivergenceObserved,
    )

    fun recordExternalTenantMutation(
        sourceTrip: BlaBlaCollectorTrip,
        configuredRotaCertaSeatAllocation: Int,
        seatAllocationVersion: Long = 0L,
        mutationType: String = "TENANT_SEAT_ALLOCATION_CHANGED",
    ): TripPublicationOutboxEvent0387? = recordExternalMutation(
        sourceTrip = sourceTrip,
        configuredRotaCertaSeatAllocation = configuredRotaCertaSeatAllocation,
        seatAllocationVersion = seatAllocationVersion,
        mutationType = mutationType,
        eventSource = "ROTA_CERTA_SETTINGS",
    )

    private fun recordExternalMutation(
        sourceTrip: BlaBlaCollectorTrip,
        configuredRotaCertaSeatAllocation: Int?,
        seatAllocationVersion: Long? = null,
        mutationType: String,
        eventSource: String,
        remoteProjectionDivergenceObserved: Boolean = false,
    ): TripPublicationOutboxEvent0387? {
        if (!store.onlineSettings().configured) return null
        val profileUuid = sourceTrip.profile_uuid.trim()
        val tripId = sourceTrip.trip_id?.trim().orEmpty()
        if (sourceTrip.identity_conflict || profileUuid.isBlank() || tripId.isBlank()) {
            UnifiedDebugEventStore.record(
                "TRIP_MUTATION_EXTERNAL_IDENTITY_BLOCKED",
                appContext.packageName,
                "tenantId=${outbox.tenantId} mutationType=$mutationType profileUuidPresent=${profileUuid.isNotBlank()} tripIdPresent=${tripId.isNotBlank()} identityConflict=${sourceTrip.identity_conflict}",
            )
            return null
        }
        val accounts = BlaBlaDynamicAccountRegistry(appContext).list().filter {
            it.profileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true
        }
        if (accounts.size != 1) {
            UnifiedDebugEventStore.record(
                "TRIP_MUTATION_EXTERNAL_ACCOUNT_BLOCKED",
                appContext.packageName,
                "tenantId=${outbox.tenantId} mutationType=$mutationType profileUuidPresent=true tripIdPresent=true accountMatches=${accounts.size}",
            )
            return null
        }
        val accountId = accounts.single().id
        val existingBinding = store.publicExternalBindingForStrongIdentity(profileUuid, tripId)
        if (seatAllocationVersion != null && existingBinding != null && existingBinding.seatAllocationVersionUsed > seatAllocationVersion) {
            UnifiedDebugEventStore.record(
                "TRIP_MUTATION_EXTERNAL_CONFIG_STALE",
                appContext.packageName,
                "tenantId=" + outbox.tenantId + " internalTripId=" + seatSyncDiagnosticKey(existingBinding.bookingTripId) +
                    " result=SKIP_STALE_REVISION configVersion=" + seatAllocationVersion +
                    " currentConfigVersion=" + existingBinding.seatAllocationVersionUsed,
            )
            return null
        }
        val canonicalExternalTrip = store.trips().firstOrNull { trip ->
            resolvedTripRecordOrigin(trip) == TripRecordOrigin.EXTERNAL_BACKING &&
                trip.blablaProfileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true &&
                trip.blablaTripId?.trim() == tripId
        }
        val canonicalTripId = existingBinding?.bookingTripId?.takeIf(String::isNotBlank)
            ?: canonicalExternalTrip?.id?.takeIf(String::isNotBlank)
            ?: strongExternalCanonicalTripId0387(outbox.tenantId, accountId, profileUuid, tripId)
        val allocation = configuredRotaCertaSeatAllocation?.takeIf { it in 0..999 }
            ?: canonicalExternalTrip?.rotaCertaSeatAllocation?.takeIf { it in 0..999 }
            ?: 0
        val signature = PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(sourceTrip, allocation) +
            "|state:" + canonicalExternalTrip?.canonicalStateHash.orEmpty()
        return outbox.enqueue(
            canonicalTripId = canonicalTripId,
            operation = TripPublicationOperation0387.UPSERT_EXTERNAL,
            mutationType = mutationType,
            source = eventSource,
            snapshot = TripPublicationSnapshot0387(
                trip = canonicalExternalTrip,
                externalTrip = sourceTrip,
                externalAccountId = accountId,
                configuredRotaCertaSeatAllocation = allocation,
                seatAllocationVersion = seatAllocationVersion?.coerceAtLeast(0L)
                    ?: existingBinding?.seatAllocationVersionUsed?.coerceAtLeast(0L) ?: 0L,
                semanticSignature = signature,
            ),
            remoteProjectionDivergenceObserved = remoteProjectionDivergenceObserved,
        )?.also { event ->
            recordEvent(
                "TRIP_MUTATION_OUTBOX_ENQUEUED",
                event,
                "previousRevision=${event.revision - 1} resultingRevision=${event.revision} externalAccountId=${seatSyncDiagnosticKey(accountId)} profileUuidPresent=true tripIdPresent=true",
            )
        }
    }

    fun recordTombstone(
        canonicalTripId: String,
        mutationType: String = "TIMELINE_OPERATIONAL_CLEAR",
        source: String = "TIMELINE",
    ): TripPublicationOutboxEvent0387? {
        if (!store.onlineSettings().configured) return null
        val trip = store.getTrip(canonicalTripId) ?: return null
        val bookings = store.bookingsFor(canonicalTripId)
        val signature = "tombstone-v1:" + sha256TripPublication0387(
            listOf(canonicalTripId, trip.remoteId.orEmpty(), trip.publicToken, "CANCELLED").joinToString("|"),
        )
        return outbox.enqueue(
            canonicalTripId = canonicalTripId,
            operation = TripPublicationOperation0387.TOMBSTONE,
            mutationType = mutationType,
            source = source,
            snapshot = TripPublicationSnapshot0387(
                trip = trip,
                bookings = bookings,
                seatAllocationVersion = trip.seatAllocationVersionUsed,
                semanticSignature = signature,
            ),
        )?.also { event ->
            recordEvent("TRIP_MUTATION_TOMBSTONE_ENQUEUED", event, "historyPreserved=true blablaMutation=false")
        }
    }

    fun recordExternalTombstone(
        binding: PublicExternalTripBinding,
        mutationType: String = "TIMELINE_OPERATIONAL_CLEAR",
        source: String = "TIMELINE",
        outboxCanonicalTripId: String? = null,
    ): TripPublicationOutboxEvent0387? {
        if (!store.onlineSettings().configured) return null
        val profileUuid = binding.profileUuid.trim()
        val tripId = binding.blablaTripId.trim()
        if (profileUuid.isBlank() || tripId.isBlank()) return null
        val accounts = BlaBlaDynamicAccountRegistry(appContext).list().filter {
            it.profileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true
        }
        if (accounts.size != 1) return null
        val canonicalTripId = outboxCanonicalTripId?.takeIf(String::isNotBlank)
            ?: binding.bookingTripId.takeIf(String::isNotBlank)
            ?: strongExternalCanonicalTripId0387(
                outbox.tenantId,
                accounts.single().id,
                profileUuid,
                tripId,
            )
        val trip = binding.asTrip().copy(
            status = TripStatus.CANCELLED,
            publicBookingEnabled = false,
        )
        val signature = "tombstone-v1:" + sha256TripPublication0387(
            listOf(canonicalTripId, binding.remoteTripId, binding.publicToken, "CANCELLED").joinToString("|"),
        )
        return outbox.enqueue(
            canonicalTripId = canonicalTripId,
            operation = TripPublicationOperation0387.TOMBSTONE,
            mutationType = mutationType,
            source = source,
            snapshot = TripPublicationSnapshot0387(
                trip = trip,
                bookings = store.bookingsFor(binding.bookingTripId),
                externalAccountId = accounts.single().id,
                seatAllocationVersion = binding.seatAllocationVersionUsed,
                semanticSignature = signature,
            ),
        )?.also { event ->
            recordEvent(
                "TRIP_MUTATION_TOMBSTONE_ENQUEUED",
                event,
                "historyPreserved=true blablaMutation=false external=true profileUuidPresent=true tripIdPresent=true",
            )
        }
    }

    fun recordProjectionTombstone0408(
        remote: DriverTripSyncState0402,
        mutationType: String,
        source: String = "PROJECTION_RECONCILER",
    ): TripPublicationOutboxEvent0387? {
        if (!store.onlineSettings().configured) return null
        if (remote.remoteTripId.isBlank() || remote.departureAtMillis <= 0L || remote.stops.size < 2) return null
        val cleanupCanonicalId = "projection-cleanup:" +
            sha256TripPublication0387(remote.remoteTripId).take(32)
        outbox.ensureRevisionAtLeast(
            cleanupCanonicalId,
            remote.publicationRevision.coerceAtLeast(0L),
        )
        val orderedStops = remote.stops.sortedBy(TripStop::order)
        val trip = Trip(
            id = cleanupCanonicalId,
            title = remote.title.ifBlank { orderedStops.first().name + " → " + orderedStops.last().name },
            departureAtMillis = remote.departureAtMillis,
            capacity = remote.capacity.coerceAtLeast(0),
            status = TripStatus.CANCELLED,
            stops = remote.stops,
            publicToken = remote.remoteTripId,
            remoteId = remote.remoteTripId,
            blablaProfileUuid = remote.blablaProfileUuid.takeIf(String::isNotBlank),
            blablaTripId = remote.blablaTripId.takeIf(String::isNotBlank),
            publicBookingEnabled = false,
            publishedSeats = remote.publishedSeats,
            rotaCertaSeatAllocation = remote.rotaCertaSeatAllocation?.coerceAtLeast(0),
            capacityReliable = remote.capacityReliable,
            publicationRevision = remote.publicationRevision.coerceAtLeast(0L),
            publicationTombstone = true,
            tripKey = remote.tripKey,
            canonicalStateHash = remote.canonicalStateHash,
        )
        val signature = "projection-tombstone-v1:" + sha256TripPublication0387(
            listOf(
                cleanupCanonicalId,
                remote.remoteTripId,
                remote.canonicalTripId,
                remote.tripKey,
                remote.blablaProfileUuid,
                remote.blablaTripId,
                "CANCELLED",
            ).joinToString("|"),
        )
        return outbox.enqueue(
            canonicalTripId = cleanupCanonicalId,
            operation = TripPublicationOperation0387.TOMBSTONE,
            mutationType = mutationType,
            source = source,
            snapshot = TripPublicationSnapshot0387(
                trip = trip,
                semanticSignature = signature,
            ),
            remoteProjectionDivergenceObserved = true,
        )?.also { event ->
            recordEvent(
                "TRIP_MUTATION_PROJECTION_TOMBSTONE_ENQUEUED_0408",
                event,
                "remoteTripKey=" + sha256TripPublication0387(remote.remoteTripId).take(12) +
                    " remoteRevision=" + remote.publicationRevision +
                    " canonicalTripIdPresent=" + remote.canonicalTripId.isNotBlank() +
                    " strongIdentityPresent=" + (remote.blablaProfileUuid.isNotBlank() && remote.blablaTripId.isNotBlank()),
            )
        }
    }

    fun ensureRevisionAtLeast(canonicalTripId: String, revision: Long) =
        outbox.ensureRevisionAtLeast(canonicalTripId, revision)

    suspend fun drainPending(limit: Int = 32): Int = withContext(Dispatchers.IO) {
        var delivered = 0
        outbox.pending(limit = limit).forEach { candidate ->
            val event = outbox.markProcessing(candidate.id) ?: return@forEach
            val startedNs = System.nanoTime()
            try {
                when (event.operation) {
                    TripPublicationOperation0387.UPSERT_LOCAL -> {
                        val snapshotTrip = requireNotNull(event.snapshot.trip) { "Snapshot local ausente." }
                        if (snapshotTrip.status == TripStatus.COMPLETED) {
                            val settings = store.onlineSettings()
                            require(settings.configured) { "Agenda Pública não configurada." }
                            val remoteId = snapshotTrip.remoteId?.takeIf(String::isNotBlank) ?: snapshotTrip.publicToken
                            val response = TripRemoteApi(settings).update(
                                snapshotTrip.copy(
                                    remoteId = remoteId,
                                    publicationRevision = event.revision,
                                    publicationTombstone = false,
                                    publicationEventId = event.id,
                                ),
                            )
                            if (response.stale) throw PublicationStaleRevision0387(response.entityRevision)
                        } else {
                            PublicAgendaAutoSync0300.syncLocalTripIncremental(
                                context = appContext,
                                store = store,
                                localTripId = event.canonicalTripId,
                                configuredRotaCertaSeatAllocation = snapshotTrip.rotaCertaSeatAllocation ?: 0,
                                snapshotTrip = snapshotTrip,
                                snapshotBookings = event.snapshot.bookings,
                                entityRevision = event.revision,
                                outboxEventId = event.id,
                            )
                        }
                    }
                    TripPublicationOperation0387.UPSERT_EXTERNAL -> {
                        val sourceTrip = requireNotNull(event.snapshot.externalTrip) { "Snapshot externo ausente." }
                        require(strongExternalIdentityMatches(event, sourceTrip)) {
                            "Identidade externa forte divergiu do snapshot persistido."
                        }
                        PublicAgendaAutoSync0300.syncExternalTripIncremental(
                            context = appContext,
                            store = store,
                            source = sourceTrip,
                            configuredRotaCertaSeatAllocation = event.snapshot.configuredRotaCertaSeatAllocation,
                            entityRevision = event.revision,
                            outboxEventId = event.id,
                            externalAccountId = event.snapshot.externalAccountId,
                            canonicalTripId = event.canonicalTripId,
                            seatAllocationVersion = event.snapshot.seatAllocationVersion,
                            canonicalTripSnapshot = event.snapshot.trip,
                        )
                    }
                    TripPublicationOperation0387.TOMBSTONE -> {
                        val snapshotTrip = requireNotNull(event.snapshot.trip) { "Snapshot de tombstone ausente." }
                        val settings = store.onlineSettings()
                        require(settings.configured) { "Agenda Pública não configurada." }
                        val remoteId = snapshotTrip.remoteId?.takeIf(String::isNotBlank) ?: snapshotTrip.publicToken
                        val response = TripRemoteApi(settings).update(
                            snapshotTrip.copy(
                                remoteId = remoteId,
                                status = TripStatus.CANCELLED,
                                publicBookingEnabled = false,
                                publicationRevision = event.revision,
                                publicationTombstone = true,
                                publicationEventId = event.id,
                            ),
                        )
                        if (response.stale) throw PublicationStaleRevision0387(response.entityRevision)
                    }
                }
                store.recordPublicationCommitted0411(
                    canonicalTripId = event.canonicalTripId,
                    publicationRevision = event.revision,
                    publicationEventId = event.id,
                    tombstone = event.operation == TripPublicationOperation0387.TOMBSTONE,
                )
                outbox.markDelivered(event.id)
                delivered++
                recordEvent(
                    "TRIP_MUTATION_OUTBOX_DELIVERED",
                    event,
                    "publicationResult=delivered retryCount=${event.attempts} latencyMs=${(System.nanoTime() - startedNs) / 1_000_000L}",
                )
            } catch (stale: PublicationStaleRevision0387) {
                val rebased = outbox.rebase(event.id, stale.remoteRevision)
                recordEvent(
                    "TRIP_MUTATION_OUTBOX_REBASED",
                    event,
                    "publicationResult=stale remoteRevision=${stale.remoteRevision} rebasedRevision=${rebased?.revision ?: -1} retryCount=${event.attempts} latencyMs=${(System.nanoTime() - startedNs) / 1_000_000L}",
                )
            } catch (cancelled: CancellationException) {
                outbox.markFailure(event.id, cancelled, retryable = true)
                throw cancelled
            } catch (error: Throwable) {
                val retryable = publicationFailureRetryable0387(error)
                outbox.markFailure(event.id, error, retryable)
                recordEvent(
                    "TRIP_MUTATION_OUTBOX_FAILED",
                    event,
                    "publicationResult=${if (retryable) "retryable" else "final"} retryCount=${event.attempts} latencyMs=${(System.nanoTime() - startedNs) / 1_000_000L} ${failureSummary0387(error)}",
                )
            }
        }
        delivered
    }

    internal fun outboxSnapshot(): List<TripPublicationOutboxEvent0387> = outbox.snapshot()

    private fun strongExternalIdentityMatches(event: TripPublicationOutboxEvent0387, sourceTrip: BlaBlaCollectorTrip): Boolean {
        val profileUuid = sourceTrip.profile_uuid.trim()
        val tripId = sourceTrip.trip_id?.trim().orEmpty()
        if (profileUuid.isBlank() || tripId.isBlank() || event.snapshot.externalAccountId.isBlank()) return false
        val boundCanonicalId = store.publicExternalBindingForStrongIdentity(profileUuid, tripId)?.bookingTripId
        if (!boundCanonicalId.isNullOrBlank() && boundCanonicalId == event.canonicalTripId) return true
        return strongExternalCanonicalTripId0387(
            event.tenantId,
            event.snapshot.externalAccountId,
            profileUuid,
            tripId,
        ) == event.canonicalTripId
    }

    private fun recordEvent(stage: String, event: TripPublicationOutboxEvent0387, extra: String) {
        UnifiedDebugEventStore.record(
            stage,
            appContext.packageName,
            "tenantId=${event.tenantId} evidenceId=${publicationEvidenceId0421(event.id, event.snapshot.trip?.canonicalRevision ?: 0L)} traceId=${event.id} internalTripId=${seatSyncDiagnosticKey(event.canonicalTripId)} canonicalTripId=${seatSyncDiagnosticKey(event.canonicalTripId)} stateHash=${event.snapshot.trip?.canonicalStateHash.orEmpty().takeLast(12)} transportRevision=${event.revision} revision=${event.revision} oldRevision=${(event.revision - 1L).coerceAtLeast(0L)} newRevision=${event.revision} logicalRevision=${event.snapshot.trip?.canonicalRevision ?: 0L} canonicalRevision=${event.snapshot.trip?.canonicalRevision ?: 0L} changedFields=${event.mutationType} mutationType=${event.mutationType} source=${event.source} publicationTarget=${event.destination} destination=${event.destination} operation=${event.operation.name} configVersion=${event.snapshot.seatAllocationVersion} outboxEventId=${event.id} $extra",
        )
    }
}

internal class PublicationStaleRevision0387(
    val remoteRevision: Long,
) : IllegalStateException("Publicação obsoleta; revisão remota=$remoteRevision")

internal fun strongExternalCanonicalTripId0387(
    tenantId: String,
    accountId: String,
    profileUuid: String,
    tripId: String,
): String {
    require(tenantId.isNotBlank() && accountId.isNotBlank() && profileUuid.isNotBlank() && tripId.isNotBlank())
    return "blablacar:" + sha256TripPublication0387(
        listOf(tenantId.trim(), accountId.trim(), profileUuid.trim().lowercase(), tripId.trim()).joinToString("|"),
    ).take(40)
}

internal fun publicationEventId0387(tenantId: String, canonicalTripId: String, revision: Long): String =
    "outbox_" + sha256TripPublication0387("$tenantId|$canonicalTripId|$revision").take(48)

internal fun publicationEvidenceId0421(traceId: String, canonicalRevision: Long): String =
    "ev_" + sha256TripPublication0387(traceId.trim() + "|" + canonicalRevision.coerceAtLeast(0L)).take(24)

private fun retryDelayMillis(attempt: Int): Long {
    val multiplier = 1 shl (attempt - 1).coerceIn(0, 6)
    return min(60L * 60L * 1000L, 5_000L * multiplier)
}

internal fun publicationFailureRetryable0387(error: Throwable): Boolean {
    val remote = generateSequence(error) { it.cause }.filterIsInstance<TripRemoteApiException>().firstOrNull()
    val deterministicConflictCodes = setOf(
        "protected_booking_required",
        "invalid_protected_booking_status",
        "invalid_protected_operational_status",
        "invalid_protected_payment_status",
        "protected_passenger_name_required",
        "invalid_protected_seats",
        "invalid_protected_booking_id",
        "protected_snapshot_requires_revision",
        "publication_revision_conflict",
        "protected_booking",
        "capacity_claim_namespace_mismatch",
        "invalid_external_capacity_claim",
        "inventory_mismatch",
    )
    return when {
        error is CancellationException -> true
        remote == null -> true
        remote.backendErrorCode in deterministicConflictCodes -> false
        remote.httpStatus <= 0 -> true
        remote.httpStatus in setOf(408, 425, 429) -> true
        remote.httpStatus == 409 -> false
        remote.httpStatus >= 500 -> true
        else -> false
    }
}

internal fun failureSummary0387(error: Throwable): String {
    val root = generateSequence(error) { it.cause }.last()
    val exceptionMessage = UnifiedDebugEventStore.sanitizeForExport(error.message.orEmpty()).take(240)
    val rootMessage = UnifiedDebugEventStore.sanitizeForExport(root.message.orEmpty()).take(240)
    return "exceptionClass=${error.javaClass.name} exceptionMessage=$exceptionMessage rootCauseClass=${root.javaClass.name} rootCauseMessage=$rootMessage"
}

internal fun sha256TripPublication0387(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
