"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const backend = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");

function operationalWindowContext0577() {
  const start = backend.indexOf("const PUBLIC_AGENDA_ARRIVAL_GRACE_MILLIS_0577");
  const end = backend.indexOf("\nfunction publicAgendaTripVisibility0466(", start);
  assert.ok(start >= 0 && end > start, "missing 0577 operational window implementation");
  const code = backend.slice(start, end) +
    "\nresult = { publicAgendaVisibleUntil0577, publicAgendaTripStillVisible0577 };";
  const context = { Date, Math, Number, Array, result: null };
  vm.runInNewContext(code, context);
  return context.result;
}

function trip0577(departure, arrival) {
  return {
    departureAtMillis: departure,
    arrivalAtMillis: arrival || 0,
    stops: [
      { order: 0, plannedDepartureMillis: departure },
      { order: 1, plannedArrivalMillis: arrival || 0 },
    ],
  };
}

test("ongoing public trip remains visible after departure", () => {
  const api = operationalWindowContext0577();
  const departure = 1_000_000;
  const arrival = departure + 4 * 60 * 60 * 1000;
  assert.equal(api.publicAgendaTripStillVisible0577(trip0577(departure, arrival), departure + 1), true);
  assert.equal(api.publicAgendaTripStillVisible0577(trip0577(departure, arrival), arrival), true);
});

test("public trip remains visible through arrival plus one hour then expires", () => {
  const api = operationalWindowContext0577();
  const departure = 1_000_000;
  const arrival = departure + 4 * 60 * 60 * 1000;
  const grace = 60 * 60 * 1000;
  assert.equal(api.publicAgendaTripStillVisible0577(trip0577(departure, arrival), arrival + grace), true);
  assert.equal(api.publicAgendaTripStillVisible0577(trip0577(departure, arrival), arrival + grace + 1), false);
});

test("missing arrival never hides the card at departure and uses safe retention", () => {
  const api = operationalWindowContext0577();
  const departure = 1_000_000;
  const retention = 12 * 60 * 60 * 1000;
  const trip = trip0577(departure, null);
  assert.equal(api.publicAgendaTripStillVisible0577(trip, departure + 1), true);
  assert.equal(api.publicAgendaTripStillVisible0577(trip, departure + retention), true);
  assert.equal(api.publicAgendaTripStillVisible0577(trip, departure + retention + 1), false);
});
