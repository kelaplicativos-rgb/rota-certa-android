const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..", "..");
const publicApp = fs.readFileSync(path.join(root, "public", "app.js"), "utf8");
const backend = fs.readFileSync(path.join(root, "functions", "index.js"), "utf8");

test("public UI shows current free seats by channel and their direct total", () => {
  assert.match(publicApp, /Capacidade de passageiros/);
  assert.match(publicApp, /Passageiros confirmados/);
  assert.match(publicApp, /Vagas disponíveis/);
  assert.match(publicApp, /BlaBlaCar/);
  assert.match(publicApp, /Rota Certa/);
  assert.match(publicApp, /Total disponível/);
  assert.match(publicApp, /blablaAvailableSeats/);
  assert.match(publicApp, /rotaCertaAvailableSeats/);
  assert.match(publicApp, /totalAvailableSeats/);
  assert.doesNotMatch(publicApp, /Disponibilidade combinada/);
});

test("BlaBla seat editor value is accepted independently from physical vehicle capacity", () => {
  assert.match(backend, /rawPublishedSeats >= 0 && rawPublishedSeats <= 999/);
  assert.doesNotMatch(backend, /rawPublishedSeats >= 0 && rawPublishedSeats <= capacity/);
});

test("operational availability is BlaBla free seats plus remaining Rota Certa free seats", () => {
  assert.match(backend, /const blablaAvailableSeats/);
  assert.match(backend, /const rotaCertaAvailableSeats/);
  assert.match(backend, /const totalAvailableSeats = Math\.min\(999, blablaAvailableSeats \+ rotaCertaAvailableSeats\)/);
  assert.match(backend, /operationalAvailableSeats: totalAvailableSeats/);
});

test("BlaBla confirmed occupancy is kept for passengers and physical segments without subtracting free seats twice", () => {
  assert.match(backend, /externalConfirmed/);
  assert.match(backend, /localConfirmed/);
  assert.match(backend, /extraLocalBeyondExternal/);
  assert.match(backend, /source === "BLABLACAR"/);
  assert.match(backend, /confirmedPassengerSeats/);
});

test("public payload exposes the exact free-seat breakdown", () => {
  assert.match(backend, /blablaAvailableSeats/);
  assert.match(backend, /rotaCertaAvailableSeats/);
  assert.match(backend, /totalAvailableSeats/);
  assert.match(backend, /operationalAvailableSeats/);
  assert.match(backend, /physicalAvailableSeatsMinimum/);
  assert.match(backend, /physicalAvailableSeatsMaximum/);
});

test("transactional booking validation still keeps physical segment and operational guards", () => {
  assert.match(backend, /function availableForBooking\(trip, records, loads, fromIndex, toIndex/);
  assert.match(backend, /Math\.min\(physical, operational\)/);
  assert.match(backend, /assertNoOperationalOverbooking/);
  assert.match(backend, /assertNoOverbooking/);
});

test("existing external BlaBla trip may refresh configured data without weakening stop protection", () => {
  assert.match(backend, /const externalBlaBlaProjection = isExternalBlaBlaTrip\("", previous\);/);
  assert.match(backend, /protectedCapacityChange = capacity !== Number\(previous\.capacity \|\| 0\) && !externalBlaBlaProjection/);
  assert.match(backend, /oldStopIds !== newStopIds/);
});
