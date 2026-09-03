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
  activeAdminTrips0417,
  validatedBlaBlaPublicUrl0417,
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

test("admin aggregates and technical list share the same active public scope", () => {
  const now = 2_000_000;
  const trips = [
    { departureAtMillis: now + 1_000, status: "PUBLISHED" },
    { departureAtMillis: now + 2_000, status: "FULL" },
    { departureAtMillis: now - 1, status: "PUBLISHED" },
    { departureAtMillis: now + 3_000, status: "CANCELLED" },
  ];
  assert.equal(activeAdminTrips0417(trips, now).length, 2);
});

test("BlaBla link is valid only for concrete public trip URL matching strong trip id", () => {
  const valid = validatedBlaBlaPublicUrl0417(
    "https://www.blablacar.com.br/trip?source=CARPOOLING&id=trip-123",
    "trip-123",
  );
  assert.match(valid, /^https:\/\/www\.blablacar\.com\.br\/trip/);
  assert.equal(validatedBlaBlaPublicUrl0417("https://www.blablacar.com.br/trip?id=other", "trip-123"), "");
  assert.equal(validatedBlaBlaPublicUrl0417("https://example.com/trip?id=trip-123", "trip-123"), "");
  assert.equal(validatedBlaBlaPublicUrl0417("", "trip-123"), "");
});

test("admin sync request is REQUESTED and SUCCESS health is guarded by attestation metrics", () => {
  const admin = fs.readFileSync(path.join(__dirname, "..", "agenda-admin-0417.js"), "utf8");
  const browser = fs.readFileSync(path.join(__dirname, "..", "..", "public", "admin-0417.js"), "utf8");
  assert.match(admin, /eventType: full \? "ADMIN_FULL_RECONCILE_REQUESTED" : "ADMIN_UPDATE_NOW_REQUESTED"[\s\S]{0,160}result: "REQUESTED"/);
  assert.match(admin, /requestedResult === "SUCCESS" && \(failures \|\| pending \|\| divergent \|\| readbackFailures\)/);
  assert.match(browser, /ignorados comprovados/);
  assert.doesNotMatch(browser, /trip\.blablaPublicUrl \|\| trip\.publicUrl/);
});

test("protected administration reuses scoped passenger session and role", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  const admin = fs.readFileSync(path.join(__dirname, "..", "agenda-admin-0417.js"), "utf8");
  assert.doesNotMatch(source, /\/v1\/public\/admin\/session/);
  assert.doesNotMatch(source, /\/v1\/driver\/admin\/password/);
  assert.match(source, /\/v1\/driver\/passengers\/admin/);
  assert.match(source, /\/v1\/admin\/sync\/update-now/);
  assert.match(source, /\/v1\/admin\/sync\/reconcile/);
  assert.match(source, /public-attestation/);
  assert.match(admin, /requirePassengerSession\(req, res\)/);
  assert.match(admin, /access\.agendaAdmin !== true/);
  assert.match(admin, /passengerAccessForIdentity/);
  assert.doesNotMatch(admin, /tripAdminSessions/);
});

test("public visibility and profile filters are applied in server projection", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  assert.match(source, /publicVisibilityPolicy0417/);
  assert.match(source, /publicTripProfileUuids0417/);
  assert.match(source, /publicProfileScope0417\.has\(profileUuid\)/);
});

test("admin browser reuses Minhas Viagens bearer session without another password", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "..", "public", "admin-0417.js"), "utf8");
  const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");
  const app = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
  assert.match(source, /rotacerta-passenger-session/);
  assert.match(source, /Authorization.*Bearer/s);
  assert.match(source, /X-Rota-Certa-Admin-Driver/);
  assert.doesNotMatch(source, /X-Rota-Certa-Admin-Session/);
  assert.doesNotMatch(html, /id="openAgendaAdmin0417"/);
  assert.match(html, /id="portalAgendaAdminCard0418"/);
  assert.match(html, /id="openAgendaAdmin0418"/);
  assert.match(app, /passengerAgendaAdmin0418/);
  assert.match(app, /body\.agendaAdmin === true/);
});


test("driver grants admin to authorized passenger without requiring a second activation gate", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  const start = source.indexOf("async function setDriverPassengerAgendaAdmin0418");
  const end = source.indexOf("async function resetDriverPassengerPassword", start);
  assert.ok(start >= 0 && end > start);
  const block = source.slice(start, end);
  assert.match(block, /passengerAccessIsAuthorized\(access\)/);
  assert.doesNotMatch(block, /passengerAccountIsActivated/);
  assert.match(block, /PASSENGER_AGENDA_ADMIN_CHANGED/);
  assert.match(source, /agendaAdmin: blocking \? false/);
  assert.match(source, /\/v1\/passenger\/logout/);
});

test("admin role is tenant scoped on passenger access rather than global account", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  assert.match(source, /passengerAccessForIdentity\(driver\.username, passengerId, passengerContact\)/);
  assert.match(source, /driverPassengerAccess/);
  assert.doesNotMatch(source, /passengerAccounts[\s\S]{0,180}agendaAdmin/);
});


test("passenger session validation defines activity timestamp before using it", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  const start = source.indexOf("async function requirePassengerSession");
  const end = source.indexOf("async function logoutPassengerAccount", start);
  assert.ok(start >= 0 && end > start);
  const block = source.slice(start, end);
  assert.match(block, /const now = Date\.now\(\)/);
  assert.match(block, /lastActivityAtMillis: Math\.max\([^\n]*now\)/);
});
