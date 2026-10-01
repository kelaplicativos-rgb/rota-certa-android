package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.Coordinate
import br.com.mapeiaia.rotacerta.GeoDistance
import java.text.Normalizer

internal enum class TimelineDirectionState {
    OUTBOUND,
    INBOUND,
    NEUTRAL,
    UNKNOWN,
}

/**
 * Presentation-only bridge for trusted direction evidence. It deliberately does
 * not feed TripPhysicalRideConsolidator, so conflict, continuity and occupancy
 * keep using their existing physical evidence pipeline.
 */
internal fun timelineTrustedDirectionStops(
    trips: List<Trip>,
    settings: AppSettings,
): List<TripStop> = buildList {
    addAll(trips.flatMap(Trip::stops))

    fun addPersisted(id: String, order: Int, label: String, coordinate: Coordinate?) {
        val point = coordinate?.takeIf(::validTimelineDirectionCoordinate) ?: return
        if (label.isBlank()) return
        add(
            TripStop(
                id = id,
                order = order,
                name = label.trim(),
                address = label.trim(),
                latitude = point.latitude,
                longitude = point.longitude,
            ),
        )
    }

    addPersisted(
        id = "timeline-settings-home",
        order = -1_000,
        label = settings.homeAddress,
        coordinate = settings.homeCoordinate,
    )
    addPersisted(
        id = "timeline-settings-alternative",
        order = -999,
        label = settings.alternativeAddress,
        coordinate = settings.alternativeCoordinate,
    )
    settings.workRegionPins
        .filter { it.enabled }
        .forEachIndexed { index, pin ->
            addPersisted(
                id = "timeline-settings-pin:${pin.id}",
                order = -900 + index,
                label = pin.address,
                coordinate = pin.coordinate,
            )
        }
}

internal fun timelineDirectionState(
    entry: TripTimelineEntry,
    trip: Trip?,
    trustedGeo: Map<String, TimelineGeoPoint>,
    reference: Coordinate?,
    radiusKm: Double,
): TimelineDirectionState {
    val base = reference?.takeIf(::validTimelineDirectionCoordinate) ?: return TimelineDirectionState.UNKNOWN
    if (!radiusKm.isFinite() || radiusKm < 0.0) return TimelineDirectionState.UNKNOWN

    val orderedStops = trip?.stops?.sortedBy(TripStop::order).orEmpty()
    val localOrigin = orderedStops.firstOrNull()
        ?.takeIf { stop -> timelineDirectionSamePlace(stop.name, entry.origin) || timelineDirectionSamePlace(stop.address, entry.origin) }
        ?.timelineDirectionCoordinate()
    val localDestination = orderedStops.lastOrNull()
        ?.takeIf { stop -> timelineDirectionSamePlace(stop.name, entry.destination) || timelineDirectionSamePlace(stop.address, entry.destination) }
        ?.timelineDirectionCoordinate()

    val origin = localOrigin ?: trustedGeo[entry.origin]?.timelineDirectionCoordinate()
        ?: return TimelineDirectionState.UNKNOWN
    val destination = localDestination ?: trustedGeo[entry.destination]?.timelineDirectionCoordinate()
        ?: return TimelineDirectionState.UNKNOWN
    val radiusMeters = radiusKm * 1_000.0
    val originInside = GeoDistance.meters(base, origin) <= radiusMeters
    val destinationInside = GeoDistance.meters(base, destination) <= radiusMeters

    return when {
        originInside && !destinationInside -> TimelineDirectionState.OUTBOUND
        !originInside && destinationInside -> TimelineDirectionState.INBOUND
        else -> TimelineDirectionState.NEUTRAL
    }
}

internal fun timelineDirectionDisplayLabel(state: TimelineDirectionState): String? = when (state) {
    TimelineDirectionState.OUTBOUND -> "↑ IDA"
    TimelineDirectionState.INBOUND -> "↓ VOLTA"
    TimelineDirectionState.NEUTRAL -> "↔ NEUTRA"
    TimelineDirectionState.UNKNOWN -> null
}

/** Compatibility label for screens that still use the earlier base terminology. */
internal fun timelineBaseDirectionLabel(
    entry: TripTimelineEntry,
    trip: Trip?,
    trustedGeo: Map<String, TimelineGeoPoint>,
    home: Coordinate?,
    radiusKm: Double,
): String? = when (timelineDirectionState(entry, trip, trustedGeo, home, radiusKm)) {
    TimelineDirectionState.OUTBOUND -> "↑ Ida • saindo da base"
    TimelineDirectionState.INBOUND -> "↓ Volta • retornando à base"
    TimelineDirectionState.NEUTRAL -> "↔ Neutra • não envolve a base"
    TimelineDirectionState.UNKNOWN -> null
}

private fun TripStop.timelineDirectionCoordinate(): Coordinate? {
    val coordinate = Coordinate(latitude ?: return null, longitude ?: return null)
    return coordinate.takeIf(::validTimelineDirectionCoordinate)
}

private fun TimelineGeoPoint.timelineDirectionCoordinate(): Coordinate? =
    Coordinate(latitude, longitude).takeIf(::validTimelineDirectionCoordinate)

private fun validTimelineDirectionCoordinate(coordinate: Coordinate): Boolean =
    coordinate.latitude.isFinite() &&
        coordinate.longitude.isFinite() &&
        coordinate.latitude in -90.0..90.0 &&
        coordinate.longitude in -180.0..180.0

private fun timelineDirectionSamePlace(left: String, right: String): Boolean {
    val a = timelineDirectionPlaceKey(left)
    val b = timelineDirectionPlaceKey(right)
    if (a.isBlank() || b.isBlank()) return false
    if (a == b) return true
    val shorter = if (a.length <= b.length) a else b
    val longer = if (a.length <= b.length) b else a
    return shorter.length >= 5 && longer.contains(shorter)
}

private fun timelineDirectionPlaceKey(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()
