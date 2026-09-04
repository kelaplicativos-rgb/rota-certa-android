package br.com.mapeiaia.rotacerta.trips

/**
 * User-selectable scripts that participate in the existing date/period authenticated collector.
 *
 * The selection controls which synchronization outputs are requested. Technical prerequisites
 * may still be executed to reach a requested downstream script, but their unselected data is not
 * committed. This keeps "Somente vagas" possible without creating a second collector.
 */
internal object BlaBlaDateScopeScriptCatalog0449 {
    val selectableRequests: List<BlaBlaBrowserRequest> = listOf(
        BlaBlaBrowserRequest.SESSION_IDENTITY,
        BlaBlaBrowserRequest.RIDE_LIST,
        BlaBlaBrowserRequest.TRIP_OPEN,
        BlaBlaBrowserRequest.TRIP_DETAIL,
        BlaBlaBrowserRequest.TRIP_PUBLIC_SHARE,
        BlaBlaBrowserRequest.PUBLIC_SEARCH_RESULTS,
        BlaBlaBrowserRequest.PASSENGER_OPEN,
        BlaBlaBrowserRequest.PASSENGER_CONTACT,
        BlaBlaBrowserRequest.TRIP_EDIT,
        BlaBlaBrowserRequest.SEAT_OPTIONS,
        BlaBlaBrowserRequest.PAGE_STATE,
    )

    val all: Set<BlaBlaBrowserRequest> = selectableRequests.toSet()

    val publicUrlRequests: Set<BlaBlaBrowserRequest> = setOf(
        BlaBlaBrowserRequest.TRIP_PUBLIC_SHARE,
        BlaBlaBrowserRequest.PUBLIC_SEARCH_RESULTS,
    )

    val passengerRequests: Set<BlaBlaBrowserRequest> = setOf(
        BlaBlaBrowserRequest.PASSENGER_OPEN,
        BlaBlaBrowserRequest.PASSENGER_CONTACT,
    )

    val seatRequests: Set<BlaBlaBrowserRequest> = setOf(
        BlaBlaBrowserRequest.SEAT_OPTIONS,
    )

    val coreTripRequests: Set<BlaBlaBrowserRequest> = setOf(
        BlaBlaBrowserRequest.RIDE_LIST,
        BlaBlaBrowserRequest.TRIP_DETAIL,
    )

    fun label(request: BlaBlaBrowserRequest): String = when (request) {
        BlaBlaBrowserRequest.SESSION_IDENTITY -> "Identidade da sessão"
        BlaBlaBrowserRequest.RIDE_LIST -> "Lista de viagens"
        BlaBlaBrowserRequest.TRIP_OPEN -> "Abrir card da viagem"
        BlaBlaBrowserRequest.TRIP_DETAIL -> "Detalhes da viagem"
        BlaBlaBrowserRequest.TRIP_PUBLIC_SHARE -> "URL pública"
        BlaBlaBrowserRequest.PUBLIC_SEARCH_RESULTS -> "URL pública · busca de fallback"
        BlaBlaBrowserRequest.PASSENGER_OPEN -> "Abrir passageiro"
        BlaBlaBrowserRequest.PASSENGER_CONTACT -> "Dados do passageiro"
        BlaBlaBrowserRequest.TRIP_EDIT -> "Abrir edição da viagem"
        BlaBlaBrowserRequest.SEAT_OPTIONS -> "Vagas publicadas"
        BlaBlaBrowserRequest.PAGE_STATE -> "Estado/segurança da página"
        else -> request.name
    }
}

internal data class BlaBlaDateScopeScriptSelection0449(
    val explicit: Boolean,
    val requested: Set<BlaBlaBrowserRequest>,
) {
    val selective: Boolean
        get() = explicit && requested != BlaBlaDateScopeScriptCatalog0449.all

    fun requested(request: BlaBlaBrowserRequest): Boolean =
        !explicit || request in requested

    fun wantsPublicUrl(): Boolean =
        !explicit || requested.any(BlaBlaDateScopeScriptCatalog0449.publicUrlRequests::contains)

    fun wantsPassengerData(): Boolean =
        !explicit || requested.any(BlaBlaDateScopeScriptCatalog0449.passengerRequests::contains)

    fun wantsSeatData(): Boolean =
        !explicit || requested.any(BlaBlaDateScopeScriptCatalog0449.seatRequests::contains)

    fun wantsCoreTripData(): Boolean =
        !explicit || requested.any(BlaBlaDateScopeScriptCatalog0449.coreTripRequests::contains)

    fun wantsTripTraversal(): Boolean =
        !explicit || requested.any { request ->
            request in BlaBlaDateScopeScriptCatalog0449.coreTripRequests ||
                request in BlaBlaDateScopeScriptCatalog0449.publicUrlRequests ||
                request in BlaBlaDateScopeScriptCatalog0449.passengerRequests ||
                request in BlaBlaDateScopeScriptCatalog0449.seatRequests
        }

    fun requestedNames(): List<String> =
        requested.map(BlaBlaBrowserRequest::name).sorted()

    companion object {
        fun legacyAll(): BlaBlaDateScopeScriptSelection0449 =
            BlaBlaDateScopeScriptSelection0449(
                explicit = false,
                requested = BlaBlaDateScopeScriptCatalog0449.all,
            )

        fun explicit(requested: Collection<BlaBlaBrowserRequest>): BlaBlaDateScopeScriptSelection0449 =
            BlaBlaDateScopeScriptSelection0449(
                explicit = true,
                requested = requested.filter(BlaBlaDateScopeScriptCatalog0449.all::contains).toSet(),
            )

        fun fromNames(names: Collection<String>?): BlaBlaDateScopeScriptSelection0449 {
            if (names == null) return legacyAll()
            val parsed = names.mapNotNull { raw ->
                runCatching { BlaBlaBrowserRequest.valueOf(raw.trim()) }.getOrNull()
            }
            return explicit(parsed)
        }
    }
}

/**
 * Applies only the outputs explicitly requested by the date/period run.
 *
 * If a selective run requests only a downstream field (for example seats or public URL),
 * an existing canonical collector trip is required so unselected fields can be preserved.
 */
internal fun mergeSelectiveCollectorTrip0449(
    previous: BlaBlaCollectorTrip?,
    fresh: BlaBlaCollectorTrip,
    selection: BlaBlaDateScopeScriptSelection0449,
): BlaBlaCollectorTrip? {
    if (!selection.selective) return fresh

    val wantsCore = selection.wantsCoreTripData()
    val base = previous ?: if (wantsCore) fresh.copy(
        public_trip_href = null,
        public_trip_href_source = "",
        public_trip_href_binding = "",
        passengers = emptyList(),
        itinerary_stops = emptyList(),
        itinerary_authoritative = false,
        booked_seats = 0,
        published_seats = null,
        passenger_roster_complete = false,
    ) else return null

    var merged = if (wantsCore) fresh else base

    merged = if (selection.wantsPublicUrl()) {
        merged.copy(
            public_trip_href = fresh.public_trip_href,
            public_trip_href_source = fresh.public_trip_href_source,
            public_trip_href_binding = fresh.public_trip_href_binding,
        )
    } else {
        merged.copy(
            public_trip_href = previous?.public_trip_href,
            public_trip_href_source = previous?.public_trip_href_source.orEmpty(),
            public_trip_href_binding = previous?.public_trip_href_binding.orEmpty(),
        )
    }

    merged = if (selection.wantsPassengerData()) {
        merged.copy(
            passengers = fresh.passengers,
            booked_seats = fresh.booked_seats,
            passenger_roster_complete = fresh.passenger_roster_complete,
        )
    } else {
        merged.copy(
            passengers = previous?.passengers.orEmpty(),
            booked_seats = previous?.booked_seats ?: 0,
            passenger_roster_complete = previous?.passenger_roster_complete ?: false,
        )
    }

    merged = if (selection.wantsSeatData()) {
        merged.copy(
            published_seats = fresh.published_seats,
        )
    } else {
        merged.copy(
            published_seats = previous?.published_seats,
        )
    }

    return merged
}
