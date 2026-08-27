"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

test("passenger access is invite-only at UI and backend", () => {
  assert.match(html, /Acesso somente por convite/);
  assert.doesNotMatch(html, /Criar acesso e entrar/);
  assert.match(api, /passenger_invite_required/);
  assert.match(api, /driverPassengerAccess/);
  assert.match(api, /requirePassengerDriverAccess/);
});

test("driver can administer invited passengers and temporary passwords", () => {
  assert.match(api, /async function listDriverPassengers/);
  assert.match(api, /async function inviteDriverPassenger/);
  assert.match(api, /async function setDriverPassengerBlocked/);
  assert.match(api, /async function resetDriverPassengerPassword/);
  assert.match(api, /temporaryPassengerPassword/);
  assert.match(api, /\/v1\/driver\/passengers\/invite/);
  assert.match(api, /\/v1\/driver\/passengers\/reset-password/);
});

test("referrals require driver approval and earn configured credits once", () => {
  assert.match(api, /requestPassengerReferralInvite/);
  assert.match(api, /status: existing\.exists && existing\.data\(\)\.status === "BLOCKED" \? "BLOCKED" : "PENDING"/);
  assert.match(api, /processReferralCreditsForCompletedTrip/);
  assert.match(api, /referralRewardGrantedAtMillis/);
  assert.match(api, /REFERRAL_EARNED/);
  assert.match(api, /referralCreditCents/);
});

test("credits are spent atomically on booking and refunded on cancellation", () => {
  assert.match(api, /creditToUseCents/);
  assert.match(api, /BOOKING_CREDIT_USED/);
  assert.match(api, /amountDueCents/);
  assert.match(api, /refundBookingCreditsIfNeeded/);
  assert.match(api, /BOOKING_CREDIT_REFUND/);
  assert.match(web, /Meus créditos|portalCreditBalance/);
  assert.match(web, /creditToUseCents/);
});

test("passenger can share a referral without granting access directly", () => {
  assert.match(html, /Indique e ganhe créditos/);
  assert.match(html, /Solicitar convite/);
  assert.match(web, /sharePassengerReferral/);
  assert.match(web, /requestReferralInvite/);
  assert.match(web, /navigator\.share/);
});
