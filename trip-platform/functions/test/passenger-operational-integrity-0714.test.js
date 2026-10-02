const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");

test("0714 explicit null coordinates are authoritative clears", () => {
  assert.match(source, /hasBoardingLatitude = Object\.prototype\.hasOwnProperty\.call\(input, "boardingLatitude"\)/);
  assert.match(source, /hasDropoffLongitude = Object\.prototype\.hasOwnProperty\.call\(input, "dropoffLongitude"\)/);
  assert.match(source, /hasBoardingLatitude \? input\.boardingLatitude : prior\.boardingLatitude/);
});

test("0717 physical car capacity is the backend seat ceiling", () => {
  const fn = source
    .split("function operationalSeatLimit(trip, records = [], now = Date.now()) {")[1]
    .split("\n}")[0];
  assert.match(fn, /physicalSeatCapacity/);
  assert.match(fn, /return physical/);
  assert.doesNotMatch(fn, /blablaAvailable \+ confirmedPeak \+ rotaCertaAllocated/);
});

test("0714 inventory invariant evaluates the atomic incoming claim set", () => {
  const candidate = source.indexOf("const candidateRecords = preserveManagedClaims0436");
  const invariant = source.indexOf("const expectedInventory0714 = operationalSeatLimit(candidateTrip, candidateRecords, now)");
  assert.ok(candidate >= 0);
  assert.ok(invariant > candidate);
  assert.doesNotMatch(
    source.slice(source.indexOf('const bookingsSnap = await tx.get(tripRef.collection("bookings"))'), candidate),
    /inventory_mismatch/,
  );
});
