package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.content.Intent
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
        val candidates = store.trips().filter { !it.remoteId.isNullOrBlank() }
        if (candidates.isEmpty()) return PublicBookingPullResult(0, emptySet(), 0)

        var imported = 0
        val changed = linkedSetOf<String>()
        candidates.forEach { trip ->
            val remoteTripId = trip.remoteId ?: return@forEach
            val remote = runCatching { TripRemoteApi(settings).listBookings(remoteTripId).bookings }
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
