"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const activity = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripsActivity.kt"), "utf8");
const timeline = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripTimelineUi.kt"), "utf8");
const outbox = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripPublicationOutbox0387.kt"), "utf8");
const passenger = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PassengerTimelineUi.kt"), "utf8");
const autoSync = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicAgendaAutoSync0300.kt"), "utf8");
const remoteApi = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripRemoteApi.kt"), "utf8");

function bodyOf(source, name, nextName) {
  const start = source.indexOf(name);
  assert.notEqual(start, -1, `${name} missing`);
  const end = nextName ? source.indexOf(nextName, start + name.length) : source.length;
  return source.slice(start, end < 0 ? source.length : end);
}

test("capacity snapshots enforce monotonic entity revision and legacy cannot downgrade versioned state", () => {
  const fn = bodyOf(api, "async function reconcileDriverCapacitySnapshot", "async function upsertDriverCapacityBooking");
  assert.match(fn, /entityRevision < currentEntityRevision/);
  assert.match(fn, /legacyAfterVersioned/);
  assert.match(fn, /publication_revision_conflict/);
  assert.match(fn, /stale: true/);
  assert.match(fn, /publicationRevision: deterministicRequest \? entityRevision : currentEntityRevision/);
  assert.match(fn, /rawProtectedBookings/);
  assert.match(fn, /protected_snapshot_requires_revision/);
  assert.match(fn, /normalizeProtectedSnapshotBooking/);
  assert.match(fn, /ANDROID_OUTBOX_SNAPSHOT/);
});

test("driver trip tombstone and update path rejects stale or unversioned writes after versioning", () => {
  const fn = bodyOf(api, "async function updateDriverTrip", "async function getPublicTrip");
  assert.match(fn, /requestedPublicationRevision < currentPublicationRevision/);
  assert.match(fn, /!requestedVersioned && currentPublicationRevision > 0/);
  assert.match(fn, /tombstone_revision_required/);
  assert.match(fn, /publication_revision_conflict/);
  assert.match(fn, /stale: true/);
});

test("all public and driver booking mutations advance the trip publication revision", () => {
  const names = [
    "async function createBooking",
    "async function cancelPublicBooking",
    "async function updatePublicBooking",
    "async function mutateDriverBookingDecision",
    "async function mutateDriverPassengerOperationalStatus",
    "async function mutateProtectedBooking",
    "async function updatePassengerBooking",
    "async function cancelPassengerBooking",
    "async function cancelActiveBookingsForBlockedPassenger",
  ];
  for (const name of names) {
    const start = api.indexOf(name);
    assert.notEqual(start, -1, `${name} missing`);
    const next = api.indexOf("\nasync function ", start + name.length);
    const fn = api.slice(start, next < 0 ? api.length : next);
    assert.match(fn, /publicationRevision/, `${name} missing publicationRevision`);
  }
});

test("server-side mutations journal one delivered outbox event at the same entity revision", () => {
  assert.match(api, /function writeDeliveredTripPublicationOutbox/);
  assert.match(api, /db\.collection\("tripPublicationOutbox"\)/);
  assert.match(api, /status: "DELIVERED"/);
  assert.match(api, /payloadReference: immutableSourceEventId \? "tripChangeEvents\/"/);
  for (const marker of [
    "PUBLIC_BOOKING_CREATED",
    "PUBLIC_BOOKING_CANCELLED",
    "PUBLIC_BOOKING_CHANGED",
    "PASSENGER_MY_TRIPS_EDIT",
    "PASSENGER_MY_TRIPS_CANCEL",
    "PASSENGER_BLOCKED_BOOKINGS_CANCELLED",
  ]) assert.match(api, new RegExp(marker));
});

test("normal Android mutations do not depend on global Agenda revision", () => {
  const increments = activity.match(/publicAgendaSyncRevision\+\+/g) || [];
  assert.equal(increments.length, 1);
  assert.match(activity, /if \(publicAgendaSyncRevision < 0\) return@LaunchedEffect/);
  assert.match(activity, /TripMutationCoordinator0387\(activity, store\)/);
  assert.match(activity, /TENANT_SEAT_ALLOCATION_EXACT_IMPACT/);
  assert.match(activity, /fullSyncRequested=false blablaNetworkSync=false/);
});

test("durable outbox is tenant scoped idempotent retryable and rebases stale revisions", () => {
  assert.match(outbox, /tenantScope\.key\(KEY_EVENTS\)/);
  assert.match(outbox, /tenantScope\.key\(KEY_REVISIONS\)/);
  assert.match(outbox, /FAILED_RETRYABLE/);
  assert.match(outbox, /FAILED_FINAL/);
  assert.match(outbox, /SUPERSEDED/);
  assert.match(outbox, /fun rebase\(/);
  assert.match(outbox, /publicationEventId0387\(target\.tenantId, target\.canonicalTripId, nextRevision\)/);
  assert.match(outbox, /processing_lease_expired/);
});

test("protected booking state is part of the immutable versioned local snapshot and card actions are local-first", () => {
  assert.match(remoteApi, /protectedBookings: List<DriverProtectedBookingSnapshot>/);
  assert.match(autoSync, /protectedBookings = if \(entityRevision > 0L\)/);
  assert.match(autoSync, /booking\.operationalStatus\.name/);
  assert.match(autoSync, /booking\.paymentStatus\.name/);
  assert.match(autoSync, /booking\.lastDriverSelection\.trim\(\)/);
  assert.match(passenger, /mutationCoordinator\.recordLocalMutation\(/);
  assert.match(passenger, /mutationCoordinator\.drainPending\(\)/);
  assert.doesNotMatch(passenger, /TripRemoteApi\(settings\)\.updateDriverPassengerOperationalStatus\(/);
  assert.doesNotMatch(passenger, /TripRemoteApi\(settings\)\.decideDriverBooking\(/);
});

test("BlaBlaCar exact-card channel requires strong identity and uses the outbox", () => {
  const start = timeline.indexOf("onResult = { nextResponse ->");
  const end = timeline.indexOf("onChanged = onChanged", start);
  assert.ok(start >= 0 && end > start);
  const resultBlock = timeline.slice(start, end);
  assert.match(resultBlock, /exactProfileUuid/);
  assert.match(resultBlock, /exactTripId/);
  assert.match(resultBlock, /exactMatches\.size != 1/);
  assert.match(resultBlock, /recordExternalManualMutation/);
  assert.match(resultBlock, /tripMutationCoordinator\.drainPending\(\)/);
  assert.doesNotMatch(resultBlock, /PublicAgendaAutoSync0300\.syncExternalTripIncremental/);
});

test("operational Timeline clear emits tombstones and explicitly avoids BlaBlaCar mutation", () => {
  assert.match(timeline, /Remover da operação \+ Agenda/);
  assert.match(timeline, /recordTombstone\(/);
  assert.match(timeline, /recordExternalTombstone\(/);
  assert.match(timeline, /blablaMutation=false/);
  assert.match(outbox, /publicationTombstone = true/);
});
