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
  assert.match(cards, /agendaSegmentPassengers0489/);
  assert.match(cards, /appendSegmentPassengerDots0489/);
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

test("0489 per-segment occupancy remains anonymous and privacy-safe", () => {
  const cards = between(app, "function renderAgendaCards", "function renderAgenda(");
  assert.match(cards, /appendSegmentPassengerDots0489\(passengers0489, segment\.passengerSeats\)/);
  assert.match(cards, /agendaSegmentPassengers0489/);
  assert.doesNotMatch(cards, /normalizedSeatCount\(item\.confirmedPassengerSeats\)/);
  assert.doesNotMatch(cards, /passengerName|passengerPhoto|passengerWhatsapp|booking\.passenger/i);
});

test("0475 public HTML has canonical mobile CSS and no administrative surface", () => {
  assert.match(html, /\.agendaCanonicalVisual0473/);
  assert.match(html, /\.agendaJourney0473\{display:grid/);
  assert.match(html, /\.agendaJourneyRail0473/);
  assert.match(html, /\.agendaPassengerDot0473/);
  assert.match(html, /@media\(max-width:480px\).*agendaJourney0473/s);
  assert.match(html, /app\.js\?v=0\.1\.489/);
  assert.doesNotMatch(html, /admin-0417\.js|agendaVisibilityToggle0471|Administrar esta viagem|Minha Área/i);
});


test("0480 card expands inline to every canonical stop with times and public addresses", () => {
  const cards = between(app, "function agendaLongDateLabel0480", "function renderAgenda(");
  assert.match(cards, /agendaLongDateLabel0480/);
  assert.match(cards, /agendaExpandedItinerary0480/);
  assert.match(cards, /stops\.forEach\(\(stop, index\) =>/);
  assert.match(cards, /agendaStopMoment0480/);
  assert.match(cards, /stop\?\.address/);
  assert.match(cards, /agendaExpandedStopCity0480/);
  assert.match(cards, /agendaExpandedStopAddress0480/);
  assert.match(cards, /Ver paradas/);
  assert.match(cards, /Recolher trajeto/);
  assert.match(cards, /aria-expanded/);
  assert.match(cards, /event\.key !== "Enter" && event\.key !== " "/);
});

test("0480 expansion stays read-only and exposes no administrative or private action", () => {
  const cards = between(app, "function agendaLongDateLabel0480", "function renderAgenda(");
  for (const forbidden of [
    "/v1/admin/", "Administrar esta viagem", "Minha Área", "public-visibility",
    "agendaVisibilityToggle0471", "passengerWhatsapp", "privateAuth",
  ]) assert.doesNotMatch(cards, new RegExp(forbidden, "i"), forbidden);
  assert.match(html, /\.agendaTripExpanded0480 \.agendaJourney0473\{display:none\}/);
  assert.match(html, /\.agendaTripExpanded0480 \.agendaExpandedItinerary0480\{display:block\}/);
  assert.match(html, /\.agendaExpandedStopAddress0480/);
  assert.match(html, /@media\(max-width:480px\).*agendaExpandedStop0480/s);
});


test("0480 expanded card date is complete and includes the four-digit year", () => {
  const longDate = between(app, "function agendaLongDateLabel0480", "function agendaStopMoment0480");
  assert.match(longDate, /date\.getFullYear\(\)/);
  assert.match(longDate, /months\[date\.getMonth\(\)\] \+ " de " \+ date\.getFullYear\(\)/);

  const toggle = between(app, "function toggleAgendaTripDetails0480", "function renderAgendaCards");
  assert.match(toggle, /expanded \? dateNode\.dataset\.expandedLabel : dateNode\.dataset\.compactLabel/);
});


test("0481 compact card shows the year when the trip is outside the current calendar year", () => {
  const compactDateSource = between(app, "function agendaDateLabel0473", "function orderedStops");
  assert.match(compactDateSource, /date\.getFullYear\(\) !== now\.getFullYear\(\)/);
  assert.match(compactDateSource, /yearSuffix/);

  assert.match(compactDateSource, /const yearSuffix = date\.getFullYear\(\) !== now\.getFullYear\(\) \? " " \+ date\.getFullYear\(\) : "";/);
  assert.match(compactDateSource, /months\[date\.getMonth\(\)\] \+ yearSuffix/);
});
