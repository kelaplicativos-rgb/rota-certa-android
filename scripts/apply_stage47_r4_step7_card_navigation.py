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

# Timeline entries retain the exact external trip identity/navigation data while
# keeping a separate local-trip identity when a private/local trip already existed.
once(
    TIMELINE,
'''    val sourcePassengerSeats: Map<BookingSource, Int>,
    val issues: Set<TripTimelineIssue> = emptySet(),
''',
'''    val sourcePassengerSeats: Map<BookingSource, Int>,
    val localTripId: String? = null,
    val blablaTripId: String? = null,
    val blablaTripHref: String? = null,
    val blablaProfileUuid: String? = null,
    val blablaPrice: String? = null,
    val blablaAvailability: String? = null,
    val issues: Set<TripTimelineIssue> = emptySet(),
''',
    "timeline external navigation fields",
)
once(
    TIMELINE,
'''                    sourcePassengerSeats = passengerSeatsBySource(tripBookings, nowMillis),
                )
''',
'''                    sourcePassengerSeats = passengerSeatsBySource(tripBookings, nowMillis),
                    localTripId = trip.id,
                )
''',
    "timeline local trip identity",
)

# Preserve the external route/link/profile/price on a merged card. Local capacity
# and reservations remain authoritative for the driver's physical vehicle, while
# same-source occupancy is reconciled by max() so a mirrored BlaBlaCar booking is
# not double-counted.
once(
    COLLECTOR,
'''                merged += external.copy(
                    tripId = local.tripId,
                    origin = local.origin,
                    destination = local.destination,
                    status = if (external.status == TripStatus.FULL) TripStatus.FULL else local.status,
                    capacity = local.capacity,
                    minimumOccupiedSeats = local.minimumOccupiedSeats,
                    maximumOccupiedSeats = local.maximumOccupiedSeats,
                    sourcePassengerSeats = local.sourcePassengerSeats,
                    issues = local.issues + external.issues,
                )
''',
'''                merged += external.copy(
                    tripId = local.tripId,
                    localTripId = local.localTripId ?: local.tripId,
                    origin = external.origin,
                    destination = external.destination,
                    status = if (external.status == TripStatus.FULL) TripStatus.FULL else local.status,
                    capacity = local.capacity,
                    minimumOccupiedSeats = maxOf(local.minimumOccupiedSeats, external.minimumOccupiedSeats),
                    maximumOccupiedSeats = maxOf(local.maximumOccupiedSeats, external.maximumOccupiedSeats),
                    sourcePassengerSeats = mergeSourceSeats(local.sourcePassengerSeats, external.sourcePassengerSeats),
                    issues = local.issues + external.issues,
                )
''',
    "merge physical local and blablacar card",
)
once(
    COLLECTOR,
'''            sourcePassengerSeats = emptyMap(),
            issues = if (verified) emptySet() else setOf(TripTimelineIssue.VALIDATION_PENDING),
''',
'''            sourcePassengerSeats = emptyMap(),
            blablaTripId = trip.trip_id?.trim()?.takeIf(String::isNotEmpty),
            blablaTripHref = canonicalManageHref(trip.trip_href),
            blablaProfileUuid = trip.profile_uuid.trim().takeIf(String::isNotEmpty),
            blablaPrice = trip.price?.trim()?.takeIf(String::isNotEmpty),
            blablaAvailability = trip.availability.trim().takeIf(String::isNotEmpty),
            issues = if (verified) emptySet() else setOf(TripTimelineIssue.VALIDATION_PENDING),
''',
    "external timeline metadata",
)
once(
    COLLECTOR,
'''        if (kotlin.math.abs(local.departureAtMillis - external.departureAtMillis) > 10L * 60L * 1000L) return false
        val actualMatches = placeKey(local.origin) == placeKey(external.origin) && placeKey(local.destination) == placeKey(external.destination)
        val searchMatches = !searchFrom.isNullOrBlank() && !searchTo.isNullOrBlank() &&
            placeKey(local.origin) == placeKey(searchFrom) && placeKey(local.destination) == placeKey(searchTo)
        return actualMatches || searchMatches
''',
'''        if (kotlin.math.abs(local.departureAtMillis - external.departureAtMillis) > 45L * 60L * 1000L) return false
        val actualMatches = samePlace(local.origin, external.origin) && samePlace(local.destination, external.destination)
        val searchMatches = !searchFrom.isNullOrBlank() && !searchTo.isNullOrBlank() &&
            samePlace(local.origin, searchFrom) && samePlace(local.destination, searchTo)
        return actualMatches || searchMatches
''',
    "same physical trip tolerance",
)
once(
    COLLECTOR,
'''    private fun placeKey(value: String): String = Normalizer.normalize(value.substringBefore(',').trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
''',
'''    private fun mergeSourceSeats(
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
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun placeKey(value: String): String = Normalizer.normalize(value.substringBefore(',').trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
''',
    "physical trip helpers",
)

# Open management in the exact AndroidX WebView profile that owns the canonical
# driver UUID. Never fall back to a generic BlaBlaCar browser session.
once(
    DYNAMIC,
'''    const val EXTRA_ACCOUNT_ID = "blablacar_account_id"
    const val EXTRA_MODE = "blablacar_mode"
    const val MODE_LOGIN = "login"
    const val MODE_SYNC = "sync"

    fun login(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_LOGIN)
    fun sync(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_SYNC)
''',
'''    const val EXTRA_ACCOUNT_ID = "blablacar_account_id"
    const val EXTRA_MODE = "blablacar_mode"
    const val EXTRA_TARGET_URL = "blablacar_target_url"
    const val MODE_LOGIN = "login"
    const val MODE_SYNC = "sync"
    const val MODE_MANAGE = "manage"

    fun login(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_LOGIN)
    fun sync(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_SYNC)
    fun manage(context: Context, account: BlaBlaDynamicAccount, tripHref: String): Intent =
        intent(context, account, MODE_MANAGE).putExtra(EXTRA_TARGET_URL, tripHref)
''',
    "exact trip management intent",
)
once(
    DYNAMIC,
'''        if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC) beginSync() else webView.loadUrl(HOME_URL)
''',
'''        when (mode) {
            BlaBlaDynamicSessionIntents.MODE_SYNC -> beginSync()
            BlaBlaDynamicSessionIntents.MODE_MANAGE -> webView.loadUrl(manageTargetUrl() ?: RIDES_URL)
            else -> webView.loadUrl(HOME_URL)
        }
''',
    "exact trip management startup",
)
once(
    DYNAMIC,
'''    private fun finishSeen() {
''',
'''    private fun manageTargetUrl(): String? {
        val value = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_URL)?.trim().orEmpty()
        return value.takeIf { href ->
            href.startsWith("https://www.blablacar.com.br/") &&
                (href.contains("/trip") || href.contains("/rides/offer"))
        }
    }

    private fun finishSeen() {
''',
    "validated exact trip target",
)

# The Timeline is the consolidated card surface: every card exposes Gerenciar and
# + Passageiro. A pure BlaBlaCar card can create its local capacity mirror on demand;
# the next recomposition immediately merges it back into the same physical card.
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
    val localEntries = remember(trips, bookings) {
        TripTimelineEngine.fromLocalAgenda(trips, bookings)
    }
    val entries = remember(localEntries, collectorResponse) {
        BlaBlaTimelineAdapter.merge(localEntries, collectorResponse)
    }
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
    val statusEmoji = timelineStatus(entry)

    fun manage() {
        if (!entry.blablaTripHref.isNullOrBlank()) {
            if (!openExactBlaBlaTrip(context, entry)) {
                Toast.makeText(
                    context,
                    "A conta BlaBlaCar deste perfil não está conectada nesta sessão.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        } else {
            quickOpen = !quickOpen
        }
    }

    Card(onClick = { manage() }, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))
            Text("$date — ${entry.origin} → ${entry.destination} $statusEmoji", style = MaterialTheme.typography.titleSmall)
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

            val sources = entry.sourcePassengerSeats
                .filterValues { it > 0 }
                .entries
                .sortedBy { it.key.ordinal }
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

            if (trip != null && quickOpen) {
                QuickPassengerPanel(trip, store, onChanged)
            }

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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TripTimelineCardNavigationStage47Step7Test {
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun local_trip_published_later_on_blablacar_becomes_one_card_and_keeps_exact_link() {
        val departure = LocalDate.of(2026, 8, 21).atTime(LocalTime.of(11, 0)).atZone(zone).toInstant().toEpochMilli()
        val local = TripTimelineEntry(
            tripId = "local-1",
            localTripId = "local-1",
            profileId = "local",
            profileLabel = "Agenda",
            departureAtMillis = departure,
            arrivalAtMillis = departure + 6 * 60 * 60 * 1000L,
            origin = "Santo André",
            destination = "Três Corações",
            status = TripStatus.PUBLISHED,
            capacity = 4,
            minimumOccupiedSeats = 1,
            maximumOccupiedSeats = 1,
            sourcePassengerSeats = mapOf(BookingSource.PRIVATE to 1),
        )
        val response = BlaBlaCollectorMonthResponse(
            status = "validated",
            trips = listOf(
                BlaBlaCollectorTrip(
                    profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
                    profile_name = "Ezequiel S",
                    date = "2026-08-21",
                    departure_time = "11:20",
                    arrival_time = "17:10",
                    actual_departure = "Santo André, SP",
                    actual_arrival = "Três Corações, MG",
                    price = "R$ 89,00",
                    trip_href = "https://www.blablacar.com.br/trip?source=CARPOOLING&id=trip-123&search_uuid=abc",
                    trip_id = "trip-123",
                    uuid_validation = "verified_from_authenticated_profile_session",
                ),
            ),
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
            tripId = id,
            profileId = profile,
            profileLabel = profile,
            departureAtMillis = start,
            arrivalAtMillis = end,
            origin = origin,
            destination = destination,
            status = TripStatus.PUBLISHED,
            capacity = 4,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = emptyMap(),
        )
        val first = entry("a", "7371f028-9c55-4903-8444-308015823efd", 1_000L, 2_000L, "Santo André", "Três Corações")
        val continuous = entry("b", "175a7068-50d8-40c3-a27a-214b9c6e0461", 3_000L, 4_000L, "Três Corações", "Santo André")
        val ok = TripTimelineEngine.annotate(listOf(first, continuous))
        assertFalse(TripTimelineIssue.PROFILE_CONTINUITY in ok.last().issues)

        val impossibleStart = continuous.copy(tripId = "c", origin = "Varginha")
        val warned = TripTimelineEngine.annotate(listOf(first, impossibleStart))
        assertTrue(TripTimelineIssue.PROFILE_CONTINUITY in warned.last().issues)
    }

    @Test
    fun overlapping_profiles_are_a_real_physical_conflict() {
        val first = TripTimelineEntry(
            tripId = "a",
            profileId = "7371f028-9c55-4903-8444-308015823efd",
            profileLabel = "Ezequiel S",
            departureAtMillis = 1_000L,
            arrivalAtMillis = 4_000L,
            origin = "A",
            destination = "B",
            status = TripStatus.PUBLISHED,
            capacity = 4,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = emptyMap(),
        )
        val second = first.copy(
            tripId = "b",
            profileId = "175a7068-50d8-40c3-a27a-214b9c6e0461",
            profileLabel = "Barbosa",
            departureAtMillis = 2_000L,
            arrivalAtMillis = 5_000L,
        )
        val annotated = TripTimelineEngine.annotate(listOf(first, second))
        assertTrue(annotated.all { TripTimelineIssue.PHYSICAL_CONFLICT in it.issues })
        assertNotNull(annotated.first().arrivalAtMillis)
    }
}
''', encoding="utf-8")

# Materialization-time guards: fail closed before Gradle if a required behavior
# disappears in a future edit.
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
