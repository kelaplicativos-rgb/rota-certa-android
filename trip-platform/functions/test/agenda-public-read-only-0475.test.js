"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const app = fs.readFileSync(path.join(root, "trip-platform", "public", "app.js"), "utf8");
const android = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "AgendaAutomaticSyncUi0397.kt"), "utf8");
const gradle = fs.readFileSync(path.join(root, "app", "build.gradle.kts"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0475 public HTML contains only the read-only trip list surface", () => {
  assert.match(html, /id="agendaTrips"/);
  assert.match(html, /app\.js\?v=0\.1\.491/);
  assert.match(html, /id="passengerAreaLink0491"/);
  assert.match(html, /href="\/minha-area\.html"/);
  for (const forbidden of [
    "Administrador", "Administração da Agenda", "Administrar esta viagem",
    "accessGate", "privateAuth", "passengerPortal", "agendaAdmin0417", "admin-0417.js",
    "agendaVisibilityToggle0471", "type=\"password\"", "/v1/passenger/session",
  ]) assert.doesNotMatch(html, new RegExp(forbidden, "i"), forbidden);
});

test("0475 browser bundle is read-only and keeps the canonical 0473 card hierarchy", () => {
  assert.equal((app.match(/function renderAgendaCards/g) || []).length, 1);
  assert.match(app, /agendaCanonicalVisual0473/);
  assert.match(app, /agendaJourney0473/);
  assert.match(app, /agendaSegmentPassengers0489/);
  assert.match(app, /appendSegmentPassengerDots0489/);
  assert.match(app, /publicAvailabilityLabel/);
  assert.match(app, /fullFareFor/);
  assert.match(app, /PUBLIC_AGENDA_CARD_STATUSES_0469/);
  for (const forbidden of [
    "/v1/admin/", "passengerSession", "privateAuth", "requestPublicAgendaAccess",
    "agendaAdminCardCapabilities", "public-visibility", "Administrar esta viagem",
    "Reservar pelo WhatsApp", "Reservar na BlaBlaCar", "startBooking",
  ]) assert.doesNotMatch(app, new RegExp(forbidden, "i"), forbidden);
});

test("0475 public backend lists every future committed canonical profile without login or legacy admin switches", () => {
  const visibility = between(api, "function publicAgendaTripVisibility0466", "async function getPublicDriverAgenda");
  assert.doesNotMatch(visibility, /tripPublicOnline0471/);
  assert.doesNotMatch(visibility, /publicTripProfileUuids0417/);
  assert.doesNotMatch(visibility, /PUBLIC_AGENDA_PROFILE_SCOPE_EXCLUDED/);
  assert.doesNotMatch(visibility, /publicBookingEnabled !== true/);
  assert.match(visibility, /publicationTombstone/);
  assert.match(visibility, /PUBLIC_STATUSES/);
  assert.match(visibility, /PUBLIC_AGENDA_DEPARTURE_NOT_FUTURE/);
  assert.match(visibility, /publicProjectionCommittedCurrent0434/);

  const agenda = between(api, "async function getPublicDriverAgenda", "function buildAdminHomeTrip0471");
  assert.doesNotMatch(agenda, /requirePassengerAgendaView/);
  assert.doesNotMatch(agenda, /agendaAuthenticationRequired0428/);
  assert.match(agenda, /where\("driverUsername", "==", username\)/);
  assert.match(agenda, /publicAgendaTripVisibility0466/);
  assert.match(agenda, /authenticationRequired: false/);
  assert.match(agenda, /readOnly: true/);
});

test("0475 Android opens only the public Agenda and version is exact", () => {
  assert.match(android, /ABRIR AGENDA PÚBLICA/);
  assert.doesNotMatch(android, /ABRIR ÁREA ADMINISTRATIVA/);
  assert.match(android, /Agenda Pública somente leitura exibe o mesmo estado canônico/);
  assert.match(gradle, /versionCode = 5783/);
  assert.match(gradle, /versionName = "0\.1\.491"/);
});
