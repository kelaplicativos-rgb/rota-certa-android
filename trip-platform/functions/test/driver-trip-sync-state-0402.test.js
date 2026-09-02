const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");

test("driver sync-state exposes server canonical stops and snapshot revision behind driver auth", () => {
  assert.match(source, /async function listDriverTripSyncState0402\(req, res\)/);
  assert.match(source, /const driver = await requireDriver\(req, res\)/);
  assert.match(source, /capacitySnapshotRevision: cleanText\(data\.capacitySnapshotRevision, 128\)/);
  assert.match(source, /stops: Array\.isArray\(data\.stops\) \? data\.stops : \[\]/);
  assert.match(source, /GET" && path === "\/v1\/driver\/trips\/sync-state"/);
});
