"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(root, "trip-platform", "functions", "index.js"), "utf8");
const shell = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0580 backend derives exact anonymous availability from canonical segment load", () => {
  const source = between(api, "function canonicalSegmentAvailableSeats0484", "function canonicalPublicCapacityState0485");
  const compiled = Function(
    'function cleanText(value, max) { return String(value || "").trim().slice(0, max || 9999); }\n' +
    source +
    "\nreturn { publicSegmentAvailability0484 };"
  )();

  const rows = compiled.publicSegmentAvailability0484({
    capacity: 4,
    stops: [{ name: "A" }, { name: "B" }, { name: "C" }, { name: "D" }],
  }, [3, 4, 1], true, [3, 4, 1]);

  assert.deepEqual(rows.map((row) => ({
    from: row.from,
    to: row.to,
    availableSeats: row.availableSeats,
  })), [
    { from: "A", to: "B", availableSeats: 1 },
    { from: "B", to: "C", availableSeats: 0 },
    { from: "C", to: "D", availableSeats: 3 },
  ]);
});

test("0580 public Agenda projection strips identities contacts and stop addresses", () => {
  const projectionSource = between(api, "function publicAgendaUiTripKey0506", "function canonicalPublicStop0411");
  const compiled = Function(
    'function cleanText(value, max) { return String(value || "").trim().slice(0, max || 9999); }\n' +
    'function sha256Hex() { return "0".repeat(64); }\n' +
    projectionSource +
    "\nreturn { publicTripProjection0491 };"
  )();

  const projected = compiled.publicTripProjection0491({
    title: "A → B",
    departureAtMillis: 123,
    capacity: 4,
    status: "PUBLISHED",
    stops: [
      { id: "private-stop-a", order: 0, name: "A", address: "Rua privada 1" },
      { id: "private-stop-b", order: 1, name: "B", address: "Rua privada 2" },
    ],
    segmentAvailability: [
      { from: "A", to: "B", availableSeats: 1, passengerSeats: 3, passengerId: "private-passenger" },
    ],
    passengerName: "Pessoa privada",
    passengerContact: "+5500000000000",
    phone: "+5500000000000",
    email: "private@example.test",
    notes: "observação privada",
    bookingId: "private-booking",
    canonicalTripId: "private-canonical-trip",
    blablaTripId: "private-provider-trip",
    sourceReference: "private-reference",
  });

  const serialized = JSON.stringify(projected);
  assert.equal(projected.stops[0].name, "A");
  assert.equal(Object.prototype.hasOwnProperty.call(projected.stops[0], "address"), false);
  assert.deepEqual(projected.segmentAvailability, [{
    from: "A",
    to: "B",
    availableSeats: 1,
    passengerSeats: 3,
  }]);

  for (const forbidden of [
    "Pessoa privada",
    "+5500000000000",
    "private@example.test",
    "Rua privada 1",
    "Rua privada 2",
    "observação privada",
    "private-booking",
    "private-canonical-trip",
    "private-provider-trip",
    "private-reference",
    "private-passenger",
  ]) {
    assert.equal(serialized.includes(forbidden), false, forbidden);
  }
});

test("0580 public card renders only server-resolved segment rows and capacity dots", () => {
  const renderer = between(shell, "function publicSegmentRows0580", "function appendJourney0569");
  assert.match(renderer, /item\?\.segmentAvailability/);
  assert.match(renderer, /segment\.availableSeats/);
  assert.match(renderer, /segment\.passengerSeats/);
  assert.match(renderer, /agendaSegmentOccupancy0596/);
  assert.match(renderer, /segmentSeatDots0580/);
  assert.doesNotMatch(renderer, /passengerName|passengerId|bookingId|phone|email|address|sourceReference|segmentPassengerLoads/);
});

test("0596 public Agenda keeps segment occupancy anonymous and server-resolved", () => {
  const renderer = between(shell, "function publicSegmentRows0580", "function appendJourney0569");
  assert.match(renderer, /const passengerSeats =/);
  assert.match(renderer, /segment\.passengerSeats/);
  assert.match(renderer, /"👥 " \+ segment\.passengerSeats \+ "\/" \+ segment\.capacity/);
  assert.doesNotMatch(
    renderer,
    /passengerName|passengerId|bookingId|phone|email|address|sourceReference|canonicalTripId|blablaTripId/,
  );
});

test("0632 card return and canonical change channel immediately reload Agenda", () => {
  const navigation = between(shell, "const AGENDA_CARD_REFRESH_KEY_0596", "function appendJourney0569");
  assert.match(navigation, /sessionStorage\.setItem/);
  assert.match(navigation, /loadAgenda0569\(true\)/);

  const card = between(shell, "function renderTripCard0569", "function renderAgenda0569");
  assert.match(card, /viewRide\.addEventListener\("click", armAgendaCardRefresh0596\)/);
  assert.match(card, /bindTripCardNavigation0596\(card, publicUrl\)/);

  assert.match(shell, /watchAgendaCanonicalChanges0632/);
  assert.match(shell, /\/changes\?since=/);
  assert.match(shell, /changeCursor0495/);
  assert.match(shell, /addEventListener\("pageshow"/);
  assert.match(shell, /addEventListener\("focus"/);
  assert.match(shell, /visibilitychange/);
  assert.match(shell, /consumeAgendaCardRefresh0596/);
});
