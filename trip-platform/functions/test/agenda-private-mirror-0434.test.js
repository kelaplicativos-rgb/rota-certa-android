"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const admin = fs.readFileSync(path.join(__dirname, "..", "agenda-admin-0417.js"), "utf8");

test("private Agenda mirror is authenticated tenant scoped and revision gated", () => {
  const write = source.slice(
    source.indexOf("async function putDriverPrivateMirror0434"),
    source.indexOf("async function readDriverPrivateMirror0434"),
  );
  assert.match(write, /requireDriver\(req, res\)/);
  assert.match(write, /tripPrivateMirrors0434/);
  assert.match(write, /private_mirror_stale_revision/);
  assert.match(write, /private_mirror_revision_hash_conflict/);
  assert.match(write, /mirrorRevision = previousMirrorRevision \+ 1/);
  assert.match(write, /canonicalRevision,/);
  assert.doesNotMatch(write, /canonicalRevision\s*\+\s*1/);
});

test("private mirror readback is independent and owner protected", () => {
  const start = source.indexOf("async function readDriverPrivateMirror0434");
  const read = source.slice(start, source.indexOf("\nasync function", start + 20));
  assert.match(read, /requireDriver\(req, res\)/);
  assert.match(read, /private_mirror_owner_mismatch/);
  assert.match(read, /privateStateHash/);
  assert.match(read, /canonicalJson/);
  assert.match(read, /persistedAtMillis/);
});

test("capacity contract persists exact canonical projection instead of reinterpreting it", () => {
  const capacity = source.slice(
    source.indexOf("async function reconcileDriverCapacitySnapshot"),
    source.indexOf("async function listDriverTripSyncState0402"),
  );
  assert.match(capacity, /expectedPublicProjectionJson0434/);
  assert.match(capacity, /incomingPublicProjection0434/);
  assert.match(capacity, /canonicalPublicProjection0434:\s*incomingPublicProjection0434/);
  assert.match(capacity, /publicProjectionRevision0434/);
  assert.match(capacity, /canonicalPublicTripHash0411\(incomingPublicProjection0434\)/);
  assert.match(capacity, /\.\.\.canonicalProjectionPersistence0434/);
  const persistenceIndex = capacity.lastIndexOf("...canonicalProjectionPersistence0434");
  const statusIndex = capacity.lastIndexOf("status,");
  assert.ok(persistenceIndex > statusIndex, "canonical projection must override server-derived public status");
});

test("visibility policy is server-side and revisioned separately", () => {
  assert.match(admin, /visibilityPolicyRevision0434/);
  assert.match(admin, /privateByDefault = \["passengerNames", "passengerContacts"\]/);
  assert.match(admin, /visibilityPolicyRevision0434 = previousRevision \+ \(policyChanged \? 1 : 0\)/);
  const updateStart = admin.indexOf("async function updateAdminPublicSettings0417");
  const updateEnd = admin.indexOf("async function updateAdminSyncSettings0417", updateStart);
  const updateVisibility = admin.slice(updateStart, updateEnd);
  assert.doesNotMatch(updateVisibility, /canonicalRevision/);

  const visibility = source.slice(
    source.indexOf("function applyPublicTripVisibility0434"),
    source.indexOf("function safePublicDriverProfile"),
  );
  assert.match(visibility, /tripAvailability/);
  assert.match(visibility, /tripStopAddresses/);
  assert.match(visibility, /public-visible-v1:/);
  assert.match(visibility, /visibilityPolicyRevision0434/);
});

test("public Agenda applies VisibilityPolicy after selecting attested canonical trips", () => {
  const agenda = source.slice(
    source.indexOf("async function getPublicDriverAgenda"),
    source.indexOf("async function createDriverTrip"),
  );
  assert.match(agenda, /publicProjectionAttestedCurrent0429/);
  assert.match(agenda, /applyPublicTripVisibility0434/);
});
