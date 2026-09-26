"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const publicHtml = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const publicJs = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");
const privateHtml = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.html"), "utf8");
const privateJs = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.js"), "utf8");
const androidSync = fs.readFileSync(
  path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicBookingSync0296.kt"),
  "utf8",
);
const publicAgendaSync = fs.readFileSync(
  path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicAgendaAutoSync0300.kt"),
  "utf8",
);

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0625 keeps one canonical passenger directory keyed by permanent passengerId and indexed by WhatsApp", () => {
  assert.match(api, /passengerDirectory0625/);
  assert.match(api, /passengerContactIndex0625/);
  assert.match(api, /writeCanonicalPassenger0625/);
  const sync = between(api, "async function syncDriverPassengerDirectory", "async function invalidatePassengerIdentitySessions");
  assert.match(sync, /passenger_global_identity_conflict/);
  assert.match(sync, /ROTA_CERTA_DIRECTORY_SYNC_0625/);
  assert.match(sync, /writeCanonicalPassenger0625/);
  const invite = between(api, "async function inviteDriverPassenger", "async function syncDriverPassengerDirectory");
  assert.match(invite, /DRIVER_INVITE_0625/);
  const whatsapp = between(api, "async function updateDriverPassengerWhatsapp", "async function setDriverPassengerAgendaAdmin0418");
  assert.match(whatsapp, /WHATSAPP_UPDATE_0625/);
  assert.match(whatsapp, /passenger_whatsapp_global_conflict/);
  assert.match(whatsapp, /batch\.delete\(passengerContactIndexRef0625\(previousPassengerContact\)\)/);
});

test("0625 canonical passenger identities still converge remotely outside the booking hot path", () => {
  assert.match(androidSync, /PASSENGER_DIRECTORY_DEFERRED_0629/);
  assert.doesNotMatch(androidSync, /api\.syncPassengerDirectory\(directoryProfiles0625\)/);
  assert.match(publicAgendaSync, /PassengerIdentityStore\(context\)\.profiles\(\)/);
  assert.match(publicAgendaSync, /api\.syncPassengerDirectory\(canonicalPassengerProfiles\)/);
  assert.match(publicAgendaSync, /PUBLIC_AGENDA_PASSENGER_DIRECTORY_SYNCED/);
});

test("0625 known passenger creates a four-digit password on first access and reuses it later", () => {
  assert.match(api, /async function publicPassengerAccessStatus0625/);
  const session = between(api, "async function openPassengerPasswordSession0625", "function driverPassengerAccessId");
  assert.match(session, /passengerPassword0625/);
  assert.match(session, /passwordConfirmation/);
  assert.match(session, /passwordHash: passengerPasswordDigest\(password, salt\)/);
  assert.match(session, /createPassengerSession/);
  assert.match(session, /invalid_credentials/);
  assert.match(session, /Muitas tentativas de senha/);
  assert.match(api, /A senha precisa ter exatamente 4 números/);
});

test("0625 public reservation is one question per step and user-facing copy never says PIN", () => {
  for (const id of [
    "bookingContactStep0625",
    "bookingNameStep0625",
    "bookingPasswordStep0625",
    "bookingPasswordConfirmStep0625",
    "bookingSeatsStep0625",
    "bookingReviewStep0625",
  ]) {
    assert.match(publicHtml, new RegExp('id="' + id + '"'));
  }
  assert.match(publicHtml, /Crie sua senha/);
  assert.match(publicHtml, /Confirme sua senha/);
  assert.doesNotMatch(publicHtml, /PIN|bookingPin0624/);
  assert.doesNotMatch(publicJs, /PIN|passenger-pin-session|bookingPin0624/);
  assert.match(publicJs, /passenger-access\/status/);
  assert.match(publicJs, /passenger-password-session/);
  assert.match(publicJs, /showBookingStep0625/);
});

test("0625 passenger area login is field-by-field and uses one global session key", () => {
  assert.match(privateHtml, /id="entryContactStep0625"/);
  assert.match(privateHtml, /id="entryPasswordStep0625"/);
  assert.match(privateHtml, /id="entryConfirmStep0625"/);
  assert.match(privateJs, /viagemCertaPassengerSession0625/);
  assert.match(privateJs, /passenger-access\/status/);
  assert.match(privateJs, /passenger-password-session/);
  assert.doesNotMatch(privateHtml, /PIN/);
  assert.match(privateHtml, /maxlength="4"/);
});

test("0625 passenger timeline uses immutable change events plus canonical historical backfill newest first", () => {
  const timeline = between(api, "async function listPassengerTimeline0625", "async function createBooking");
  assert.match(timeline, /tripChangeEvents/);
  assert.match(timeline, /affectedPassengerIds/);
  assert.match(timeline, /passengerBookingIndexEntries0491/);
  assert.match(timeline, /historicalBackfill: true/);
  assert.match(timeline, /Number\(b\.occurredAtMillis\) - Number\(a\.occurredAtMillis\)/);
  assert.match(api, /path === "\/v1\/passenger\/me\/timeline"/);
  assert.match(privateHtml, /id="timeline0625"/);
  assert.match(privateHtml, /Os acontecimentos mais recentes aparecem primeiro/);
  assert.match(privateJs, /renderTimeline0625/);
  assert.match(privateJs, /\/v1\/passenger\/me\/timeline/);
});

test("0625 reservation still creates a transactional canonical REQUESTED capacity claim", () => {
  assert.match(api, /status: "REQUESTED",\s*operationalStatus: "PENDING"/);
  assert.match(api, /eventType: "RESERVATION_REQUESTED"/);
  assert.match(api, /if \(seats > available\)/);
  assert.match(publicJs, /"Idempotency-Key": idempotencyKey/);
  assert.match(publicJs, /credentials: "same-origin"/);
});
