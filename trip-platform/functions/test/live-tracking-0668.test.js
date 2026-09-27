"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  distanceMeters0668,
  publicTrackingPoints0668,
  shouldClosePassengerShare0668,
  trackingShareDocId0668,
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