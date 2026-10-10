"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

function read(name) {
  return fs.readFileSync(path.join(__dirname, "..", name), "utf8");
}

test("authenticated polling fallback receives a 30-minute pending-job window", () => {
  assert.match(read("standalone-covers-remote-0736.js"),
    /const JOB_TTL_MILLIS_0736 = 30 \* 60 \* 1000;/);
  assert.match(read("blablacar-trip-query-remote-0737.js"),
    /const QUERY_JOB_TTL_MILLIS_0737 = 30 \* 60 \* 1000;/);
  assert.match(read("remote-health-0747.js"),
    /const JOB_TTL_MILLIS_0747 = 30 \* 60 \* 1000;/);
});

test("public capability and driver-authenticated pending checks remain isolated", () => {
  const covers = read("standalone-covers-remote-0736.js");
  const health = read("remote-health-0747.js");
  const trip = read("blablacar-trip-query-remote-0737.js");
  for (const source of [covers, health, trip]) {
    assert.match(source, /remoteAutoAccessEnabled0764/);
    assert.match(source, /expiresAtMillis/);
    assert.match(source, /resultExpiresAtMillis/);
  }
  assert.match(covers, /pendingConsentJob0758/);
  assert.match(trip, /pendingConsentTripQuery0760/);
  assert.match(health, /pendingHealthDriver0762/);
  assert.match(health, /pendingDriver0761/);
});
