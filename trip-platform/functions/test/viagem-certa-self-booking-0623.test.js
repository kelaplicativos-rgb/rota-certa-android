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

test("0623 booking is reservation-native instead of WhatsApp-native", () => {
  const availability = between(shell, "function appendSegmentAvailability0580", "const AGENDA_CARD_REFRESH_KEY_0596");
  assert.match(availability, /openBooking0623\(item, segment, stops, segmentIndex\)/);
  assert.doesNotMatch(availability, /segmentWhatsappHref0584/);
  assert.match(availability, /reserve\.type = "button"/);
  assert.match(shell, /\/v1\/public\/trips\/.*\/bookings/);
  assert.match(shell, /Authorization: "Bearer " \+ passengerSessionToken0623/);
  assert.match(shell, /"Idempotency-Key": idempotencyKey/);
});

test("0623 phone verification uses Firebase OTP and backend verifies the ID token", () => {
  assert.match(html, /\/__\/firebase\/8\.10\.1\/firebase-auth\.js/);
  assert.match(html, /autocomplete="one-time-code"/);
  assert.match(shell, /firebase\.auth\(\)\.signInWithPhoneNumber/);
  assert.match(shell, /phoneConfirmation0623\.confirm\(code\)/);
  assert.match(shell, /getIdToken\(true\)/);
  assert.match(api, /const \{ getAuth \} = require\("firebase-admin\/auth"\)/);
  assert.match(api, /getAuth\(\)\.verifyIdToken\(match\[1\], true\)/);
  assert.match(api, /decoded && decoded\.phone_number/);
});

test("0623 self-registration keeps blocked passengers blocked and creates an authorized verified identity otherwise", () => {
  const exchange = between(api, "async function exchangeVerifiedPassengerPhoneSession0623", "async function invalidatePassengerSessions");
  assert.match(exchange, /PASSENGER_RESTRICTED_ACCESS_STATUSES\.has\(status\)/);
  assert.match(exchange, /status: "AUTHORIZED"/);
  assert.match(exchange, /selfVerifiedPhone0623: true/);
  assert.match(exchange, /phoneVerifiedAtMillis0623/);
  assert.match(exchange, /createPassengerSession/);
  assert.doesNotMatch(exchange, /passwordHash|passwordSalt/);
});

test("0623 public projection carries only the opaque booking identity needed by the exact segment", () => {
  const projection = between(api, "function publicTripProjection0491", "function canonicalPublicStop0411");
  assert.match(projection, /"tripId", "publicToken"/);
  assert.match(projection, /\["id", "order", "name"/);
  assert.doesNotMatch(projection, /passengerContact|passwordHash|driverToken/);
});

test("0623 card offers native sharing and keeps BlaBlaCar as secondary trust reference", () => {
  assert.match(shell, /navigator\.share/);
  assert.match(shell, /url\.searchParams\.set\("viagem", token\)/);
  assert.match(shell, /Ver anúncio na BlaBlaCar/);
  assert.match(shell, /agendaShare0623/);
  assert.match(shell, /openFullTripBooking0623/);
});

test("0623 requested booking immediately participates in the canonical capacity claim", () => {
  assert.match(api, /record\.status === "REQUESTED" \|\| record\.status === "CONFIRMED"/);
  assert.match(api, /status: "REQUESTED",\s*operationalStatus: "PENDING"/);
  assert.match(api, /eventType: "RESERVATION_REQUESTED"/);
  assert.match(api, /db\.runTransaction\(async \(tx\) =>/);
  assert.match(api, /if \(seats > available\)/);
  assert.match(api, /sendDriverBookingPush\(\{/);
});

test("0623 browser avoids persistent raw-phone storage", () => {
  assert.doesNotMatch(shell, /localStorage/);
  assert.doesNotMatch(shell, /sessionStorage\.setItem\([^\n]*(bookingPhone|passengerContact|phone)/i);
});
