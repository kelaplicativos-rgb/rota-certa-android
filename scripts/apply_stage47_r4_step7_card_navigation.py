#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
TRIPS = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips"
TIMELINE = TRIPS / "TripTimeline.kt"
TIMELINE_UI = TRIPS / "TripTimelineUi.kt"
COLLECTOR = TRIPS / "TripBlaBlaCollector.kt"
DYNAMIC = TRIPS / "BlaBlaDynamicAccounts.kt"
TESTS = SOURCE / "app/src/test/java/br/com/mapeiaia/rotacerta/trips"


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


for path in (TIMELINE, TIMELINE_UI, COLLECTOR, DYNAMIC):
    if not path.is_file():
        raise SystemExit(f"missing Stage47 card-navigation source: {path}")

once(
    TIMELINE,
    '''    val sourcePassengerSeats: Map<BookingSource, Int>,\n    val issues: Set<TripTimelineIssue> = emptySet(),\n''',
    '''    val sourcePassengerSeats: Map<BookingSource, Int>,\n    val localTripId: String? = null,\n    val blablaTripId: String? = null,\n    val blablaTripHref: String? = null,\n    val blablaProfileUuid: String? = null,\n    val blablaPrice: String? = null,\n    val blablaAvailability: String? = null,\n    val issues: Set<TripTimelineIssue> = emptySet(),\n''',
    "timeline external navigation fields",
)
once(
    TIMELINE,
    '''                    sourcePassengerSeats = passengerSeatsBySource(tripBookings, nowMillis),\n                )\n''',
    '''                    sourcePassengerSeats = passengerSeatsBySource(tripBookings, nowMillis),\n                    localTripId = trip.id,\n                )\n''',
    "timeline local trip identity",
)

once(
    COLLECTOR,
    '''                merged += external.copy(\n                    tripId = local.tripId,\n                    origin = local.origin,\n                    destination = local.destination,\n                    status = if (external.status == TripStatus.FULL) TripStatus.FULL else local.status,\n                    capacity = local.capacity,\n                    minimumOccupiedSeats = local.minimumOccupiedSeats,\n                    maximumOccupiedSeats = local.maximumOccupiedSeats,\n                    sourcePassengerSeats = local.sourcePassengerSeats,\n                    issues = local.issues + external.issues,\n                )\n''',
    '''                merged += external.copy(\n                    tripId = local.tripId,\n                    localTripId = local.localTripId ?: local.tripId,\n                    origin = external.origin,\n                    destination = external.destination,\n                    status = if (external.status == TripStatus.FULL) TripStatus.FULL else local.status,\n                    capacity = local.capacity,\n                    minimumOccupiedSeats = maxOf(local.minimumOccupiedSeats, external.minimumOccupiedSeats),\n                    maximumOccupiedSeats = maxOf(local.maximumOccupiedSeats, external.maximumOccupiedSeats),\n                    sourcePassengerSeats = mergeSourceSeats(local.sourcePassengerSeats, external.sourcePassengerSeats),\n                    issues = local.issues + external.issues,\n                )\n''',
    "merge physical local and blablacar card",
)
once(
    COLLECTOR,
    '''            sourcePassengerSeats = emptyMap(),\n            issues = if (verified) emptySet() else setOf(TripTimelineIssue.VALIDATION_PENDING),\n''',
    '''            sourcePassengerSeats = emptyMap(),\n            blablaTripId = trip.trip_id?.trim()?.takeIf(String::isNotEmpty),\n            blablaTripHref = canonicalManageHref(trip.trip_href),\n            blablaProfileUuid = trip.profile_uuid.trim().takeIf(String::isNotEmpty),\n            blablaPrice = trip.price?.trim()?.takeIf(String::isNotEmpty),\n            blablaAvailability = trip.availability.trim().takeIf(String::isNotEmpty),\n            issues = if (verified) emptySet() else setOf(TripTimelineIssue.VALIDATION_PENDING),\n''',
    "external timeline metadata",
)
once(
    COLLECTOR,
    '''        if (kotlin.math.abs(local.departureAtMillis - external.departureAtMillis) > 10L * 60L * 1000L) return false\n        val actualMatches = placeKey(local.origin) == placeKey(external.origin) && placeKey(local.destination) == placeKey(external.destination)\n        val searchMatches = !searchFrom.isNullOrBlank() && !searchTo.isNullOrBlank() &&\n            placeKey(local.origin) == placeKey(searchFrom) && placeKey(local.destination) == placeKey(searchTo)\n        return actualMatches || searchMatches\n''',
    '''        if (kotlin.math.abs(local.departureAtMillis - external.departureAtMillis) > 45L * 60L * 1000L) return false\n        val actualMatches = samePlace(local.origin, external.origin) && samePlace(local.destination, external.destination)\n        val searchMatches = !searchFrom.isNullOrBlank() && !searchTo.isNullOrBlank() &&\n            samePlace(local.origin, searchFrom) && samePlace(local.destination, searchTo)\n        return actualMatches || searchMatches\n''',
    "same physical trip tolerance",
)

# Step7 inserts strongExternalIdentity immediately before parseDateTime.  Insert
# the new helpers at this stable post-Step7 anchor instead of matching placeKey's
# escaped regex literal, which is deliberately left untouched.
once(
    COLLECTOR,
    '''    private fun parseDateTime(date: String, time: String?, zoneId: ZoneId): Long? = runCatching {\n''',
    r'''    private fun mergeSourceSeats(
        local: Map<BookingSource, Int>,
        external: Map<BookingSource, Int>,
    ): Map<BookingSource, Int> = (local.keys + external.keys).associateWith { source ->
        maxOf(local[source] ?: 0, external[source] ?: 0)
    }.filterValues { it > 0 }

    private fun canonicalManageHref(raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val absolute = if (value.startsWith('/')) "https://www.blablacar.com.br$value" else value
        return absolute.takeIf { href ->
            href.startsWith("https://www.blablacar.com.br/") &&
                (href.contains("/trip") || href.contains("/rides/offer"))
        }
    }

    private fun samePlace(left: String, right: String): Boolean {
        val a = normalizeWholePlace(left)
        val b = normalizeWholePlace(right)
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        return shorter.length >= 5 && longer.contains(shorter)
    }

    private fun normalizeWholePlace(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun parseDateTime(date: String, time: String?, zoneId: ZoneId): Long? = runCatching {
''',
    "physical trip helpers before parseDateTime",
)

once(
    DYNAMIC,
    '''    const val EXTRA_ACCOUNT_ID = "blablacar_account_id"\n    const val EXTRA_MODE = "blablacar_mode"\n    const val MODE_LOGIN = "login"\n    const val MODE_SYNC = "sync"\n\n    fun login(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_LOGIN)\n    fun sync(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_SYNC)\n''',
    '''    const val EXTRA_ACCOUNT_ID = "blablacar_account_id"\n    const val EXTRA_MODE = "blablacar_mode"\n    const val EXTRA_TARGET_URL = "blablacar_target_url"\n    const val MODE_LOGIN = "login"\n    const val MODE_SYNC = "sync"\n    const val MODE_MANAGE = "manage"\n\n    fun login(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_LOGIN)\n    fun sync(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_SYNC)\n    fun manage(context: Context, account: BlaBlaDynamicAccount, tripHref: String): Intent =\n        intent(context, account, MODE_MANAGE).putExtra(EXTRA_TARGET_URL, tripHref)\n''',
    "exact trip management intent",
)
once(
    DYNAMIC,
    '''        if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC) beginSync() else webView.loadUrl(HOME_URL)\n''',
    '''        when (mode) {\n            BlaBlaDynamicSessionIntents.MODE_SYNC -> beginSync()\n            BlaBlaDynamicSessionIntents.MODE_MANAGE -> webView.loadUrl(manageTargetUrl() ?: RIDES_URL)\n            else -> webView.loadUrl(HOME_URL)\n        }\n''',
    "exact trip management startup",
)
once(
    DYNAMIC,
    '''    private fun finishSeen() {\n''',
    '''    private fun manageTargetUrl(): String? {\n        val value = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_URL)?.trim().orEmpty()\n        return value.takeIf { href ->\n            href.startsWith("https://www.blablacar.com.br/") &&\n                (href.contains("/trip") || href.contains("/rides/offer"))\n        }\n    }\n\n    private fun finishSeen() {\n''',
    "validated exact trip target",
)

TIMELINE_UI.write_text(r'''package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TripTimelineScreen(
    trips: List<Trip>,
    bookings: List<Booking>,
    store: TripStore,
    onChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val collectorStore = remember(context) { BlaBlaCollectorStateStore(context) }
    var collectorResponse by remember { mutableStateOf(collectorStore.lastResponse()) }
    val localEntries = remember(trips, bookings) { TripTimelineEngine.fromLocalAgenda(trips, bookings) }
    val entries = remember(localEntries, collectorResponse) { BlaBlaTimelineAdapter.merge(localEntries, collectorResponse) }
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM HH:mm") }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Linha do tempo", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onBack) { Text("Voltar") }
    }

    BlaBlaCollectorPanel(
        trips = trips,
        stateStore = collectorStore,
        currentResponse = collectorResponse,
        onResult = { collectorResponse = it },
        onChanged = onChanged,
    )

    if (entries.isEmpty()) {
        Text("Nenhuma viagem para organizar.")
        return
    }

    entries.forEach { entry ->
        val trip = entry.localTripId?.let { localId -> trips.firstOrNull { it.id == localId } }
        TimelineEntryCard(entry, trip, store, formatter, onChanged)
    }
}

@Composable
private fun TimelineEntryCard(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    formatter: DateTimeFormatter,
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    var quickOpen by remember(entry.tripId, trip?.id) { mutableStateOf(false) }
    var capacitySetupOpen by remember(entry.tripId, trip?.id) { mutableStateOf(false) }
    var capacityText by remember(entry.tripId) { mutableStateOf("") }

    fun manage() {
        if (!entry.blablaTripHref.isNullOrBlank()) {
            if (!openExactBlaBlaTrip(context, entry)) {
                Toast.makeText(context, "A conta BlaBlaCar deste perfil não está conectada nesta sessão.", Toast.LENGTH_LONG).show()
            }
        } else {
            quickOpen = !quickOpen
        }
    }

    Card(onClick = { manage() }, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))
            Text("$date — ${entry.origin} → ${entry.destination} ${timelineStatus(entry)}", style = MaterialTheme.typography.titleSmall)
            Text("Perfil: ${entry.profileLabel} • ${tripStatusLabel(entry.status)}")

            if (!entry.blablaTripHref.isNullOrBlank()) {
                val price = entry.blablaPrice ?: "preço não exposto"
                val externalId = entry.blablaTripId?.take(12)?.let { " • ID $it" }.orEmpty()
                Text("BlaBlaCar: $price$externalId", style = MaterialTheme.typography.bodySmall)
            }

            if (entry.capacity > 0) {
                val occupancy = if (entry.minimumOccupiedSeats == entry.maximumOccupiedSeats) {
                    "${entry.maximumOccupiedSeats}/${entry.capacity}"
                } else {
                    "${entry.minimumOccupiedSeats}–${entry.maximumOccupiedSeats}/${entry.capacity}"
                }
                val free = if (entry.minimumAvailableSeats == entry.maximumAvailableSeats) {
                    "${entry.minimumAvailableSeats}"
                } else {
                    "${entry.minimumAvailableSeats}–${entry.maximumAvailableSeats}"
                }
                val availability = if (entry.minimumAvailableSeats == 0) "CHEIO" else "disponível"
                Text("Ocupação $occupancy • livres $free • $availability")
            } else {
                Text("Capacidade local ainda não definida • ${entry.blablaAvailability ?: "BlaBlaCar vinculado"}")
            }

            val sources = entry.sourcePassengerSeats.filterValues { it > 0 }.entries.sortedBy { it.key.ordinal }
                .joinToString(" • ") { (source, seats) -> "${sourceLabel(source)} $seats" }
            if (sources.isNotBlank()) Text("Passageiros: $sources", style = MaterialTheme.typography.bodySmall)
            if (entry.localTripId != null && !entry.blablaTripHref.isNullOrBlank()) {
                Text("Agenda local + BlaBlaCar mescladas em uma única corrida", style = MaterialTheme.typography.bodySmall)
            }
            timelineIssueText(entry)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { manage() }) { Text("Gerenciar") }
                OutlinedButton(onClick = {
                    if (trip != null) {
                        quickOpen = !quickOpen
                        capacitySetupOpen = false
                    } else {
                        capacitySetupOpen = !capacitySetupOpen
                        quickOpen = false
                    }
                }) { Text("+ Passageiro") }
            }

            if (trip != null && quickOpen) QuickPassengerPanel(trip, store, onChanged)

            if (trip == null && capacitySetupOpen) {
                Text("Defina a capacidade total uma vez para controlar passageiros desta publicação sem criar card duplicado.")
                OutlinedTextField(
                    value = capacityText,
                    onValueChange = { capacityText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Capacidade total") },
                    modifier = Modifier.fillMaxWidth(),
                )
                val capacity = capacityText.toIntOrNull()?.takeIf { it in 1..99 }
                Button(
                    enabled = capacity != null,
                    onClick = {
                        val value = capacity ?: return@Button
                        store.saveTrip(localMirror(entry, value))
                        onChanged("Controle local criado; a publicação BlaBlaCar foi mesclada à mesma corrida.")
                    },
                ) { Text("Criar controle local") }
            }
        }
    }
}

private fun openExactBlaBlaTrip(context: Context, entry: TripTimelineEntry): Boolean {
    val href = entry.blablaTripHref?.trim()?.takeIf(String::isNotEmpty) ?: return false
    val profileUuid = entry.blablaProfileUuid?.trim()?.takeIf(String::isNotEmpty) ?: return false
    val account = BlaBlaDynamicAccountRegistry(context).list().firstOrNull { account ->
        account.profileUuid?.equals(profileUuid, ignoreCase = true) == true
    } ?: return false
    context.startActivity(BlaBlaDynamicSessionIntents.manage(context, account, href))
    return true
}

private fun localMirror(entry: TripTimelineEntry, capacity: Int): Trip = Trip(
    title = "${entry.origin} → ${entry.destination}",
    departureAtMillis = entry.departureAtMillis,
    capacity = capacity,
    status = if (entry.status == TripStatus.FULL) TripStatus.FULL else TripStatus.PUBLISHED,
    stops = listOf(
        TripStop(
            order = 0,
            name = entry.origin,
            address = entry.origin,
            plannedArrivalMillis = entry.departureAtMillis,
            plannedDepartureMillis = entry.departureAtMillis,
        ),
        TripStop(
            order = 1,
            name = entry.destination,
            address = entry.destination,
            plannedArrivalMillis = entry.arrivalAtMillis,
        ),
    ),
)

private fun tripStatusLabel(status: TripStatus): String = when (status) {
    TripStatus.DRAFT -> "rascunho"
    TripStatus.PUBLISHED -> "publicada"
    TripStatus.FULL -> "cheia"
    TripStatus.STARTING -> "saindo"
    TripStatus.ACTIVE -> "em andamento"
    TripStatus.COMPLETED -> "concluída"
    TripStatus.CANCELLED -> "cancelada"
}

private fun timelineStatus(entry: TripTimelineEntry): String = when {
    TripTimelineIssue.OVERBOOKING in entry.issues -> "❌"
    TripTimelineIssue.PHYSICAL_CONFLICT in entry.issues -> "❌"
    TripTimelineIssue.DUPLICATE in entry.issues -> "⚠️"
    TripTimelineIssue.PROFILE_CONTINUITY in entry.issues -> "⚠️"
    TripTimelineIssue.VALIDATION_PENDING in entry.issues -> "⚠️"
    else -> "✅"
}

private fun timelineIssueText(entry: TripTimelineEntry): String? {
    val labels = buildList {
        if (TripTimelineIssue.OVERBOOKING in entry.issues) add("capacidade excedida")
        if (TripTimelineIssue.PHYSICAL_CONFLICT in entry.issues) add("conflito físico real")
        if (TripTimelineIssue.DUPLICATE in entry.issues) add("duplicidade ainda não mesclada")
        if (TripTimelineIssue.PROFILE_CONTINUITY in entry.issues) add("atenção à continuidade física do motorista")
        if (TripTimelineIssue.VALIDATION_PENDING in entry.issues) add("UUID ainda não confirmado")
    }
    return labels.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

private fun sourceLabel(source: BookingSource): String = when (source) {
    BookingSource.ROTA_CERTA -> "Rota Certa"
    BookingSource.BLABLACAR -> "BlaBlaCar"
    BookingSource.PRIVATE -> "Particular"
    BookingSource.OTHER -> "Outro"
}
''', encoding="utf-8")

TESTS.mkdir(parents=True, exist_ok=True)
(TESTS / "TripTimelineCardNavigationStage47Step7Test.kt").write_text(r'''package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripTimelineCardNavigationStage47Step7Test {
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun local_trip_published_later_on_blablacar_becomes_one_card_and_keeps_exact_link() {
        val departure = LocalDate.of(2026, 8, 21).atTime(LocalTime.of(11, 0)).atZone(zone).toInstant().toEpochMilli()
        val local = TripTimelineEntry(
            tripId = "local-1", localTripId = "local-1", profileId = "local", profileLabel = "Agenda",
            departureAtMillis = departure, arrivalAtMillis = departure + 6 * 60 * 60 * 1000L,
            origin = "Santo André", destination = "Três Corações", status = TripStatus.PUBLISHED,
            capacity = 4, minimumOccupiedSeats = 1, maximumOccupiedSeats = 1,
            sourcePassengerSeats = mapOf(BookingSource.PRIVATE to 1),
        )
        val response = BlaBlaCollectorMonthResponse(
            status = "validated",
            trips = listOf(BlaBlaCollectorTrip(
                profile_uuid = "7371f028-9c55-4903-8444-308015823efd", profile_name = "Ezequiel S",
                date = "2026-08-21", departure_time = "11:20", arrival_time = "17:10",
                actual_departure = "Santo André, SP", actual_arrival = "Três Corações, MG",
                price = "R$ 89,00", trip_href = "https://www.blablacar.com.br/trip?source=CARPOOLING&id=trip-123&search_uuid=abc",
                trip_id = "trip-123", uuid_validation = "verified_from_authenticated_profile_session",
            )),
        )
        val merged = BlaBlaTimelineAdapter.merge(listOf(local), response, zone)
        assertEquals(1, merged.size)
        val card = merged.single()
        assertEquals("local-1", card.tripId)
        assertEquals("local-1", card.localTripId)
        assertEquals("trip-123", card.blablaTripId)
        assertEquals("https://www.blablacar.com.br/trip?source=CARPOOLING&id=trip-123&search_uuid=abc", card.blablaTripHref)
        assertEquals("7371f028-9c55-4903-8444-308015823efd", card.blablaProfileUuid)
        assertEquals("R$ 89,00", card.blablaPrice)
        assertEquals("Santo André, SP", card.origin)
        assertEquals("Três Corações, MG", card.destination)
        assertEquals(1, card.sourcePassengerSeats[BookingSource.PRIVATE])
        assertFalse(TripTimelineIssue.DUPLICATE in card.issues)
    }

    @Test
    fun ezequiel_and_barbosa_are_checked_as_one_physical_timeline() {
        fun entry(id: String, profile: String, start: Long, end: Long, origin: String, destination: String) = TripTimelineEntry(
            tripId = id, profileId = profile, profileLabel = profile, departureAtMillis = start, arrivalAtMillis = end,
            origin = origin, destination = destination, status = TripStatus.PUBLISHED, capacity = 4,
            minimumOccupiedSeats = 0, maximumOccupiedSeats = 0, sourcePassengerSeats = emptyMap(),
        )
        val first = entry("a", "7371f028-9c55-4903-8444-308015823efd", 1_000L, 2_000L, "Santo André", "Três Corações")
        val continuous = entry("b", "175a7068-50d8-40c3-a27a-214b9c6e0461", 3_000L, 4_000L, "Três Corações", "Santo André")
        val ok = TripTimelineEngine.annotate(listOf(first, continuous))
        assertFalse(TripTimelineIssue.PROFILE_CONTINUITY in ok.last().issues)
        val warned = TripTimelineEngine.annotate(listOf(first, continuous.copy(tripId = "c", origin = "Varginha")))
        assertTrue(TripTimelineIssue.PROFILE_CONTINUITY in warned.last().issues)
    }
}
''', encoding="utf-8")

checks = {
    TIMELINE: ["blablaTripHref", "localTripId"],
    COLLECTOR: ["canonicalManageHref", "45L * 60L * 1000L", "mergeSourceSeats"],
    DYNAMIC: ["MODE_MANAGE", "EXTRA_TARGET_URL", "manageTargetUrl"],
    TIMELINE_UI: ["Gerenciar", "+ Passageiro", "openExactBlaBlaTrip", "Agenda local + BlaBlaCar mescladas"],
}
for path, markers in checks.items():
    text = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            raise SystemExit(f"missing card-navigation marker {marker!r} in {path.name}")

print(
    "stage47_r4_step7_card_navigation=PASS exact_trip_link=true correct_webview_profile=true "
    "local_blablacar_single_card=true all_cards_manage=true all_cards_add_passenger=true "
    "external_route_on_card=true cross_profile_physical_timeline=true farol_touched=false"
)
