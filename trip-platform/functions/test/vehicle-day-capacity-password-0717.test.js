"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0717 backend never manufactures seats from BlaBla plus Rota Certa quotas", () => {
  const fn = between(api, "function operationalSeatLimit(trip, records = [], now = Date.now()) {", "\n}\n\nfunction reconciledOperationalSeatSummary");
  assert.match(fn, /physicalSeatCapacity/);
  assert.match(fn, /return physical/);
  assert.doesNotMatch(fn, /blablaAvailable/);
  assert.doesNotMatch(fn, /confirmedPeak/);
  assert.doesNotMatch(fn, /rotaCertaAllocated/);
  assert.doesNotMatch(api, /blablaAvailable \+ confirmedPeak \+ rotaCertaAllocated/);
});

test("0717 public projection clamps availability to physical capacity", () => {
  const safe = between(api, "function safePublicTrip(token, data) {", "\n}\n\nfunction canonicalPublicTripPayload0411");
  assert.match(safe, /physicalSeatCapacity/);
  assert.match(safe, /Math\.min\(\s*capacity/);
});

test("0717 driver reset clears credential and invalidates all passenger identity sessions", () => {
  const reset = between(api, "async function resetDriverPassengerPassword", "async function updateDriverReferralSettings");
  assert.match(reset, /passwordSalt: FieldValue\.delete\(\)/);
  assert.match(reset, /passwordHash: FieldValue\.delete\(\)/);
  assert.match(reset, /passwordFormat0625: FieldValue\.delete\(\)/);
  assert.match(reset, /pinAuthVersion0624: FieldValue\.delete\(\)/);
  assert.match(reset, /invalidatePassengerIdentitySessions/);
  assert.match(reset, /cleared: true/);
  assert.doesNotMatch(reset, /temporaryPassword/);
});
