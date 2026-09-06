"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(root, "trip-platform", "functions", "index.js"), "utf8");
const app = fs.readFileSync(path.join(root, "trip-platform", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

function compileSegmentCapacity() {
  const source = between(api, "function recordOccupiesCapacity", "function operationalSeatLimit");
  return Function(
    'function cleanText(value, max) { return String(value || "").trim().slice(0, max || 9999); }\n' +
    source +
    "\nreturn { reconciledSegmentCapacity };"
  )();
}

function compilePublicSegments() {
  const source = between(api, "function canonicalSegmentAvailableSeats0484", "function normalizeDriverCapacityBooking");
  return Function(
    'function cleanText(value, max) { return String(value || "").trim().slice(0, max || 9999); }\n' +
    source +
    "\nreturn { canonicalSegmentAvailableSeats0484, publicSegmentAvailability0484, canonicalPublicCapacityState0485, capacityAvailabilityRange };"
  )();
}

function compileLabels() {
  const source = between(app, "function publicSegmentRows0484", "function fullFareFor");
  return Function(source + "\nreturn { publicSegmentRows0484, segmentAvailabilityLabel0484 };")();
}

test("0484 canonical occupancy consumes only the segments actually crossed", () => {
  const { reconciledSegmentCapacity } = compileSegmentCapacity();
  const trip = {
    capacity: 2,
    stops: [
      { id: "tc", name: "Três Corações" },
      { id: "pa", name: "Pouso Alegre" },
      { id: "ex", name: "Extrema" },
      { id: "at", name: "Atibaia" },
      { id: "sa", name: "Santo André" },
    ],
  };
  const state = reconciledSegmentCapacity(trip, [{
    id: "opaque-booking",
    passengerId: "opaque-passenger",
    boardingStopId: "pa",
    dropoffStopId: "sa",
    seats: 1,
    status: "CONFIRMED",
    capacityClaimType: "PASSENGER",
  }], 0);

  assert.deepEqual(state.loads, [0, 1, 1, 1]);
  assert.deepEqual(state.passengerLoads, [0, 1, 1, 1]);
  assert.deepEqual(state.blockedLoads, [0, 0, 0, 0]);
});

test("0484 public projection turns canonical segment loads into exact anonymous availability rows", () => {
  const { publicSegmentAvailability0484 } = compilePublicSegments();
  const rows = publicSegmentAvailability0484({
    capacity: 2,
    stops: [
      { id: "1", name: "Três Corações" },
      { id: "2", name: "Pouso Alegre" },
      { id: "3", name: "Extrema" },
      { id: "4", name: "Atibaia" },
      { id: "5", name: "Santo André" },
    ],
  }, [1, 2, 1, 0], true, [1, 1, 1, 0]);

  assert.deepEqual(rows, [
    { from: "Três Corações", to: "Pouso Alegre", availableSeats: 1, passengerSeats: 1 },
    { from: "Pouso Alegre", to: "Extrema", availableSeats: 0, passengerSeats: 1 },
    { from: "Extrema", to: "Atibaia", availableSeats: 1, passengerSeats: 1 },
    { from: "Atibaia", to: "Santo André", availableSeats: 2, passengerSeats: 0 },
  ]);
});

test("0484 public segment projection fails closed on unreliable or incomplete canonical shape", () => {
  const { publicSegmentAvailability0484 } = compilePublicSegments();
  const trip = { capacity: 4, stops: [{ name: "A" }, { name: "B" }, { name: "C" }] };
  assert.deepEqual(publicSegmentAvailability0484(trip, [1, 1], false), []);
  assert.deepEqual(publicSegmentAvailability0484(trip, [1], true), []);
});

test("0485 a single full segment does not mark the whole trip FULL", () => {
  const { canonicalPublicCapacityState0485 } = compilePublicSegments();
  const state = canonicalPublicCapacityState0485({
    capacity: 2,
    status: "FULL",
    stops: [
      { name: "Três Corações" },
      { name: "Pouso Alegre" },
      { name: "Extrema" },
      { name: "Atibaia" },
      { name: "Santo André" },
    ],
    segmentLoads: [1, 2, 1, 0],
    capacityReliable: true,
    operationalOverbookingSeats: 0,
  });

  assert.equal(state.status, "PUBLISHED");
  assert.equal(state.isFull, false);
  assert.equal(state.availableSeatsMinimum, 0);
  assert.equal(state.availableSeatsMaximum, 2);
});

test("0485 all segments full still marks the whole trip FULL", () => {
  const { canonicalPublicCapacityState0485 } = compilePublicSegments();
  const state = canonicalPublicCapacityState0485({
    capacity: 2,
    status: "PUBLISHED",
    stops: [{ name: "A" }, { name: "B" }, { name: "C" }],
    segmentLoads: [2, 2],
    capacityReliable: true,
    operationalOverbookingSeats: 0,
  });

  assert.equal(state.status, "FULL");
  assert.equal(state.isFull, true);
  assert.equal(state.availableSeatsMinimum, 0);
  assert.equal(state.availableSeatsMaximum, 0);
});

test("0485 real overbooking stays fail-closed as FULL", () => {
  const { canonicalPublicCapacityState0485 } = compilePublicSegments();
  const state = canonicalPublicCapacityState0485({
    capacity: 2,
    status: "PUBLISHED",
    stops: [{ name: "A" }, { name: "B" }, { name: "C" }],
    segmentLoads: [3, 0],
    capacityReliable: true,
    operationalOverbookingSeats: 1,
  });

  assert.equal(state.status, "FULL");
  assert.equal(state.isFull, true);
  assert.equal(state.overbookingSeats, 1);
});

test("0484 public API exposes backend-resolved segmentAvailability on canonical and legacy-safe paths", () => {
  const canonical = between(api, "function safePublicTripFromCanonical0434", "function safePublicTrip(token");
  const fallback = between(api, "function safePublicTrip(token", "function canonicalPublicStop0411");
  const canonicalStateCall = between(canonical, "const capacityState0485 =", "const passengerLoads");
  const fallbackStateCall = between(fallback, "const capacityState0485 =", "const itineraryAuthoritative");
  assert.match(canonicalStateCall, /capacityReliable: reliable/);
  assert.doesNotMatch(canonicalStateCall, /capacityReliable: capacityState0485\.reliable/);
  assert.match(fallbackStateCall, /capacityReliable,/);
  assert.doesNotMatch(fallbackStateCall, /capacityReliable: capacityState0485\.reliable/);
  assert.doesNotMatch(canonical, /\bavailableMaximum\b(?!\s*:)/);
  assert.match(canonical, /publicSegmentAvailability0484/);
  assert.match(canonical, /canonicalPublicCapacityState0485/);
  assert.match(canonical, /status: capacityState0485\.status/);
  assert.match(canonical, /segmentAvailability,/);
  assert.match(fallback, /publicSegmentAvailability0484/);
  assert.match(fallback, /canonicalPublicCapacityState0485/);
  assert.match(fallback, /status: capacityState0485\.status/);
  assert.match(fallback, /segmentAvailability,/);
});

test("0484 browser renders server rows without recomputing capacity from passenger totals or segment loads", () => {
  const helpers = between(app, "function publicSegmentRows0484", "function fullFareFor");
  const cards = between(app, "function renderAgendaCards", "function renderAgenda(");
  assert.match(helpers, /item\.segmentAvailability/);
  assert.match(helpers, /segment\.passengerSeats/);
  assert.doesNotMatch(helpers, /segmentLoads|segmentPassengerLoads|confirmedPassengerSeats|capacity\s*-/);
  assert.match(cards, /"Vagas por trecho"/);
  assert.match(cards, /publicSegmentRows0484\(item\)/);
  assert.match(cards, /segmentAvailabilityLabel0484\(segment\.availableSeats\)/);
  assert.match(cards, /appendSegmentPassengerDots0489\(passengers0489, segment\.passengerSeats\)/);
  const section = cards.slice(cards.indexOf("const segmentAvailability0484"), cards.indexOf("const bottom"));
  assert.doesNotMatch(section, /passengerName|passengerId|bookingId|phone|photo|sourceReference/);
  const bottom = cards.slice(cards.indexOf("const bottom"), cards.indexOf("const summary0473"));
  assert.doesNotMatch(bottom, /confirmedPassengerSeats|passengerStack0473|passengerCount0473/);
});

test("0484 public labels are exact and privacy-safe", () => {
  const { segmentAvailabilityLabel0484 } = compileLabels();
  assert.equal(segmentAvailabilityLabel0484(0), "LOTADO");
  assert.equal(segmentAvailabilityLabel0484(1), "1 vaga");
  assert.equal(segmentAvailabilityLabel0484(2), "2 vagas");
  assert.match(html, /agendaSegmentAvailability0484/);
  assert.match(html, /agendaSegmentPassengers0489/);
  assert.match(html, /app\.js\?v=0\.1\.489/);
});
