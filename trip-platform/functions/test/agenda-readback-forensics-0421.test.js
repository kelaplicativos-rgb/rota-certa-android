"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");

test("public readback persists logical canonical revision independently from transport revision", () => {
  assert.match(source, /schemaVersion:\s*"public-trip-v2"/);
  assert.match(source, /canonicalRevision:\s*Math\.max\(0, Number\(data\.canonicalRevision \|\| 0\)\)/);
  assert.match(source, /publicationRevision:\s*Math\.max\(0, Number\(data\.publicationRevision \|\| 0\)\)/);
  assert.match(source, /semanticPayload = \{ \.\.\.payload, publicationRevision: 0 \}/);
  assert.doesNotMatch(source, /publicationRevision: 0, blablaPublicUrl: ""/);
  assert.match(source, /publicCommittedAt0422: FieldValue\.serverTimestamp\(\)/);
  assert.match(source, /committedAt\.toMillis/);
});

test("deterministic protected-booking conflict returns bounded correlation details", () => {
  assert.match(source, /code:\s*"protected_booking_required"/);
  assert.match(source, /protectedBookingRefHash:/);
  assert.match(source, /protectedBookingPresent:/);
  assert.match(source, /protectedBookingSource:/);
  assert.match(source, /error\.details \|\| null/);
  assert.doesNotMatch(source, /protectedBookingId:\s*bookingId/);
});

test("older transport revision is not stale when the logical snapshot is identical", () => {
  const capacity = source.slice(
    source.indexOf("async function reconcileDriverCapacitySnapshot"),
    source.indexOf("async function listDriverTripSyncState0402"),
  );
  assert.match(capacity, /const sameLogicalSnapshot = deterministicRequest/);
  assert.match(capacity, /incomingLogicalRevision === currentLogicalRevision/);
  assert.match(capacity, /incomingCanonicalStateHash === currentCanonicalStateHash/);
  assert.match(capacity, /if \(staleByRevision && sameLogicalSnapshot\)/);
  assert.match(capacity, /logicalReplay:\s*true/);
  assert.match(capacity, /stale:\s*false/);
});

test("capacity publication persists stable mutation identity and idempotency", () => {
  const capacity = source.slice(
    source.indexOf("async function reconcileDriverCapacitySnapshot"),
    source.indexOf("async function listDriverTripSyncState0402"),
  );
  assert.match(capacity, /mutationId0421/);
  assert.match(capacity, /idempotencyKey0421/);
  assert.match(capacity, /publicationMutationId0421/);
  assert.match(capacity, /publicationIdempotencyKey0421/);
  assert.match(capacity, /sameIdempotentMutation/);
  assert.match(source, /mutationId:\s*cleanText\(mutationId/);
  assert.match(source, /idempotencyKey:\s*cleanText\(idempotencyKey/);
});

test("public trip create upserts by canonical identity and rejects incompatible strong-id reuse", () => {
  const create = source.slice(source.indexOf("async function createDriverTrip"), source.indexOf("async function processReferralCreditsForCompletedTrip"));
  assert.match(source, /canonicalTripId: cleanText\(raw\.canonicalTripId \|\| raw\.id/);
  assert.match(create, /sameCanonicalTrip/);
  assert.match(create, /projectionPhysicalIdentityCompatible0421/);
  assert.match(create, /code: "strong_identity_conflict"/);
  assert.match(create, /adoptedCanonicalIdentity/);
});

test("sync-state exposes both logical and transport revision spaces", () => {
  const syncState = source.slice(source.indexOf("async function listDriverTripSyncState0402"), source.indexOf("async function reconcileDriverAgendaSeatAllocation"));
  assert.match(syncState, /canonicalRevision:/);
  assert.match(syncState, /publicationRevision:/);
  assert.match(syncState, /canonicalStateHash:/);
});


test("equal transport revision only no-ops after logical state matches", () => {
  const capacity = source.slice(
    source.indexOf("async function reconcileDriverCapacitySnapshot"),
    source.indexOf("async function listDriverTripSyncState0402"),
  );
  assert.match(capacity, /if \(sameLogicalSnapshot\)[\s\S]*logicalReplay:\s*true/);
  assert.match(capacity, /publication_revision_repair_identity_mismatch/);
  assert.match(capacity, /Same mutation \+ same transport revision but stale\/missing logical projection/);
  assert.match(capacity, /\(!deterministicRequest \|\| sameLogicalSnapshot\)/);
});

test("public hash treats the observed BlaBla public URL as semantic state", () => {
  const hash = source.slice(
    source.indexOf("function canonicalPublicTripHash0411"),
    source.indexOf("async function getDriverPublicTripReadback0411"),
  );
  assert.match(hash, /publicationRevision: 0/);
  assert.doesNotMatch(hash, /blablaPublicUrl:\s*""/);
});
