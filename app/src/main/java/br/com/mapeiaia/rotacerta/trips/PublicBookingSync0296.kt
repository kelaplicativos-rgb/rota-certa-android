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
    suspend fun pullAndReconcile(context: Context, store: TripStore): PublicBookingPullResult {
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
        val candidates = store.trips().filter { !it.remoteId.isNullOrBlank() }
        val externalBindings = store.publicExternalBindings()
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
        val changedExternalRemoteIds = linkedSetOf<String>()
        val externalSeatDeltaByRemoteId = linkedMapOf<String, Int>()
        val externalSeatStateKeyByRemoteId = linkedMapOf<String, String>()
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
                val existing = store.bookings().firstOrNull { it.id == incoming.id }
                val mapped = incoming.toLocalBooking(trip.id, existing)
                if (existing != mapped) {
                    store.saveBooking(mapped)
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
                    val existing = store.bookings().firstOrNull { it.id == incoming.id }
                    val mapped = incoming.toLocalBooking(binding.bookingTripId, existing)
                    if (existing != mapped) {
                        val backingTrip = binding.asTrip()
                        val beforeBookings = store.bookingsFor(binding.bookingTripId)
                        store.saveBooking(mapped)
                        val afterBookings = store.bookingsFor(binding.bookingTripId)
                        val seatDelta = publicBookingSeatDelta(backingTrip, beforeBookings, afterBookings)
                        externalSeatDeltaByRemoteId[binding.remoteTripId] =
                            (externalSeatDeltaByRemoteId[binding.remoteTripId] ?: 0) + seatDelta
                        externalSeatStateKeyByRemoteId[binding.remoteTripId] = seatSyncDiagnosticKey(
                            afterBookings
                                .sortedBy(Booking::id)
                                .joinToString("|") { booking ->
                                    "${booking.id}:${booking.status.name}:${booking.seats}:${booking.boardingStopId}:${booking.dropoffStopId}"
                                },
                        )
                        imported++
                        changed += binding.bookingTripId
                        changedExternalRemoteIds += binding.remoteTripId
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
        val localTrips = store.trips()
        val localEntries = TripTimelineEngine.fromLocalAgenda(localTrips, store.bookings())
        val external = BlaBlaCollectorStateStore(context).lastResponseRecoveringDynamicSessions()
        val merged = BlaBlaTimelineAdapter.merge(localEntries, external)
        var queued = 0
        changed.forEach { localTripId ->
            val trip = store.getTrip(localTripId) ?: return@forEach
            AgendaTrace.event(context, "BOOKING_SEAT_SYNC_REQUESTED", "source=local changedTrip=true", traceId, reconcileOperation.operationId)
            val exact = merged.filter { it.localTripId == localTripId || (it.tripId == localTripId && it.localTripId == localTripId) }
            if (exact.size != 1) {
                AgendaTrace.event(context, "BOOKING_SEAT_SYNC_BLOCKED", "source=local reason=strong_timeline_match_count_${exact.size}", traceId, reconcileOperation.operationId)
                UnifiedDebugEventStore.record("PUBLIC_BOOKING_SEAT_SYNC_PENDING", context.packageName, "localTrip=$localTripId reason=strong_timeline_match_count_${exact.size}")
                return@forEach
            }
            AgendaTrace.event(context, "BOOKING_SEAT_SYNC_IDENTITY_RESOLVED", "source=local resolution=timeline_strong", traceId, reconcileOperation.operationId)
            val result = BlaBlaReliableSeatSyncBridge.enqueueDesiredStateForTimeline(
                context = context,
                entry = exact.single(),
                trip = trip,
                store = store,
                reason = "automatic_after_public_link_booking",
            )
            if (result.shouldSync) {
                queued++
                AgendaTrace.event(context, "BOOKING_SEAT_SYNC_ENQUEUED", "source=local changedTrip=true", traceId, reconcileOperation.operationId)
                AgendaTrace.event(context, "BOOKING_SEAT_SYNC_QUEUE", "source=local changedTrip=true", traceId, reconcileOperation.operationId)
            } else {
                AgendaTrace.event(context, "BOOKING_SEAT_SYNC_BLOCKED", "source=local reason=desired_state_unavailable", traceId, reconcileOperation.operationId)
            }
        }

        changedExternalRemoteIds.forEach { remoteTripId ->
            val binding = store.publicExternalBinding(remoteTripId) ?: return@forEach
            val tripKey = seatSyncDiagnosticKey("${binding.profileUuid}|${binding.blablaTripId}|${binding.remoteTripId}")
            val seatDelta = externalSeatDeltaByRemoteId[remoteTripId] ?: 0
            AgendaTrace.event(context, "BOOKING_SEAT_SYNC_REQUESTED", "source=external changedTrip=true tripKey=$tripKey", traceId, reconcileOperation.operationId)
            val exact = merged.filter(binding::matches)
            val resolution = resolvePublicBookingSeatSyncIdentity(binding, exact.size)
            val result = when (resolution) {
                PublicBookingSeatSyncIdentityResolution.TIMELINE_STRONG -> {
                    AgendaTrace.event(context, "BOOKING_SEAT_SYNC_IDENTITY_RESOLVED", "source=external resolution=timeline_strong tripKey=$tripKey", traceId, reconcileOperation.operationId)
                    BlaBlaReliableSeatSyncBridge.enqueueDesiredStateForTimeline(
                        context = context,
                        entry = exact.single(),
                        trip = binding.asTrip(),
                        store = store,
                        reason = "automatic_after_public_link_booking_external_card",
                    )
                }
                PublicBookingSeatSyncIdentityResolution.PERSISTED_STRONG_BINDING -> {
                    if (seatDelta == 0) {
                        UnifiedDebugEventStore.record("BOOKING_SEAT_SYNC_NOOP", context.packageName, "source=external resolution=persisted_binding reason=segment_bottleneck_unchanged tripKey=$tripKey")
                        return@forEach
                    }
                    AgendaTrace.event(context, "BOOKING_SEAT_SYNC_IDENTITY_RESOLVED", "source=external resolution=persisted_binding profileUuidPresent=true blablaTripIdPresent=true tripKey=$tripKey", traceId, reconcileOperation.operationId)
                    BlaBlaReliableSeatSyncBridge.enqueuePublicBookingDelta(
                        context = context,
                        binding = binding,
                        seatDelta = seatDelta,
                        stateKey = externalSeatStateKeyByRemoteId[remoteTripId].orEmpty(),
                        reason = "automatic_after_public_link_booking_strong_binding",
                    )
                }
                PublicBookingSeatSyncIdentityResolution.BLOCKED -> {
                    AgendaTrace.event(context, "BOOKING_SEAT_SYNC_BLOCKED", "source=external reason=strong_timeline_match_count_${exact.size} strongBinding=false tripKey=$tripKey", traceId, reconcileOperation.operationId)
                    UnifiedDebugEventStore.record(
                        "PUBLIC_BOOKING_EXTERNAL_SEAT_SYNC_PENDING",
                        context.packageName,
                        "remoteTripPresent=true profileUuidPresent=${binding.profileUuid.isNotBlank()} blablaTripIdPresent=${binding.blablaTripId.isNotBlank()} reason=strong_timeline_match_count_${exact.size}",
                    )
                    return@forEach
                }
            }
            if (result.shouldSync) {
                queued++
                AgendaTrace.event(context, "BOOKING_SEAT_SYNC_ENQUEUED", "source=external resolution=${resolution.name.lowercase()} tripKey=$tripKey", traceId, reconcileOperation.operationId)
                AgendaTrace.event(context, "BOOKING_SEAT_SYNC_QUEUE", "source=external changedTrip=true tripKey=$tripKey", traceId, reconcileOperation.operationId)
            } else if (!result.noOp) {
                AgendaTrace.event(context, "BOOKING_SEAT_SYNC_BLOCKED", "source=external reason=${result.blockedReason ?: "enqueue_rejected"} resolution=${resolution.name.lowercase()} tripKey=$tripKey", traceId, reconcileOperation.operationId)
            }
        }
        UnifiedDebugEventStore.record(
            "PUBLIC_BOOKING_PULL_RECONCILED",
            context.packageName,
            "imported=$imported changedTrips=${changed.size} externalCards=${changedExternalRemoteIds.size} seatSyncQueued=$queued",
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
