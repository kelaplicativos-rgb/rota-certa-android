package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.content.Intent
import br.com.mapeiaia.rotacerta.DebugLogPreferenceStore
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore

internal data class PublicBookingPullResult(
    val importedCount: Int,
    val changedTripIds: Set<String>,
    val seatSyncQueued: Int,
)

internal object PublicBookingRemoteSync0296 {
    suspend fun pullAndReconcile(context: Context, store: TripStore): PublicBookingPullResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            pullAndReconcileOnIo(context, store)
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

        val remoteFetchOperation = AgendaTrace.operationStart(
            context,
            "BOOKING_REMOTE_FETCH",
            "PublicBookingRemoteSync0296",
            traceId,
            reconcileOperation.operationId,
        )
        val compareOperation = AgendaTrace.operationStart(
            context,
            "BOOKING_COMPARE",
            "PublicBookingRemoteSync0296",
            traceId,
            reconcileOperation.operationId,
        )
        val importOperation = AgendaTrace.operationStart(
            context,
            "BOOKING_IMPORT",
            "PublicBookingRemoteSync0296",
            traceId,
            reconcileOperation.operationId,
        )
        var remoteFetched = 0
        var imported = 0
        val changed = linkedSetOf<String>()
        candidates.forEach { trip ->
            val remoteTripId = trip.remoteId ?: return@forEach
            val remote = runCatching { api.listBookings(remoteTripId).bookings }
                .getOrElse { error ->
                    UnifiedDebugEventStore.record(
                        "PUBLIC_BOOKING_PULL_FAILED",
                        context.packageName,
                        "localTrip=${trip.id} remoteTripPresent=true reason=${error.javaClass.simpleName}",
                    )
                    return@forEach
                }
            remoteFetched += remote.size
            remote.forEach { incoming ->
                val existing = bookingSnapshot[incoming.id]
                val mapped = incoming.toLocalBooking(trip.id, existing)
                if (existing != mapped) {
                    val saved = store.saveBooking(mapped)
                    bookingSnapshot[saved.id] = saved
                    imported++
                    changed += trip.id
                }
            }
        }

        externalBindings.forEach { binding ->
            val remote = runCatching { api.listBookings(binding.remoteTripId).bookings }
                .getOrElse { error ->
                    UnifiedDebugEventStore.record(
                        "PUBLIC_BOOKING_EXTERNAL_PULL_FAILED",
                        context.packageName,
                        "remoteTripPresent=true reason=${error.javaClass.simpleName}",
                    )
                    return@forEach
                }
            remoteFetched += remote.size
            remote.asSequence()
                .filter { incoming ->
                    incoming.source == BookingSource.ROTA_CERTA ||
                        incoming.sourceReference.startsWith("PUBLIC_LINK:")
                }
                .forEach { incoming ->
                    val existing = bookingSnapshot[incoming.id]
                    val mapped = incoming.toLocalBooking(binding.bookingTripId, existing)
                    if (existing != mapped) {
                        val saved = store.saveBooking(mapped)
                        bookingSnapshot[saved.id] = saved
                        imported++
                        changed += binding.bookingTripId
                    }
                }
        }

        AgendaTrace.operationEnd(context, remoteFetchOperation, processedCount = remoteFetched)
        AgendaTrace.operationEnd(context, compareOperation, processedCount = remoteFetched)
        AgendaTrace.operationEnd(context, importOperation, processedCount = imported)

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
        changed.forEach { tripId ->
            val trip = store.getTrip(tripId) ?: return@forEach
            val bookingsForTrip = store.bookingsFor(tripId)
            val derivedCapacity = operationalInventoryCapacity(trip, bookingsForTrip)
            if (trip.capacity != derivedCapacity) {
                store.saveTrip(trip.copy(capacity = derivedCapacity))
            }
            UnifiedDebugEventStore.record(
                "BOOKING_INVENTORY_RECALCULATED",
                context.packageName,
                "trip=$tripId available=${SeatAvailabilityEngine.remainingSeatsForWholeTrip(trip.copy(capacity = derivedCapacity), bookingsForTrip)} externalSeatMutation=false reason=independent_channel_inventory",
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
    }

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
                "reason=${error.javaClass.simpleName}",
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
