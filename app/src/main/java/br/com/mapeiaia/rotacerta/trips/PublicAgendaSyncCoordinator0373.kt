package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PublicAgendaSyncRequest0373(
    val rotaCertaSeatAllocation: Int,
    val reason: String,
)

internal data class PublicAgendaSyncCompletion0373(
    val result: PublicAgendaAutoSyncResult?,
    val reason: String,
    val durationMs: Long,
    val errorClass: String = "",
    val errorEvidence: String = "",
)

internal class PublicAgendaSyncCoordinator0373(
    scope: CoroutineScope,
    private val signatureProvider: suspend (Int) -> String,
    private val syncAction: suspend (Int) -> PublicAgendaAutoSyncResult,
    private val eventSink: (String, String) -> Unit = { _, _ -> },
) {
    private val requests = Channel<PublicAgendaSyncRequest0373>(Channel.CONFLATED)
    private val active = AtomicBoolean(false)
    private val _completions = MutableSharedFlow<PublicAgendaSyncCompletion0373>(replay = 1, extraBufferCapacity = 8)
    val completions: SharedFlow<PublicAgendaSyncCompletion0373> = _completions

    private var lastCompletedSignature: String? = null

    init {
        scope.launch {
            for (initial in requests) {
                drain(initial)
            }
        }
    }

    fun request(rotaCertaSeatAllocation: Int, reason: String) {
        val request = PublicAgendaSyncRequest0373(
            rotaCertaSeatAllocation = rotaCertaSeatAllocation.coerceIn(0, 999),
            reason = reason.take(80),
        )
        val wasActive = active.get()
        requests.trySend(request)
        if (wasActive) {
            eventSink(
                "CAPACITY_PUBLIC_SYNC_COALESCED",
                "reason=${request.reason} scope=active_tenant pending=true",
            )
        }
    }

    private suspend fun drain(initial: PublicAgendaSyncRequest0373) {
        var request = initial
        while (true) {
            val before = signatureProvider(request.rotaCertaSeatAllocation)
            if (before == lastCompletedSignature) {
                eventSink(
                    "CAPACITY_PUBLIC_SYNC_IDENTICAL_SKIPPED",
                    "reason=${request.reason} scope=active_tenant signature=${before.take(12)}",
                )
            } else {
                active.set(true)
                val started = System.nanoTime()
                eventSink(
                    "CAPACITY_PUBLIC_SYNC_SINGLE_FLIGHT_START",
                    "reason=${request.reason} scope=active_tenant signature=${before.take(12)}",
                )
                val completion = try {
                    val result = syncAction(request.rotaCertaSeatAllocation)
                    if (result.failures == 0) {
                        lastCompletedSignature = before
                    } else {
                        eventSink(
                            "CAPACITY_PUBLIC_SYNC_PARTIAL_NOT_DEDUPED",
                            "reason=${request.reason} scope=active_tenant failures=${result.failures}",
                        )
                    }
                    PublicAgendaSyncCompletion0373(
                        result = result,
                        reason = request.reason,
                        durationMs = ((System.nanoTime() - started).coerceAtLeast(0L)) / 1_000_000L,
                    )
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    eventSink(
                        "CAPACITY_PUBLIC_SYNC_SINGLE_FLIGHT_CANCELLED",
                        "reason=lifecycle_cancel scope=active_tenant",
                    )
                    throw cancelled
                } catch (error: Throwable) {
                    val evidence = AgendaFailureEvidence.describe(
                        error = error,
                        operation = "CAPACITY_PUBLIC_SYNC",
                        component = "PublicAgendaSyncCoordinator0373",
                        method = "drain",
                    )
                    PublicAgendaSyncCompletion0373(
                        result = null,
                        reason = request.reason,
                        durationMs = ((System.nanoTime() - started).coerceAtLeast(0L)) / 1_000_000L,
                        errorClass = error.javaClass.simpleName,
                        errorEvidence = "signature=${before.take(12)} $evidence",
                    )
                } finally {
                    active.set(false)
                }
                eventSink(
                    "CAPACITY_PUBLIC_SYNC_SINGLE_FLIGHT_END",
                    "reason=${request.reason} scope=active_tenant durationMs=${completion.durationMs} local=${completion.result?.localPublished ?: 0} external=${completion.result?.externalPublished ?: 0} failures=${completion.result?.failures ?: 0} " +
                        completion.errorEvidence.ifBlank { "error=none" },
                )
                _completions.emit(completion)
            }

            val sourceAfter = signatureProvider(request.rotaCertaSeatAllocation)
            val queued = requests.tryReceive().getOrNull()
            val sourceChangedDuringRun = sourceAfter != before
            if (sourceChangedDuringRun) {
                eventSink(
                    "CAPACITY_PUBLIC_SYNC_DIRTY_PENDING",
                    "reason=source_changed_during_run scope=active_tenant before=${before.take(12)} after=${sourceAfter.take(12)}",
                )
            }
            request = when {
                queued != null -> queued
                sourceChangedDuringRun -> request.copy(reason = "source_changed_during_sync")
                else -> break
            }
        }
    }
}

internal fun createPublicAgendaSyncCoordinator0373(
    context: Context,
    store: TripStore,
    scope: CoroutineScope,
): PublicAgendaSyncCoordinator0373 = PublicAgendaSyncCoordinator0373(
    scope = scope,
    signatureProvider = { allocation ->
        withContext(Dispatchers.IO) {
            publicAgendaInputSignature0373(context, store, allocation)
        }
    },
    syncAction = { allocation ->
        withContext(Dispatchers.IO) {
            val traceId = AgendaTrace.currentTraceId()
            val operation = AgendaTrace.operationStart(
                context,
                "CAPACITY_PUBLIC_SYNC",
                "TripApp.publicAgendaCoordinator",
                traceId,
            )
            try {
                PublicAgendaAutoSync0300.sync(
                    context = context,
                    store = store,
                    configuredVehicleCapacity = 0,
                    configuredRotaCertaSeatAllocation = allocation,
                ).also { result ->
                    AgendaTrace.operationEnd(
                        context,
                        operation,
                        result = if (result.failures == 0) "completed" else "partial",
                        processedCount = result.localPublished + result.externalPublished,
                    )
                    AgendaTrace.event(
                        context,
                        "CAPACITY_PUBLIC_AGENDA_SYNC_RESULT",
                        "source=single_flight completed=${result.failures == 0} published=${result.localPublished + result.externalPublished} claims=${result.seatClaimsSynced} remoteBlaBlaMutationConfirmed=false",
                        traceId,
                        operation.operationId,
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                AgendaTrace.operationCancelled(context, operation)
                throw cancelled
            } catch (error: Throwable) {
                AgendaTrace.operationError(context, operation, error)
                throw error
            }
        }
    },
    eventSink = { event, details ->
        UnifiedDebugEventStore.record(event, context.packageName, details)
    },
)

internal fun publicAgendaInputSignature0373(
    context: Context,
    store: TripStore,
    rotaCertaSeatAllocation: Int,
): String {
    val settings = store.onlineSettings()
    val localTrips = store.trips()
        .asSequence()
        .filter(Trip::isCanonicalLocalPublishSource)
        .sortedBy(Trip::id)
        .toList()
    val localIds = localTrips.mapTo(hashSetOf(), Trip::id)
    val localBookings = store.bookings()
        .asSequence()
        .filter { it.tripId in localIds }
        .sortedBy(Booking::id)
        .toList()

    val accounts = BlaBlaDynamicAccountRegistry(context).list()
    val external = BlaBlaDynamicSessionStore(context)
        .combinedResponse(accounts)
        .trips
        .sortedWith(compareBy<BlaBlaCollectorTrip>({ it.profile_uuid }, { it.trip_id.orEmpty() }, { it.date }, { it.departure_time.orEmpty() }))

    val raw = buildString {
        append("allocation=").append(rotaCertaSeatAllocation.coerceIn(0, 999)).append('|')
        append("online=")
        append(settings.apiBaseUrl).append('|')
        append(settings.publicBaseUrl).append('|')
        append(settings.driverToken.isNotBlank()).append('|')
        append(settings.publicCalendarToken.isNotBlank()).append('|')
        append(settings.driverDisplayName).append('|')
        append(settings.driverUsername).append('|')
        append(settings.driverWhatsapp).append('|')
        append(settings.driverPhotoUrl).append('|')
        append(settings.driverPublicAbout).append('|')
        append(settings.driverPublicRating).append('|')
        append(settings.driverPublicReviewCount).append('|')
        append(settings.driverPublicBadge).append('|')
        append(settings.vehicleMakeModel).append('|')
        append(settings.vehicleColor).append('|')
        append(settings.vehicleAmenities).append('|')
        append(settings.driverPreferences).append('|')
        append(settings.paymentInstructions).append('|')
        append(settings.publicProfileMode.name).append('|')
        append(settings.selectedPublicProfileAccountId).append('|')
        append(settings.publicProfileOverrideFields.sorted().joinToString(",")).append(';')

        localTrips.forEach { trip ->
            append("L:")
            append(trip.id).append('|').append(trip.title).append('|').append(trip.departureAtMillis).append('|')
            append(trip.status.name).append('|').append(trip.publishedSeats ?: -1).append('|')
            append(trip.blablaProfileUuid.orEmpty()).append('|').append(trip.blablaTripId.orEmpty()).append('|')
            trip.stops.sortedBy(TripStop::order).forEach { stop ->
                append(stop.id).append('~').append(stop.order).append('~').append(stop.name).append('~')
                append(stop.address).append('~').append(stop.priceToNextCents).append(',')
            }
            append(';')
        }
        localBookings.forEach { booking ->
            append("B:")
            append(booking.id).append('|').append(booking.tripId).append('|').append(booking.boardingStopId).append('|')
            append(booking.dropoffStopId).append('|').append(booking.seats).append('|').append(booking.status.name).append('|')
            append(booking.source.name).append('|').append(booking.capacityClaimType.name).append('|')
            append(booking.sourceReference).append('|').append(booking.occupancyGroupId.orEmpty()).append(';')
        }
        external.forEach { trip ->
            append("E:")
            append(trip.profile_uuid).append('|').append(trip.trip_id.orEmpty()).append('|').append(trip.trip_href.orEmpty()).append('|')
            append(trip.date).append('|').append(trip.departure_time.orEmpty()).append('|').append(trip.arrival_time.orEmpty()).append('|')
            append(trip.actual_departure.orEmpty()).append('|').append(trip.actual_arrival.orEmpty()).append('|')
            append(trip.published_seats ?: -1).append('|').append(trip.booked_seats).append('|')
            append(trip.passenger_roster_complete).append('|').append(trip.itinerary_authoritative).append('|')
            append(trip.itinerary_stops.joinToString(">")).append('|')
            trip.passengers.forEach { passenger ->
                append(passenger.booking_href.orEmpty()).append('~').append(passenger.seats).append('~')
                append(passenger.boarding.orEmpty()).append('~').append(passenger.dropoff.orEmpty()).append(',')
            }
            append(';')
        }
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
