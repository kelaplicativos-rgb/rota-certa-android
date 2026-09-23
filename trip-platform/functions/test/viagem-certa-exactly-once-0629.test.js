"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const publicJs = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");
const androidApi = fs.readFileSync(
  path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripRemoteApi.kt"),
  "utf8",
);
const bookingSync = fs.readFileSync(
  path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicBookingSync0296.kt"),
  "utf8",
);
const publicAgendaSync = fs.readFileSync(
  path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicAgendaAutoSync0300.kt"),
  "utf8",
);

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0629 one booking intent keeps one idempotency key across retries in the tab", () => {
  assert.match(publicJs, /bookingIntentStorageKey0629/);
  assert.match(publicJs, /sessionStorage\.getItem\(bookingIntentStorageKey0629\)/);
  assert.match(publicJs, /sessionStorage\.setItem\(bookingIntentStorageKey0629/);
  assert.match(publicJs, /saved\.fingerprint === fingerprint/);
  assert.match(publicJs, /clientIntentId: idempotencyKey/);
  const confirm = between(publicJs, "async function confirmBooking0625", "function shareTripUrl0623");
  assert.ok((confirm.match(/submitBookingIntent0629\(idempotencyKey\)/g) || []).length >= 2);
  assert.match(confirm, /reconcileBookingIntent0629\(idempotencyKey\)/);
  assert.match(confirm, /a próxima tentativa continuará a mesma reserva/);
});

test("0629 transport ambiguity can only become success after server evidence", () => {
  const reconcile = between(publicJs, "async function reconcileBookingIntent0629", "function showBookingSuccess0629");
  assert.match(reconcile, /\/v1\/passenger\/me\/booking-intents\//);
  assert.match(reconcile, /body\?\.found === true/);
  assert.match(reconcile, /response\.status === 404/);

  const success = between(publicJs, "function showBookingSuccess0629", "async function confirmBooking0625");
  assert.match(success, /clearBookingIntent0629\(idempotencyKey\)/);
  assert.match(success, /void loadAgenda0569\(true\)/);
  assert.doesNotMatch(success, /await loadAgenda0569/);
});

test("0629 backend read-back is authenticated and tied to the deterministic booking id", () => {
  const readback = between(api, "async function getPassengerBookingIntent0629", "async function createBooking");
  assert.match(readback, /requirePassengerSession\(req, res\)/);
  assert.match(readback, /publicBookingId\(token, intentId\)/);
  assert.match(readback, /passengerSessionOwnsBooking\(session, booking\)/);
  assert.match(api, /parts\[3\] === "booking-intents"/);
  assert.match(api, /getPassengerBookingIntent0629\(req, res, parts\[4\], parts\[5\]\)/);
});

test("0629 replay wins over FULL, closed or departed checks", () => {
  const booking = between(api, "async function createBooking", "async function updatePublicBooking");
  const exactReplay = booking.indexOf("const existingAttempt = await tx.get(bookingRef)");
  const semanticReplay = booking.indexOf("const semanticExisting0629 = equivalentActivePassengerBooking0629");
  const mutableFullCheck = booking.indexOf('if (trip.status === "FULL")');
  assert.ok(exactReplay >= 0);
  assert.ok(semanticReplay > exactReplay);
  assert.ok(mutableFullCheck > semanticReplay);
  assert.doesNotMatch(booking, /if \(authTripData\.status === "FULL"\)/);
  assert.match(booking, /clientIntentId: idempotencyKey/);
  assert.match(booking, /semanticReplay: result\.semanticReplay === true/);
});

test("0629 semantic replay prevents a second active booking for the same passenger, route and seats", () => {
  const helper = between(api, "function equivalentActivePassengerBooking0629", "async function getPassengerBookingIntent0629");
  assert.match(helper, /PASSENGER_BOOKING_INACTIVE_STATUSES_0629/);
  assert.match(helper, /passengerSessionOwnsBooking\(session, record\)/);
  assert.match(helper, /boardingStopId/);
  assert.match(helper, /dropoffStopId/);
  assert.match(helper, /Number\(record && record\.seats \|\| 0\) === seats/);
});

test("0629 Android directory sync is bounded and diagnostics retain the remote cause", () => {
  assert.match(androidApi, /PASSENGER_DIRECTORY_BATCH_SIZE_0629 = 40/);
  assert.match(androidApi, /normalized\.chunked\(PASSENGER_DIRECTORY_BATCH_SIZE_0629\)/);
  assert.match(androidApi, /PassengerDirectoryBatchException0629/);
  assert.match(androidApi, /totalPassengers=\$totalPassengers/);
  assert.match(bookingSync, /PASSENGER_DIRECTORY_DEFERRED_0629/);
  assert.doesNotMatch(bookingSync, /api\.syncPassengerDirectory\(directoryProfiles0625\)/);
  assert.match(publicAgendaSync, /api\.syncPassengerDirectory\(canonicalPassengerProfiles\)/);
  assert.match(publicAgendaSync, /AgendaFailureEvidence\.describe/);
  assert.match(publicAgendaSync, /component = "PublicAgendaAutoSync0300"/);
});
