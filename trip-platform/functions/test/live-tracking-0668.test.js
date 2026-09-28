"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  continuousTrackingPoints0670,
  trackingQueryFloor0670,
  distanceMeters0668,
  publicTrackingPoints0668,
  publicTrackerTelemetry0670,
  passengerDistanceToDestinationMeters0669,
  shouldClosePassengerShare0668,
  trackingShareDocId0668,
  trackingShareExpired0680,
  normalizeFamilyUsername0681,
  familyPinHash0681,
} = require("../live-tracking-0668");

test("passenger projection never exposes points from before share creation", () => {
  const points = [
    { latitude:-23.5, longitude:-46.6, recordedAtMillis:1000 },
    { latitude:-23.4, longitude:-46.5, recordedAtMillis:2000 },
    { latitude:-23.3, longitude:-46.4, recordedAtMillis:3000 },
  ];
  const passenger = publicTrackingPoints0668(points, {
    scope:"PASSENGER",
    createdAtMillis:2000,
    sessionStartedAtMillis:1000,
  });
  assert.deepEqual(passenger.map((p) => p.recordedAtMillis), [2000, 3000]);

  const family = publicTrackingPoints0668(points, {
    scope:"FAMILY",
    createdAtMillis:2000,
    sessionStartedAtMillis:1000,
  });
  assert.deepEqual(family.map((p) => p.recordedAtMillis), [1000, 2000, 3000]);
});

test("passenger share closes only near destination after minimum active window", () => {
  const now = 2_000_000;
  const share = {
    scope:"PASSENGER",
    active:true,
    createdAtMillis:now - 11 * 60 * 1000,
    destinationLatitude:-23.5505,
    destinationLongitude:-46.6333,
  };
  assert.equal(shouldClosePassengerShare0668(share, {
    latitude:-23.5506,
    longitude:-46.6332,
    recordedAtMillis:now,
  }, now), true);
  assert.equal(shouldClosePassengerShare0668({ ...share, createdAtMillis:now - 2 * 60 * 1000 }, {
    latitude:-23.5506,
    longitude:-46.6332,
    recordedAtMillis:now,
  }, now), false);
  assert.equal(shouldClosePassengerShare0668(share, {
    latitude:-23.5000,
    longitude:-46.6000,
    recordedAtMillis:now,
  }, now), false);
});

test("share id stores only hash and distance is physically plausible", () => {
  const token = "abcdefghijklmnopqrstuv0123456789ABCDEFG";
  const id = trackingShareDocId0668(token);
  assert.equal(id.length, 64);
  assert.equal(id.includes(token), false);
  const meters = distanceMeters0668(-23.5505, -46.6333, -23.5514, -46.6333);
  assert.ok(meters > 90 && meters < 120);
});

test("passenger distance stays unavailable until a post-share GPS point exists", () => {
  const share = {
    scope:"PASSENGER",
    createdAtMillis:2000,
    destinationLatitude:-23.5505,
    destinationLongitude:-46.6333,
  };
  const visibleBeforeFreshGps = publicTrackingPoints0668([
    { latitude:-23.5000, longitude:-46.6000, recordedAtMillis:1000 },
  ], {
    scope:"PASSENGER",
    createdAtMillis:share.createdAtMillis,
    sessionStartedAtMillis:1000,
  });
  assert.deepEqual(visibleBeforeFreshGps, []);
  assert.equal(passengerDistanceToDestinationMeters0669(null, share), null);

  const visibleAfterFreshGps = publicTrackingPoints0668([
    { latitude:-23.5000, longitude:-46.6000, recordedAtMillis:1000 },
    { latitude:-23.5514, longitude:-46.6333, recordedAtMillis:2500 },
  ], {
    scope:"PASSENGER",
    createdAtMillis:share.createdAtMillis,
    sessionStartedAtMillis:1000,
  });
  const current = visibleAfterFreshGps.at(-1);
  const meters = passengerDistanceToDestinationMeters0669(current, share);
  assert.ok(meters > 90 && meters < 120);
});

test("passenger auto-close ignores a queued GPS point from before link creation", () => {
  const now = 3_000_000;
  const share = {
    scope:"PASSENGER",
    active:true,
    createdAtMillis:now - 11 * 60 * 1000,
    destinationLatitude:-23.5505,
    destinationLongitude:-46.6333,
  };
  assert.equal(shouldClosePassengerShare0668(share, {
    latitude:-23.5506,
    longitude:-46.6332,
    recordedAtMillis:share.createdAtMillis - 1,
  }, now), false);
});


test("tracker heartbeat stays connected even when GPS position does not move", () => {
  const now = 5_000_000;
  const current = {
    latitude:-23.5505,
    longitude:-46.6333,
    recordedAtMillis:now - 18_000,
  };
  const telemetry = publicTrackerTelemetry0670({
    lastDeviceHeartbeatAtMillis:now - 4_000,
    lastGpsAtMillis:current.recordedAtMillis,
  }, { scope:"FAMILY" }, current, now);
  assert.equal(telemetry.deviceState, "CONNECTED");
  assert.equal(telemetry.gpsState, "FRESH");
  assert.equal(telemetry.lastDeviceHeartbeatAtMillis, now - 4_000);
});

test("tracker distinguishes connected device from stale GPS", () => {
  const now = 6_000_000;
  const current = {
    latitude:-23.5505,
    longitude:-46.6333,
    recordedAtMillis:now - 45_000,
  };
  const telemetry = publicTrackerTelemetry0670({
    lastDeviceHeartbeatAtMillis:now - 3_000,
    lastGpsAtMillis:current.recordedAtMillis,
  }, { scope:"FAMILY" }, current, now);
  assert.equal(telemetry.deviceState, "CONNECTED");
  assert.equal(telemetry.gpsState, "STALE");
});

test("passenger telemetry never exposes pre-share GPS age", () => {
  const now = 7_000_000;
  const telemetry = publicTrackerTelemetry0670({
    lastDeviceHeartbeatAtMillis:now - 2_000,
    lastGpsAtMillis:now - 1_000,
  }, { scope:"PASSENGER", createdAtMillis:now - 500 }, null, now);
  assert.equal(telemetry.deviceState, "CONNECTED");
  assert.equal(telemetry.gpsState, "WAITING");
  assert.equal(telemetry.lastGpsAtMillis, 0);
});

test("tracker reports offline after heartbeat stops", () => {
  const now = 8_000_000;
  const telemetry = publicTrackerTelemetry0670({
    lastDeviceHeartbeatAtMillis:now - 61_000,
    lastGpsAtMillis:now - 61_000,
  }, { scope:"FAMILY" }, {
    latitude:-23.5505,
    longitude:-46.6333,
    recordedAtMillis:now - 61_000,
  }, now);
  assert.equal(telemetry.deviceState, "OFFLINE");
  assert.equal(telemetry.gpsState, "OLD");
});


test("continuous tracker keeps every valid five-second GPS point instead of sampling by distance or minute", () => {
  const start = 10_000_000;
  const raw = Array.from({ length: 13 }, (_, index) => ({
    latitude:-23.550500 + index * 0.000015,
    longitude:-46.633300 + index * 0.000015,
    recordedAtMillis:start + index * 5_000,
    accuracyMeters:5,
    speedMetersPerSecond:8,
  }));
  const kept = continuousTrackingPoints0670(raw);
  assert.equal(kept.length, 13);
  assert.deepEqual(
    kept.map((point) => point.recordedAtMillis),
    raw.map((point) => point.recordedAtMillis),
  );
});

test("incremental public route floor never crosses passenger privacy boundary", () => {
  const session = { startedAtMillis:1_000 };
  const passenger = { scope:"PASSENGER", createdAtMillis:5_000 };
  const family = { scope:"FAMILY", createdAtMillis:5_000 };
  assert.equal(trackingQueryFloor0670(passenger, session, 0), 5_000);
  assert.equal(trackingQueryFloor0670(passenger, session, 7_500), 7_500);
  assert.equal(trackingQueryFloor0670(passenger, session, 3_000), 5_000);
  assert.equal(trackingQueryFloor0670(family, session, 7_500), 7_500);
});


test("family tracking is revocation-driven while passenger tracking still expires", () => {
  const now = 20_000_000;
  assert.equal(trackingShareExpired0680({
    scope:"FAMILY",
    active:true,
    expiresAtMillis:now - 1,
  }, now), false);
  assert.equal(trackingShareExpired0680({
    scope:"PASSENGER",
    active:true,
    expiresAtMillis:now - 1,
  }, now), true);
  assert.equal(trackingShareExpired0680({
    scope:"PASSENGER",
    active:true,
    expiresAtMillis:now + 1,
  }, now), false);
});


test("permanent family GPS username normalizes predictably", () => {
  assert.equal(normalizeFamilyUsername0681(" Ezequiel Silva "), "ezequiel-silva");
  assert.equal(normalizeFamilyUsername0681("Ézéquiel"), "ezequiel");
});

test("family PIN hash is scoped to the driver username", () => {
  const first = familyPinHash0681("ezequiel", "123456");
  const same = familyPinHash0681("EZEQUIEL", "123456");
  const otherPin = familyPinHash0681("ezequiel", "654321");
  const otherDriver = familyPinHash0681("barbosa", "123456");
  assert.equal(first, same);
  assert.notEqual(first, otherPin);
  assert.notEqual(first, otherDriver);
  assert.equal(first.length, 64);
});
