package br.com.mapeiaia.rotacerta

import kotlinx.serialization.Serializable

@Serializable
data class WorkTrackPoint(
    val coordinate: Coordinate,
    val recordedAtMillis: Long,
    val accuracyMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
)

data class WorkTrackingSummary(
    val points: List<WorkTrackPoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val startedAtMillis: Long? = null,
    val endedAtMillis: Long? = null,
) {
    val durationMillis: Long
        get() = if (startedAtMillis != null && endedAtMillis != null) {
            (endedAtMillis - startedAtMillis).coerceAtLeast(0L)
        } else {
            0L
        }

    val lastPoint: WorkTrackPoint?
        get() = points.lastOrNull()
}

fun buildWorkTrackingSummary(
    points: List<WorkTrackPoint>,
    startInclusiveMillis: Long,
    endExclusiveMillis: Long,
): WorkTrackingSummary {
    val filtered = points
        .asSequence()
        .filter { it.recordedAtMillis in startInclusiveMillis until endExclusiveMillis }
        .sortedBy { it.recordedAtMillis }
        .toList()

    val distance = filtered.zipWithNext().sumOf { (previous, current) ->
        val gapMillis = current.recordedAtMillis - previous.recordedAtMillis
        if (gapMillis <= 0L || gapMillis > MAX_TRACK_GAP_MS) {
            0.0
        } else {
            GeoDistance.meters(previous.coordinate, current.coordinate)
        }
    }

    return WorkTrackingSummary(
        points = filtered,
        distanceMeters = distance,
        startedAtMillis = filtered.firstOrNull()?.recordedAtMillis,
        endedAtMillis = filtered.lastOrNull()?.recordedAtMillis,
    )
}

private const val MAX_TRACK_GAP_MS = 10 * 60 * 1000L
