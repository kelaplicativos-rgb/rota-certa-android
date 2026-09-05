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

test("0473 public and authenticated Home use exactly one canonical visual card surface", () => {
  const cards = between(app, "function renderAgendaCards", "async function loadTrip");
  assert.match(cards, /agendaCanonicalVisual0473/);
  assert.match(cards, /data-card-surface/);
  assert.match(cards, /canonical-trip-0473/);
  assert.match(cards, /adminManageable0471/);
  assert.match(cards, /Administrar esta viagem/);
  assert.equal((app.match(/function renderAgendaCards/g) || []).length, 1);
  assert.doesNotMatch(app, /renderAdminAgendaCards|renderPrivateTripCards|renderPublicTripCards0473/);
});

test("0473 shared card reproduces compact journey hierarchy without duplicating trip data", () => {
  const cards = between(app, "function renderAgendaCards", "async function loadTrip");
  assert.match(cards, /agendaDateLabel0473\(item\.departureAtMillis\)/);
  assert.match(cards, /agendaJourneyTime0473/);
  assert.match(cards, /agendaJourneyRail0473/);
  assert.match(cards, /agendaJourneyCity0473/);
  assert.match(cards, /agendaDurationBetween0473/);
  assert.match(cards, /startStop0473 = stops\[fromIndex\]/);
  assert.match(cards, /endStop0473 = stops\[toIndex\]/);
  assert.match(cards, /startCity0473\\.textContent = from/);
  assert.match(cards, /endCity0473\\.textContent = to/);
});

test("0473 date heading has yesterday today tomorrow and compact weekday fallback", () => {
  const helper = between(app, "function agendaDateLabel0473", "function agendaSegmentMoment0473");
  assert.match(helper, /"Ontem"/);
  assert.match(helper, /"Hoje"/);
  assert.match(helper, /"Amanhã"/);
  assert.match(helper, /"Seg\."/);
  assert.match(helper, /"Set\."/);
});

test("0473 occupancy row remains privacy-safe and identical in public and admin modes", () => {
  const cards = between(app, "function renderAgendaCards", "async function loadTrip");
  assert.match(cards, /normalizedSeatCount\(item\.confirmedPassengerSeats\)/);
  assert.match(cards, /agendaPassengerStack0473/);
  assert.match(cards, /agendaPassengerMore0473/);
  assert.doesNotMatch(cards, /passengerName|passengerPhoto|passengerWhatsapp|booking\.passenger/i);
});

test("0473 CSS preserves reference hierarchy on mobile while admin controls stay additive", () => {
  assert.match(html, /\.agendaCanonicalVisual0473/);
  assert.match(html, /\.agendaJourney0473\{display:grid/);
  assert.match(html, /\.agendaJourneyRail0473/);
  assert.match(html, /\.agendaPassengerDot0473/);
  assert.match(html, /\.agendaTripAdmin0473 .*\.agendaDate0473/);
  assert.match(html, /@media\(max-width:480px\).*agendaJourney0473/s);
  assert.match(html, /app\.js\?v=0\.1\.473/);
  assert.match(html, /admin-0417\.js\?v=0\.1\.473/);
});
