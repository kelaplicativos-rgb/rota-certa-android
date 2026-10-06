"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  sanitizeOperationalPayload0737,
  normalizeTarget0737,
  operationalRemoteCapable0737,
} = require("../blablacar-operational-remote-0737");

function payload0737() {
  return {
    schemaVersion: "rota-certa-blablacar-operational-v1",
    kind: "BLABLACAR_OPERATIONAL_QUERY",
    capturedAt: "2026-10-06T03:00:00Z",
    sourceAppVersion: "0.1.737",
    sourceVersionCode: 6028,
    sourceCommitSha: "a".repeat(40),
    sourceBranch: "agent/blablacar-operational-query-0.1.737",
    result: "COMPLETE",
    profileUuid: "7371f028-9c55-4903-8444-308015823efd",
    tripId: "01a0359e-de23-7a2b-ab27-43990c399a74",
    date: "2026-11-08",
    departureTime: "10:30",
    arrivalTime: "15:30",
    origin: "Origem",
    destination: "Destino",
    price: "R$ 100",
    availability: "3",
    bookedSeats: 0,
    publishedSeats: 4,
    passengerCount: 0,
    passengerRosterComplete: true,
    itineraryAuthoritative: true,
    itineraryStops: ["Origem", "Destino"],
    itineraryStopTimes: ["10:30", "15:30"],
    segments: [],
    identityConflict: false,
    coreComplete: true,
    errorCode: "",
    isolation: {
      writesTimeline: false,
      writesAgenda: false,
      writesCanonicalTrips: false,
      writesAvailability: false,
      writesCapacity: false,
      writesTodayState: false,
      uploadsRawHtml: false,
      uploadsSessionData: false,
    },
  };
}

test("COMPLETE operacional exige prova mínima", () => {
  const value = sanitizeOperationalPayload0737(payload0737());
  assert.equal(value.result, "COMPLETE");
  assert.equal(value.passengerRosterComplete, true);
  assert.equal(value.coreComplete, true);
});

test("resultado rejeita campos não autorizados", () => {
  const input = payload0737();
  input.extraPrivateField = "bloqueado";
  assert.throws(() => sanitizeOperationalPayload0737(input), /campo não permitido/i);
});

test("PARTIAL nunca vira COMPLETE", () => {
  const input = payload0737();
  input.result = "PARTIAL";
  input.coreComplete = false;
  input.passengerRosterComplete = false;
  input.publishedSeats = null;
  const value = sanitizeOperationalPayload0737(input);
  assert.equal(value.result, "PARTIAL");
});

test("COMPLETE sem roster ou com conflito é bloqueado", () => {
  const missingRoster = payload0737();
  missingRoster.passengerRosterComplete = false;
  assert.throws(() => sanitizeOperationalPayload0737(missingRoster), /prova mínima/i);

  const conflict = payload0737();
  conflict.identityConflict = true;
  assert.throws(() => sanitizeOperationalPayload0737(conflict), /prova mínima/i);
});

test("alvo exige profileUuid e tripId canônicos", () => {
  assert.deepEqual(
    normalizeTarget0737(
      "7371f028-9c55-4903-8444-308015823efd",
      "01a0359e-de23-7a2b-ab27-43990c399a74",
    ),
    {
      profileUuid: "7371f028-9c55-4903-8444-308015823efd",
      tripId: "01a0359e-de23-7a2b-ab27-43990c399a74",
    },
  );
  assert.equal(normalizeTarget0737("nome", "viagem"), null);
});

test("push operacional exige versão declarada pelo APK", () => {
  const now = 10_000;
  const base = { token: "x".repeat(64), expiresAtMillis: now + 60_000 };
  assert.equal(operationalRemoteCapable0737(base, now), false);
  assert.equal(operationalRemoteCapable0737({ ...base, blablacarOperationalRemoteVersion: 1 }, now), true);
});
