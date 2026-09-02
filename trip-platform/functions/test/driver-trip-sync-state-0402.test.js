const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");

test("driver sync-state exposes server canonical stops and snapshot revision behind driver auth", () => {
  assert.match(source, /async function listDriverTripSyncState0402\(req, res\)/);
  assert.match(source, /const driver = await requireDriver\(req, res\)/);
  assert.match(source, /capacitySnapshotRevision: cleanText\(data\.capacitySnapshotRevision, 128\)/);
  assert.match(source, /canonicalStateHash: cleanText\(data\.canonicalStateHash, 160\)/);
  assert.match(source, /operationalAvailableSeats/);
  assert.match(source, /availableSeatsMinimum/);
  assert.match(source, /availableSeatsMaximum/);
  assert.match(source, /publishedSeats:/);
  assert.match(source, /rotaCertaSeatAllocation:/);
  assert.match(source, /occupancyRevision:/);
  assert.match(source, /stops: Array\.isArray\(data\.stops\) \? data\.stops : \[\]/);
  assert.match(source, /GET" && path === "\/v1\/driver\/trips\/sync-state"/);
});


test("driver trip creation adopts an existing strong BlaBlaCar identity instead of creating a parallel token", () => {
  const start = source.indexOf("async function createDriverTrip");
  const end = source.indexOf("async function updateDriverTrip", start);
  const fn = source.slice(start, end);
  assert.match(fn, /strongProfile/);
  assert.match(fn, /strongTripId/);
  assert.match(fn, /requestedTripKey/);
  assert.match(fn, /identityMatches/);
  assert.match(fn, /adoptedCanonicalIdentity/);
  assert.match(fn, /where\("driverUsername", "==", driver\.username\)/);
});
