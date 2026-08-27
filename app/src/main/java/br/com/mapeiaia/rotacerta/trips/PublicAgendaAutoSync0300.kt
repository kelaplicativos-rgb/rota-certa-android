package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

internal data class PublicAgendaAutoSyncResult(
    val localPublished: Int = 0,
    val externalPublished: Int = 0,
    val seatClaimsSynced: Int = 0,
    val failures: Int = 0,
)

internal data class PublicAgendaExternalTrip(
    val trip: Trip,
    val bookedSeats: Int,
    val sourceReference: String,
    val profileUuid: String = "",
    val blablaTripId: String = "",
    val blablaTripHref: String = "",
)

internal object PublicAgendaAutoSync0300 {
    suspend fun sync(
        context: Context,
        store: TripStore,
        configuredVehicleCapacity: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): PublicAgendaAutoSyncResult {
        val settings = store.onlineSettings()
        if (!settings.configured) return PublicAgendaAutoSyncResult()

        val api = TripRemoteApi(settings)
        var localPublished = 0
        var externalPublished = 0
        var seatClaimsSynced = 0
        var failures = 0

        val localTrips = store.trips()
            .filter { it.departureAtMillis > nowMillis }
            .filter { it.status in PUBLIC_LOCAL_STATUSES }

        localTrips.forEach { original ->
            val publicTrip = original.copy(publicBookingEnabled = true)
            runCatching {
                val response = if (publicTrip.remoteId.isNullOrBlank()) {
                    api.publish(publicTrip)
                } else {
                    api.update(publicTrip)
                }
                store.saveTrip(
                    publicTrip.copy(
                        remoteId = response.tripId,
                        publicToken = response.publicToken,
                        publicUrl = response.publicUrl,
                    ),
                )
                response
            }.onSuccess { response ->
                localPublished++
                runCatching {
                    syncLocalCapacityClaims(
                        api = api,
                        remoteTripId = response.tripId,
                        localTrip = original,
                        localBookings = store.bookingsFor(original.id),
                    )
                }.onSuccess { synced ->
                    seatClaimsSynced += synced
                    UnifiedDebugEventStore.record(
                        "PUBLIC_AGENDA_LOCAL_CAPACITY_SYNCED",
                        context.packageName,
                        "localTrip=${original.id} remoteTripPresent=true claimsSynced=$synced localBookings=${store.bookingsFor(original.id).size}",
                    )
                }.onFailure { error ->
                    failures++
                    UnifiedDebugEventStore.record(
                        "PUBLIC_AGENDA_LOCAL_CAPACITY_SYNC_FAILED",
                        context.packageName,
                        "localTrip=${original.id} remoteTripPresent=true reason=${error.javaClass.simpleName}",
                    )
                }
            }.onFailure { error ->
                failures++
                UnifiedDebugEventStore.record(
                    "PUBLIC_AGENDA_LOCAL_PUBLISH_FAILED",
                    context.packageName,
                    "localTrip=${original.id} reason=${error.javaClass.simpleName}",
                )
            }
        }

        val capacity = configuredVehicleCapacity.takeIf { it in 1..999 } ?: 4
        val externalTrips = BlaBlaCollectorStateStore(context)
            .lastResponseRecoveringDynamicSessions()
            ?.trips
            .orEmpty()
            .asSequence()
            .filterNot(BlaBlaCollectorTrip::identity_conflict)
            .mapNotNull { toPublicTrip(it, capacity, nowMillis) }
            .filterNot { synthesized ->
                localTrips.any { local -> samePhysicalTrip(local, synthesized.trip) }
            }
            .distinctBy { it.trip.publicToken }
            .take(100)
            .toList()

        externalTrips.forEach { synthesized ->
            val publicTrip = synthesized.trip
            runCatching {
                val response = runCatching { api.publish(publicTrip) }.getOrElse {
                    api.update(publicTrip.copy(remoteId = publicTrip.publicToken))
                }
                if (synthesized.bookedSeats > 0) {
                    val ordered = publicTrip.stops.sortedBy(TripStop::order)
                    val claim = Booking(
                        id = "blablacar-${publicTrip.publicToken.take(40)}",
                        tripId = publicTrip.id,
                        passengerName = "Vagas já ocupadas na BlaBlaCar",
                        boardingStopId = ordered.first().id,
                        dropoffStopId = ordered.last().id,
                        seats = synthesized.bookedSeats.coerceAtMost(publicTrip.capacity),
                        status = BookingStatus.CONFIRMED,
                        source = BookingSource.BLABLACAR,
                        capacityClaimType = CapacityClaimType.RESERVED_SEAT,
                        sourceReference = synthesized.sourceReference,
                        occupancyGroupId = "blablacar:${publicTrip.publicToken}",
                    )
                    api.upsertDriverBooking(response.tripId, claim)
                    seatClaimsSynced++
                }
            }.onSuccess { response ->
                store.savePublicExternalBinding(
                    PublicExternalTripBinding(
                        remoteTripId = response.tripId,
                        publicToken = response.publicToken,
                        bookingTripId = "public-external:${response.tripId}",
                        profileUuid = synthesized.profileUuid,
                        blablaTripId = synthesized.blablaTripId,
                        blablaTripHref = synthesized.blablaTripHref,
                        title = publicTrip.title,
                        departureAtMillis = publicTrip.departureAtMillis,
                        capacity = publicTrip.capacity,
                        stops = publicTrip.stops,
                    ),
                )
                UnifiedDebugEventStore.record(
                    "PUBLIC_EXTERNAL_BINDING_SAVED",
                    context.packageName,
                    "remoteTripPresent=true profileUuidPresent=${synthesized.profileUuid.isNotBlank()} blablaTripIdPresent=${synthesized.blablaTripId.isNotBlank()}",
                )
                externalPublished++
            }.onFailure {
                failures++
            }
        }

        return PublicAgendaAutoSyncResult(
            localPublished = localPublished,
            externalPublished = externalPublished,
            seatClaimsSynced = seatClaimsSynced,
            failures = failures,
        )
    }

    private suspend fun syncLocalCapacityClaims(
        api: TripRemoteApi,
        remoteTripId: String,
        localTrip: Trip,
        localBookings: List<Booking>,
    ): Int {
        val mirrors = localCapacityMirrors(localTrip, localBookings)

        val currentMirrorIds = mirrors.map(Booking::id).toSet()
        val remoteMirrorBookings = api.listBookings(remoteTripId).bookings
            .filter { it.sourceReference.startsWith(LOCAL_MIRROR_PREFIX) }

        var synced = 0

        remoteMirrorBookings
            .filterNot { it.id in currentMirrorIds }
            .filterNot { it.status == BookingStatus.CANCELLED.name || it.status == BookingStatus.EXPIRED.name }
            .forEach { stale ->
                api.upsertDriverBooking(
                    remoteTripId = remoteTripId,
                    booking = stale.toLocalBooking(localTrip.id).copy(
                        passengerName = "Ocupação sincronizada",
                        passengerContact = "",
                        status = BookingStatus.CANCELLED,
                    ),
                )
                synced++
            }

        mirrors.forEach { mirror ->
            api.upsertDriverBooking(remoteTripId, mirror)
            synced++
        }

        return synced
    }

    internal fun localCapacityMirrors(
        localTrip: Trip,
        localBookings: List<Booking>,
    ): List<Booking> = localBookings
        .filterNot { it.source == BookingSource.ROTA_CERTA }
        .map { booking ->
            val fingerprint = sha256(booking.id).take(32)
            booking.copy(
                id = "mirror-$fingerprint",
                tripId = localTrip.id,
                passengerName = "Ocupação sincronizada",
                passengerContact = "",
                sourceReference = "$LOCAL_MIRROR_PREFIX$fingerprint",
                occupancyGroupId = booking.occupancyGroupId ?: "local:$fingerprint",
            )
        }

    internal fun toPublicTrip(
        source: BlaBlaCollectorTrip,
        capacity: Int,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PublicAgendaExternalTrip? {
        val departure = parseDateTime(source.date, source.departure_time, zoneId) ?: return null
        if (departure <= nowMillis) return null

        val origin = source.actual_departure?.takeIf(String::isNotBlank)
            ?: source.search_from?.takeIf(String::isNotBlank)
            ?: return null
        val destination = source.actual_arrival?.takeIf(String::isNotBlank)
            ?: source.search_to?.takeIf(String::isNotBlank)
            ?: return null
        if (normalizePlace(origin) == normalizePlace(destination)) return null

        var arrival = parseDateTime(source.date, source.arrival_time, zoneId)
        if (arrival != null && arrival < departure) arrival += DAY_MILLIS

        val identity = stableIdentity(source)
        val token = "bb${sha256(identity).take(30)}"
        val safeCapacity = capacity.coerceIn(1, 999)
        val booked = source.booked_seats.coerceAtLeast(source.passengers.sumOf { it.seats.coerceAtLeast(1) })
        val priceCents = parsePriceCents(source.price)

        val trip = Trip(
            id = "public:$token",
            title = "${shortPlace(origin)} → ${shortPlace(destination)}",
            departureAtMillis = departure,
            capacity = safeCapacity,
            status = if (source.availability.equals("full", true) || booked >= safeCapacity) TripStatus.FULL else TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(
                    id = "origin-$token",
                    order = 0,
                    name = shortPlace(origin),
                    address = origin,
                    plannedDepartureMillis = departure,
                    priceToNextCents = priceCents,
                ),
                TripStop(
                    id = "destination-$token",
                    order = 1,
                    name = shortPlace(destination),
                    address = destination,
                    plannedArrivalMillis = arrival,
                ),
            ),
            publicToken = token,
            notes = "",
            remoteId = token,
            publicBookingEnabled = true,
        )
        return PublicAgendaExternalTrip(
            trip = trip,
            bookedSeats = booked.coerceAtMost(safeCapacity),
            sourceReference = source.trip_id.orEmpty().ifBlank { source.trip_href.orEmpty() }.ifBlank { "BLABLACAR:$token" },
            profileUuid = source.profile_uuid.trim(),
            blablaTripId = source.trip_id.orEmpty().trim(),
            blablaTripHref = source.trip_href.orEmpty().trim(),
        )
    }

    internal fun parsePriceCents(raw: String?): Long {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return 0L
        val match = Regex("""(\d{1,4}(?:[.,]\d{1,2})?)""").find(value)?.groupValues?.getOrNull(1) ?: return 0L
        val normalized = match.replace(".", "").replace(",", ".")
        return ((normalized.toDoubleOrNull() ?: return 0L) * 100.0).toLong().coerceAtLeast(0L)
    }

    private fun parseDateTime(dateRaw: String, timeRaw: String?, zoneId: ZoneId): Long? = runCatching {
        val time = timeRaw?.trim()?.takeIf(String::isNotEmpty) ?: return@runCatching null
        LocalDate.parse(dateRaw.trim()).atTime(LocalTime.parse(time.take(5))).atZone(zoneId).toInstant().toEpochMilli()
    }.getOrNull()

    private fun samePhysicalTrip(left: Trip, right: Trip): Boolean {
        if (abs(left.departureAtMillis - right.departureAtMillis) > 45L * 60L * 1000L) return false
        val leftStops = left.stops.sortedBy(TripStop::order)
        val rightStops = right.stops.sortedBy(TripStop::order)
        val leftOrigin = leftStops.firstOrNull()?.name.orEmpty()
        val leftDestination = leftStops.lastOrNull()?.name.orEmpty()
        val rightOrigin = rightStops.firstOrNull()?.name.orEmpty()
        val rightDestination = rightStops.lastOrNull()?.name.orEmpty()
        return normalizePlace(leftOrigin) == normalizePlace(rightOrigin) &&
            normalizePlace(leftDestination) == normalizePlace(rightDestination)
    }

    private fun stableIdentity(source: BlaBlaCollectorTrip): String = listOf(
        source.profile_uuid.trim(),
        source.trip_id.orEmpty().trim(),
        source.trip_href.orEmpty().trim(),
        source.date.trim(),
        source.departure_time.orEmpty().trim(),
        source.actual_departure.orEmpty().trim(),
        source.actual_arrival.orEmpty().trim(),
        source.search_from.orEmpty().trim(),
        source.search_to.orEmpty().trim(),
    ).joinToString("|")

    private fun shortPlace(value: String): String = value.substringBefore(',').trim().ifBlank { value.trim() }

    private fun normalizePlace(value: String): String = java.text.Normalizer
        .normalize(shortPlace(value), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private val PUBLIC_LOCAL_STATUSES = setOf(
        TripStatus.PUBLISHED,
        TripStatus.FULL,
        TripStatus.STARTING,
        TripStatus.ACTIVE,
    )

    private const val LOCAL_MIRROR_PREFIX = "LOCAL_MIRROR:"
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
}
