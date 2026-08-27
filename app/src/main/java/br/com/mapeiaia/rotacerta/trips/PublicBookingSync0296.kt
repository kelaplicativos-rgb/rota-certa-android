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
        val settings = store.onlineSettings()
        if (!settings.configured) return PublicBookingPullResult(0, emptySet(), 0)
        val api = TripRemoteApi(settings)
        pullPublicLinkDebugTrace(context, api)
        val candidates = store.trips().filter { !it.remoteId.isNullOrBlank() }
        if (candidates.isEmpty()) return PublicBookingPullResult(0, emptySet(), 0)

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

        if (changed.isEmpty()) return PublicBookingPullResult(imported, changed, 0)
        val localTrips = store.trips()
        val localEntries = TripTimelineEngine.fromLocalAgenda(localTrips, store.bookings())
        val external = BlaBlaCollectorStateStore(context).lastResponseRecoveringDynamicSessions()
        val merged = BlaBlaTimelineAdapter.merge(localEntries, external)
        var queued = 0
        changed.forEach { localTripId ->
            val trip = store.getTrip(localTripId) ?: return@forEach
            val exact = merged.filter { it.localTripId == localTripId || (it.tripId == localTripId && it.localTripId == localTripId) }
            if (exact.size != 1) {
                UnifiedDebugEventStore.record(
                    "PUBLIC_BOOKING_SEAT_SYNC_PENDING",
                    context.packageName,
                    "localTrip=$localTripId reason=strong_timeline_match_count_${exact.size}",
                )
                return@forEach
            }
            val result = BlaBlaReliableSeatSyncBridge.enqueueDesiredStateForTimeline(
                context = context,
                entry = exact.single(),
                trip = trip,
                store = store,
                reason = "automatic_after_public_link_booking",
            )
            if (result.shouldSync) queued++
        }
        UnifiedDebugEventStore.record(
            "PUBLIC_BOOKING_PULL_RECONCILED",
            context.packageName,
            "imported=$imported changedTrips=${changed.size} seatSyncQueued=$queued",
        )
        return PublicBookingPullResult(imported, changed, queued)
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
