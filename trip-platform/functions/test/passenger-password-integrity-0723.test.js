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

test("0723 password reset is identity-bound, atomic, audited and read-after-write verified", () => {
  const reset = between(api, "async function resetDriverPassengerPassword", "async function updateDriverReferralSettings");
  assert.match(reset, /passenger_identity_mismatch/);
  assert.match(reset, /db\.runTransaction/);
  assert.match(reset, /passwordSalt: FieldValue\.delete\(\)/);
  assert.match(reset, /passwordHash: FieldValue\.delete\(\)/);
  assert.match(reset, /passwordStateVersion0723/);
  assert.match(reset, /passwordClearedAtMillis0723/);
  assert.match(reset, /PASSENGER_PASSWORD_CLEARED/);
  assert.match(reset, /verifiedAccount = await accountRef\.get\(\)/);
  assert.match(reset, /!passengerAccountIsActivated\(verifiedData\)/);
  assert.match(reset, /password_clear_verification_failed/);
  assert.match(reset, /verified: true/);
  assert.doesNotMatch(reset, /temporaryPassword/);
});

test("0723 old known-device sessions are rejected by the authoritative password clear timestamp", () => {
  const requireSession = between(api, "async function requirePassengerSession", "async function logoutPassengerAccount");
  assert.match(requireSession, /passwordClearedAtMillis0723/);
  assert.match(requireSession, /passwordClearedAtMillis0683/);
  assert.match(requireSession, /Number\(data\.createdAtMillis \|\| 0\) <= passwordClearedAtMillis0723/);
  assert.match(requireSession, /password_reset_session_invalidated/);
  assert.match(requireSession, /clearPassengerKnownDeviceCookie0626\(res\)/);
});

test("0723 session deletion is mandatory before reset success is returned", () => {
  const reset = between(api, "async function resetDriverPassengerPassword", "async function updateDriverReferralSettings");
  const invalidation = reset.indexOf("invalidatePassengerIdentitySessions");
  const success = reset.indexOf("cleared: true");
  assert.ok(invalidation >= 0 && success > invalidation);
  assert.match(reset, /passenger_session_invalidation_failed/);
});
