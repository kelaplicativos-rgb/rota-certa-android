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
  assert.match(source, /reconciledSegmentLoads\(trip, candidates, now\)/);
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
