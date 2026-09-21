package br.com.mapeiaia.rotacerta.trips

/**
 * Pure Agenda domain boundary.
 *
 * The UI, Firebase transport and public projection must consume this materialized
 * state instead of reinterpreting lifecycle, seat inventory or public navigation.
 */
internal enum class AgendaLifecycleState0582 {
    INVALID,
    FUTURE,
    ACTIVE,
    EXPIRED,
}

internal data class AgendaVisibilityState0582(
    val visible: Boolean,
    val visibleUntilMillis: Long,
    val lifecycle: AgendaLifecycleState0582,
    val reasonCode: String,
)

internal data class AgendaPublicLinkState0582(
    val url: String?,
    val available: Boolean,
    val reasonCode: String,
)

internal data class AgendaSegmentAvailabilityState0582(
    val fromStopId: String,
    val toStopId: String,
    val occupiedSeats: Int,
    val passengerSeats: Int,
    val blockedSeats: Int,
    val availableSeats: Int,
    val overbookingSeats: Int,
)

internal data class AgendaFacetFingerprints0582(
    val visibility: String,
    val seats: String,
    val publicLink: String,
    val route: String,
    val schedule: String,
)

internal data class AgendaTripViewState0582(
    val tripId: String,
    val canonicalRevision: Long,
    val publicationRevision: Long,
    val visibility: AgendaVisibilityState0582,
    val publicLink: AgendaPublicLinkState0582,
    val segments: List<AgendaSegmentAvailabilityState0582>,
    val fingerprints: AgendaFacetFingerprints0582,
)

internal object AgendaVisibilityPolicy0582 {
    fun evaluate(
        trip: Trip,
        nowMillis: Long,
    ): AgendaVisibilityState0582 {
        val decision = canonicalAgendaLifecycleDecision0581(
            departureAtMillis = trip.departureAtMillis,
            arrivalAtMillis = canonicalAgendaArrivalAtMillis0581(trip),
            nowMillis = nowMillis,
        )
        val lifecycle = when (decision.reasonCode) {
            "AGENDA_VISIBILITY_FUTURE_TRIP" -> AgendaLifecycleState0582.FUTURE
            "AGENDA_VISIBILITY_ACTIVE_TRIP" -> AgendaLifecycleState0582.ACTIVE
            "AGENDA_VISIBILITY_EXPIRED" -> AgendaLifecycleState0582.EXPIRED
            else -> AgendaLifecycleState0582.INVALID
        }
        return AgendaVisibilityState0582(
            visible = decision.visible,
            visibleUntilMillis = decision.visibleUntilMillis,
            lifecycle = lifecycle,
            reasonCode = decision.reasonCode,
        )
    }

    fun isVisible(
        trip: Trip,
        nowMillis: Long,
    ): Boolean = evaluate(trip, nowMillis).visible
}

internal object AgendaPublicLinkPolicy0582 {
    fun resolve(trip: Trip): AgendaPublicLinkState0582 {
        val administrativeTripId = trip.blablaTripId.orEmpty().trim()
        if (administrativeTripId.isBlank()) {
            return AgendaPublicLinkState0582(
                url = null,
                available = false,
                reasonCode = "AGENDA_PUBLIC_LINK_STRONG_IDENTITY_MISSING",
            )
        }
        val resolved = canonicalBoundBlaBlaPublicUrl0423(
            trip.blablaPublicUrl,
            administrativeTripId,
        )?.trim().orEmpty()
        return if (resolved.isNotBlank()) {
            AgendaPublicLinkState0582(
                url = resolved,
                available = true,
                reasonCode = "AGENDA_PUBLIC_LINK_VALID",
            )
        } else {
            AgendaPublicLinkState0582(
                url = null,
                available = false,
                reasonCode = "AGENDA_PUBLIC_LINK_MISSING",
            )
        }
    }
}

internal object AgendaSeatPolicy0582 {
    fun segments(
        trip: Trip,
        bookings: List<Booking>,
        nowMillis: Long,
    ): List<AgendaSegmentAvailabilityState0582> {
        if (!segmentAvailabilityTruth0603(trip).verified) return emptyList()
        return SeatAvailabilityEngine.segmentLoads(trip, bookings, nowMillis).map { load ->
            AgendaSegmentAvailabilityState0582(
                fromStopId = load.from.id,
                toStopId = load.to.id,
                occupiedSeats = load.occupiedSeats,
                passengerSeats = load.passengerSeats,
                blockedSeats = load.blockedSeats,
                availableSeats = load.availableSeats,
                overbookingSeats = load.overbookingSeats,
            )
        }
    }
}

internal object AgendaDomainPipeline0582 {
    fun project(
        trip: Trip,
        bookings: List<Booking>,
        nowMillis: Long,
    ): AgendaTripViewState0582 {
        val visibility = AgendaVisibilityPolicy0582.evaluate(trip, nowMillis)
        val publicLink = AgendaPublicLinkPolicy0582.resolve(trip)
        val segments = AgendaSeatPolicy0582.segments(trip, bookings, nowMillis)
        val orderedStops = trip.stops.sortedBy(TripStop::order)
        val routeFingerprint = orderedStops.joinToString(">") {
            listOf(it.id, it.order.toString(), it.name.trim(), it.address.trim()).joinToString("~")
        }
        val scheduleFingerprint = buildString {
            append(trip.departureAtMillis)
            append('|')
            orderedStops.forEach { stop ->
                append(stop.id).append('~')
                append(stop.plannedArrivalMillis ?: -1L).append('~')
                append(stop.plannedDepartureMillis ?: -1L).append(',')
            }
        }
        val seatFingerprint = segments.joinToString("|") {
            listOf(
                it.fromStopId,
                it.toStopId,
                it.occupiedSeats,
                it.passengerSeats,
                it.blockedSeats,
                it.availableSeats,
                it.overbookingSeats,
            ).joinToString("~")
        }
        val visibilityFingerprint = listOf(
            visibility.visible,
            visibility.visibleUntilMillis,
            visibility.lifecycle.name,
            visibility.reasonCode,
        ).joinToString("|")
        val linkFingerprint = listOf(
            publicLink.available,
            publicLink.url.orEmpty(),
            publicLink.reasonCode,
        ).joinToString("|")
        return AgendaTripViewState0582(
            tripId = trip.id,
            canonicalRevision = trip.canonicalRevision,
            publicationRevision = trip.publicationRevision,
            visibility = visibility,
            publicLink = publicLink,
            segments = segments,
            fingerprints = AgendaFacetFingerprints0582(
                visibility = visibilityFingerprint,
                seats = seatFingerprint,
                publicLink = linkFingerprint,
                route = routeFingerprint,
                schedule = scheduleFingerprint,
            ),
        )
    }
}

internal enum class AgendaChangeScope0582 {
    VISIBILITY,
    SEATS,
    PUBLIC_LINK,
    ROUTE,
    SCHEDULE,
}

internal object AgendaRegressionGuard0582 {
    fun unexpectedChanges(
        scope: AgendaChangeScope0582,
        before: AgendaFacetFingerprints0582,
        after: AgendaFacetFingerprints0582,
    ): Set<String> {
        val changed = buildSet {
            if (before.visibility != after.visibility) add("visibility")
            if (before.seats != after.seats) add("seats")
            if (before.publicLink != after.publicLink) add("publicLink")
            if (before.route != after.route) add("route")
            if (before.schedule != after.schedule) add("schedule")
        }
        val allowed = when (scope) {
            AgendaChangeScope0582.VISIBILITY -> setOf("visibility")
            AgendaChangeScope0582.SEATS -> setOf("seats")
            AgendaChangeScope0582.PUBLIC_LINK -> setOf("publicLink")
            AgendaChangeScope0582.ROUTE -> setOf("route")
            AgendaChangeScope0582.SCHEDULE -> setOf("schedule", "visibility")
        }
        return changed - allowed
    }
}
