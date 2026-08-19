"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");

test("all booking sources feed one reconciled physical capacity", () => {
  assert.match(api, /function reconciledSegmentLoads/);
  assert.match(api, /occupancyGroupId/);
  assert.match(api, /group:\$\{group\}/);
  assert.match(api, /Math\.max|seats > previous/);
  assert.match(api, /source: "ROTA_CERTA"/);
  assert.match(api, /DRIVER_BOOKING_SOURCES/);
});

test("driver can sync private and BlaBlaCar claims but cannot overwrite public bookings", () => {
  assert.match(api, /upsertDriverCapacityBooking/);
  assert.match(api, /capacity_reconciliation_failed/);
  assert.match(api, /protected_booking/);
  assert.match(api, /parts\.length === 6.*req\.method === "PUT"/s);
});

test("public booking and cancellation recompute authoritative segment loads inside transactions", () => {
  assert.match(api, /tx\.get\(tripRef\.collection\("bookings"\)\)/);
  assert.match(api, /reconciledSegmentLoads\(trip, \[\.\.\.existing, candidate\]/);
  assert.match(api, /reconciledRecords/);
  assert.match(api, /assertNoOverbooking/);
  assert.doesNotMatch(api, /loads\[index\]\s*=\s*\(loads\[index\].*\+\s*seats/);
});
