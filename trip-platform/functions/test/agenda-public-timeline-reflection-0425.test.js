"use strict";

const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const test = require("node:test");

const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");

test("sync-state exposes the hash of the actual public projection", () => {
  const syncState = source.slice(
    source.indexOf("async function listDriverTripSyncState0402"),
    source.indexOf("async function reconcileDriverAgendaSeatAllocation"),
  );
  assert.match(syncState, /publicProjectionHash:\s*canonicalPublicTripHash0411/);
  assert.match(syncState, /canonicalPublicTripPayload0411\(doc\.id, data\)/);
  assert.match(syncState, /bookingsCount:/);
});

test("same capacity revision is not enough to declare public no-op", () => {
  const capacity = source.slice(
    source.indexOf("async function reconcileDriverCapacitySnapshot"),
    source.indexOf("async function listDriverTripSyncState0402"),
  );
  assert.match(capacity, /expectedPublicProjectionHash0425/);
  assert.match(capacity, /currentPublicProjectionHash0425/);
  assert.match(capacity, /publicProjectionHashMatches0425/);
  assert.match(capacity, /capacityNoOpProven0425/);
  assert.match(
    capacity,
    /capacitySnapshotRevision, 128\) === snapshotRevision[\s\S]*capacityNoOpProven0425/,
  );
});

test("versioned projection can be repaired without inventing a new logical revision", () => {
  const capacity = source.slice(
    source.indexOf("async function reconcileDriverCapacitySnapshot"),
    source.indexOf("async function listDriverTripSyncState0402"),
  );
  assert.match(capacity, /sameRevisionProjectionRepair0425/);
  assert.match(capacity, /legacyProjectionRepair0425/);
  assert.match(capacity, /logicalMetadataMatches0425/);
  assert.match(capacity, /!publicProjectionHashMatches0425/);
  assert.match(capacity, /!legacyProjectionRepair0425/);
});

test("server verifies the candidate bytes before committing a repair", () => {
  const capacity = source.slice(
    source.indexOf("async function reconcileDriverCapacitySnapshot"),
    source.indexOf("async function listDriverTripSyncState0402"),
  );
  assert.match(capacity, /nextProjectionData0425/);
  assert.match(capacity, /nextPublicProjectionHash0425/);
  assert.match(capacity, /public_projection_hash_mismatch/);
  assert.match(capacity, /canonicalPublicTripPayload0411\(token, nextProjectionData0425\)/);
});

test("canonical public stop normalization preserves explicit null optional timestamps", () => {
  const stop = source.slice(
    source.indexOf("function canonicalPublicStop0411"),
    source.indexOf("function canonicalPublicTripPayloadFromStored0434"),
  );
  assert.match(stop, /stop\.plannedArrivalMillis == null[\s\S]*\? null/);
  assert.match(stop, /stop\.plannedDepartureMillis == null[\s\S]*\? null/);
  assert.doesNotMatch(
    stop,
    /plannedArrivalMillis:\s*Number\.isFinite\(Number\(stop\.plannedArrivalMillis\)\)/,
  );
  assert.doesNotMatch(
    stop,
    /plannedDepartureMillis:\s*Number\.isFinite\(Number\(stop\.plannedDepartureMillis\)\)/,
  );
});

test("server canonical normalizer hashes explicit null stop timestamps byte for byte", () => {
  const stopStart = source.indexOf("function canonicalPublicStop0411");
  const payloadStart = source.indexOf("function canonicalPublicTripPayloadFromStored0434");
  const payloadEnd = source.indexOf("function canonicalPublicTripPayload0411");
  const hashStart = source.indexOf("function canonicalPublicTripHash0411");
  const hashEnd = source.indexOf("function privateMirrorDocumentId0434");
  assert.ok(stopStart >= 0 && payloadStart > stopStart && payloadEnd > payloadStart);
  assert.ok(hashStart >= 0 && hashEnd > hashStart);

  const context = {
    cleanText(value, max = 240) {
      return String(value || "").trim().slice(0, max);
    },
    normalizeBlaBlaPublicUrl(raw) {
      return String(raw || "");
    },
    sha256Hex(value) {
      return crypto.createHash("sha256").update(String(value), "utf8").digest("hex");
    },
  };
  vm.createContext(context);
  vm.runInContext(
    source.slice(stopStart, payloadEnd) +
      source.slice(hashStart, hashEnd) +
      ";this.normalizeProjection=canonicalPublicTripPayloadFromStored0434;" +
      "this.hashProjection=canonicalPublicTripHash0411;",
    context,
  );

  const payload = {
    schemaVersion: "public-trip-v2",
    canonicalTripId: "trip-key-0438",
    canonicalRevision: 8,
    blablaProfileUuid: "profile",
    blablaTripId: "trip",
    title: "São Paulo → São Tomé das Letras",
    departureAtMillis: 1788528600000,
    timezoneId: "America/Sao_Paulo",
    status: "PUBLISHED",
    capacity: 4,
    stops: [
      {
        id: "stop-0",
        order: 0,
        name: "São Paulo",
        address: "São Paulo",
        plannedArrivalMillis: null,
        plannedDepartureMillis: 1788528600000,
      },
      {
        id: "stop-1",
        order: 1,
        name: "São Tomé das Letras",
        address: "São Tomé das Letras",
        plannedArrivalMillis: 1788541200000,
        plannedDepartureMillis: null,
      },
    ],
    segmentLoads: [0],
    segmentPassengerLoads: [0],
    segmentBlockedLoads: [0],
    availableSeatsMinimum: 4,
    availableSeatsMaximum: 4,
    operationalAvailableSeats: 4,
    publishedSeats: 4,
    rotaCertaSeatAllocation: 0,
    publicBookingEnabled: true,
    capacityReliable: true,
    itineraryAuthoritative: true,
    publicUrl: "",
    blablaPublicUrl: "",
    publicationRevision: 8,
    canonicalStateHash: "tripstate-v1:test",
  };

  const normalized = context.normalizeProjection(payload);
  assert.equal(normalized.stops[0].plannedArrivalMillis, null);
  assert.equal(normalized.stops[1].plannedDepartureMillis, null);

  const semantic = { ...payload, publicationRevision: 0 };
  const expectedHash = "public-v2:" +
    crypto.createHash("sha256").update(JSON.stringify(semantic), "utf8").digest("hex");
  assert.equal(context.hashProjection(normalized), expectedHash);
});

test("public profile scope remains intact and independent from projection repair", () => {
  const agenda = source.slice(
    source.indexOf("async function getPublicDriverAgenda"),
    source.indexOf("async function createDriverTrip"),
  );
  assert.match(agenda, /publicTripProfileUuids0417/);
  assert.match(agenda, /publicProfileScope0417\.has\(profileUuid\)/);
});


test("public agenda renders committed canonical projection without falsely granting blue attestation", () => {
  const committed = source.slice(
    source.indexOf("function publicProjectionCommittedCurrent0434"),
    source.indexOf("async function getPublicDriverAgenda"),
  );
  assert.match(committed, /publicationTombstone === true/);
  assert.match(committed, /canonicalTripId/);
  assert.match(committed, /canonicalPublicProjection0434/);
  assert.match(committed, /publicProjectionHash0434/);
  assert.match(committed, /publicCommittedAt0422/);
  assert.match(committed, /canonicalPublicTripHash0411/);
  assert.doesNotMatch(committed, /publicAttestationState0417/);

  const attested = source.slice(
    source.indexOf("function publicProjectionAttestedCurrent0429"),
    source.indexOf("function publicProjectionCommittedCurrent0434"),
  );
  assert.match(attested, /publicAttestationState0417[^\n]+VERIFIED/);
  assert.match(attested, /publicAttestedHash0417/);

  const agenda = source.slice(
    source.indexOf("async function getPublicDriverAgenda"),
    source.indexOf("async function createDriverTrip"),
  );
  assert.match(agenda, /publicProjectionCommittedCurrent0434\(doc\.id, doc\.data\(\)\)/);
  assert.doesNotMatch(agenda, /publicProjectionAttestedCurrent0429\(doc\.id, doc\.data\(\)\)/);
});

test("public adoption never uses route or time similarity as canonical identity", () => {
  const create = source.slice(
    source.indexOf("async function createDriverTrip"),
    source.indexOf("async function processReferralCreditsForCompletedTrip"),
  );
  assert.match(create, /sameStrongIdentity/);
  assert.match(create, /sameTripKey/);
  assert.match(create, /sameCanonicalTrip/);
  assert.doesNotMatch(create, /projectionPhysicalIdentityCompatible0421/);
  assert.doesNotMatch(source, /function projectionPhysicalIdentityCompatible0421/);
});
