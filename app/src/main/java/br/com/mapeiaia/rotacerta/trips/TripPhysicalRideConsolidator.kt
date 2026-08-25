package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class TimelineGeoPoint(val latitude: Double, val longitude: Double)

object TripTimelineGeoResolver {
    /**
     * Text-only geocoding is intentionally not a continuity authority. Only
     * coordinates already persisted on trusted TripStop records may feed this
     * map. A label with conflicting coordinates fails closed.
     */
    suspend fun resolve(
        @Suppress("UNUSED_PARAMETER") context: Context,
        places: Collection<String>,
        trustedStops: Collection<TripStop> = emptyList(),
    ): Map<String, TimelineGeoPoint> = resolveTrustedStops(places, trustedStops)

    internal fun resolveTrustedStops(
        places: Collection<String>,
        trustedStops: Collection<TripStop>,
    ): Map<String, TimelineGeoPoint> {
        val candidatesByKey = mutableMapOf<String, MutableList<TimelineGeoPoint>>()
        trustedStops.forEach { stop ->
            val lat = stop.latitude ?: return@forEach
            val lon = stop.longitude ?: return@forEach
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@forEach
            val point = TimelineGeoPoint(lat, lon)
            listOf(stop.name, stop.address)
                .asSequence()
                .map(::geoPlaceKey)
                .filter(String::isNotBlank)
                .distinct()
                .forEach { key -> candidatesByKey.getOrPut(key) { mutableListOf() } += point }
        }

        return buildMap {
            places.distinct().forEach { place ->
                val key = geoPlaceKey(place)
                if (key.isBlank()) return@forEach
                val distinct = candidatesByKey[key]
                    .orEmpty()
                    .distinctBy { point -> point.latitude to point.longitude }
                if (distinct.size == 1) put(place, distinct.single())
            }
        }
    }

    private fun geoPlaceKey(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}

/**
 * Consolidates mirrors/subsegments before fatal logistics validation.  A same
 * date/time alone is NEVER enough: text endpoint evidence or same-direction
 * geocoded corridor evidence is required.
 */
object TripPhysicalRideConsolidator {
    fun consolidate(entriesRaw: List<TripTimelineEntry>, geo: Map<String, TimelineGeoPoint>): List<TripTimelineEntry> {
        if (entriesRaw.size < 2) return validate(entriesRaw.map(::recalculateOccupancy), geo)
        val pending = entriesRaw.sortedBy(TripTimelineEntry::departureAtMillis).toMutableList()
        val result = mutableListOf<TripTimelineEntry>()
        while (pending.isNotEmpty()) {
            val seed = pending.removeAt(0)
            val group = mutableListOf(seed)
            var changed: Boolean
            do {
                changed = false
                val iterator = pending.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    if (group.any { samePhysicalRide(it, candidate, geo) }) {
                        group += candidate
                        iterator.remove()
                        changed = true
                    }
                }
            } while (changed)
            result += mergeGroup(group, geo)
        }
        return validate(result, geo)
    }

    private fun samePhysicalRide(a: TripTimelineEntry, b: TripTimelineEntry, geo: Map<String, TimelineGeoPoint>): Boolean {
        val zone = ZoneId.systemDefault()
        val dayA = Instant.ofEpochMilli(a.departureAtMillis).atZone(zone).toLocalDate()
        val dayB = Instant.ofEpochMilli(b.departureAtMillis).atZone(zone).toLocalDate()
        if (dayA != dayB) return false
        if (abs(a.departureAtMillis - b.departureAtMillis) > 90L * 60L * 1000L) return false
        if (!a.blablaTripId.isNullOrBlank() && a.blablaTripId == b.blablaTripId) return true
        if (samePlace(a.origin, b.origin) && samePlace(a.destination, b.destination)) return true
        val ao = geo[a.origin] ?: return false
        val ad = geo[a.destination] ?: return false
        val bo = geo[b.origin] ?: return false
        val bd = geo[b.destination] ?: return false
        val bearingDiff = angleDiff(bearing(ao, ad), bearing(bo, bd))
        if (bearingDiff > 35.0) return false
        val aLen = distanceKm(ao, ad)
        val bLen = distanceKm(bo, bd)
        if (aLen < 15.0 || bLen < 15.0) return false
        val broadO: TimelineGeoPoint
        val broadD: TimelineGeoPoint
        val shortO: TimelineGeoPoint
        val shortD: TimelineGeoPoint
        val broadLen: Double
        if (aLen >= bLen) {
            broadO = ao; broadD = ad; shortO = bo; shortD = bd; broadLen = aLen
        } else {
            broadO = bo; broadD = bd; shortO = ao; shortD = ad; broadLen = bLen
        }
        val originDetour = distanceKm(broadO, shortO) + distanceKm(shortO, broadD) - broadLen
        val destDetour = distanceKm(broadO, shortD) + distanceKm(shortD, broadD) - broadLen
        return originDetour <= 70.0 && destDetour <= 70.0
    }

    private fun mergeGroup(group: List<TripTimelineEntry>, geo: Map<String, TimelineGeoPoint>): TripTimelineEntry {
        if (group.size == 1) return recalculateOccupancy(group.single()).copy(
            issues = group.single().issues - setOf(TripTimelineIssue.PHYSICAL_CONFLICT, TripTimelineIssue.PROFILE_CONTINUITY, TripTimelineIssue.DUPLICATE),
        )
        val canonical = group.maxByOrNull { entry ->
            val o = geo[entry.origin]
            val d = geo[entry.destination]
            if (o != null && d != null) distanceKm(o, d) else if (!entry.blablaTripHref.isNullOrBlank()) 1.0 else 0.0
        } ?: group.first()
        val sources = BookingSource.values().mapNotNull { source ->
            val values = group.map { it.sourcePassengerSeats[source] ?: 0 }.filter { it > 0 }
            if (values.isEmpty()) null else source to when (source) {
                BookingSource.BLABLACAR, BookingSource.ROTA_CERTA -> values.maxOrNull() ?: 0
                BookingSource.PRIVATE, BookingSource.OTHER -> values.sum()
            }
        }.toMap()
        val occupied = sources.values.sum()
        val capacity = group.maxOfOrNull(TripTimelineEntry::capacity) ?: 0
        val external = group.firstOrNull { !it.blablaTripHref.isNullOrBlank() }
        val local = group.firstOrNull { it.localTripId != null }
        val cleanIssues = group.flatMap(TripTimelineEntry::issues).toSet() - setOf(
            TripTimelineIssue.PHYSICAL_CONFLICT,
            TripTimelineIssue.PROFILE_CONTINUITY,
            TripTimelineIssue.DUPLICATE,
            TripTimelineIssue.OVERBOOKING,
        )
        val issues = if (capacity > 0 && occupied > capacity) cleanIssues + TripTimelineIssue.OVERBOOKING else cleanIssues
        val status = when {
            capacity > 0 && occupied >= capacity -> TripStatus.FULL
            local != null -> local.status
            else -> canonical.status
        }
        return canonical.copy(
            tripId = local?.tripId ?: canonical.tripId,
            localTripId = local?.localTripId,
            blablaTripId = external?.blablaTripId,
            blablaTripHref = external?.blablaTripHref,
            blablaProfileUuid = external?.blablaProfileUuid,
            blablaPrice = external?.blablaPrice,
            blablaAvailability = external?.blablaAvailability,
            blablaPassengers = BlaBlaCollectorPassengerModule.coalesceDuplicateEvidence(group.flatMap(TripTimelineEntry::blablaPassengers)),
            profileId = external?.profileId ?: canonical.profileId,
            profileLabel = external?.profileLabel ?: canonical.profileLabel,
            capacity = capacity,
            minimumOccupiedSeats = occupied,
            maximumOccupiedSeats = occupied,
            sourcePassengerSeats = sources,
            issues = issues,
        )
    }

    private fun recalculateOccupancy(entry: TripTimelineEntry): TripTimelineEntry {
        val occupied = entry.sourcePassengerSeats.values.sum()
        if (entry.sourcePassengerSeats.isEmpty()) return entry
        val cleanIssues = entry.issues - TripTimelineIssue.OVERBOOKING
        val issues = if (entry.capacity > 0 && occupied > entry.capacity) cleanIssues + TripTimelineIssue.OVERBOOKING else cleanIssues
        val status = if (entry.capacity > 0 && occupied >= entry.capacity) TripStatus.FULL else entry.status
        return entry.copy(
            minimumOccupiedSeats = occupied,
            maximumOccupiedSeats = occupied,
            status = status,
            issues = issues,
        )
    }

    private fun validate(entriesRaw: List<TripTimelineEntry>, geo: Map<String, TimelineGeoPoint>): List<TripTimelineEntry> {
        val entries = entriesRaw.sortedBy(TripTimelineEntry::departureAtMillis)
        val issueMap = entries.associate { entry ->
            entry.tripId to (entry.issues - setOf(TripTimelineIssue.PHYSICAL_CONFLICT, TripTimelineIssue.PROFILE_CONTINUITY)).toMutableSet()
        }.toMutableMap()
        entries.forEach { entry ->
            if (entry.capacity > 0 && entry.maximumOccupiedSeats > entry.capacity) issueMap.getValue(entry.tripId) += TripTimelineIssue.OVERBOOKING
        }
        entries.zipWithNext().forEach { (previous, next) ->
            val end = previous.arrivalAtMillis
            if (end != null && next.departureAtMillis < end) {
                issueMap.getValue(previous.tripId) += TripTimelineIssue.PHYSICAL_CONFLICT
                issueMap.getValue(next.tripId) += TripTimelineIssue.PHYSICAL_CONFLICT
            } else if (hasTrustedContinuityEvidence(previous.destination, next.origin, geo) && !continuous(previous.destination, next.origin, geo)) {
                issueMap.getValue(next.tripId) += TripTimelineIssue.PROFILE_CONTINUITY
            }
        }
        return entries.map { it.copy(issues = issueMap.getValue(it.tripId).toSet()) }
    }

    private fun hasTrustedContinuityEvidence(destination: String, origin: String, geo: Map<String, TimelineGeoPoint>): Boolean {
        if (samePlace(destination, origin)) return true
        return geo[destination] != null && geo[origin] != null
    }

    private fun continuous(destination: String, origin: String, geo: Map<String, TimelineGeoPoint>): Boolean {
        if (samePlace(destination, origin)) return true
        val a = geo[destination] ?: return false
        val b = geo[origin] ?: return false
        return distanceKm(a, b) <= 35.0
    }

    private fun samePlace(a: String, b: String): Boolean {
        val left = key(a); val right = key(b)
        if (left.isBlank() || right.isBlank()) return false
        return left == right || (left.length >= 5 && right.contains(left)) || (right.length >= 5 && left.contains(right))
    }

    private fun key(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun distanceKm(a: TimelineGeoPoint, b: TimelineGeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun bearing(a: TimelineGeoPoint, b: TimelineGeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude); val lat2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun angleDiff(a: Double, b: Double): Double {
        val raw = abs(a - b) % 360.0
        return if (raw > 180.0) 360.0 - raw else raw
    }
}
