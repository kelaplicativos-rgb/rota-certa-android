"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.resolve(__dirname, "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const page = fs.readFileSync(path.join(root, "public", "app.js"), "utf8");
const rules = fs.readFileSync(path.join(root, "firestore.rules"), "utf8");

test("booking capacity is changed inside a Firestore transaction", () => {
  assert.match(api, /db\.runTransaction/);
  assert.match(api, /segmentLoads/);
  assert.match(api, /insufficient_seats/);
  assert.match(api, /tx\.create\(bookingRef/);
  assert.match(api, /tx\.update\(tripRef/);
});

test("public trip serializer does not expose passenger identity fields", () => {
  const start = api.indexOf("function safePublicTrip");
  const end = api.indexOf("function clientIp", start);
  const block = api.slice(start, end);
  assert.ok(block.length > 100);
  assert.doesNotMatch(block, /passengerName|passengerContact|cancellationHash/);
});

test("Firestore is deny by default and browser uses the HTTPS API", () => {
  assert.match(rules, /allow read, write: if false/);
  assert.match(page, /\/v1\/public\/trips\//);
  assert.doesNotMatch(page, /firebase\.firestore|collection\(/);
});

test("browser calendar export excludes passenger contact", () => {
  const start = page.indexOf("function downloadIcs");
  const end = page.indexOf("async function shareCalendarFeed", start);
  const block = page.slice(start, end);
  assert.ok(block.length > 100);
  assert.doesNotMatch(block, /passengerContact/);
  assert.match(block, /text\/calendar/);
});

test("driver endpoints require a secret compared in constant time", () => {
  assert.match(api, /timingSafeEqual/);
  assert.match(api, /X-Rota-Certa-Driver-Token/);
  assert.match(api, /defineSecret\("ROTA_CERTA_DRIVER_TOKEN"\)/);
});
