"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  requestedSegment0701,
  projectTrip0701,
  serializeXml0701,
  createLiveAgendaFeed0701,
} = require("../live-agenda-feed-0701");

function tripFixture() {
  return {
    canonicalTripId: "trip-1",
    title: "Três Corações → São Paulo",
    departureAtMillis: Date.parse("2026-10-02T21:30:00.000Z"),
    timezoneId: "America/Sao_Paulo",
    status: "PUBLISHED",
    capacity: 4,
    capacityReliable: true,
    itineraryAuthoritative: true,
    updatedAtMillis: Date.parse("2026-09-29T22:40:00.000Z"),
    stops: [
      { order: 0, name: "Três Corações" },
      { order: 1, name: "Posto Fernandão - Pouso Alegre" },
      { order: 2, name: "Extrema" },
      { order: 3, name: "São Paulo - Metrô Penha" },
    ],
    segmentLoads: [4, 4, 3],
    segmentPassengerLoads: [4, 4, 3],
    segmentBlockedLoads: [0, 0, 0],
    segmentAvailability: [
      { from: "Três Corações", to: "Pouso Alegre", availableSeats: 0, passengerSeats: 4 },
      { from: "Pouso Alegre", to: "Extrema", availableSeats: 0, passengerSeats: 4 },
      { from: "Extrema", to: "São Paulo", availableSeats: 1, passengerSeats: 3 },
    ],
  };
}

test("requested segment uses the minimum vacancy across every crossed leg", () => {
  const result = requestedSegment0701(tripFixture(), "Pouso Alegre", "São Paulo");
  assert.equal(result.found, true);
  assert.equal(result.reliable, true);
  assert.equal(result.availableSeats, 0);
  assert.equal(result.occupiedSeats, 4);
});

test("projection exposes only operational route data and preserves the local date", () => {
  const projected = projectTrip0701(tripFixture(), {
    date: "2026-10-02",
    origin: "Pouso Alegre",
    destination: "São Paulo",
  });
  assert.equal(projected.localDate, "2026-10-02");
  assert.equal(projected.localTime, "18:30");
  assert.equal(projected.requestedSegment.availableSeats, 0);
  assert.equal(projected.segments[2].availableSeats, 1);
  assert.equal(Object.prototype.hasOwnProperty.call(projected, "passengerName"), false);
  assert.equal(Object.prototype.hasOwnProperty.call(projected, "passengerContact"), false);
});

test("xml serializer escapes text and includes queried segment vacancy", () => {
  const trip = projectTrip0701(
    { ...tripFixture(), title: 'Rota & teste <seguro>' },
    { origin: "Pouso Alegre", destination: "São Paulo" },
  );
  const xml = serializeXml0701({
    schemaVersion: "live-agenda-feed-v1",
    driverUsername: "ezequiel",
    generatedAtIsoUtc: "2026-09-29T22:45:00.000Z",
    timezoneId: "America/Sao_Paulo",
    sourceStatus: "LIVE",
    query: {
      date: "",
      origin: "Pouso Alegre",
      destination: "São Paulo",
      profileUuid: "11111111-1111-4111-8111-111111111111",
    },
    latestChangeAtMillis: trip.updatedAtMillis,
    trips: [trip],
  });
  assert.match(xml, /Rota &amp; teste &lt;seguro&gt;/);
  assert.match(xml, /consulta[^>]*perfilUuid="11111111-1111-4111-8111-111111111111"/);
  assert.match(xml, /trechoConsultado[^>]*vagas="0"/);
  assert.doesNotMatch(xml, /passengerContact|WhatsApp|telefone/i);
});

test("handler reads the canonical public source without passenger authentication", async () => {
  const docs = [{
    id: "trip-1",
    data: () => ({
      driverUsername: "ezequiel",
      departureAtMillis: tripFixture().departureAtMillis,
    }),
  }];
  const driverSnap = { exists: true, data: () => ({ publicAgendaEnabled: true }) };
  const db = {
    collection(name) {
      assert.equal(name, "trips");
      return {
        where(field, op, value) {
          assert.equal(field, "driverUsername");
          assert.equal(op, "==");
          assert.equal(value, "viagem-certa");
          return {
            limit() {
              return { get: async () => ({ docs }) };
            },
          };
        },
      };
    },
  };
  const feed = createLiveAgendaFeed0701({
    db,
    resolveDriverUsername: async () => ({
      requestedUsername: "ezequiel",
      canonicalUsername: "viagem-certa",
      publicUsername: "ezequiel",
      driverSnap,
    }),
    selectCanonicalTripDocuments0495: (items) => items,
    publicAgendaTripVisibility0466: () => ({ visible: true }),
    safePublicTripWithCanonicalBookings0497: async () => tripFixture(),
  });
  const headers = {};
  let statusCode = 0;
  let payload = "";
  const res = {
    status(code) { statusCode = code; return this; },
    set(key, value) { headers[key] = value; return this; },
    send(value) { payload = String(value); return this; },
  };
  await feed.getLiveAgendaFeed0701({
    query: {
      data: "2026-10-02",
      origem: "Pouso Alegre",
      destino: "São Paulo",
    },
  }, res, "ezequiel", "json");
  assert.equal(statusCode, 200);
  assert.equal(headers["Cache-Control"].includes("no-store"), true);
  assert.equal(headers["X-Robots-Tag"].includes("noindex"), true);
  const parsed = JSON.parse(payload);
  assert.equal(parsed.driverUsername, "ezequiel");
  assert.equal(parsed.count, 1);
  assert.equal(parsed.trips[0].requestedSegment.availableSeats, 0);
});


test("profileUuid filters one BlaBla profile inside the same canonical physical agenda", async () => {
  const uuidA = "11111111-1111-4111-8111-111111111111";
  const uuidB = "22222222-2222-4222-8222-222222222222";
  const docs = [
    {
      id: "trip-a",
      data: () => ({
        driverUsername: "viagem-certa",
        departureAtMillis: tripFixture().departureAtMillis,
        blablaProfileUuid: uuidA,
      }),
    },
    {
      id: "trip-b",
      data: () => ({
        driverUsername: "viagem-certa",
        departureAtMillis: tripFixture().departureAtMillis + 60_000,
        canonicalPublicProjection0434: {
          blablaProfileUuid: uuidB,
        },
      }),
    },
  ];
  const driverSnap = { exists: true, data: () => ({ publicAgendaEnabled: true }) };
  const db = {
    collection(name) {
      assert.equal(name, "trips");
      return {
        where(field, op, value) {
          assert.equal(field, "driverUsername");
          assert.equal(op, "==");
          assert.equal(value, "viagem-certa");
          return {
            limit() {
              return { get: async () => ({ docs }) };
            },
          };
        },
      };
    },
  };
  const safeCalls = [];
  const feed = createLiveAgendaFeed0701({
    db,
    resolveDriverUsername: async () => ({
      requestedUsername: "ezequiel",
      canonicalUsername: "viagem-certa",
      publicUsername: "ezequiel",
      driverSnap,
    }),
    selectCanonicalTripDocuments0495: (items) => items,
    publicAgendaTripVisibility0466: () => ({ visible: true }),
    safePublicTripWithCanonicalBookings0497: async (doc) => {
      safeCalls.push(doc.id);
      return {
        ...tripFixture(),
        canonicalTripId: doc.id,
        departureAtMillis: doc.data().departureAtMillis || tripFixture().departureAtMillis,
      };
    },
  });
  let statusCode = 0;
  let payload = "";
  const res = {
    status(code) { statusCode = code; return this; },
    set() { return this; },
    send(value) { payload = String(value); return this; },
  };

  await feed.getLiveAgendaFeed0701({
    query: {
      date: "2026-10-02",
      profileUuid: uuidB.toUpperCase(),
    },
  }, res, "ezequiel", "json");

  assert.equal(statusCode, 200);
  const parsed = JSON.parse(payload);
  assert.equal(parsed.driverUsername, "ezequiel");
  assert.equal(parsed.query.profileUuid, uuidB);
  assert.equal(parsed.count, 1);
  assert.equal(parsed.trips[0].id, "trip-b");
  assert.deepEqual(safeCalls, ["trip-b"]);
});

test("invalid profileUuid fails closed before reading the canonical driver", async () => {
  let resolved = false;
  const feed = createLiveAgendaFeed0701({
    db: {},
    resolveDriverUsername: async () => {
      resolved = true;
      return null;
    },
    selectCanonicalTripDocuments0495: (items) => items,
    publicAgendaTripVisibility0466: () => ({ visible: true }),
    safePublicTripWithCanonicalBookings0497: async () => tripFixture(),
  });
  let statusCode = 0;
  let payload = "";
  const res = {
    status(code) { statusCode = code; return this; },
    set() { return this; },
    send(value) { payload = String(value); return this; },
  };

  await feed.getLiveAgendaFeed0701({
    query: { profileUuid: "barbosa" },
  }, res, "ezequiel", "json");

  assert.equal(statusCode, 400);
  assert.equal(resolved, false);
  const parsed = JSON.parse(payload);
  assert.equal(parsed.error, "invalid_profile_uuid");
});
