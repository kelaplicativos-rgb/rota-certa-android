#!/usr/bin/env python3
from pathlib import Path
import sys

PATCHES = Path(sys.argv[1]).resolve()
BACKEND = PATCHES / "trip-platform/functions/index.js"


def once(old: str, new: str, label: str) -> None:
    text = BACKEND.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    BACKEND.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_between(start: str, end: str, new: str, label: str) -> None:
    text = BACKEND.read_text(encoding="utf-8")
    if text.count(start) != 1 or text.count(end) != 1:
        raise SystemExit(f"{label}: function boundaries not unique")
    begin = text.index(start)
    finish = text.index(end, begin)
    if finish <= begin:
        raise SystemExit(f"{label}: invalid function boundary order")
    BACKEND.write_text(text[:begin] + new + text[finish:], encoding="utf-8")


once(
'''const DRIVER_MUTABLE_STATUSES = new Set(["DRAFT", "PUBLISHED", "FULL", "STARTING", "ACTIVE", "COMPLETED", "CANCELLED"]);
''',
'''const DRIVER_MUTABLE_STATUSES = new Set(["DRAFT", "PUBLISHED", "FULL", "STARTING", "ACTIVE", "COMPLETED", "CANCELLED"]);
const CAPACITY_BOOKING_STATUSES = new Set(["REQUESTED", "HELD", "CONFIRMED", "CANCELLED", "EXPIRED"]);
const DRIVER_BOOKING_SOURCES = new Set(["BLABLACAR", "PRIVATE", "OTHER"]);
const CAPACITY_CLAIM_TYPES = new Set(["PASSENGER", "RESERVED_SEAT"]);
''',
"capacity reconciliation enums",
)

once(
'''function bookingSegmentRange(trip, boardingStopId, dropoffStopId) {
  const stops = trip.stops || [];
  const fromIndex = stops.findIndex((stop) => stop.id === boardingStopId);
  const toIndex = stops.findIndex((stop) => stop.id === dropoffStopId);
  if (fromIndex < 0 || toIndex < 0 || fromIndex >= toIndex) throw new Error("Trecho de embarque/desembarque inválido.");
  return { fromIndex, toIndex };
}
''',
'''function bookingSegmentRange(trip, boardingStopId, dropoffStopId) {
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
''',
"capacity reconciliation helpers",
)

replace_between(
'''async function createBooking(req, res, token) {
''',
'''async function cancelPublicBooking(req, res, token, bookingId) {
''',
'''async function createBooking(req, res, token) {
  await enforceBookingRateLimit(req);
  const passengerName = cleanText(req.body && req.body.passengerName, 120);
  const passengerContact = cleanText(req.body && req.body.passengerContact, 180);
  const boardingStopId = cleanText(req.body && req.body.boardingStopId, 80);
  const dropoffStopId = cleanText(req.body && req.body.dropoffStopId, 80);
  const seats = Number(req.body && req.body.seats);
  if (!passengerName) return fail(res, 400, "passenger_name_required", "Informe seu nome.");
  if (!Number.isInteger(seats) || seats < 1 || seats > 999) return fail(res, 400, "invalid_seats", "Quantidade de lugares inválida.");

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
      const bookingsSnap = await tx.get(tripRef.collection("bookings"));
      const existing = bookingsSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
      const { fromIndex, toIndex } = bookingSegmentRange(trip, boardingStopId, dropoffStopId);
      const currentLoads = reconciledSegmentLoads(trip, existing);
      const available = availableForSegmentRange(trip, currentLoads, fromIndex, toIndex);
      if (seats > available) {
        throw Object.assign(new Error(`Somente ${available} vaga(s) disponível(is) nesse trecho.`), { httpStatus: 409, code: "insufficient_seats" });
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
        sourceReference: bookingId,
        occupancyGroupId: null,
        cancellationHash,
        farePerSeatCents,
        totalFareCents,
        createdAtMillis: now,
        updatedAtMillis: now,
      };
      const reconciled = reconciledSegmentLoads(trip, [...existing, candidate], now);
      assertNoOverbooking(trip, reconciled);
      tx.create(bookingRef, { ...candidate, id: undefined });
      tx.update(tripRef, {
        segmentLoads: reconciled,
        bookingsCount: existing.length + 1,
        status: statusForReconciledLoads(trip, reconciled),
        updatedAtMillis: now,
      });
      return {
        availableSeats: availableForSegmentRange(trip, reconciled, fromIndex, toIndex),
        farePerSeatCents,
        totalFareCents,
      };
    });
    return json(res, 201, { bookingId, cancellationToken, availableSeats: result.availableSeats, farePerSeatCents: result.farePerSeatCents, totalFareCents: result.totalFareCents });
  } catch (error) {
    return fail(res, error.httpStatus || 400, error.code || "booking_failed", error.message || "Falha ao reservar.");
  }
}

''',
"public booking reconciliation",
)

replace_between(
'''async function cancelPublicBooking(req, res, token, bookingId) {
''',
'''async function listDriverBookings(req, res, token) {
''',
'''async function cancelPublicBooking(req, res, token, bookingId) {
  const cancellationToken = cleanText(req.body && req.body.cancellationToken, 120);
  const suppliedHash = crypto.createHash("sha256").update(cancellationToken).digest("hex");
  const tripRef = db.collection("trips").doc(token);
  const bookingRef = tripRef.collection("bookings").doc(bookingId);
  try {
    await db.runTransaction(async (tx) => {
      const tripSnap = await tx.get(tripRef);
      if (!tripSnap.exists) throw Object.assign(new Error("Reserva não encontrada."), { httpStatus: 404, code: "booking_not_found" });
      const trip = tripSnap.data();
      const bookingsSnap = await tx.get(tripRef.collection("bookings"));
      const records = bookingsSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
      const booking = records.find((record) => record.id === bookingId);
      if (!booking) throw Object.assign(new Error("Reserva não encontrada."), { httpStatus: 404, code: "booking_not_found" });
      if (!safeEqual(suppliedHash, booking.cancellationHash || "")) throw Object.assign(new Error("Código de cancelamento inválido."), { httpStatus: 401, code: "invalid_cancel_token" });
      const now = Date.now();
      const reconciledRecords = records.map((record) => record.id === bookingId ? { ...record, status: "CANCELLED", updatedAtMillis: now } : record);
      const loads = reconciledSegmentLoads(trip, reconciledRecords, now);
      assertNoOverbooking(trip, loads);
      if (booking.status !== "CANCELLED" && booking.status !== "EXPIRED") {
        tx.update(bookingRef, { status: "CANCELLED", updatedAtMillis: now });
      }
      tx.update(tripRef, {
        segmentLoads: loads,
        status: statusForReconciledLoads(trip, loads),
        updatedAtMillis: now,
      });
    });
    return json(res, 200, { cancelled: true });
  } catch (error) {
    return fail(res, error.httpStatus || 400, error.code || "cancel_failed", error.message || "Falha ao cancelar reserva.");
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
      tx.set(bookingRef, { ...normalized, id: undefined }, { merge: true });
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

''',
"cancellation and driver capacity reconciliation",
)

once(
'''    if (parts.length === 5 && parts[0] === "v1" && parts[1] === "driver" && parts[2] === "trips" && parts[4] === "bookings" && req.method === "GET") {
      return await listDriverBookings(req, res, parts[3]);
    }
''',
'''    if (parts.length === 6 && parts[0] === "v1" && parts[1] === "driver" && parts[2] === "trips" && parts[4] === "bookings" && req.method === "PUT") {
      return await upsertDriverCapacityBooking(req, res, parts[3], parts[5]);
    }
    if (parts.length === 5 && parts[0] === "v1" && parts[1] === "driver" && parts[2] === "trips" && parts[4] === "bookings" && req.method === "GET") {
      return await listDriverBookings(req, res, parts[3]);
    }
''',
"driver capacity reconciliation route",
)

print("stage47_unified_capacity_backend_r4_step2=PASS recompute_all_claims=true transaction_query=true private_blablacar_shared_capacity=true duplicate_group=max overbooking_guard=true public_booking_protected=true")
