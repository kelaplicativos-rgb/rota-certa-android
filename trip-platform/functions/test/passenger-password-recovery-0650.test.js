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

test("0717 driver password reset clears credential and sessions", () => {
  const reset = between(
    api,
    "async function resetDriverPassengerPassword",
    "async function updateDriverReferralSettings",
  );
  assert.match(reset, /passengerAccessForIdentity\(driver\.username, passengerId, passengerContact\)/);
  assert.match(reset, /passwordSalt: FieldValue\.delete\(\)/);
  assert.match(reset, /passwordHash: FieldValue\.delete\(\)/);
  assert.match(reset, /passwordClearedAtMillis0683/);
  assert.match(reset, /invalidatePassengerIdentitySessions/);
  assert.match(reset, /cleared: true/);
  assert.doesNotMatch(reset, /temporaryPassword/);
});

test("0650 reset-password route remains driver authenticated and explicit", () => {
  assert.match(
    api,
    /req\.method === "POST" && path === "\/v1\/driver\/passengers\/reset-password"\) return await resetDriverPassengerPassword\(req, res\)/,
  );
  const reset = between(
    api,
    "async function resetDriverPassengerPassword",
    "async function updateDriverReferralSettings",
  );
  assert.match(reset, /await requireDriver\(req, res\)/);
  assert.match(reset, /driver_username_required/);
});
