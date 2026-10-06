"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  sanitizeHtmlPayload0737,
  normalizeTarget0737,
  remoteHtmlCapablePushToken0737,
} = require("../blablacar-html-remote-0737");

function payload0737() {
  return {
    schemaVersion: "rota-certa-blablacar-trip-html-v1",
    kind: "BLABLACAR_TRIP_HTML_QUERY",
    capturedAt: "2026-10-06T03:00:00Z",
    sourceAppVersion: "0.1.737",
    sourceVersionCode: 6028,
    sourceCommitSha: "a".repeat(40),
    sourceBranch: "agent/blablacar-remote-html-query-0.1.737",
    result: "COMPLETE",
    profileUuid: "7371f028-9c55-4903-8444-308015823efd",
    profileName: "Perfil",
    tripId: "01a0359e-de23-7a2b-ab27-43990c399a74",
    date: "2026-11-08",
    departureTime: "10:30",
    arrivalTime: "15:30",
    origin: "Origem",
    destination: "Destino",
    price: "R$ 100",
    availability: "3",
    uuidValidation: "confirmed",
    bookedSeats: 0,
    publishedSeats: 4,
    passengerRosterComplete: true,
    itineraryAuthoritative: true,
    itineraryStops: ["Origem", "Destino"],
    itineraryStopTimes: ["10:30", "15:30"],
    identityConflict: false,
    coreComplete: true,
    privateDetailsComplete: true,
    passengers: [],
    errorCode: "",
    isolation: {
      writesTimeline: false,
      writesAgenda: false,
      writesCanonicalTrips: false,
      writesAvailability: false,
      writesCapacity: false,
      writesTodayState: false,
      readsPassengers: true,
      opensTripDetails: true,
      rawHtmlUploaded: false,
      credentialsUploaded: false,
    },
  };
}

test("COMPLETE HTML exige prova forte e mantém somente dados estruturados", () => {
  const value = sanitizeHtmlPayload0737(payload0737());
  assert.equal(value.result, "COMPLETE");
  assert.equal(value.passengerRosterComplete, true);
  assert.equal(value.coreComplete, true);
  assert.equal(value.isolation.rawHtmlUploaded, false);
  assert.equal(value.isolation.credentialsUploaded, false);
});

test("payload HTML rejeita qualquer campo privado extra dentro do passageiro", () => {
  const input = payload0737();
  input.passengers = [{
    name: "Pessoa",
    seats: 1,
    boarding: "A",
    dropoff: "B",
    privateField: "nao permitido",
  }];
  assert.throws(() => sanitizeHtmlPayload0737(input), /campo não permitido/i);
});

test("PARTIAL pode transportar evidência positiva mas não vira COMPLETE", () => {
  const input = payload0737();
  input.result = "PARTIAL";
  input.coreComplete = false;
  input.passengerRosterComplete = false;
  input.publishedSeats = null;
  const value = sanitizeHtmlPayload0737(input);
  assert.equal(value.result, "PARTIAL");
  assert.equal(value.passengerRosterComplete, false);
});

test("COMPLETE sem roster ou com conflito é bloqueado", () => {
  const missingRoster = payload0737();
  missingRoster.passengerRosterComplete = false;
  assert.throws(() => sanitizeHtmlPayload0737(missingRoster), /prova operacional/i);

  const conflict = payload0737();
  conflict.identityConflict = true;
  assert.throws(() => sanitizeHtmlPayload0737(conflict), /prova operacional/i);
});

test("alvo remoto aceita somente UUIDs canônicos", () => {
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

test("push HTML exige capacidade explicitamente registrada pelo APK", () => {
  const now = 10_000;
  const base = {
    token: "x".repeat(64),
    expiresAtMillis: now + 60_000,
  };
  assert.equal(remoteHtmlCapablePushToken0737(base, now), false);
  assert.equal(remoteHtmlCapablePushToken0737({ ...base, blablacarHtmlRemoteVersion: 1 }, now), true);
});
