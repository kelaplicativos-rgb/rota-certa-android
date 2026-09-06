"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");

test("public API exposes authoritative availability fields", () => {
  assert.match(api, /availableSeatsMinimum/);
  assert.match(api, /availableSeatsMaximum/);
  assert.match(api, /isFull: capacityReliable && fullyOccupied/);
  assert.match(api, /canReserve: data\.publicBookingEnabled === true && capacityReliable && !fullyOccupied && availableMaximum > 0/);
  assert.match(api, /capacityReliable/);
  assert.match(api, /availableSeatsMinimum/);
  assert.match(api, /confirmedPassengerSeats/);
  assert.match(api, /blockedSeats/);
  assert.match(api, /operationalAvailableSeats/);
});

test("FULL trips fail closed even if stale segment loads exist", () => {
  assert.match(api, /if \(trip\.status === "FULL"\)/);
  assert.match(api, /code: "trip_full"/);
  assert.match(api, /Esta viagem está lotada/);
});

test("agenda UI keeps full trips non-interactive while available trips expose current reservation actions", () => {
  assert.match(web, /const card = document\.createElement\("article"\)/);
  assert.match(web, /if \(soldOut \|\| full\)/);
  assert.match(web, /fullWord\.textContent = "LOTADO"/);
  assert.match(web, /if \(actionsEnabled\) \{/);
  assert.match(web, /whatsapp\.textContent = "Reservar pelo WhatsApp"/);
  assert.match(web, /agendaTripFull/);
  assert.match(web, /return "🪑 LOTADO"/);
  assert.match(web, /return "Disponibilidade sendo atualizada"/);
  assert.match(web, /show\("tripSticky", canUseExternalActions\)/);
});
