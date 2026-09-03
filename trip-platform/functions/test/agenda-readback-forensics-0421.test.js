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
  assert.match(source, /semanticPayload = \{ \.\.\.payload, publicationRevision: 0, blablaPublicUrl: "" \}/);
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

test("sync-state exposes both logical and transport revision spaces", () => {
  const syncState = source.slice(source.indexOf("async function listDriverTripSyncState0402"), source.indexOf("async function reconcileDriverAgendaSeatAllocation"));
  assert.match(syncState, /canonicalRevision:/);
  assert.match(syncState, /publicationRevision:/);
  assert.match(syncState, /canonicalStateHash:/);
});
