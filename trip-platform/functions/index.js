"use strict";

const crypto = require("crypto");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

initializeApp();
const db = getFirestore();
const driverTokenSecret = defineSecret("ROTA_CERTA_DRIVER_TOKEN");

const PUBLIC_STATUSES = new Set(["PUBLISHED", "FULL", "STARTING", "ACTIVE"]);
const DRIVER_MUTABLE_STATUSES = new Set(["DRAFT", "PUBLISHED", "FULL", "STARTING", "ACTIVE", "COMPLETED", "CANCELLED"]);

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

function requireDriver(req, res) {
  const supplied = req.get("X-Rota-Certa-Driver-Token") || "";
  const expected = driverTokenSecret.value() || "";
  if (!safeEqual(supplied, expected)) {
    fail(res, 401, "driver_auth_required", "Autenticação do motorista inválida.");
    return false;
  }
  return true;
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
  }));
  if (stops.some((stop) => !stop.name)) throw new Error("Toda parada precisa de um nome.");
  if (new Set(stops.map((stop) => stop.id)).size !== stops.length) throw new Error("IDs de parada devem ser únicos.");
  return stops;
}

function normalizeDriverTrip(raw, previous = null) {
  const capacity = Number(raw.capacity);
  if (!Number.isInteger(capacity) || capacity < 1 || capacity > 8) throw new Error("Capacidade inválida.");
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
    notes: cleanText(raw.notes, 1200),
  };
}

function safePublicTrip(token, data) {
  return {
    tripId: token,
    publicToken: token,
    title: data.title,
    departureAtMillis: data.departureAtMillis,
    capacity: data.capacity,
    status: data.status,
    stops: data.stops,
    segmentLoads: data.segmentLoads || [],
    notes: data.notes || "",
    publicUrl: data.publicUrl || null,
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

function bookingSegmentRange(trip, boardingStopId, dropoffStopId) {
  const stops = trip.stops || [];
  const fromIndex = stops.findIndex((stop) => stop.id === boardingStopId);
  const toIndex = stops.findIndex((stop) => stop.id === dropoffStopId);
  if (fromIndex < 0 || toIndex < 0 || fromIndex >= toIndex) throw new Error("Trecho de embarque/desembarque inválido.");
  return { fromIndex, toIndex };
}

function publicUrlFor(req, token) {
  const supplied = cleanText(req.get("X-Rota-Certa-Public-Base-Url"), 500).replace(/\/$/, "");
  if (supplied.startsWith("https://")) return `${supplied}/?trip=${encodeURIComponent(token)}`;
  const proto = cleanText(req.get("x-forwarded-proto"), 12) || "https";
  const host = cleanText(req.get("x-forwarded-host") || req.get("host"), 300);
  return host ? `${proto}://${host}/?trip=${encodeURIComponent(token)}` : `/?trip=${encodeURIComponent(token)}`;
}

async function createDriverTrip(req, res) {
  if (!requireDriver(req, res)) return;
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
  const publicUrl = publicUrlFor(req, token);
  try {
    await db.runTransaction(async (tx) => {
      const existing = await tx.get(ref);
      if (existing.exists) throw Object.assign(new Error("Token público já existe."), { httpStatus: 409, code: "token_collision" });
      tx.create(ref, {
        ...normalized,
        publicToken: token,
        publicUrl,
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
  if (!requireDriver(req, res)) return;
  const ref = db.collection("trips").doc(token);
  try {
    const result = await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) throw Object.assign(new Error("Viagem não encontrada."), { httpStatus: 404, code: "trip_not_found" });
      const previous = snap.data();
      const normalized = normalizeDriverTrip(req.body || {}, previous);
      const publicUrl = previous.publicUrl || publicUrlFor(req, token);
      tx.update(ref, { ...normalized, publicUrl, updatedAtMillis: Date.now() });
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
  if (!PUBLIC_STATUSES.has(data.status)) return fail(res, 404, "trip_not_available", "Viagem não está disponível para reserva.");
  return json(res, 200, safePublicTrip(token, data));
}

async function createBooking(req, res, token) {
  await enforceBookingRateLimit(req);
  const passengerName = cleanText(req.body && req.body.passengerName, 120);
  const passengerContact = cleanText(req.body && req.body.passengerContact, 180);
  const boardingStopId = cleanText(req.body && req.body.boardingStopId, 80);
  const dropoffStopId = cleanText(req.body && req.body.dropoffStopId, 80);
  const seats = Number(req.body && req.body.seats);
  if (!passengerName) return fail(res, 400, "passenger_name_required", "Informe seu nome.");
  if (!Number.isInteger(seats) || seats < 1 || seats > 4) return fail(res, 400, "invalid_seats", "Quantidade de vagas inválida.");

  const tripRef = db.collection("trips").doc(token);
  const bookingId = crypto.randomUUID();
  const cancellationToken = crypto.randomBytes(24).toString("base64url");
  const cancellationHash = crypto.createHash("sha256").update(cancellationToken).digest("hex");
  const bookingRef = tripRef.collection("bookings").doc(bookingId);
  try {
    const result = await db.runTransaction(async (tx) => {
      const tripSnap = await tx.get(tripRef);
      if (!tripSnap.exists) throw Object.assign(new Error("Viagem não encontrada."), { httpStatus: 404, code: "trip_not_found" });
      const trip = tripSnap.data();
      if (!PUBLIC_STATUSES.has(trip.status) || trip.status === "CANCELLED" || trip.status === "COMPLETED") {
        throw Object.assign(new Error("Viagem não aceita novas reservas."), { httpStatus: 409, code: "trip_closed" });
      }
      const { fromIndex, toIndex } = bookingSegmentRange(trip, boardingStopId, dropoffStopId);
      const loads = Array.isArray(trip.segmentLoads) ? trip.segmentLoads.map(Number) : new Array(trip.stops.length - 1).fill(0);
      let available = trip.capacity;
      for (let index = fromIndex; index < toIndex; index += 1) {
        available = Math.min(available, trip.capacity - (loads[index] || 0));
      }
      if (seats > available) {
        throw Object.assign(new Error(`Somente ${Math.max(0, available)} vaga(s) disponível(is) nesse trecho.`), { httpStatus: 409, code: "insufficient_seats" });
      }
      for (let index = fromIndex; index < toIndex; index += 1) loads[index] = (loads[index] || 0) + seats;
      const globallyFull = loads.length > 0 && loads.every((load) => load >= trip.capacity);
      const now = Date.now();
      tx.create(bookingRef, {
        tripId: token,
        passengerName,
        passengerContact,
        boardingStopId,
        dropoffStopId,
        seats,
        status: "CONFIRMED",
        cancellationHash,
        createdAtMillis: now,
        updatedAtMillis: now,
      });
      tx.update(tripRef, {
        segmentLoads: loads,
        bookingsCount: FieldValue.increment(1),
        status: globallyFull ? "FULL" : (trip.status === "FULL" ? "PUBLISHED" : trip.status),
        updatedAtMillis: now,
      });
      return { availableSeats: available - seats };
    });
    return json(res, 201, { bookingId, cancellationToken, availableSeats: result.availableSeats });
  } catch (error) {
    return fail(res, error.httpStatus || 400, error.code || "booking_failed", error.message || "Falha ao reservar.");
  }
}

async function cancelPublicBooking(req, res, token, bookingId) {
  const cancellationToken = cleanText(req.body && req.body.cancellationToken, 120);
  const suppliedHash = crypto.createHash("sha256").update(cancellationToken).digest("hex");
  const tripRef = db.collection("trips").doc(token);
  const bookingRef = tripRef.collection("bookings").doc(bookingId);
  try {
    await db.runTransaction(async (tx) => {
      const [tripSnap, bookingSnap] = await Promise.all([tx.get(tripRef), tx.get(bookingRef)]);
      if (!tripSnap.exists || !bookingSnap.exists) throw Object.assign(new Error("Reserva não encontrada."), { httpStatus: 404, code: "booking_not_found" });
      const trip = tripSnap.data();
      const booking = bookingSnap.data();
      if (!safeEqual(suppliedHash, booking.cancellationHash || "")) throw Object.assign(new Error("Código de cancelamento inválido."), { httpStatus: 401, code: "invalid_cancel_token" });
      if (booking.status === "CANCELLED" || booking.status === "EXPIRED") return;
      const { fromIndex, toIndex } = bookingSegmentRange(trip, booking.boardingStopId, booking.dropoffStopId);
      const loads = Array.isArray(trip.segmentLoads) ? trip.segmentLoads.map(Number) : new Array(trip.stops.length - 1).fill(0);
      for (let index = fromIndex; index < toIndex; index += 1) loads[index] = Math.max(0, (loads[index] || 0) - Number(booking.seats || 0));
      const globallyFull = loads.length > 0 && loads.every((load) => load >= trip.capacity);
      tx.update(bookingRef, { status: "CANCELLED", updatedAtMillis: Date.now() });
      tx.update(tripRef, {
        segmentLoads: loads,
        status: globallyFull ? "FULL" : (trip.status === "FULL" ? "PUBLISHED" : trip.status),
        updatedAtMillis: Date.now(),
      });
    });
    return json(res, 200, { cancelled: true });
  } catch (error) {
    return fail(res, error.httpStatus || 400, error.code || "cancel_failed", error.message || "Falha ao cancelar reserva.");
  }
}

async function listDriverBookings(req, res, token) {
  if (!requireDriver(req, res)) return;
  const tripSnap = await db.collection("trips").doc(token).get();
  if (!tripSnap.exists) return fail(res, 404, "trip_not_found", "Viagem não encontrada.");
  const snapshot = await db.collection("trips").doc(token).collection("bookings").orderBy("createdAtMillis", "desc").limit(200).get();
  return json(res, 200, { bookings: snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data(), cancellationHash: undefined })) });
}

exports.tripApi = onRequest({ secrets: [driverTokenSecret], region: "southamerica-east1" }, async (req, res) => {
  if (req.method === "OPTIONS") return res.status(204).send("");
  const path = (req.path || req.url || "/").split("?")[0].replace(/\/+$/, "") || "/";
  const parts = path.split("/").filter(Boolean);
  try {
    if (req.method === "POST" && path === "/v1/driver/trips") return await createDriverTrip(req, res);
    if (parts.length === 4 && parts[0] === "v1" && parts[1] === "driver" && parts[2] === "trips" && req.method === "PUT") {
      return await updateDriverTrip(req, res, parts[3]);
    }
    if (parts.length === 5 && parts[0] === "v1" && parts[1] === "driver" && parts[2] === "trips" && parts[4] === "bookings" && req.method === "GET") {
      return await listDriverBookings(req, res, parts[3]);
    }
    if (parts.length === 4 && parts[0] === "v1" && parts[1] === "public" && parts[2] === "trips" && req.method === "GET") {
      return await getPublicTrip(res, parts[3]);
    }
    if (parts.length === 5 && parts[0] === "v1" && parts[1] === "public" && parts[2] === "trips" && parts[4] === "bookings" && req.method === "POST") {
      return await createBooking(req, res, parts[3]);
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
