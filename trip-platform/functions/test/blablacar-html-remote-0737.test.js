"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  canonicalUuid0737,
  sanitizePayload0737,
  refreshDecision0737,
  remoteCapablePushToken0737,
  targetStateId0737,
} = require("../blablacar-html-remote-0737");

const PROFILE = "7371f028-9c55-4903-8444-308015823efd";
const TRIP = "01a0359e-de23-7a2b-ab27-43990c399a74";

function payload0737() {
  return {
    schemaVersion: "rota-certa-blablacar-html-trip-v1",
    kind: "BLABLACAR_HTML_TRIP_QUERY",
    capturedAt: "2026-10-06T04:30:00Z",
    sourceAppVersion: "0.1.737",
    sourceVersionCode: 6028,
    sourceCommitSha: "a".repeat(40),
    sourceBranch: "agent/blablacar-assistant-read-0.1.737",
    profileUuid: PROFILE,
    tripId: TRIP,
    result: "COMPLETE",
    operationalComplete: true,
    errorCode: "",
    privacy: {
      rawHtmlUploaded: false,
      cookiesUploaded: false,
      blablaCredentialsUploaded: false,
      passengerPhoneUploaded: false,
      passengerBookingHrefUploaded: false,
      writesAgenda: false,
      writesTimeline: false,
      writesCollectorCanonicalState: false,
    },
    snapshot: {
      profileUuid: PROFILE,
      profileName: "Ezequiel",
      tripId: TRIP,
      date: "2026-11-08",
      departureTime: "10:30",
      arrivalTime: "15:30",
      origin: "São Paulo",
      destination: "São Tomé das Letras",
      price: "R$ 100",
      availability: "available",
      passengers: [],
      bookedSeats: 0,
      publishedSeats: 4,
      passengerRosterComplete: true,
      itineraryStops: ["São Paulo", "São Tomé das Letras"],
      itineraryStopTimes: ["10:30", "15:30"],
      itineraryAuthoritative: true,
      publicTripUrl: "https://www.blablacar.com.br/trip/test",
      identityConflict: false,
    },
  };
}

test("COMPLETE exige identidade forte e prova operacional completa", () => {
  const value = sanitizePayload0737(payload0737());
  assert.equal(value.result, "COMPLETE");
  assert.equal(value.profileUuid, PROFILE);
  assert.equal(value.tripId, TRIP);
  assert.equal(value.snapshot.passengerRosterComplete, true);
  assert.equal(value.snapshot.passengers.length, 0);
});

test("telefone, booking href ou HTML bruto nunca entram no contrato remoto", () => {
  const input = payload0737();
  input.snapshot.passengers = [{
    name: "Pessoa",
    seats: 1,
    boarding: "A",
    dropoff: "B",
    phone: "+5511999999999",
  }];
  assert.throws(() => sanitizePayload0737(input), /campo não permitido/i);

  const raw = payload0737();
  raw.rawHtml = "<html>segredo</html>";
  assert.throws(() => sanitizePayload0737(raw), /campo não permitido/i);
});

test("qualquer violação de isolamento é rejeitada", () => {
  const input = payload0737();
  input.privacy.writesAgenda = true;
  assert.throws(() => sanitizePayload0737(input), /privacidade|isolamento/i);
});

test("PARTIAL não pode se passar por COMPLETE", () => {
  const input = payload0737();
  input.result = "PARTIAL";
  input.operationalComplete = false;
  input.snapshot.passengerRosterComplete = false;
  input.snapshot.itineraryAuthoritative = false;
  input.snapshot.publishedSeats = null;
  const value = sanitizePayload0737(input);
  assert.equal(value.result, "PARTIAL");
  assert.equal(value.operationalComplete, false);

  input.operationalComplete = true;
  assert.throws(() => sanitizePayload0737(input), /PARTIAL/i);
});

test("COMPLETE sem roster comprovado é rejeitado", () => {
  const input = payload0737();
  input.snapshot.passengerRosterComplete = false;
  assert.throws(() => sanitizePayload0737(input), /roster/i);
});

test("UUID e tripId são canônicos e target state é determinístico", () => {
  assert.equal(canonicalUuid0737(PROFILE), PROFILE);
  assert.equal(canonicalUuid0737("Ezequiel"), "");
  assert.equal(targetStateId0737("motorista", PROFILE, TRIP), targetStateId0737("motorista", PROFILE, TRIP));
});

test("refresh reutiliza job ativo, limita spam e permite nova leitura após backoff", () => {
  const now = 1_000_000;
  assert.deepEqual(
    refreshDecision0737("RUNNING", now + 30_000, now - 1_000, now),
    { action: "REUSE_ACTIVE", retryAfterMillis: 0 },
  );
  assert.deepEqual(
    refreshDecision0737("COMPLETE", now + 30_000, now - 1_000, now),
    { action: "THROTTLE", retryAfterMillis: 14_000 },
  );
  assert.deepEqual(
    refreshDecision0737("COMPLETE", now + 30_000, now - 20_000, now),
    { action: "CREATE", retryAfterMillis: 0 },
  );
});

test("somente dispositivo que declarou suporte recebe consulta HTML remota", () => {
  const now = 1_000_000;
  const base = { token: "x".repeat(64), expiresAtMillis: now + 60_000 };
  assert.equal(remoteCapablePushToken0737(base, now), false);
  assert.equal(remoteCapablePushToken0737({ ...base, blablacarHtmlRemoteVersion: 0 }, now), false);
  assert.equal(remoteCapablePushToken0737({ ...base, blablacarHtmlRemoteVersion: 1 }, now), true);
});
