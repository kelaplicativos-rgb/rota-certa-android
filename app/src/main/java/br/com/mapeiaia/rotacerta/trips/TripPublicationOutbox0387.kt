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
    ): TripPublicationOutboxEvent0387? {
        require(canonicalTripId.isNotBlank()) { "canonicalTripId obrigatório." }
        require(snapshot.semanticSignature.isNotBlank()) { "Assinatura semântica obrigatória." }
        synchronized(LOCK) {
            val events = recoverInterrupted(readEvents()).toMutableList()
            val latest = events.filter { it.canonicalTripId == canonicalTripId }.maxByOrNull { it.revision }
            if (
                latest != null && latest.operation == operation &&
                latest.snapshot.semanticSignature == snapshot.semanticSignature &&
                latest.status != TripPublicationStatus0387.FAILED_FINAL
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
    ): TripPublicationOutboxEvent0387? {
        if (canonicalTripId.isBlank()) return null
        if (reconcileBookingInventory) store.reconcileBookingDerivedInventory(setOf(canonicalTripId))
        val original = store.getTrip(canonicalTripId) ?: return null
        if (!original.isCanonicalLocalPublishSource()) return null
        val bookings = store.bookingsFor(canonicalTripId)
        val allocation = configuredRotaCertaSeatAllocation?.takeIf { it in 0..999 }
            ?: original.rotaCertaSeatAllocation?.takeIf { it in 0..999 } ?: 0
        val allocated = original.copy(rotaCertaSeatAllocation = allocation, publicBookingEnabled = true)
        val publicTrip = allocated.copy(capacity = operationalInventoryCapacity(allocated, bookings))
        val signature = PublicAgendaAutoSync0300.localCapacitySnapshotRevision(publicTrip, bookings, allocation)
        return outbox.enqueue(
            canonicalTripId = canonicalTripId,
            operation = TripPublicationOperation0387.UPSERT_LOCAL,
            mutationType = mutationType,
            source = source,
            snapshot = TripPublicationSnapshot0387(
                trip = publicTrip,
                bookings = bookings,
                semanticSignature = signature,
            ),
        )?.also { event ->
            recordEvent(
                "TRIP_MUTATION_OUTBOX_ENQUEUED",
                event,
                "previousRevision=${event.revision - 1} resultingRevision=${event.revision}",
            )
        }
    }

    fun recordExternalManualMutation(
        sourceTrip: BlaBlaCollectorTrip,
        mutationType: String = "BLABLACAR_MANUAL_CARD_SYNC",
    ): TripPublicationOutboxEvent0387? {
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
        val canonicalTripId = strongExternalCanonicalTripId0387(outbox.tenantId, accountId, profileUuid, tripId)
        val allocation = store.trips()
            .firstOrNull { it.blablaProfileUuid.equals(profileUuid, true) && it.blablaTripId == tripId }
            ?.rotaCertaSeatAllocation?.takeIf { it in 0..999 } ?: 0
        val signature = PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(sourceTrip, allocation)
        return outbox.enqueue(
            canonicalTripId = canonicalTripId,
            operation = TripPublicationOperation0387.UPSERT_EXTERNAL,
            mutationType = mutationType,
            source = "BLABLACAR_MANUAL_CARD",
            snapshot = TripPublicationSnapshot0387(
                externalTrip = sourceTrip,
                externalAccountId = accountId,
                semanticSignature = signature,
            ),
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
        val trip = store.getTrip(canonicalTripId) ?: return null
        val bookings = store.bookingsFor(canonicalTripId)
        val signature = "tombstone-v1:" + sha256TripPublication0387(
            listOf(canonicalTripId, trip.remoteId.orEmpty(), trip.publicToken, mutationType).joinToString("|"),
        )
        return outbox.enqueue(
            canonicalTripId = canonicalTripId,
            operation = TripPublicationOperation0387.TOMBSTONE,
            mutationType = mutationType,
            source = source,
            snapshot = TripPublicationSnapshot0387(trip = trip, bookings = bookings, semanticSignature = signature),
        )?.also { event ->
            recordEvent("TRIP_MUTATION_TOMBSTONE_ENQUEUED", event, "historyPreserved=true blablaMutation=false")
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
                    TripPublicationOperation0387.UPSERT_EXTERNAL -> {
                        val sourceTrip = requireNotNull(event.snapshot.externalTrip) { "Snapshot externo ausente." }
                        require(strongExternalIdentityMatches(event, sourceTrip)) {
                            "Identidade externa forte divergiu do snapshot persistido."
                        }
                        PublicAgendaAutoSync0300.syncExternalTripIncremental(
                            context = appContext,
                            store = store,
                            source = sourceTrip,
                            configuredRotaCertaSeatAllocation = 0,
                            entityRevision = event.revision,
                            outboxEventId = event.id,
                            externalAccountId = event.snapshot.externalAccountId,
                        )
                    }
                    TripPublicationOperation0387.TOMBSTONE -> {
                        val snapshotTrip = requireNotNull(event.snapshot.trip) { "Snapshot de tombstone ausente." }
                        val settings = store.onlineSettings()
                        require(settings.configured) { "Agenda Pública não configurada." }
                        val remoteId = snapshotTrip.remoteId?.takeIf(String::isNotBlank) ?: snapshotTrip.publicToken
                        TripRemoteApi(settings).update(
                            snapshotTrip.copy(
                                remoteId = remoteId,
                                status = TripStatus.CANCELLED,
                                publicBookingEnabled = false,
                                publicationRevision = event.revision,
                                publicationTombstone = true,
                                publicationEventId = event.id,
                            ),
                        )
                    }
                }
                outbox.markDelivered(event.id)
                delivered++
                recordEvent(
                    "TRIP_MUTATION_OUTBOX_DELIVERED",
                    event,
                    "publicationResult=delivered retryCount=${event.attempts} latencyMs=${(System.nanoTime() - startedNs) / 1_000_000L}",
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
            "tenantId=${event.tenantId} canonicalTripId=${seatSyncDiagnosticKey(event.canonicalTripId)} revision=${event.revision} mutationType=${event.mutationType} source=${event.source} destination=${event.destination} operation=${event.operation.name} outboxEventId=${event.id} $extra",
        )
    }
}

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

private fun retryDelayMillis(attempt: Int): Long {
    val multiplier = 1 shl (attempt - 1).coerceIn(0, 6)
    return min(60L * 60L * 1000L, 5_000L * multiplier)
}

internal fun publicationFailureRetryable0387(error: Throwable): Boolean {
    val remote = generateSequence(error) { it.cause }.filterIsInstance<TripRemoteApiException>().firstOrNull()
    return when {
        error is CancellationException -> true
        remote == null -> true
        remote.httpStatus <= 0 -> true
        remote.httpStatus in setOf(408, 409, 425, 429) -> true
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
