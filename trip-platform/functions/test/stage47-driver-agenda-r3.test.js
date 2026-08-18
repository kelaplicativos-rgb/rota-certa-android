"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.resolve(__dirname, "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const calendar = fs.readFileSync(path.join(root, "calendar-functions", "index.js"), "utf8");
const page = fs.readFileSync(path.join(root, "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(root, "public", "index.html"), "utf8");

test("each driver can claim a unique username with separate admin and public tokens", () => {
  assert.match(api, /\/v1\/drivers\/register/);
  assert.match(api, /tripDrivers/);
  assert.match(api, /driverTokenHash/);
  assert.match(api, /agendaTokenHash/);
  assert.match(api, /X-Rota-Certa-Driver-Username/);
});

test("public agenda is scoped to driver username plus public token", () => {
  assert.match(api, /getPublicDriverAgenda/);
  assert.match(api, /driverUsername/);
  assert.match(page, /motorista/);
  assert.match(page, /agendaToken/);
  assert.match(calendar, /tripDrivers/);
  assert.match(calendar, /driverUsername/);
});

test("route fares are public but passenger private data stays private", () => {
  assert.match(api, /priceToNextCents/);
  assert.match(api, /farePerSeatCents/);
  assert.match(api, /totalFareCents/);
  assert.match(page, /fareFor/);
  const safeStart = api.indexOf("function safePublicTrip");
  const safeEnd = api.indexOf("function clientIp", safeStart);
  const safeBlock = api.slice(safeStart, safeEnd);
  assert.doesNotMatch(safeBlock, /passengerContact|cancellationHash/);
});

test("passenger can cancel own reservation without exposing cancellation token in agenda link", () => {
  assert.match(page, /cancelReservation/);
  assert.match(page, /\/cancel/);
  assert.match(html, /Cancelar minha reserva/);
  assert.doesNotMatch(page, /agendaToken.*cancellationToken|cancellationToken.*agendaToken/);
});

test("Google Calendar remains an optional mirror, not the seat authority", () => {
  assert.match(html, /Google Agenda/);
  assert.match(page, /calendar\.google\.com/);
  assert.match(api, /db\.runTransaction/);
  assert.match(api, /insufficient_seats/);
});
