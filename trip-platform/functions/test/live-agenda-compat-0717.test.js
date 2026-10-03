"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.resolve(__dirname, "..", "..", "..");
const firebase = JSON.parse(fs.readFileSync(path.join(root, "firebase.json"), "utf8"));
const indexSource = fs.readFileSync(path.join(root, "functions", "index.js"), "utf8");

test("0717 keeps all public agenda aliases wired to the same tripApi", () => {
  const rewrites = firebase.hosting && Array.isArray(firebase.hosting.rewrites)
    ? firebase.hosting.rewrites
    : [];
  const bySource = new Map(rewrites.filter((item) => item && item.source).map((item) => [item.source, item]));
  for (const source of ["/api/agenda/**", "/api/v1/agenda/**", "/v1/**"]) {
    assert.ok(bySource.has(source), `missing hosting rewrite ${source}`);
    assert.equal(bySource.get(source).function.functionId, "tripApi");
    assert.equal(bySource.get(source).function.region, "southamerica-east1");
  }

  assert.match(
    indexSource,
    /parts\.length === 3 && parts\[0\] === "api" && parts\[1\] === "agenda"/,
  );
  assert.match(
    indexSource,
    /parts\.length === 4 && parts\[0\] === "api" && parts\[1\] === "v1" && parts\[2\] === "agenda"/,
  );
  assert.match(
    indexSource,
    /parts\.length === 4 && parts\[0\] === "v1" && parts\[1\] === "public" && parts\[2\] === "agenda-feed"/,
  );
  assert.match(indexSource, /const feedPart0701 = parts\.length === 3 \? parts\[2\] : parts\[3\];/);
});

test("0717 preserves a single canonical live-agenda feed implementation", () => {
  assert.equal((indexSource.match(/getLiveAgendaFeed0701\(/g) || []).length >= 1, true);
  assert.equal(indexSource.includes("createLiveAgendaFeed0701"), true);
});
