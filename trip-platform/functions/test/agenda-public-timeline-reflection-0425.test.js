"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
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
