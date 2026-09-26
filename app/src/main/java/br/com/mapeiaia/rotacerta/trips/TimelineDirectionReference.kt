package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.Coordinate

internal data class TimelineDirectionReference(
    val coordinate: Coordinate?,
    val radiusKm: Double,
)

internal fun timelineDirectionReference(
    explicit: TripReferenceOrigin?,
    settings: AppSettings,
): TimelineDirectionReference {
    val explicitCoordinate = explicit?.takeIf(TripReferenceOrigin::isValid)?.coordinate
    val homeCoordinate = settings.homeCoordinate?.takeIf(::validTimelineReferenceCoordinate)
    val coordinate = explicitCoordinate ?: homeCoordinate
    val radius = explicit?.radiusKm?.takeIf { it.isFinite() && it in TripReferenceOrigin.MIN_RADIUS_KM..TripReferenceOrigin.MAX_RADIUS_KM }
        ?: settings.homeRadiusKm.takeIf { it.isFinite() && it in TripReferenceOrigin.MIN_RADIUS_KM..TripReferenceOrigin.MAX_RADIUS_KM }
        ?: TripReferenceOrigin.DEFAULT_RADIUS_KM
    return TimelineDirectionReference(coordinate = coordinate, radiusKm = radius)
}

private fun validTimelineReferenceCoordinate(value: Coordinate): Boolean =
    value.latitude.isFinite() && value.longitude.isFinite() &&
        value.latitude in -90.0..90.0 && value.longitude in -180.0..180.0
