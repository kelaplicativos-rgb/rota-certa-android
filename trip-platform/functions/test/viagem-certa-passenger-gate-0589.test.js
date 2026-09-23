"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const shell = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");
const privateHtml = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.html"), "utf8");
const privateJs = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.js"), "utf8");
const gradle = fs.readFileSync(path.join(root, "app", "build.gradle.kts"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0589 gate is superseded by 0625 public-read password reserve-on-demand", () => {
  assert.match(html, /id="accessGate0589" class="accessGate0589 hidden"/);
  assert.doesNotMatch(html, /id="passengerWhatsapp0589"/);
  for (const id of [
    "bookingContactStep0625",
    "bookingPasswordStep0625",
    "bookingPasswordConfirmStep0625",
    "bookingSeatsStep0625",
    "bookingReviewStep0625",
  ]) {
    assert.match(html, new RegExp('id="' + id + '"'));
  }
  assert.match(html, />SOLICITAR RESERVA</);
  assert.doesNotMatch(html, /PIN|bookingPin0624|bookingOtp0623|RECEBER CÓDIGO|firebase-auth\.js/);
});

test("0625 keeps the 0623 public-read contract while password protects reservation identity", () => {
  const agenda = between(api, "async function getPublicDriverAgenda", "async function waitPublicAgendaCanonicalChange0495");
  assert.doesNotMatch(agenda, /requirePassengerAgendaView/);
  assert.match(agenda, /identifiedAccessRequired0589: false/);
  assert.match(agenda, /PUBLIC_READ_RESERVE_ON_DEMAND_0623/);

  const changes = between(api, "async function waitPublicAgendaCanonicalChange0495", "function buildAdminHomeTrip0471");
  assert.doesNotMatch(changes, /requirePassengerAgendaView/);

  const publicTrip = between(api, "async function getPublicTrip", "function normalizeBrazilWhatsapp");
  assert.doesNotMatch(publicTrip, /requirePassengerAgendaView/);
  assert.match(publicTrip, /identifiedAccessRequired0589: false/);
});

test("private passenger data stays protected even after the public agenda is reopened", () => {
  assert.match(privateHtml, /VIAGEM CERTA/);
  assert.match(privateHtml, /<h1>Minhas viagens<\/h1>/);
  assert.match(privateJs, /headers\.Authorization = "Bearer " \+ sessionToken0491/);
  assert.match(privateJs, /\/v1\/passenger\/me\/bookings/);
  assert.doesNotMatch(privateJs, /localStorage/);
});

test("0.1.627 package metadata is explicit", () => {
  assert.match(gradle, /releaseVersionCode = 5_918/);
  assert.match(gradle, /releaseVersionName = "0\.1\.626"/);
});
