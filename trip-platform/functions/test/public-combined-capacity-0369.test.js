const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..", "..");
const publicApp = fs.readFileSync(path.join(root, "public", "app.js"), "utf8");
const backend = fs.readFileSync(path.join(root, "functions", "index.js"), "utf8");

test("public UI separates physical capacity, allocations, confirmed passengers and remaining seats", () => {
  assert.match(publicApp, /Capacidade de passageiros/);
  assert.match(publicApp, /Passageiros confirmados/);
  assert.match(publicApp, /Vagas disponíveis/);
  assert.match(publicApp, /BlaBlaCar/);
  assert.match(publicApp, /Rota Certa/);
  assert.match(publicApp, /Total considerado/);
  assert.doesNotMatch(publicApp, /blablaAvailableSeats/);
  assert.doesNotMatch(publicApp, /rotaCertaSeatPool/);
  assert.doesNotMatch(publicApp, /Disponibilidade combinada/);
});

test("operational total is published seats plus the explicit Rota Certa allocation, never physical capacity", () => {
  assert.match(backend, /function operationalSeatLimit\(trip\)/);
  assert.match(backend, /trip && trip\.publishedSeats/);
  assert.match(backend, /trip && trip\.rotaCertaSeatAllocation/);
  assert.match(backend, /return Math\.min\(999, blabla \+ rotaCerta\)/);
  assert.doesNotMatch(backend, /blablaAvailableSeats/);
  assert.doesNotMatch(backend, /rotaCertaSeatPool/);
});

test("confirmed passengers, blocks and duplicate mirrors share one canonical occupancy group", () => {
  assert.match(backend, /function reconciledOperationalSeatSummary\(trip, records/);
  assert.match(backend, /occupancyGroupId/);
  assert.match(backend, /claimType === "PASSENGER" \|\| claimType === "EXTERNAL_OCCUPANCY"/);
  assert.match(backend, /record\.status === "CONFIRMED"/);
  assert.match(backend, /confirmedPassengerSeats/);
  assert.match(backend, /blockedSeats/);
  assert.match(backend, /operationalAvailableSeats/);
  assert.match(backend, /operationalOverbookingSeats/);
});

test("reservation availability is the lower of physical segment seats and operational remaining seats", () => {
  assert.match(backend, /function availableForBooking\(trip, records, loads, fromIndex, toIndex/);
  assert.match(backend, /Math\.min\(physical, operational\)/);
  assert.match(backend, /assertNoOperationalOverbooking/);
  assert.match(backend, /availableForBooking\(trip, existing, currentLoads, fromIndex, toIndex\)/);
  assert.match(backend, /availableForBooking\(trip, candidateRecords, reconciled, fromIndex, toIndex, now\)/);
});

test("public payload exposes segment passengers, blocked seats and operational totals separately", () => {
  assert.match(backend, /segmentPassengerLoads/);
  assert.match(backend, /segmentBlockedLoads/);
  assert.match(backend, /confirmedPassengerSeats/);
  assert.match(backend, /blockedSeats/);
  assert.match(backend, /totalConsideredSeats/);
  assert.match(backend, /operationalAvailableSeats/);
  assert.match(backend, /rotaCertaSeatAllocation/);
  assert.match(backend, /publishedSeats: data\.publishedSeats/);
});

test("existing external BlaBla trip may refresh configured limits without weakening stop protection", () => {
  assert.match(backend, /const externalBlaBlaProjection = isExternalBlaBlaTrip\("", previous\);/);
  assert.match(backend, /protectedCapacityChange = capacity !== Number\(previous\.capacity \|\| 0\) && !externalBlaBlaProjection/);
  assert.match(backend, /oldStopIds !== newStopIds/);
  assert.match(backend, /assertNoOverbooking\(candidateTrip, loads\);/);
  assert.match(backend, /assertNoOperationalOverbooking\(candidateTrip, records\);/);
});
