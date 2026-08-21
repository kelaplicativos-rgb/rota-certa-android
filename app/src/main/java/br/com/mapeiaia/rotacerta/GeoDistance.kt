package br.com.mapeiaia.rotacerta

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object GeoDistance {
    fun meters(from: Coordinate, to: Coordinate): Double {
        val latDelta = Math.toRadians(to.latitude - from.latitude)
        val lonDelta = Math.toRadians(to.longitude - from.longitude)
        val fromLat = Math.toRadians(from.latitude)
        val toLat = Math.toRadians(to.latitude)
        val haversine = sin(latDelta / 2) * sin(latDelta / 2) +
            cos(fromLat) * cos(toLat) * sin(lonDelta / 2) * sin(lonDelta / 2)
        val normalizedHaversine = haversine.coerceIn(0.0, 1.0)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(normalizedHaversine), sqrt(1 - normalizedHaversine))
    }

    fun bearingDegrees(from: Coordinate, to: Coordinate): Double {
        val fromLat = Math.toRadians(from.latitude)
        val toLat = Math.toRadians(to.latitude)
        val lonDelta = Math.toRadians(to.longitude - from.longitude)
        val y = sin(lonDelta) * cos(toLat)
        val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(lonDelta)
        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    fun angleDifferenceDegrees(first: Double, second: Double): Double {
        val diff = abs(normalizeDegrees(first) - normalizeDegrees(second))
        return min(diff, 360.0 - diff)
    }

    fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
