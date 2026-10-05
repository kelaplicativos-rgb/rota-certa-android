"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  TOOL_SCHEMA_VERSION_0732,
  classifyAgendaRead0732,
  snapshotHash0732,
  createLiveAgendaTool0732,
} = require("../live-agenda-tool-0732");

function feedFixture(overrides = {}) {
  return {
    schemaVersion: "live-agenda-feed-v1",
    sourceStatus: "LIVE",
    generatedAtMillis: Date.parse("2026-10-05T13:00:00.000Z"),
    generatedAtIsoUtc: "2026-10-05T13:00:00.000Z",
    latestChangeAtMillis: Date.parse("2026-10-05T12:58:00.000Z"),
    driverUsername: "ezequiel",
    query: {
      date: "2026-10-09",
      origin: "São Paulo",
      destination: "Três Corações",
    },
    trips: [{
      id: "trip-1",
      localDate: "2026-10-09",
      localTime: "11:20",
      status: "PUBLISHED",
      capacity: 4,
      capacityReliable: true,
      itineraryAuthoritative: true,
      availableSeats: 2,
      updatedAtMillis: Date.parse("2026-10-05T12:58:00.000Z"),
      stops: [
        { order: 0, name: "São Paulo" },
        { order: 1, name: "Três Corações" },
      ],
      segments: [{
        index: 0,
        from: "São Paulo",
        to: "Três Corações",
        availableSeats: 2,
        occupiedSeats: 2,
        passengerSeats: 2,
        blockedSeats: 0,
      }],
      requestedSegment: {
        found: true,
        origin: "São Paulo",
        destination: "Três Corações",
        reliable: true,
        availableSeats: 2,
        occupiedSeats: 2,
      },
    }],
    ...overrides,
  };
}

function responseRecorder() {
  let statusCode = 0;
  let payload = "";
  const headers = {};
  return {
    status(code) {
      statusCode = Number(code);
      return this;
    },
    set(key, value) {
      headers[String(key)] = String(value);
      return this;
    },
    send(value) {
      payload = String(value == null ? "" : value);
      return this;
    },
    snapshot() {
      return { statusCode, payload, headers };
    },
  };
}

test("reliable canonical agenda read is COMPLETE but never publish-safe without collector validation", async () => {
  const feed = feedFixture();
  const classification = classifyAgendaRead0732(feed);
  assert.equal(classification.validationStatus, "COMPLETE");

  const fakeFeed = {
    async getLiveAgendaFeed0701(_req, res) {
      res.status(200).set("Content-Type", "application/json").send(JSON.stringify(feed));
    },
  };
  const tool = createLiveAgendaTool0732({ liveAgendaFeed0701: fakeFeed });
  const res = responseRecorder();
  await tool.getLiveAgendaTool0732({ query: {}, headers: {} }, res, "ezequiel");
  const recorded = res.snapshot();
  assert.equal(recorded.statusCode, 200);
  const body = JSON.parse(recorded.payload);
  assert.equal(body.schemaVersion, TOOL_SCHEMA_VERSION_0732);
  assert.equal(body.validationStatus, "COMPLETE");
  assert.equal(body.collectorValidationStatus, "PENDING_UNKNOWN");
  assert.equal(body.decisionSafeForPublish, false);
  assert.equal(body.trips[0].requestedSegment.availableSeats, 2);
  assert.match(recorded.headers.ETag, /^"[a-f0-9]{64}"$/);
  assert.equal(recorded.headers["Cache-Control"], "no-store, no-cache, max-age=0, must-revalidate");
});

test("unreliable itinerary or capacity makes agenda read PARTIAL", () => {
  const feed = feedFixture({
    trips: [{
      ...feedFixture().trips[0],
      capacityReliable: false,
      segments: [{
        ...feedFixture().trips[0].segments[0],
        availableSeats: null,
        occupiedSeats: null,
      }],
    }],
  });
  const classification = classifyAgendaRead0732(feed);
  assert.equal(classification.validationStatus, "PARTIAL");
  assert.equal(classification.unreliableTrips, 1);
  assert.deepEqual(classification.reasons, ["UNRELIABLE_TRIP_FIELDS"]);
});

test("empty canonical agenda can be read completely but collector coverage remains independent", async () => {
  const feed = feedFixture({ trips: [], latestChangeAtMillis: 0 });
  const classification = classifyAgendaRead0732(feed);
  assert.equal(classification.validationStatus, "COMPLETE");

  const fakeFeed = {
    async getLiveAgendaFeed0701(_req, res) {
      res.status(200).send(JSON.stringify(feed));
    },
  };
  const tool = createLiveAgendaTool0732({ liveAgendaFeed0701: fakeFeed });
  const res = responseRecorder();
  await tool.getLiveAgendaTool0732({ query: {}, headers: {} }, res, "ezequiel");
  const body = JSON.parse(res.snapshot().payload);
  assert.equal(body.count, 0);
  assert.equal(body.collectorValidationStatus, "PENDING_UNKNOWN");
  assert.equal(body.decisionSafeForPublish, false);
});

test("ETag is stable across generated-at changes when canonical snapshot is unchanged", () => {
  const first = feedFixture();
  const second = feedFixture({
    generatedAtMillis: first.generatedAtMillis + 30_000,
    generatedAtIsoUtc: "2026-10-05T13:00:30.000Z",
  });
  assert.equal(snapshotHash0732(first), snapshotHash0732(second));
});

test("matching If-None-Match returns 304 after a live read", async () => {
  const feed = feedFixture();
  const hash = snapshotHash0732(feed);
  const fakeFeed = {
    async getLiveAgendaFeed0701(_req, res) {
      res.status(200).send(JSON.stringify(feed));
    },
  };
  const tool = createLiveAgendaTool0732({ liveAgendaFeed0701: fakeFeed });
  const res = responseRecorder();
  await tool.getLiveAgendaTool0732({
    query: {},
    headers: { "if-none-match": `"${hash}"` },
  }, res, "ezequiel");
  const recorded = res.snapshot();
  assert.equal(recorded.statusCode, 304);
  assert.equal(recorded.payload, "");
});

test("upstream live-feed failure is FAILED and never converted into empty agenda", async () => {
  const fakeFeed = {
    async getLiveAgendaFeed0701(_req, res) {
      res.status(503).send(JSON.stringify({
        error: "live_feed_failed",
        message: "Falha de rede.",
      }));
    },
  };
  const tool = createLiveAgendaTool0732({ liveAgendaFeed0701: fakeFeed });
  const res = responseRecorder();
  await tool.getLiveAgendaTool0732({ query: {}, headers: {} }, res, "ezequiel");
  const recorded = res.snapshot();
  assert.equal(recorded.statusCode, 503);
  const body = JSON.parse(recorded.payload);
  assert.equal(body.validationStatus, "FAILED");
  assert.equal(body.collectorValidationStatus, "FAILED");
  assert.equal(body.decisionSafeForPublish, false);
  assert.equal(Object.prototype.hasOwnProperty.call(body, "trips"), false);
});
