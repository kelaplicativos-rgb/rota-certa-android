"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

function physicalValidator0494() {
  const production = between(
    api,
    "function canonicalTimelinePlaceKey0494",
    "async function listDriverTripSyncState0402",
  );
  const sandbox = {};
  vm.runInNewContext(
    `
      function cleanText(value, maxLength = 1000) {
        return String(value == null ? "" : value).trim().slice(0, maxLength);
      }
      ${production}
      this.validate0494 = applyCanonicalTimelinePhysicalIssues0494;
    `,
    sandbox,
  );
  return sandbox.validate0494;
}

function stop(name, lat = null, lon = null) {
  return { name, address: name, latitude: lat, longitude: lon };
}

test("0494 physical overlap is diagnosed by canonical backend, not Timeline", () => {
  const validate = physicalValidator0494();
  const trips = validate([
    {
      canonicalTripId: "a",
      departureAtMillis: 1000,
      arrivalAtMillis: 5000,
      stops: [stop("A"), stop("B")],
      canonicalIssues: [],
    },
    {
      canonicalTripId: "b",
      departureAtMillis: 4000,
      arrivalAtMillis: 7000,
      stops: [stop("B"), stop("C")],
      canonicalIssues: [],
    },
  ]);

  assert.deepEqual(trips.map((trip) => trip.canonicalIssues.includes("PHYSICAL_CONFLICT")), [true, true]);
});

test("0494 trusted coordinate discontinuity is diagnosed without text geocoding", () => {
  const validate = physicalValidator0494();
  const trips = validate([
    {
      canonicalTripId: "a",
      departureAtMillis: 1000,
      arrivalAtMillis: 2000,
      stops: [stop("A", -23.5, -46.6), stop("B", -22.1, -45.0)],
      canonicalIssues: [],
    },
    {
      canonicalTripId: "b",
      departureAtMillis: 3000,
      arrivalAtMillis: 4000,
      stops: [stop("C", -23.7, -46.5), stop("D", -24.0, -47.0)],
      canonicalIssues: [],
    },
  ]);

  assert.equal(trips[1].canonicalIssues.includes("PROFILE_CONTINUITY"), true);
});

test("0494 missing trusted coordinates fails closed instead of inventing teleport conflict", () => {
  const validate = physicalValidator0494();
  const trips = validate([
    {
      canonicalTripId: "a",
      departureAtMillis: 1000,
      arrivalAtMillis: 2000,
      stops: [stop("A"), stop("Destino sem coordenada")],
      canonicalIssues: [],
    },
    {
      canonicalTripId: "b",
      departureAtMillis: 3000,
      arrivalAtMillis: 4000,
      stops: [stop("Outra origem sem coordenada"), stop("D")],
      canonicalIssues: [],
    },
  ]);

  assert.equal(trips[1].canonicalIssues.includes("PROFILE_CONTINUITY"), false);
});

test("0494 endpoint is an authenticated canonical projection and embeds canonical bookings", () => {
  const fn = between(api, "async function listDriverTripSyncState0402", "async function reconcileDriverAgendaSeatAllocation");

  assert.match(fn, /requireDriver\(req, res\)/);
  assert.match(fn, /timelineProjection0494/);
  assert.match(fn, /doc\.ref\.collection\("bookings"\)/);
  assert.match(fn, /canonicalTripId:/);
  assert.match(fn, /canonicalRevision:/);
  assert.match(fn, /segmentAvailableSeats:/);
  assert.match(fn, /applyCanonicalTimelinePhysicalIssues0494/);
  assert.match(fn, /source: "CANONICAL_BACKEND"/);
  assert.doesNotMatch(fn, /BlaBlaCollector/);
  assert.doesNotMatch(fn, /timeline-ext-/);
});

test("0494 operational mutations update canonical server projection atomically", () => {
  for (const name of [
    "async function mutateDriverBookingDecision",
    "async function mutateDriverPassengerOperationalStatus",
    "async function mutateProtectedBooking",
  ]) {
    const start = api.indexOf(name);
    assert.notEqual(start, -1, name + " missing");
    const next = api.indexOf("\nasync function ", start + name.length);
    const body = api.slice(start, next < 0 ? api.length : next);
    assert.match(body, /canonicalServerProjectionPatch0468/);
    assert.match(body, /writeDeliveredTripPublicationOutbox/);
  }
});
