"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const backend = fs.readFileSync(path.join(__dirname, "..", "live-tracking-0668.js"), "utf8");
const trackingJs = fs.readFileSync(path.join(__dirname, "..", "..", "public", "tracking.js"), "utf8");
const trackingHtml = fs.readFileSync(path.join(__dirname, "..", "..", "public", "tracking.html"), "utf8");

test("passenger public projection is live-only and does not query route points", () => {
  assert.match(backend, /passengerLiveOnly0691 = share\.scope === "PASSENGER"/);
  assert.match(backend, /if \(!passengerLiveOnly0691\) \{[\s\S]*collection\("points"\)/);
  assert.match(backend, /mode:passengerLiveOnly0691 \? "LIVE_ONLY" : "LIVE_WITH_TRACE"/);
  assert.match(backend, /traceAvailable:!passengerLiveOnly0691/);
  assert.match(backend, /points,/);
});

test("passenger page never renders a route and follows only the current marker", () => {
  assert.match(trackingJs, /PASSENGER_LIVE_ONLY_0691/);
  assert.match(trackingJs, /if \(passenger\) \{[\s\S]*route\.remove\(\)/);
  assert.match(trackingJs, /currentPassengerPosition0691 = here/);
  assert.match(trackingJs, /centerPassenger0691/);
  assert.match(trackingJs, /routePoints0670 = \[\]/);
  assert.match(trackingHtml, /id="centerVehicle"/);
  assert.match(trackingHtml, /Local de desembarque/);
});

test("arrival shutdown is server-side and coordinates stop after destination confirmation", () => {
  assert.match(backend, /endReason = "DESTINATION_REACHED"/);
  assert.match(backend, /passenger_arrived/);
  assert.match(backend, /active = false/);
  assert.match(trackingJs, /Passageiro chegou ao destino/);
  assert.match(trackingJs, /Este link não fornece mais coordenadas/);
});

test("latest-wins is enforced transactionally for points and heartbeat", () => {
  const transactions = backend.match(/db\.runTransaction/g) || [];
  assert.ok(transactions.length >= 2);
  assert.match(backend, /newest\.recordedAtMillis > previousGpsAt/);
  assert.match(backend, /heartbeatPoint\.recordedAtMillis > previousGpsAt/);
  assert.match(backend, /latestWins0691:true/);
});
