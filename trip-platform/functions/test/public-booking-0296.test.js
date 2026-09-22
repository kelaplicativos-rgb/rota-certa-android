"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "public-agenda-shell-0569.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

test("published future trips are available from the permanent public agenda", () => {
  assert.match(api, /PUBLIC_STATUSES\.has\(trip\.status\).*departureAtMillis/s);
  assert.doesNotMatch(api, /PUBLIC_STATUSES\.has\(trip\.status\) && trip\.publicBookingEnabled === true/);
  assert.doesNotMatch(api, /PUBLIC_STATUSES\.has\(data\.status\) \|\| data\.publicBookingEnabled !== true/);
  assert.doesNotMatch(api, /PUBLIC_STATUSES\.has\(trip\.status\) \|\| trip\.publicBookingEnabled !== true/);
  assert.match(api, /departureAtMillis.*<= Date\.now\(\)/s);
});

test("public booking is idempotent and transactionally reconciles segment capacity", () => {
  assert.match(api, /Idempotency-Key/);
  assert.match(api, /publicBookingId\(token, idempotencyKey\)/);
  assert.match(api, /idempotencyFingerprint/);
  assert.match(api, /existingAttempt\.exists/);
  assert.match(api, /const candidateRecords = \[\.\.\.existing, candidate\]/);
  assert.match(api, /reconciledSegmentCapacity\(trip, candidateRecords, now\)/);
  assert.match(api, /assertNoOperationalOverbooking\(trip, candidateRecords, now\)/);
  assert.match(api, /assertNoOverbooking/);
  assert.match(api, /db\.runTransaction/);
  assert.doesNotMatch(api, /loads\[index\]\s*=\s*\(loads\[index\].*\+\s*seats/);
});

test("backend validates Brazilian WhatsApp and public source", () => {
  assert.match(api, /normalizeBrazilWhatsapp/);
  assert.match(api, /source: "ROTA_CERTA"/);
  assert.match(api, /sourceReference: `PUBLIC_LINK:/);
});

test("mobile portal reserves directly after phone verification with dynamic seat limits and idempotency", () => {
  assert.match(web, /openBooking0623/);
  assert.match(web, /signInWithPhoneNumber/);
  assert.match(web, /phoneConfirmation0623\.confirm\(code\)/);
  assert.match(web, /bookingSeats0623 = Math\.min\(max, bookingSeats0623 \+ 1\)/);
  assert.match(web, /bookingIdempotencyKey0623/);
  assert.match(web, /"Idempotency-Key": idempotencyKey/);
  assert.match(web, /\/v1\/public\/passenger-phone-session/);
  assert.match(web, /\/v1\/public\/trips\/.*\/bookings/);
  assert.match(html, /id="bookingPhone0623"/);
  assert.match(html, /id="bookingOtp0623"/);
  assert.match(html, />RECEBER CÓDIGO</);
  assert.match(html, />CONFIRMAR E SOLICITAR RESERVA</);
  assert.match(html, /Você não precisa criar senha/);
});

test("driver validates the permanent public agenda token before sharing without self-healing", () => {
  assert.match(api, /async function ensureDriverPublicAgenda/);
  assert.match(api, /publicAgendaLinkHash/);
  assert.match(api, /tokenIsCurrent/);
  assert.match(api, /agenda_token_mismatch/);
  assert.match(api, /PUBLIC_LINK_PRESERVED/);
  assert.match(api, /\/v1\/driver\/agenda\/ensure/);
  assert.match(api, /repaired: false/);
});
