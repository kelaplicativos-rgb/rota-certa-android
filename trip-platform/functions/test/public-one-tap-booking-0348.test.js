"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

function block(source, start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(from >= 0, `missing block start: ${start}`);
  assert.ok(to > from, `missing block end: ${end}`);
  return source.slice(from, to);
}

test("authenticated passenger sees real seats before the direct reservation is submitted", () => {
  const quick = block(web, "function startQuickReservation", "function refreshTripAvailabilitySummary");
  const reserve = block(web, "async function reserve()", "async function undoQuickBooking");
  assert.match(html, /id="startBooking"[^>]*>RESERVAR</);
  assert.match(html, /id="name" type="hidden"/);
  assert.match(html, /id="contact" type="hidden"/);
  assert.doesNotMatch(html, /<label>Seu nome\s*<input id="name"/);
  assert.doesNotMatch(html, /<label>Seu WhatsApp\s*<input id="contact"/);
  assert.match(quick, /return openTripSeatPicker\(auto\)/);
  assert.match(reserve, /body: JSON\.stringify\(\{ \.\.\.bookingPayload, idempotencyKey \}\)/);
  assert.doesNotMatch(reserve, /showOnly\("review"\)/);
});

test("unauthenticated seat-confirmed intent is persisted and resumes after private authentication", () => {
  const confirm = block(web, "function confirmSeatPicker", "function stopMatchesSearch");
  const afterAuth = block(web, "async function continueAfterAuthentication", "async function loginAccessGate");
  assert.match(confirm, /persistPendingBookingIntent\(pendingBooking\)/);
  assert.match(confirm, /showPrivateAuthGate\("trip", "reserve"\)/);
  assert.match(afterAuth, /resume === "reserve"/);
  assert.match(afterAuth, /restorePendingBookingIntent\(\)/);
  assert.match(afterAuth, /return reserve\(\)/);
});

test("booking identity comes from authenticated passenger and canonical passenger access", () => {
  const create = block(api, "async function createBooking", "async function cancelPublicBooking");
  assert.match(create, /const passengerContact = session\.passengerContact/);
  assert.match(create, /authorized\.access && authorized\.access\.displayName/);
  assert.match(create, /const passengerId = cleanText\(session\.passengerId \|\| authorized\.access\.passengerId/);
  assert.match(create, /publicBookingFingerprint\(\{ passengerId, boardingStopId, dropoffStopId, seats \}\)/);
  assert.doesNotMatch(create, /if \(!passengerName\) return fail\(res, 400, "passenger_name_required"/);
});

test("blocked passenger check and atomic last-seat protection remain in the existing backend transaction", () => {
  const access = block(api, "async function requirePassengerDriverAccess", "function passengerAgendaViewToken");
  const create = block(api, "async function createBooking", "async function cancelPublicBooking");
  assert.match(access, /PASSENGER_RESTRICTED_ACCESS_STATUSES/);
  assert.match(access, /passengerAccessForIdentity/);
  assert.match(create, /requirePassengerDriverAccess/);
  assert.match(create, /db\.runTransaction/);
  assert.match(create, /availableForSegmentRange/);
  assert.match(create, /insufficient_seats/);
  assert.match(create, /assertNoOverbooking/);
  assert.match(create, /segmentLoads: reconciled/);
});

test("double tap converges through client in-flight guard and stable server idempotency", () => {
  const reserve = block(web, "async function reserve()", "async function undoQuickBooking");
  assert.match(reserve, /bookingRequestInFlight/);
  assert.match(reserve, /Idempotency-Key/);
  assert.match(web, /rotacerta-booking-intent-/);
  assert.match(api, /publicBookingId\(token, idempotencyKey\)/);
  assert.match(api, /existingAttempt\.exists/);
});

test("success is shown only after an OK response and capacity is updated immediately", () => {
  const reserve = block(web, "async function reserve()", "async function undoQuickBooking");
  assert.match(reserve, /if \(!response\.ok\) \{/);
  assert.match(reserve, /failure\.availableSeats/);
  assert.match(reserve, /recomputeLoadsAfterBooking/);
  assert.match(reserve, /"✓ RESERVA SOLICITADA"/);
  assert.match(reserve, /PUBLIC_SEATS_UPDATED/);
  assert.match(api, /sendDriverBookingPush/);
});

test("undo uses the existing cancellation endpoint and releases capacity transactionally", () => {
  const undo = block(web, "async function undoQuickBooking", "function cancellationStorageKey");
  const cancel = block(api, "async function cancelPublicBooking", "async function updatePublicBooking");
  assert.match(undo, /\/cancel/);
  assert.match(undo, /localStorage\.removeItem\(\`rotacerta-booking-intent-/);
  assert.match(cancel, /status: "CANCELLED"/);
  assert.match(cancel, /reconciledSegmentLoads/);
  assert.match(cancel, /segmentLoads: loads/);
  assert.match(cancel, /sendDriverBookingPush/);
});

test("agenda card exposes direct RESERVAR action while preserving details navigation", () => {
  const cards = block(web, "function renderAgendaCards", "async function loadTrip");
  assert.match(cards, /reserveParams\.set\("reservar", "1"\)/);
  assert.match(cards, /action\.textContent = "RESERVAR"/);
  assert.match(cards, /details\.textContent = "Ver detalhes"/);
  assert.match(cards, /fareForTripSegment/);
});
