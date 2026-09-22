"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const shell = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0624 booking UI uses user-created 4 digit PIN with no OTP dependencies", () => {
  assert.match(html, /id="bookingPin0624"/);
  assert.match(html, /id="bookingPinConfirm0624"/);
  assert.match(html, /maxlength="4"/);
  assert.match(html, /SOLICITAR RESERVA/);
  assert.match(html, /não envia código por SMS/);
  assert.doesNotMatch(html, /\/__\/firebase\/|firebase-auth\.js|recaptchaContainer0623|bookingOtpStep0623/);
  assert.doesNotMatch(shell, /signInWithPhoneNumber|RecaptchaVerifier|phoneConfirmation0623/);
});

test("0624 browser authenticates PIN before creating canonical booking", () => {
  const auth = between(shell, "async function authenticateAndReserve0624", "function bookingIdempotencyKey0623");
  assert.match(auth, /\/v1\/public\/passenger-pin-session/);
  assert.match(auth, /\/^\\d\{4\}\$\//);
  assert.match(auth, /pin !== pinConfirmation/);
  assert.match(auth, /sessionStorage\.setItem\(passengerSessionKey0623/);
  assert.match(auth, /\/v1\/public\/trips\/.*\/bookings/);
  assert.match(auth, /Authorization: "Bearer " \+ passengerSessionToken0623/);
});

test("0624 backend stores only salted scrypt PIN digest and preserves blocked access", () => {
  const pinSession = between(api, "async function openPassengerPinSession0624", "async function retiredPassengerPhoneSession0624");
  assert.match(api, /function passengerPin0624/);
  assert.match(api, /crypto\.scryptSync\(password, salt, 64\)/);
  assert.match(pinSession, /passwordSalt: salt/);
  assert.match(pinSession, /passwordHash: passengerPasswordDigest\(pin, salt\)/);
  assert.match(pinSession, /PASSENGER_RESTRICTED_ACCESS_STATUSES\.has\(status\)/);
  assert.match(pinSession, /status === "MOVED"/);
  assert.match(pinSession, /contactVerificationStatus0624: contactVerified \? "VERIFIED_LEGACY_OTP" : "UNVERIFIED"/);
  assert.doesNotMatch(pinSession, /phoneVerifiedAtMillis0623:\s*now|selfVerifiedPhone0623:\s*true/);
});

test("0624 online PIN guessing is account-limited", () => {
  assert.match(api, /PASSENGER_PIN_MAX_FAILURES_0624 = 5/);
  assert.match(api, /PASSENGER_PIN_LOCK_MILLIS_0624 = 15 \* 60 \* 1000/);
  assert.match(api, /recordPassengerPinFailure0624/);
  assert.match(api, /code: "pin_locked"/);
  assert.match(api, /clearPassengerPinFailures0624/);
});

test("0624 first reservation still claims capacity and waits for driver", () => {
  assert.match(api, /record\.status === "REQUESTED" \|\| record\.status === "CONFIRMED"/);
  assert.match(api, /status: "REQUESTED",\s*operationalStatus: "PENDING"/);
  assert.match(api, /eventType: "RESERVATION_REQUESTED"/);
  assert.match(api, /if \(seats > available\)/);
  assert.match(api, /sendDriverBookingPush\(\{/);
});

test("0624 keeps raw phone and PIN out of persistent browser storage", () => {
  assert.doesNotMatch(shell, /localStorage/);
  assert.doesNotMatch(shell, /sessionStorage\.setItem\([^\n]*(bookingPhone|passengerContact|bookingPin|pin)/i);
});

test("0624 legacy OTP endpoint is explicitly retired", () => {
  assert.match(api, /path === "\/v1\/public\/passenger-phone-session"\) return await retiredPassengerPhoneSession0624/);
  assert.match(api, /410,\s*"phone_otp_retired"/);
  assert.doesNotMatch(api, /getAuth\(\)\.verifyIdToken/);
});
