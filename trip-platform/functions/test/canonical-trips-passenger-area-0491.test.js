"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const remoteApi = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripRemoteApi.kt"), "utf8");
const quickUi = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripQuickPassengerUi.kt"), "utf8");
const timeline = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PassengerTimelineUi.kt"), "utf8");
const autoSync = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicAgendaAutoSync0300.kt"), "utf8");
const publicHtml = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const publicApp = fs.readFileSync(path.join(root, "trip-platform", "public", "app.js"), "utf8");
const privateHtml = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.html"), "utf8");
const privateApp = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.js"), "utf8");

function between(source, start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(from >= 0, "missing start " + start);
  assert.ok(to > from, "missing end " + end);
  return source.slice(from, to);
}

test("0491 driver booking mutation preserves passenger identity and atomically advances canonical public state", () => {
  const normalize = between(api, "function normalizeDriverCapacityBooking", "function normalizeProtectedSnapshotBooking");
  assert.match(normalize, /passengerId/);
  assert.match(normalize, /operationalStatus/);
  assert.match(normalize, /paymentStatus/);

  const upsert = between(api, "async function upsertDriverCapacityBooking", "async function listDriverBookings");
  assert.match(upsert, /passengerAccessForIdentity/);
  assert.match(upsert, /movePassengerBookingIndex/);
  assert.match(upsert, /driverCapacityBookingChanges0491/);
  assert.match(upsert, /canonicalServerProjectionPatch0468/);
  assert.match(upsert, /writeDeliveredTripPublicationOutbox/);
  assert.match(upsert, /publicationRevision \|\| 0\)\) \+ 1/);
  assert.match(upsert, /changed: false/);
  assert.match(upsert, /entityRevision/);
  assert.doesNotMatch(upsert, /tx\.create\(tripRef/);
});

test("0491 Android sends canonical passengerId and waits for remote canonical booking mutation", () => {
  const request = between(remoteApi, "data class DriverBookingUpsertRequest", "data class DriverBookingUpsertResponse");
  assert.match(request, /val passengerId: String = ""/);
  assert.equal((remoteApi.match(/passengerId = booking\.passengerId/g) || []).length >= 2, true);

  assert.match(quickUi, /TripRemoteApi\(settings\)\.upsertDriverBooking/);
  assert.match(quickUi, /recordRemoteAppliedLocal/);
  assert.match(quickUi, /recordExternalManualMutation/);
  assert.match(timeline, /updateProtectedDriverBooking/);
  assert.match(timeline, /cancelProtectedDriverBooking/);
  assert.match(timeline, /upsertDriverBooking/);
});

test("0491 manual Trip already uses the same local canonical publication pipeline without BlaBlaCar dependency", () => {
  assert.match(autoSync, /filter\(Trip::isCanonicalLocalPublishSource\)/);
  const local = between(autoSync, "suspend fun syncLocalTripIncremental", "private suspend fun");
  assert.match(local, /syncPrivateAgendaMirror0434/);
  assert.match(local, /api\.publish\(publicTrip\.copy/);
  assert.match(local, /reconcileCapacitySnapshot/);
  assert.doesNotMatch(local, /require.*blablaTripId/i);
});

test("0491 canonical booking retry is a no-op revision while semantic change is one server revision", () => {
  const upsert = between(api, "async function upsertDriverCapacityBooking", "async function listDriverBookings");
  assert.match(upsert, /const changed = previous == null \|\| changes\.length > 0/);
  assert.match(upsert, /if \(!changed\)/);
  assert.match(upsert, /publicationRevision \|\| 0\)\) \+ 1/);
});


test("0491 public Agenda stays anonymous, read-only and free of internal trip identifiers", () => {
  assert.match(publicHtml, /id="passengerAreaLink0491"/);
  assert.match(publicHtml, /href="\/minha-area\.html"/);
  assert.doesNotMatch(publicHtml, /type="password"|\/v1\/admin\/|agenda-admin-0417\.js/i);
  assert.doesNotMatch(publicApp, /\/v1\/passenger\/|\/v1\/admin\//);
  assert.doesNotMatch(publicApp, /dataset\.canonicalTripId/);
  assert.match(publicApp, /authoritativeUpdatedLabel0491/);
  assert.match(publicApp, /setInterval\([\s\S]*15000/);

  const sanitizer = between(api, "function publicTripProjection0491", "function canonicalPublicStop0411");
  for (const field of ["tripId", "publicToken", "canonicalTripId", "blablaTripId", "notes"]) {
    assert.match(sanitizer, new RegExp("delete out\\." + field));
  }
  assert.match(sanitizer, /delete safe\.id/);

  const agenda = between(api, "async function getPublicDriverAgenda", "function buildAdminHomeTrip0471");
  assert.match(agenda, /publicTripProjection0491/);
  assert.match(agenda, /authenticationRequired: false/);
  assert.match(agenda, /readOnly: true/);
  assert.doesNotMatch(agenda, /requirePassengerAgendaView/);
});

test("0491 Minha Area is a separate passenger-only surface using existing authenticated APIs", () => {
  assert.match(privateHtml, /<h1>Minha Área<\/h1>/);
  assert.match(privateHtml, /Telefone\/WhatsApp/);
  assert.match(privateHtml, /type="password"/);
  assert.doesNotMatch(privateHtml, /\/admin|Administrar esta viagem|agenda-admin-0417\.js/i);

  for (const endpoint of [
    "/v1/passenger/session",
    "/v1/passenger/me",
    "/v1/passenger/me/bookings",
    "/v1/passenger/me/notifications",
    "/v1/passenger/logout",
  ]) {
    assert.match(privateApp, new RegExp(endpoint.replaceAll("/", "\\/")));
  }
  assert.match(privateApp, /headers\.Authorization = "Bearer " \+ sessionToken0491/);
  assert.match(privateApp, /sessionStorage/);
  assert.doesNotMatch(privateApp, /localStorage|sessionToken.*searchParams|\/v1\/admin\//i);
  assert.match(privateApp, /setInterval\([\s\S]*10000/);
});

test("0491 passenger history is keyed by stable passengerId while contact index remains legacy-compatible", () => {
  const index = between(api, "function passengerBookingIdentityIndexRef0491", "function passengerSessionToken");
  assert.match(index, /passengerBookingIdentityIndex0491/);
  assert.match(index, /session\.passengerId/);
  assert.match(index, /passengerBookingIndex/);

  const list = between(api, "async function listPassengerBookings", "async function createBooking");
  assert.match(list, /passengerBookingIndexEntries0491/);
  assert.match(list, /passengerRequestedDriverScope0491/);
  assert.match(list, /passengerSessionOwnsBooking/);
  assert.match(list, /passengerAccessForIdentity/);
  assert.match(list, /requestedDriverUsername0491/);

  const privateBooking = between(api, "function passengerPrivateBooking0491", "function passengerNotificationResponse0491");
  assert.doesNotMatch(privateBooking, /passengerContact\s*:|passengerId\s*:|bookingId\s*:/);
});

test("0491 passenger tenant isolation is enforced server-side for bookings and notifications", () => {
  const scope = between(api, "async function passengerRequestedDriverScope0491", "function passengerAgendaViewToken");
  assert.match(scope, /resolveDriverUsername/);
  assert.match(scope, /requirePassengerDriverAccess/);

  const list = between(api, "async function listPassengerBookings", "async function createBooking");
  assert.match(list, /tripDriver0491 !== requestedDriverUsername0491/);
  assert.match(list, /tripDriver0491 !== session\.driverScope0428/);

  const notifications = between(api, "async function listPassengerNotifications", "async function markDriverNotificationRead");
  assert.match(notifications, /passengerRequestedDriverScope0491/);
  assert.match(notifications, /passengerNotificationResponse0491/);
});

test("0491 credential handling keeps password hashes server-side, rate limits attempts and accepts explicit international E.164", () => {
  assert.match(api, /crypto\.scryptSync\(password, salt, 64\)/);
  assert.match(api, /const tokenHash = sha256Hex\(token\)/);
  assert.match(api, /if \(count >= 10\).*rate_limited/s);
  assert.match(api, /passenger_session_expired/);

  const source = between(api, "function normalizeBrazilWhatsapp", "function publicBookingIdempotencyKey");
  const { normalizeBrazilWhatsapp } = Function(source + "\nreturn { normalizeBrazilWhatsapp };")();
  assert.equal(normalizeBrazilWhatsapp("+447911123456"), "+447911123456");
  assert.equal(normalizeBrazilWhatsapp("+5511999999999"), "+5511999999999");
  assert.equal(normalizeBrazilWhatsapp("11999999999"), "+5511999999999");
  assert.throws(() => normalizeBrazilWhatsapp("+00000000"));
});


test("0491 individual public trip read is also open and never requires passenger credentials", () => {
  const publicTrip = between(api, "async function getPublicTrip", "function normalizeBrazilWhatsapp");
  assert.doesNotMatch(publicTrip, /requirePassengerAgendaView|agendaAuthenticationRequired0428/);
  assert.match(publicTrip, /sessionType: tester \? "TESTER" : "OPEN"/);
  assert.match(publicTrip, /authenticationRequired: false/);
  assert.match(publicTrip, /publicTripProjection0491/);
});
