"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const app = fs.readFileSync(path.join(root, "trip-platform", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0475 public Home keeps exactly one canonical visual card renderer", () => {
  const cards = between(app, "function renderAgendaCards", "function renderAgenda(");
  assert.match(cards, /agendaCanonicalVisual0473/);
  assert.match(cards, /canonical-trip-0473/);
  assert.match(cards, /agendaJourney0473/);
  assert.match(cards, /agendaPassengerStack0473/);
  assert.equal((app.match(/function renderAgendaCards/g) || []).length, 1);
  assert.doesNotMatch(app, /renderAdminAgendaCards|renderPrivateTripCards|adminManageable0471/);
});

test("0475 compact journey hierarchy preserves the approved 0.1.474 visual structure", () => {
  const cards = between(app, "function renderAgendaCards", "function renderAgenda(");
  assert.match(cards, /agendaDateLabel0473\(item\.departureAtMillis\)/);
  assert.match(cards, /agendaJourneyTime0473/);
  assert.match(cards, /agendaJourneyRail0473/);
  assert.match(cards, /agendaJourneyCity0473/);
  assert.match(cards, /agendaDurationBetween0473/);
  assert.match(cards, /startCity0473\.textContent = from/);
  assert.match(cards, /endCity0473\.textContent = to/);
});

test("0475 occupancy remains privacy-safe", () => {
  const cards = between(app, "function renderAgendaCards", "function renderAgenda(");
  assert.match(cards, /normalizedSeatCount\(item\.confirmedPassengerSeats\)/);
  assert.match(cards, /agendaPassengerMore0473/);
  assert.doesNotMatch(cards, /passengerName|passengerPhoto|passengerWhatsapp|booking\.passenger/i);
});

test("0475 public HTML has canonical mobile CSS and no administrative surface", () => {
  assert.match(html, /\.agendaCanonicalVisual0473/);
  assert.match(html, /\.agendaJourney0473\{display:grid/);
  assert.match(html, /\.agendaJourneyRail0473/);
  assert.match(html, /\.agendaPassengerDot0473/);
  assert.match(html, /@media\(max-width:480px\).*agendaJourney0473/s);
  assert.match(html, /app\.js\?v=0\.1\.475/);
  assert.doesNotMatch(html, /admin-0417\.js|agendaVisibilityToggle0471|Administrar esta viagem|Minha Área/i);
});
