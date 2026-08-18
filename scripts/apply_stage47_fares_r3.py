#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
PATCHES = Path(sys.argv[2]).resolve()


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

pkg = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips"
domain = pkg / "TripDomain.kt"
once(domain,
'''    val plannedDepartureMillis: Long? = null,
)
''',
'''    val plannedDepartureMillis: Long? = null,
    val priceToNextCents: Long = 0L,
)
''', "TripStop price")
once(domain,
'''object SeatAvailabilityEngine {
''',
'''object TripFareEngine {
    fun farePerSeatCents(trip: Trip, boardingStopId: String, dropoffStopId: String): Long {
        val stops = trip.stops.sortedBy(TripStop::order)
        val fromIndex = stops.indexOfFirst { it.id == boardingStopId }
        val toIndex = stops.indexOfFirst { it.id == dropoffStopId }
        require(fromIndex >= 0) { "Unknown boarding stop" }
        require(toIndex > fromIndex) { "Dropoff must be after boarding" }
        return (fromIndex until toIndex).sumOf { stops[it].priceToNextCents.coerceAtLeast(0L) }
    }

    fun totalFareCents(trip: Trip, boardingStopId: String, dropoffStopId: String, seats: Int): Long {
        require(seats > 0) { "Seats must be positive" }
        return farePerSeatCents(trip, boardingStopId, dropoffStopId) * seats.toLong()
    }
}

object SeatAvailabilityEngine {
''', "TripFareEngine")

activity = pkg / "TripsActivity.kt"
once(activity, 'import java.time.format.DateTimeFormatter\n', 'import java.time.format.DateTimeFormatter\nimport java.util.Locale\nimport kotlin.math.roundToLong\n', "fare imports")
once(activity,
'''    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
''',
'''    var notes by remember { mutableStateOf("") }
    var segmentPrices by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
''', "fare state")
once(activity,
'''    OutlinedTextField(departure, { departure = it }, label = { Text("Saída — dd/MM/aaaa HH:mm") }, modifier = Modifier.fillMaxWidth())
''',
'''    OutlinedTextField(
        segmentPrices,
        { segmentPrices = it },
        label = { Text("Valores por trecho em R$ — uma linha por trecho") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
    Text("Ex.: origem → parada = 20,00; parada → destino = 25,00. Deixe vazio para não publicar valor.", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(departure, { departure = it }, label = { Text("Saída — dd/MM/aaaa HH:mm") }, modifier = Modifier.fillMaxWidth())
''', "fare editor")
once(activity,
'''                require(names.size >= 2) { "A viagem precisa de origem e destino." }
                val stops = names.mapIndexed { index, name ->
                    TripStop(
                        order = index,
                        name = name,
                        address = name,
                        plannedDepartureMillis = if (index == 0) departureMillis else null,
                        plannedArrivalMillis = if (index == 0) departureMillis else null,
                    )
                }
''',
'''                require(names.size >= 2) { "A viagem precisa de origem e destino." }
                val rawPrices = segmentPrices.lines().map(String::trim).filter(String::isNotBlank)
                val prices = if (rawPrices.isEmpty()) List(names.size - 1) { 0L } else {
                    require(rawPrices.size == names.size - 1) { "Informe exatamente ${names.size - 1} valor(es), um para cada trecho." }
                    rawPrices.map { raw -> parseFareCents(raw) ?: throw IllegalArgumentException("Valor inválido: $raw") }
                }
                val stops = names.mapIndexed { index, name ->
                    TripStop(
                        order = index,
                        name = name,
                        address = name,
                        plannedDepartureMillis = if (index == 0) departureMillis else null,
                        plannedArrivalMillis = if (index == 0) departureMillis else null,
                        priceToNextCents = prices.getOrElse(index) { 0L },
                    )
                }
''', "fare parse")
once(activity,
'''                        Text("${load.from.name} → ${load.to.name}: ${load.occupiedSeats}/${trip.capacity} ocupadas")
''',
'''                        val price = load.from.priceToNextCents
                        Text(buildString {
                            append("${load.from.name} → ${load.to.name}: ${load.occupiedSeats}/${trip.capacity} ocupadas")
                            if (price > 0L) append(" • ${formatFare(price)} por pessoa")
                        })
''', "fare card")
once(activity,
'''    Text("Disponíveis nesse trecho: ${availability?.availableSeats ?: 0}")
''',
'''    Text("Disponíveis nesse trecho: ${availability?.availableSeats ?: 0}")
    val farePerSeat = runCatching { TripFareEngine.farePerSeatCents(trip, stops[fromIndex].id, stops[toIndex].id) }.getOrDefault(0L)
    if (farePerSeat > 0L) Text("Valor: ${formatFare(farePerSeat)} por pessoa • total ${formatFare(farePerSeat * requested.toLong())}")
''', "manual fare")
once(activity,
'''@Composable
private fun OnlineSettingsEditor(
''',
'''private fun parseFareCents(value: String): Long? {
    val normalized = value.trim().replace("R$", "", ignoreCase = true).replace(" ", "").replace(".", "").replace(",", ".")
    val amount = normalized.toDoubleOrNull() ?: return null
    if (!amount.isFinite() || amount < 0.0 || amount > 1_000_000.0) return null
    return (amount * 100.0).roundToLong()
}

private fun formatFare(cents: Long): String = String.format(Locale("pt", "BR"), "R$ %.2f", cents.coerceAtLeast(0L) / 100.0)

@Composable
private fun OnlineSettingsEditor(
''', "fare helpers")

backend = PATCHES / "trip-platform/functions/index.js"
once(backend,
'''    plannedDepartureMillis: Number.isFinite(raw.plannedDepartureMillis) ? raw.plannedDepartureMillis : null,
  }));
  if (stops.some((stop) => !stop.name)) throw new Error("Toda parada precisa de um nome.");
''',
'''    plannedDepartureMillis: Number.isFinite(raw.plannedDepartureMillis) ? raw.plannedDepartureMillis : null,
    priceToNextCents: Number(raw.priceToNextCents || 0),
  }));
  if (stops.some((stop) => !stop.name)) throw new Error("Toda parada precisa de um nome.");
  if (stops.some((stop) => !Number.isInteger(stop.priceToNextCents) || stop.priceToNextCents < 0 || stop.priceToNextCents > 100000000)) throw new Error("Valor de trecho inválido.");
''', "backend fare normalize")
once(backend,
'''      const { fromIndex, toIndex } = bookingSegmentRange(trip, boardingStopId, dropoffStopId);
      const loads = Array.isArray(trip.segmentLoads) ? trip.segmentLoads.map(Number) : new Array(trip.stops.length - 1).fill(0);
''',
'''      const { fromIndex, toIndex } = bookingSegmentRange(trip, boardingStopId, dropoffStopId);
      const farePerSeatCents = (trip.stops || []).slice(fromIndex, toIndex).reduce((sum, stop) => sum + Math.max(0, Number(stop.priceToNextCents || 0)), 0);
      const totalFareCents = farePerSeatCents * seats;
      const loads = Array.isArray(trip.segmentLoads) ? trip.segmentLoads.map(Number) : new Array(trip.stops.length - 1).fill(0);
''', "booking fare calc")
once(backend, '        cancellationHash,\n        createdAtMillis: now,\n', '        cancellationHash,\n        farePerSeatCents,\n        totalFareCents,\n        createdAtMillis: now,\n', "booking fare persist")
once(backend,
'''      return { availableSeats: available - seats };
    });
    return json(res, 201, { bookingId, cancellationToken, availableSeats: result.availableSeats });
''',
'''      return { availableSeats: available - seats, farePerSeatCents, totalFareCents };
    });
    return json(res, 201, { bookingId, cancellationToken, availableSeats: result.availableSeats, farePerSeatCents: result.farePerSeatCents, totalFareCents: result.totalFareCents });
''', "booking fare response")

page = PATCHES / "trip-platform/public/app.js"
once(page,
'''function availableFor(fromIndex, toIndex) {
''',
'''function formatMoney(cents) { return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(Math.max(0, Number(cents || 0)) / 100); }
function fareFor(fromIndex, toIndex) {
  const stops = orderedStops();
  if (fromIndex < 0 || toIndex <= fromIndex) return 0;
  let cents = 0;
  for (let i = fromIndex; i < toIndex; i += 1) cents += Math.max(0, Number(stops[i]?.priceToNextCents || 0));
  return cents;
}

function availableFor(fromIndex, toIndex) {
''', "public fare helper")
once(page,
'''  $("availability").textContent = `${available} lugar(es) disponível(is) neste trecho`;
  $("reserve").disabled = available < 1 || requested > available;
''',
'''  $("availability").textContent = `${available} lugar(es) disponível(is) neste trecho`;
  const fare = fareFor(fromIndex, toIndex);
  $("fare").textContent = fare > 0 ? `${formatMoney(fare)} por pessoa • ${formatMoney(fare * requested)} total` : "Valor a combinar com o motorista";
  $("reserve").disabled = available < 1 || requested > available;
''', "public live fare")
once(page,
'''      seats,
    };
''',
'''      seats,
      farePerSeatCents: body.farePerSeatCents || 0,
      totalFareCents: body.totalFareCents || 0,
    };
''', "public booking fare state")
once(page,
'''    $("confirmationText").textContent = `Reserva ${body.bookingId} confirmada para ${seats} lugar(es).`;
''',
'''    $("confirmationText").textContent = `Reserva ${body.bookingId} confirmada para ${seats} lugar(es)${body.totalFareCents ? ` • total ${formatMoney(body.totalFareCents)}` : ""}.`;
''', "public fare confirmation")

html = PATCHES / "trip-platform/public/index.html"
once(html, '    <div id="availability" class="availability"></div>\n', '    <div id="availability" class="availability"></div>\n    <div id="fare" class="availability"></div>\n', "public fare block")

print("stage47_fares_r3=PASS segment_prices=true selected_route_total=true backend_authoritative_fare=true")
