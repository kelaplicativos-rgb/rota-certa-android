package br.com.mapeiaia.rotacerta.trips

internal data class BlaBlaScriptGroup0449(
    val title: String,
    val description: String,
    val requests: List<BlaBlaBrowserRequest>,
)

/**
 * Complete UI inventory of the browser orchestrator.
 *
 * Every registered BlaBlaBrowserRequest is exposed exactly once. The date/period
 * synchronization uses the selection as an execution/output permission set.
 * Some selected downstream outputs need technical navigation/read prerequisites;
 * those prerequisites never turn an unselected output into a committed field.
 */
internal object BlaBlaDateScopeScriptCatalog0449 {
    val groups: List<BlaBlaScriptGroup0449> = listOf(
        BlaBlaScriptGroup0449(
            title = "Conta e perfil",
            description = "Identidade autenticada, perfil do motorista e avaliações.",
            requests = listOf(
                BlaBlaBrowserRequest.SESSION_IDENTITY,
                BlaBlaBrowserRequest.DRIVER_PROFILE,
                BlaBlaBrowserRequest.DRIVER_REVIEWS,
            ),
        ),
        BlaBlaScriptGroup0449(
            title = "Viagem",
            description = "Lista, abertura, detalhes, URL pública, itinerário e edição.",
            requests = listOf(
                BlaBlaBrowserRequest.RIDE_LIST,
                BlaBlaBrowserRequest.TRIP_OPEN,
                BlaBlaBrowserRequest.TRIP_DETAIL,
                BlaBlaBrowserRequest.TRIP_PUBLIC_SHARE,
                BlaBlaBrowserRequest.TRIP_ITINERARY,
                BlaBlaBrowserRequest.TRIP_EDIT,
            ),
        ),
        BlaBlaScriptGroup0449(
            title = "Passageiros",
            description = "Lista, abertura, identidade, contato, tarifa, trecho e endereços.",
            requests = listOf(
                BlaBlaBrowserRequest.PASSENGER_ROSTER,
                BlaBlaBrowserRequest.PASSENGER_OPEN,
                BlaBlaBrowserRequest.PASSENGER_IDENTITY,
                BlaBlaBrowserRequest.PASSENGER_CONTACT,
                BlaBlaBrowserRequest.PASSENGER_FARE,
                BlaBlaBrowserRequest.PASSENGER_SEGMENT,
                BlaBlaBrowserRequest.PASSENGER_ADDRESSES,
            ),
        ),
        BlaBlaScriptGroup0449(
            title = "Vagas",
            description = "Leitura de vagas e operações remotas de alteração/salvamento.",
            requests = listOf(
                BlaBlaBrowserRequest.SEAT_OPTIONS,
                BlaBlaBrowserRequest.SEAT_CHANGE,
                BlaBlaBrowserRequest.SEAT_SAVE,
            ),
        ),
        BlaBlaScriptGroup0449(
            title = "Pesquisa pública BlaBlaCar",
            description = "Busca pública, abertura de resultado e perfil público.",
            requests = listOf(
                BlaBlaBrowserRequest.PUBLIC_SEARCH_FORM,
                BlaBlaBrowserRequest.PUBLIC_SEARCH_SCROLL,
                BlaBlaBrowserRequest.PUBLIC_SEARCH_RESULTS,
                BlaBlaBrowserRequest.PUBLIC_RESULT_OPEN,
                BlaBlaBrowserRequest.PUBLIC_DRIVER_PROFILE_OPEN,
                BlaBlaBrowserRequest.PUBLIC_DRIVER_PROFILE,
                BlaBlaBrowserRequest.PUBLIC_DRIVER_REVIEWS,
            ),
        ),
        BlaBlaScriptGroup0449(
            title = "Mensagens",
            description = "Abertura do passageiro na conversa e leitura da thread.",
            requests = listOf(
                BlaBlaBrowserRequest.MESSAGE_PASSENGER_OPEN,
                BlaBlaBrowserRequest.MESSAGE_THREAD,
            ),
        ),
        BlaBlaScriptGroup0449(
            title = "Viagens arquivadas",
            description = "Lista e abertura de viagens arquivadas/passadas.",
            requests = listOf(
                BlaBlaBrowserRequest.ARCHIVED_RIDE_LIST,
                BlaBlaBrowserRequest.ARCHIVED_RIDE_OPEN,
            ),
        ),
        BlaBlaScriptGroup0449(
            title = "Diagnóstico",
            description = "Estado da página e snapshot do DOM para evidência técnica.",
            requests = listOf(
                BlaBlaBrowserRequest.PAGE_STATE,
                BlaBlaBrowserRequest.DOM_SNAPSHOT,
            ),
        ),
    )

    val selectableRequests: List<BlaBlaBrowserRequest> = groups.flatMap(BlaBlaScriptGroup0449::requests)
    val all: Set<BlaBlaBrowserRequest> = selectableRequests.toSet()

    val remoteWriteRequests: Set<BlaBlaBrowserRequest> =
        all.filterTo(linkedSetOf()) { it.operation == BlaBlaBrowserOperation.REMOTE_WRITE }

    /** Public permalink acquisition path. */
    val publicUrlRequests: Set<BlaBlaBrowserRequest> = setOf(
        BlaBlaBrowserRequest.TRIP_PUBLIC_SHARE,
        BlaBlaBrowserRequest.PUBLIC_SEARCH_FORM,
        BlaBlaBrowserRequest.PUBLIC_SEARCH_SCROLL,
        BlaBlaBrowserRequest.PUBLIC_SEARCH_RESULTS,
        BlaBlaBrowserRequest.PUBLIC_RESULT_OPEN,
    )

    /** Passenger-related outputs. */
    val passengerRequests: Set<BlaBlaBrowserRequest> = setOf(
        BlaBlaBrowserRequest.PASSENGER_ROSTER,
        BlaBlaBrowserRequest.PASSENGER_OPEN,
        BlaBlaBrowserRequest.PASSENGER_IDENTITY,
        BlaBlaBrowserRequest.PASSENGER_CONTACT,
        BlaBlaBrowserRequest.PASSENGER_FARE,
        BlaBlaBrowserRequest.PASSENGER_SEGMENT,
        BlaBlaBrowserRequest.PASSENGER_ADDRESSES,
    )

    /** Read-only seat output used by "Só vagas". Writes remain separately authorized. */
    val seatRequests: Set<BlaBlaBrowserRequest> = setOf(
        BlaBlaBrowserRequest.SEAT_OPTIONS,
    )

    val coreTripRequests: Set<BlaBlaBrowserRequest> = setOf(
        BlaBlaBrowserRequest.RIDE_LIST,
        BlaBlaBrowserRequest.TRIP_OPEN,
        BlaBlaBrowserRequest.TRIP_DETAIL,
        BlaBlaBrowserRequest.TRIP_ITINERARY,
    )

    init {
        check(selectableRequests.size == 32) { "Expected 32 orchestrator scripts, got ${selectableRequests.size}" }
        check(selectableRequests.distinct().size == selectableRequests.size) { "Duplicate orchestrator script in UI catalog" }
        check(all == BlaBlaBrowserRequest.values().toSet()) { "UI catalog must expose every registered browser request" }
    }

    fun label(request: BlaBlaBrowserRequest): String = when (request) {
        BlaBlaBrowserRequest.SESSION_IDENTITY -> "Identidade da sessão"
        BlaBlaBrowserRequest.DRIVER_PROFILE -> "Perfil do motorista"
        BlaBlaBrowserRequest.DRIVER_REVIEWS -> "Avaliações do motorista"
        BlaBlaBrowserRequest.RIDE_LIST -> "Lista de viagens"
        BlaBlaBrowserRequest.TRIP_OPEN -> "Abrir viagem"
        BlaBlaBrowserRequest.TRIP_DETAIL -> "Detalhes da viagem"
        BlaBlaBrowserRequest.TRIP_PUBLIC_SHARE -> "URL pública · compartilhar"
        BlaBlaBrowserRequest.TRIP_ITINERARY -> "Itinerário e paradas"
        BlaBlaBrowserRequest.TRIP_EDIT -> "Edição da viagem"
        BlaBlaBrowserRequest.PASSENGER_ROSTER -> "Lista de passageiros"
        BlaBlaBrowserRequest.PASSENGER_OPEN -> "Abrir passageiro"
        BlaBlaBrowserRequest.PASSENGER_IDENTITY -> "Identidade do passageiro"
        BlaBlaBrowserRequest.PASSENGER_CONTACT -> "Contato do passageiro"
        BlaBlaBrowserRequest.PASSENGER_FARE -> "Tarifa do passageiro"
        BlaBlaBrowserRequest.PASSENGER_SEGMENT -> "Trecho do passageiro"
        BlaBlaBrowserRequest.PASSENGER_ADDRESSES -> "Endereços do passageiro"
        BlaBlaBrowserRequest.SEAT_OPTIONS -> "Vagas publicadas"
        BlaBlaBrowserRequest.SEAT_CHANGE -> "Alterar vagas"
        BlaBlaBrowserRequest.SEAT_SAVE -> "Salvar alteração de vagas"
        BlaBlaBrowserRequest.PUBLIC_SEARCH_FORM -> "Formulário da busca pública"
        BlaBlaBrowserRequest.PUBLIC_SEARCH_SCROLL -> "Scroll da busca pública"
        BlaBlaBrowserRequest.PUBLIC_SEARCH_RESULTS -> "Resultados da busca pública"
        BlaBlaBrowserRequest.PUBLIC_RESULT_OPEN -> "Abrir resultado público"
        BlaBlaBrowserRequest.PUBLIC_DRIVER_PROFILE_OPEN -> "Abrir perfil público"
        BlaBlaBrowserRequest.PUBLIC_DRIVER_PROFILE -> "Perfil público do motorista"
        BlaBlaBrowserRequest.PUBLIC_DRIVER_REVIEWS -> "Avaliações públicas"
        BlaBlaBrowserRequest.MESSAGE_PASSENGER_OPEN -> "Abrir conversa do passageiro"
        BlaBlaBrowserRequest.MESSAGE_THREAD -> "Conversa / mensagens"
        BlaBlaBrowserRequest.ARCHIVED_RIDE_LIST -> "Lista de viagens arquivadas"
        BlaBlaBrowserRequest.ARCHIVED_RIDE_OPEN -> "Abrir viagem arquivada"
        BlaBlaBrowserRequest.PAGE_STATE -> "Estado da página"
        BlaBlaBrowserRequest.DOM_SNAPSHOT -> "Snapshot do DOM"
    }

    fun operationLabel(request: BlaBlaBrowserRequest): String = when (request.operation) {
        BlaBlaBrowserOperation.CAPTURE -> "CAPTURE"
        BlaBlaBrowserOperation.NAVIGATION -> "NAVIGATION"
        BlaBlaBrowserOperation.REMOTE_WRITE -> "REMOTE_WRITE"
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

        /**
         * 0.1.476: the unattended collector that feeds the read-only public Agenda
         * must materialize every strong-identity trip before optional administrative
         * enrichment. Passenger, seat and public-link probes remain available to
         * explicit/manual syncs and existing values are preserved by selective merge.
         */
        fun automaticAgendaListing0476(): BlaBlaDateScopeScriptSelection0449 =
            explicit(BlaBlaDateScopeScriptCatalog0449.coreTripRequests)

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
