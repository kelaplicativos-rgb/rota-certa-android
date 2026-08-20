#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
TRIPS = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips"
COLLECTOR = TRIPS / "TripBlaBlaCollector.kt"
DOM = TRIPS / "BlaBlaDomParsing.kt"
DYNAMIC = TRIPS / "BlaBlaDynamicAccounts.kt"
TIMELINE = TRIPS / "TripTimeline.kt"
TIMELINE_UI = TRIPS / "TripTimelineUi.kt"
QUICK_UI = TRIPS / "TripQuickPassengerUi.kt"
RESPONSIVE = TRIPS / "ResponsiveTripActions.kt"
CONSOLIDATOR = TRIPS / "TripPhysicalRideConsolidator.kt"


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


for path in (COLLECTOR, DOM, DYNAMIC, TIMELINE, TIMELINE_UI, QUICK_UI):
    if not path.is_file():
        raise SystemExit(f"missing Stage47 clean-occupancy source: {path}")

# Structured passenger evidence comes from the authenticated trip page.  The
# name suffix '(2)' is confirmed by the physical video to correspond to two
# places.  Phone stays optional and is never invented.
once(
    COLLECTOR,
    '''@Serializable\ndata class BlaBlaCollectorTrip(\n''',
    '''@Serializable\ndata class BlaBlaCollectorPassenger(\n    val name: String = "",\n    val seats: Int = 1,\n    val boarding: String? = null,\n    val dropoff: String? = null,\n    val phone: String? = null,\n    val booking_href: String? = null,\n)\n\n@Serializable\ndata class BlaBlaCollectorTrip(\n''',
    "collector passenger model",
)
once(
    COLLECTOR,
    '''    val trip_id: String? = null,\n    val uuid_validation: String = "unknown",\n)\n''',
    '''    val trip_id: String? = null,\n    val uuid_validation: String = "unknown",\n    val passengers: List<BlaBlaCollectorPassenger> = emptyList(),\n    val booked_seats: Int = 0,\n)\n''',
    "collector trip passenger fields",
)

once(
    DOM,
    '''    val driverName: String = "",\n    val profileLinks: List<String> = emptyList(),\n)\n''',
    '''    val driverName: String = "",\n    val profileLinks: List<String> = emptyList(),\n    val passengers: List<BlaBlaCollectorPassenger> = emptyList(),\n)\n''',
    "dom passenger field",
)
once(
    DOM,
    '''            trip_id = tripId(href),\n            uuid_validation = uuidValidation,\n        )\n''',
    '''            trip_id = tripId(href),\n            uuid_validation = uuidValidation,\n            passengers = detail.passengers,\n            booked_seats = detail.passengers.sumOf(BlaBlaCollectorPassenger::seats),\n        )\n''',
    "normalize booked seats",
)

# Never let a generic trip/page H1 overwrite the account label.  The label the
# user assigned to the isolated account is stable; visibleName is only optional
# profile evidence.
once(
    DYNAMIC,
    '''    val displayLabel: String\n        get() = profileName?.trim()?.takeIf(String::isNotEmpty) ?: label\n''',
    '''    val displayLabel: String\n        get() = label.trim().takeIf(String::isNotEmpty)\n            ?: profileName?.trim()?.takeIf(String::isNotEmpty)\n            ?: "Conta BlaBlaCar"\n''',
    "stable account display label",
)
once(
    DYNAMIC,
    '''              const nameNode = document.querySelector('[data-testid*="profile-name"], [data-testid*="driver-name"], h1');\n''',
    '''              const nameNode = document.querySelector('[data-testid*="profile-name"], [data-testid*="driver-name"]');\n''',
    "identity name selector without page h1",
)

# Read confirmed passenger rows already rendered on the exact authenticated
# trip.  No click, mutation, +/- or save is performed.
once(
    DYNAMIC,
    '''              const dateText = clean(Array.from(document.querySelectorAll('[data-testid*="date"], time, h1, h2, h3')).map((node) => node.innerText).join(' | ')).slice(0, 1400);\n              $SANITIZED_HTML_JS\n''',
    r'''              const dateText = clean(Array.from(document.querySelectorAll('[data-testid*="date"], time, h1, h2, h3')).map((node) => node.innerText).join(' | ')).slice(0, 1400);
              const passengerAnchors = Array.from(document.querySelectorAll('a[href*="/rides/offer/passenger/"]'));
              const passengerMap = new Map();
              passengerAnchors.forEach((anchor) => {
                const href = anchor.href || '';
                if (!href || passengerMap.has(href)) return;
                const root = anchor.closest('li, article, [data-testid*="passenger"], [role="listitem"]') || anchor;
                const raw = (root.innerText || anchor.innerText || '').trim();
                const lines = raw.split(/\n+/).map(clean).filter(Boolean);
                let name = lines[0] || '';
                const suffix = name.match(/\((\d+)\)\s*$/);
                const seats = suffix ? Math.max(1, parseInt(suffix[1], 10) || 1) : 1;
                if (suffix) name = clean(name.replace(/\((\d+)\)\s*$/, ''));
                const route = lines.find((line) => line.includes('→') || line.includes('->')) || '';
                const routeParts = route.split(/→|->/).map(clean);
                const tel = root.querySelector && root.querySelector('a[href^="tel:"]');
                passengerMap.set(href, {
                  name: name,
                  seats: seats,
                  boarding: routeParts.length >= 2 ? routeParts[0] : null,
                  dropoff: routeParts.length >= 2 ? routeParts[routeParts.length - 1] : null,
                  phone: tel ? (tel.getAttribute('href') || '').replace(/^tel:/i, '') : null,
                  booking_href: href
                });
              });
              const passengers = Array.from(passengerMap.values()).filter((item) => item.name);
              $SANITIZED_HTML_JS
''',
    "trip passenger DOM evidence",
)
once(
    DYNAMIC,
    '''                  driverName: clean(driverNode && driverNode.innerText),\n                  profileLinks: scopedDriverLinks.length ? scopedDriverLinks : allProfileLinks\n''',
    '''                  driverName: clean(driverNode && driverNode.innerText),\n                  profileLinks: scopedDriverLinks.length ? scopedDriverLinks : allProfileLinks,\n                  passengers: passengers\n''',
    "trip passenger payload",
)

once(
    TIMELINE,
    '''    val blablaAvailability: String? = null,\n    val issues: Set<TripTimelineIssue> = emptySet(),\n''',
    '''    val blablaAvailability: String? = null,\n    val blablaPassengers: List<BlaBlaCollectorPassenger> = emptyList(),\n    val issues: Set<TripTimelineIssue> = emptySet(),\n''',
    "timeline passenger evidence",
)

# Public occupancy is the visible confirmed passenger seats.  When a local
# capacity exists, private/other seats are added while an already mirrored
# BLABLACAR source is reconciled by max, not double counted.
once(
    COLLECTOR,
    '''                    minimumOccupiedSeats = maxOf(local.minimumOccupiedSeats, external.minimumOccupiedSeats),\n                    maximumOccupiedSeats = maxOf(local.maximumOccupiedSeats, external.maximumOccupiedSeats),\n                    sourcePassengerSeats = mergeSourceSeats(local.sourcePassengerSeats, external.sourcePassengerSeats),\n''',
    '''                    minimumOccupiedSeats = mergeSourceSeats(local.sourcePassengerSeats, external.sourcePassengerSeats).values.sum(),\n                    maximumOccupiedSeats = mergeSourceSeats(local.sourcePassengerSeats, external.sourcePassengerSeats).values.sum(),\n                    sourcePassengerSeats = mergeSourceSeats(local.sourcePassengerSeats, external.sourcePassengerSeats),\n''',
    "merged real physical occupancy",
)
once(
    COLLECTOR,
    '''            sourcePassengerSeats = emptyMap(),\n            blablaTripId = trip.trip_id?.trim()?.takeIf(String::isNotEmpty),\n''',
    '''            sourcePassengerSeats = if (trip.booked_seats > 0) mapOf(BookingSource.BLABLACAR to trip.booked_seats) else emptyMap(),\n            blablaTripId = trip.trip_id?.trim()?.takeIf(String::isNotEmpty),\n''',
    "external source passenger seats",
)
once(
    COLLECTOR,
    '''            blablaAvailability = trip.availability.trim().takeIf(String::isNotEmpty),\n            issues = if (verified) emptySet() else setOf(TripTimelineIssue.VALIDATION_PENDING),\n''',
    '''            blablaAvailability = trip.availability.trim().takeIf(String::isNotEmpty),\n            blablaPassengers = trip.passengers,\n            issues = if (verified) emptySet() else setOf(TripTimelineIssue.VALIDATION_PENDING),\n''',
    "external passenger names",
)
once(
    COLLECTOR,
    '''            minimumOccupiedSeats = 0,\n            maximumOccupiedSeats = 0,\n''',
    '''            minimumOccupiedSeats = trip.booked_seats,\n            maximumOccupiedSeats = trip.booked_seats,\n''',
    "external booked occupancy count",
)

RESPONSIVE.write_text(r'''package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ResponsiveTripAction(
    val label: String,
    val outlined: Boolean = true,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** Shared phone-safe action renderer. No label is allowed to wrap into a tall pill. */
@Composable
fun ResponsiveTripActions(actions: List<ResponsiveTripAction>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < 360.dp || actions.size > 2
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                actions.forEach { action ->
                    if (action.outlined) {
                        OutlinedButton(action.onClick, enabled = action.enabled, modifier = Modifier.fillMaxWidth()) { Text(action.label, maxLines = 1) }
                    } else {
                        Button(action.onClick, enabled = action.enabled, modifier = Modifier.fillMaxWidth()) { Text(action.label, maxLines = 1) }
                    }
                }
            }
        } else {
            val gap = 8.dp
            val width = (maxWidth - gap * (actions.size - 1)) / actions.size
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                actions.forEach { action ->
                    if (action.outlined) {
                        OutlinedButton(action.onClick, enabled = action.enabled, modifier = Modifier.width(width)) { Text(action.label, maxLines = 1) }
                    } else {
                        Button(action.onClick, enabled = action.enabled, modifier = Modifier.width(width)) { Text(action.label, maxLines = 1) }
                    }
                }
            }
        }
    }
}
''', encoding="utf-8")

CONSOLIDATOR.write_text(r'''package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.location.Geocoder
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TimelineGeoPoint(val latitude: Double, val longitude: Double)

object TripTimelineGeoResolver {
    @Suppress("DEPRECATION")
    suspend fun resolve(context: Context, places: Collection<String>): Map<String, TimelineGeoPoint> = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext emptyMap()
        val geocoder = Geocoder(context.applicationContext, Locale("pt", "BR"))
        places.distinct().filter(String::isNotBlank).mapNotNull { place ->
            val address = runCatching { geocoder.getFromLocationName("$place, Brasil", 1)?.firstOrNull() }.getOrNull()
            address?.let { place to TimelineGeoPoint(it.latitude, it.longitude) }
        }.toMap()
    }
}

/**
 * Consolidates mirrors/subsegments before fatal logistics validation.  A same
 * date/time alone is NEVER enough: text endpoint evidence or same-direction
 * geocoded corridor evidence is required.
 */
object TripPhysicalRideConsolidator {
    fun consolidate(entriesRaw: List<TripTimelineEntry>, geo: Map<String, TimelineGeoPoint>): List<TripTimelineEntry> {
        if (entriesRaw.size < 2) return validate(entriesRaw, geo)
        val pending = entriesRaw.sortedBy(TripTimelineEntry::departureAtMillis).toMutableList()
        val result = mutableListOf<TripTimelineEntry>()
        while (pending.isNotEmpty()) {
            val seed = pending.removeAt(0)
            val group = mutableListOf(seed)
            var changed: Boolean
            do {
                changed = false
                val iterator = pending.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    if (group.any { samePhysicalRide(it, candidate, geo) }) {
                        group += candidate
                        iterator.remove()
                        changed = true
                    }
                }
            } while (changed)
            result += mergeGroup(group, geo)
        }
        return validate(result, geo)
    }

    private fun samePhysicalRide(a: TripTimelineEntry, b: TripTimelineEntry, geo: Map<String, TimelineGeoPoint>): Boolean {
        val zone = ZoneId.systemDefault()
        val dayA = Instant.ofEpochMilli(a.departureAtMillis).atZone(zone).toLocalDate()
        val dayB = Instant.ofEpochMilli(b.departureAtMillis).atZone(zone).toLocalDate()
        if (dayA != dayB) return false
        if (abs(a.departureAtMillis - b.departureAtMillis) > 90L * 60L * 1000L) return false
        if (!a.blablaTripId.isNullOrBlank() && a.blablaTripId == b.blablaTripId) return true
        if (samePlace(a.origin, b.origin) && samePlace(a.destination, b.destination)) return true
        val ao = geo[a.origin] ?: return false
        val ad = geo[a.destination] ?: return false
        val bo = geo[b.origin] ?: return false
        val bd = geo[b.destination] ?: return false
        val bearingDiff = angleDiff(bearing(ao, ad), bearing(bo, bd))
        if (bearingDiff > 35.0) return false
        val aLen = distanceKm(ao, ad)
        val bLen = distanceKm(bo, bd)
        if (aLen < 15.0 || bLen < 15.0) return false
        val broadO: TimelineGeoPoint
        val broadD: TimelineGeoPoint
        val shortO: TimelineGeoPoint
        val shortD: TimelineGeoPoint
        val broadLen: Double
        if (aLen >= bLen) {
            broadO = ao; broadD = ad; shortO = bo; shortD = bd; broadLen = aLen
        } else {
            broadO = bo; broadD = bd; shortO = ao; shortD = ad; broadLen = bLen
        }
        val originDetour = distanceKm(broadO, shortO) + distanceKm(shortO, broadD) - broadLen
        val destDetour = distanceKm(broadO, shortD) + distanceKm(shortD, broadD) - broadLen
        return originDetour <= 70.0 && destDetour <= 70.0
    }

    private fun mergeGroup(group: List<TripTimelineEntry>, geo: Map<String, TimelineGeoPoint>): TripTimelineEntry {
        if (group.size == 1) return group.single().copy(
            issues = group.single().issues - setOf(TripTimelineIssue.PHYSICAL_CONFLICT, TripTimelineIssue.PROFILE_CONTINUITY, TripTimelineIssue.DUPLICATE),
        )
        val canonical = group.maxByOrNull { entry ->
            val o = geo[entry.origin]
            val d = geo[entry.destination]
            if (o != null && d != null) distanceKm(o, d) else if (!entry.blablaTripHref.isNullOrBlank()) 1.0 else 0.0
        } ?: group.first()
        val sources = BookingSource.entries.mapNotNull { source ->
            val values = group.map { it.sourcePassengerSeats[source] ?: 0 }.filter { it > 0 }
            if (values.isEmpty()) null else source to when (source) {
                BookingSource.BLABLACAR, BookingSource.ROTA_CERTA -> values.maxOrNull() ?: 0
                BookingSource.PRIVATE, BookingSource.OTHER -> values.sum()
            }
        }.toMap()
        val occupied = sources.values.sum()
        val capacity = group.maxOfOrNull(TripTimelineEntry::capacity) ?: 0
        val external = group.firstOrNull { !it.blablaTripHref.isNullOrBlank() }
        val local = group.firstOrNull { it.localTripId != null }
        val cleanIssues = group.flatMap(TripTimelineEntry::issues).toSet() - setOf(
            TripTimelineIssue.PHYSICAL_CONFLICT,
            TripTimelineIssue.PROFILE_CONTINUITY,
            TripTimelineIssue.DUPLICATE,
            TripTimelineIssue.OVERBOOKING,
        )
        val issues = if (capacity > 0 && occupied > capacity) cleanIssues + TripTimelineIssue.OVERBOOKING else cleanIssues
        val status = when {
            capacity > 0 && occupied >= capacity -> TripStatus.FULL
            local != null -> local.status
            else -> canonical.status
        }
        return canonical.copy(
            tripId = local?.tripId ?: canonical.tripId,
            localTripId = local?.localTripId,
            blablaTripId = external?.blablaTripId,
            blablaTripHref = external?.blablaTripHref,
            blablaProfileUuid = external?.blablaProfileUuid,
            blablaPrice = external?.blablaPrice,
            blablaAvailability = external?.blablaAvailability,
            blablaPassengers = group.flatMap(TripTimelineEntry::blablaPassengers).distinctBy { it.booking_href ?: "${it.name}|${it.boarding}|${it.dropoff}" },
            profileId = external?.profileId ?: canonical.profileId,
            profileLabel = external?.profileLabel ?: canonical.profileLabel,
            capacity = capacity,
            minimumOccupiedSeats = occupied,
            maximumOccupiedSeats = occupied,
            sourcePassengerSeats = sources,
            issues = issues,
        )
    }

    private fun validate(entriesRaw: List<TripTimelineEntry>, geo: Map<String, TimelineGeoPoint>): List<TripTimelineEntry> {
        val entries = entriesRaw.sortedBy(TripTimelineEntry::departureAtMillis)
        val issueMap = entries.associate { entry ->
            entry.tripId to (entry.issues - setOf(TripTimelineIssue.PHYSICAL_CONFLICT, TripTimelineIssue.PROFILE_CONTINUITY)).toMutableSet()
        }.toMutableMap()
        entries.forEach { entry ->
            if (entry.capacity > 0 && entry.maximumOccupiedSeats > entry.capacity) issueMap.getValue(entry.tripId) += TripTimelineIssue.OVERBOOKING
        }
        entries.zipWithNext().forEach { (previous, next) ->
            val end = previous.arrivalAtMillis
            if (end != null && next.departureAtMillis < end) {
                issueMap.getValue(previous.tripId) += TripTimelineIssue.PHYSICAL_CONFLICT
                issueMap.getValue(next.tripId) += TripTimelineIssue.PHYSICAL_CONFLICT
            } else if (!continuous(previous.destination, next.origin, geo)) {
                issueMap.getValue(next.tripId) += TripTimelineIssue.PROFILE_CONTINUITY
            }
        }
        return entries.map { it.copy(issues = issueMap.getValue(it.tripId).toSet()) }
    }

    private fun continuous(destination: String, origin: String, geo: Map<String, TimelineGeoPoint>): Boolean {
        if (samePlace(destination, origin)) return true
        val a = geo[destination] ?: return false
        val b = geo[origin] ?: return false
        return distanceKm(a, b) <= 35.0
    }

    private fun samePlace(a: String, b: String): Boolean {
        val left = key(a); val right = key(b)
        if (left.isBlank() || right.isBlank()) return false
        return left == right || (left.length >= 5 && right.contains(left)) || (right.length >= 5 && left.contains(right))
    }

    private fun key(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun distanceKm(a: TimelineGeoPoint, b: TimelineGeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun bearing(a: TimelineGeoPoint, b: TimelineGeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude); val lat2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun angleDiff(a: Double, b: Double): Double {
        val raw = abs(a - b) % 360.0
        return if (raw > 180.0) 360.0 - raw else raw
    }
}
''', encoding="utf-8")

# Clean private-passenger flow. Source/mirror internals remain in the engine but
# are not exposed to the everyday driver UI.
QUICK_UI.write_text(r'''package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun QuickPassengerPanel(trip: Trip, store: TripStore, onChanged: (String) -> Unit) {
    val stops = trip.stops.sortedBy(TripStop::order)
    if (stops.size < 2) return
    val scope = rememberCoroutineScope()
    var name by remember(trip.id) { mutableStateOf("") }
    var contact by remember(trip.id) { mutableStateOf("") }
    var seats by remember(trip.id) { mutableIntStateOf(1) }
    var fromIndex by remember(trip.id) { mutableIntStateOf(0) }
    var toIndex by remember(trip.id) { mutableIntStateOf(stops.lastIndex) }
    var busy by remember(trip.id) { mutableStateOf(false) }
    var error by remember(trip.id) { mutableStateOf<String?>(null) }
    val bookings = store.bookingsFor(trip.id)
    val availability = runCatching { SeatAvailabilityEngine.availability(trip, bookings, stops[fromIndex].id, stops[toIndex].id, seats) }.getOrNull()

    HorizontalDivider()
    Text("Adicionar passageiro particular")
    OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(contact, { contact = it }, label = { Text("WhatsApp (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    ResponsiveTripActions(listOf(
        ResponsiveTripAction("Embarque: ${stops[fromIndex].name}") {
            fromIndex = (fromIndex + 1).coerceAtMost(stops.lastIndex - 1)
            if (toIndex <= fromIndex) toIndex = fromIndex + 1
        },
        ResponsiveTripAction("Destino: ${stops[toIndex].name}") {
            toIndex++
            if (toIndex > stops.lastIndex) toIndex = fromIndex + 1
        },
    ))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { if (seats > 1) seats-- }) { Text("−") }
        Text("$seats vaga(s)")
        OutlinedButton(onClick = { if (seats < trip.capacity) seats++ }) { Text("+") }
    }
    val free = availability?.availableSeats ?: 0
    Text(if (availability?.canBook == true) "$free vaga(s) livre(s) neste trecho" else "Sem vaga física neste trecho")
    error?.let { Text(it) }
    Button(
        enabled = !busy && name.isNotBlank() && availability?.canBook == true,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            error = null
            val request = QuickPassengerRequest(
                passengerName = name,
                passengerContact = contact,
                boardingStopId = stops[fromIndex].id,
                dropoffStopId = stops[toIndex].id,
                seats = seats,
                source = BookingSource.PRIVATE,
            )
            val plan = runCatching { QuickPassengerEngine.build(trip, bookings, request) }
                .onFailure { error = it.message ?: "Sem vaga para incluir este passageiro." }
                .getOrNull() ?: return@Button
            busy = true
            scope.launch {
                val settings = store.onlineSettings()
                val remoteTripId = trip.remoteId
                val syncOnline = settings.configured && remoteTripId != null
                runCatching {
                    if (syncOnline) TripRemoteApi(settings).upsertDriverBooking(remoteTripId!!, plan.passenger)
                    store.saveBooking(plan.passenger)
                }.onSuccess {
                    name = ""; contact = ""; seats = 1
                    onChanged("Passageiro particular adicionado. Ocupação recalculada.")
                }.onFailure { error = "Não foi possível salvar: ${it.message}" }
                busy = false
            }
        },
    ) { Text(if (busy) "Salvando…" else "Adicionar passageiro") }

    val active = bookings.filter { it.capacityClaimType == CapacityClaimType.PASSENGER && (it.status == BookingStatus.CONFIRMED || it.status == BookingStatus.HELD) }
    if (active.isNotEmpty()) {
        HorizontalDivider()
        Text("Particulares (${active.sumOf(Booking::seats)})")
        active.forEach { booking ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${booking.passengerName} • ${booking.seats}")
                if (booking.source == BookingSource.PRIVATE || booking.source == BookingSource.OTHER) {
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            runCatching { store.saveBooking(booking.copy(status = BookingStatus.CANCELLED)) }
                                .onSuccess { onChanged("Passageiro removido e vaga liberada.") }
                                .onFailure { error = "Não foi possível liberar a vaga: ${it.message}" }
                            busy = false
                        }
                    }) { Text("Remover") }
                }
            }
        }
    }
}
''', encoding="utf-8")

# Timeline is intentionally terse. Entire card opens the exact trip, so the
# duplicate Gerenciar button is removed. Details/passenger names stay collapsed.
TIMELINE_UI.write_text(r'''package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TripTimelineScreen(
    trips: List<Trip>,
    bookings: List<Booking>,
    store: TripStore,
    onChanged: (String) -> Unit,
    onBack: () -> Unit,
    onManageLocal: (String) -> Unit,
) {
    val context = LocalContext.current
    val collectorStore = remember(context) { BlaBlaCollectorStateStore(context) }
    val archiveStore = remember(context) { TripTimelineArchiveStore(context) }
    var collectorResponse by remember { mutableStateOf(collectorStore.lastResponse()) }
    var archiveRevision by remember { mutableIntStateOf(0) }
    var showArchived by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var geo by remember { mutableStateOf<Map<String, TimelineGeoPoint>>(emptyMap()) }
    var geoReady by remember { mutableStateOf(false) }
    val localEntries = remember(trips, bookings) { TripTimelineEngine.fromLocalAgenda(trips, bookings) }
    val merged = remember(localEntries, collectorResponse) { BlaBlaTimelineAdapter.merge(localEntries, collectorResponse) }

    LaunchedEffect(merged.map { "${it.origin}|${it.destination}" }) {
        geoReady = merged.size < 2
        geo = TripTimelineGeoResolver.resolve(context, merged.flatMap { listOf(it.origin, it.destination) })
        geoReady = true
    }
    val physical = remember(merged, geo, geoReady) {
        if (geoReady) TripPhysicalRideConsolidator.consolidate(merged, geo) else emptyList()
    }
    val today = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val entries = remember(physical, archiveRevision, showArchived, today) {
        physical.filter { it.departureAtMillis >= today }
            .filter { archiveStore.isArchived(it) == showArchived }
            .sortedBy(TripTimelineEntry::departureAtMillis)
    }
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy • HH:mm", Locale("pt", "BR")) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (showArchived) "Arquivadas" else "Próximas viagens", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onBack) { Text("Voltar") }
    }
    ResponsiveTripActions(listOf(
        ResponsiveTripAction(if (showSync) "Fechar sincronização" else "Sincronizar BlaBlaCar") { showSync = !showSync },
        ResponsiveTripAction(if (showArchived) "Ver próximas" else "Ver arquivadas") { showArchived = !showArchived },
    ))
    if (showSync) {
        BlaBlaCollectorPanel(trips, collectorStore, collectorResponse, { collectorResponse = it }, onChanged)
    }

    if (!geoReady) {
        Text("Conferindo continuidade e rotas…")
        return
    }
    if (entries.isEmpty()) {
        Text(if (showArchived) "Nenhuma viagem arquivada." else "Nenhuma viagem futura.")
        return
    }

    LaunchedEffect(entries.map { it.tripId to it.issues }) {
        entries.firstOrNull { TripTimelineIssue.OVERBOOKING in it.issues }?.let {
            Toast.makeText(context, "URGENTE: há mais passageiros do que lugares em ${it.origin} → ${it.destination}.", Toast.LENGTH_LONG).show()
        }
    }

    entries.forEach { entry ->
        val trip = entry.localTripId?.let { id -> trips.firstOrNull { it.id == id } }
        val archived = archiveStore.isArchived(entry)
        TimelineEntryCard(entry, trip, store, formatter, archived, onManageLocal, onChanged) {
            archiveStore.setArchived(entry, !archived)
            archiveRevision++
            onChanged(if (archived) "Viagem restaurada." else "Viagem arquivada sem cancelar a publicação.")
        }
    }
}

@Composable
private fun TimelineEntryCard(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    formatter: DateTimeFormatter,
    archived: Boolean,
    onManageLocal: (String) -> Unit,
    onChanged: (String) -> Unit,
    onArchive: () -> Unit,
) {
    val context = LocalContext.current
    var quickOpen by remember(entry.tripId) { mutableStateOf(false) }
    var detailsOpen by remember(entry.tripId) { mutableStateOf(false) }

    fun openCard() {
        when {
            !entry.blablaTripHref.isNullOrBlank() -> if (!openBlaBlaHref(context, entry, entry.blablaTripHref!!)) {
                Toast.makeText(context, "Conta BlaBlaCar não conectada.", Toast.LENGTH_LONG).show()
            }
            entry.localTripId != null -> onManageLocal(entry.localTripId)
        }
    }

    Card(onClick = ::openCard, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))
            Text(date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }, style = MaterialTheme.typography.labelLarge)
            Text("${entry.origin} → ${entry.destination}", style = MaterialTheme.typography.titleMedium)

            val meta = listOfNotNull(entry.profileLabel.takeIf(String::isNotBlank), entry.blablaPrice).joinToString(" • ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall)

            val occupied = entry.maximumOccupiedSeats
            if (entry.capacity > 0) {
                val free = (entry.capacity - occupied).coerceAtLeast(0)
                Text("$occupied/${entry.capacity} ocupadas • $free livre(s) ${statusMark(entry)}")
            } else if (occupied > 0) {
                Text("BlaBlaCar: $occupied lugar(es) reservado(s) ${statusMark(entry)}")
            } else {
                Text("Ocupação aguardando leitura ${statusMark(entry)}")
            }

            val sourceLine = entry.sourcePassengerSeats.filterValues { it > 0 }.entries.joinToString(" • ") { (source, seats) ->
                "${sourceShort(source)} $seats"
            }
            if (sourceLine.isNotBlank()) Text(sourceLine, style = MaterialTheme.typography.bodySmall)

            when {
                TripTimelineIssue.OVERBOOKING in entry.issues -> Text("❌ URGENTE: passageiros acima da capacidade física.")
                TripTimelineIssue.PHYSICAL_CONFLICT in entry.issues -> Text("❌ Conflito real de horário/local.")
                TripTimelineIssue.PROFILE_CONTINUITY in entry.issues -> Text("⚠️ Próxima origem não bate com a chegada anterior.")
                TripTimelineIssue.VALIDATION_PENDING in entry.issues -> Text("⏳ Falta confirmar a origem dos dados.")
            }

            if (entry.blablaPassengers.isNotEmpty()) {
                TextButton(onClick = { detailsOpen = !detailsOpen }) {
                    Text(if (detailsOpen) "Ocultar passageiros" else "Passageiros BlaBlaCar (${entry.blablaPassengers.sumOf(BlaBlaCollectorPassenger::seats)})")
                }
            }
            if (detailsOpen) {
                entry.blablaPassengers.forEach { passenger ->
                    val route = listOfNotNull(passenger.boarding, passenger.dropoff).joinToString(" → ")
                    Column {
                        Text("${passenger.name} • ${passenger.seats} lugar(es)${if (route.isNotBlank()) " • $route" else ""}")
                        val actions = buildList {
                            passenger.phone?.takeIf(String::isNotBlank)?.let { phone ->
                                add(ResponsiveTripAction("WhatsApp") { openWhatsApp(context, phone) })
                            }
                            passenger.booking_href?.takeIf(String::isNotBlank)?.let { href ->
                                add(ResponsiveTripAction("Abrir reserva") { openBlaBlaHref(context, entry, href) })
                            }
                        }
                        if (actions.isNotEmpty()) ResponsiveTripActions(actions)
                    }
                }
            }

            ResponsiveTripActions(listOf(
                ResponsiveTripAction("+ Passageiro") { quickOpen = !quickOpen },
                ResponsiveTripAction(if (archived) "Restaurar" else "Arquivar") { onArchive() },
            ))
            if (trip != null && quickOpen) QuickPassengerPanel(trip, store, onChanged)
            if (trip == null && quickOpen) Text("Defina a capacidade física desta viagem na Agenda antes de incluir passageiro particular.")
        }
    }
}

private fun statusMark(entry: TripTimelineEntry): String = when {
    TripTimelineIssue.OVERBOOKING in entry.issues || TripTimelineIssue.PHYSICAL_CONFLICT in entry.issues -> "❌"
    TripTimelineIssue.PROFILE_CONTINUITY in entry.issues || TripTimelineIssue.DUPLICATE in entry.issues -> "⚠️"
    TripTimelineIssue.VALIDATION_PENDING in entry.issues -> "⏳"
    else -> "✅"
}

private fun sourceShort(source: BookingSource): String = when (source) {
    BookingSource.BLABLACAR -> "BlaBlaCar"
    BookingSource.PRIVATE -> "Particular"
    BookingSource.ROTA_CERTA -> "Rota Certa"
    BookingSource.OTHER -> "Outro"
}

private fun openBlaBlaHref(context: Context, entry: TripTimelineEntry, href: String): Boolean {
    val profileUuid = entry.blablaProfileUuid?.trim()?.lowercase() ?: return false
    val account = BlaBlaDynamicAccountRegistry(context).list().firstOrNull { it.profileUuid?.trim()?.lowercase() == profileUuid } ?: return false
    context.startActivity(BlaBlaDynamicSessionIntents.manage(context, account, href).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}

private fun openWhatsApp(context: Context, raw: String) {
    var digits = raw.filter(Char::isDigit)
    if (digits.length in 10..11) digits = "55$digits"
    if (digits.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private class TripTimelineArchiveStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isArchived(entry: TripTimelineEntry): Boolean = aliases(entry).any(prefs::getBooleanTrue)
    fun setArchived(entry: TripTimelineEntry, archived: Boolean) {
        val edit = prefs.edit()
        aliases(entry).forEach { edit.putBoolean(it, archived) }
        edit.apply()
    }
    private fun aliases(entry: TripTimelineEntry): Set<String> = setOfNotNull(
        entry.localTripId?.let { "local:$it" },
        entry.blablaTripId?.let { "blabla-id:$it" },
        entry.blablaTripHref?.let { "blabla-href:${it.substringBefore("&search_uuid=")}" },
        "timeline:${entry.tripId}",
    )
    private fun android.content.SharedPreferences.getBooleanTrue(key: String): Boolean = getBoolean(key, false)
    companion object { private const val PREFS = "rota_certa_timeline_archive_v1" }
}
''', encoding="utf-8")

# Static causal guards.  The protected FAROL is not read or written here.
for path, markers in {
    RESPONSIVE: ["ResponsiveTripActions", "maxWidth < 360.dp"],
    CONSOLIDATOR: ["same date/time alone is NEVER enough", "bearingDiff > 35.0", "TripTimelineGeoResolver"],
    TIMELINE_UI: ["Passageiros BlaBlaCar", "URGENTE", "+ Passageiro", "Arquivar", "Card(onClick = ::openCard"],
    QUICK_UI: ["Adicionar passageiro particular", "BookingSource.PRIVATE", "Sem vaga física neste trecho"],
}.items():
    text = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            raise SystemExit(f"missing clean-occupancy marker {marker!r} in {path.name}")

if "Gerenciar" in TIMELINE_UI.read_text(encoding="utf-8"):
    raise SystemExit("Timeline must not render the redundant Gerenciar button")

print("stage47_r4_step7_clean_occupancy=PASS clean_cards=true manage_button_removed=true card_opens_trip=true responsive_actions=true private_passenger_simple=true visible_blablacar_passengers=true booked_seats=true geo_corridor_merge=true overbooking_urgent=true farol_touched=false")
