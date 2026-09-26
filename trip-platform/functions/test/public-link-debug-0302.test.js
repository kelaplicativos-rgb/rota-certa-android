"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");

test("public link debug has a privacy-safe server pipeline", () => {
  assert.match(api, /PUBLIC_DEBUG_EVENTS/);
  assert.match(api, /PUBLIC_LINK_OPENED/);
  assert.match(api, /PUBLIC_RESERVATION_CREATED/);
  assert.match(api, /PUBLIC_RESERVATION_FAILED/);
  assert.match(api, /PUBLIC_RESERVATION_CANCELLED/);
  assert.match(api, /PUBLIC_SEATS_UPDATED/);
  assert.match(api, /tripPublicDebugEvents/);
  assert.match(api, /tripRefHash:/);
  assert.match(api, /agendaRefHash:/);
  assert.match(api, /PUBLIC_DEBUG_RETENTION_MILLIS/);
  assert.match(api, /\/v1\/public\/debug\/events/);
  assert.match(api, /\/v1\/driver\/public-debug/);
  assert.match(api, /requireDriver\(req, res\)/);
});

test("public browser trace never sends passenger PII or cancellation secrets", () => {
  const start = web.indexOf("function tracePublicAction");
  const end = web.indexOf("function show(", start);
  assert.ok(start >= 0 && end > start);
  const trace = web.slice(start, end);
  assert.doesNotMatch(trace, /passengerName/);
  assert.doesNotMatch(trace, /passengerContact/);
  assert.doesNotMatch(trace, /cancellationToken/);
  assert.doesNotMatch(trace, /bookingId/);
  assert.match(trace, /sessionId/);
  assert.match(trace, /statusCode/);
  assert.match(trace, /fromIndex/);
  assert.match(trace, /toIndex/);
});

test("public UI traces the complete passenger journey", () => {
  for (const event of [
    "PUBLIC_LINK_OPENED",
    "PUBLIC_AGENDA_LOADED",
    "PUBLIC_AGENDA_LOAD_FAILED",
    "PUBLIC_TRIP_SELECTED",
    "PUBLIC_TRIP_LOADED",
    "PUBLIC_TRIP_LOAD_FAILED",
    "PUBLIC_SEARCH_CHANGED",
    "PUBLIC_RESERVATION_STARTED",
    "PUBLIC_RESERVATION_REQUEST_SENT",
    "PUBLIC_RESERVATION_CREATED",
    "PUBLIC_RESERVATION_FAILED",
    "PUBLIC_RESERVATION_CANCEL_STARTED",
    "PUBLIC_RESERVATION_CANCELLED",
    "PUBLIC_RESERVATION_CANCEL_FAILED",
    "PUBLIC_SEATS_UPDATED",
  ]) {
    assert.match(web, new RegExp(event));
  }
});

test("server records authoritative booking and cancellation outcomes", () => {
  assert.match(api, /event: "PUBLIC_RESERVATION_CREATED"[\s\S]*source: "server"/);
  assert.match(api, /event: "PUBLIC_RESERVATION_FAILED"[\s\S]*reason: error\.code/);
  assert.match(api, /event: "PUBLIC_RESERVATION_CANCELLED"[\s\S]*source: "server"/);
  assert.match(api, /event: "PUBLIC_RESERVATION_CANCEL_FAILED"[\s\S]*reason: error\.code/);
});
