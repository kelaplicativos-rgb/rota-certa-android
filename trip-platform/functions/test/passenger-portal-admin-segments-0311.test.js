"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

test("protected public booking remains protected from generic reconciliation", () => {
  assert.ok(api.includes('code: "protected_booking"'));
  assert.ok(api.includes('previous.source === "ROTA_CERTA" || previous.cancellationHash'));
  assert.ok(api.includes("async function mutateProtectedBooking"));
  assert.ok(api.includes('parts[6] === "admin"'));
});

test("administrative mutation remains capacity safe", () => {
  assert.ok(api.includes("reconciledSegmentLoads(trip, candidateRecords, now)"));
  assert.ok(api.includes("assertNoOverbooking(trip, loads)"));
  assert.ok(api.includes("statusForReconciledLoads(trip, loads)"));
});

test("passenger access uses phone password session and reservation proof", () => {
  assert.ok(api.includes("async function registerPassengerAccount"));
  assert.ok(api.includes("crypto.scryptSync"));
  assert.ok(api.includes("passengerAccounts"));
  assert.ok(api.includes("passengerSessions"));
  assert.ok(api.includes("passengerBookingIndex"));
  assert.ok(api.includes("/v1/passenger/session"));
  assert.ok(api.includes("/v1/passenger/me/bookings"));
});

test("public web exposes passenger reservation management", () => {
  assert.ok(html.includes('id="passengerPortal"'));
  assert.ok(html.includes('id="portalContact"'));
  assert.ok(html.includes('id="portalPassword"'));
  assert.ok(html.includes('id="portalRegister"'));
  assert.ok(web.includes("loginPassengerPortal"));
  assert.ok(web.includes("loadPassengerBookings"));
  assert.ok(web.includes("registerPassengerPortal"));
  assert.ok(web.includes("vaga(s) até"));
});


test("public agenda stays clean neutral and browser-geolocation free", () => {
  assert.ok(!html.includes("--blue:"));
  assert.ok(!html.includes("Cidade, estação, local"));
  assert.ok(!html.includes(">Procurar<"));
  assert.ok(!web.includes("navigator.geolocation"));
  assert.ok(!web.includes("getCurrentPosition("));
  assert.ok(html.includes("--accent:#171717"));
  assert.ok(html.includes(".primary{background:#171717;color:#fff}"));
  assert.ok(html.includes(".agendaArrow{font-size:30px;color:var(--ink)"));
});


test("passenger area entry remains prominent and self explanatory", () => {
  assert.ok(html.includes('id="openPassengerPortal" class="passengerAreaEntry"'));
  assert.ok(html.includes('<span class="passengerAreaTitle">Minha área</span>'));
  assert.ok(html.includes('<span class="passengerAreaSub">Reservas e conta</span>'));
  assert.ok(html.includes('<h1 class="stepTitle">Minha área</h1>'));
  assert.ok(html.includes('<h2 style="margin-top:24px">Minhas reservas</h2>'));
});
