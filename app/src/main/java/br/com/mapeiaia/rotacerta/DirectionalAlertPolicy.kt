package br.com.mapeiaia.rotacerta

import kotlin.math.max

/** Regras puras usadas para impedir alertas atrás do veículo ou no sentido oposto. */
object DirectionalAlertPolicy {
    fun maxAcceptedAccuracyMeters(thresholdMeters: Int): Double = when {
        thresholdMeters <= 250 -> 20.0
        thresholdMeters <= 600 -> 27.0
        else -> 35.0
    }

    fun isFixUsable(
        fix: PreciseNavigationFix,
        thresholdMeters: Int,
        nowMillis: Long,
    ): Boolean =
        nowMillis - fix.timestampMillis in 0L..MAX_FIX_AGE_MILLIS &&
            fix.accuracyMeters in 0.1..maxAcceptedAccuracyMeters(thresholdMeters) &&
            fix.speedMetersPerSecond >= MIN_MOVING_SPEED_MPS &&
            fix.headingDegrees != null

    fun isTargetAhead(
        headingDegrees: Double?,
        bearingToTargetDegrees: Double,
        toleranceDegrees: Double = TARGET_AHEAD_TOLERANCE_DEGREES,
    ): Boolean {
        val heading = headingDegrees ?: return false
        return GeoDistance.angleDifferenceDegrees(heading, bearingToTargetDegrees) <= toleranceDegrees
    }

    fun radarDirectionMatches(radar: ImportedRadar, headingDegrees: Double?): Boolean {
        val directionType = radar.directionType ?: 0
        val radarDirection = radar.direction?.toDouble()?.let(GeoDistance::normalizeDegrees)
        if (directionType == 0 || radarDirection == null) return true
        val heading = headingDegrees ?: return false
        val primaryDiff = GeoDistance.angleDifferenceDegrees(heading, radarDirection)
        if (primaryDiff <= RADAR_DIRECTION_TOLERANCE_DEGREES) return true
        if (directionType >= DOUBLE_DIRECTION_TYPE) {
            val inverse = GeoDistance.normalizeDegrees(radarDirection + 180.0)
            return GeoDistance.angleDifferenceDegrees(heading, inverse) <= RADAR_DIRECTION_TOLERANCE_DEGREES
        }
        return false
    }

    fun hasPassed(
        headingDegrees: Double?,
        bearingToTargetDegrees: Double,
        minimumDistanceMeters: Double,
        currentDistanceMeters: Double,
        increasingSamples: Int,
    ): Boolean {
        val heading = headingDegrees ?: return false
        val targetBehind = GeoDistance.angleDifferenceDegrees(heading, bearingToTargetDegrees) >= TARGET_BEHIND_DEGREES
        val movedAway = currentDistanceMeters >= minimumDistanceMeters + PASS_DISTANCE_INCREASE_METERS
        return targetBehind && movedAway && increasingSamples >= REQUIRED_INCREASING_SAMPLES
    }

    fun isApproaching(previousDistanceMeters: Double?, currentDistanceMeters: Double, accuracyMeters: Double): Boolean {
        val previous = previousDistanceMeters ?: return false
        val toleratedJitter = max(MIN_DISTANCE_JITTER_METERS, accuracyMeters * 0.35)
        return currentDistanceMeters <= previous + toleratedJitter
    }

    const val MAX_FIX_AGE_MILLIS = 3_500L
    const val MIN_MOVING_SPEED_MPS = 1.2
    const val TARGET_AHEAD_TOLERANCE_DEGREES = 55.0
    const val RADAR_DIRECTION_TOLERANCE_DEGREES = 42.0
    const val TARGET_BEHIND_DEGREES = 105.0
    const val PASS_DISTANCE_INCREASE_METERS = 24.0
    const val REQUIRED_INCREASING_SAMPLES = 2
    const val DOUBLE_DIRECTION_TYPE = 2
    const val MIN_DISTANCE_JITTER_METERS = 3.0
}
