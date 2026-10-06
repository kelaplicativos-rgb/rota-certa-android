"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  normalizeTripId0737,
  normalizeAdministrativeHref0737,
  sanitizeTripQueryPayload0737,
  queryResultTransition0737,
  remoteQueryCapablePushToken0737,
} = require("../blablacar-trip-query-remote-0737");

function payload0737() {
  return {
    schemaVersion: "rota-certa-blablacar-trip-query-v1",
    kind: "BLABLACAR_TRIP_QUERY",
    capturedAt: "2026-10-06T05:00:00Z",
    sourceAppVersion: "0.1.737",
    sourceVersionCode: 6028,
    sourceCommitSha: "a".repeat(40),
    sourceBranch: "agent/blablacar-remote-trip-query-0.1.737",
    profileUuid: "7371f028-9c55-4903-8444-308015823efd",
    tripId: "01a10f40-5046-7e0f-a0f1-084aaeb436e9",
    dateIso: "2026-11-08",
    departureTime: "10:30",
    arrivalTime: "15:30",
    origin: "São Paulo",
    destination: "São Tomé das Letras",
    price: "R$ 100",
    availability: "available",
    bookedSeats: 2,
    publishedSeats: 4,
    passengerRosterComplete: true,
    itineraryAuthoritative: true,
    operationalComplete: true,
    passengerCount: 1,
    passengerSeatCount: 2,
    passengers: [{
      name: "Pessoa",
      seats: 2,
      boarding: "São Paulo",
      dropoff: "Pouso Alegre",
    }],
    itineraryStops: ["São Paulo", "Pouso Alegre", "São Tomé das Letras"],
    itineraryStopTimes: ["10:30", "13:00", "15:30"],
  };
}

test("snapshot COMPLETE aceita somente campos operacionais sanitizados", () => {
  const value = sanitizeTripQueryPayload0737(
    payload0737(),
    "7371f028-9c55-4903-8444-308015823efd",
    "01a10f40-5046-7e0f-a0f1-084aaeb436e9",
  );
  assert.equal(value.passengerCount, 1);
  assert.equal(value.passengerSeatCount, 2);
  assert.equal(value.passengers[0].name, "Pessoa");
});

test("telefone e booking href sao rejeitados no snapshot remoto", () => {
  const value = payload0737();
  value.passengers[0].phone = "+5511999999999";
  assert.throws(() => sanitizeTripQueryPayload0737(value), /campo não permitido/i);
  delete value.passengers[0].phone;
  value.passengers[0].booking_href = "https://example.com";
  assert.throws(() => sanitizeTripQueryPayload0737(value), /campo não permitido/i);
});

test("identidade forte divergente e contagens inconsistentes falham", () => {
  assert.throws(
    () => sanitizeTripQueryPayload0737(
      payload0737(),
      "175a7068-50d8-40c3-a27a-214b9c6e0461",
      "01a10f40-5046-7e0f-a0f1-084aaeb436e9",
    ),
    /UUID.*diverge/i,
  );
  const value = payload0737();
  value.passengerSeatCount = 1;
  assert.throws(() => sanitizeTripQueryPayload0737(value), /inconsistentes/i);
});

test("href administrativo precisa ser HTTPS BlaBlaCar e conter o tripId", () => {
  const tripId = "01a10f40-5046-7e0f-a0f1-084aaeb436e9";
  const valid = "https://www.blablacar.com.br/rides/offer/" + tripId;
  assert.equal(normalizeAdministrativeHref0737(valid, tripId), valid);
  assert.equal(normalizeAdministrativeHref0737("https://evil.example/" + tripId, tripId), "");
  assert.equal(normalizeAdministrativeHref0737("https://www.blablacar.com.br/rides/offer/outro", tripId), "");
  assert.equal(normalizeTripId0737("../x"), "");
});

test("estado terminal nao pode regredir", () => {
  assert.deepEqual(queryResultTransition0737("RUNNING", "COMPLETE"), { action: "WRITE", state: "COMPLETE" });
  assert.deepEqual(queryResultTransition0737("COMPLETE", "COMPLETE"), { action: "IDEMPOTENT", state: "COMPLETE" });
  assert.deepEqual(queryResultTransition0737("COMPLETE", "PARTIAL"), { action: "REJECT", state: "COMPLETE" });
});

test("push detalhado exige capability explicita do APK", () => {
  const now = 1_000_000;
  const base = { token: "x".repeat(64), expiresAtMillis: now + 60_000 };
  assert.equal(remoteQueryCapablePushToken0737(base, now), false);
  assert.equal(remoteQueryCapablePushToken0737({ ...base, blablacarTripQueryRemoteVersion: 1 }, now), true);
});
