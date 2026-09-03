"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const {
  normalizeProfileScope0417,
  normalizeSyncPolicy0417,
  sameSyncPolicy0417,
  authenticationRequired0417,
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
  assert.match(admin, /const eventType = full \? "ADMIN_FULL_RECONCILE_REQUESTED" : "ADMIN_UPDATE_NOW_REQUESTED"/);
  assert.match(admin, /result: "DISPATCHING"/);
  assert.match(admin, /result: "REQUESTED"/);
  assert.match(admin, /X-Rota-Certa-Operation-Id/);
  assert.match(admin, /requestedResult === "SUCCESS" && \(failures \|\| pending \|\| divergent \|\| readbackFailures\)/);
  assert.match(browser, /ignorados comprovados/);
  assert.doesNotMatch(browser, /trip\.blablaPublicUrl \|\| trip\.publicUrl/);
});

test("server MATCH requires current canonical revision as well as transport revision", () => {
  const admin = fs.readFileSync(path.join(__dirname, "..", "agenda-admin-0417.js"), "utf8");
  assert.match(admin, /requestedCanonicalRevision === currentCanonicalRevision/);
  assert.match(admin, /requestedRevision === currentRevision/);
  assert.match(admin, /readbackHash === expectedHash/);
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


test("passenger session activity returns the persisted value and admin mutations force a real touch", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  const admin = fs.readFileSync(path.join(__dirname, "..", "agenda-admin-0417.js"), "utf8");
  const start = source.indexOf("async function requirePassengerSession");
  const end = source.indexOf("async function logoutPassengerAccount", start);
  assert.ok(start >= 0 && end > start);
  const block = source.slice(start, end);
  assert.match(block, /let lastActivityAtMillis = Number\(data\.lastActivityAtMillis/);
  assert.match(block, /lastActivityAtMillis = now/);
  assert.doesNotMatch(block, /lastActivityAtMillis: Math\.max/);
  assert.match(source, /async function touchPassengerSessionActivity0427/);
  assert.match(admin, /touchAdminSession0417\(session\)/);
});

test("sync save is a no-op when requested policy already matches persisted policy", () => {
  assert.equal(sameSyncPolicy0417(
    { automatic: true, intervalMinutes: 15 },
    { automatic: true, intervalMinutes: 15 },
  ), true);
  assert.equal(sameSyncPolicy0417(
    { automatic: true, intervalMinutes: 15 },
    { automatic: false, intervalMinutes: 15 },
  ), false);
  const admin = fs.readFileSync(path.join(__dirname, "..", "agenda-admin-0417.js"), "utf8");
  assert.match(admin, /db\.runTransaction/);
  assert.match(admin, /sameSyncPolicy0417\(before, requested\)/);
  assert.match(admin, /changed: false/);
});

test("same browser context replaces its previous passenger session while other contexts survive", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  const app = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
  assert.match(app, /rotacerta-passenger-session-context-0427/);
  assert.match(app, /sessionContextId: passengerSessionContextId0427/);
  assert.match(source, /passengerSessionContextHash0427/);
  assert.match(source, /sameContext = Boolean/);
  assert.match(source, /return expired \|\| sameContext/);
  assert.match(source, /sessionContextHash,/);
});

test("admin browser binds once and keeps each mutating control single-flight", () => {
  const browser = fs.readFileSync(path.join(__dirname, "..", "..", "public", "admin-0417.js"), "utf8");
  assert.match(browser, /__rotaCertaAgendaAdminBound0427/);
  assert.match(browser, /adminInFlight0427/);
  assert.match(browser, /button\.disabled = true/);
  assert.match(browser, /X-Rota-Certa-Operation-Id/);
  assert.match(browser, /operationId = newAdminOperationId0427\("update_now"\)/);
  assert.match(browser, /operationId = newAdminOperationId0427\("reconcile"\)/);
  assert.match(browser, /result\.changed === false/);
});


test("Agenda authentication requirement defaults on and can be explicitly disabled per driver", () => {
  assert.equal(authenticationRequired0417({}), true);
  assert.equal(authenticationRequired0417({ agendaAuthenticationRequired0428: true }), true);
  assert.equal(authenticationRequired0417({ agendaAuthenticationRequired0428: false }), false);
});

test("0.1.428 open mode removes public and admin login gates without creating a second session store", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  const admin = fs.readFileSync(path.join(__dirname, "..", "agenda-admin-0417.js"), "utf8");
  const browser = fs.readFileSync(path.join(__dirname, "..", "..", "public", "admin-0417.js"), "utf8");
  const app = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
  const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

  assert.match(source, /function agendaAuthenticationRequired0428/);
  assert.match(source, /passwordBypassed: !authenticationRequired/);
  assert.match(source, /sessionType: tester \? "TESTER" : \(authenticationRequired \? "PASSENGER" : "OPEN"\)/);
  assert.match(admin, /agendaAuthenticationRequired0428/);
  assert.match(admin, /actorId: "agenda-open-0428"/);
  assert.match(browser, /adminAuthenticationRequired0428/);
  assert.match(browser, /token \? \{ "Authorization": "Bearer " \+ token \} : \{\}/);
  assert.match(app, /Continuar sem senha/);
  assert.match(app, /authentication_disabled/);
  assert.match(html, /Exigir autenticação na Agenda/);

  assert.equal((source.match(/db\.collection\("passengerSessions"\)/g) || []).length > 0, true);
  assert.doesNotMatch(source, /openPassengerSessions|noAuthSessions|bypassSessions/);
});


test("0.1.428 open sessions stay within one Agenda", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  assert.match(source, /driverScope0428/);
  assert.match(source, /visibleEntries/);
  assert.match(source, /scopedDocs/);
  assert.match(source, /passenger_auth_restored/);
  assert.match(source, /password_change_unavailable/);
});
