"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const shell = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");

test("0623 OTP implementation is retired by the 0624 PIN flow", () => {
  assert.doesNotMatch(html, /firebase-auth\.js|recaptchaContainer0623|bookingOtp0623|Código recebido por SMS/);
  assert.doesNotMatch(shell, /signInWithPhoneNumber|RecaptchaVerifier|phoneConfirmation0623/);
  assert.match(api, /phone_otp_retired/);
  assert.match(api, /passenger-pin-session/);
});

test("reservation remains native and transactional after OTP retirement", () => {
  assert.match(shell, /\/v1\/public\/trips\/.*\/bookings/);
  assert.match(shell, /Authorization: "Bearer " \+ passengerSessionToken0623/);
  assert.match(api, /status: "REQUESTED",\s*operationalStatus: "PENDING"/);
  assert.match(api, /eventType: "RESERVATION_REQUESTED"/);
});
