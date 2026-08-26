package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

internal fun timelineStrongExternalTripKey(entry: TripTimelineEntry): String? {
    val profile = entry.blablaProfileUuid
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(::looksCanonicalTimelineProfileUuid0256)
        ?: return null
    val externalId = entry.blablaTripId?.trim()?.takeIf(String::isNotEmpty)
    val href = entry.blablaTripHref
        ?.trim()
        ?.substringBefore("&search_uuid=")
        ?.takeIf { value ->
            value.startsWith("https://") &&
                (value.contains("/rides/offer/") || value.contains("/trip/"))
        }
    val identity = externalId?.let { "id:$it" } ?: href?.let { "href:$it" } ?: return null
    return "$profile|$identity"
}

internal fun timelineExternalBackingTripId(entry: TripTimelineEntry): String? =
    timelineStrongExternalTripKey(entry)?.let { "timeline-ext-${sha256Short0256(it, 24)}" }

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
    require(capacity in 1..999) { "Capacidade física inválida." }
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
        status = if (entry.status == TripStatus.FULL) TripStatus.FULL else TripStatus.PUBLISHED,
        stops = stops,
        notes = "Backing local de publicação externa; identidade estável preservada pelo Rota Certa.",
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
    entry.blablaPassengers.forEach { passenger ->
        val seats = passenger.seats.coerceAtLeast(1)
        val boardingIndex = timelineStopIndexForLabel0256(stops, passengerTimelinePlaceLabel(passenger.name, passenger.boarding))
        val dropoffIndex = timelineStopIndexForLabel0256(stops, passengerTimelinePlaceLabel(passenger.name, passenger.dropoff))
        if (boardingIndex >= 0 && dropoffIndex > boardingIndex) {
            mappedPassengerSeats += seats
            for (segment in boardingIndex until dropoffIndex) mappedExternalBySegment[segment] += seats
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
                    capacityClaimType = CapacityClaimType.RESERVED_SEAT,
                    sourceReference = "$EXTERNAL_CAPACITY_PREFIX$refHash",
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
        }
    }
}

internal fun prepareTimelineTripForPassenger(
    entry: TripTimelineEntry,
    store: TripStore,
): TimelinePassengerTripPreparation {
    val strongExternal = timelineStrongExternalTripKey(entry)
    val allTrips = store.trips()
    val deterministicId = timelineExternalBackingTripId(entry)
    val direct = entry.localTripId?.let(store::getTrip)
        ?: store.getTrip(entry.tripId)
        ?: deterministicId?.let(store::getTrip)
    val physicalMatches = if (direct == null) timelinePhysicalTripMatches(entry, allTrips) else emptyList()
    if (direct == null && physicalMatches.size > 1) {
        throw IllegalStateException("Há mais de uma viagem interna compatível. Não criei nem escolhi uma viagem automaticamente.")
    }
    var trip = direct ?: physicalMatches.singleOrNull()
    var created = false
    if (trip == null) {
        require(strongExternal != null) { "Selecione uma viagem interna existente ou crie uma nova viagem particular." }
        require(entry.capacity in 1..999) { "Configure primeiro a capacidade física do veículo." }
        trip = buildTimelineExternalBackingTrip(entry, entry.capacity)
        created = true
    }

    if (strongExternal != null) {
        trip = augmentExternalBackingStops(trip, entry)
        if (trip.status == TripStatus.DRAFT) trip = trip.copy(status = if (entry.status == TripStatus.FULL) TripStatus.FULL else TripStatus.PUBLISHED)
        if (trip.capacity !in 1..999 && entry.capacity in 1..999) trip = trip.copy(capacity = entry.capacity)
        trip = store.saveTrip(trip)

        val previousClaims = store.bookingsFor(trip.id).filter {
            it.capacityClaimType == CapacityClaimType.RESERVED_SEAT &&
                it.sourceReference.startsWith(EXTERNAL_CAPACITY_PREFIX)
        }
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
                    "Passageiros adicionados aqui ocupam a Agenda imediatamente. Quando esta publicação BlaBlaCar tem identidade forte, salvar reduz as vagas externas e remover devolve somente uma redução já comprovada.",
                    style = MaterialTheme.typography.bodySmall,
                )
                QuickPassengerPanel(
                    trip = trip,
                    store = store,
                    onChanged = onChanged,
                    onBlaBlaSyncRequested = if (timelineStrongExternalTripKey(entry) != null) onTargetSync else null,
                    externalSeatTarget = BlaBlaReliableSeatSyncBridge.targetForTimeline(entry),
                    onSaved = onDismiss,
                    showExistingPassengers = true,
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
    formatter: DateTimeFormatter,
    onChanged: (String) -> Unit,
    onNewTrip: () -> Unit,
    onTargetSync: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var chooseTrip by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<TripTimelineEntry?>(null) }
    var selectedTrip by remember { mutableStateOf<Trip?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    OutlinedButton(onClick = {
        open = true
        chooseTrip = false
        selectedEntry = null
        selectedTrip = null
        error = null
    }, modifier = Modifier.fillMaxWidth()) {
        Text("+ Passageiro")
    }

    if (!open) return
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Adicionar passageiro") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    selectedTrip != null && selectedEntry != null -> {
                        val entry = selectedEntry!!
                        val trip = selectedTrip!!
                        val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))
                        Text("${entry.profileLabel} • $date", style = MaterialTheme.typography.labelLarge)
                        Text("${entry.origin} → ${entry.destination}")
                        Text(
                            "Embarque e destino são escolhidos nos pontos conhecidos desta rota. A capacidade é conferida por trecho.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        QuickPassengerPanel(
                            trip = trip,
                            store = store,
                            onChanged = onChanged,
                            onBlaBlaSyncRequested = if (timelineStrongExternalTripKey(entry) != null) {
                                { onTargetSync(canonicalTimelineProfileUuid(entry)) }
                            } else null,
                            externalSeatTarget = BlaBlaReliableSeatSyncBridge.targetForTimeline(entry),
                            onSaved = { open = false },
                            showExistingPassengers = false,
                        )
                    }
                    chooseTrip -> {
                        Text("Selecione a viagem", style = MaterialTheme.typography.titleSmall)
                        if (entries.isEmpty()) {
                            Text("Não há viagens disponíveis na Timeline.")
                        } else {
                            entries.sortedBy(TripTimelineEntry::departureAtMillis).forEach { entry ->
                                val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))
                                OutlinedButton(
                                    onClick = {
                                        error = null
                                        runCatching { prepareTimelineTripForPassenger(entry, store) }
                                            .onSuccess { prepared ->
                                                selectedEntry = entry
                                                selectedTrip = prepared.trip
                                                onChanged(
                                                    if (prepared.created) {
                                                        "Viagem vinculada internamente sem duplicar a publicação. Capacidade externa reconciliada."
                                                    } else {
                                                        "Viagem selecionada. Capacidade por trecho conferida."
                                                    },
                                                )
                                            }
                                            .onFailure { error = it.message ?: "Não foi possível preparar esta viagem." }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("${entry.profileLabel} • $date\n${entry.origin} → ${entry.destination}")
                                }
                            }
                        }
                    }
                    else -> {
                        Button(
                            enabled = entries.isNotEmpty(),
                            onClick = { chooseTrip = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Selecionar viagem") }
                        OutlinedButton(
                            onClick = {
                                open = false
                                onNewTrip()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Nova viagem") }
                        Text(
                            "Viagem BlaBlaCar: escolha os pontos conhecidos da rota. Viagem particular: use Nova viagem para informar origem e destino.",
                            style = MaterialTheme.typography.bodySmall,
                        )
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
                        chooseTrip = true
                    }
                    chooseTrip -> chooseTrip = false
                    else -> open = false
                }
                error = null
            }) { Text(if (chooseTrip || selectedTrip != null) "Voltar" else "Fechar") }
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
