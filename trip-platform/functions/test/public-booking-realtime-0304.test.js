"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "public-agenda-shell-0569.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

test("driver push token registration is authenticated and stored server-side", () => {
  assert.match(api, /async function registerDriverPushToken/);
  assert.match(api, /requireDriver\(req, res\)/);
  assert.match(api, /tripDriverPushTokens/);
  assert.match(api, /\/v1\/driver\/push-tokens/);
});

test("booking create change and cancellation each emit a distinct realtime push", () => {
  assert.match(api, /event: "reservation_created"/);
  assert.match(api, /event: "reservation_changed"/);
  assert.match(api, /event: "reservation_cancelled"/);
  assert.match(api, /priority: "high"/);
  assert.match(api, /sendEachForMulticast/);
});

test("public booking changes are capacity-safe and protected by cancellation secret", () => {
  assert.match(api, /async function updatePublicBooking/);
  assert.match(api, /safeEqual\(suppliedHash, previous\.cancellationHash/);
  assert.match(api, /assertNoOverbooking\(trip, loads\)/);
  assert.match(api, /PUBLIC_RESERVATION_CHANGED/);
  assert.match(api, /req\.method === "PUT"/);
});

test("0.1.623 public portal creates one authenticated request and routes later management to Minhas viagens", () => {
  assert.match(html, /id="passengerAreaLink0589"/);
  assert.match(web, /sessionStorage\.setItem\(passengerSessionKey0623, passengerSessionToken0623\)/);
  assert.match(web, /"Idempotency-Key": idempotencyKey/);
  assert.match(web, /method: "POST"/);
  assert.match(web, /syncPassengerNav0623\(\)/);
  assert.doesNotMatch(web, /startQuickReservation/);
});
