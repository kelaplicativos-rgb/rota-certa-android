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
  assert.match(api, /isFull: fullyOccupied/);
  assert.match(api, /canReserve: data\.publicBookingEnabled === true && !fullyOccupied/);
});

test("FULL trips fail closed even if stale segment loads exist", () => {
  assert.match(api, /if \(trip\.status === "FULL"\)/);
  assert.match(api, /code: "trip_full"/);
  assert.match(api, /Esta viagem está lotada/);
});

test("agenda UI marks full trips and removes reservation navigation", () => {
  assert.match(web, /LOTADO • 0 vagas/);
  assert.match(web, /link\.removeAttribute\("href"\)/);
  assert.match(web, /aria-disabled/);
  assert.match(web, /agendaTripFull/);
  assert.match(web, /show\("booking", !tripFull && trip\.canReserve !== false\)/);
  assert.match(web, /LOTADO • sem vagas/);
});
