"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");

function between(source, startNeedle, endNeedle) {
  const start = source.indexOf(startNeedle);
  const end = source.indexOf(endNeedle, start + startNeedle.length);
  assert.ok(start >= 0 && end > start, "missing source range: " + startNeedle);
  return source.slice(start, end);
}

function lifecycleApi() {
  const source = between(
    api,
    "const PUBLIC_AGENDA_ARRIVAL_GRACE_MILLIS_0577",
    "function publicAgendaTripVisibility0466",
  ) + "\nresult = { publicAgendaVisibleUntil0577, publicAgendaTripStillVisible0577 };";
  const context = { Date, Math, Number, Array, result: null };
  vm.runInNewContext(source, context);
  return context.result;
}

test("0581 canonical cutoff wins over stale or incomplete transport shape", () => {
  const lifecycle = lifecycleApi();
  const now = 2_000_000_000_000;
  const canonicalCutoff = now + 8 * 60 * 60 * 1000;
  const data = {
    departureAtMillis: now - 30 * 60 * 1000,
    agendaVisibleUntilMillis0581: canonicalCutoff,
    stops: [{ order: 0 }, { order: 1, plannedArrivalMillis: now - 1 }],
  };
  assert.equal(lifecycle.publicAgendaVisibleUntil0577(data), canonicalCutoff);
  assert.equal(lifecycle.publicAgendaTripStillVisible0577(data, now), true);
});

test("0581 legacy migration uses actual last stop and its latest endpoint time", () => {
  const lifecycle = lifecycleApi();
  const departure = 1_000_000;
  const lastArrival = departure + 3 * 60 * 60 * 1000;
  const lastDeparture = departure + 4 * 60 * 60 * 1000;
  const data = {
    departureAtMillis: departure,
    stops: [
      { order: 2, plannedArrivalMillis: lastArrival, plannedDepartureMillis: lastDeparture },
      { order: 0, plannedDepartureMillis: departure },
      { order: 1, plannedArrivalMillis: departure + 60 * 60 * 1000 },
    ],
  };
  assert.equal(
    lifecycle.publicAgendaVisibleUntil0577(data),
    lastDeparture + 60 * 60 * 1000,
  );
});

test("0581 public visibility does not use projection attestation as a deletion predicate", () => {
  const visibility = between(
    api,
    "function publicAgendaTripVisibility0466",
    "async function safePublicTripWithCanonicalBookings0497",
  );
  assert.match(visibility, /projectionCommitted0581 = publicProjectionCommittedCurrent0434/);
  assert.match(visibility, /AGENDA_VISIBILITY_FUTURE_TRIP/);
  assert.match(visibility, /AGENDA_VISIBILITY_ACTIVE_TRIP/);
  assert.match(visibility, /AGENDA_VISIBILITY_EXPIRED/);
  assert.doesNotMatch(
    visibility,
    /return \{ visible: false, reason: "PUBLIC_AGENDA_PROJECTION_NOT_COMMITTED" \}/,
  );
  assert.equal(
    (visibility.match(/publicAgendaTripStillVisible0577\(/g) || []).length,
    1,
    "lifecycle must be evaluated exactly once against canonical data",
  );
});

test("0581 permalink, capacity and partial-source state are absent from lifecycle predicate", () => {
  const visibility = between(
    api,
    "function publicAgendaTripVisibility0466",
    "async function safePublicTripWithCanonicalBookings0497",
  );
  assert.doesNotMatch(visibility, /blablaPublicUrl|blablaManageUrl|sourceComplete|capacityReliable|publishedSeats/);
  assert.match(visibility, /tripPublicOnline0471/);
  assert.match(visibility, /PUBLIC_AGENDA_OFFLINE_0491/);
});

test("0581 canonical cutoff is persisted and included in canonical/public projections", () => {
  const normalize = between(api, "function normalizeDriverTrip", "function isExternalBlaBlaTrip");
  const payload = between(api, "function canonicalPublicTripPayloadFromStored0434", "function canonicalSegmentVector0497");
  const serverHash = between(api, "function canonicalServerStateHash0468", "function canonicalServerProjectionPatch0468");
  assert.match(normalize, /agendaVisibleUntilMillis0581/);
  assert.match(payload, /agendaVisibleUntilMillis0581/);
  assert.match(serverHash, /agendaVisibleUntilMillis0581/);
});

test("0581 stale and replay guards remain in the existing reconcile path", () => {
  const reconcile = between(api, "async function reconcileDriverCapacitySnapshot", "async function listDriverTripSyncState0402");
  assert.match(reconcile, /entityRevision < currentEntityRevision/);
  assert.match(reconcile, /sameIdempotentMutation/);
  assert.match(reconcile, /logicalReplay: true/);
  assert.match(reconcile, /stale: true/);
});
