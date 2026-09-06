"use strict";

const crypto = require("crypto");

function cleanIdentifier(value, max = 120) {
  return String(value || "").trim().replace(/[^A-Za-z0-9_-]/g, "").slice(0, max);
}

function hashToken(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function constantTimeEqual(a, b) {
  if (!a || !b) return false;
  const left = Buffer.from(String(a));
  const right = Buffer.from(String(b));
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

function tokenMatches(token, expectedHash) {
  const normalized = cleanIdentifier(token);
  return normalized.length >= 16 && constantTimeEqual(hashToken(normalized), expectedHash);
}

function deriveRotationToken(secret, username, rotationId) {
  const safeRotationId = cleanIdentifier(rotationId, 100);
  const safeUsername = String(username || "").trim().toLowerCase();
  if (!secret || safeUsername.length < 3 || safeRotationId.length < 16) return "";
  return crypto.createHmac("sha256", String(secret))
    .update(`agenda-link:${safeUsername}:${safeRotationId}`)
    .digest("base64url");
}

module.exports = {
  cleanIdentifier,
  hashToken,
  constantTimeEqual,
  tokenMatches,
  deriveRotationToken,
};
