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


test("public agenda renders only the current attested canonical projection", () => {
  const helper = source.slice(
    source.indexOf("function publicProjectionAttestedCurrent0429"),
    source.indexOf("async function getPublicDriverAgenda"),
  );
  assert.match(helper, /publicationTombstone === true/);
  assert.match(helper, /canonicalTripId/);
  assert.match(helper, /publicAttestationState0417[^\n]+VERIFIED/);
  assert.match(helper, /publicAttestedPublicationRevision0417[^\n]+publicationRevision/);
  assert.match(helper, /publicAttestedCanonicalRevision0417[^\n]+canonicalRevision/);
  assert.match(helper, /canonicalPublicTripHash0411/);
  assert.match(helper, /publicAttestedHash0417/);

  const agenda = source.slice(
    source.indexOf("async function getPublicDriverAgenda"),
    source.indexOf("async function createDriverTrip"),
  );
  assert.match(agenda, /publicProjectionAttestedCurrent0429\(doc\.id, doc\.data\(\)\)/);
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
