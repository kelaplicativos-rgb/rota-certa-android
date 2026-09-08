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

function compileAvailabilityLabels() {
  const source = between(app, "function seatRange", "function publicSegmentRows0484");
  return Function(source + "\nreturn { exactAvailabilityLabel, publicAvailabilityLabel };")();
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

test("0504 public labels keep canonical availability numeric and privacy-safe", () => {
  const { segmentAvailabilityLabel0484 } = compileLabels();
  assert.equal(segmentAvailabilityLabel0484(0), "0 vagas");
  assert.equal(segmentAvailabilityLabel0484(1), "1 vaga");
  assert.equal(segmentAvailabilityLabel0484(2), "2 vagas");
  assert.match(html, /agendaSegmentAvailability0484/);
  assert.match(html, /agendaSegmentPassengers0489/);
  assert.match(html, /app\.js\?v=0\.1\.506/);
});

test("0504 card summary remains numeric for zero, exact and segment ranges", () => {
  const { exactAvailabilityLabel, publicAvailabilityLabel } = compileAvailabilityLabels();
  assert.equal(exactAvailabilityLabel(0), "0 vagas disponíveis");
  assert.equal(exactAvailabilityLabel(1), "1 vaga disponível");
  assert.equal(exactAvailabilityLabel(3), "3 vagas disponíveis");
  assert.equal(publicAvailabilityLabel({
    capacityReliable: true,
    availableSeatsMinimum: 0,
    availableSeatsMaximum: 0,
  }), "0 vagas disponíveis");
  assert.equal(publicAvailabilityLabel({
    capacityReliable: true,
    availableSeatsMinimum: 0,
    availableSeatsMaximum: 2,
  }), "0–2 vagas disponíveis por trecho");
  assert.equal(publicAvailabilityLabel({
    capacityReliable: true,
    availableSeatsMinimum: 1,
    availableSeatsMaximum: 3,
  }), "1–3 vagas disponíveis por trecho");
});

test("0504 requested A-B-C-D segment example renders 2, 0 and 3 vacancies without recomputing in UI", () => {
  const { publicSegmentAvailability0484 } = compilePublicSegments();
  const rows = publicSegmentAvailability0484({
    capacity: 3,
    stops: [{ name: "A" }, { name: "B" }, { name: "C" }, { name: "D" }],
  }, [1, 3, 0], true, [1, 3, 0]);
  assert.deepEqual(rows, [
    { from: "A", to: "B", availableSeats: 2, passengerSeats: 1 },
    { from: "B", to: "C", availableSeats: 0, passengerSeats: 3 },
    { from: "C", to: "D", availableSeats: 3, passengerSeats: 0 },
  ]);
  const { segmentAvailabilityLabel0484 } = compileLabels();
  assert.deepEqual(rows.map((row) => segmentAvailabilityLabel0484(row.availableSeats)), ["2 vagas", "0 vagas", "3 vagas"]);
});

test("0504 canonical capacity is per-trip and is not hardcoded to 3 or 4", () => {
  const { publicSegmentAvailability0484 } = compilePublicSegments();
  for (const capacity of [3, 4, 5]) {
    const occupied = Math.max(0, capacity - 1);
    assert.deepEqual(publicSegmentAvailability0484(
      { capacity, stops: [{ name: "A" }, { name: "B" }] },
      [occupied], true, [occupied],
    ), [{ from: "A", to: "B", availableSeats: 1, passengerSeats: occupied }]);
  }
});


test("0501 collector vacancies stay explicit while live canonical dots update", () => {
  const source = between(api, "function canonicalSegmentVector0497", "function canonicalPublicTripPayload0411");
  const compiled = Function(
    `function canonicalPublicTripPayloadFromStored0434(raw) { return raw; }
function canonicalPublicCapacityState0485(input) {
  const available = (input.segmentLoads || []).map((load) => Math.max(0, Number(input.capacity || 0) - Number(load || 0)));
  return {
    availableSeatsMinimum: available.length ? Math.min(...available) : 0,
    availableSeatsMaximum: available.length ? Math.max(...available) : 0,
    reliable: input.capacityReliable === true,
  };
}
` + source + "\nreturn { canonicalPublicTripPayloadFromCurrentCanonicalOccupancy0497 };"
  )();

  const payload = {
    capacity: 4,
    status: "PUBLISHED",
    stops: [{ name: "A" }, { name: "B" }, { name: "C" }],
    segmentLoads: [0, 3],
    segmentPassengerLoads: [0, 3],
    segmentBlockedLoads: [0, 0],
    capacityReliable: true,
  };
  const projected = compiled.canonicalPublicTripPayloadFromCurrentCanonicalOccupancy0497("trip", {
    canonicalPublicProjection0434: payload,
    segmentLoads: [4, 4],
    segmentPassengerLoads: [1, 3],
    segmentBlockedLoads: [3, 1],
    confirmedPassengerSeats: 3,
    capacityReliable: false,
  });

  assert.deepEqual(projected.segmentPassengerLoads, [1, 3]);
  assert.deepEqual(projected.segmentLoads, [1, 3]);
  assert.equal(projected.capacityReliable, true);

  const { publicSegmentAvailability0484 } = compilePublicSegments();
  assert.deepEqual(
    publicSegmentAvailability0484(
      { capacity: projected.capacity, stops: projected.stops },
      projected.segmentLoads,
      projected.capacityReliable,
      projected.segmentPassengerLoads,
    ),
    [
      { from: "A", to: "B", availableSeats: 3, passengerSeats: 1 },
      { from: "B", to: "C", availableSeats: 1, passengerSeats: 3 },
    ],
  );
});

test("0500 capacity 4 preserves exact anonymous occupancy 0..4 and vacancies 4..0", () => {
  const { publicSegmentAvailability0484 } = compilePublicSegments();
  for (let occupied = 0; occupied <= 4; occupied += 1) {
    assert.deepEqual(publicSegmentAvailability0484(
      { capacity: 4, stops: [{ name: "A" }, { name: "B" }] },
      [occupied], true, [occupied],
    ), [{ from: "A", to: "B", availableSeats: 4 - occupied, passengerSeats: occupied }]);
  }
});

test("0500 A-B-C-D passengers consume every and only traversed segment", () => {
  const { reconciledSegmentCapacity } = compileSegmentCapacity();
  const { publicSegmentAvailability0484 } = compilePublicSegments();
  const trip = { capacity: 4, stops: [
    { id: "a", name: "A" }, { id: "b", name: "B" }, { id: "c", name: "C" }, { id: "d", name: "D" },
  ]};
  const records = [
    { id: "p1", passengerId: "p1", boardingStopId: "a", dropoffStopId: "b", seats: 1, status: "CONFIRMED", capacityClaimType: "PASSENGER" },
    { id: "p2", passengerId: "p2", boardingStopId: "b", dropoffStopId: "d", seats: 1, status: "CONFIRMED", capacityClaimType: "PASSENGER" },
    { id: "p3", passengerId: "p3", boardingStopId: "c", dropoffStopId: "d", seats: 1, status: "CONFIRMED", capacityClaimType: "PASSENGER" },
  ];
  const state = reconciledSegmentCapacity(trip, records, 0);
  assert.deepEqual(state.passengerLoads, [1, 1, 2]);
  assert.deepEqual(state.loads, [1, 1, 2]);
  assert.deepEqual(
    publicSegmentAvailability0484(trip, state.loads, true, state.passengerLoads)
      .map((row) => [row.passengerSeats, row.availableSeats]),
    [[1, 3], [1, 3], [2, 2]],
  );
});

test("0500 public Agenda projection allowlist removes passenger/private/admin data before JSON", () => {
  const production = between(api, "function publicTripProjection0491", "function canonicalPublicStop0411");
  const { publicTripProjection0491 } = Function(production + "\nreturn { publicTripProjection0491 };")();
  const projected = publicTripProjection0491({
    title: "A → B", departureAtMillis: 123, capacity: 4, status: "PUBLISHED",
    stops: [{ id: "private-stop-id", order: 0, name: "A", address: "Parada pública" }, { id: "private-stop-id-2", order: 1, name: "B" }],
    segmentPassengerLoads: [3],
    segmentAvailability: [{ from: "A", to: "B", passengerSeats: 3, availableSeats: 1, passengerId: "segment-secret" }],
    blablaPublicUrl: "https://www.blablacar.com.br/trip/public-safe",
    passengerId: "secret-passenger-id", passengerName: "Secret Person", passengerContact: "+5511999999999",
    whatsapp: "+5511888888888", email: "secret@example.test",
    boardingAddress: "Secret Boarding Address", dropoffAddress: "Secret Dropoff Address",
    fareMinorUnits: 9999, paymentStatus: "PAID", notes: "Secret Notes",
    sourceReference: "BLABLACAR_SYNC:secret", bookingId: "secret-booking",
    canonicalTripId: "private-canonical-id", blablaTripId: "private-blabla-id",
    driverUsername: "private-admin-user", token: "secret-token", session: "secret-session",
  });
  const serialized = JSON.stringify(projected);
  assert.equal(projected.segmentAvailability[0].passengerSeats, 3);
  assert.equal(projected.segmentAvailability[0].availableSeats, 1);
  assert.equal(projected.blablaPublicUrl, "https://www.blablacar.com.br/trip/public-safe");
  for (const forbidden of [
    "Secret Person", "+5511999999999", "+5511888888888", "secret@example.test",
    "Secret Boarding Address", "Secret Dropoff Address", "Secret Notes", "secret-passenger-id",
    "secret-booking", "private-canonical-id", "private-blabla-id", "secret-token", "secret-session",
    "sourceReference", "passengerId", "passengerName", "paymentStatus", "fareMinorUnits",
  ]) assert.equal(serialized.includes(forbidden), false, forbidden);
});


test("0500 anonymous public driver projection never serializes WhatsApp or driver phone", () => {
  const profile = between(api, "function safePublicDriverProfile", "function publicProjectionAttestedCurrent0429");
  assert.doesNotMatch(profile, /profile\.whatsapp/);
  assert.doesNotMatch(profile, /driverWhatsapp/);
});
