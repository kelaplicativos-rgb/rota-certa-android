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


function canonicalSelector0495() {
  const production = between(
    api,
    "function canonicalTripLegacyProjection0495",
    "async function convergeLegacyCanonicalTripDocuments0495",
  );
  const sandbox = {};
  vm.runInNewContext(
    `
      function cleanText(value, maxLength = 1000) {
        return String(value == null ? "" : value).trim().slice(0, maxLength);
      }
      ${production}
      this.select0495 = selectCanonicalTripDocuments0495;
    `,
    sandbox,
  );
  return sandbox.select0495;
}

function fakeTripDoc(id, data) {
  return {
    id,
    data: () => ({ ...data }),
  };
}

test("0495 same-day same-driver trips with different strong provider IDs never collapse", () => {
  const select = canonicalSelector0495();
  const profile = "profile-fixture";
  const docs = [
    fakeTripDoc("remote-a", {
      canonicalTripId: "canonical-a",
      tripKey: "key-a",
      blablaProfileUuid: profile,
      blablaTripId: "provider-trip-a",
      canonicalRevision: 5,
      departureAtMillis: Date.parse("2026-09-07T11:30:00-03:00"),
    }),
    fakeTripDoc("remote-b", {
      canonicalTripId: "canonical-b",
      tripKey: "key-b",
      blablaProfileUuid: profile,
      blablaTripId: "provider-trip-b",
      canonicalRevision: 5,
      departureAtMillis: Date.parse("2026-09-07T19:00:00-03:00"),
    }),
  ];

  assert.deepEqual(select(docs).map((doc) => doc.id).sort(), ["remote-a", "remote-b"]);
});

test("0495 canonical winner defeats timeline-ext legacy projection only by shared strong identity", () => {
  const select = canonicalSelector0495();
  const canonical = fakeTripDoc("canonical-remote", {
    canonicalTripId: "canonical-trip",
    tripKey: "strong-key",
    blablaProfileUuid: "profile",
    blablaTripId: "provider-trip",
    canonicalRevision: 7,
    publicationRevision: 9,
  });
  const legacy = fakeTripDoc("timeline-ext-old", {
    canonicalTripId: "timeline-ext-old",
    tripKey: "legacy-key",
    blablaProfileUuid: "profile",
    blablaTripId: "provider-trip",
    canonicalRevision: 99,
    publicationRevision: 99,
  });

  assert.deepEqual(select([legacy, canonical]).map((doc) => doc.id), ["canonical-remote"]);
});

test("0495 superseded higher revision cannot beat active canonical document", () => {
  const select = canonicalSelector0495();
  const active = fakeTripDoc("active", {
    canonicalTripId: "same-canonical",
    tripKey: "same-key",
    canonicalRevision: 8,
    publicationRevision: 8,
  });
  const superseded = fakeTripDoc("superseded", {
    canonicalTripId: "same-canonical",
    tripKey: "same-key",
    canonicalRevision: 100,
    publicationRevision: 100,
    legacyProjectionState0495: "SUPERSEDED",
    supersededByCanonicalTripId0495: "same-canonical",
  });

  assert.deepEqual(select([superseded, active]).map((doc) => doc.id), ["active"]);
});

test("0495 real 11:30 then 19:00 fixture has no artificial physical conflict", () => {
  const validate = physicalValidator0494();
  const profile = "same-driver-profile";
  const trips = validate([
    {
      canonicalTripId: "fixture-1130",
      blablaProfileUuid: profile,
      departureAtMillis: Date.parse("2026-09-07T11:30:00-03:00"),
      arrivalAtMillis: Date.parse("2026-09-07T16:40:00-03:00"),
      stops: [
        stop("São Paulo", -23.5505, -46.6333),
        stop("São Tomé das Letras", -21.7218, -44.9849),
      ],
      canonicalIssues: [],
    },
    {
      canonicalTripId: "fixture-1900",
      blablaProfileUuid: profile,
      departureAtMillis: Date.parse("2026-09-07T19:00:00-03:00"),
      arrivalAtMillis: Date.parse("2026-09-07T23:30:00-03:00"),
      stops: [
        stop("Três Corações", -21.696, -45.254),
        stop("Santo André", -23.6639, -46.5383),
      ],
      canonicalIssues: [],
    },
  ]);

  assert.equal(trips[0].canonicalIssues.includes("PHYSICAL_CONFLICT"), false);
  assert.equal(trips[1].canonicalIssues.includes("PHYSICAL_CONFLICT"), false);
  assert.equal(trips[1].canonicalIssues.includes("PROFILE_CONTINUITY"), false);
});

test("0495 physical overlap is scoped to the same proven profile resource", () => {
  const validate = physicalValidator0494();
  const trips = validate([
    {
      canonicalTripId: "profile-a-trip",
      blablaProfileUuid: "profile-a",
      departureAtMillis: 1_000,
      arrivalAtMillis: 5_000,
      stops: [stop("A"), stop("B")],
      canonicalIssues: [],
    },
    {
      canonicalTripId: "profile-b-trip",
      blablaProfileUuid: "profile-b",
      departureAtMillis: 4_000,
      arrivalAtMillis: 7_000,
      stops: [stop("C"), stop("D")],
      canonicalIssues: [],
    },
  ]);

  assert.equal(trips.some((trip) => trip.canonicalIssues.includes("PHYSICAL_CONFLICT")), false);
});

test("0495 shared selectors and canonical departure repair are wired into both projections", () => {
  const publicAgenda = between(api, "async function getPublicDriverAgenda", "function buildAdminHomeTrip0471");
  const timeline = between(api, "async function listDriverTripSyncState0402", "async function reconcileDriverAgendaSeatAllocation");
  const serverProjection = between(api, "function canonicalServerProjectionPatch0468", "function assertNoOperationalOverbooking");

  assert.match(publicAgenda, /selectCanonicalTripDocuments0495/);
  assert.match(timeline, /selectCanonicalTripDocuments0495/);
  assert.match(timeline, /canonicalDepartureStops0495/);
  assert.match(serverProjection, /stops: canonicalDepartureStops0495/);
  assert.doesNotMatch(publicAgenda, /groupBy.*departureAtMillis|origin.*destination.*departureAtMillis/i);
});

test("0495 legacy convergence migrates bookings and passenger indexes without route-date identity", () => {
  const migration = between(
    api,
    "function canonicalBookingIdentityKeys0495",
    "function publicAgendaTripVisibility0466",
  );
  assert.match(migration, /canonicalTripIdentityKeys0495/);
  assert.match(migration, /occupancyGroupId/);
  assert.match(migration, /sourceReference/);
  assert.match(migration, /passengerBookingIndexRef/);
  assert.match(migration, /passengerBookingIdentityIndexRef0491/);
  assert.match(migration, /canonicalCapacityPersistence/);
  assert.match(migration, /LEGACY_TRIP_SUPERSEDED/);
  assert.doesNotMatch(migration, /origin.*destination|departureAtMillis.*winner|date.*route/i);
});
