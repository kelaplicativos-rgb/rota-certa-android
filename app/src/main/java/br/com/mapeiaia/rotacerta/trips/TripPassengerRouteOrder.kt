package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.Coordinate
import java.text.Normalizer
import kotlin.math.cos
import kotlin.math.hypot

internal data class TripRouteProgress(
    /** Fractional index in the ordered stop list: 0.0 is the first stop, 1.5 is halfway from stop 1 to 2. */
    val stopIndexProgress: Double,
    val corridorDistanceKm: Double,
)

internal object TripPassengerRouteOrder {
    private const val MAX_TRUSTED_CORRIDOR_DISTANCE_KM = 50.0
    private const val NEXT_STOP_TOLERANCE = 0.35

    fun stopIndexForId(trip: Trip, stopId: String?): Int? {
        val id = stopId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val index = trip.stops.sortedBy(TripStop::order).indexOfFirst { it.id == id }
        return index.takeIf { it >= 0 }
    }

    /** Text matching is accepted only when exactly one TripStop is compatible. */
    fun stopIndexForLabel(trip: Trip, label: String?): Int? {
        val key = placeKey(label.orEmpty())
        if (key.isBlank()) return null
        val matches = trip.stops.sortedBy(TripStop::order).mapIndexedNotNull { index, stop ->
            val candidates = listOf(stop.name, stop.address).map(::placeKey).filter(String::isNotBlank)
            val compatible = candidates.any { candidate ->
                candidate == key ||
                    (candidate.length >= 5 && key.contains(candidate)) ||
                    (key.length >= 5 && candidate.contains(key))
            }
            index.takeIf { compatible }
        }.distinct()
        return matches.singleOrNull()
    }

    /**
     * Uses the stored stop coordinates as a light-weight route polyline. GPS never
     * changes the macro stop order; it only estimates progress along that order.
     */
    fun progress(trip: Trip, current: Coordinate?): TripRouteProgress? {
        current ?: return null
        val ordered = trip.stops.sortedBy(TripStop::order)
        if (ordered.size < 2) return null
        val points = ordered.map { stop ->
            val lat = stop.latitude ?: return null
            val lon = stop.longitude ?: return null
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            Coordinate(lat, lon)
        }
        if (!current.latitude.isFinite() || !current.longitude.isFinite()) return null

        var bestDistanceKm = Double.POSITIVE_INFINITY
        var bestProgress = 0.0
        for (index in 0 until points.lastIndex) {
            val projection = projectToSegment(current, points[index], points[index + 1])
            if (projection.distanceKm < bestDistanceKm) {
                bestDistanceKm = projection.distanceKm
                bestProgress = index.toDouble() + projection.t
            }
        }
        if (!bestDistanceKm.isFinite() || bestDistanceKm > MAX_TRUSTED_CORRIDOR_DISTANCE_KM) return null
        return TripRouteProgress(bestProgress, bestDistanceKm)
    }

    fun isNextBoarding(boardingStopIndex: Int?, progress: TripRouteProgress?): Boolean {
        val boarding = boardingStopIndex ?: return false
        val current = progress?.stopIndexProgress ?: return false
        return boarding.toDouble() >= current - NEXT_STOP_TOLERANCE
    }

    private data class Projection(val t: Double, val distanceKm: Double)

    private fun projectToSegment(point: Coordinate, start: Coordinate, end: Coordinate): Projection {
        val latitudeRefRadians = Math.toRadians((start.latitude + end.latitude + point.latitude) / 3.0)
        val lonScale = cos(latitudeRefRadians).coerceAtLeast(0.01)
        fun x(coordinate: Coordinate): Double = coordinate.longitude * lonScale
        fun y(coordinate: Coordinate): Double = coordinate.latitude

        val ax = x(start)
        val ay = y(start)
        val bx = x(end)
        val by = y(end)
        val px = x(point)
        val py = y(point)
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared <= 1e-12) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val projectedX = ax + t * dx
        val projectedY = ay + t * dy
        val degreesDistance = hypot(px - projectedX, py - projectedY)
        return Projection(t = t, distanceKm = degreesDistance * 111.32)
    }

    private fun placeKey(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
