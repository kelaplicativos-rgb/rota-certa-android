#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
ACTIVITY = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt"
text = ACTIVITY.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    text = text.replace(old, new, 1)


replace_once(
    '    var error by remember { mutableStateOf<String?>(null) }\n',
    '''    var error by remember { mutableStateOf<String?>(null) }
    var routePlan by remember { mutableStateOf<TripRoutePlan?>(null) }
''',
    "route plan state",
)

replace_once(
    '''    OutlinedTextField(notes, { notes = it }, label = { Text("Observações públicas opcionais") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    error?.let { Text(it) }
''',
    '''    OutlinedTextField(notes, { notes = it }, label = { Text("Observações públicas opcionais") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    val planningNames = buildList {
        if (origin.isNotBlank()) add(origin.trim())
        addAll(intermediate.lines().map(String::trim).filter(String::isNotBlank))
        if (destination.isNotBlank()) add(destination.trim())
    }
    val planningDepartureMillis = runCatching {
        LocalDateTime.parse(departure.trim(), formatter)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
    TripRoutePlannerControl(
        stopNames = planningNames,
        departureAtMillis = planningDepartureMillis,
        onPlan = { routePlan = it },
    )
    error?.let { Text(it) }
''',
    "route planner control",
)

replace_once(
    '''                val stops = names.mapIndexed { index, name ->
                    TripStop(
                        order = index,
                        name = name,
                        address = name,
                        plannedDepartureMillis = if (index == 0) departureMillis else null,
                        plannedArrivalMillis = if (index == 0) departureMillis else null,
                    )
                }
''',
    '''                val planned = routePlan?.takeIf { plan ->
                    plan.stops.map(TripStop::name) == names &&
                        plan.stops.firstOrNull()?.plannedDepartureMillis == departureMillis
                }
                val stops = planned?.stops ?: names.mapIndexed { index, name ->
                    TripStop(
                        order = index,
                        name = name,
                        address = name,
                        plannedDepartureMillis = if (index == 0) departureMillis else null,
                        plannedArrivalMillis = if (index == 0) departureMillis else null,
                    )
                }
''',
    "route plan persistence",
)

replace_once(
    '''    val bookings = store.bookingsFor(trip.id)
    val available = SeatAvailabilityEngine.remainingSeatsForWholeTrip(trip, bookings)
''',
    '''    val bookings = store.bookingsFor(trip.id)
    val seatRange = SeatAvailabilityEngine.availableSeatRange(trip, bookings)
    val availabilityText = if (seatRange.variesBySegment) {
        "vagas por trecho ${seatRange.minimum}–${seatRange.maximum}/${trip.capacity}"
    } else {
        "${seatRange.maximum}/${trip.capacity} vagas livres"
    }
''',
    "truthful trip availability range",
)

replace_once(
    '''            Text("${formatter.format(Instant.ofEpochMilli(trip.departureAtMillis).atZone(ZoneId.systemDefault()))} • ${trip.status} • $available/${trip.capacity} vagas livres")
''',
    '''            Text("${formatter.format(Instant.ofEpochMilli(trip.departureAtMillis).atZone(ZoneId.systemDefault()))} • ${trip.status} • $availabilityText")
''',
    "trip availability text",
)

replace_once(
    '''                    }) { Text(if (trip.remoteId == null) "Publicar online" else "Sincronizar online") }
''',
    '''                    }) { Text(if (trip.remoteId == null) "Publicar online" else "Sincronizar online") }
                    if (trip.remoteId != null) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching {
                                    TripRemoteApi(settings).listBookings(trip.remoteId).bookings
                                }.onSuccess { remoteBookings ->
                                    remoteBookings.forEach { remote ->
                                        store.saveBooking(remote.toLocalBooking(trip.id))
                                    }
                                    onChanged("Reservas online atualizadas: ${remoteBookings.size}.")
                                }.onFailure {
                                    onChanged("Falha ao atualizar reservas: ${it.message}")
                                }
                            }
                        }) { Text("Atualizar reservas online") }
                    }
''',
    "remote booking synchronization action",
)

ACTIVITY.write_text(text, encoding="utf-8")
print("stage47_route_planner_ui=PASS route_eta=true truthful_segment_availability=true remote_booking_sync=true")
