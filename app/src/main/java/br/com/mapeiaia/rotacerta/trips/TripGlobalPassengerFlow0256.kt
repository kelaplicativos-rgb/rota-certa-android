package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val EXTERNAL_CAPACITY_PREFIX = "timeline-external-capacity:"

internal data class TimelinePassengerTripPreparation(
    val trip: Trip,
    val created: Boolean,
    val mirroredExternalSeats: Int,
)

internal fun timelineStrongExternalTripKey(entry: TripTimelineEntry): String? =
    canonicalExternalTripIdentityKey(
        entry.blablaProfileUuid,
        entry.blablaTripId,
        entry.blablaTripHref,
    )

internal fun timelineExternalBackingTripId(entry: TripTimelineEntry): String? =
    externalBackingTripIdFor(
        entry.blablaProfileUuid,
        entry.blablaTripId,
        entry.blablaTripHref,
    )

internal fun timelineManualPassengerOccupancyKnown(entry: TripTimelineEntry): Boolean =
    timelineStrongExternalTripKey(entry) == null || entry.blablaPassengerRosterComplete == true

internal fun timelinePhysicalTripMatches(entry: TripTimelineEntry, trips: List<Trip>): List<Trip> = trips.filter { trip ->
    kotlin.math.abs(trip.departureAtMillis - entry.departureAtMillis) <= 45L * 60L * 1000L &&
        trip.stops.sortedBy(TripStop::order).let { stops ->
            val first = stops.firstOrNull()?.name ?: return@let false
            val last = stops.lastOrNull()?.name ?: return@let false
            timelineSamePlace0256(first, entry.origin) && timelineSamePlace0256(last, entry.destination)
        }
}

internal fun timelineExternalRoutePointLabels(entry: TripTimelineEntry): List<String> {
    val origin = entry.origin.trim().takeIf(String::isNotEmpty) ?: return emptyList()
    val destination = entry.destination.trim().takeIf(String::isNotEmpty) ?: return emptyList()
    val originKey = timelinePlaceKey0256(origin)
    val destinationKey = timelinePlaceKey0256(destination)
    if (originKey.isBlank() || destinationKey.isBlank() || originKey == destinationKey) return listOf(origin, destination)

    val observedItinerary = entry.blablaItineraryStops
        .map(String::trim)
        .filter(String::isNotBlank)
    if (observedItinerary.size >= 2) {
        val observedOriginIndex = observedItinerary.indexOfFirst { timelineSamePlace0256(it, origin) }
        val observedDestinationIndex = observedItinerary.indexOfLast { timelineSamePlace0256(it, destination) }
        if (observedOriginIndex >= 0 && observedDestinationIndex > observedOriginIndex) {
            val route = observedItinerary
                .subList(observedOriginIndex, observedDestinationIndex + 1)
                .fold(mutableListOf<String>()) { acc, label ->
                    if (acc.none { timelineSamePlace0256(it, label) }) acc += label
                    acc
                }
            if (route.size >= 2) {
                route[0] = origin
                route[route.lastIndex] = destination
                return route
            }
        }
    }

    val labels = linkedMapOf(originKey to origin, destinationKey to destination)
    val edges = linkedMapOf<String, MutableSet<String>>()
    fun addNode(key: String) { edges.getOrPut(key) { linkedSetOf() } }
    fun addLabel(raw: String?): String? {
        val label = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val key = timelinePlaceKey0256(label).takeIf(String::isNotEmpty) ?: return null
        labels.putIfAbsent(key, label)
        addNode(key)
        return key
    }
    fun addEdge(from: String, to: String) {
        if (from != to) edges.getOrPut(from) { linkedSetOf() }.add(to)
        addNode(to)
    }

    addNode(originKey)
    addNode(destinationKey)
    entry.blablaPassengers.forEach { passenger ->
        val boarding = addLabel(passengerTimelinePlaceLabel(passenger.name, passenger.boarding))
        val dropoff = addLabel(passengerTimelinePlaceLabel(passenger.name, passenger.dropoff))
        if (boarding != null && dropoff != null) addEdge(boarding, dropoff)
    }

    val nodes = labels.keys.toList()
    nodes.filter { it != originKey }.forEach { addEdge(originKey, it) }
    nodes.filter { it != destinationKey }.forEach { addEdge(it, destinationKey) }

    val indegree = nodes.associateWith { 0 }.toMutableMap()
    edges.forEach { (_, destinations) -> destinations.forEach { indegree[it] = (indegree[it] ?: 0) + 1 } }
    val remaining = nodes.toMutableSet()
    val ordered = mutableListOf<String>()
    while (remaining.isNotEmpty()) {
        val candidates = remaining.filter { (indegree[it] ?: 0) == 0 }
        if (candidates.size != 1) return listOf(origin, destination)
        val next = candidates.single()
        ordered += next
        remaining -= next
        edges[next].orEmpty().forEach { target -> indegree[target] = (indegree[target] ?: 0) - 1 }
    }
    if (ordered.firstOrNull() != originKey || ordered.lastOrNull() != destinationKey) return listOf(origin, destination)
    return ordered.mapNotNull(labels::get)
}

internal fun buildTimelineExternalBackingTrip(entry: TripTimelineEntry, capacity: Int): Trip {
    require(capacity in 1..999) { "Inventário operacional indisponível." }
    val id = timelineExternalBackingTripId(entry)
        ?: throw IllegalArgumentException("Publicação sem identidade externa forte; não criei viagem duplicada.")
    val labels = timelineExternalRoutePointLabels(entry).ifEmpty { listOf(entry.origin, entry.destination) }
    require(labels.size >= 2) { "A rota precisa ter origem e destino." }
    val stops = labels.mapIndexed { index, label ->
        TripStop(
            id = timelineExternalStopId(id, label),
            order = index,
            name = label.trim(),
            address = label.trim(),
            plannedDepartureMillis = entry.departureAtMillis.takeIf { index == 0 },
            plannedArrivalMillis = entry.arrivalAtMillis.takeIf { index == labels.lastIndex },
        )
    }
    return Trip(
        id = id,
        title = "${labels.first()} → ${labels.last()}",
        departureAtMillis = entry.departureAtMillis,
        capacity = capacity,
        rotaCertaSeatAllocation = entry.rotaCertaSeatAllocation,
        status = if (entry.status == TripStatus.FULL) TripStatus.FULL else TripStatus.PUBLISHED,
        stops = stops,
        notes = "Backing local de publicação externa; identidade estável preservada pelo Rota Certa.",
        blablaProfileUuid = entry.blablaProfileUuid,
        blablaTripId = entry.blablaTripId,
        blablaManageUrl = entry.blablaTripHref,
        blablaPublicUrl = entry.blablaPublicHref,
        publishedSeats = entry.blablaPublishedSeats,
        recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
    )
}

internal fun augmentExternalBackingStops(trip: Trip, entry: TripTimelineEntry): Trip {
    val current = trip.stops.sortedBy(TripStop::order)
    if (current.size != 2) return trip
    val labels = timelineExternalRoutePointLabels(entry)
    if (labels.size <= 2) return trip
    val first = current.first()
    val last = current.last()
    if (!timelineSamePlace0256(first.name, labels.first()) || !timelineSamePlace0256(last.name, labels.last())) return trip
    val stops = labels.mapIndexed { index, label ->
        when (index) {
            0 -> first.copy(order = 0)
            labels.lastIndex -> last.copy(order = labels.lastIndex)
            else -> TripStop(
                id = timelineExternalStopId(trip.id, label),
                order = index,
                name = label,
                address = label,
            )
        }
    }
    return trip.copy(stops = stops)
}

internal fun planTimelineExternalCapacityClaims(
    entry: TripTimelineEntry,
    trip: Trip,
    existingBookings: List<Booking>,
): List<Booking> {
    val externalKey = timelineStrongExternalTripKey(entry) ?: return emptyList()
    val stops = trip.stops.sortedBy(TripStop::order)
    if (stops.size < 2) return emptyList()
    val now = System.currentTimeMillis()
    val segmentCount = stops.size - 1

    fun isActive(booking: Booking): Boolean = when (booking.status) {
        BookingStatus.CONFIRMED -> true
        BookingStatus.HELD -> booking.holdExpiresAtMillis == null || booking.holdExpiresAtMillis > now
        BookingStatus.REQUESTED,
        BookingStatus.REJECTED,
        BookingStatus.CANCELLED,
        BookingStatus.EXPIRED,
        -> false
    }

    val localExternalBySegment = IntArray(segmentCount)
    existingBookings.asSequence()
        .filter { booking ->
            booking.tripId == trip.id &&
                booking.source == BookingSource.BLABLACAR &&
                booking.seats > 0 &&
                isActive(booking) &&
                !booking.sourceReference.startsWith(EXTERNAL_CAPACITY_PREFIX)
        }
        .forEach { booking ->
            val fromIndex = stops.indexOfFirst { it.id == booking.boardingStopId }
            val toIndex = stops.indexOfFirst { it.id == booking.dropoffStopId }
            if (fromIndex >= 0 && toIndex > fromIndex) {
                for (segment in fromIndex until toIndex) localExternalBySegment[segment] += booking.seats
            }
        }

    val mappedExternalBySegment = IntArray(segmentCount)
    var mappedPassengerSeats = 0
    val passengerSeatEvidence = entry.blablaPassengers.sumOf { it.seats.coerceAtLeast(1) }
    val activeManualPassengers = existingBookings.filter { booking ->
        booking.tripId == trip.id &&
            booking.source in setOf(BookingSource.PRIVATE, BookingSource.OTHER) &&
            booking.capacityClaimType == CapacityClaimType.PASSENGER &&
            booking.seats > 0 &&
            isActive(booking)
    }
    entry.blablaPassengers.forEach { passenger ->
        val seats = passenger.seats.coerceAtLeast(1)
        val boardingIndex = timelineStopIndexForLabel0256(stops, passengerTimelinePlaceLabel(passenger.name, passenger.boarding))
        val dropoffIndex = timelineStopIndexForLabel0256(stops, passengerTimelinePlaceLabel(passenger.name, passenger.dropoff))
        if (boardingIndex >= 0 && dropoffIndex > boardingIndex) {
            mappedPassengerSeats += seats
            val externalPhone = passenger.phone?.filter(Char::isDigit)?.takeIf { it.length >= 8 }
            val exactLocalMirror = externalPhone != null && activeManualPassengers.any { booking ->
                val localPhone = booking.passengerContact.filter(Char::isDigit)
                val localFrom = stops.indexOfFirst { it.id == booking.boardingStopId }
                val localTo = stops.indexOfFirst { it.id == booking.dropoffStopId }
                localPhone == externalPhone && booking.seats == seats && localFrom == boardingIndex && localTo == dropoffIndex
            }
            if (!exactLocalMirror) {
                for (segment in boardingIndex until dropoffIndex) mappedExternalBySegment[segment] += seats
            }
        }
    }

    val externalSeatEvidence = maxOf(
        entry.sourcePassengerSeats[BookingSource.BLABLACAR] ?: 0,
        passengerSeatEvidence,
    )
    if (externalSeatEvidence <= 0) return emptyList()
    val unsegmentedSeats = (externalSeatEvidence - mappedPassengerSeats).coerceAtLeast(0)
    val desiredBySegment = IntArray(segmentCount) { segment ->
        val externalFloor = mappedExternalBySegment[segment] + unsegmentedSeats
        (externalFloor - localExternalBySegment[segment]).coerceAtLeast(0)
    }
    if (desiredBySegment.all { it == 0 }) return emptyList()

    return buildList {
        desiredBySegment.forEachIndexed { segment, seats ->
            if (seats <= 0) return@forEachIndexed
            val from = stops[segment]
            val to = stops[segment + 1]
            val refHash = sha256Short0256("$externalKey|segment|${from.id}|${to.id}", 24)
            add(
                Booking(
                    id = "timeline-ext-seat-$refHash",
                    tripId = trip.id,
                    passengerName = "Reservas externas reconciliadas",
                    boardingStopId = from.id,
                    dropoffStopId = to.id,
                    seats = seats,
                    status = BookingStatus.CONFIRMED,
                    source = BookingSource.BLABLACAR,
                    capacityClaimType = CapacityClaimType.EXTERNAL_OCCUPANCY,
                    sourceReference = "$EXTERNAL_CAPACITY_PREFIX$refHash",
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
        }
    }
}

internal fun isTimelineExternalCapacityClaim(booking: Booking): Boolean =
    booking.capacityClaimType == CapacityClaimType.EXTERNAL_OCCUPANCY &&
        booking.sourceReference.startsWith(EXTERNAL_CAPACITY_PREFIX)

internal data class TimelineSeatSyncPlan(
    val loads: List<SegmentLoad>,
    val desiredPublishedSeats: Int,
    val localTripId: String,
)

/**
 * Computes the single publication target from the bottleneck of the already-existing
 * per-segment inventory engine. External roster must be complete; otherwise we fail
 * closed instead of inventing segment availability.
 */
internal fun timelineDesiredSeatSyncPlan(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
): TimelineSeatSyncPlan? = timelineDesiredSeatSyncPlan(
    entry = entry,
    trip = trip,
    bookingsSnapshot = trip?.let { store.bookingsFor(it.id) }.orEmpty(),
)

/**
 * UI-safe overload: callers that already own the canonical booking snapshot must not
 * deserialize TripStore again while a LazyColumn item is entering the viewport.
 */
internal fun timelineDesiredSeatSyncPlan(
    entry: TripTimelineEntry,
    trip: Trip?,
    bookingsSnapshot: List<Booking>,
): TimelineSeatSyncPlan? {
    if (timelineStrongExternalTripKey(entry) == null || entry.capacity !in 1..999) return null
    if (entry.blablaPassengerRosterComplete != true) return null

    val base = when {
        trip != null -> trip.copy(capacity = entry.capacity)
        else -> buildTimelineExternalBackingTrip(entry, entry.capacity)
    }
    val working = augmentExternalBackingStops(base, entry).copy(capacity = entry.capacity)
    val existing = trip?.let { target -> bookingsSnapshot.filter { it.tripId == target.id } }.orEmpty()
    val localClaims = existing.filterNot(::isTimelineExternalCapacityClaim)
    val externalClaims = planTimelineExternalCapacityClaims(entry, working, localClaims)
    val loads = SeatAvailabilityEngine.segmentLoads(working, localClaims + externalClaims)
    if (loads.isEmpty()) return null
    return TimelineSeatSyncPlan(
        loads = loads,
        desiredPublishedSeats = loads.minOf(SegmentLoad::availableSeats).coerceIn(0, entry.capacity),
        localTripId = working.id,
    )
}

internal fun prepareTimelineTripForPassenger(
    entry: TripTimelineEntry,
    store: TripStore,
): TimelinePassengerTripPreparation {
    val strongExternal = timelineStrongExternalTripKey(entry)
    require(timelineManualPassengerOccupancyKnown(entry)) {
        "A ocupação BlaBlaCar deste card ainda não foi lida por completo. Sincronize este card antes de adicionar passageiro por fora."
    }
    val allTrips = store.trips()
    val deterministicId = timelineExternalBackingTripId(entry)
    val direct = entry.localTripId?.let(store::getTrip)
        ?: store.getTrip(entry.tripId)
        ?: deterministicId?.let(store::getTrip)
    val strongMatches = if (direct == null && strongExternal != null) {
        allTrips.filter { trip ->
            canonicalExternalTripIdentityKey(
                trip.blablaProfileUuid,
                trip.blablaTripId,
                trip.blablaManageUrl,
            ) == strongExternal
        }
    } else emptyList()
    if (strongMatches.size > 1) {
        throw IllegalStateException("Identidade externa forte está associada a mais de uma viagem interna. Não escolhi automaticamente.")
    }
    var trip = direct ?: strongMatches.singleOrNull()
    var created = false
    if (trip == null) {
        require(strongExternal != null) { "Selecione uma viagem interna existente ou crie uma nova viagem particular." }
        require(entry.capacity in 1..999) { "Não há vagas disponíveis nesta viagem." }
        trip = buildTimelineExternalBackingTrip(entry, entry.capacity)
        created = true
    }

    if (strongExternal != null) {
        trip = augmentExternalBackingStops(trip, entry)
        if (trip.status == TripStatus.DRAFT) trip = trip.copy(status = if (entry.status == TripStatus.FULL) TripStatus.FULL else TripStatus.PUBLISHED)
        if (entry.capacity in 1..999) {
            trip = trip.copy(
                capacity = entry.capacity,
                rotaCertaSeatAllocation = entry.rotaCertaSeatAllocation ?: trip.rotaCertaSeatAllocation,
                blablaProfileUuid = entry.blablaProfileUuid ?: trip.blablaProfileUuid,
                blablaTripId = entry.blablaTripId ?: trip.blablaTripId,
                blablaManageUrl = entry.blablaTripHref ?: trip.blablaManageUrl,
                blablaPublicUrl = entry.blablaPublicHref ?: trip.blablaPublicUrl,
                publishedSeats = entry.blablaPublishedSeats ?: trip.publishedSeats,
            )
        }
        trip = store.saveTrip(trip)

        val previousClaims = store.bookingsFor(trip.id).filter(::isTimelineExternalCapacityClaim)
        val desired = planTimelineExternalCapacityClaims(entry, trip, store.bookingsFor(trip.id))
        val desiredIds = desired.map(Booking::id).toSet()
        previousClaims.filterNot { it.id in desiredIds }.forEach { store.deleteBooking(it.id) }
        desired.forEach(store::saveBooking)
        val mirrored = desired.sumOf(Booking::seats)
        return TimelinePassengerTripPreparation(store.getTrip(trip.id) ?: trip, created, mirrored)
    }

    return TimelinePassengerTripPreparation(trip, created, 0)
}

@Composable
internal fun TimelineCardQuickPassengerDialog(
    entry: TripTimelineEntry,
    trip: Trip,
    store: TripStore,
    onChanged: (String) -> Unit,
    onTargetSync: () -> Unit,
    onDismiss: () -> Unit,
    canonicalBookings0494: List<Booking> = emptyList(),
) {
    val formatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • HH:mm", Locale.getDefault()) }
    val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar passageiro") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${entry.profileLabel} • $date", style = MaterialTheme.typography.labelLarge)
                Text("${entry.origin} → ${entry.destination}")
                Text(
                    "A inclusão é gravada primeiro no backend canônico. A Timeline não ajusta a publicação BlaBlaCar nem recalcula capacidade localmente.",
                    style = MaterialTheme.typography.bodySmall,
                )
                QuickPassengerPanel(
                    trip = trip,
                    store = store,
                    onChanged = onChanged,
                    onBlaBlaSyncRequested = null,
                    externalSeatTarget = null,
                    onSaved = onDismiss,
                    showExistingPassengers = true,
                    canonicalBookings0494 = canonicalBookings0494,
                    canonicalBackendAuthority0494 = entry.canonicalBackendAuthoritative0494,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
internal fun GlobalPassengerFlowPanel(
    entries: List<TripTimelineEntry>,
    store: TripStore,
    openRequestToken: Int,
    formatter: DateTimeFormatter,
    onChanged: (String) -> Unit,
    onNewTrip: (String) -> Unit,
    onTargetSync: (TripTimelineEntry, Trip) -> Unit,
    resumeRequestToken: Int = 0,
    resumePassengerId: String? = null,
    resumeTripId: String? = null,
    canonicalTrips0494: List<Trip> = emptyList(),
    canonicalBookings0494: List<Booking> = emptyList(),
) {
    val context = LocalContext.current
    val passengerStore = remember(context) { PassengerIdentityStore(context) }
    var open by remember { mutableStateOf(false) }
    var selectedPassenger by remember { mutableStateOf<PassengerProfile?>(null) }
    var selectedEntry by remember { mutableStateOf<TripTimelineEntry?>(null) }
    var selectedTrip by remember { mutableStateOf<Trip?>(null) }
    var passengerSearch by remember { mutableStateOf("") }
    var registeringNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newWhatsapp by remember { mutableStateOf("") }
    var passengerRevision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    fun resetFresh() {
        selectedPassenger = null
        selectedEntry = null
        selectedTrip = null
        passengerSearch = ""
        registeringNew = false
        newName = ""
        newWhatsapp = ""
        error = null
    }

    LaunchedEffect(openRequestToken) {
        if (openRequestToken > 0) {
            open = true
            resetFresh()
        }
    }

    LaunchedEffect(resumeRequestToken) {
        if (resumeRequestToken > 0) {
            val passenger = passengerStore.profile(resumePassengerId)
            val trip = resumeTripId?.let { tripId ->
                canonicalTrips0494.firstOrNull { it.id == tripId } ?: store.getTrip(tripId)
            }
            if (passenger != null && trip != null) {
                open = true
                resetFresh()
                selectedPassenger = passenger
                selectedTrip = trip
                selectedEntry = entries.firstOrNull { entry ->
                    entry.localTripId == trip.id || entry.tripId == trip.id
                }
                error = null
            }
        }
    }

    if (!open) return


    val passengerSnapshot = remember(passengerRevision, openRequestToken, resumeRequestToken) {
        passengerStore.pickerSnapshot()
    }
    val passengerObservations = remember(passengerRevision, passengerSnapshot) {
        passengerSnapshot.profiles.associate { profile -> profile.id to passengerStore.observations(profile.id) }
    }
    val query = passengerSearch.trim()
    val passengerCandidates = remember(passengerSnapshot, passengerObservations, query) {
        if (query.isBlank()) {
            passengerSnapshot.profiles
        } else {
            searchCanonicalPassengers(
                profiles = passengerSnapshot.profiles,
                observationsByProfile = passengerObservations,
                raw = query,
                limit = Int.MAX_VALUE,
            )
        }
    }

    LaunchedEffect(openRequestToken, passengerRevision, selectedPassenger?.id) {
        if (open && selectedPassenger == null) {
            AgendaTrace.event(context, "PASSENGER_PICKER_OPENED", "stage=select_passenger")
            AgendaTrace.event(
                context,
                "PASSENGER_SOURCE_COUNT",
                "count=${passengerSnapshot.rawProfileCount} distinctPassengerIds=${passengerSnapshot.distinctPassengerIdCount}",
            )
            AgendaTrace.event(
                context,
                "PASSENGER_CANONICAL_COUNT",
                "count=${passengerSnapshot.profiles.size}",
            )
            if (passengerSnapshot.resolvedDuplicateCount > 0) {
                AgendaTrace.event(
                    context,
                    "PASSENGER_DUPLICATE_RESOLVED",
                    "source=picker count=${passengerSnapshot.resolvedDuplicateCount}",
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("➕ Adicionar a uma viagem") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {

                    selectedPassenger == null -> {
                        if (!registeringNew) {
                            Text("1. Selecionar passageiro", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Escolha uma pessoa já cadastrada. O passengerId permanece a identidade; nome e WhatsApp não criam uma identidade nova.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedTextField(
                                value = passengerSearch,
                                onValueChange = { passengerSearch = it.take(120); error = null },
                                label = { Text("Buscar por nome, WhatsApp ou identificador") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (passengerCandidates.isEmpty()) {
                                Text(
                                    if (query.isBlank()) "Nenhum passageiro cadastrado." else "Nenhum passageiro encontrado.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    items(passengerCandidates, key = PassengerProfile::id) { profile ->
                                        OutlinedButton(
                                            enabled = !profile.blocked,
                                            onClick = {
                                                AgendaTrace.event(
                                                    context,
                                                    "PASSENGER_SELECTED",
                                                    "passengerHash=${passengerDebugIdentityHash(profile.id)}",
                                                )
                                                selectedPassenger = profile
                                                selectedEntry = null
                                                selectedTrip = null
                                                registeringNew = false
                                                error = null
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            val phone = formatPassengerContactForDisplay(profile.agendaAccessContact())
                                            Text((if (profile.blocked) "⛔ " else "") + profile.displayName + " • " + phone)
                                        }
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    registeringNew = true
                                    newName = passengerSearch.takeIf { it.any(Char::isLetter) }.orEmpty()
                                    newWhatsapp = passengerSearch.takeIf { it.filter(Char::isDigit).length >= 8 }.orEmpty()
                                    error = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Cadastrar novo passageiro") }
                        } else {
                            Text("Cadastrar novo passageiro", style = MaterialTheme.typography.titleSmall)
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it.take(120); error = null },
                                label = { Text("Nome") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = newWhatsapp,
                                onValueChange = { newWhatsapp = it.take(40); error = null },
                                label = { Text("WhatsApp") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Button(
                                enabled = newName.isNotBlank() && passengerContactKey(newWhatsapp).isNotBlank(),
                                onClick = {
                                    val exact = passengerStore.pickerContactMatches(newWhatsapp)
                                    when {
                                        exact.size > 1 -> {
                                            error = "Há mais de uma identidade canônica com esse WhatsApp. Selecione a pessoa existente; nenhum passengerId será criado."
                                        }
                                        exact.size == 1 -> {
                                            selectedPassenger = exact.single()
                                            AgendaTrace.event(
                                                context,
                                                "PASSENGER_SELECTED",
                                                "passengerHash=${passengerDebugIdentityHash(exact.single().id)} source=existing_contact",
                                            )
                                            registeringNew = false
                                            passengerRevision++
                                            error = null
                                            onChanged("Passageiro existente reconhecido e selecionado sem duplicar a identidade.")
                                        }
                                        else -> {
                                            runCatching {
                                                passengerStore.saveProfile(
                                                    PassengerProfile(
                                                        displayName = newName.trim(),
                                                        whatsapp = newWhatsapp.trim(),
                                                        agendaAccessWhatsapp = newWhatsapp.trim(),
                                                    ),
                                                )
                                            }
                                                .onSuccess { created ->
                                                    selectedPassenger = created
                                                    AgendaTrace.event(
                                                        context,
                                                        "PASSENGER_SELECTED",
                                                        "passengerHash=${passengerDebugIdentityHash(created.id)} source=new_profile",
                                                    )
                                                    registeringNew = false
                                                    passengerRevision++
                                                    error = null
                                                    onChanged("Passageiro cadastrado e selecionado. Agora escolha a viagem.")
                                                }
                                                .onFailure { failure ->
                                                    error = failure.message ?: "Não foi possível cadastrar o passageiro."
                                                }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Cadastrar e continuar") }
                            TextButton(onClick = { registeringNew = false; error = null }) { Text("Cancelar cadastro") }
                        }
                    }

                    selectedTrip == null -> {
                        val passenger = selectedPassenger!!
                        Text("✓ ${passenger.displayName}", style = MaterialTheme.typography.labelLarge)
                        Text("2. Selecionar viagem", style = MaterialTheme.typography.titleSmall)
                        if (passenger.blocked) {
                            Text("⛔ NÃO ACEITO NO MEU CARRO — a inclusão está bloqueada.", style = MaterialTheme.typography.bodySmall)
                        } else if (entries.isEmpty()) {
                            Text("Não há viagens disponíveis na Timeline.", style = MaterialTheme.typography.bodySmall)
                        } else {

                            val sortedEntries = entries.sortedBy(TripTimelineEntry::departureAtMillis)
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(sortedEntries, key = TripTimelineEntry::tripId) { entry ->
                                    val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))
                                    val source = if (timelineStrongExternalTripKey(entry) != null) "BlaBlaCar" else "Particular"
                                    val vacancies = if (entry.minimumAvailableSeats == entry.maximumAvailableSeats) {
                                        "Vagas disponíveis: ${entry.minimumAvailableSeats}"
                                    } else {
                                        "Vagas disponíveis: ${entry.minimumAvailableSeats}–${entry.maximumAvailableSeats} por trecho"
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            error = null
                                            val canonicalTrip0494 = canonicalTrips0494.firstOrNull { it.id == entry.tripId }
                                            if (entry.canonicalBackendAuthoritative0494 && canonicalTrip0494 != null) {
                                                selectedEntry = entry
                                                selectedTrip = canonicalTrip0494
                                                onChanged("Viagem canônica selecionada. A capacidade será validada pelo backend ao salvar.")
                                            } else {
                                                runCatching { prepareTimelineTripForPassenger(entry, store) }
                                                    .onSuccess { prepared ->
                                                        selectedEntry = entry
                                                        selectedTrip = prepared.trip
                                                        onChanged("Viagem selecionada.")
                                                    }
                                                    .onFailure { error = it.message ?: "Não foi possível preparar esta viagem." }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("${entry.profileLabel} • $date\n${entry.origin} → ${entry.destination}\n$source • $vacancies")
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            enabled = !passenger.blocked,
                            onClick = {
                                open = false
                                onNewTrip(passenger.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Criar nova viagem") }
                    }


                    else -> {
                        val passenger = selectedPassenger!!
                        val trip = selectedTrip!!
                        val entry = selectedEntry
                        val stops = trip.stops.sortedBy(TripStop::order)
                        val route = listOfNotNull(stops.firstOrNull()?.name, stops.lastOrNull()?.name).joinToString(" → ")
                        val date = formatter.format(Instant.ofEpochMilli(trip.departureAtMillis).atZone(ZoneId.systemDefault()))
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("✓ ${passenger.displayName}", style = MaterialTheme.typography.labelLarge)
                            Text("✓ $date • $route", style = MaterialTheme.typography.labelLarge)
                            Text("3. Dados da reserva", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (entry != null && timelineStrongExternalTripKey(entry) != null) {
                                    "Para viagem BlaBlaCar, escolha os pontos disponíveis da rota."
                                } else {
                                    "Para viagem particular, utilize a origem e o destino configurados na viagem."
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            QuickPassengerPanel(
                                trip = trip,
                                store = store,
                                onChanged = onChanged,
                                onBlaBlaSyncRequested = if (entry?.canonicalBackendAuthoritative0494 == true) null else {
                                    if (entry != null && timelineStrongExternalTripKey(entry) != null) {
                                        { onTargetSync(entry, trip) }
                                    } else null
                                },
                                externalSeatTarget = if (entry?.canonicalBackendAuthoritative0494 == true) null
                                    else entry?.let(BlaBlaReliableSeatSyncBridge::targetForTimeline),
                                onSaved = { open = false },
                                showExistingPassengers = false,
                                initialPassenger = passenger,
                                lockPassengerIdentity = true,
                                requireConfirmation = true,
                                primaryActionLabel = "Adicionar à viagem",
                                canonicalBookings0494 = canonicalBookings0494,
                                canonicalBackendAuthority0494 = entry?.canonicalBackendAuthoritative0494 == true,
                            )
                        }
                    }
                }
                error?.let { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                when {
                    selectedTrip != null -> {
                        selectedTrip = null
                        selectedEntry = null
                    }
                    selectedPassenger != null -> selectedPassenger = null
                    registeringNew -> registeringNew = false
                    else -> open = false
                }
                error = null
            }) {
                Text(if (selectedPassenger != null || selectedTrip != null || registeringNew) "Voltar" else "Fechar")
            }
        },
    )
}

private fun timelineExternalStopId(tripId: String, label: String): String =
    "ext-stop-${sha256Short0256("$tripId|${timelinePlaceKey0256(label)}", 20)}"

private fun timelineStopIndexForLabel0256(stops: List<TripStop>, raw: String?): Int {
    val label = raw?.trim()?.takeIf(String::isNotEmpty) ?: return -1
    return stops.indexOfFirst { stop -> timelineSamePlace0256(stop.name, label) || timelineSamePlace0256(stop.address, label) }
}

private fun timelineSamePlace0256(left: String, right: String): Boolean {
    val a = timelinePlaceKey0256(left)
    val b = timelinePlaceKey0256(right)
    if (a.isBlank() || b.isBlank()) return false
    if (a == b) return true
    val shorter = if (a.length <= b.length) a else b
    val longer = if (a.length <= b.length) b else a
    return shorter.length >= 5 && longer.contains(shorter)
}

private fun timelinePlaceKey0256(raw: String): String = java.text.Normalizer.normalize(
    raw.substringBefore(',').trim(),
    java.text.Normalizer.Form.NFD,
).replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun looksCanonicalTimelineProfileUuid0256(value: String): Boolean = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
).matches(value)

private fun sha256Short0256(value: String, chars: Int): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    .take(chars)
