package br.com.mapeiaia.rotacerta

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 0.1.686 — contrato local de segurança para radares/alertas.
 *
 * A localização pode ser consumida localmente em alta frequência sem aumentar
 * a frequência de persistência/upload. A janela efetiva cresce com velocidade e
 * precisão, e um salto GPS pode ser auditado pelo segmento entre dois fixes.
 */
internal object RadarSafetyPolicy0686 {
    const val CONTRACT_MARKER = "RADAR_FAST_LOCAL_SAFETY_0686"

    const val LOCAL_LOCATION_INTERVAL_MS = 1_000L
    const val LOCAL_MIN_UPDATE_INTERVAL_MS = 500L
    const val SHARED_POINT_INTERVAL_MS = 5_000L
    const val PREARM_RADIUS_METERS = 4_000.0
    const val PASSED_AUTO_CLOSE_MILLIS = 5_000L

    private const val LOOKAHEAD_SECONDS = 10.0
    private const val MIN_ACCURACY_MARGIN_METERS = 30.0
    private const val MAX_EFFECTIVE_THRESHOLD_METERS = 2_000.0
    private const val MAX_SEGMENT_SPAN_METERS = 1_500.0

    fun effectiveThresholdMeters(
        configuredThresholdMeters: Int,
        speedMetersPerSecond: Double,
        accuracyMeters: Double,
    ): Int {
        val base = configuredThresholdMeters.coerceIn(200, 1000).toDouble()
        val speed = speedMetersPerSecond.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val accuracy = accuracyMeters.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val predictedTravel = speed * LOOKAHEAD_SECONDS
        val accuracyMargin = max(MIN_ACCURACY_MARGIN_METERS, accuracy * 2.0)
        return max(base, predictedTravel + accuracyMargin)
            .coerceAtMost(MAX_EFFECTIVE_THRESHOLD_METERS)
            .roundToInt()
    }

    fun segmentCrossesTarget(
        previous: Coordinate,
        current: Coordinate,
        target: Coordinate,
        thresholdMeters: Double,
    ): Boolean {
        if (thresholdMeters <= 0.0 || !thresholdMeters.isFinite()) return false
        val segmentSpan = GeoDistance.meters(previous, current)
        if (!segmentSpan.isFinite() || segmentSpan <= 0.0 || segmentSpan > MAX_SEGMENT_SPAN_METERS) return false
        return GeoDistance.distanceToSegmentMeters(previous, current, target) <= thresholdMeters
    }

    fun shouldPersistSharedPoint(
        previousPersistedAtMillis: Long,
        currentAtMillis: Long,
    ): Boolean {
        if (currentAtMillis <= 0L) return previousPersistedAtMillis <= 0L
        if (previousPersistedAtMillis <= 0L) return true
        return currentAtMillis - previousPersistedAtMillis >= SHARED_POINT_INTERVAL_MS
    }
}
