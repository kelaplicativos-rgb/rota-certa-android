"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

test("passenger consultation is phone-based while private actions remain driver-controlled", () => {
  assert.match(html, /Informe apenas seu WhatsApp/);
  assert.doesNotMatch(html, /id="accessPassword"/);
  assert.match(api, /driverPassengerAccess/);
  assert.match(api, /requirePassengerAgendaView/);
  assert.match(api, /requirePassengerDriverAccess/);
});

test("driver administers authorized suspended and blocked access without first-login temporary password", () => {
  assert.match(api, /async function listDriverPassengers/);
  assert.match(api, /async function inviteDriverPassenger/);
  assert.match(api, /async function syncDriverPassengerDirectory/);
  assert.match(api, /"SUSPENDED"/);
  assert.match(api, /"BLOCKED"/);
  assert.match(api, /status: "AUTHORIZED"/);
  assert.match(api, /async function resetDriverPassengerPassword/);
  assert.match(api, /\/v1\/driver\/passengers\/reset-password/);
});

test("referrals require driver approval and earn configured credits once", () => {
  assert.match(api, /requestPassengerReferralInvite/);
  assert.match(api, /status: existing\.exists && existingData\.status === "BLOCKED" \? "BLOCKED" : "PENDING"/);
  assert.match(api, /firstReferrer = cleanText\(existingData\.referredByContact/);
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
