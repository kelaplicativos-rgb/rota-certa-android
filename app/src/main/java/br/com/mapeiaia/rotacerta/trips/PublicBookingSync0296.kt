package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.content.Intent
import br.com.mapeiaia.rotacerta.DebugLogPreferenceStore
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

internal data class PublicBookingPullResult(
    val importedCount: Int,
    val changedTripIds: Set<String>,
    val seatSyncQueued: Int,
)

private data class BookingFetchTarget0373(
    val localTripId: String,
    val remoteTripId: String,
    val publicOnly: Boolean,
    val localCandidate: Boolean,
)

private data class BookingFetchBatch0373(
    val target: BookingFetchTarget0373,
    val bookings: List<RemoteBooking>,
)

internal data class BookingSingleFlightResult0380<T>(
    val value: T,
    val coalesced: Boolean,
)

internal class BookingReconcileSingleFlight0380<T>(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val mutex = Mutex()
    private val flights = mutableMapOf<String, Deferred<T>>()

    suspend fun execute(key: String, block: suspend () -> T): BookingSingleFlightResult0380<T> {
        var coalesced = false
        val deferred = mutex.withLock {
            flights[key]?.takeIf { !it.isCompleted }?.also {
                coalesced = true
            } ?: scope.async { block() }.also { flights[key] = it }
        }
        return try {
            BookingSingleFlightResult0380(deferred.await(), coalesced)
        } finally {
            if (deferred.isCompleted) {
                mutex.withLock {
                    if (flights[key] === deferred) flights.remove(key)
                }
            }
        }
    }
}

internal object PublicBookingRemoteSync0296 {
    private val reconcileSingleFlight = BookingReconcileSingleFlight0380<PublicBookingPullResult>()

    suspend fun pullAndReconcile(context: Context, store: TripStore): PublicBookingPullResult {
        val applicationContext = context.applicationContext
        val scopeKey = store.bookingReconcileScopeKey()
        val flight = reconcileSingleFlight.execute(scopeKey) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                pullAndReconcileOnIo(applicationContext, store)
            }
        }
        if (flight.coalesced) {
            UnifiedDebugEventStore.record(
                "BOOKING_RECONCILE_COALESCED",
                applicationContext.packageName,
                "scope=tenant tenantKey=${seatSyncDiagnosticKey(scopeKey)} reused=true",
            )
        }
        return flight.value
    }

    private suspend fun pullAndReconcileOnIo(context: Context, store: TripStore): PublicBookingPullResult {
        val traceId = AgendaTrace.currentTraceId()
        val reconcileStartedNs = android.os.SystemClock.elapsedRealtimeNanos()
        val reconcileOperation = AgendaTrace.operationStart(
            context,
            "BOOKING_RECONCILE",
            "PublicBookingRemoteSync0296.pullAndReconcile",
            traceId,
        )
        try {
            val settings = store.onlineSettings()
        if (!settings.configured) {
            AgendaTrace.operationEnd(context, reconcileOperation, result = "not_configured", processedCount = 0)
            return PublicBookingPullResult(0, emptySet(), 0)
        }
        val api = TripRemoteApi(settings)
        pullPublicLinkDebugTrace(context, api)

        val localReadOperation = AgendaTrace.operationStart(
            context,
            "BOOKING_LOCAL_READ",
            "PublicBookingRemoteSync0296",
            traceId,
            reconcileOperation.operationId,
        )
        val persistedTrips = store.trips()
        val candidates = persistedTrips.filter {
            it.isCanonicalLocalPublishSource() && !it.remoteId.isNullOrBlank()
        }
        val excludedExternalBackings = persistedTrips.count {
            resolvedTripRecordOrigin(it) == TripRecordOrigin.EXTERNAL_BACKING && !it.remoteId.isNullOrBlank()
        }
        val externalBindings = store.publicExternalBindings()
        UnifiedDebugEventStore.record(
            "BOOKING_RECONCILE_SOURCE_CLASSIFIED",
            context.packageName,
            "localRemoteTrips=${candidates.size} externalBindings=${externalBindings.size} externalBackingsExcluded=$excludedExternalBackings",
        )
        val bookingSnapshot = store.bookings().associateBy(Booking::id).toMutableMap()
        AgendaTrace.operationEnd(
            context,
            localReadOperation,
            processedCount = candidates.size + externalBindings.size,
        )
        if (candidates.isEmpty() && externalBindings.isEmpty()) {
            AgendaTrace.operationEnd(context, reconcileOperation, result = "nothing_to_reconcile", processedCount = 0)
            return PublicBookingPullResult(0, emptySet(), 0)
        }

        val targets = buildList {
            candidates.forEach { trip ->
                val remoteTripId = trip.remoteId ?: return@forEach
                add(
                    BookingFetchTarget0373(
                        localTripId = trip.id,
                        remoteTripId = remoteTripId,
                        publicOnly = false,
                        localCandidate = true,
                    ),
                )
            }
            externalBindings.forEach { binding ->
                add(
                    BookingFetchTarget0373(
                        localTripId = binding.bookingTripId,
                        remoteTripId = binding.remoteTripId,
                        publicOnly = true,
                        localCandidate = false,
                    ),
                )
            }
        }.distinctBy { target ->
            "${target.localTripId}|${target.remoteTripId}|${target.publicOnly}"
        }

        val remoteFetchOperation = AgendaTrace.operationStart(
            context,
            "BOOKING_REMOTE_FETCH",
            "PublicBookingRemoteSync0296",
            traceId,
            reconcileOperation.operationId,
        )
        val fetchSemaphore = Semaphore(BOOKING_FETCH_CONCURRENCY_0373)
        val fetchedBatches = coroutineScope {
            targets.map { target ->
                async {
                    fetchSemaphore.withPermit {
                        runCatching { api.listBookings(target.remoteTripId).bookings }
                            .fold(
                                onSuccess = { remote ->
                                    BookingFetchBatch0373(target, remote)
                                },
                                onFailure = { error ->
                                    val targetTrip = persistedTrips.firstOrNull { it.id == target.localTripId }
                                    val targetContext = targetTrip?.let { trip ->
                                        AgendaFailureEvidence.tripContext(
                                            trip = trip,
                                            bookings = bookingSnapshot.values.filter { it.tripId == trip.id },
                                            tripKey = seatSyncDiagnosticKey(trip.id),
                                            publicIdentity = target.remoteTripId,
                                            origin = resolvedTripRecordOrigin(trip).name,
                                        )
                                    } ?: AgendaFailureTripContext(
                                        tripKey = seatSyncDiagnosticKey(target.localTripId),
                                        canonicalIdentity = target.localTripId,
                                        publicIdentity = target.remoteTripId,
                                        origin = if (target.localCandidate) TripRecordOrigin.LOCAL.name else TripRecordOrigin.EXTERNAL_BACKING.name,
                                    )
                                    UnifiedDebugEventStore.record(
                                        if (target.localCandidate) "PUBLIC_BOOKING_PULL_FAILED"
                                        else "PUBLIC_BOOKING_EXTERNAL_PULL_FAILED",
                                        context.packageName,
                                        AgendaFailureEvidence.describe(
                                            error = error,
                                            operation = "BOOKING_REMOTE_FETCH",
                                            component = "PublicBookingRemoteSync0296",
                                            method = "pullAndReconcileOnIo",
                                            trip = targetContext,
                                        ),
                                    )
                                    null
                                },
                            )
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val remoteFetched = fetchedBatches.sumOf { it.bookings.size }
        AgendaTrace.operationEnd(context, remoteFetchOperation, processedCount = remoteFetched)

        val compareOperation = AgendaTrace.operationStart(
            context,
            "BOOKING_COMPARE",
            "PublicBookingRemoteSync0296",
            traceId,
            reconcileOperation.operationId,
        )
        val pendingImports = mutableListOf<Booking>()
        fetchedBatches.forEach { batch ->
            batch.bookings.asSequence()
                .filter { incoming ->
                    !batch.target.publicOnly ||
                        incoming.source == BookingSource.ROTA_CERTA ||
                        incoming.sourceReference.startsWith("PUBLIC_LINK:")
                }
                .forEach { incoming ->
                    val existing = bookingSnapshot[incoming.id]
                    val mapped = incoming.toLocalBooking(batch.target.localTripId, existing)
                    if (existing != mapped) {
                        pendingImports += mapped
                        bookingSnapshot[mapped.id] = mapped
                    }
                }
        }
        AgendaTrace.operationEnd(context, compareOperation, processedCount = remoteFetched)

        val importOperation = AgendaTrace.operationStart(
            context,
            "BOOKING_IMPORT",
            "PublicBookingRemoteSync0296",
            traceId,
            reconcileOperation.operationId,
        )
        val importedBookings = try {
            store.saveBookingsBatch(
                bookingsToSave = pendingImports,
                preserveSourceUpdatedAt = true,
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            AgendaTrace.operationCancelled(context, importOperation, result = "cancelled")
            throw cancelled
        } catch (error: Throwable) {
            AgendaTrace.operationError(context, importOperation, error)
            throw error
        }
        val imported = importedBookings.size
        val changed = importedBookings.mapTo(linkedSetOf(), Booking::tripId)
        importedBookings.forEach { saved -> bookingSnapshot[saved.id] = saved }
        AgendaTrace.operationEnd(context, importOperation, processedCount = imported)
        UnifiedDebugEventStore.record(
            "BOOKING_IMPORT_BATCH_0380",
            context.packageName,
            "requested=${pendingImports.size} imported=$imported changedTrips=${changed.size} bookingSnapshotWrites=${if (imported > 0) 1 else 0}",
        )
        UnifiedDebugEventStore.record(
            "BOOKING_RECONCILE_PHASES_0373",
            context.packageName,
            "targets=${targets.size} remoteFetched=$remoteFetched pendingImports=${pendingImports.size} imported=$imported fetchConcurrency=$BOOKING_FETCH_CONCURRENCY_0373 externalBackingsExcluded=$excludedExternalBackings",
        )

        if (changed.isEmpty()) {
            val durationMs = ((android.os.SystemClock.elapsedRealtimeNanos() - reconcileStartedNs).coerceAtLeast(0L)) / 1_000_000L
            recordReconcileSlowThresholds(context, traceId, reconcileOperation.operationId, durationMs)
            AgendaTrace.operationEnd(context, reconcileOperation, result = "no_changes", processedCount = remoteFetched)
            return PublicBookingPullResult(imported, changed, 0)
        }
        // Booking changes consume the canonical Rota Certa inventory immediately.
        // The BlaBlaCar seat-editor value is an independent CURRENT-REMAINING input;
        // mutating it here and also counting this booking would subtract the same seat twice.
        // A normal collector refresh remains responsible for observing genuine external changes.
        val queued = 0
        val inventoryUpdated = store.reconcileBookingDerivedInventory(changed)
        val tripsAfterImport = store.trips().associateBy(Trip::id)
        val bookingsAfterImport = store.bookings().groupBy(Booking::tripId)
        changed.forEach { tripId ->
            val trip = tripsAfterImport[tripId] ?: return@forEach
            val bookingsForTrip = bookingsAfterImport[tripId].orEmpty()
            UnifiedDebugEventStore.record(
                "BOOKING_INVENTORY_RECALCULATED",
                context.packageName,
                "trip=$tripId available=${SeatAvailabilityEngine.remainingSeatsForWholeTrip(trip, bookingsForTrip)} externalSeatMutation=false reason=independent_channel_inventory batchInventoryUpdated=$inventoryUpdated",
            )
        }
        UnifiedDebugEventStore.record(
            "PUBLIC_BOOKING_PULL_RECONCILED",
            context.packageName,
            "imported=$imported changedTrips=${changed.size} externalSeatMutation=false seatSyncQueued=$queued",
        )
        val durationMs = ((android.os.SystemClock.elapsedRealtimeNanos() - reconcileStartedNs).coerceAtLeast(0L)) / 1_000_000L
        recordReconcileSlowThresholds(context, traceId, reconcileOperation.operationId, durationMs)
        AgendaTrace.operationEnd(
            context,
            reconcileOperation,
            result = "reconciled",
            processedCount = remoteFetched,
        )
        return PublicBookingPullResult(imported, changed, queued)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            AgendaTrace.operationCancelled(context, reconcileOperation, result = "cancelled")
            throw cancelled
        } catch (error: Throwable) {
            AgendaTrace.operationError(context, reconcileOperation, error)
            throw error
        }
    }

    private const val BOOKING_FETCH_CONCURRENCY_0373 = 4

    private fun recordReconcileSlowThresholds(
        context: Context,
        traceId: String,
        operationId: String,
        durationMs: Long,
    ) {
        listOf(1_000L, 2_000L, 5_000L, 10_000L).forEach { threshold ->
            if (durationMs >= threshold) {
                AgendaTrace.event(
                    context,
                    "BOOKING_RECONCILE_SLOW_${threshold}MS",
                    "durationMs=$durationMs thresholdMs=$threshold",
                    traceId,
                    operationId,
                )
            }
        }
    }

    private suspend fun pullPublicLinkDebugTrace(
        context: Context,
        api: TripRemoteApi,
    ) {
        if (!DebugLogPreferenceStore.isEnabled(context)) return
        val prefs = context.applicationContext.getSharedPreferences(
            "rota_certa_public_debug_import_0302",
            Context.MODE_PRIVATE,
        )
        val lastMillis = prefs.getLong("last_created_at_millis", 0L)
        val seenIds = prefs.getString("seen_ids", "")
            .orEmpty()
            .lineSequence()
            .filter(String::isNotBlank)
            .toCollection(linkedSetOf())

        val response = runCatching {
            api.listPublicDebugEvents(
                afterMillis = (lastMillis - 1_000L).coerceAtLeast(0L),
                limit = 200,
            )
        }.getOrElse { error ->
            UnifiedDebugEventStore.record(
                "PUBLIC_LINK_DEBUG_PULL_FAILED",
                context.packageName,
                AgendaFailureEvidence.describe(
                    error = error,
                    operation = "PUBLIC_LINK_DEBUG_PULL",
                    component = "PublicBookingRemoteSync0296",
                    method = "pullPublicLinkDebugTrace",
                ),
            )
            return
        }

        val fresh = response.events
            .filterNot { it.id in seenIds }
            .sortedWith(compareBy<RemotePublicDebugEvent> { it.createdAtMillis }.thenBy { it.id })

        fresh.forEach { event ->
            UnifiedDebugEventStore.record(
                stage = "PUBLIC_LINK_REMOTE_${event.event}",
                packageName = context.packageName,
                details = buildString {
                    append("source=${event.source}")
                    append(" session=${event.sessionId.take(24)}")
                    append(" target=${event.targetType}:${event.targetRefHash}")
                    append(" screen=${event.screen}")
                    append(" status=${event.statusCode}")
                    if (event.reason.isNotBlank()) append(" reason=${event.reason}")
                    if (event.seats > 0) append(" seats=${event.seats}")
                    if (event.fromIndex >= 0) append(" fromIndex=${event.fromIndex}")
                    if (event.toIndex >= 0) append(" toIndex=${event.toIndex}")
                    if (event.replayed) append(" replayed=true")
                },
                nowMillis = event.createdAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
        }

        val mergedSeen = (fresh.asReversed().map { it.id } + seenIds)
            .distinct()
            .take(200)
        val newestMillis = maxOf(
            lastMillis,
            fresh.maxOfOrNull { it.createdAtMillis } ?: lastMillis,
        )
        prefs.edit()
            .putLong("last_created_at_millis", newestMillis)
            .putString("seen_ids", mergedSeen.joinToString("\n"))
            .apply()

        if (fresh.isNotEmpty()) {
            UnifiedDebugEventStore.record(
                "PUBLIC_LINK_DEBUG_IMPORTED",
                context.packageName,
                "events=${fresh.size} newestMillis=$newestMillis",
            )
        }
    }
}

internal object TripPublicBookingLink0296 {
    fun share(context: Context, url: String): Boolean {
        val safe = url.trim().takeIf { it.startsWith("https://") } ?: return false
        return runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, safe)
                        putExtra(Intent.EXTRA_SUBJECT, "Reserve sua viagem")
                    },
                    "Compartilhar reservas",
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }
}


internal enum class PublicBookingSeatSyncIdentityResolution {
    TIMELINE_STRONG,
    PERSISTED_STRONG_BINDING,
    BLOCKED,
}

internal fun resolvePublicBookingSeatSyncIdentity(
    binding: PublicExternalTripBinding,
    timelineMatchCount: Int,
): PublicBookingSeatSyncIdentityResolution = when {
    timelineMatchCount == 1 -> PublicBookingSeatSyncIdentityResolution.TIMELINE_STRONG
    binding.profileUuid.isNotBlank() && binding.blablaTripId.isNotBlank() ->
        PublicBookingSeatSyncIdentityResolution.PERSISTED_STRONG_BINDING
    else -> PublicBookingSeatSyncIdentityResolution.BLOCKED
}

internal fun publicBookingSeatDelta(
    trip: Trip,
    beforeBookings: List<Booking>,
    afterBookings: List<Booking>,
): Int = SeatAvailabilityEngine.remainingSeatsForWholeTrip(trip, afterBookings) -
    SeatAvailabilityEngine.remainingSeatsForWholeTrip(trip, beforeBookings)
