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
    /** Stable logical mutation identity. Survives transport rebase/retry. */
    val mutationId0421: String = "",
    /** Stable idempotency key for the same logical snapshot. Never contains credentials. */
    val idempotencyKey0421: String = "",
    val status: TripPublicationStatus0387 = TripPublicationStatus0387.PENDING,
    val attempts: Int = 0,
    /** Cumulative stale-revision rebases for the same logical mutation. */
    val rebaseCount0453: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val lastError: String = "",
    val nextAttemptAtMillis: Long = 0L,
)

internal fun shouldDeduplicatePublicationEvent0410(
    latest: TripPublicationOutboxEvent0387?,
    operation: TripPublicationOperation0387,
    snapshot: TripPublicationSnapshot0387,
    remoteProjectionDivergenceObserved: Boolean = false,
): Boolean {
    if (latest == null || latest.operation != operation) return false
    if (latest.snapshot.semanticSignature != snapshot.semanticSignature) return false
    if (latest.snapshot.seatAllocationVersion != snapshot.seatAllocationVersion) return false
    // A terminal transport event is not eternal proof of the public projection.
    // Explicit repair must receive a fresh transport revision after either a previous
    // delivery or a final failure produced by an older projection/precondition rule.
    // Active/retryable work still deduplicates so the same snapshot never runs in parallel.
    if (
        remoteProjectionDivergenceObserved &&
        latest.status in setOf(
            TripPublicationStatus0387.DELIVERED,
            TripPublicationStatus0387.FAILED_FINAL,
        )
    ) return false
    return true
}

internal fun strongExternalSnapshotIdentityMatches0387(
    canonicalTripId: String,
    snapshotTrip: Trip?,
    sourceTrip: BlaBlaCollectorTrip,
): Boolean {
    val canonical = snapshotTrip ?: return false
    val profileUuid = sourceTrip.profile_uuid.trim()
    val tripId = sourceTrip.trip_id?.trim().orEmpty()
    if (canonicalTripId.isBlank() || profileUuid.isBlank() || tripId.isBlank()) return false
    return resolvedTripRecordOrigin(canonical) == TripRecordOrigin.EXTERNAL_BACKING &&
        canonical.tripKey == canonicalTripId &&
        canonical.blablaProfileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true &&
        canonical.blablaTripId?.trim() == tripId
}

internal fun resolveExternalOutboxAccountId0454(
    persistedAccountId: String,
    profileUuid: String,
    accounts: List<BlaBlaDynamicAccount>,
): String {
    val normalizedProfileUuid = profileUuid.trim()
    if (normalizedProfileUuid.isBlank()) return ""
    val matchingAccounts = accounts.filter { account ->
        account.id.isNotBlank() &&
            account.profileUuid?.trim()?.equals(normalizedProfileUuid, ignoreCase = true) == true
    }
    val persisted = persistedAccountId.trim()
    if (persisted.isNotBlank()) {
        return persisted.takeIf { wanted -> matchingAccounts.count { it.id == wanted } == 1 }.orEmpty()
    }
    return matchingAccounts.singleOrNull()?.id.orEmpty()
}

internal fun durableExternalCanonicalSnapshotNeedsRebase0456(
    persistedSnapshot: Trip?,
    currentCanonical: Trip,
): Boolean {
    val persisted = persistedSnapshot ?: return true
    return persisted.tripKey != currentCanonical.tripKey ||
        persisted.canonicalRevision != currentCanonical.canonicalRevision ||
        persisted.canonicalStateHash != currentCanonical.canonicalStateHash ||
        persisted.capacity != currentCanonical.capacity
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
            val mutationIdentity = publicationMutationIdentity0421(canonicalTripId, snapshot)
            val event = TripPublicationOutboxEvent0387(
                id = publicationEventId0387(tenantScope.tenantId, canonicalTripId, nextRevision),
                tenantId = tenantScope.tenantId,
                canonicalTripId = canonicalTripId,
                revision = nextRevision,
                operation = operation,
                mutationType = mutationType.take(80),
                source = source.take(80),
                snapshot = snapshot,
                mutationId0421 = mutationIdentity.first,
                idempotencyKey0421 = mutationIdentity.second,
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
            val mutationIdentity = publicationMutationIdentity0421(canonicalTripId, snapshot)
            val authoritative = TripPublicationOutboxEvent0387(
                id = eventId,
                tenantId = tenantScope.tenantId,
                canonicalTripId = canonicalTripId,
                revision = revision,
                operation = operation,
                mutationType = mutationType.take(80),
                source = source.take(80),
                snapshot = snapshot,
                mutationId0421 = mutationIdentity.first,
                idempotencyKey0421 = mutationIdentity.second,
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

    fun pending(
        nowMillis: Long = System.currentTimeMillis(),
        limit: Int = 32,
        canonicalTripIds: Set<String>? = null,
    ): List<TripPublicationOutboxEvent0387> =
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
                        it.nextAttemptAtMillis <= nowMillis &&
                        (canonicalTripIds == null || it.canonicalTripId in canonicalTripIds)
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

        if (target.rebaseCount0453 >= MAX_STALE_REBASES_0453) {
            events[index] = target.copy(
                status = TripPublicationStatus0387.FAILED_FINAL,
                updatedAtMillis = now,
                lastError = "stale_revision_rebase_limit_remote_$remoteRevision",
                nextAttemptAtMillis = 0L,
            )
            revisions[target.canonicalTripId] = maxOf(
                revisions[target.canonicalTripId] ?: 0L,
                remoteRevision,
                newest?.revision ?: 0L,
            )
            require(
                prefs.edit()
                    .putString(eventsKey, json.encodeToString(compact(events)))
                    .putString(revisionsKey, json.encodeToString(revisions))
                    .commit(),
            ) { "Falha ao persistir limite de rebase." }
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
            rebaseCount0453 = target.rebaseCount0453 + 1,
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
        private const val MAX_STALE_REBASES_0453 = 4
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
    private val evidenceJson0421 = Json { encodeDefaults = true }

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
        val canonicalTripId = canonicalBlaBlaTripKey0406(
            tenantId = outbox.tenantId,
            profileUuid = profileUuid,
            providerTripId = tripId,
        ) ?: return null
        val allocation = configuredRotaCertaSeatAllocation?.takeIf { it in 0..999 }
            ?: 0
        val blablaQuota = sourceTrip.published_seats?.takeIf { it in 0..999 } ?: 0
        val synthesized = PublicAgendaAutoSync0300.toPublicTrip(
            source = sourceTrip,
            capacity = (blablaQuota + allocation).coerceIn(0, 999),
            rotaCertaSeatAllocation = allocation,
        ) ?: return null
        val transportTrip0468 = synthesized.trip.copy(
            tripKey = canonicalTripId,
            recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
            canonicalRevision = 0L,
            canonicalStateHash = "",
        )
        val signature = PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(sourceTrip, allocation) +
            "|server-canonical:" + canonicalTripId +
            "|config:" + (seatAllocationVersion?.coerceAtLeast(0L) ?: 0L)
        return outbox.enqueue(
            canonicalTripId = canonicalTripId,
            operation = TripPublicationOperation0387.UPSERT_EXTERNAL,
            mutationType = mutationType,
            source = eventSource,
            snapshot = TripPublicationSnapshot0387(
                trip = transportTrip0468,
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
            ?: store.getTrip(binding.bookingTripId)?.tripKey?.takeIf(String::isNotBlank)
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

    suspend fun drainPending(
        limit: Int = 32,
        canonicalTripIds: Set<String>? = null,
    ): Int = withContext(Dispatchers.IO) {
        var delivered = 0
        val pendingVerification0429 = linkedMapOf<String, TripPublicationOutboxEvent0387>()
        repeat(limit.coerceAtLeast(1)) eventLoop@{
            val candidate = outbox.pending(limit = 1, canonicalTripIds = canonicalTripIds).firstOrNull()
                ?: return@eventLoop
            val event = outbox.markProcessing(candidate.id) ?: return@eventLoop
            val startedNs = System.nanoTime()
            recordEvidence0421(
                stage = "OUTBOX_DEQUEUE",
                status = "OK",
                reason = "OUTBOX_EVENT_ACQUIRED",
                event = event,
                extra = "attempt=${event.attempts} previousStage=OUTBOX_ENQUEUE nextStage=REQUEST_BUILD",
            )
            try {
                var backendCanonicalVerified0468 = false
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
                                mutationId0421 = event.resolvedMutationId0421(),
                                idempotencyKey0421 = event.resolvedIdempotencyKey0421(),
                            )
                        }
                    }
                    TripPublicationOperation0387.UPSERT_EXTERNAL -> {
                        val sourceTrip = requireNotNull(event.snapshot.externalTrip) { "Snapshot externo ausente." }
                        val effectiveExternalAccountId0454 = resolveExternalOutboxAccountId0454(
                            persistedAccountId = event.snapshot.externalAccountId,
                            profileUuid = sourceTrip.profile_uuid,
                            accounts = BlaBlaDynamicAccountRegistry(appContext).list(),
                        )
                        val canonicalSnapshotIdentityAccepted0455 = strongExternalSnapshotIdentityMatches0387(
                            canonicalTripId = event.canonicalTripId,
                            snapshotTrip = event.snapshot.trip,
                            sourceTrip = sourceTrip,
                        )
                        val externalIdentityAccepted0455 = strongExternalIdentityMatches(
                            event = event,
                            sourceTrip = sourceTrip,
                            effectiveExternalAccountId = effectiveExternalAccountId0454,
                        )
                        recordEvidence0421(
                            stage = "OUTBOX_IDENTITY_GUARD",
                            status = if (externalIdentityAccepted0455) "OK" else "FAILED",
                            reason = if (externalIdentityAccepted0455) "OUTBOX_CANONICAL_IDENTITY_ACCEPTED" else "OUTBOX_CANONICAL_IDENTITY_REJECTED",
                            event = event,
                            extra = "snapshotIdentity=" + canonicalSnapshotIdentityAccepted0455 +
                                " accountIdentity=" + effectiveExternalAccountId0454.isNotBlank() +
                                " persistedAccountIdPresent=" + event.snapshot.externalAccountId.isNotBlank() +
                                " profileUuidPresent=" + sourceTrip.profile_uuid.isNotBlank() +
                                " tripIdPresent=" + !sourceTrip.trip_id.isNullOrBlank() +
                                " previousStage=OUTBOX_DEQUEUE nextStage=CANONICAL_REBASE_GUARD",
                        )
                        require(externalIdentityAccepted0455) {
                            "Identidade externa forte divergiu do snapshot persistido."
                        }
                        if (event.snapshot.externalAccountId.isBlank() && effectiveExternalAccountId0454.isNotBlank()) {
                            recordEvent(
                                "TRIP_MUTATION_OUTBOX_ACCOUNT_RECOVERED_0454",
                                event,
                                "accountRecovered=true profileUuidPresent=true tripIdPresent=true",
                            )
                        }
                        val backendCanonicalDirectTransport0468 =
                            event.snapshot.trip?.canonicalRevision == 0L &&
                                event.snapshot.trip?.canonicalStateHash.isNullOrBlank() &&
                                event.canonicalTripId == canonicalBlaBlaTripKey0406(
                                    tenantId = event.tenantId,
                                    profileUuid = sourceTrip.profile_uuid,
                                    providerTripId = sourceTrip.trip_id,
                                )
                        if (backendCanonicalDirectTransport0468) {
                            recordEvidence0421(
                                stage = "CANONICAL_REBASE_GUARD",
                                status = "SKIPPED",
                                reason = "BACKEND_CANONICAL_AUTHORITY_0468",
                                event = event,
                                extra = "localCanonicalRead=false previousStage=OUTBOX_IDENTITY_GUARD nextStage=INCREMENTAL_IDENTITY_GUARD",
                            )
                            val canonicalAck0468 = PublicAgendaAutoSync0300.syncExternalTripIncremental(
                                context = appContext,
                                store = store,
                                source = sourceTrip,
                                configuredRotaCertaSeatAllocation = event.snapshot.configuredRotaCertaSeatAllocation,
                                entityRevision = event.revision,
                                outboxEventId = event.id,
                                mutationId0421 = event.resolvedMutationId0421(),
                                idempotencyKey0421 = event.resolvedIdempotencyKey0421(),
                                externalAccountId = effectiveExternalAccountId0454,
                                canonicalTripId = event.canonicalTripId,
                                seatAllocationVersion = event.snapshot.seatAllocationVersion,
                                canonicalTripSnapshot = event.snapshot.trip,
                            )
                            require(canonicalAck0468.published) { "BACKEND_CANONICAL_WRITE_NOT_CONFIRMED_0468" }
                            require(canonicalAck0468.canonicalTripId == event.canonicalTripId) {
                                "BACKEND_CANONICAL_IDENTITY_MISMATCH_0468"
                            }
                            require(canonicalAck0468.canonicalRevision > 0L) {
                                "BACKEND_CANONICAL_REVISION_MISSING_0468"
                            }
                            require(canonicalAck0468.canonicalStateHash.startsWith("server-canonical-v1:")) {
                                "BACKEND_CANONICAL_HASH_MISSING_0468"
                            }
                            require(canonicalAck0468.publicProjectionHash.startsWith("public-v2:")) {
                                "BACKEND_PUBLIC_HASH_MISSING_0468"
                            }
                            val settings0468 = store.onlineSettings()
                            require(settings0468.configured) { "Agenda Pública não configurada." }
                            val api0468 = TripRemoteApi(settings0468)
                            val remoteTripId0468 = canonicalAck0468.remoteTripId
                                .ifBlank { event.snapshot.trip?.publicToken.orEmpty() }
                                .ifBlank { event.canonicalTripId }
                            val proofContext0468 = RemotePublicationEvidenceContext0421(
                                evidenceId = publicationEvidenceId0421(event.id, canonicalAck0468.canonicalRevision),
                                traceId = event.id,
                                canonicalTripId = event.canonicalTripId,
                                logicalRevision = canonicalAck0468.canonicalRevision,
                                transportRevision = canonicalAck0468.publicationRevision,
                                mutationId = event.resolvedMutationId0421(),
                                idempotencyKey = event.resolvedIdempotencyKey0421(),
                            )
                            val readback0468 = api0468.readPublicTripProjection0411(
                                remoteTripId = remoteTripId0468,
                                evidence0421 = proofContext0468,
                            )
                            val readbackComputedHash0468 = canonicalPublicProjectionHash0411(readback0468.payload)
                            val mismatch0468 = buildList {
                                if (readback0468.payload.canonicalTripId != event.canonicalTripId) add("identity")
                                if (readback0468.payload.canonicalRevision != canonicalAck0468.canonicalRevision) add("canonicalRevision")
                                if (readback0468.payload.publicationRevision != canonicalAck0468.publicationRevision) add("publicationRevision")
                                if (readback0468.payload.canonicalStateHash != canonicalAck0468.canonicalStateHash) add("canonicalStateHash")
                                if (readback0468.publicProjectionHash != canonicalAck0468.publicProjectionHash) add("publicProjectionHash")
                                if (readbackComputedHash0468 != readback0468.publicProjectionHash) add("serverHash")
                                if (!readback0468.agendaVisible) add("agendaVisibility")
                                if (readback0468.persistedAtMillis <= 0L) add("persistedAtMillis")
                            }
                            val proofOk0468 = mismatch0468.isEmpty()
                            recordEvidence0421(
                                stage = "SERVER_CANONICAL_PUBLIC_READBACK_0468",
                                status = if (proofOk0468) "OK" else "FAILED",
                                reason = if (proofOk0468) "SERVER_CANONICAL_PUBLIC_MATCH_0468" else "SERVER_CANONICAL_PUBLIC_MISMATCH_0468",
                                event = event,
                                extra = "remoteTripId=" + seatSyncDiagnosticKey(remoteTripId0468) +
                                    " canonicalRevision=" + canonicalAck0468.canonicalRevision +
                                    " publicationRevision=" + canonicalAck0468.publicationRevision +
                                    " agendaVisible=" + readback0468.agendaVisible +
                                    " mismatchFields=" + mismatch0468.joinToString(",") +
                                    " previousStage=SERVER_ACK nextStage=ATTESTATION",
                            )
                            val attestation0468 = api0468.reportPublicTripAttestation0417(
                                remoteTripId = remoteTripId0468,
                                request = DriverPublicAttestationRequest0417(
                                    state = if (proofOk0468) "VERIFIED" else "DIVERGENT",
                                    canonicalRevision = canonicalAck0468.canonicalRevision,
                                    publicationRevision = canonicalAck0468.publicationRevision,
                                    canonicalStateHash = canonicalAck0468.canonicalStateHash,
                                    expectedHash = canonicalAck0468.publicProjectionHash,
                                    readbackHash = readback0468.publicProjectionHash,
                                    mismatchFields = mismatch0468,
                                    reason = if (proofOk0468) {
                                        "PUBLIC_READBACK_MATCH_AGENDA_VISIBLE_0468"
                                    } else {
                                        readback0468.agendaVisibilityReason.ifBlank { "PUBLIC_READBACK_MISMATCH_0468" }
                                    },
                                    correlationId = event.id,
                                ),
                            )
                            backendCanonicalVerified0468 =
                                proofOk0468 &&
                                    serverPublicAttestationConfirmed0433(
                                        expectedCanonicalRevision = canonicalAck0468.canonicalRevision,
                                        expectedPublicationRevision = canonicalAck0468.publicationRevision,
                                        response = attestation0468,
                                    )
                            require(backendCanonicalVerified0468) {
                                "BACKEND_CANONICAL_ATTESTATION_NOT_VERIFIED_0468"
                            }
                        } else {
                        val currentCanonicalMatches0456 = store.trips().filter { trip ->
                            resolvedTripRecordOrigin(trip) == TripRecordOrigin.EXTERNAL_BACKING &&
                                !trip.deleted &&
                                trip.tripKey == event.canonicalTripId &&
                                trip.blablaProfileUuid?.trim()?.equals(sourceTrip.profile_uuid.trim(), ignoreCase = true) == true &&
                                trip.blablaTripId?.trim() == sourceTrip.trip_id?.trim()
                        }
                        val currentCanonical0456 = currentCanonicalMatches0456.singleOrNull()
                        if (currentCanonical0456 == null) {
                            recordEvidence0421(
                                stage = "CANONICAL_REBASE_GUARD",
                                status = "FAILED",
                                reason = "CANONICAL_REBASE_IDENTITY_NOT_UNIQUE",
                                event = event,
                                extra = "canonicalMatches=" + currentCanonicalMatches0456.size +
                                    " previousStage=OUTBOX_IDENTITY_GUARD nextStage=INCREMENTAL_IDENTITY_GUARD",
                            )
                            error("CANONICAL_REBASE_IDENTITY_NOT_UNIQUE matches=" + currentCanonicalMatches0456.size)
                        }
                        val currentBookings0456 = store.bookingsFor(currentCanonical0456.id)
                        val domainInventory0456 = operationalInventoryCapacity(currentCanonical0456, currentBookings0456)
                        val legacyCapacityMismatch0456 = currentCanonical0456.capacity != domainInventory0456
                        val repairedCanonical0456 = if (legacyCapacityMismatch0456) {
                            store.saveTrip(currentCanonical0456.copy(capacity = domainInventory0456))
                        } else {
                            currentCanonical0456
                        }
                        if (repairedCanonical0456.capacity != domainInventory0456) {
                            recordEvidence0421(
                                stage = "CANONICAL_REBASE_GUARD",
                                status = "FAILED",
                                reason = "CANONICAL_CAPACITY_REPAIR_NOT_COMMITTED",
                                event = event,
                                extra = "actualCapacity=" + repairedCanonical0456.capacity +
                                    " expectedCapacity=" + domainInventory0456 +
                                    " previousStage=OUTBOX_IDENTITY_GUARD nextStage=INCREMENTAL_IDENTITY_GUARD",
                            )
                            error(
                                "CANONICAL_CAPACITY_REPAIR_NOT_COMMITTED expected=" +
                                    domainInventory0456 + " actual=" + repairedCanonical0456.capacity,
                            )
                        }
                        val durableSnapshotNeedsRebase0456 = durableExternalCanonicalSnapshotNeedsRebase0456(
                            persistedSnapshot = event.snapshot.trip,
                            currentCanonical = repairedCanonical0456,
                        )
                        if (legacyCapacityMismatch0456 || durableSnapshotNeedsRebase0456) {
                            val reason0456 = if (legacyCapacityMismatch0456) {
                                "LEGACY_CANONICAL_CAPACITY_REBASED"
                            } else {
                                "DURABLE_CANONICAL_SNAPSHOT_REBASED"
                            }
                            recordEvidence0421(
                                stage = "CANONICAL_REBASE_GUARD",
                                status = "REBASED",
                                reason = reason0456,
                                event = event,
                                extra = "oldLogicalRevision=" + (event.snapshot.trip?.canonicalRevision ?: 0L) +
                                    " newLogicalRevision=" + repairedCanonical0456.canonicalRevision +
                                    " oldCapacity=" + (event.snapshot.trip?.capacity ?: -1) +
                                    " currentCapacity=" + repairedCanonical0456.capacity +
                                    " domainInventory=" + domainInventory0456 +
                                    " oldStateHash=" + event.snapshot.trip?.canonicalStateHash.orEmpty().takeLast(12) +
                                    " newStateHash=" + repairedCanonical0456.canonicalStateHash.takeLast(12) +
                                    " previousStage=OUTBOX_IDENTITY_GUARD nextStage=OUTBOX_ENQUEUE",
                            )
                            val repairedAllocation0456 = repairedCanonical0456.rotaCertaSeatAllocation
                                ?.takeIf { it in 0..999 }
                                ?: 0
                            val repairedSignature0456 =
                                PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(
                                    sourceTrip,
                                    repairedAllocation0456,
                                ) + "|state:" + repairedCanonical0456.canonicalStateHash
                            val rebasedEvent0456 = requireNotNull(
                                outbox.enqueue(
                                    canonicalTripId = repairedCanonical0456.tripKey,
                                    operation = TripPublicationOperation0387.UPSERT_EXTERNAL,
                                    mutationType = event.mutationType,
                                    source = event.source,
                                    snapshot = event.snapshot.copy(
                                        trip = repairedCanonical0456,
                                        externalTrip = sourceTrip,
                                        externalAccountId = effectiveExternalAccountId0454
                                            .ifBlank { event.snapshot.externalAccountId },
                                        configuredRotaCertaSeatAllocation = repairedAllocation0456,
                                        seatAllocationVersion = maxOf(
                                            event.snapshot.seatAllocationVersion,
                                            repairedCanonical0456.seatAllocationVersionUsed,
                                        ),
                                        semanticSignature = repairedSignature0456,
                                    ),
                                    remoteProjectionDivergenceObserved = true,
                                ),
                            ) { "CANONICAL_REBASE_OUTBOX_ENQUEUE_FAILED" }
                            recordEvent(
                                "TRIP_MUTATION_OUTBOX_ENQUEUED",
                                rebasedEvent0456,
                                "previousRevision=" + event.revision +
                                    " resultingRevision=" + rebasedEvent0456.revision +
                                    " canonicalRebase0456=true",
                            )
                            recordEvent(
                                "TRIP_MUTATION_OUTBOX_CANONICAL_REBASED_0456",
                                rebasedEvent0456,
                                "supersedesTrace=" + event.id +
                                    " oldLogicalRevision=" + (event.snapshot.trip?.canonicalRevision ?: 0L) +
                                    " newLogicalRevision=" + repairedCanonical0456.canonicalRevision +
                                    " oldCapacity=" + (event.snapshot.trip?.capacity ?: -1) +
                                    " newCapacity=" + repairedCanonical0456.capacity +
                                    " domainInventory=" + domainInventory0456,
                            )
                            return@eventLoop
                        }
                        recordEvidence0421(
                            stage = "CANONICAL_REBASE_GUARD",
                            status = "OK",
                            reason = "CANONICAL_SNAPSHOT_CURRENT",
                            event = event,
                            extra = "logicalRevision=" + repairedCanonical0456.canonicalRevision +
                                " capacity=" + repairedCanonical0456.capacity +
                                " domainInventory=" + domainInventory0456 +
                                " previousStage=OUTBOX_IDENTITY_GUARD nextStage=INCREMENTAL_IDENTITY_GUARD",
                        )
                        val publicationCanonicalTripId0434 = repairedCanonical0456.tripKey
                            .takeIf(String::isNotBlank)
                            ?: event.canonicalTripId
                        PublicAgendaAutoSync0300.syncExternalTripIncremental(
                            context = appContext,
                            store = store,
                            source = sourceTrip,
                            configuredRotaCertaSeatAllocation = event.snapshot.configuredRotaCertaSeatAllocation,
                            entityRevision = event.revision,
                            outboxEventId = event.id,
                            mutationId0421 = event.resolvedMutationId0421(),
                            idempotencyKey0421 = event.resolvedIdempotencyKey0421(),
                            externalAccountId = effectiveExternalAccountId0454,
                            canonicalTripId = publicationCanonicalTripId0434,
                            seatAllocationVersion = event.snapshot.seatAllocationVersion,
                            canonicalTripSnapshot = repairedCanonical0456,
                        )
                        }
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
                if (backendCanonicalVerified0468) {
                    outbox.markDelivered(event.id)
                    delivered++
                    recordEvent(
                        "TRIP_MUTATION_OUTBOX_DELIVERED",
                        event,
                        "publicationResult=server_canonical_readback_confirmed_0468 blue=true localCanonicalRead=false retryCount=" +
                            event.attempts + " latencyMs=" + ((System.nanoTime() - startedNs) / 1_000_000L),
                    )
                    return@eventLoop
                }
                val localMirrorSourceId0434 = event.snapshot.trip?.id?.takeIf(String::isNotBlank)
                    ?: event.canonicalTripId
                store.recordPublicationCommitted0411(
                    canonicalTripId = localMirrorSourceId0434,
                    publicationRevision = event.revision,
                    publicationEventId = event.id,
                    tombstone = event.operation == TripPublicationOperation0387.TOMBSTONE,
                )
                if (event.operation == TripPublicationOperation0387.TOMBSTONE) {
                    outbox.markDelivered(event.id)
                    delivered++
                    recordEvent(
                        "TRIP_MUTATION_OUTBOX_DELIVERED",
                        event,
                        "publicationResult=delivered_tombstone retryCount=${event.attempts} latencyMs=${(System.nanoTime() - startedNs) / 1_000_000L}",
                    )
                } else {
                    pendingVerification0429[localMirrorSourceId0434] = event
                    recordEvent(
                        "TRIP_MUTATION_OUTBOX_READBACK_PENDING_0453",
                        event,
                        "publicationResult=server_write_accepted nextStage=PUBLIC_IDENTITY_RESOLUTION retryCount=${event.attempts}",
                    )
                }
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
                val chain0458 = generateSequence(error) { it.cause }.toList()
                val root0458 = chain0458.lastOrNull() ?: error
                val remote = chain0458.filterIsInstance<TripRemoteApiException>().firstOrNull()
                val message0458 = runCatching { UnifiedDebugEventStore.sanitizeForExport(error.message.orEmpty()) }
                    .getOrDefault("<sanitization-failed>")
                    .replace('"', '\'')
                    .take(240)
                val rootMessage0458 = runCatching { UnifiedDebugEventStore.sanitizeForExport(root0458.message.orEmpty()) }
                    .getOrDefault("<sanitization-failed>")
                    .replace('"', '\'')
                    .take(240)
                val source0458 = error.stackTrace.firstOrNull {
                    it.className.startsWith("br.com.mapeiaia.rotacerta")
                }
                recordEvidence0421(
                    stage = "OUTBOX_FAILURE",
                    status = "FAILED",
                    reason = "UNCAUGHT_PUBLICATION_EXCEPTION",
                    event = event,
                    extra = buildString {
                        append("exceptionClass=").append(error.javaClass.name)
                        append(" exceptionMessage=\"").append(message0458).append('\"')
                        append(" rootCauseClass=").append(root0458.javaClass.name)
                        append(" rootCauseMessage=\"").append(rootMessage0458).append('\"')
                        remote?.let { value ->
                            append(" networkCallId=").append(value.networkCallId)
                            append(" transportPhase=").append(value.transportPhase)
                            append(" httpStatus=").append(value.httpStatus)
                            append(" backendErrorCode=").append(value.backendErrorCode)
                            append(" requestBytes=").append(value.requestBytes)
                            append(" responseBytes=").append(value.responseBytes)
                            append(" requestSha256=").append(value.requestSha256)
                            append(" responseSha256=").append(value.responseSha256)
                        }
                        if (source0458 != null) {
                            append(" exceptionSource=")
                                .append(source0458.fileName ?: source0458.className.substringAfterLast('.'))
                                .append(':').append(source0458.methodName).append(':').append(source0458.lineNumber)
                        }
                        append(" previousStage=PIPELINE nextStage=")
                            .append(if (retryable) "OUTBOX_RETRY" else "STOP")
                    },
                )
                outbox.markFailure(event.id, error, retryable)
                val persistedFailureStage0458 = when {
                    remote?.httpStatus?.let { it > 0 } == true -> "HTTP_RESPONSE"
                    remote?.networkCallId?.isNotBlank() == true || (remote?.requestBytes ?: 0) > 0 -> "HTTP_SEND"
                    else -> "OUTBOX_FAILURE"
                }
                val persistedReason0458 = buildString {
                    append(error.javaClass.simpleName.ifBlank { error.javaClass.name })
                    if (message0458.isNotBlank()) append(':').append(message0458)
                }.take(160)
                store.recordPublicMirrorPublicationFailure0421(
                    canonicalTripId = event.snapshot.trip?.id?.takeIf(String::isNotBlank)
                        ?: event.canonicalTripId,
                    expectedCanonicalRevision = event.snapshot.trip?.canonicalRevision ?: 0L,
                    transportRevision = event.revision,
                    evidenceId = publicationEvidenceId0421(event.id, event.snapshot.trip?.canonicalRevision ?: 0L),
                    traceId = event.id,
                    retryable = retryable,
                    httpStatus = remote?.httpStatus ?: 0,
                    backendErrorCode = remote?.backendErrorCode.orEmpty(),
                    networkCallId = remote?.networkCallId.orEmpty(),
                    requestBytes = remote?.requestBytes ?: 0,
                    responseBytes = remote?.responseBytes ?: 0,
                    requestHash = remote?.requestSha256.orEmpty(),
                    responseHash = remote?.responseSha256.orEmpty(),
                    reason = persistedReason0458,
                    failedStage = persistedFailureStage0458,
                )
                recordEvent(
                    "TRIP_MUTATION_OUTBOX_FAILED",
                    event,
                    "publicationResult=${if (retryable) "retryable" else "final"} retryCount=${event.attempts} latencyMs=${(System.nanoTime() - startedNs) / 1_000_000L} ${failureSummary0387(error)}",
                )
            }
        }
        if (pendingVerification0429.isNotEmpty()) {
            val settings = store.onlineSettings()
            if (!settings.configured) {
                pendingVerification0429.values.forEach { event ->
                    outbox.markFailure(event.id, IllegalStateException("Agenda Pública não configurada para readback."), retryable = true)
                }
            } else {
                try {
                    val api = TripRemoteApi(settings)
                    pendingVerification0429.values.forEach { event ->
                        recordEvidence0421(
                            stage = "PUBLIC_IDENTITY_RESOLUTION",
                            status = "START",
                            reason = "SYNC_STATE_LOOKUP",
                            event = event,
                            extra = "includePastForVerification=true previousStage=SERVER_ACK nextStage=PUBLIC_IDENTITY_RESOLUTION",
                        )
                    }
                    val remoteStates = api.listDriverTripSyncStates0402(
                        includePastForVerification0429 = true,
                    ).trips
                    pendingVerification0429.forEach { (canonicalTripId, event) ->
                        val trip = store.getTrip(canonicalTripId)
                        if (trip == null || trip.deleted || trip.publicationTombstone || trip.status == TripStatus.CANCELLED) {
                            recordEvidence0421(
                                stage = "PUBLIC_IDENTITY_RESOLUTION",
                                status = "FAILED",
                                reason = "CANONICAL_SNAPSHOT_UNAVAILABLE_AFTER_WRITE",
                                event = event,
                                extra = "previousStage=SERVER_ACK nextStage=STOP",
                            )
                            outbox.markFailure(event.id, IllegalStateException("Snapshot canônico indisponível após publicação."), retryable = false)
                            return@forEach
                        }
                        val candidates = remoteStates
                            .filter { remote -> remoteMatchesCanonicalProjection0408(trip, remote) }
                            .distinctBy(DriverTripSyncState0402::remoteTripId)
                        if (candidates.size != 1) {
                            val duplicate = candidates.size > 1
                            val identityReason0429 = if (candidates.isEmpty()) {
                                "PUBLIC_IDENTITY_UNRESOLVED"
                            } else {
                                "PUBLIC_IDENTITY_AMBIGUOUS"
                            }
                            recordEvidence0421(
                                stage = "PUBLIC_IDENTITY_RESOLUTION",
                                status = "FAILED",
                                reason = identityReason0429,
                                event = event,
                                extra = "projectionCandidates=" + candidates.size +
                                    " includePastForVerification=true previousStage=SERVER_ACK nextStage=STOP",
                            )
                            recordEvidence0421(
                                stage = "ATTESTATION",
                                status = "DENIED",
                                reason = identityReason0429,
                                event = event,
                                extra = "retryable=" + (!duplicate) + " publicReadbackAttempted=false",
                            )
                            outbox.markFailure(
                                event.id,
                                IllegalStateException(if (duplicate) "Public identity ambígua." else "Public identity ainda não resolvida."),
                                retryable = !duplicate,
                            )
                            UnifiedDebugEventStore.record(
                                "PUBLIC_INCREMENTAL_ATTESTATION_DEFERRED_0429",
                                appContext.packageName,
                                "canonicalTripId=" + seatSyncDiagnosticKey(trip.tripKey.ifBlank { trip.id }) +
                                    " projectionCandidates=" + candidates.size +
                                    " reason=" + identityReason0429,
                            )
                            return@forEach
                        }
                        recordEvidence0421(
                            stage = "PUBLIC_IDENTITY_RESOLUTION",
                            status = "OK",
                            reason = "PUBLIC_IDENTITY_RESOLVED",
                            event = event,
                            extra = "projectionCandidates=1 includePastForVerification=true publicIdentityKey=" +
                                seatSyncDiagnosticKey(candidates.single().remoteTripId) +
                                " previousStage=SERVER_ACK nextStage=PUBLIC_READBACK_REQUEST",
                        )
                        val attestation = PublicMirrorAttestationCoordinator0411.attest(
                            context = appContext,
                            store = store,
                            api = api,
                            trip = trip,
                            remote = candidates.single(),
                            force = true,
                        )
                        val refreshed = store.getTrip(canonicalTripId)
                        val projectionConfirmed =
                            refreshed?.publicMirrorAttestationCurrent0411() == true ||
                                refreshed?.publicMirrorProjectionCurrent0411() == true
                        if (projectionConfirmed) {
                            outbox.markDelivered(event.id)
                            delivered++
                            recordEvent(
                                "TRIP_MUTATION_OUTBOX_DELIVERED",
                                event,
                                "publicationResult=readback_confirmed blue=" + (refreshed?.publicMirrorAttestationCurrent0411() == true) +
                                    " optionalUrlUnproven=" + (refreshed?.publicMirrorAttestationReason0411 == "BLABLACAR_PUBLIC_URL_PENDING_AGENDA_VISIBLE_0466"),
                            )
                        } else {
                            val retryable = attestation.pending > 0 || attestation.readbackFailures > 0
                            outbox.markFailure(
                                event.id,
                                IllegalStateException("Public readback/attestation não confirmou a mutação."),
                                retryable = retryable,
                            )
                            recordEvent(
                                "TRIP_MUTATION_OUTBOX_VERIFICATION_FAILED_0453",
                                event,
                                "publicationResult=" + if (retryable) "readback_retryable" else "readback_divergent" +
                                    " pending=" + attestation.pending +
                                    " divergent=" + attestation.divergent +
                                    " readbackFailures=" + attestation.readbackFailures,
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    pendingVerification0429.values.forEach { event ->
                        recordEvidence0421(
                            stage = "PUBLIC_IDENTITY_RESOLUTION",
                            status = "FAILED",
                            reason = "PUBLIC_IDENTITY_LOOKUP_CANCELLED",
                            event = event,
                            extra = "previousStage=SERVER_ACK nextStage=STOP",
                        )
                        outbox.markFailure(event.id, cancelled, retryable = true)
                    }
                    throw cancelled
                } catch (error: Throwable) {
                    pendingVerification0429.values.forEach { event ->
                        recordEvidence0421(
                            stage = "PUBLIC_IDENTITY_RESOLUTION",
                            status = "FAILED",
                            reason = "PUBLIC_IDENTITY_LOOKUP_FAILED",
                            event = event,
                            extra = failureSummary0387(error) + " previousStage=SERVER_ACK nextStage=STOP",
                        )
                        recordEvidence0421(
                            stage = "ATTESTATION",
                            status = "DENIED",
                            reason = "PUBLIC_IDENTITY_LOOKUP_FAILED",
                            event = event,
                            extra = "retryable=true publicReadbackAttempted=false",
                        )
                        outbox.markFailure(event.id, error, retryable = true)
                    }
                    UnifiedDebugEventStore.record(
                        "PUBLIC_INCREMENTAL_ATTESTATION_FAILED_0429",
                        appContext.packageName,
                        "canonicalCount=" + pendingVerification0429.size +
                            " error=" + error::class.java.simpleName +
                            " message=" + error.message.orEmpty().take(180),
                    )
                }
            }
        }
        delivered
    }

    internal fun outboxSnapshot(): List<TripPublicationOutboxEvent0387> = outbox.snapshot()

    private fun strongExternalIdentityMatches(
        event: TripPublicationOutboxEvent0387,
        sourceTrip: BlaBlaCollectorTrip,
        effectiveExternalAccountId: String,
    ): Boolean {
        val profileUuid = sourceTrip.profile_uuid.trim()
        val tripId = sourceTrip.trip_id?.trim().orEmpty()
        if (profileUuid.isBlank() || tripId.isBlank()) return false
        val accountIdentityConfirmed = effectiveExternalAccountId.isNotBlank()
        val boundCanonicalId = store.publicExternalBindingForStrongIdentity(profileUuid, tripId)?.bookingTripId.orEmpty()
        val expectedStrongId = if (accountIdentityConfirmed) {
            canonicalBlaBlaTripKey0406(
                tenantId = event.tenantId,
                profileUuid = profileUuid,
                providerTripId = tripId,
            ).orEmpty()
        } else ""
        return externalIncrementalPublicationIdentityAccepted0455(
            resolvedInternalTripId = event.canonicalTripId,
            expectedStrongId = expectedStrongId,
            boundInternalTripId = boundCanonicalId,
            canonicalTripSnapshot = event.snapshot.trip,
            source = sourceTrip,
            accountIdentityConfirmed = accountIdentityConfirmed,
        )
    }

    private fun recordEvent(stage: String, event: TripPublicationOutboxEvent0387, extra: String) {
        UnifiedDebugEventStore.record(
            stage,
            appContext.packageName,
            "tenantScope=${seatSyncDiagnosticKey(event.tenantId)} evidenceId=${publicationEvidenceId0421(event.id, event.snapshot.trip?.canonicalRevision ?: 0L)} traceId=${event.id} internalTripId=${seatSyncDiagnosticKey(event.canonicalTripId)} canonicalTripId=${seatSyncDiagnosticKey(event.canonicalTripId)} stateHash=${event.snapshot.trip?.canonicalStateHash.orEmpty().takeLast(12)} transportRevision=${event.revision} revision=${event.revision} oldRevision=${(event.revision - 1L).coerceAtLeast(0L)} newRevision=${event.revision} logicalRevision=${event.snapshot.trip?.canonicalRevision ?: 0L} canonicalRevision=${event.snapshot.trip?.canonicalRevision ?: 0L} changedFields=${event.mutationType} mutationType=${event.mutationType} source=${event.source} publicationTarget=${event.destination} destination=${event.destination} operation=${event.operation.name} configVersion=${event.snapshot.seatAllocationVersion} mutationId=${event.resolvedMutationId0421()} idempotencyKey=${event.resolvedIdempotencyKey0421()} outboxEventId=${event.id} $extra",
        )
        if (stage == "TRIP_MUTATION_OUTBOX_ENQUEUED" || stage == "TRIP_MUTATION_TOMBSTONE_ENQUEUED") {
            recordEvidence0421(
                stage = "CANONICAL_RESOLUTION",
                status = "OK",
                reason = "CANONICAL_IDENTITY_RESOLVED",
                event = event,
                extra = "localTripId=" + seatSyncDiagnosticKey(event.snapshot.trip?.id.orEmpty()) +
                    " canonicalTripKey=" + seatSyncDiagnosticKey(event.snapshot.trip?.tripKey.orEmpty()) +
                    " previousStage=TRIGGER nextStage=CANONICAL_READ",
            )
            recordEvidence0421(
                stage = "CANONICAL_READ",
                status = "OK",
                reason = "CANONICAL_SNAPSHOT_READY",
                event = event,
                extra = "logicalRevision=" + (event.snapshot.trip?.canonicalRevision ?: 0L) +
                    " previousStage=CANONICAL_RESOLUTION nextStage=CANONICAL_SERIALIZATION",
            )
            val bytes = runCatching {
                evidenceJson0421.encodeToString(event.snapshot).toByteArray(Charsets.UTF_8)
            }.getOrDefault(ByteArray(0))
            val hash = if (bytes.isEmpty()) "" else MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            val canonicalByteEvidence0458 = AgendaFailureEvidence.byteSanitizationEvidence0458(bytes)
            recordEvidence0421(
                stage = "CANONICAL_SERIALIZATION",
                status = "OK",
                reason = "SNAPSHOT_SERIALIZED",
                event = event,
                extra = "inputHash=${event.snapshot.trip?.canonicalStateHash.orEmpty()} outputHash=$hash outputBytes=${bytes.size} " +
                    canonicalByteEvidence0458.compactDetails0458() +
                    " charset=UTF-8 serialization=kotlinx-json canonicalization=semantic-signature previousStage=CANONICAL_READ nextStage=OUTBOX_ENQUEUE",
            )
            recordEvidence0421(
                stage = "OUTBOX_ENQUEUE",
                status = "OK",
                reason = "OUTBOX_EVENT_PERSISTED",
                event = event,
                extra = "attempt=0 previousStage=CANONICAL_SERIALIZATION nextStage=OUTBOX_DEQUEUE",
            )
        }
    }

    private fun recordEvidence0421(
        stage: String,
        status: String,
        reason: String,
        event: TripPublicationOutboxEvent0387,
        extra: String = "",
    ) {
        // The flight recorder is intentionally fail-open: evidence collection
        // must not alter the outbox result it is observing.
        runCatching {
            UnifiedDebugEventStore.recordAlways(
                "PUBLIC_EVIDENCE_0421",
                appContext.packageName,
                buildString {
                    append("evidenceId=").append(publicationEvidenceId0421(event.id, event.snapshot.trip?.canonicalRevision ?: 0L))
                    append(" traceId=").append(event.id)
                    append(" correlationId=").append(event.id)
                    append(" tenantScope=").append(seatSyncDiagnosticKey(event.tenantId))
                    append(" stage=").append(stage)
                    append(" status=").append(status)
                    append(" reasonCode=").append(reason)
                    append(" canonicalTripId=").append(seatSyncDiagnosticKey(event.canonicalTripId))
                    append(" logicalRevision=").append(event.snapshot.trip?.canonicalRevision ?: 0L)
                    append(" transportRevision=").append(event.revision)
                    append(" canonicalStateHash=").append(event.snapshot.trip?.canonicalStateHash.orEmpty())
                    append(" mutationId=").append(event.resolvedMutationId0421())
                    append(" idempotencyKey=").append(event.resolvedIdempotencyKey0421())
                    append(" durationMs=0")
                    if (extra.isNotBlank()) append(' ').append(extra)
                },
            )
        }
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

internal fun publicationMutationIdentity0421(
    canonicalTripId: String,
    snapshot: TripPublicationSnapshot0387,
): Pair<String, String> {
    val material = listOf(
        canonicalTripId.trim(),
        (snapshot.trip?.canonicalRevision ?: 0L).coerceAtLeast(0L).toString(),
        snapshot.trip?.canonicalStateHash.orEmpty().trim(),
        snapshot.semanticSignature.trim(),
        snapshot.seatAllocationVersion.coerceAtLeast(0L).toString(),
    ).joinToString("|")
    val digest = sha256TripPublication0387(material)
    return "mut_" + digest.take(48) to "idem_" + digest.take(48)
}

internal fun TripPublicationOutboxEvent0387.resolvedMutationId0421(): String =
    mutationId0421.ifBlank { publicationMutationIdentity0421(canonicalTripId, snapshot).first }

internal fun TripPublicationOutboxEvent0387.resolvedIdempotencyKey0421(): String =
    idempotencyKey0421.ifBlank { publicationMutationIdentity0421(canonicalTripId, snapshot).second }

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
    val chain = generateSequence(error) { it.cause }.toList()
    val root = chain.last()
    val remote = chain.filterIsInstance<TripRemoteApiException>().firstOrNull()
    val exceptionMessage = runCatching { UnifiedDebugEventStore.sanitizeForExport(error.message.orEmpty()) }
        .getOrDefault("<sanitization-failed>")
        .take(240)
    val rootMessage = runCatching { UnifiedDebugEventStore.sanitizeForExport(root.message.orEmpty()) }
        .getOrDefault("<sanitization-failed>")
        .take(240)
    return buildString {
        append("exceptionClass=").append(error.javaClass.name)
        append(" exceptionMessage=").append(exceptionMessage)
        append(" rootCauseClass=").append(root.javaClass.name)
        append(" rootCauseMessage=").append(rootMessage)
        remote?.let { value ->
            append(" httpStatus=").append(value.httpStatus)
            append(" backendErrorCode=").append(value.backendErrorCode)
            append(" networkCallId=").append(value.networkCallId)
            append(" transportPhase=").append(value.transportPhase)
            append(" requestBytes=").append(value.requestBytes)
            append(" responseBytes=").append(value.responseBytes)
            append(" requestSha256=").append(value.requestSha256)
            append(" responseSha256=").append(value.responseSha256)
            append(" requestId=").append(value.requestId)
            append(" correlationId=").append(value.correlationId)
        }
    }
}

internal fun sha256TripPublication0387(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
