const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");

test("0.1.356 creates one canonical REQUESTED booking that protects capacity", () => {
  assert.match(source, /record\.status === "REQUESTED" \|\| record\.status === "CONFIRMED"/);
  assert.match(source, /status: "REQUESTED",\s*operationalStatus: "PENDING"/);
  assert.match(source, /occupancyGroupId: bookingId/);
  assert.match(source, /eventType: "RESERVATION_REQUESTED"/);
});

test("0.1.356 approves or rejects transactionally and idempotently", () => {
  assert.match(source, /async function mutateDriverBookingDecision/);
  assert.match(source, /targetStatus = action === "APPROVE" \? "CONFIRMED" : "REJECTED"/);
  assert.match(source, /previous\.status === targetStatus/);
  assert.match(source, /previous\.status !== "REQUESTED"/);
  assert.match(source, /invalid_pending_capacity_claim/);
  assert.match(source, /missing_pending_occupancy_group/);
  assert.match(source, /reconciledSegmentCapacity\(trip, candidates, now\)/);
  assert.match(source, /assertNoOperationalOverbooking\(trip, candidates, now\)/);
  assert.match(source, /RESERVATION_APPROVED/);
  assert.match(source, /RESERVATION_REJECTED/);
});

test("0.1.356 keeps notification read separate from resolution and protects structural edits", () => {
  assert.match(source, /pending_reservations_require_decision/);
  const start = source.indexOf("async function markDriverNotificationRead");
  const end = source.indexOf("\nasync function markPassengerNotificationRead", start);
  const markRead = source.slice(start, end);
  assert.match(markRead, /const now = Date\.now\(\)/);
  assert.match(markRead, /readAtMillis: now/);
  assert.doesNotMatch(markRead, /status:\s*"CONFIRMED"/);
  assert.doesNotMatch(markRead, /status:\s*"REJECTED"/);
});

test("last-seat contention is serialized by the same Firestore trip transaction", () => {
  const start = source.indexOf("async function createBooking");
  const end = source.indexOf("\nasync function cancelPublicBooking", start);
  const create = source.slice(start, end);
  assert.ok(start >= 0 && end > start);
  assert.match(create, /db\.runTransaction\(async \(tx\) =>/);
  assert.match(create, /const tripSnap = await tx\.get\(tripRef\)/);
  assert.match(create, /tx\.get\(tripRef\.collection\("bookings"\)\)/);
  assert.match(create, /const currentLoads = reconciledSegmentLoads\(trip, existing\)/);
  assert.match(create, /const available = availableForBooking\(trip, existing, currentLoads, fromIndex, toIndex\)/);
  assert.match(create, /if \(seats > available\)/);
  assert.match(create, /code: "insufficient_seats", availableSeats: available/);
  assert.match(create, /assertNoOverbooking\(trip, reconciled\)/);
  assert.match(create, /tx\.create\(bookingRef, candidatePersisted\)/);
  assert.match(create, /tx\.update\(tripRef, canonicalServerProjectionPatch0468\(/);\n  assert.match(create, /canonicalCapacityPersistence\(trip, candidateRecords, reconciledCapacityState, now\)/);
  assert.ok(create.indexOf("if (seats > available)") < create.indexOf("tx.create(bookingRef, candidatePersisted)"));
  assert.ok(create.indexOf("assertNoOverbooking(trip, reconciled)") < create.indexOf("tx.create(bookingRef, candidatePersisted)"));
});

test("booking edit releases its own old claim only for revalidation and remains all-or-nothing", () => {
  const start = source.indexOf("async function updatePublicBooking");
  const end = source.indexOf("\nasync function mutateDriverBookingDecision", start);
  const update = source.slice(start, end);
  assert.ok(start >= 0 && end > start);
  assert.match(update, /db\.runTransaction\(async \(tx\) =>/);
  assert.match(update, /capacityIsReliable\(token, trip\)/);
  assert.match(update, /otherRecords = records\.filter\(\(record\) => record\.id !== bookingId\)/);
  assert.match(update, /currentLoads = reconciledSegmentLoads\(trip, otherRecords, capacityCheckAtMillis\)/);
  assert.match(update, /available = availableForBooking\(trip, otherRecords, currentLoads, fromIndex, toIndex, capacityCheckAtMillis\)/);
  assert.match(update, /if \(seats > available\)/);
  assert.match(update, /currentSeatCapacityMessage\(available\)/);
  assert.match(update, /candidateRecords = records\.map\(\(record\) => record\.id === bookingId \? updated : record\)/);
  assert.match(update, /assertNoOverbooking\(trip, loads\)/);
  assert.ok(update.indexOf("if (seats > available)") < update.indexOf("tx.set(bookingRef, updatedPersisted"));
});

test("REQUESTED keeps the canonical capacity claim and rejected or cancelled records do not", () => {
  assert.match(source, /record\.status === "REQUESTED" \|\| record\.status === "CONFIRMED"/);
  assert.match(source, /\["CANCELLED", "REJECTED", "EXPIRED"\]/);
  assert.match(source, /source: "ROTA_CERTA"/);
  assert.match(source, /capacityClaimType: "PASSENGER"/);
  assert.match(source, /occupancyGroupId: bookingId/);
});

test("manipulated seat quantities cannot bypass final authoritative availability", () => {
  const start = source.indexOf("async function createBooking");
  const end = source.indexOf("\nasync function cancelPublicBooking", start);
  const create = source.slice(start, end);
  assert.match(create, /!Number\.isInteger\(seats\) \|\| seats < 1 \|\| seats > 999/);
  assert.match(create, /availableForBooking\(trip, existing, currentLoads, fromIndex, toIndex\)/);
  assert.match(create, /if \(seats > available\)/);
  assert.match(create, /return fail\([\s\S]*capacityDetails/);
});

