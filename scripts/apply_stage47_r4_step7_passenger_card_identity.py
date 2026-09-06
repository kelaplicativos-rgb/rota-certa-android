#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
UI = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
if not UI.is_file():
    raise SystemExit(f"missing materialized Timeline UI: {UI}")

text = UI.read_text(encoding="utf-8")

# Passenger identity must be visible directly on the physical trip card. Phone is
# the strongest cross-channel identity signal. Name/seats/route are only secondary
# evidence and are visibly marked as such instead of silently asserting identity.
old = '''            if (entry.blablaPassengers.isNotEmpty()) {\n                TextButton(onClick = { detailsOpen = !detailsOpen }) {\n                    Text(if (detailsOpen) "Ocultar passageiros" else "Passageiros BlaBlaCar (${entry.blablaPassengers.sumOf(BlaBlaCollectorPassenger::seats)})")\n                }\n            }\n            if (detailsOpen) {\n                entry.blablaPassengers.forEach { passenger ->\n                    val route = listOfNotNull(passenger.boarding, passenger.dropoff).joinToString(" → ")\n                    Column {\n                        Text("${passenger.name} • ${passenger.seats} lugar(es)${if (route.isNotBlank()) " • $route" else ""}")\n                        val actions = buildList {\n                            passenger.phone?.takeIf(String::isNotBlank)?.let { phone ->\n                                add(ResponsiveTripAction("WhatsApp") { openWhatsApp(context, phone) })\n                            }\n                            passenger.booking_href?.takeIf(String::isNotBlank)?.let { href ->\n                                add(ResponsiveTripAction("Abrir reserva") { openBlaBlaHref(context, entry, href) })\n                            }\n                        }\n                        if (actions.isNotEmpty()) ResponsiveTripActions(actions)\n                    }\n                }\n            }\n'''
new = '''            val passengerRows = passengerCardRows(entry, trip, store)\n            if (passengerRows.isNotEmpty()) {\n                passengerRows.forEach { passenger ->\n                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {\n                        val label = buildString {\n                            append(passenger.name)\n                            passenger.phone?.let { append(" • ").append(displayPhone(it)) }\n                            if (passenger.seats > 1) append(" • ").append(passenger.seats).append(" lugares")\n                        }\n                        if (!passenger.phone.isNullOrBlank()) {\n                            TextButton(onClick = { openWhatsApp(context, passenger.phone!!) }) { Text(label) }\n                        } else {\n                            Text(label, style = MaterialTheme.typography.bodyMedium)\n                        }\n                        val source = passenger.sources.joinToString(" + ") { sourceShort(it) }\n                        val identity = when {\n                            passenger.matchedByPhone -> " • ✓ telefone"\n                            passenger.probableMatch -> " • ⚠️ conferir vínculo"\n                            passenger.phone.isNullOrBlank() -> " • telefone pendente"\n                            else -> ""\n                        }\n                        Text(source + identity, style = MaterialTheme.typography.bodySmall)\n                    }\n                }\n            }\n'''
if text.count(old) != 1:
    raise SystemExit(f"passenger visible-card block count={text.count(old)}")
text = text.replace(old, new, 1)

# detailsOpen no longer exists: the card itself is the concise passenger dashboard.
old_state = '''    var detailsOpen by remember(entry.tripId) { mutableStateOf(false) }\n'''
if text.count(old_state) != 1:
    raise SystemExit(f"obsolete passenger details state count={text.count(old_state)}")
text = text.replace(old_state, "", 1)

anchor = '''private fun statusMark(entry: TripTimelineEntry): String = when {\n'''
helpers = r'''private data class PassengerCardRow(
    val name: String,
    val phone: String?,
    val seats: Int,
    val boarding: String? = null,
    val dropoff: String? = null,
    val sources: Set<BookingSource>,
    val matchedByPhone: Boolean = false,
    val probableMatch: Boolean = false,
)

private fun passengerCardRows(entry: TripTimelineEntry, trip: Trip?, store: TripStore): List<PassengerCardRow> {
    val rows = entry.blablaPassengers.map { passenger ->
        PassengerCardRow(
            name = passenger.name.trim(),
            phone = passenger.phone?.trim()?.takeIf(String::isNotEmpty),
            seats = passenger.seats.coerceAtLeast(1),
            boarding = passenger.boarding,
            dropoff = passenger.dropoff,
            sources = setOf(BookingSource.BLABLACAR),
        )
    }.toMutableList()

    if (trip == null) return rows
    val stops = trip.stops.associateBy(TripStop::id)
    val local = store.bookingsFor(trip.id)
        .filter { it.capacityClaimType == CapacityClaimType.PASSENGER }
        .filter { it.status == BookingStatus.CONFIRMED || it.status == BookingStatus.HELD }
        .filter { it.seats > 0 }

    local.forEach { booking ->
        val phone = booking.passengerContact.trim().takeIf(String::isNotEmpty)
        val boarding = stops[booking.boardingStopId]?.name
        val dropoff = stops[booking.dropoffStopId]?.name
        val phoneKey = normalizePhone(phone)
        val candidateIndex = rows.indexOfFirst { current ->
            val currentPhone = normalizePhone(current.phone)
            phoneKey.isNotBlank() && currentPhone.isNotBlank() && phoneKey == currentPhone
        }
        val secondaryIndex = if (candidateIndex >= 0) -1 else rows.indexOfFirst { current ->
            normalizePassengerName(current.name) == normalizePassengerName(booking.passengerName) &&
                current.seats == booking.seats &&
                routeEvidenceMatches(current.boarding, current.dropoff, boarding, dropoff)
        }
        val index = if (candidateIndex >= 0) candidateIndex else secondaryIndex
        if (index >= 0) {
            val current = rows[index]
            rows[index] = current.copy(
                name = current.name.ifBlank { booking.passengerName.trim() },
                phone = current.phone?.takeIf(String::isNotBlank) ?: phone,
                seats = maxOf(current.seats, booking.seats),
                boarding = current.boarding ?: boarding,
                dropoff = current.dropoff ?: dropoff,
                sources = current.sources + booking.source,
                matchedByPhone = candidateIndex >= 0,
                probableMatch = candidateIndex < 0,
            )
        } else {
            rows += PassengerCardRow(
                name = booking.passengerName.trim(),
                phone = phone,
                seats = booking.seats,
                boarding = boarding,
                dropoff = dropoff,
                sources = setOf(booking.source),
            )
        }
    }
    return rows.filter { it.name.isNotBlank() }
}

private fun normalizePhone(raw: String?): String {
    val digits = raw.orEmpty().filter(Char::isDigit)
    return when (digits.length) {
        10, 11 -> "55$digits"
        in 12..13 -> digits
        else -> ""
    }
}

private fun displayPhone(raw: String): String {
    val digits = normalizePhone(raw)
    return when {
        digits.length == 13 && digits.startsWith("55") -> "+55 (${digits.substring(2, 4)}) ${digits.substring(4, 9)}-${digits.substring(9)}"
        digits.length == 12 && digits.startsWith("55") -> "+55 (${digits.substring(2, 4)}) ${digits.substring(4, 8)}-${digits.substring(8)}"
        else -> raw.trim()
    }
}

private fun normalizePassengerName(raw: String): String = java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun routeEvidenceMatches(aBoard: String?, aDrop: String?, bBoard: String?, bDrop: String?): Boolean {
    if (aBoard.isNullOrBlank() || aDrop.isNullOrBlank() || bBoard.isNullOrBlank() || bDrop.isNullOrBlank()) return false
    return placeIdentityKey(aBoard) == placeIdentityKey(bBoard) && placeIdentityKey(aDrop) == placeIdentityKey(bDrop)
}

private fun placeIdentityKey(raw: String): String = java.text.Normalizer.normalize(raw.substringBefore(',').trim(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun statusMark(entry: TripTimelineEntry): String = when {
'''
if text.count(anchor) != 1:
    raise SystemExit(f"passenger identity helper anchor count={text.count(anchor)}")
text = text.replace(anchor, helpers, 1)

for marker in (
    "passengerCardRows(entry, trip, store)",
    "✓ telefone",
    "⚠️ conferir vínculo",
    "telefone pendente",
    "normalizePhone",
    "openWhatsApp(context, passenger.phone!!)",
):
    if marker not in text:
        raise SystemExit(f"passenger card identity marker missing: {marker}")

UI.write_text(text, encoding="utf-8")
print("stage47_r4_step7_passenger_card_identity=PASS name_visible=true phone_visible=true tap_opens_whatsapp=true phone_primary_identity=true route_name_seats_secondary=true no_phone_invention=true farol_touched=false")
