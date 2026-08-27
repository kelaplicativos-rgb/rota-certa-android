"use strict";

const crypto = require("crypto");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

initializeApp();
const db = getFirestore();
const driverTokenSecret = defineSecret("ROTA_CERTA_DRIVER_TOKEN");

const PUBLIC_STATUSES = new Set(["PUBLISHED", "FULL", "STARTING", "ACTIVE"]);
const DRIVER_MUTABLE_STATUSES = new Set(["DRAFT", "PUBLISHED", "FULL", "STARTING", "ACTIVE", "COMPLETED", "CANCELLED"]);
const CAPACITY_BOOKING_STATUSES = new Set(["REQUESTED", "HELD", "CONFIRMED", "CANCELLED", "EXPIRED"]);
const DRIVER_BOOKING_SOURCES = new Set(["BLABLACAR", "PRIVATE", "OTHER"]);
const CAPACITY_CLAIM_TYPES = new Set(["PASSENGER", "RESERVED_SEAT"]);

const PUBLIC_DEBUG_EVENTS = new Set([
  "PUBLIC_LINK_OPENED",
  "PUBLIC_AGENDA_LOADED",
  "PUBLIC_AGENDA_LOAD_FAILED",
  "PUBLIC_TRIP_SELECTED",
  "PUBLIC_TRIP_LOADED",
  "PUBLIC_TRIP_LOAD_FAILED",
  "PUBLIC_SEARCH_CHANGED",
  "PUBLIC_RESERVATION_STARTED",
  "PUBLIC_RESERVATION_REQUEST_SENT",
  "PUBLIC_RESERVATION_CREATED",
  "PUBLIC_RESERVATION_CHANGED",
  "PUBLIC_RESERVATION_FAILED",
  "PUBLIC_RESERVATION_CANCEL_STARTED",
  "PUBLIC_RESERVATION_CANCELLED",
  "PUBLIC_RESERVATION_CANCEL_FAILED",
  "PUBLIC_SEATS_UPDATED",
]);
const PUBLIC_DEBUG_RETENTION_MILLIS = 14 * 24 * 60 * 60 * 1000;

function json(res, status, body) {
  res.status(status);
  res.set("Content-Type", "application/json; charset=utf-8");
  res.set("Cache-Control", "no-store");
  res.set("X-Content-Type-Options", "nosniff");
  res.set("Referrer-Policy", "no-referrer");
  res.send(JSON.stringify(body));
}

function fail(res, status, code, message) {
  return json(res, status, { error: code, message });
}

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

async function requireDriver(req, res) {
  const supplied = req.get("X-Rota-Certa-Driver-Token") || "";
  const username = normalizeUsername(req.get("X-Rota-Certa-Driver-Username") || "");
  if (username) {
    const driverSnap = await db.collection("tripDrivers").doc(username).get();
    if (!driverSnap.exists || !safeEqual(sha256Hex(supplied), driverSnap.data().driverTokenHash || "")) {
      fail(res, 401, "driver_auth_required", "Autenticação do motorista inválida.");
      return null;
    }
    const data = driverSnap.data();
    return { username, displayName: cleanText(data.displayName, 120), legacy: false };
  }
  const expected = driverTokenSecret.value() || "";
  if (!safeEqual(supplied, expected)) {
    fail(res, 401, "driver_auth_required", "Autenticação do motorista inválida.");
    return null;
  }
  return { username: "", displayName: "", legacy: true };
}

async function registerDriverPushToken(req, res) {
  const driver = await requireDriver(req, res);
  if (!driver) return;
  if (!driver.username) return fail(res, 400, "driver_username_required", "Identidade pública do motorista não configurada.");
  const token = cleanText(req.body && req.body.token, 4096);
  if (token.length < 32) return fail(res, 400, "invalid_push_token", "Token de notificação inválido.");
  const now = Date.now();
  const ref = db.collection("tripDriverPushTokens").doc(sha256Hex(token));
  await ref.set({
    driverUsername: driver.username,
    token,
    platform: "android",
    appVersion: cleanText(req.body && req.body.appVersion, 40),
    deviceLabel: cleanText(req.body && req.body.deviceLabel, 80),
    createdAtMillis: now,
    updatedAtMillis: now,
    expiresAtMillis: now + 120 * 24 * 60 * 60 * 1000,
  }, { merge: true });
  return json(res, 200, { registered: true });
}

async function sendDriverBookingPush({
  driverUsername,
  event,
  tripToken,
  bookingId = "",
  seats = 0,
  tripTitle = "",
}) {
  const username = normalizeUsername(driverUsername);
  if (!username) return;
  const snapshot = await db.collection("tripDriverPushTokens")
    .where("driverUsername", "==", username)
    .limit(20)
    .get();
  const now = Date.now();
  const activeDocs = snapshot.docs.filter((doc) => Number(doc.data().expiresAtMillis || 0) > now && cleanText(doc.data().token, 4096).length >= 32);
  if (!activeDocs.length) return;

  const tokens = activeDocs.map((doc) => cleanText(doc.data().token, 4096));
  const response = await getMessaging().sendEachForMulticast({
    tokens,
    data: {
      event,
      remoteTripId: cleanText(tripToken, 100),
      bookingId: cleanText(bookingId, 120),
      seats: String(Math.max(0, Number(seats || 0))),
      tripTitle: cleanText(tripTitle, 180),
    },
    android: {
      priority: "high",
      ttl: 60 * 60 * 1000,
    },
  });

  const invalid = [];
  response.responses.forEach((item, index) => {
    if (item.success) return;
    const code = item.error && item.error.code || "";
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
      invalid.push(activeDocs[index].ref.delete());
    }
  });
  if (invalid.length) await Promise.allSettled(invalid);
}

function cleanText(value, max = 240) {
  return String(value || "").trim().slice(0, max);
}

function normalizeStops(rawStops) {
  if (!Array.isArray(rawStops) || rawStops.length < 2 || rawStops.length > 24) {
    throw new Error("A viagem precisa ter entre 2 e 24 paradas.");
  }
  const stops = rawStops.map((raw, index) => ({
    id: cleanText(raw.id, 80) || `stop-${index}`,
    order: index,
    name: cleanText(raw.name, 160),
    address: cleanText(raw.address, 300),
    latitude: Number.isFinite(raw.latitude) ? raw.latitude : null,
    longitude: Number.isFinite(raw.longitude) ? raw.longitude : null,
    plannedArrivalMillis: Number.isFinite(raw.plannedArrivalMillis) ? raw.plannedArrivalMillis : null,
    plannedDepartureMillis: Number.isFinite(raw.plannedDepartureMillis) ? raw.plannedDepartureMillis : null,
    priceToNextCents: Number.isFinite(Number(raw.priceToNextCents)) ? Math.max(0, Math.round(Number(raw.priceToNextCents))) : 0,
  }));
  if (stops.some((stop) => !stop.name)) throw new Error("Toda parada precisa de um nome.");
  if (new Set(stops.map((stop) => stop.id)).size !== stops.length) throw new Error("IDs de parada devem ser únicos.");
  return stops;
}

function normalizeDriverTrip(raw, previous = null) {
  const capacity = Number(raw.capacity);
  if (!Number.isInteger(capacity) || capacity < 1 || capacity > 999) throw new Error("Capacidade inválida.");
  const departureAtMillis = Number(raw.departureAtMillis);
  if (!Number.isFinite(departureAtMillis) || departureAtMillis <= 0) throw new Error("Horário de saída inválido.");
  const status = cleanText(raw.status, 24) || "DRAFT";
  if (!DRIVER_MUTABLE_STATUSES.has(status)) throw new Error("Estado de viagem inválido.");
  const stops = normalizeStops(raw.stops);
  if (previous && Number(previous.bookingsCount || 0) > 0) {
    const oldStopIds = (previous.stops || []).map((stop) => stop.id).join("|");
    const newStopIds = stops.map((stop) => stop.id).join("|");
    if (capacity !== previous.capacity || oldStopIds !== newStopIds) {
      throw new Error("Capacidade e estrutura de paradas não podem mudar depois da primeira reserva.");
    }
  }
  return {
    localTripId: cleanText(raw.id, 100),
    title: cleanText(raw.title, 220),
    departureAtMillis,
    capacity,
    status,
    stops,
    publicBookingEnabled: raw.publicBookingEnabled === true,
    notes: cleanText(raw.notes, 1200),
  };
}

function safePublicTrip(token, data) {
  const capacity = Math.max(0, Number(data.capacity || 0));
  const expectedSegments = Math.max(0, (Array.isArray(data.stops) ? data.stops.length : 0) - 1);
  const segmentLoads = Array.isArray(data.segmentLoads)
    ? data.segmentLoads.slice(0, expectedSegments).map((load) => Math.max(0, Number(load || 0)))
    : [];
  const availability = capacityAvailabilityRange({ capacity }, segmentLoads);
  const fullyOccupied = data.status === "FULL" || (segmentLoads.length === expectedSegments && expectedSegments > 0 && segmentLoads.every((load) => load >= capacity));
  return {
    tripId: token,
    publicToken: token,
    title: data.title,
    departureAtMillis: data.departureAtMillis,
    capacity,
    status: fullyOccupied ? "FULL" : data.status,
    stops: data.stops,
    segmentLoads,
    availableSeatsMinimum: fullyOccupied ? 0 : availability.minimum,
    availableSeatsMaximum: fullyOccupied ? 0 : availability.maximum,
    isFull: fullyOccupied,
    canReserve: data.publicBookingEnabled === true && !fullyOccupied && availability.maximum > 0,
    publicBookingEnabled: data.publicBookingEnabled === true,
    notes: data.notes || "",
    publicUrl: data.publicUrl || null,
    driverUsername: data.driverUsername || "",
    driverDisplayName: data.driverDisplayName || "",
    updatedAtMillis: data.updatedAtMillis || null,
  };
}

function clientIp(req) {
  const forwarded = req.get("x-forwarded-for") || "";
  return cleanText(forwarded.split(",")[0] || req.ip || "unknown", 96);
}

async function enforceBookingRateLimit(req) {
  const minute = Math.floor(Date.now() / 60000);
  const key = crypto.createHash("sha256").update(`${clientIp(req)}:${minute}`).digest("hex");
  const ref = db.collection("tripRateLimits").doc(key);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const count = snap.exists ? Number(snap.data().count || 0) : 0;
    if (count >= 10) throw Object.assign(new Error("Muitas tentativas. Aguarde um minuto."), { httpStatus: 429, code: "rate_limited" });
    tx.set(ref, { count: count + 1, expiresAtMillis: Date.now() + 5 * 60000 }, { merge: true });
  });
}

async function enforcePublicDebugRateLimit(req) {
  const minute = Math.floor(Date.now() / 60000);
  const key = sha256Hex(`public-debug:${clientIp(req)}:${minute}`);
  const ref = db.collection("tripPublicDebugRateLimits").doc(key);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const count = snap.exists ? Number(snap.data().count || 0) : 0;
    if (count >= 60) throw Object.assign(new Error("Limite de diagnóstico atingido."), { httpStatus: 429, code: "debug_rate_limited" });
    tx.set(ref, { count: count + 1, expiresAtMillis: Date.now() + 5 * 60000 }, { merge: true });
  });
}

function safePublicDebugReason(value) {
  return cleanText(value, 80).toLowerCase().replace(/[^a-z0-9_.:-]+/g, "_").replace(/^_+|_+$/g, "");
}

function safePublicDebugSession(value) {
  return cleanText(value, 80).replace(/[^A-Za-z0-9_-]/g, "").slice(0, 80);
}

async function appendPublicDebugEvent({
  driverUsername,
  event,
  source = "server",
  sessionId = "",
  tripToken = "",
  agendaToken = "",
  screen = "",
  reason = "",
  statusCode = 0,
  seats = 0,
  fromIndex = -1,
  toIndex = -1,
  replayed = false,
}) {
  const username = normalizeUsername(driverUsername);
  if (!username || !PUBLIC_DEBUG_EVENTS.has(event)) return;
  const now = Date.now();
  const ref = db.collection("tripPublicDebugEvents").doc(`${now}_${crypto.randomBytes(8).toString("hex")}`);
  const safeStatus = Number.isInteger(Number(statusCode)) ? Math.max(0, Math.min(599, Number(statusCode))) : 0;
  const safeSeats = Number.isInteger(Number(seats)) ? Math.max(0, Math.min(999, Number(seats))) : 0;
  const safeFrom = Number.isInteger(Number(fromIndex)) ? Math.max(-1, Math.min(23, Number(fromIndex))) : -1;
  const safeTo = Number.isInteger(Number(toIndex)) ? Math.max(-1, Math.min(23, Number(toIndex))) : -1;
  await ref.set({
    driverUsername: username,
    event,
    source: source === "browser" ? "browser" : "server",
    sessionId: safePublicDebugSession(sessionId),
    targetType: tripToken ? "trip" : (agendaToken ? "agenda" : "unknown"),
    tripRefHash: tripToken ? sha256Hex(`trip:${tripToken}`).slice(0, 24) : "",
    agendaRefHash: agendaToken ? sha256Hex(`agenda:${agendaToken}`).slice(0, 24) : "",
    screen: cleanText(screen, 24).replace(/[^a-zA-Z0-9_-]/g, ""),
    reason: safePublicDebugReason(reason),
    statusCode: safeStatus,
    seats: safeSeats,
    fromIndex: safeFrom,
    toIndex: safeTo,
    replayed: replayed === true,
    createdAtMillis: now,
    expiresAtMillis: now + PUBLIC_DEBUG_RETENTION_MILLIS,
  });
}

async function resolvePublicDebugTarget(body) {
  const tripToken = cleanText(body && body.tripToken, 80).replace(/[^A-Za-z0-9_-]/g, "");
  if (tripToken.length >= 16) {
    const tripSnap = await db.collection("trips").doc(tripToken).get();
    if (!tripSnap.exists) return null;
    const data = tripSnap.data();
    const username = normalizeUsername(data.driverUsername || "");
    if (!username) return null;
    return { driverUsername: username, tripToken, agendaToken: "" };
  }
  const driverUsername = normalizeUsername(body && body.driverUsername);
  const agendaToken = cleanText(body && body.agendaToken, 80).replace(/[^A-Za-z0-9_-]/g, "");
  if (driverUsername.length >= 3 && agendaToken.length >= 16) {
    const driverSnap = await db.collection("tripDrivers").doc(driverUsername).get();
    if (!driverSnap.exists || !safeEqual(sha256Hex(agendaToken), driverSnap.data().agendaTokenHash || "")) return null;
    return { driverUsername, tripToken: "", agendaToken };
  }
  return null;
}

async function recordPublicBrowserDebugEvent(req, res) {
  try {
    await enforcePublicDebugRateLimit(req);
  } catch (error) {
    return res.status(error.httpStatus || 429).send("");
  }
  const event = cleanText(req.body && req.body.event, 80);
  if (!PUBLIC_DEBUG_EVENTS.has(event)) return res.status(204).send("");
  const target = await resolvePublicDebugTarget(req.body || {});
  if (!target) return res.status(204).send("");
  await appendPublicDebugEvent({
    ...target,
    event,
    source: "browser",
    sessionId: req.body && req.body.sessionId,
    screen: req.body && req.body.screen,
    reason: req.body && req.body.reason,
    statusCode: Number(req.body && req.body.statusCode || 0),
    seats: Number(req.body && req.body.seats || 0),
    fromIndex: Number((req.body && req.body.fromIndex) ?? -1),
    toIndex: Number((req.body && req.body.toIndex) ?? -1),
    replayed: req.body && req.body.replayed === true,
  });
  return res.status(204).send("");
}

async function listDriverPublicDebugEvents(req, res) {
  const driver = await requireDriver(req, res);
  if (!driver) return;
  if (!driver.username) return fail(res, 400, "driver_username_required", "Identidade pública do motorista não configurada.");
  const limit = Math.max(1, Math.min(250, Number(req.query && req.query.limit || 100) || 100));
  const afterMillis = Math.max(0, Number(req.query && req.query.afterMillis || 0) || 0);
  const snapshot = await db.collection("tripPublicDebugEvents").where("driverUsername", "==", driver.username).limit(500).get();
  const now = Date.now();
  const stale = [];
  const events = [];
  for (const doc of snapshot.docs) {
    const data = doc.data();
    if (Number(data.expiresAtMillis || 0) > 0 && Number(data.expiresAtMillis) < now) {
      if (stale.length < 40) stale.push(doc.ref.delete());
      continue;
    }
    if (Number(data.createdAtMillis || 0) <= afterMillis) continue;
    events.push({
      id: doc.id,
      event: cleanText(data.event, 80),
      source: data.source === "browser" ? "browser" : "server",
      sessionId: safePublicDebugSession(data.sessionId),
      targetType: cleanText(data.targetType, 16),
      targetRefHash: cleanText(data.tripRefHash || data.agendaRefHash, 24),
      screen: cleanText(data.screen, 24),
      reason: safePublicDebugReason(data.reason),
      statusCode: Number(data.statusCode || 0),
      seats: Number(data.seats || 0),
      fromIndex: Number(data.fromIndex ?? -1),
      toIndex: Number(data.toIndex ?? -1),
      replayed: data.replayed === true,
      createdAtMillis: Number(data.createdAtMillis || 0),
    });
  }
  if (stale.length) await Promise.allSettled(stale);
  events.sort((a, b) => a.createdAtMillis - b.createdAtMillis || a.id.localeCompare(b.id));
  return json(res, 200, { events: events.slice(-limit) });
}

function bookingSegmentRange(trip, boardingStopId, dropoffStopId) {
  const stops = trip.stops || [];
  const fromIndex = stops.findIndex((stop) => stop.id === boardingStopId);
  const toIndex = stops.findIndex((stop) => stop.id === dropoffStopId);
  if (fromIndex < 0 || toIndex < 0 || fromIndex >= toIndex) throw new Error("Trecho de embarque/desembarque inválido.");
  return { fromIndex, toIndex };
}

function recordOccupiesCapacity(record, now = Date.now()) {
  if (record.status === "CONFIRMED") return true;
  if (record.status !== "HELD") return false;
  const expiry = Number(record.holdExpiresAtMillis || 0);
  return !expiry || expiry > now;
}

function reconciledSegmentLoads(trip, records, now = Date.now()) {
  const stops = trip.stops || [];
  const claims = Array.from({ length: Math.max(0, stops.length - 1) }, () => new Map());
  for (const record of records) {
    if (!record || Number(record.seats || 0) <= 0 || !recordOccupiesCapacity(record, now)) continue;
    const fromIndex = stops.findIndex((stop) => stop.id === record.boardingStopId);
    const toIndex = stops.findIndex((stop) => stop.id === record.dropoffStopId);
    if (fromIndex < 0 || toIndex <= fromIndex) continue;
    const group = cleanText(record.occupancyGroupId, 120);
    const key = group ? `group:${group}` : `booking:${cleanText(record.id, 120)}`;
    for (let index = fromIndex; index < toIndex; index += 1) {
      const previous = Number(claims[index].get(key) || 0);
      const seats = Number(record.seats || 0);
      if (seats > previous) claims[index].set(key, seats);
    }
  }
  return claims.map((segment) => Array.from(segment.values()).reduce((sum, seats) => sum + Number(seats || 0), 0));
}

function assertNoOverbooking(trip, loads) {
  const capacity = Number(trip.capacity || 0);
  if (loads.some((load) => Number(load || 0) > capacity)) {
    throw Object.assign(new Error("A conciliação ultrapassaria a capacidade física do veículo."), { httpStatus: 409, code: "overbooking" });
  }
}

function statusForReconciledLoads(trip, loads) {
  if (trip.status !== "PUBLISHED" && trip.status !== "FULL") return trip.status;
  const globallyFull = loads.length > 0 && loads.every((load) => Number(load || 0) >= Number(trip.capacity || 0));
  return globallyFull ? "FULL" : "PUBLISHED";
}

function availableForSegmentRange(trip, loads, fromIndex, toIndex) {
  let available = Number(trip.capacity || 0);
  for (let index = fromIndex; index < toIndex; index += 1) {
    available = Math.min(available, Number(trip.capacity || 0) - Number(loads[index] || 0));
  }
  return Math.max(0, available);
}

function capacityAvailabilityRange(trip, loads) {
  if (!loads.length) return { minimum: Number(trip.capacity || 0), maximum: Number(trip.capacity || 0) };
  const available = loads.map((load) => Math.max(0, Number(trip.capacity || 0) - Number(load || 0)));
  return { minimum: Math.min(...available), maximum: Math.max(...available) };
}

function normalizeDriverCapacityBooking(raw, trip, bookingId, previous = null) {
  const source = cleanText(raw.source, 24).toUpperCase() || "OTHER";
  const capacityClaimType = cleanText(raw.capacityClaimType, 24).toUpperCase() || "PASSENGER";
  const status = cleanText(raw.status, 24).toUpperCase() || "CONFIRMED";
  if (!DRIVER_BOOKING_SOURCES.has(source)) throw new Error("Origem de reserva inválida.");
  if (!CAPACITY_CLAIM_TYPES.has(capacityClaimType)) throw new Error("Tipo de ocupação inválido.");
  if (!CAPACITY_BOOKING_STATUSES.has(status)) throw new Error("Estado de reserva inválido.");
  const seats = Number(raw.seats);
  if (!Number.isInteger(seats) || seats < 1 || seats > 999) throw new Error("Quantidade de lugares inválida.");
  const passengerName = cleanText(raw.passengerName, 120);
  if (!passengerName) throw new Error("Informe o passageiro ou a identificação da vaga.");
  const boardingStopId = cleanText(raw.boardingStopId, 80);
  const dropoffStopId = cleanText(raw.dropoffStopId, 80);
  bookingSegmentRange(trip, boardingStopId, dropoffStopId);
  const holdExpiresAtMillis = Number.isFinite(Number(raw.holdExpiresAtMillis)) && Number(raw.holdExpiresAtMillis) > 0
    ? Number(raw.holdExpiresAtMillis)
    : null;
  const now = Date.now();
  return {
    id: bookingId,
    tripId: trip.publicToken || bookingId,
    passengerName,
    passengerContact: cleanText(raw.passengerContact, 180),
    boardingStopId,
    dropoffStopId,
    seats,
    status,
    holdExpiresAtMillis,
    source,
    capacityClaimType,
    sourceReference: cleanText(raw.sourceReference, 240),
    occupancyGroupId: cleanText(raw.occupancyGroupId, 120) || null,
    createdAtMillis: Number(previous && previous.createdAtMillis) || now,
    updatedAtMillis: now,
  };
}

function publicBaseFor(req) {
  const supplied = cleanText(req.get("X-Rota-Certa-Public-Base-Url"), 500).replace(/\/$/, "");
  if (supplied.startsWith("https://")) return supplied;
  const proto = cleanText(req.get("x-forwarded-proto"), 12) || "https";
  const host = cleanText(req.get("x-forwarded-host") || req.get("host"), 300);
  return host ? `${proto}://${host}` : "";
}

function publicUrlFor(req, token, username = "") {
  const base = publicBaseFor(req);
  const query = username
    ? `?motorista=${encodeURIComponent(username)}&trip=${encodeURIComponent(token)}`
    : `?trip=${encodeURIComponent(token)}`;
  return base ? `${base}/${query}` : `/${query}`;
}

function publicAgendaUrlFor(req, username, agendaToken) {
  const base = publicBaseFor(req);
  const query = `?motorista=${encodeURIComponent(username)}&agenda=${encodeURIComponent(agendaToken)}`;
  return base ? `${base}/${query}` : `/${query}`;
}

function publicCalendarUrlFor(req, username, agendaToken) {
  const base = publicBaseFor(req);
  const path = `/calendar/${encodeURIComponent(username)}/${encodeURIComponent(agendaToken)}.ics`;
  return base ? `${base}${path}` : path;
}

async function registerDriver(req, res) {
  await enforceBookingRateLimit(req);
  const displayName = cleanText(req.body && req.body.displayName, 120);
  const username = normalizeUsername(req.body && req.body.username);
  if (!displayName) return fail(res, 400, "driver_name_required", "Informe o nome público do motorista.");
  if (username.length < 3 || username.length > 32) return fail(res, 400, "invalid_username", "Nome de usuário inválido.");
  const driverToken = crypto.randomBytes(32).toString("base64url");
  const publicAgendaToken = crypto.randomBytes(24).toString("base64url");
  const ref = db.collection("tripDrivers").doc(username);
  const now = Date.now();
  try {
    await db.runTransaction(async (tx) => {
      const existing = await tx.get(ref);
      if (existing.exists) throw Object.assign(new Error("Esse nome de usuário já está em uso."), { httpStatus: 409, code: "username_taken" });
      tx.create(ref, {
        username,
        displayName,
        driverTokenHash: sha256Hex(driverToken),
        agendaTokenHash: sha256Hex(publicAgendaToken),
        createdAtMillis: now,
        updatedAtMillis: now,
      });
    });
    return json(res, 201, {
      displayName,
      username,
      driverToken,
      publicAgendaToken,
      publicAgendaUrl: publicAgendaUrlFor(req, username, publicAgendaToken),
      calendarUrl: publicCalendarUrlFor(req, username, publicAgendaToken),
    });
  } catch (error) {
    return fail(res, error.httpStatus || 500, error.code || "driver_registration_failed", error.message || "Falha ao gerar o link do motorista.");
  }
}

async function ensureDriverPublicAgenda(req, res) {
  const driver = await requireDriver(req, res);
  if (!driver) return;
  if (!driver.username) {
    return fail(res, 409, "driver_identity_required", "Cadastre um nome de usuário do motorista para usar a agenda pública.");
  }

  const suppliedToken = cleanText(req.body && req.body.publicAgendaToken, 120).replace(/[^A-Za-z0-9_-]/g, "");
  const ref = db.collection("tripDrivers").doc(driver.username);
  const snap = await ref.get();
  if (!snap.exists) return fail(res, 404, "driver_not_found", "Motorista não encontrado.");

  const data = snap.data();
  const storedHash = cleanText(data.agendaTokenHash, 128);
  const tokenIsCurrent = suppliedToken.length >= 16 &&
    storedHash &&
    safeEqual(sha256Hex(suppliedToken), storedHash);

  if (!tokenIsCurrent) {
    return fail(
      res,
      409,
      "agenda_token_mismatch",
      "O token da agenda não confere. O link atual foi preservado. Use a ação explícita Gerar novo link para substituí-lo.",
    );
  }
  const publicAgendaToken = suppliedToken;

  let driverWhatsapp = "";
  try {
    const rawWhatsapp = cleanText(req.body && req.body.driverWhatsapp, 40);
    driverWhatsapp = rawWhatsapp ? normalizeBrazilWhatsapp(rawWhatsapp) : "";
  } catch (error) {
    return fail(res, 400, error.code || "invalid_whatsapp", error.message);
  }

  const publicProfileUpdate = {
    driverWhatsapp,
    driverPhotoUrl: cleanText(req.body && req.body.driverPhotoUrl, 500).startsWith("https://")
      ? cleanText(req.body && req.body.driverPhotoUrl, 500)
      : "",
    driverPublicAbout: cleanText(req.body && req.body.driverPublicAbout, 320),
    driverPublicRating: cleanText(req.body && req.body.driverPublicRating, 20),
    driverPublicReviewCount: Math.max(0, Math.min(9999999, Number(req.body && req.body.driverPublicReviewCount || 0) || 0)),
    driverPublicBadge: cleanText(req.body && req.body.driverPublicBadge, 80),
    vehicleMakeModel: cleanText(req.body && req.body.vehicleMakeModel, 120),
    vehicleColor: cleanText(req.body && req.body.vehicleColor, 60),
    vehicleAmenities: cleanText(req.body && req.body.vehicleAmenities, 240),
    driverPreferences: cleanText(req.body && req.body.driverPreferences, 240),
    paymentInstructions: cleanText(req.body && req.body.paymentInstructions, 240),
    updatedAtMillis: Date.now(),
  };
  await ref.update(publicProfileUpdate);

  return json(res, 200, {
    displayName: cleanText(data.displayName, 120) || driver.displayName,
    username: driver.username,
    publicAgendaToken,
    publicAgendaUrl: publicAgendaUrlFor(req, driver.username, publicAgendaToken),
    calendarUrl: publicCalendarUrlFor(req, driver.username, publicAgendaToken),
    repaired: false,
  });
}

async function regenerateDriverPublicAgenda(req, res) {
  const driver = await requireDriver(req, res);
  if (!driver) return;
  if (!driver.username) {
    return fail(res, 409, "driver_identity_required", "Cadastre um nome de usuário do motorista para usar a agenda pública.");
  }

  const confirmation = cleanText(req.body && req.body.confirmation, 80);
  if (confirmation !== "REGENERATE_PUBLIC_AGENDA_LINK") {
    return fail(res, 400, "explicit_confirmation_required", "Confirmação explícita obrigatória para gerar um novo link.");
  }

  const ref = db.collection("tripDrivers").doc(driver.username);
  const snap = await ref.get();
  if (!snap.exists) return fail(res, 404, "driver_not_found", "Motorista não encontrado.");

  const publicAgendaToken = crypto.randomBytes(24).toString("base64url");
  await ref.update({
    agendaTokenHash: sha256Hex(publicAgendaToken),
    updatedAtMillis: Date.now(),
  });

  return json(res, 200, {
    displayName: cleanText(snap.data().displayName, 120) || driver.displayName,
    username: driver.username,
    publicAgendaToken,
    publicAgendaUrl: publicAgendaUrlFor(req, driver.username, publicAgendaToken),
    calendarUrl: publicCalendarUrlFor(req, driver.username, publicAgendaToken),
    repaired: true,
  });
}

function splitPublicList(value) {
  return cleanText(value, 240)
    .split(/[;,\n]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 12);
}

function safePublicDriverProfile(data, username = "") {
  const driver = data || {};
  return {
    displayName: cleanText(driver.displayName, 120),
    username: normalizeUsername(username || driver.username || ""),
    whatsapp: cleanText(driver.driverWhatsapp, 24),
    photoUrl: cleanText(driver.driverPhotoUrl, 500).startsWith("https://")
      ? cleanText(driver.driverPhotoUrl, 500)
      : "",
    about: cleanText(driver.driverPublicAbout, 320),
    rating: cleanText(driver.driverPublicRating, 20),
    reviewCount: Math.max(0, Number(driver.driverPublicReviewCount || 0) || 0),
    badge: cleanText(driver.driverPublicBadge, 80),
    vehicle: {
      makeModel: cleanText(driver.vehicleMakeModel, 120),
      color: cleanText(driver.vehicleColor, 60),
    },
    amenities: splitPublicList(driver.vehicleAmenities),
    preferences: splitPublicList(driver.driverPreferences),
    paymentInstructions: cleanText(driver.paymentInstructions, 240),
  };
}

async function getPublicDriverAgenda(res, req, usernameRaw, agendaToken) {
  const username = normalizeUsername(usernameRaw);
  if (!username || !agendaToken) return fail(res, 404, "agenda_not_found", "Agenda não encontrada.");
  const driverSnap = await db.collection("tripDrivers").doc(username).get();
  if (!driverSnap.exists || !safeEqual(sha256Hex(agendaToken), driverSnap.data().agendaTokenHash || "")) {
    if (driverSnap.exists) {
      await appendPublicDebugEvent({
        driverUsername: username,
        event: "PUBLIC_AGENDA_LOAD_FAILED",
        source: "server",
        agendaToken,
        screen: "agenda",
        reason: "agenda_not_found",
        statusCode: 404,
      }).catch(() => {});
    }
    return fail(res, 404, "agenda_not_found", "Agenda não encontrada.");
  }
  const driver = driverSnap.data();
  const snapshot = await db.collection("trips").where("driverUsername", "==", username).limit(200).get();
  const trips = snapshot.docs
    .map((doc) => safePublicTrip(doc.id, doc.data()))
    .filter((trip) => PUBLIC_STATUSES.has(trip.status) && Number(trip.departureAtMillis) > Date.now())
    .sort((a, b) => Number(a.departureAtMillis) - Number(b.departureAtMillis))
    .slice(0, 100);
  await appendPublicDebugEvent({
    driverUsername: username,
    event: "PUBLIC_AGENDA_LOADED",
    source: "server",
    agendaToken,
    screen: "agenda",
    statusCode: 200,
  }).catch(() => {});
  return json(res, 200, {
    driver: safePublicDriverProfile(driver, username),
    trips,
  });
}

async function createDriverTrip(req, res) {
  const driver = await requireDriver(req, res);
  if (!driver) return;
  let normalized;
  try {
    normalized = normalizeDriverTrip(req.body || {});
  } catch (error) {
    return fail(res, 400, "invalid_trip", error.message);
  }
  const requestedToken = cleanText(req.body && req.body.publicToken, 80).replace(/[^A-Za-z0-9_-]/g, "");
  const token = requestedToken.length >= 16 ? requestedToken : crypto.randomBytes(24).toString("base64url");
  const ref = db.collection("trips").doc(token);
  const now = Date.now();
  const publicUrl = publicUrlFor(req, token, driver.username);
  try {
    await db.runTransaction(async (tx) => {
      const existing = await tx.get(ref);
      if (existing.exists) throw Object.assign(new Error("Token público já existe."), { httpStatus: 409, code: "token_collision" });
      tx.create(ref, {
        ...normalized,
        publicToken: token,
        publicUrl,
        driverUsername: driver.username,
        driverDisplayName: driver.displayName,
        segmentLoads: new Array(normalized.stops.length - 1).fill(0),
        bookingsCount: 0,
        createdAtMillis: now,
        updatedAtMillis: now,
      });
    });
    return json(res, 201, { tripId: token, publicToken: token, publicUrl });
  } catch (error) {
    return fail(res, error.httpStatus || 500, error.code || "publish_failed", error.message || "Falha ao publicar viagem.");
  }
}

async function updateDriverTrip(req, res, token) {
  const driver = await requireDriver(req, res);
  if (!driver) return;
  const ref = db.collection("trips").doc(token);
  try {
    const result = await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) throw Object.assign(new Error("Viagem não encontrada."), { httpStatus: 404, code: "trip_not_found" });
      const previous = snap.data();
      if (previous.driverUsername && previous.driverUsername !== driver.username) {
        throw Object.assign(new Error("Viagem pertence a outro motorista."), { httpStatus: 403, code: "trip_owner_mismatch" });
      }
      const normalized = normalizeDriverTrip(req.body || {}, previous);
      const ownerUsername = previous.driverUsername || driver.username;
      const ownerDisplayName = previous.driverDisplayName || driver.displayName;
      const publicUrl = previous.publicUrl || publicUrlFor(req, token, ownerUsername);
      tx.update(ref, { ...normalized, publicUrl, driverUsername: ownerUsername, driverDisplayName: ownerDisplayName, updatedAtMillis: Date.now() });
      return { publicUrl };
    });
    return json(res, 200, { tripId: token, publicToken: token, publicUrl: result.publicUrl });
  } catch (error) {
    return fail(res, error.httpStatus || 400, error.code || "update_failed", error.message || "Falha ao atualizar viagem.");
  }
}

async function getPublicTrip(res, token) {
  const snap = await db.collection("trips").doc(token).get();
  if (!snap.exists) return fail(res, 404, "trip_not_found", "Viagem não encontrada.");
  const data = snap.data();
  const driverUsername = normalizeUsername(data.driverUsername || "");
  if (!PUBLIC_STATUSES.has(data.status)) {
    await appendPublicDebugEvent({
      driverUsername,
      event: "PUBLIC_TRIP_LOAD_FAILED",
      source: "server",
      tripToken: token,
      screen: "trip",
      reason: "trip_not_available",
      statusCode: 404,
    }).catch(() => {});
    return fail(res, 404, "trip_not_available", "Esta viagem não está mais disponível para reserva.");
  }
  if (Number(data.departureAtMillis || 0) <= Date.now()) {
    await appendPublicDebugEvent({
      driverUsername,
      event: "PUBLIC_TRIP_LOAD_FAILED",
      source: "server",
      tripToken: token,
      screen: "trip",
      reason: "trip_departed",
      statusCode: 409,
    }).catch(() => {});
    return fail(res, 409, "trip_departed", "Esta viagem já saiu e não aceita novas reservas.");
  }
  await appendPublicDebugEvent({
    driverUsername,
    event: "PUBLIC_TRIP_LOADED",
    source: "server",
    tripToken: token,
    screen: "trip",
    statusCode: 200,
  }).catch(() => {});
  let publicDriver = safePublicDriverProfile(data, driverUsername);
  if (driverUsername) {
    const driverSnap = await db.collection("tripDrivers").doc(driverUsername).get().catch(() => null);
    if (driverSnap && driverSnap.exists) publicDriver = safePublicDriverProfile(driverSnap.data(), driverUsername);
  }
  return json(res, 200, {
    ...safePublicTrip(token, data),
    driver: publicDriver,
  });
}

function normalizeBrazilWhatsapp(value) {
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
  let debugDriverUsername = "";
  const passengerName = cleanText(req.body && req.body.passengerName, 120);
  const boardingStopId = cleanText(req.body && req.body.boardingStopId, 80);
  const dropoffStopId = cleanText(req.body && req.body.dropoffStopId, 80);
  const seats = Number(req.body && req.body.seats);
  if (!passengerName) return fail(res, 400, "passenger_name_required", "Informe seu nome.");
  if (!Number.isInteger(seats) || seats < 1 || seats > 999) return fail(res, 400, "invalid_seats", "Quantidade de lugares inválida.");

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
      debugDriverUsername = normalizeUsername(trip.driverUsername || "");
      if (!PUBLIC_STATUSES.has(trip.status)) {
        throw Object.assign(new Error("Esta viagem não aceita reservas pelo link."), { httpStatus: 409, code: "trip_closed" });
      }
      if (trip.status === "FULL") {
        throw Object.assign(new Error("Esta viagem está lotada."), { httpStatus: 409, code: "trip_full" });
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
          driverUsername: debugDriverUsername,
          tripTitle: cleanText(trip.title, 180),
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
        driverUsername: debugDriverUsername,
        tripTitle: cleanText(trip.title, 180),
      };
    });
    const statusCode = result.replayed ? 200 : 201;
    await appendPublicDebugEvent({
      driverUsername: result.driverUsername || debugDriverUsername,
      event: "PUBLIC_RESERVATION_CREATED",
      source: "server",
      tripToken: token,
      screen: "trip",
      statusCode,
      seats,
      replayed: result.replayed,
    }).catch(() => {});
    await appendPublicDebugEvent({
      driverUsername: result.driverUsername || debugDriverUsername,
      event: "PUBLIC_SEATS_UPDATED",
      source: "server",
      tripToken: token,
      screen: "trip",
      statusCode,
      seats,
    }).catch(() => {});
    if (!result.replayed) {
      await sendDriverBookingPush({
        driverUsername: result.driverUsername || debugDriverUsername,
        event: "reservation_created",
        tripToken: token,
        bookingId,
        seats,
        tripTitle: result.tripTitle || "",
      }).catch((error) => console.error("push reservation_created", error));
    }
    return json(res, statusCode, {
      bookingId,
      cancellationToken,
      availableSeats: result.availableSeats,
      farePerSeatCents: result.farePerSeatCents,
      totalFareCents: result.totalFareCents,
      replayed: result.replayed,
    });
  } catch (error) {
    await appendPublicDebugEvent({
      driverUsername: debugDriverUsername,
      event: "PUBLIC_RESERVATION_FAILED",
      source: "server",
      tripToken: token,
      screen: "trip",
      reason: error.code || "booking_failed",
      statusCode: error.httpStatus || 400,
      seats,
    }).catch(() => {});
    return fail(res, error.httpStatus || 400, error.code || "booking_failed", error.message || "Falha ao reservar.");
  }
}

async function cancelPublicBooking(req, res, token, bookingId) {
  const cancellationToken = cleanText(req.body && req.body.cancellationToken, 120);
  let debugDriverUsername = "";
  const suppliedHash = crypto.createHash("sha256").update(cancellationToken).digest("hex");
  const tripRef = db.collection("trips").doc(token);
  const bookingRef = tripRef.collection("bookings").doc(bookingId);
  try {
    const result = await db.runTransaction(async (tx) => {
      const tripSnap = await tx.get(tripRef);
      if (!tripSnap.exists) throw Object.assign(new Error("Reserva não encontrada."), { httpStatus: 404, code: "booking_not_found" });
      const trip = tripSnap.data();
      debugDriverUsername = normalizeUsername(trip.driverUsername || "");
      const bookingsSnap = await tx.get(tripRef.collection("bookings"));
      const records = bookingsSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
      const booking = records.find((record) => record.id === bookingId);
      if (!booking) throw Object.assign(new Error("Reserva não encontrada."), { httpStatus: 404, code: "booking_not_found" });
      if (!safeEqual(suppliedHash, booking.cancellationHash || "")) throw Object.assign(new Error("Código de cancelamento inválido."), { httpStatus: 401, code: "invalid_cancel_token" });
      const now = Date.now();
      const reconciledRecords = records.map((record) => record.id === bookingId ? { ...record, status: "CANCELLED", updatedAtMillis: now } : record);
      const loads = reconciledSegmentLoads(trip, reconciledRecords, now);
      assertNoOverbooking(trip, loads);
      const changed = booking.status !== "CANCELLED" && booking.status !== "EXPIRED";
      if (changed) {
        tx.update(bookingRef, { status: "CANCELLED", updatedAtMillis: now });
      }
      tx.update(tripRef, {
        segmentLoads: loads,
        status: statusForReconciledLoads(trip, loads),
        updatedAtMillis: now,
      });
      return {
        changed,
        driverUsername: debugDriverUsername,
        tripTitle: cleanText(trip.title, 180),
        seats: Number(booking.seats || 0),
      };
    });
    await appendPublicDebugEvent({
      driverUsername: debugDriverUsername,
      event: "PUBLIC_RESERVATION_CANCELLED",
      source: "server",
      tripToken: token,
      screen: "trip",
      statusCode: 200,
    }).catch(() => {});
    await appendPublicDebugEvent({
      driverUsername: debugDriverUsername,
      event: "PUBLIC_SEATS_UPDATED",
      source: "server",
      tripToken: token,
      screen: "trip",
      statusCode: 200,
    }).catch(() => {});
    if (result.changed) {
      await sendDriverBookingPush({
        driverUsername: result.driverUsername || debugDriverUsername,
        event: "reservation_cancelled",
        tripToken: token,
        bookingId,
        seats: result.seats,
        tripTitle: result.tripTitle || "",
      }).catch((error) => console.error("push reservation_cancelled", error));
    }
    return json(res, 200, { cancelled: true });
  } catch (error) {
    await appendPublicDebugEvent({
      driverUsername: debugDriverUsername,
      event: "PUBLIC_RESERVATION_CANCEL_FAILED",
      source: "server",
      tripToken: token,
      screen: "trip",
      reason: error.code || "cancel_failed",
      statusCode: error.httpStatus || 400,
    }).catch(() => {});
    return fail(res, error.httpStatus || 400, error.code || "cancel_failed", error.message || "Falha ao cancelar reserva.");
  }
}

async function updatePublicBooking(req, res, token, bookingIdRaw) {
  await enforceBookingRateLimit(req);
  const bookingId = cleanText(bookingIdRaw, 120).replace(/[^A-Za-z0-9_-]/g, "");
  const cancellationToken = cleanText(req.body && req.body.cancellationToken, 120);
  if (!bookingId || !cancellationToken) return fail(res, 400, "booking_credentials_required", "Reserva e código particular são obrigatórios.");
  const suppliedHash = sha256Hex(cancellationToken);
  const tripRef = db.collection("trips").doc(token);
  const bookingRef = tripRef.collection("bookings").doc(bookingId);
  let debugDriverUsername = "";
  try {
    const result = await db.runTransaction(async (tx) => {
      const tripSnap = await tx.get(tripRef);
      if (!tripSnap.exists) throw Object.assign(new Error("Reserva não encontrada."), { httpStatus: 404, code: "booking_not_found" });
      const trip = tripSnap.data();
      debugDriverUsername = normalizeUsername(trip.driverUsername || "");
      const bookingsSnap = await tx.get(tripRef.collection("bookings"));
      const records = bookingsSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
      const previous = records.find((record) => record.id === bookingId);
      if (!previous) throw Object.assign(new Error("Reserva não encontrada."), { httpStatus: 404, code: "booking_not_found" });
      if (!safeEqual(suppliedHash, previous.cancellationHash || "")) throw Object.assign(new Error("Código particular inválido."), { httpStatus: 401, code: "invalid_cancel_token" });
      if (previous.status === "CANCELLED" || previous.status === "EXPIRED") throw Object.assign(new Error("Esta reserva não pode mais ser alterada."), { httpStatus: 409, code: "booking_inactive" });

      const passengerName = cleanText(req.body && req.body.passengerName, 120) || previous.passengerName;
      const passengerContact = req.body && req.body.passengerContact
        ? normalizeBrazilWhatsapp(req.body.passengerContact)
        : previous.passengerContact;
      const boardingStopId = cleanText(req.body && req.body.boardingStopId, 80) || previous.boardingStopId;
      const dropoffStopId = cleanText(req.body && req.body.dropoffStopId, 80) || previous.dropoffStopId;
      const seats = req.body && req.body.seats != null ? Number(req.body.seats) : Number(previous.seats || 0);
      if (!passengerName) throw Object.assign(new Error("Informe seu nome."), { httpStatus: 400, code: "passenger_name_required" });
      if (!Number.isInteger(seats) || seats < 1 || seats > 999) throw Object.assign(new Error("Quantidade de lugares inválida."), { httpStatus: 400, code: "invalid_seats" });
      const { fromIndex, toIndex } = bookingSegmentRange(trip, boardingStopId, dropoffStopId);
      const farePerSeatCents = (trip.stops || []).slice(fromIndex, toIndex).reduce((sum, stop) => sum + Math.max(0, Number(stop.priceToNextCents || 0)), 0);
      const totalFareCents = farePerSeatCents * seats;
      const now = Date.now();
      const updated = {
        ...previous,
        passengerName,
        passengerContact,
        boardingStopId,
        dropoffStopId,
        seats,
        farePerSeatCents,
        totalFareCents,
        updatedAtMillis: now,
      };
      const candidateRecords = records.map((record) => record.id === bookingId ? updated : record);
      const loads = reconciledSegmentLoads(trip, candidateRecords, now);
      assertNoOverbooking(trip, loads);
      const updatedPersisted = { ...updated };
      delete updatedPersisted.id;
      tx.set(bookingRef, updatedPersisted, { merge: true });
      tx.update(tripRef, {
        segmentLoads: loads,
        status: statusForReconciledLoads(trip, loads),
        updatedAtMillis: now,
      });
      return {
        booking: updated,
        availableSeats: availableForSegmentRange(trip, loads, fromIndex, toIndex),
        driverUsername: debugDriverUsername,
        tripTitle: cleanText(trip.title, 180),
      };
    });

    await appendPublicDebugEvent({
      driverUsername: result.driverUsername || debugDriverUsername,
      event: "PUBLIC_RESERVATION_CHANGED",
      source: "server",
      tripToken: token,
      screen: "trip",
      statusCode: 200,
      seats: result.booking.seats,
    }).catch(() => {});
    await appendPublicDebugEvent({
      driverUsername: result.driverUsername || debugDriverUsername,
      event: "PUBLIC_SEATS_UPDATED",
      source: "server",
      tripToken: token,
      screen: "trip",
      statusCode: 200,
      seats: result.booking.seats,
    }).catch(() => {});
    await sendDriverBookingPush({
      driverUsername: result.driverUsername || debugDriverUsername,
      event: "reservation_changed",
      tripToken: token,
      bookingId,
      seats: result.booking.seats,
      tripTitle: result.tripTitle || "",
    }).catch((error) => console.error("push reservation_changed", error));
    return json(res, 200, {
      bookingId,
      availableSeats: result.availableSeats,
      farePerSeatCents: Number(result.booking.farePerSeatCents || 0),
      totalFareCents: Number(result.booking.totalFareCents || 0),
      changed: true,
    });
  } catch (error) {
    return fail(res, error.httpStatus || 400, error.code || "booking_update_failed", error.message || "Falha ao alterar reserva.");
  }
}

async function upsertDriverCapacityBooking(req, res, token, bookingIdRaw) {
  const driver = await requireDriver(req, res);
  if (!driver) return;
  const bookingId = cleanText(bookingIdRaw, 120).replace(/[^A-Za-z0-9_-]/g, "");
  if (!bookingId) return fail(res, 400, "invalid_booking_id", "Identificador de reserva inválido.");
  const tripRef = db.collection("trips").doc(token);
  const bookingRef = tripRef.collection("bookings").doc(bookingId);
  try {
    const result = await db.runTransaction(async (tx) => {
      const tripSnap = await tx.get(tripRef);
      if (!tripSnap.exists) throw Object.assign(new Error("Viagem não encontrada."), { httpStatus: 404, code: "trip_not_found" });
      const trip = tripSnap.data();
      if (trip.driverUsername && trip.driverUsername !== driver.username) {
        throw Object.assign(new Error("Viagem pertence a outro motorista."), { httpStatus: 403, code: "trip_owner_mismatch" });
      }
      const bookingsSnap = await tx.get(tripRef.collection("bookings"));
      const records = bookingsSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
      const previous = records.find((record) => record.id === bookingId) || null;
      if (previous && (previous.source === "ROTA_CERTA" || previous.cancellationHash)) {
        throw Object.assign(new Error("Reserva pública do Rota Certa não pode ser sobrescrita por conciliação externa."), { httpStatus: 409, code: "protected_booking" });
      }
      const normalized = normalizeDriverCapacityBooking(req.body || {}, trip, bookingId, previous);
      const candidateRecords = previous
        ? records.map((record) => record.id === bookingId ? normalized : record)
        : [...records, normalized];
      const loads = reconciledSegmentLoads(trip, candidateRecords);
      assertNoOverbooking(trip, loads);
      const range = capacityAvailabilityRange(trip, loads);
      const normalizedPersisted = { ...normalized };
      delete normalizedPersisted.id;
      tx.set(bookingRef, normalizedPersisted, { merge: true });
      tx.update(tripRef, {
        segmentLoads: loads,
        bookingsCount: candidateRecords.length,
        status: statusForReconciledLoads(trip, loads),
        updatedAtMillis: Date.now(),
      });
      return { booking: normalized, segmentLoads: loads, range };
    });
    return json(res, 200, {
      booking: result.booking,
      segmentLoads: result.segmentLoads,
      availableSeatsMinimum: result.range.minimum,
      availableSeatsMaximum: result.range.maximum,
    });
  } catch (error) {
    return fail(res, error.httpStatus || 400, error.code || "capacity_reconciliation_failed", error.message || "Falha ao conciliar as vagas.");
  }
}

async function listDriverBookings(req, res, token) {
  const driver = await requireDriver(req, res);
  if (!driver) return;
  const tripSnap = await db.collection("trips").doc(token).get();
  if (!tripSnap.exists) return fail(res, 404, "trip_not_found", "Viagem não encontrada.");
  const tripData = tripSnap.data();
  if (tripData.driverUsername && tripData.driverUsername !== driver.username) return fail(res, 403, "trip_owner_mismatch", "Viagem pertence a outro motorista.");
  const snapshot = await db.collection("trips").doc(token).collection("bookings").orderBy("createdAtMillis", "desc").limit(200).get();
  return json(res, 200, { bookings: snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data(), cancellationHash: undefined })) });
}

exports.tripApi = onRequest({ secrets: [driverTokenSecret], region: "southamerica-east1" }, async (req, res) => {
  if (req.method === "OPTIONS") return res.status(204).send("");
  const path = (req.path || req.url || "/").split("?")[0].replace(/\/+$/, "") || "/";
  const parts = path.split("/").filter(Boolean);
  try {
    if (req.method === "POST" && path === "/v1/public/debug/events") return await recordPublicBrowserDebugEvent(req, res);
    if (req.method === "GET" && path === "/v1/driver/public-debug") return await listDriverPublicDebugEvents(req, res);
    if (req.method === "POST" && path === "/v1/drivers/register") return await registerDriver(req, res);
    if (req.method === "POST" && path === "/v1/driver/push-tokens") return await registerDriverPushToken(req, res);
    if (req.method === "POST" && path === "/v1/driver/trips") return await createDriverTrip(req, res);
    if (req.method === "POST" && path === "/v1/driver/agenda/ensure") return await ensureDriverPublicAgenda(req, res);
    if (req.method === "POST" && path === "/v1/driver/agenda/regenerate") return await regenerateDriverPublicAgenda(req, res);
    if (parts.length === 4 && parts[0] === "v1" && parts[1] === "driver" && parts[2] === "trips" && req.method === "PUT") {
      return await updateDriverTrip(req, res, parts[3]);
    }
    if (parts.length === 6 && parts[0] === "v1" && parts[1] === "driver" && parts[2] === "trips" && parts[4] === "bookings" && req.method === "PUT") {
      return await upsertDriverCapacityBooking(req, res, parts[3], parts[5]);
    }
    if (parts.length === 5 && parts[0] === "v1" && parts[1] === "driver" && parts[2] === "trips" && parts[4] === "bookings" && req.method === "GET") {
      return await listDriverBookings(req, res, parts[3]);
    }
    if (parts.length === 6 && parts[0] === "v1" && parts[1] === "public" && parts[2] === "drivers" && parts[5] === "agenda" && req.method === "GET") {
      return await getPublicDriverAgenda(res, req, parts[3], parts[4]);
    }
    if (parts.length === 4 && parts[0] === "v1" && parts[1] === "public" && parts[2] === "trips" && req.method === "GET") {
      return await getPublicTrip(res, parts[3]);
    }
    if (parts.length === 5 && parts[0] === "v1" && parts[1] === "public" && parts[2] === "trips" && parts[4] === "bookings" && req.method === "POST") {
      return await createBooking(req, res, parts[3]);
    }
    if (parts.length === 6 && parts[0] === "v1" && parts[1] === "public" && parts[2] === "trips" && parts[4] === "bookings" && req.method === "PUT") {
      return await updatePublicBooking(req, res, parts[3], parts[5]);
    }
    if (parts.length === 7 && parts[0] === "v1" && parts[1] === "public" && parts[2] === "trips" && parts[4] === "bookings" && parts[6] === "cancel" && req.method === "POST") {
      return await cancelPublicBooking(req, res, parts[3], parts[5]);
    }
    if (path === "/v1/health" && req.method === "GET") return json(res, 200, { ok: true, service: "rota-certa-trips", version: "stage47" });
    return fail(res, 404, "not_found", "Endpoint não encontrado.");
  } catch (error) {
    console.error("tripApi", error);
    return fail(res, error.httpStatus || 500, error.code || "internal_error", error.message || "Erro interno.");
  }
});
