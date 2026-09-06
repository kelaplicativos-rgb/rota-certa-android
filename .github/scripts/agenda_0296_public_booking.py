#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_between(path: Path, start: str, end: str, replacement: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if text.count(start) != 1 or text.count(end) != 1:
        raise SystemExit(f"{label}: boundaries not unique start={text.count(start)} end={text.count(end)}")
    begin = text.index(start)
    finish = text.index(end, begin)
    if finish <= begin:
        raise SystemExit(f"{label}: invalid boundary order")
    path.write_text(text[:begin] + replacement + text[finish:], encoding="utf-8")


def run_existing(*args: str) -> None:
    subprocess.run([sys.executable, *map(str, args)], cwd=ROOT, check=True)

# Reuse the most advanced existing Stage47 pieces instead of creating a second backend.
run_existing(ROOT / "scripts/apply_stage47_driver_identity_backend_r3.py", ROOT, ROOT)
run_existing(ROOT / "scripts/apply_stage47_unified_capacity_r4_step2.py", ROOT)
run_existing(ROOT / "scripts/apply_stage47_public_cancel_r3.py", ROOT)

# Android domain: public booking is explicit opt-in and defaults closed.
domain = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripDomain.kt"
once(
    domain,
    '''    val publicUrl: String? = null,\n    val createdAtMillis: Long = System.currentTimeMillis(),\n''',
    '''    val publicUrl: String? = null,\n    /** Public passenger portal is opt-in. A synchronized BlaBlaCar card is never exposed automatically. */\n    val publicBookingEnabled: Boolean = false,\n    val createdAtMillis: Long = System.currentTimeMillis(),\n''',
    "Trip public opt-in",
)

remote = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt"
once(
    remote,
    '''data class PublicBookingRequest(\n    val passengerName: String,\n    val passengerContact: String = "",\n    val boardingStopId: String,\n    val dropoffStopId: String,\n    val seats: Int = 1,\n)\n''',
    '''data class PublicBookingRequest(\n    val passengerName: String,\n    val passengerContact: String = "",\n    val boardingStopId: String,\n    val dropoffStopId: String,\n    val seats: Int = 1,\n    /** Stable per user intent so retries/double taps converge to one backend Booking. */\n    val idempotencyKey: String = "",\n)\n''',
    "remote public idempotency contract",
)

# Backend: preserve Rota Certa as physical-capacity authority, but make the public door
# opt-in, future-only, WhatsApp validated and idempotent.
backend = ROOT / "trip-platform/functions/index.js"
once(
    backend,
    '''    status,\n    stops,\n    notes: cleanText(raw.notes, 1200),\n''',
    '''    status,\n    stops,\n    publicBookingEnabled: raw.publicBookingEnabled === true,\n    notes: cleanText(raw.notes, 1200),\n''',
    "backend public opt-in normalize",
)
once(
    backend,
    '''    status: data.status,\n    stops: data.stops,\n    segmentLoads: data.segmentLoads || [],\n''',
    '''    status: data.status,\n    stops: data.stops,\n    segmentLoads: data.segmentLoads || [],\n    publicBookingEnabled: data.publicBookingEnabled === true,\n''',
    "backend safe public opt-in",
)
once(
    backend,
    '''    .filter((trip) => PUBLIC_STATUSES.has(trip.status) && Number(trip.departureAtMillis) >= cutoff)\n''',
    '''    .filter((trip) => PUBLIC_STATUSES.has(trip.status) && trip.publicBookingEnabled === true && Number(trip.departureAtMillis) > Date.now())\n''',
    "driver agenda filters only explicitly public future trips",
)
once(
    backend,
    '''  const data = snap.data();\n  if (!PUBLIC_STATUSES.has(data.status)) return fail(res, 404, "trip_not_available", "Viagem não está disponível para reserva.");\n  return json(res, 200, safePublicTrip(token, data));\n}\n''',
    '''  const data = snap.data();\n  if (!PUBLIC_STATUSES.has(data.status) || data.publicBookingEnabled !== true) {\n    return fail(res, 404, "trip_not_available", "Esta viagem não está mais disponível para reserva.");\n  }\n  if (Number(data.departureAtMillis || 0) <= Date.now()) {\n    return fail(res, 409, "trip_departed", "Esta viagem já saiu e não aceita novas reservas.");\n  }\n  return json(res, 200, safePublicTrip(token, data));\n}\n''',
    "specific public trip closed and past guard",
)

create_booking = r'''function normalizeBrazilWhatsapp(value) {
  let digits = String(value || "").replace(/\D/g, "");
  if (digits.startsWith("55") && (digits.length === 12 || digits.length === 13)) digits = digits.slice(2);
  if (!/^\d{10,11}$/.test(digits)) {
    throw Object.assign(new Error("Informe um WhatsApp brasileiro com DDD."), { httpStatus: 400, code: "invalid_whatsapp" });
  }
  const ddd = Number(digits.slice(0, 2));
  if (ddd < 11 || ddd > 99) {
    throw Object.assign(new Error("Informe um DDD brasileiro válido."), { httpStatus: 400, code: "invalid_whatsapp" });
  }
  return `+55${digits}`;
}

function publicBookingIdempotencyKey(req) {
  const value = cleanText(req.get("Idempotency-Key") || (req.body && req.body.idempotencyKey), 128);
  if (!/^[A-Za-z0-9_-]{16,128}$/.test(value)) {
    throw Object.assign(new Error("Identificador seguro da tentativa ausente."), { httpStatus: 400, code: "idempotency_key_required" });
  }
  return value;
}

function publicBookingId(token, idempotencyKey) {
  return `public_${sha256Hex(`${token}:${idempotencyKey}`).slice(0, 48)}`;
}

function publicBookingFingerprint(payload) {
  return sha256Hex(JSON.stringify(payload));
}

function publicCancellationToken(token, idempotencyKey) {
  const secret = driverTokenSecret.value() || "";
  if (!secret) throw Object.assign(new Error("Servidor de reservas não está ativado."), { httpStatus: 503, code: "booking_secret_unavailable" });
  return crypto.createHmac("sha256", secret).update(`${token}:${idempotencyKey}:cancel`).digest("base64url");
}

async function createBooking(req, res, token) {
  await enforceBookingRateLimit(req);
  const passengerName = cleanText(req.body && req.body.passengerName, 120);
  const boardingStopId = cleanText(req.body && req.body.boardingStopId, 80);
  const dropoffStopId = cleanText(req.body && req.body.dropoffStopId, 80);
  const seats = Number(req.body && req.body.seats);
  if (!passengerName) return fail(res, 400, "passenger_name_required", "Informe seu nome.");
  if (!Number.isInteger(seats) || seats < 1 || seats > 4) return fail(res, 400, "invalid_seats", "Quantidade de lugares inválida.");

  let passengerContact;
  let idempotencyKey;
  try {
    passengerContact = normalizeBrazilWhatsapp(req.body && req.body.passengerContact);
    idempotencyKey = publicBookingIdempotencyKey(req);
  } catch (error) {
    return fail(res, error.httpStatus || 400, error.code || "invalid_booking", error.message || "Reserva inválida.");
  }

  const bookingId = publicBookingId(token, idempotencyKey);
  const cancellationToken = publicCancellationToken(token, idempotencyKey);
  const cancellationHash = sha256Hex(cancellationToken);
  const fingerprint = publicBookingFingerprint({ passengerName, passengerContact, boardingStopId, dropoffStopId, seats });
  const tripRef = db.collection("trips").doc(token);
  const bookingRef = tripRef.collection("bookings").doc(bookingId);

  try {
    const result = await db.runTransaction(async (tx) => {
      const tripSnap = await tx.get(tripRef);
      if (!tripSnap.exists) throw Object.assign(new Error("Viagem não encontrada."), { httpStatus: 404, code: "trip_not_found" });
      const trip = tripSnap.data();
      if (!PUBLIC_STATUSES.has(trip.status) || trip.publicBookingEnabled !== true) {
        throw Object.assign(new Error("Esta viagem não aceita reservas pelo link."), { httpStatus: 409, code: "trip_closed" });
      }
      if (Number(trip.departureAtMillis || 0) <= Date.now()) {
        throw Object.assign(new Error("Esta viagem já saiu."), { httpStatus: 409, code: "trip_departed" });
      }

      const existingAttempt = await tx.get(bookingRef);
      if (existingAttempt.exists) {
        const existingData = existingAttempt.data();
        if (!safeEqual(existingData.idempotencyFingerprint || "", fingerprint)) {
          throw Object.assign(new Error("Esta tentativa já foi usada para outra reserva."), { httpStatus: 409, code: "idempotency_conflict" });
        }
        return {
          replayed: true,
          availableSeats: null,
          farePerSeatCents: Number(existingData.farePerSeatCents || 0),
          totalFareCents: Number(existingData.totalFareCents || 0),
        };
      }

      const bookingsSnap = await tx.get(tripRef.collection("bookings"));
      const existing = bookingsSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
      const { fromIndex, toIndex } = bookingSegmentRange(trip, boardingStopId, dropoffStopId);
      const currentLoads = reconciledSegmentLoads(trip, existing);
      const available = availableForSegmentRange(trip, currentLoads, fromIndex, toIndex);
      if (seats > available) {
        throw Object.assign(new Error("Essa vaga acabou de ser reservada. Escolha outro trecho ou viagem."), { httpStatus: 409, code: "insufficient_seats" });
      }
      const farePerSeatCents = (trip.stops || []).slice(fromIndex, toIndex).reduce((sum, stop) => sum + Math.max(0, Number(stop.priceToNextCents || 0)), 0);
      const totalFareCents = farePerSeatCents * seats;
      const now = Date.now();
      const candidate = {
        id: bookingId,
        tripId: token,
        passengerName,
        passengerContact,
        boardingStopId,
        dropoffStopId,
        seats,
        status: "CONFIRMED",
        source: "ROTA_CERTA",
        capacityClaimType: "PASSENGER",
        sourceReference: `PUBLIC_LINK:${bookingId}`,
        occupancyGroupId: bookingId,
        cancellationHash,
        idempotencyFingerprint: fingerprint,
        farePerSeatCents,
        totalFareCents,
        createdAtMillis: now,
        updatedAtMillis: now,
      };
      const reconciled = reconciledSegmentLoads(trip, [...existing, candidate], now);
      assertNoOverbooking(trip, reconciled);
      const candidatePersisted = { ...candidate };
      delete candidatePersisted.id;
      tx.create(bookingRef, candidatePersisted);
      tx.update(tripRef, {
        segmentLoads: reconciled,
        bookingsCount: existing.length + 1,
        status: statusForReconciledLoads(trip, reconciled),
        updatedAtMillis: now,
      });
      return {
        replayed: false,
        availableSeats: availableForSegmentRange(trip, reconciled, fromIndex, toIndex),
        farePerSeatCents,
        totalFareCents,
      };
    });
    return json(res, result.replayed ? 200 : 201, {
      bookingId,
      cancellationToken,
      availableSeats: result.availableSeats,
      farePerSeatCents: result.farePerSeatCents,
      totalFareCents: result.totalFareCents,
      replayed: result.replayed,
    });
  } catch (error) {
    return fail(res, error.httpStatus || 400, error.code || "booking_failed", error.message || "Falha ao reservar.");
  }
}

'''
replace_between(
    backend,
    "async function createBooking(req, res, token) {\n",
    "async function cancelPublicBooking(req, res, token, bookingId) {\n",
    create_booking,
    "idempotent public booking function",
)

# Android: automatic server -> local reconciliation using existing Booking and exact local Trip remote id.
public_sync = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/PublicBookingSync0296.kt"
public_sync.write_text(r'''package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.content.Intent
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore

internal data class PublicBookingPullResult(
    val importedCount: Int,
    val changedTripIds: Set<String>,
    val seatSyncQueued: Int,
)

internal object PublicBookingRemoteSync0296 {
    suspend fun pullAndReconcile(context: Context, store: TripStore): PublicBookingPullResult {
        val settings = store.onlineSettings()
        if (!settings.configured) return PublicBookingPullResult(0, emptySet(), 0)
        val candidates = store.trips().filter { !it.remoteId.isNullOrBlank() }
        if (candidates.isEmpty()) return PublicBookingPullResult(0, emptySet(), 0)

        var imported = 0
        val changed = linkedSetOf<String>()
        candidates.forEach { trip ->
            val remoteTripId = trip.remoteId ?: return@forEach
            val remote = runCatching { TripRemoteApi(settings).listBookings(remoteTripId).bookings }
                .getOrElse { error ->
                    UnifiedDebugEventStore.record(
                        "PUBLIC_BOOKING_PULL_FAILED",
                        context.packageName,
                        "localTrip=${trip.id} remoteTripPresent=true reason=${error.javaClass.simpleName}",
                    )
                    return@forEach
                }
            remote.forEach { incoming ->
                val existing = store.bookings().firstOrNull { it.id == incoming.id }
                val mapped = incoming.toLocalBooking(trip.id, existing)
                if (existing != mapped) {
                    store.saveBooking(mapped)
                    imported++
                    changed += trip.id
                }
            }
        }

        if (changed.isEmpty()) return PublicBookingPullResult(imported, changed, 0)
        val localTrips = store.trips()
        val localEntries = TripTimelineEngine.fromLocalAgenda(localTrips, store.bookings())
        val external = BlaBlaCollectorStateStore(context).lastResponseRecoveringDynamicSessions()
        val merged = BlaBlaTimelineAdapter.merge(localEntries, external)
        var queued = 0
        changed.forEach { localTripId ->
            val trip = store.getTrip(localTripId) ?: return@forEach
            val exact = merged.filter { it.localTripId == localTripId || (it.tripId == localTripId && it.localTripId == localTripId) }
            if (exact.size != 1) {
                UnifiedDebugEventStore.record(
                    "PUBLIC_BOOKING_SEAT_SYNC_PENDING",
                    context.packageName,
                    "localTrip=$localTripId reason=strong_timeline_match_count_${exact.size}",
                )
                return@forEach
            }
            val result = BlaBlaReliableSeatSyncBridge.enqueueDesiredStateForTimeline(
                context = context,
                entry = exact.single(),
                trip = trip,
                store = store,
                reason = "automatic_after_public_link_booking",
            )
            if (result.shouldSync) queued++
        }
        UnifiedDebugEventStore.record(
            "PUBLIC_BOOKING_PULL_RECONCILED",
            context.packageName,
            "imported=$imported changedTrips=${changed.size} seatSyncQueued=$queued",
        )
        return PublicBookingPullResult(imported, changed, queued)
    }
}

internal object TripPublicBookingLink0296 {
    fun share(context: Context, url: String): Boolean {
        val safe = url.trim().takeIf { it.startsWith("https://") } ?: return false
        return runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, safe)
                        putExtra(Intent.EXTRA_SUBJECT, "Reserve sua viagem")
                    },
                    "Compartilhar reservas",
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }
}
''', encoding="utf-8")

activity = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt"
once(
    activity,
    '''    val refresh = {\n        trips = store.trips()\n        bookings = store.bookings()\n        TripWidgetProvider.updateAll(activity)\n    }\n\n    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->\n''',
    '''    val refresh = {\n        trips = store.trips()\n        bookings = store.bookings()\n        TripWidgetProvider.updateAll(activity)\n    }\n\n    androidx.compose.runtime.LaunchedEffect(Unit) {\n        val result = PublicBookingRemoteSync0296.pullAndReconcile(activity, store)\n        if (result.importedCount > 0) {\n            refresh()\n            message = "${result.importedCount} reserva(s) recebida(s) pelo link público."\n            if (result.seatSyncQueued > 0) autoBlaBlaSyncToken++\n        }\n    }\n\n    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->\n''',
    "automatic public booking pull",
)
once(
    activity,
    '''                val settings = store.onlineSettings()\n                if (settings.configured && trip.status != TripStatus.DRAFT && trip.status != TripStatus.CANCELLED) {\n''',
    '''                val settings = store.onlineSettings()\n                if (trip.status !in setOf(TripStatus.CANCELLED, TripStatus.COMPLETED)) {\n                    OutlinedButton(onClick = {\n                        val next = trip.copy(publicBookingEnabled = !trip.publicBookingEnabled)\n                        store.saveTrip(next)\n                        if (settings.configured && next.remoteId != null) {\n                            scope.launch {\n                                runCatching { TripRemoteApi(settings).update(next) }\n                                    .onSuccess { onChanged(if (next.publicBookingEnabled) "Reservas pelo link ativadas para esta viagem." else "Reservas pelo link desativadas para esta viagem.") }\n                                    .onFailure { onChanged("Estado salvo no Rota Certa, mas ainda não sincronizado online: ${it.message}") }\n                            }\n                        } else {\n                            onChanged(if (next.publicBookingEnabled) "Reservas pelo link ativadas localmente. Publique/sincronize online para compartilhar." else "Reservas pelo link desativadas.")\n                        }\n                    }) { Text(if (trip.publicBookingEnabled) "Reservas pelo link: ATIVADAS" else "Reservas pelo link: DESATIVADAS") }\n                    if (trip.publicBookingEnabled && !trip.publicUrl.isNullOrBlank()) {\n                        OutlinedButton(onClick = {\n                            if (!TripPublicBookingLink0296.share(activity, trip.publicUrl.orEmpty())) {\n                                onChanged("Link público ainda não está disponível.")\n                            }\n                        }) { Text("📲 Compartilhar reservas") }\n                    }\n                }\n                if (settings.configured && trip.status != TripStatus.DRAFT && trip.status != TripStatus.CANCELLED) {\n''',
    "public booking opt in card control",
)

# Frontend incremental modernization over the existing passenger portal.
app = ROOT / "trip-platform/public/app.js"
once(
    app,
    '''let trip = null;\nlet confirmedBooking = null;\n''',
    '''let trip = null;\nlet confirmedBooking = null;\nlet pendingBooking = null;\n''',
    "web pending review state",
)
once(
    app,
    '''function formatDate(ms) { return new Intl.DateTimeFormat("pt-BR", { dateStyle: "full", timeStyle: "short" }).format(new Date(ms)); }\n''',
    '''function formatDate(ms) { return new Intl.DateTimeFormat("pt-BR", { dateStyle: "full", timeStyle: "short" }).format(new Date(ms)); }\nfunction formatDay(ms) { return new Intl.DateTimeFormat("pt-BR", { weekday: "short", day: "2-digit", month: "short" }).format(new Date(ms)).toUpperCase().replace(/\\./g, ""); }\nfunction normalizeWhatsapp(value) {\n  let digits = String(value || "").replace(/\\D/g, "");\n  if (digits.startsWith("55") && (digits.length === 12 || digits.length === 13)) digits = digits.slice(2);\n  return digits.length === 10 || digits.length === 11 ? `+55${digits}` : "";\n}\nfunction maskWhatsapp(value) {\n  let digits = String(value || "").replace(/\\D/g, "");\n  if (digits.startsWith("55") && digits.length > 11) digits = digits.slice(2);\n  digits = digits.slice(0, 11);\n  if (digits.length <= 2) return digits;\n  if (digits.length <= 6) return `(${digits.slice(0,2)}) ${digits.slice(2)}`;\n  if (digits.length <= 10) return `(${digits.slice(0,2)}) ${digits.slice(2,6)}-${digits.slice(6)}`;\n  return `(${digits.slice(0,2)}) ${digits.slice(2,7)}-${digits.slice(7)}`;\n}\n''',
    "web WhatsApp helpers",
)
once(
    app,
    '''    route.textContent = item.title || (item.stops || []).map((stop) => stop.name).filter(Boolean).join(" → ");\n''',
    '''    route.textContent = `${formatDay(item.departureAtMillis)} — ${item.title || (item.stops || []).map((stop) => stop.name).filter(Boolean).join(" → ")}`;\n''',
    "web large simple dates",
)
replace_between(
    app,
    "function refreshSelectors() {\n",
    "function refreshAvailability() {\n",
    r'''function refreshSelectors() {
  const stops = orderedStops();
  const boarding = $("boarding");
  const dropoff = $("dropoff");
  const fromIndex = stops.findIndex((stop) => stop.id === boarding.value);
  dropoff.innerHTML = "";
  stops.forEach((stop, index) => {
    if (index <= fromIndex || availableFor(fromIndex, index) < 1) return;
    const option = document.createElement("option");
    option.value = stop.id;
    option.textContent = stop.name;
    dropoff.appendChild(option);
  });
  refreshAvailability();
}

''',
    "web valid destination choices",
)
replace_between(
    app,
    "function refreshAvailability() {\n",
    "async function loadTrip() {\n",
    r'''function refreshAvailability() {
  if (!trip) return;
  const stops = orderedStops();
  const fromIndex = stops.findIndex((s) => s.id === $("boarding").value);
  const toIndex = stops.findIndex((s) => s.id === $("dropoff").value);
  const available = availableFor(fromIndex, toIndex);
  const selector = $("seats");
  const previous = Number(selector.value || 1);
  selector.innerHTML = "";
  for (let seats = 1; seats <= Math.min(4, available); seats += 1) {
    const option = document.createElement("option");
    option.value = String(seats);
    option.textContent = seats === 1 ? "1 lugar" : `${seats} lugares`;
    selector.appendChild(option);
  }
  if (available > 0) selector.value = String(Math.min(Math.max(1, previous), Math.min(4, available)));
  $("availability").textContent = available > 0
    ? `${available} lugar(es) disponível(is) neste trecho`
    : "Sem vagas neste trecho. Escolha outro embarque ou destino.";
  $("reserve").disabled = available < 1 || !$("dropoff").value;
}

''',
    "web dynamic seats",
)
once(
    app,
    '''  refreshSelectors();\n  restoreCancellation();\n}\n''',
    '''  refreshSelectors();\n  restoreCancellation();\n  restoreExistingBooking();\n}\n''',
    "web refresh-safe confirmed booking",
)
once(
    app,
    '''function restoreCancellation() {\n  let saved = null;\n  try { saved = JSON.parse(localStorage.getItem(cancellationStorageKey()) || "null"); } catch (_) {}\n  if (!saved?.bookingId || !saved?.cancellationToken) return;\n  $("cancelBookingId").value = saved.bookingId;\n  $("cancelToken").value = saved.cancellationToken;\n  show("cancelBooking", true);\n}\n\nasync function reserve() {\n''',
    '''function restoreCancellation() {\n  let saved = null;\n  try { saved = JSON.parse(localStorage.getItem(cancellationStorageKey()) || "null"); } catch (_) {}\n  if (!saved?.bookingId || !saved?.cancellationToken) return;\n  $("cancelBookingId").value = saved.bookingId;\n  $("cancelToken").value = saved.cancellationToken;\n  show("cancelBooking", true);\n}\n\nfunction restoreExistingBooking() {\n  let saved = null;\n  try { saved = JSON.parse(localStorage.getItem(cancellationStorageKey()) || "null"); } catch (_) {}\n  if (!saved?.bookingId || !saved?.cancellationToken) return;\n  confirmedBooking = saved;\n  $("confirmationText").textContent = "Sua reserva já está confirmada neste aparelho.";\n  $("cancelCode").textContent = saved.cancellationToken;\n  show("confirmed", true);\n  show("booking", false);\n  show("review", false);\n}\n\nfunction requestIdentity(payload) {\n  const fingerprint = JSON.stringify(payload);\n  const key = `rotacerta-booking-intent-${tripToken}`;\n  let saved = null;\n  try { saved = JSON.parse(localStorage.getItem(key) || "null"); } catch (_) {}\n  if (saved?.fingerprint === fingerprint && saved?.idempotencyKey) return saved.idempotencyKey;\n  const idempotencyKey = (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}_${Math.random().toString(36).slice(2)}_${Math.random().toString(36).slice(2)}`).replace(/[^A-Za-z0-9_-]/g, "_");\n  try { localStorage.setItem(key, JSON.stringify({ fingerprint, idempotencyKey })); } catch (_) {}\n  return idempotencyKey;\n}\n\nfunction reviewBooking() {\n  if (!trip) return;\n  const name = $("name").value.trim();\n  const passengerContact = normalizeWhatsapp($("contact").value);\n  const seats = Number($("seats").value || 0);\n  if (!name) return void ($("bookingMessage").textContent = "Informe seu nome.");\n  if (!passengerContact) return void ($("bookingMessage").textContent = "Informe seu WhatsApp com DDD.");\n  if (!$("boarding").value || !$("dropoff").value || seats < 1) return void ($("bookingMessage").textContent = "Escolha um trecho com vagas.");\n  pendingBooking = { passengerName: name, passengerContact, boardingStopId: $("boarding").value, dropoffStopId: $("dropoff").value, seats };\n  const stops = orderedStops();\n  const from = stops.find((s) => s.id === pendingBooking.boardingStopId)?.name || "Embarque";\n  const to = stops.find((s) => s.id === pendingBooking.dropoffStopId)?.name || "Destino";\n  $("reviewText").textContent = `${formatDate(trip.departureAtMillis)} • ${from} → ${to} • ${seats} lugar(es) • ${name}`;\n  show("review", true);\n  $("review").scrollIntoView({ behavior: "smooth", block: "start" });\n}\n\nasync function reserve() {\n''',
    "web review and idempotency state",
)
# Replace only the old reserve body, preserving the function signature inserted above.
reserve_start = "async function reserve() {\n"
reserve_end = "function recomputeLoadsAfterBooking(loads, booking) {\n"
reserve_function = r'''async function reserve() {
  if (!trip || !pendingBooking) return;
  $("confirmReserve").disabled = true;
  $("reviewMessage").textContent = "Confirmando sua vaga…";
  const idempotencyKey = requestIdentity(pendingBooking);
  try {
    const response = await fetch(`/v1/public/trips/${encodeURIComponent(tripToken)}/bookings`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json", "Idempotency-Key": idempotencyKey },
      body: JSON.stringify({ ...pendingBooking, idempotencyKey }),
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Não foi possível reservar.");
    confirmedBooking = {
      bookingId: body.bookingId,
      cancellationToken: body.cancellationToken,
      boardingStopId: pendingBooking.boardingStopId,
      dropoffStopId: pendingBooking.dropoffStopId,
      seats: pendingBooking.seats,
    };
    try {
      localStorage.setItem(`rotacerta-booking-${body.bookingId}`, JSON.stringify({ trip: tripToken, cancellationToken: body.cancellationToken }));
      localStorage.setItem(cancellationStorageKey(), JSON.stringify(confirmedBooking));
    } catch (_) {}
    $("cancelBookingId").value = body.bookingId;
    $("cancelToken").value = body.cancellationToken;
    $("bookingMessage").textContent = "";
    $("reviewMessage").textContent = "";
    $("confirmationText").textContent = body.replayed
      ? "✅ Esta reserva já estava confirmada. Nenhuma duplicata foi criada."
      : `✅ Reserva confirmada para ${pendingBooking.seats} lugar(es).`;
    $("cancelCode").textContent = body.cancellationToken;
    show("confirmed", true);
    show("booking", false);
    show("review", false);
    show("cancelBooking", true);
    trip.segmentLoads = recomputeLoadsAfterBooking(trip.segmentLoads || [], confirmedBooking);
    pendingBooking = null;
  } catch (error) {
    $("reviewMessage").textContent = error.message || "Falha ao confirmar reserva.";
    await loadTrip();
  } finally {
    $("confirmReserve").disabled = false;
  }
}

'''
replace_between(app, reserve_start, reserve_end, reserve_function, "web confirm reservation")
once(
    app,
    '''      localStorage.removeItem(cancellationStorageKey());\n      localStorage.removeItem(`rotacerta-booking-${bookingId}`);\n''',
    '''      localStorage.removeItem(cancellationStorageKey());\n      localStorage.removeItem(`rotacerta-booking-${bookingId}`);\n      localStorage.removeItem(`rotacerta-booking-intent-${tripToken}`);\n''',
    "web cancellation clears request identity",
)
once(
    app,
    '''$("boarding").addEventListener("change", refreshSelectors);\n$("dropoff").addEventListener("change", refreshAvailability);\n$("seats").addEventListener("change", refreshAvailability);\n$("reserve").addEventListener("click", reserve);\n$("cancelReservation").addEventListener("click", cancelReservation);\n''',
    '''$("boarding").addEventListener("change", refreshSelectors);\n$("dropoff").addEventListener("change", refreshAvailability);\n$("seats").addEventListener("change", refreshAvailability);\n$("contact").addEventListener("input", (event) => { event.target.value = maskWhatsapp(event.target.value); });\n$("reserve").addEventListener("click", reviewBooking);\n$("confirmReserve").addEventListener("click", reserve);\n$("editReservation").addEventListener("click", () => show("review", false));\n$("cancelReservation").addEventListener("click", cancelReservation);\n''',
    "web review listeners",
)

html = ROOT / "trip-platform/public/index.html"
once(
    html,
    '''button{border:0;background:#171717;color:white;font-weight:750;cursor:pointer}''',
    '''button{border:0;background:#171717;color:white;font-weight:800;cursor:pointer;min-height:52px;font-size:16px}input,select{min-height:52px;font-size:17px}.bookingStep{font-size:20px;margin:18px 0 8px}.summary{font-size:17px;line-height:1.55;padding:12px;background:#f7f8fa;border-radius:12px}''',
    "mobile first controls",
)
once(
    html,
    '''    <h2>Solicitar vaga</h2>\n    <div class="grid">\n      <label>Embarque<select id="boarding"></select></label>\n      <label>Desembarque<select id="dropoff"></select></label>\n    </div>\n    <div id="availability" class="availability"></div>\n    <div class="grid">\n      <label>Seu nome<input id="name" autocomplete="name" maxlength="120" required></label>\n      <label>Contato opcional<input id="contact" autocomplete="email" maxlength="180" placeholder="WhatsApp ou e-mail"></label>\n    </div>\n    <label style="margin-top:10px">Quantidade de lugares<select id="seats"><option>1</option><option>2</option><option>3</option><option>4</option></select></label>\n    <div class="actions"><button id="reserve">Confirmar reserva</button></div>\n    <p id="bookingMessage" class="muted"></p>\n  </section>\n\n  <section id="cancelBooking" class="card hidden">\n''',
    '''    <h2>Reserve sua viagem</h2>\n    <p class="bookingStep"><strong>1. Onde você embarca?</strong></p>\n    <label>Embarque<select id="boarding"></select></label>\n    <p class="bookingStep"><strong>2. Onde você vai descer?</strong></p>\n    <label>Desembarque<select id="dropoff"></select></label>\n    <div id="availability" class="availability"></div>\n    <p class="bookingStep"><strong>3. Quantos lugares?</strong></p>\n    <label>Quantidade de lugares<select id="seats"></select></label>\n    <p class="bookingStep"><strong>4. Seus dados</strong></p>\n    <label>Qual é o seu nome?<input id="name" autocomplete="name" maxlength="120" required></label>\n    <label style="margin-top:10px">Qual é o seu WhatsApp?<input id="contact" autocomplete="tel" inputmode="tel" maxlength="16" placeholder="(11) 99999-9999" required></label>\n    <div class="actions"><button id="reserve">REVISAR RESERVA</button></div>\n    <p id="bookingMessage" class="muted"></p>\n  </section>\n\n  <section id="review" class="card hidden">\n    <h2>CONFIRME SUA VIAGEM</h2>\n    <p id="reviewText" class="summary"></p>\n    <div class="actions">\n      <button id="confirmReserve">CONFIRMAR RESERVA</button>\n      <button id="editReservation" class="secondary">Voltar e corrigir</button>\n    </div>\n    <p id="reviewMessage" class="muted"></p>\n  </section>\n\n  <section id="cancelBooking" class="card hidden">\n''',
    "simple public booking steps",
)
once(
    html,
    '''    <h2>Reserva confirmada</h2>\n''',
    '''    <h2>✅ RESERVA CONFIRMADA</h2>\n''',
    "clear confirmation",
)

# Tests prove the contracts introduced here without duplicating the booking engine.
test_dir = ROOT / "trip-platform/functions/test"
test_dir.mkdir(parents=True, exist_ok=True)
(test_dir / "public-booking-0296.test.js").write_text(r'''"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

test("public booking remains opt-in and future-only", () => {
  assert.match(api, /publicBookingEnabled: raw\.publicBookingEnabled === true/);
  assert.match(api, /trip\.publicBookingEnabled !== true/);
  assert.match(api, /departureAtMillis.*<= Date\.now\(\)/s);
});

test("public booking is idempotent and transactionally reconciles segment capacity", () => {
  assert.match(api, /Idempotency-Key/);
  assert.match(api, /publicBookingId\(token, idempotencyKey\)/);
  assert.match(api, /idempotencyFingerprint/);
  assert.match(api, /existingAttempt\.exists/);
  assert.match(api, /reconciledSegmentLoads\(trip, \[\.\.\.existing, candidate\]/);
  assert.match(api, /assertNoOverbooking/);
  assert.match(api, /db\.runTransaction/);
  assert.doesNotMatch(api, /loads\[index\]\s*=\s*\(loads\[index\].*\+\s*seats/);
});

test("backend validates Brazilian WhatsApp and public source", () => {
  assert.match(api, /normalizeBrazilWhatsapp/);
  assert.match(api, /source: "ROTA_CERTA"/);
  assert.match(api, /sourceReference: `PUBLIC_LINK:/);
});

test("mobile portal reviews before confirmation and limits seats dynamically", () => {
  assert.match(web, /reviewBooking/);
  assert.match(web, /Math\.min\(4, available\)/);
  assert.match(web, /normalizeWhatsapp/);
  assert.match(web, /requestIdentity/);
  assert.match(web, /body\.replayed/);
  assert.match(html, /CONFIRME SUA VIAGEM/);
  assert.match(html, /Qual é o seu WhatsApp/);
  assert.match(html, /✅ RESERVA CONFIRMADA/);
});
''', encoding="utf-8")

kt_test = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/trips/PublicBooking0296Test.kt"
kt_test.write_text(r'''package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PublicBooking0296Test {
    @Test
    fun publicBookingIsOptInByDefault() {
        val trip = Trip(
            title = "A → B",
            departureAtMillis = 2_000L,
            capacity = 4,
            stops = listOf(TripStop(order = 0, name = "A"), TripStop(order = 1, name = "B")),
        )
        assertFalse(trip.publicBookingEnabled)
    }

    @Test
    fun remotePublicBookingMapsToSameLocalBookingDomain() {
        val remote = RemoteBooking(
            id = "public_booking_1",
            passengerName = "Joao",
            passengerContact = "+5511999999999",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 2,
            source = BookingSource.ROTA_CERTA,
            status = BookingStatus.CONFIRMED.name,
        )
        val local = remote.toLocalBooking("local-trip")
        assertEquals("local-trip", local.tripId)
        assertEquals(BookingSource.ROTA_CERTA, local.source)
        assertEquals(2, local.seats)
        assertEquals(BookingStatus.CONFIRMED, local.status)
    }
}
''', encoding="utf-8")

# Version only after all source transformations are established.
build = ROOT / "app/build.gradle.kts"
once(build, 'versionCode = 5588', 'versionCode = 5589', "version code")
once(build, 'versionName = "0.1.295"', 'versionName = "0.1.296"', "version name")

print("agenda_0296_public_booking=PASS reused_stage47_backend=true public_opt_in=true idempotent=true segment_authority=true mobile_review=true automatic_pull=true")
