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
      blablaProfileUuid: "profile-same",
      departureAtMillis: 1000,
      arrivalAtMillis: 5000,
      stops: [stop("A"), stop("B")],
      canonicalIssues: [],
    },
    {
      canonicalTripId: "b",
      blablaProfileUuid: "profile-same",
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
      blablaProfileUuid: "profile-same",
      departureAtMillis: 1000,
      arrivalAtMillis: 2000,
      stops: [stop("A", -23.5, -46.6), stop("B", -22.1, -45.0)],
      canonicalIssues: [],
    },
    {
      canonicalTripId: "b",
      blablaProfileUuid: "profile-same",
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

test("0503 Timeline endpoint reads canonical Agenda and authenticated private mirror without collector fallback", () => {
  const fn = between(api, "async function listDriverTripSyncState0402", "async function reconcileDriverAgendaSeatAllocation");
  assert.match(fn, /requireDriver\(req, res\)/);
  assert.match(fn, /timelineProjection0494/);
  assert.match(fn, /db\.collection\("trips"\)/);
  assert.match(fn, /tripPrivateMirrors0434/);
  assert.match(fn, /privateMirrorCurrent0499/);
  assert.match(fn, /bookings0494/);
  assert.match(fn, /blablaTripId:/);
  assert.match(fn, /notes0499:/);
  assert.match(fn, /AGENDA_CANONICAL_ONLY_0503/);
  assert.match(fn, /collectorRead: false/);
  assert.match(fn, /collectorFallback: false/);
  assert.match(fn, /collectorDerivedData: false/);
  assert.doesNotMatch(fn, /BlaBlaTimelineAdapter|BlaBlaCollector|AUTOMATIC_COLLECTOR/);
});

test("0512 Timeline passenger projection is revision-bound and complete before Android accepts it", () => {
  const fn = between(api, "async function listDriverTripSyncState0402", "async function reconcileDriverAgendaSeatAllocation");
  assert.match(fn, /bookingSnapshot0512/);
  assert.match(fn, /doc\.ref\.get\(\)/);
  assert.match(fn, /expectedCanonicalRevision0512/);
  assert.match(fn, /expectedOccupancyRevision0512/);
  assert.match(fn, /REVISION_INCOMPATIBLE/);
  assert.match(fn, /PASSENGER_PROJECTION_INCOMPLETE/);
  assert.match(fn, /tripId: canonicalTripId0499/);
  assert.match(fn, /expectedBookingsCount0512/);
  assert.doesNotMatch(fn, /orderBy\("createdAtMillis"/);
});

test("0512 incomplete external identity is diagnostic only and manual canonical trips remain valid", () => {
  const fn = between(api, "async function listDriverTripSyncState0402", "async function reconcileDriverAgendaSeatAllocation");
  assert.match(fn, /hasExternalProfile0512 !== hasExternalTrip0512/);
  assert.match(fn, /EXTERNAL_IDENTITY_INCOMPLETE/);
  assert.match(fn, /canonicalTripId0499/);
  assert.doesNotMatch(fn, /if \(!hasExternalProfile0512 \|\| !hasExternalTrip0512\) return null/);
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

  assert.equal(Array.from(select(docs), (doc) => doc.id).sort().join("|"), "remote-a|remote-b");
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

  assert.equal(Array.from(select([legacy, canonical]), (doc) => doc.id).join("|"), "canonical-remote");
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

  assert.equal(Array.from(select([superseded, active]), (doc) => doc.id).join("|"), "active");
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


test("0503 Timeline canonical payload may retain BlaBla-origin fields after Agenda materialization", () => {
  const fn = between(api, "async function listDriverTripSyncState0402", "async function reconcileDriverAgendaSeatAllocation");
  assert.match(fn, /selectCanonicalTripDocuments0495/);
  assert.match(fn, /blablaProfileUuid:/);
  assert.match(fn, /blablaTripId:/);
  assert.match(fn, /blablaPublicUrl:/);
  assert.match(fn, /publishedSeats:/);
  assert.match(fn, /bookings: bookings0494/);
  assert.doesNotMatch(fn, /timelineTripHasCollectorProvenance0500/);
  assert.doesNotMatch(fn, /timelineBookingHasCollectorProvenance0500/);
});

test("0503 same transport revision collision returns stale so Android outbox can rebase", () => {
  const start = api.indexOf("if (deterministicRequest && entityRevision === currentEntityRevision");
  assert.notEqual(start, -1);
  const end = api.indexOf("const capacityNoOpProven0425", start);
  assert.ok(end > start);
  const sameRevision = api.slice(start, end);
  assert.match(sameRevision, /currentEventId !== outboxEventId/);
  assert.match(sameRevision, /stale: true/);
  assert.match(sameRevision, /entityRevision: currentEntityRevision/);
  assert.doesNotMatch(sameRevision, /publication_revision_conflict/);
});


test("0513 Timeline uses authenticated canonical private stops while public stop projection stays sanitized", () => {
  const privateStop = between(api, "function canonicalTimelinePrivateStop0513", "function canonicalPublicTripPayloadFromStored0434");
  const publicStop = between(api, "function canonicalPublicStop0411", "function canonicalTimelinePrivateStop0513");
  const timeline = between(api, "async function listDriverTripSyncState0402", "async function reconcileDriverAgendaSeatAllocation");

  assert.match(privateStop, /address: cleanText\(source\.address, 300\)/);
  assert.match(privateStop, /latitude/);
  assert.match(privateStop, /longitude/);
  assert.match(privateStop, /priceToNextCents/);
  assert.doesNotMatch(publicStop, /latitude:/);
  assert.doesNotMatch(publicStop, /longitude:/);
  assert.match(timeline, /data\.stops\.map\(canonicalTimelinePrivateStop0513\)/);
  assert.doesNotMatch(timeline, /canonicalIssues0494\.push\("PRIVATE_PROJECTION_STALE"\)/);
});

test("0513 canonical booking mutations persist private driver metadata without logging its values", () => {
  const privateMetadata = between(api, "function canonicalPrivateBookingMetadata0513", "function tripRelevantChanges");
  const protectedMutation = between(api, "async function mutateProtectedBooking", "async function updatePassengerBooking");
  const capacityMutation = between(api, "function normalizeDriverCapacityBooking", "function protectedSnapshotEventType");

  for (const field of [
    "fareMinorUnits", "fareCurrencyCode", "boardingAddress", "dropoffAddress",
    "boardingLatitude", "boardingLongitude", "dropoffLatitude", "dropoffLongitude",
  ]) {
    assert.match(privateMetadata, new RegExp(field));
  }
  assert.match(capacityMutation, /canonicalPrivateBookingMetadata0513\(raw, previous\)/);
  assert.match(capacityMutation, /\.\.\.privateMetadata0513/);
  assert.match(privateMetadata, /privateOperationalMetadata/);
  assert.match(privateMetadata, /before: "REDACTED", after: "UPDATED"/);
  assert.match(protectedMutation, /canonicalPrivateBookingMetadata0513\(req\.body \|\| \{\}, previous\)/);
  assert.match(protectedMutation, /passengerVisibleChange0513/);
  assert.match(protectedMutation, /passengerRecipients: passengerVisibleChange0513 \?/);
});

test("0513 Timeline booking payload remains direct-canonical with mirror only as fallback evidence", () => {
  const timeline = between(api, "async function listDriverTripSyncState0402", "async function reconcileDriverAgendaSeatAllocation");
  assert.match(timeline, /raw\.fareMinorUnits != null \? raw\.fareMinorUnits : privateBooking0499\.fareMinorUnits/);
  assert.match(timeline, /cleanText\(raw\.boardingAddress, 240\) \|\| cleanText\(privateBooking0499\.boardingAddress, 240\)/);
  assert.match(timeline, /raw\.boardingLatitude != null \? raw\.boardingLatitude : privateBooking0499\.boardingLatitude/);
  assert.match(timeline, /raw\.dropoffLongitude != null \? raw\.dropoffLongitude : privateBooking0499\.dropoffLongitude/);
  assert.match(timeline, /privateMirrorAvailable0499: Boolean\(privatePayload0499\)/);
  assert.match(timeline, /privateMirrorCurrent0499/);
  assert.match(timeline, /collectorRead: false/);
  assert.match(timeline, /collectorFallback: false/);
  assert.match(timeline, /collectorDerivedData: false/);
});
