"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {
  canonicalTripId0737,
  sanitizeRemoteTripQueryPayload0737,
  remoteTripQueryTransition0737,
  remoteTripQueryRefreshDecision0737,
  remoteTripQueryCapablePushToken0737,
} = require("../blablacar-trip-query-0737");

function payload0737() {
  return {
    schemaVersion: "rota-certa-blablacar-trip-query-v1",
    kind: "BLABLACAR_TRIP_QUERY",
    capturedAt: "2026-10-06T04:00:00Z",
    sourceAppVersion: "0.1.737",
    sourceVersionCode: 6028,
    sourceCommitSha: "a".repeat(40),
    profileUuid: "7371f028-9c55-4903-8444-308015823efd",
    tripId: "01a0359e-de23-7a2b-ab27-43990c399a74",
    result: "COMPLETE",
    operationalComplete: true,
    passengerRosterComplete: true,
    itineraryAuthoritative: true,
    date: "2026-11-08",
    departureTime: "10:30",
    arrivalTime: "15:40",
    origin: "São Paulo",
    destination: "São Tomé das Letras",
    price: "R$ 100",
    availability: "available",
    bookedSeats: 2,
    publishedSeats: 2,
    itineraryStops: ["São Paulo", "Pouso Alegre", "São Tomé das Letras"],
    itineraryStopTimes: ["10:30", "13:20", "15:40"],
    passengers: [
      { name: "Pessoa A", seats: 2, boarding: "São Paulo", dropoff: "Pouso Alegre" },
    ],
    evidencePresent: true,
    errorCode: "",
  };
}

test("tripId UUIDv7 é aceito sem afrouxar identidade do perfil", () => {
  assert.equal(
    canonicalTripId0737("01a0359e-de23-7a2b-ab27-43990c399a74"),
    "01a0359e-de23-7a2b-ab27-43990c399a74",
  );
  assert.equal(canonicalTripId0737("Ezequiel"), "");
});

test("consulta profunda COMPLETE preserva apenas projeção operacional permitida", () => {
  const value = sanitizeRemoteTripQueryPayload0737(payload0737());
  assert.equal(value.result, "COMPLETE");
  assert.equal(value.passengerRosterComplete, true);
  assert.equal(value.passengers.length, 1);
  assert.equal(value.passengers[0].seats, 2);
  assert.equal(Object.hasOwn(value.passengers[0], "phone"), false);
  assert.equal(Object.hasOwn(value.passengers[0], "booking_href"), false);
});

test("telefone, HTML e href administrativo são rejeitados pelo backend", () => {
  for (const [key, value] of [
    ["phone", "+5511999999999"],
    ["htmlFile", "private-evidence/a.html"],
    ["administrativeHref", "https://example.invalid/private"],
  ]) {
    const input = payload0737();
    input.passengers[0][key] = value;
    assert.throws(() => sanitizeRemoteTripQueryPayload0737(input), /campo privado proibido/i);
  }
});

test("COMPLETE sem roster/itinerário/vagas comprovados é rejeitado", () => {
  const noRoster = payload0737();
  noRoster.passengerRosterComplete = false;
  assert.throws(() => sanitizeRemoteTripQueryPayload0737(noRoster), /provas operacionais/i);

  const noSeats = payload0737();
  noSeats.publishedSeats = null;
  assert.throws(() => sanitizeRemoteTripQueryPayload0737(noSeats), /provas operacionais/i);
});

test("PARTIAL nunca pode declarar completude operacional", () => {
  const input = payload0737();
  input.result = "PARTIAL";
  input.operationalComplete = false;
  input.passengerRosterComplete = false;
  input.itineraryAuthoritative = false;
  input.publishedSeats = null;
  const value = sanitizeRemoteTripQueryPayload0737(input);
  assert.equal(value.result, "PARTIAL");
  assert.equal(value.operationalComplete, false);

  input.operationalComplete = true;
  assert.throws(() => sanitizeRemoteTripQueryPayload0737(input), /PARTIAL/i);
});

test("estado terminal direcionado é idempotente e não regride", () => {
  assert.deepEqual(remoteTripQueryTransition0737("RUNNING", "COMPLETE"), { action: "WRITE", state: "COMPLETE" });
  assert.deepEqual(remoteTripQueryTransition0737("COMPLETE", "COMPLETE"), { action: "IDEMPOTENT", state: "COMPLETE" });
  assert.deepEqual(remoteTripQueryTransition0737("COMPLETE", "PARTIAL"), { action: "REJECT", state: "COMPLETE" });
});

test("refresh reaproveita job ativo e limita repetição", () => {
  const now = 1_000_000;
  assert.deepEqual(
    remoteTripQueryRefreshDecision0737({
      latestState: "RUNNING",
      latestExpiresAtMillis: now + 60_000,
      lastRequestedAtMillis: now - 1_000,
      nowMillis: now,
    }),
    { action: "REUSE_ACTIVE", retryAfterMillis: 0 },
  );
  assert.deepEqual(
    remoteTripQueryRefreshDecision0737({
      latestState: "COMPLETE",
      latestExpiresAtMillis: now + 60_000,
      lastRequestedAtMillis: now - 5_000,
      nowMillis: now,
    }),
    { action: "THROTTLE", retryAfterMillis: 15_000 },
  );
});

test("push de consulta profunda exige suporte explícito do APK", () => {
  const now = 1_000_000;
  const base = { token: "x".repeat(64), expiresAtMillis: now + 60_000 };
  assert.equal(remoteTripQueryCapablePushToken0737(base, now), false);
  assert.equal(remoteTripQueryCapablePushToken0737({ ...base, blablaTripQueryRemoteVersion: 1 }, now), true);
});

test("rotas privadas por viagem estão ligadas ao mesmo capability token das capas", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  assert.match(source, /createBlaBlaRemoteTripQuery0737/);
  assert.match(source, /blablaRemoteTripQuery0737\.refreshPublic0737/);
  assert.match(source, /blablaRemoteTripQuery0737\.latestPublic0737/);
  assert.match(source, /blablaRemoteTripQuery0737\.ackJob0737/);
  assert.match(source, /blablaRemoteTripQuery0737\.submitResult0737/);
});
