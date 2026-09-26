"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  initialTesterCredits,
  applyTesterCredits,
  refundTesterCredits,
  appendTesterNotification,
  markTesterNotificationRead,
  markAllTesterNotificationsRead,
} = require("../tester-shadow-state");

test("tester credit ledger is isolated, bounded and reversible", () => {
  const baseline = initialTesterCredits(1000);
  const used = applyTesterCredits(baseline, 800, 600, { id: "use_b1", type: "TESTER_BOOKING_CREDIT_USED" });
  assert.equal(used.appliedCents, 600);
  assert.equal(used.amountDueCents, 0);
  assert.equal(used.credits.balanceCents, 400);
  assert.equal(used.credits.spentCents, 600);
  assert.equal(baseline.balanceCents, 1000);

  const refunded = refundTesterCredits(used.credits, 600, { id: "refund_b1", type: "TESTER_BOOKING_CREDIT_REFUND" });
  assert.equal(refunded.credits.balanceCents, 1000);
  assert.equal(refunded.credits.spentCents, 0);
  assert.deepEqual(refunded.credits.entries.map((entry) => entry.id), ["tester_credit_baseline", "use_b1", "refund_b1"]);
});

test("tester notifications are idempotent and isolated per state object", () => {
  const a0 = [];
  const a1 = appendTesterNotification(a0, { id: "n1", title: "A" });
  const a2 = appendTesterNotification(a1, { id: "n1", title: "duplicate" });
  const b1 = appendTesterNotification([], { id: "n1", title: "B" });
  assert.equal(a2.length, 1);
  assert.equal(a2[0].title, "A");
  assert.equal(b1[0].title, "B");
  assert.equal(a0.length, 0);
  assert.equal(markTesterNotificationRead(a2, "n1")[0].read, true);
  assert.ok(markAllTesterNotificationsRead([{ id: "n2", read: false }, { id: "n3", read: false }]).every((item) => item.read));
});
