"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const migration = fs.readFileSync(path.join(__dirname, "..", "scripts", "migrate-passengers-single-store-0683.js"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0683 has one canonical passenger identity collection", () => {
  assert.match(api, /PASSENGER_STORE_COLLECTION_0683 = "passengers"/);
  assert.match(api, /PASSENGER_ACCESS_SUBCOLLECTION_0683 = "driverAccess0683"/);
  assert.doesNotMatch(api, /db\.collection\("passengerAccounts"\)/);
  assert.doesNotMatch(api, /db\.collection\("passengerDirectory0625"\)/);
  assert.doesNotMatch(api, /db\.collection\("passengerContactIndex0625"\)/);
  assert.doesNotMatch(api, /db\.collection\("driverPassengerAccess"\)/);
});

test("0683 authorization requires an explicit current-policy driver approval", () => {
  const predicate = between(api, "function passengerAccessIsAuthorized", "function passengerAccountIsActivated");
  assert.match(predicate, /PASSENGER_AUTHORIZED_ACCESS_STATUSES/);
  assert.match(predicate, /approvalPolicyVersion/);
  assert.match(predicate, /PASSENGER_ACCESS_POLICY_VERSION_0683/);
  assert.match(predicate, /approvedAtMillis/);

  const sync = between(api, "async function syncDriverPassengerDirectory", "async function invalidatePassengerIdentitySessions");
  assert.match(sync, /"LOCAL_ONLY"/);
  assert.match(sync, /wasAuthorized \? "AUTHORIZED"/);
  assert.doesNotMatch(sync, /blocked \? "BLOCKED" : "AUTHORIZED"/);

  const pin = between(api, "async function openPassengerPinSession0624", "async function retiredPassengerPhoneSession0624");
  assert.match(pin, /passengerAccessIsAuthorized\(initialAccessSnap\.data\(\)\)/);
  assert.match(pin, /passenger_invite_required/);
});

test("0683 invitation and referral approval are explicit driver actions", () => {
  assert.match(api, /async function approveDriverPassenger/);
  assert.match(api, /path === "\/v1\/driver\/passengers\/approve"/);
  const approve = between(api, "async function approvePassengerAccess0683", "async function inviteDriverPassenger");
  assert.match(approve, /approvalPolicyVersion: PASSENGER_ACCESS_POLICY_VERSION_0683/);
  assert.match(approve, /approvedAtMillis: now/);

  const referral = between(api, "async function requestPassengerReferralInvite", "async function getPassengerCredits");
  assert.match(referral, /status: "PENDING"/);
  assert.match(referral, /approvedAtMillis: 0/);
});

test("0683 driver reset clears password instead of generating a credential", () => {
  const reset = between(api, "async function resetDriverPassengerPassword", "async function updateDriverReferralSettings");
  assert.match(reset, /passwordSalt: FieldValue\.delete\(\)/);
  assert.match(reset, /passwordHash: FieldValue\.delete\(\)/);
  assert.match(reset, /passwordClearedAtMillis0683/);
  assert.match(reset, /invalidatePassengerIdentitySessions/);
  assert.match(reset, /cleared: true/);
  assert.doesNotMatch(reset, /temporaryPassword/);
  assert.doesNotMatch(api, /function temporaryPassengerPassword/);
});

test("0683 one-time migration revokes all old access and clears every old session", () => {
  assert.match(migration, /MIGRATION_ID = "passenger_single_store_0683"/);
  assert.match(migration, /already_completed/);
  assert.match(migration, /"passengerAccounts"/);
  assert.match(migration, /"passengerDirectory0625"/);
  assert.match(migration, /"passengerContactIndex0625"/);
  assert.match(migration, /"driverPassengerAccess"/);
  assert.match(migration, /"passengerSessions"/);
  assert.match(migration, /"passengerAgendaViewSessions"/);
  assert.match(migration, /"passengerPinGuards0624"/);
  assert.match(migration, /"passengerReferralCodes"/);
  assert.match(migration, /passwordHash: FieldValue\.delete\(\)/);
  assert.match(migration, /status = legacyStatus === "BLOCKED" \? "BLOCKED" : \(legacyStatus === "MOVED" \? "MOVED" : "REVOKED"\)/);
  assert.match(migration, /activeAccessRemaining/);
  assert.match(migration, /credentialsRemaining/);
});
