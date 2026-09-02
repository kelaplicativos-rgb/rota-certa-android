"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const {
  normalizeProfileScope0417,
  normalizeSyncPolicy0417,
  safeVisibility0417,
  redact0417,
} = require("../agenda-admin-0417");

test("sync policy clamps to Android WorkManager-safe bounds", () => {
  assert.deepEqual(normalizeSyncPolicy0417({ automatic: false, intervalMinutes: 1 }), {
    automatic: false,
    intervalMinutes: 15,
  });
  assert.equal(normalizeSyncPolicy0417({ automatic: true, intervalMinutes: 99999 }).intervalMinutes, 1440);
});

test("public profile scope uses strong UUID-like identities and deduplicates", () => {
  assert.deepEqual(
    normalizeProfileScope0417([
      "7371f028-9c55-4903-8444-308015823efd",
      "7371F028-9C55-4903-8444-308015823-EFD".replace("-EFD", "efd"),
      "Barbosa",
      "",
    ]),
    ["7371f028-9c55-4903-8444-308015823efd"],
  );
});

test("visibility is explicit and defaults to visible for backward compatibility", () => {
  const policy = safeVisibility0417({ whatsapp: false, vehicle: false });
  assert.equal(policy.whatsapp, false);
  assert.equal(policy.vehicle, false);
  assert.equal(policy.name, true);
  assert.equal(policy.reviews, true);
});

test("exports redact credentials recursively", () => {
  const sanitized = redact0417({
    password: "secret",
    nested: { sessionToken: "token", cookie: "cookie", value: "kept" },
    list: [{ apiKey: "key", event: "PUBLIC_AGENDA_LOADED" }],
  });
  assert.equal(sanitized.password, "[REDACTED]");
  assert.equal(sanitized.nested.sessionToken, "[REDACTED]");
  assert.equal(sanitized.nested.value, "kept");
  assert.equal(sanitized.list[0].apiKey, "[REDACTED]");
});

test("backend routes require admin session for protected administration", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  const admin = fs.readFileSync(path.join(__dirname, "..", "agenda-admin-0417.js"), "utf8");
  assert.match(source, /\/v1\/public\/admin\/session/);
  assert.match(source, /\/v1\/admin\/sync\/update-now/);
  assert.match(source, /\/v1\/admin\/sync\/reconcile/);
  assert.match(source, /\/public-attestation/);
  assert.match(admin, /requireAdminSession0417\(req, res\)/);
  assert.match(admin, /driverUsername.*session\.driverUsername/s);
  assert.doesNotMatch(admin, /passwordHash:\s*password/);
});

test("public visibility and profile filters are applied in server projection", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  assert.match(source, /publicVisibilityPolicy0417/);
  assert.match(source, /publicTripProfileUuids0417/);
  assert.match(source, /publicProfileScope0417\.has\(profileUuid\)/);
});

test("admin browser keeps session in sessionStorage and never persists password", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "..", "public", "admin-0417.js"), "utf8");
  assert.match(source, /sessionStorage\.setItem\(sessionKey/);
  assert.doesNotMatch(source, /localStorage\.setItem\([^\n]*admin/i);
  assert.doesNotMatch(source, /setItem\([^\n]*password/i);
  assert.match(source, /X-Rota-Certa-Admin-Session/);
});
