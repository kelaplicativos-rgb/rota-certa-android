#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
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


# 1) Backend: keep the already-supported flexible capacity and preserve segment fares.
backend = ROOT / "trip-platform/functions/index.js"
replace_once(
    backend,
    '  if (!Number.isInteger(capacity) || capacity < 1 || capacity > 8) throw new Error("Capacidade inválida.");\n',
    '  if (!Number.isInteger(capacity) || capacity < 1 || capacity > 999) throw new Error("Capacidade inválida.");\n',
    "backend flexible vehicle capacity",
)
replace_once(
    backend,
    '  if (!Number.isInteger(seats) || seats < 1 || seats > 4) return fail(res, 400, "invalid_seats", "Quantidade de lugares inválida.");\n',
    '  if (!Number.isInteger(seats) || seats < 1 || seats > 999) return fail(res, 400, "invalid_seats", "Quantidade de lugares inválida.");\n',
    "backend dynamic public booking seats",
)
replace_once(
    backend,
    '''    plannedDepartureMillis: Number.isFinite(raw.plannedDepartureMillis) ? raw.plannedDepartureMillis : null,\n  }));\n''',
    '''    plannedDepartureMillis: Number.isFinite(raw.plannedDepartureMillis) ? raw.plannedDepartureMillis : null,\n    priceToNextCents: Number.isFinite(Number(raw.priceToNextCents)) ? Math.max(0, Math.round(Number(raw.priceToNextCents))) : 0,\n  }));\n''',
    "backend persists fare per segment",
)

# 2) Passenger page: quantity follows actual availability instead of a hard four-seat selector.
html = ROOT / "trip-platform/public/index.html"
replace_once(
    html,
    '    <label>Quantidade de lugares<select id="seats"></select></label>\n',
    '    <label>Quantidade de lugares<input id="seats" type="number" min="1" step="1" value="1" inputmode="numeric"></label>\n',
    "public dynamic seat input",
)

web = ROOT / "trip-platform/public/app.js"
replace_once(
    web,
    '''function availableFor(fromIndex, toIndex) {\n  if (!trip || fromIndex < 0 || toIndex <= fromIndex) return 0;\n  let available = trip.capacity;\n  for (let i = fromIndex; i < toIndex; i += 1) {\n    available = Math.min(available, trip.capacity - Number((trip.segmentLoads || [])[i] || 0));\n  }\n  return Math.max(0, available);\n}\n''',
    '''function availableFor(fromIndex, toIndex) {\n  if (!trip || fromIndex < 0 || toIndex <= fromIndex) return 0;\n  let available = trip.capacity;\n  for (let i = fromIndex; i < toIndex; i += 1) {\n    available = Math.min(available, trip.capacity - Number((trip.segmentLoads || [])[i] || 0));\n  }\n  return Math.max(0, available);\n}\n\nfunction fareFor(fromIndex, toIndex) {\n  if (!trip || fromIndex < 0 || toIndex <= fromIndex) return 0;\n  const stops = orderedStops();\n  return stops.slice(fromIndex, toIndex).reduce((sum, stop) => sum + Math.max(0, Number(stop.priceToNextCents || 0)), 0);\n}\n\nfunction formatMoney(cents) {\n  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(Math.max(0, Number(cents || 0)) / 100);\n}\n''',
    "public fare helpers",
)
replace_between(
    web,
    "function refreshAvailability() {\n",
    "async function loadTrip() {\n",
    r'''function refreshAvailability() {
  if (!trip) return;
  const stops = orderedStops();
  const fromIndex = stops.findIndex((s) => s.id === $("boarding").value);
  const toIndex = stops.findIndex((s) => s.id === $("dropoff").value);
  const available = availableFor(fromIndex, toIndex);
  const farePerSeatCents = fareFor(fromIndex, toIndex);
  const seatsInput = $("seats");
  seatsInput.max = String(Math.max(1, available));
  let requested = Number(seatsInput.value || 1);
  if (!Number.isInteger(requested) || requested < 1) {
    requested = 1;
    seatsInput.value = "1";
  }
  if (available > 0 && requested > available) {
    requested = available;
    seatsInput.value = String(available);
  }
  const fareText = farePerSeatCents > 0 ? ` • ${formatMoney(farePerSeatCents)} por pessoa` : "";
  $("availability").textContent = available > 0
    ? `${available} lugar(es) disponível(is) neste trecho${fareText}`
    : "Sem vagas neste trecho. Escolha outro embarque ou destino.";
  $("reserve").disabled = available < 1 || requested > available || !$("dropoff").value;
}

''',
    "public dynamic seats and fare display",
)
replace_once(
    web,
    '''  const from = stops.find((s) => s.id === pendingBooking.boardingStopId)?.name || "Embarque";\n  const to = stops.find((s) => s.id === pendingBooking.dropoffStopId)?.name || "Destino";\n  $("reviewText").textContent = `${formatDate(trip.departureAtMillis)} • ${from} → ${to} • ${seats} lugar(es) • ${name}`;\n''',
    '''  const fromIndex = stops.findIndex((s) => s.id === pendingBooking.boardingStopId);\n  const toIndex = stops.findIndex((s) => s.id === pendingBooking.dropoffStopId);\n  const from = stops[fromIndex]?.name || "Embarque";\n  const to = stops[toIndex]?.name || "Destino";\n  const farePerSeatCents = fareFor(fromIndex, toIndex);\n  const totalFareCents = farePerSeatCents * seats;\n  const fareText = farePerSeatCents > 0 ? ` • ${formatMoney(farePerSeatCents)} por pessoa • total ${formatMoney(totalFareCents)}` : "";\n  $("reviewText").textContent = `${formatDate(trip.departureAtMillis)} • ${from} → ${to} • ${seats} lugar(es)${fareText} • ${name}`;\n''',
    "public review exact segment fare",
)
replace_once(
    web,
    '''        seats: pendingBooking.seats,\n      };\n''',
    '''        seats: pendingBooking.seats,\n        farePerSeatCents: Number(body.farePerSeatCents || 0),\n        totalFareCents: Number(body.totalFareCents || 0),\n      };\n''',
    "public confirmed booking fare state",
)
replace_once(
    web,
    '''      $("confirmationText").textContent = body.replayed\n        ? "✅ Esta reserva já estava confirmada. Nenhuma duplicata foi criada."\n        : `✅ Reserva confirmada para ${pendingBooking.seats} lugar(es).`;\n''',
    '''      const confirmedFare = Number(body.totalFareCents || 0) > 0 ? ` Valor total: ${formatMoney(body.totalFareCents)}.` : "";\n      $("confirmationText").textContent = body.replayed\n        ? `✅ Esta reserva já estava confirmada. Nenhuma duplicata foi criada.${confirmedFare}`\n        : `✅ Reserva confirmada para ${pendingBooking.seats} lugar(es).${confirmedFare}`;\n''',
    "public confirmation total fare",
)
replace_once(
    web,
    '$("seats").addEventListener("change", refreshAvailability);\n',
    '$("seats").addEventListener("input", refreshAvailability);\n$("seats").addEventListener("change", refreshAvailability);\n',
    "public live quantity validation",
)

# The 0.1.296 local test is updated to the same dynamic-capacity contract already
# proven by stage47-flexible-capacity-r2.test.js.
public_test = ROOT / "trip-platform/functions/test/public-booking-0296.test.js"
replace_once(
    public_test,
    '  assert.match(web, /Math\\.min\\(4, available\\)/);\n',
    '  assert.match(web, /seatsInput\\.max = String\\(Math\\.max\\(1, available\\)\\)/);\n',
    "0.1.296 test follows dynamic seat contract",
)

# 3) Calendar: one physical driver owns one public agenda/token across all linked profiles.
calendar = ROOT / "trip-platform/calendar-functions/index.js"
calendar.write_text(r'''"use strict";

const crypto = require("crypto");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

initializeApp();
const db = getFirestore();
const publicCalendarToken = defineSecret("ROTA_CERTA_PUBLIC_CALENDAR_TOKEN");
const allowedStatuses = new Set(["PUBLISHED", "FULL", "STARTING", "ACTIVE"]);

function safeEqual(a, b) {
  if (!a || !b) return false;
  const left = Buffer.from(String(a));
  const right = Buffer.from(String(b));
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

function sha256Hex(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function normalizeUsername(value) {
  return String(value || "")
    .normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 32);
}

function escapeIcs(value) {
  return String(value || "")
    .replace(/\\/g, "\\\\")
    .replace(/;/g, "\\;")
    .replace(/,/g, "\\,")
    .replace(/\r?\n/g, "\\n");
}

function utc(ms) {
  return new Date(ms).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}Z$/, "Z");
}

function publicTrip(id, trip) {
  return {
    tripId: id,
    publicToken: trip.publicToken || id,
    title: trip.title || "Rota Certa — Viagem",
    departureAtMillis: trip.departureAtMillis,
    capacity: trip.capacity,
    status: trip.status,
    stops: Array.isArray(trip.stops) ? trip.stops : [],
    segmentLoads: Array.isArray(trip.segmentLoads) ? trip.segmentLoads : [],
    notes: trip.notes || "",
    publicUrl: trip.publicUrl || null,
    driverUsername: trip.driverUsername || "",
    driverDisplayName: trip.driverDisplayName || "",
    updatedAtMillis: trip.updatedAtMillis || null,
  };
}

function tripEvent(id, trip) {
  const stops = Array.isArray(trip.stops) ? [...trip.stops].sort((a, b) => a.order - b.order) : [];
  const first = stops[0] || {};
  const last = stops[stops.length - 1] || {};
  const begin = Number(first.plannedDepartureMillis || first.plannedArrivalMillis || trip.departureAtMillis);
  const end = Math.max(begin + 60000, Number(last.plannedArrivalMillis || (begin + 3600000)));
  const route = stops.map((stop) => stop.name).filter(Boolean).join(" → ");
  return [
    "BEGIN:VEVENT",
    `UID:${escapeIcs(id)}@rotacerta`,
    `DTSTAMP:${utc(Date.now())}`,
    `DTSTART:${utc(begin)}`,
    `DTEND:${utc(end)}`,
    `SUMMARY:${escapeIcs(trip.title || route || "Rota Certa — Viagem")}`,
    `LOCATION:${escapeIcs(first.address || first.name || "")}`,
    `DESCRIPTION:${escapeIcs(`Rota Certa | ${route}${trip.notes ? ` | ${trip.notes}` : ""}`)}`,
    trip.publicUrl ? `URL:${escapeIcs(trip.publicUrl)}` : null,
    "END:VEVENT",
  ].filter(Boolean).join("\r\n");
}

async function driverAgenda(usernameRaw, agendaToken) {
  const username = normalizeUsername(usernameRaw);
  if (!username || !agendaToken) return null;
  const driverRef = db.collection("tripDrivers").doc(username);
  const driverSnap = await driverRef.get();
  if (!driverSnap.exists || !safeEqual(sha256Hex(agendaToken), driverSnap.data().agendaTokenHash || "")) return null;
  const snapshot = await db.collection("trips").where("driverUsername", "==", username).limit(200).get();
  const cutoff = Date.now() - 6 * 60 * 60 * 1000;
  const documents = snapshot.docs
    .filter((doc) => {
      const trip = doc.data();
      return allowedStatuses.has(trip.status) && trip.publicBookingEnabled === true && Number(trip.departureAtMillis || 0) >= cutoff;
    })
    .sort((a, b) => Number(a.data().departureAtMillis || 0) - Number(b.data().departureAtMillis || 0))
    .slice(0, 100);
  return { username, driver: driverSnap.data(), documents };
}

async function legacyAgenda(supplied) {
  if (!safeEqual(supplied, publicCalendarToken.value())) return null;
  const snapshot = await db.collection("trips")
    .where("departureAtMillis", ">=", Date.now() - 6 * 60 * 60 * 1000)
    .orderBy("departureAtMillis", "asc")
    .limit(100)
    .get();
  return {
    username: "",
    driver: { displayName: "Rota Certa" },
    documents: snapshot.docs.filter((doc) => allowedStatuses.has(doc.data().status)),
  };
}

exports.tripCalendarFeed = onRequest(
  { secrets: [publicCalendarToken], region: "southamerica-east1" },
  async (req, res) => {
    const path = (req.path || req.url || "").split("?")[0];
    const scoped = path.match(/\/calendar\/([a-z0-9-]{3,32})\/([A-Za-z0-9_-]{16,120})\.(ics|json)$/);
    const legacy = path.match(/\/calendar\/([A-Za-z0-9_-]{16,120})\.(ics|json)$/);
    try {
      const agenda = scoped
        ? await driverAgenda(scoped[1], scoped[2])
        : legacy
          ? await legacyAgenda(legacy[1])
          : null;
      if (!agenda) return res.status(404).set("Cache-Control", "no-store").send("Not found");
      const format = scoped ? scoped[3] : legacy[2];
      if (format === "json") {
        const trips = agenda.documents.map((doc) => publicTrip(doc.id, doc.data()));
        return res.status(200)
          .set("Content-Type", "application/json; charset=utf-8")
          .set("Cache-Control", "public, max-age=30")
          .set("X-Content-Type-Options", "nosniff")
          .set("Referrer-Policy", "no-referrer")
          .send(JSON.stringify({ driver: { username: agenda.username, displayName: agenda.driver.displayName || agenda.username }, trips }));
      }
      const events = agenda.documents.map((doc) => tripEvent(doc.id, doc.data()));
      const calendarName = agenda.driver.displayName ? `Rota Certa — ${agenda.driver.displayName}` : "Rota Certa — Viagens";
      const body = [
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Rota Certa//Agenda Pública de Viagens//PT-BR",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        `X-WR-CALNAME:${escapeIcs(calendarName)}`,
        ...events,
        "END:VCALENDAR",
        "",
      ].join("\r\n");
      return res.status(200)
        .set("Content-Type", "text/calendar; charset=utf-8")
        .set("Cache-Control", "public, max-age=60")
        .set("X-Content-Type-Options", "nosniff")
        .set("Referrer-Policy", "no-referrer")
        .send(body);
    } catch (error) {
      console.error("tripCalendarFeed", error);
      return res.status(500).set("Cache-Control", "no-store").send("Calendar temporarily unavailable");
    }
  },
);
''', encoding="utf-8")

# Strong postconditions so a future materializer regression fails immediately.
backend_text = backend.read_text(encoding="utf-8")
web_text = web.read_text(encoding="utf-8")
html_text = html.read_text(encoding="utf-8")
calendar_text = calendar.read_text(encoding="utf-8")
assert "capacity > 999" in backend_text and "capacity > 8" not in backend_text
assert "seats > 999" in backend_text
assert "priceToNextCents" in backend_text
assert "fareFor" in web_text and "seatsInput.max = String(Math.max(1, available))" in web_text
assert 'id="seats" type="number" min="1" step="1" value="1"' in html_text
assert "tripDrivers" in calendar_text and "driverUsername" in calendar_text and "agendaTokenHash" in calendar_text

print("agenda_0296_final_contract_repair=PASS dynamic_capacity=true segment_fares=true driver_calendar=true")
