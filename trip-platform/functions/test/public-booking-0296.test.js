"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

test("public booking remains opt-in and future-only", () => {
  assert.match(api, /publicBookingEnabled: raw\.publicBookingEnabled === true/);
  assert.match(api, /trip\.publicBookingEnabled !== true/);
  assert.match(api, /departureAtMillis.*<= Date\.now\(\)/s);
});

test("public booking is idempotent and transactionally reconciles segment capacity", () => {
  assert.match(api, /Idempotency-Key/);
  assert.match(api, /publicBookingId\(token, idempotencyKey\)/);
  assert.match(api, /idempotencyFingerprint/);
  assert.match(api, /existingAttempt\.exists/);
  assert.match(api, /reconciledSegmentLoads\(trip, \[\.\.\.existing, candidate\]/);
  assert.match(api, /assertNoOverbooking/);
  assert.match(api, /db\.runTransaction/);
  assert.doesNotMatch(api, /loads\[index\]\s*=\s*\(loads\[index\].*\+\s*seats/);
});

test("backend validates Brazilian WhatsApp and public source", () => {
  assert.match(api, /normalizeBrazilWhatsapp/);
  assert.match(api, /source: "ROTA_CERTA"/);
  assert.match(api, /sourceReference: `PUBLIC_LINK:/);
});

test("mobile portal reviews before confirmation and limits seats dynamically", () => {
  assert.match(web, /reviewBooking/);
  assert.match(web, /seatsInput\.max = String\(Math\.max\(1, available\)\)/);
  assert.match(web, /normalizeWhatsapp/);
  assert.match(web, /requestIdentity/);
  assert.match(web, /body\.replayed/);
  assert.match(html, /CONFIRME SUA VIAGEM/);
  assert.match(html, /Qual é o seu WhatsApp/);
  assert.match(html, /✅ RESERVA CONFIRMADA/);
});
