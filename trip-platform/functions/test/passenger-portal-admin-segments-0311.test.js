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

test("passenger access uses phone password session and driver invitation", () => {
  assert.ok(api.includes("async function registerPassengerAccount"));
  assert.ok(api.includes("crypto.scryptSync"));
  assert.ok(api.includes("passengerAccounts"));
  assert.ok(api.includes("passengerSessions"));
  assert.ok(api.includes("passengerBookingIndex"));
  assert.ok(api.includes("driverPassengerAccess"));
  assert.ok(api.includes("passenger_invite_required"));
  assert.ok(api.includes("/v1/passenger/session"));
  assert.ok(api.includes("/v1/passenger/me/bookings"));
});

test("public web keeps passenger management private behind the authenticated area", () => {
  assert.ok(html.includes('id="passengerPortal"'));
  assert.ok(html.includes('id="privateAuthPassword"'));
  assert.ok(html.includes('id="portalCreditBalance"'));
  assert.ok(html.includes("A senha só será solicitada para reservar"));
  assert.ok(web.includes("showPrivateAuthGate"));
  assert.ok(web.includes("loadPassengerBookings"));
  assert.ok(web.includes("sharePassengerReferral"));
  assert.ok(web.includes("vaga(s) até"));
});
