"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {
  sanitizeStandaloneCoverPayload0736,
  publicAccessToken0736,
  normalizePublicToken0736,
  canonicalUuid0736,
  shouldExpireStandaloneCoverResult0736,
  standaloneCoverResultTransition0736,
  standaloneCoverRefreshDecision0736,
  remoteCapablePushToken0736,
  MAX_PAYLOAD_BYTES_0736,
} = require("../standalone-covers-remote-0736");

function payload0736() {
  return {
    schemaVersion: "rota-certa-blablacar-ride-covers-v1",
    kind: "BLABLACAR_RIDE_COVERS_STANDALONE",
    capturedAt: "2026-10-05T23:26:21.672545Z",
    sourceAppVersion: "0.1.736",
    sourceVersionCode: 6027,
    sourceCommitSha: "a".repeat(40),
    sourceBranch: "agent/blablacar-standalone-covers-remote-0.1.736",
    result: "COMPLETE",
    totalProfiles: 1,
    totalCards: 1,
    isolation: {
      downloadOnly: true,
      writesTimeline: false,
      writesAgenda: false,
      writesCanonicalTrips: false,
      writesAvailability: false,
      writesCapacity: false,
      writesTodayState: false,
      readsPassengers: false,
      opensTripDetails: false,
    },
    profiles: [{
      displayName: "Conta de teste",
      profileUuid: "7371f028-9c55-4903-8444-308015823efd",
      identityConfirmed: true,
      status: "COMPLETE",
      observedCardCount: 1,
      exportedCardCount: 1,
      reachedEnd: true,
      stabilized: true,
      errorCode: "",
      collectionAttempts: 1,
      recoveredTransiently: false,
      cards: [{
        tripId: "01a087fd-fa75-71c8-b4d9-42abc7a6164b",
        administrativeHref: "https://www.blablacar.com.br/rides/offer",
        dateIso: "2026-11-19",
        dateText: "Qui. 19 Nov.",
        departureTime: "11:00",
        arrivalTime: "16:20",
        origin: "Origem",
        destination: "Destino",
        price: "",
      }],
    }],
  };
}

test("payload remoto preserva contrato avulso e inventário COMPLETE", () => {
  const input = payload0736();
  const value = sanitizeStandaloneCoverPayload0736(input);
  assert.equal(value.result, "COMPLETE");
  assert.equal(value.totalProfiles, 1);
  assert.equal(value.totalCards, 1);
  assert.equal(value.isolation.downloadOnly, true);
  assert.equal(value.isolation.writesTimeline, false);
  assert.equal(value.isolation.writesAgenda, false);
  assert.equal(value.isolation.writesCanonicalTrips, false);
  assert.equal(value.isolation.writesAvailability, false);
  assert.equal(value.isolation.writesCapacity, false);
  assert.equal(value.isolation.writesTodayState, false);
  assert.equal(value.isolation.readsPassengers, false);
  assert.equal(value.isolation.opensTripDetails, false);
});

test("payload remoto rejeita qualquer tentativa de transportar passageiro", () => {
  const input = payload0736();
  input.profiles[0].cards[0].passengerName = "não permitido";
  assert.throws(
    () => sanitizeStandaloneCoverPayload0736(input),
    /campo não permitido/i,
  );
});

test("payload remoto rejeita violação de isolamento", () => {
  const input = payload0736();
  input.isolation.writesAgenda = true;
  assert.throws(
    () => sanitizeStandaloneCoverPayload0736(input),
    /isolamento/i,
  );
});

test("PARTIAL nunca pode ser promovido a COMPLETE no servidor", () => {
  const input = payload0736();
  input.result = "PARTIAL";
  assert.throws(
    () => sanitizeStandaloneCoverPayload0736(input),
    /resultado global/i,
  );

  input.profiles[0].status = "PARTIAL";
  input.profiles[0].reachedEnd = false;
  input.profiles[0].stabilized = false;
  input.profiles[0].errorCode = "COVER_LIST_NOT_STABLE";
  const value = sanitizeStandaloneCoverPayload0736(input);
  assert.equal(value.result, "PARTIAL");
});

test("capability token remoto é opaco e validável sem identidade hardcoded", () => {
  const token = publicAccessToken0736();
  assert.equal(normalizePublicToken0736(token), token);
  assert.equal(normalizePublicToken0736("curto"), "");
  assert.equal(canonicalUuid0736("7371f028-9c55-4903-8444-308015823efd"), "7371f028-9c55-4903-8444-308015823efd");
  assert.equal(canonicalUuid0736("nome"), "");
});

test("limite remoto cabe abaixo do documento Firestore e rejeita excesso", () => {
  const input = payload0736();
  input.profiles[0].cards[0].origin = "x".repeat(MAX_PAYLOAD_BYTES_0736);
  assert.throws(() => sanitizeStandaloneCoverPayload0736(input), /limite remoto/i);
});

test("roteamento remoto fica separado das rotas de Agenda", () => {
  const source = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  assert.match(source, /\/v1\/driver\/standalone-covers\/access\/ensure/);
  assert.match(source, /standaloneCoversRemote0736\.refreshPublic0736/);
  assert.match(source, /standaloneCoversRemote0736\.latestPublic0736/);
  assert.match(source, /standaloneCoversRemote0736\.ackJob0736/);
  assert.match(source, /standaloneCoversRemote0736\.submitResult0736/);
});


test("resultado remoto é temporário e expira sem virar inventário válido", () => {
  const now = 1_000_000;
  assert.equal(shouldExpireStandaloneCoverResult0736("COMPLETE", now - 1, now), true);
  assert.equal(shouldExpireStandaloneCoverResult0736("PARTIAL", now, now), true);
  assert.equal(shouldExpireStandaloneCoverResult0736("FAILED", now - 10, now), true);
  assert.equal(shouldExpireStandaloneCoverResult0736("COMPLETE", now + 1, now), false);
  assert.equal(shouldExpireStandaloneCoverResult0736("RUNNING", now - 1, now), false);
  assert.equal(shouldExpireStandaloneCoverResult0736("EXPIRED", now - 1, now), false);
});


test("resultado terminal remoto é idempotente e nunca regride", () => {
  assert.deepEqual(
    standaloneCoverResultTransition0736("RUNNING", "COMPLETE"),
    { action: "WRITE", state: "COMPLETE" },
  );
  assert.deepEqual(
    standaloneCoverResultTransition0736("COMPLETE", "COMPLETE"),
    { action: "IDEMPOTENT", state: "COMPLETE" },
  );
  assert.deepEqual(
    standaloneCoverResultTransition0736("PARTIAL", "PARTIAL"),
    { action: "IDEMPOTENT", state: "PARTIAL" },
  );
  assert.deepEqual(
    standaloneCoverResultTransition0736("COMPLETE", "PARTIAL"),
    { action: "REJECT", state: "COMPLETE" },
  );
  assert.deepEqual(
    standaloneCoverResultTransition0736("FAILED", "COMPLETE"),
    { action: "REJECT", state: "FAILED" },
  );
  assert.deepEqual(
    standaloneCoverResultTransition0736("EXPIRED", "COMPLETE"),
    { action: "REJECT", state: "EXPIRED" },
  );
});


test("refresh remoto nunca mascara resultado terminal antigo como coleta fresca", () => {
  const now = 1_000_000;
  assert.deepEqual(
    standaloneCoverRefreshDecision0736({
      latestState: "RUNNING",
      latestExpiresAtMillis: now + 60_000,
      lastRequestedAtMillis: now - 5_000,
      nowMillis: now,
    }),
    { action: "REUSE_ACTIVE", retryAfterMillis: 0 },
  );
  assert.deepEqual(
    standaloneCoverRefreshDecision0736({
      latestState: "COMPLETE",
      latestExpiresAtMillis: now + 60_000,
      lastRequestedAtMillis: now - 5_000,
      nowMillis: now,
    }),
    { action: "THROTTLE", retryAfterMillis: 40_000 },
  );
  assert.deepEqual(
    standaloneCoverRefreshDecision0736({
      latestState: "COMPLETE",
      latestExpiresAtMillis: now + 60_000,
      lastRequestedAtMillis: now - 60_000,
      nowMillis: now,
    }),
    { action: "CREATE", retryAfterMillis: 0 },
  );
});


test("push remoto só alcança cliente que declarou suporte explícito", () => {
  const now = 1_000_000;
  const base = {
    token: "x".repeat(64),
    expiresAtMillis: now + 60_000,
  };
  assert.equal(remoteCapablePushToken0736(base, now), false);
  assert.equal(remoteCapablePushToken0736({ ...base, standaloneCoversRemoteVersion: 0 }, now), false);
  assert.equal(remoteCapablePushToken0736({ ...base, standaloneCoversRemoteVersion: 1 }, now), true);
  assert.equal(
    remoteCapablePushToken0736({ ...base, standaloneCoversRemoteVersion: 1, expiresAtMillis: now }, now),
    false,
  );
});


test("PENDING_DEVICE pode tentar novamente após o backoff sem fingir frescor", () => {
  const now = 1_000_000;
  assert.deepEqual(
    standaloneCoverRefreshDecision0736({
      latestState: "PENDING_DEVICE",
      latestExpiresAtMillis: now + 600_000,
      lastRequestedAtMillis: now - 5_000,
      nowMillis: now,
    }),
    { action: "THROTTLE", retryAfterMillis: 40_000 },
  );
  assert.deepEqual(
    standaloneCoverRefreshDecision0736({
      latestState: "PENDING_DEVICE",
      latestExpiresAtMillis: now + 600_000,
      lastRequestedAtMillis: now - 60_000,
      nowMillis: now,
    }),
    { action: "CREATE", retryAfterMillis: 0 },
  );
});
