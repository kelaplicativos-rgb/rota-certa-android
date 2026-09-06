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
