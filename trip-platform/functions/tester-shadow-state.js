"use strict";

function cents(value) {
  const number = Math.floor(Number(value || 0));
  return Number.isFinite(number) ? Math.max(0, number) : 0;
}

function initialTesterCredits(seedCents = 0) {
  const seed = cents(seedCents);
  return {
    seedCents: seed,
    balanceCents: seed,
    earnedCents: seed,
    spentCents: 0,
    entries: seed > 0 ? [{
      id: "tester_credit_baseline",
      type: "TESTER_BASELINE",
      amountCents: seed,
      createdAtMillis: 0,
    }] : [],
  };
}

function normalizeTesterCredits(raw, seedCents = 0) {
  const fallback = initialTesterCredits(seedCents);
  const source = raw && typeof raw === "object" ? raw : fallback;
  return {
    seedCents: cents(source.seedCents || fallback.seedCents),
    balanceCents: cents(source.balanceCents),
    earnedCents: cents(source.earnedCents),
    spentCents: cents(source.spentCents),
    entries: Array.isArray(source.entries) ? source.entries.filter(Boolean).slice(-100) : [],
  };
}

function appendTesterCreditEntry(creditsInput, entry) {
  const credits = normalizeTesterCredits(creditsInput, creditsInput && creditsInput.seedCents);
  const id = String(entry && entry.id || "");
  if (!id || credits.entries.some((item) => String(item && item.id || "") === id)) return credits;
  return { ...credits, entries: [...credits.entries, { ...entry, id }].slice(-100) };
}

function applyTesterCredits(creditsInput, requestedCents, totalFareCents, entry = null) {
  const credits = normalizeTesterCredits(creditsInput, creditsInput && creditsInput.seedCents);
  const applied = Math.min(cents(requestedCents), cents(totalFareCents), credits.balanceCents);
  let next = {
    ...credits,
    balanceCents: credits.balanceCents - applied,
    spentCents: credits.spentCents + applied,
  };
  if (applied > 0 && entry) next = appendTesterCreditEntry(next, { ...entry, amountCents: -applied });
  return { credits: next, appliedCents: applied, amountDueCents: Math.max(0, cents(totalFareCents) - applied) };
}

function refundTesterCredits(creditsInput, amountCents, entry = null) {
  const credits = normalizeTesterCredits(creditsInput, creditsInput && creditsInput.seedCents);
  const refund = cents(amountCents);
  if (refund <= 0) return { credits, refundedCents: 0 };
  let next = {
    ...credits,
    balanceCents: credits.balanceCents + refund,
    spentCents: Math.max(0, credits.spentCents - refund),
  };
  if (entry) next = appendTesterCreditEntry(next, { ...entry, amountCents: refund });
  return { credits: next, refundedCents: refund };
}

function normalizeTesterNotifications(raw) {
  return Array.isArray(raw) ? raw.filter(Boolean).slice(-100) : [];
}

function appendTesterNotification(raw, notification) {
  const list = normalizeTesterNotifications(raw);
  const id = String(notification && notification.id || "");
  if (!id || list.some((item) => String(item && item.id || "") === id)) return list;
  return [...list, { ...notification, id, read: notification.read === true }].slice(-100);
}

function markTesterNotificationRead(raw, notificationId) {
  const id = String(notificationId || "");
  return normalizeTesterNotifications(raw).map((item) => String(item && item.id || "") === id ? { ...item, read: true } : item);
}

function markAllTesterNotificationsRead(raw) {
  return normalizeTesterNotifications(raw).map((item) => ({ ...item, read: true }));
}

module.exports = {
  initialTesterCredits,
  normalizeTesterCredits,
  appendTesterCreditEntry,
  applyTesterCredits,
  refundTesterCredits,
  normalizeTesterNotifications,
  appendTesterNotification,
  markTesterNotificationRead,
  markAllTesterNotificationsRead,
};
