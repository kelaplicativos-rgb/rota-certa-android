"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const outbox = fs.readFileSync(
  path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripPublicationOutbox0387.kt"),
  "utf8",
);

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

function productionDecision() {
  const urlHelpers = between(api, "function cleanText", "async function confirmDriverBlaBlaIdentityRecovery0472");
  const decision = between(
    api,
    "function staleTransportPublicUrlEnrichmentDecisionStage1",
    "function staleTransportPublicUrlEnrichmentPatchStage1",
  );
  const sandbox = { URL };
  vm.runInNewContext(
    `${urlHelpers}\n${decision}\nthis.stage1Decision = staleTransportPublicUrlEnrichmentDecisionStage1;`,
    sandbox,
  );
  return sandbox.stage1Decision;
}

function payload(overrides = {}) {
  return {
    schemaVersion: "public-trip-v2",
    canonicalTripId: "tripkey:tenant:profile:abcdefgh",
    canonicalRevision: 7,
    blablaProfileUuid: "profile-uuid-12345678",
    blablaTripId: "abcdefgh12345678",
    title: "Origem → Destino",
    departureAtMillis: 1790000000000,
    timezoneId: "America/Sao_Paulo",
    status: "PUBLISHED",
    capacity: 4,
    stops: [
      { id: "a", order: 0, name: "Origem", address: "Origem", plannedArrivalMillis: null, plannedDepartureMillis: 1790000000000 },
      { id: "b", order: 1, name: "Destino", address: "Destino", plannedArrivalMillis: null, plannedDepartureMillis: null },
    ],
    segmentLoads: [1],
    segmentPassengerLoads: [1],
    segmentBlockedLoads: [0],
    availableSeatsMinimum: 3,
    availableSeatsMaximum: 3,
    operationalAvailableSeats: 3,
    publishedSeats: 4,
    rotaCertaSeatAllocation: 0,
    publicBookingEnabled: true,
    capacityReliable: true,
    itineraryAuthoritative: true,
    publicUrl: "https://rota-certa.example/trip",
    blablaPublicUrl: "",
    publicationRevision: 14,
    canonicalStateHash: "tripstate-v1:old",
    ...overrides,
  };
}

test("stage1 accepts only a one-step logical public-link enrichment carried by stale transport", () => {
  const decide = productionDecision();
  const current = payload();
  const incoming = payload({
    canonicalRevision: 8,
    publicationRevision: 13,
    canonicalStateHash: "tripstate-v1:new-link",
    blablaPublicUrl: "https://www.blablacar.com.br/trip/abcdefgh12345678",
  });

  const result = decide(current, incoming, 13, 14);
  assert.equal(result.accepted, true);
  assert.equal(result.reason, "STALE_TRANSPORT_PUBLIC_URL_ENRICHMENT_ACCEPTED");
  assert.equal(result.canonicalRevision, 8);
  assert.equal(result.canonicalStateHash, "tripstate-v1:new-link");
  assert.match(result.blablaPublicUrl, /^https:\/\/www\.blablacar\.com\.br\/trip\/abcdefgh12345678/);
});

test("stage1 rejects stale transport when any non-link public semantic field changes", () => {
  const decide = productionDecision();
  const current = payload();
  const incoming = payload({
    canonicalRevision: 8,
    publicationRevision: 13,
    canonicalStateHash: "tripstate-v1:new-link-and-title",
    blablaPublicUrl: "https://www.blablacar.com.br/trip/abcdefgh12345678",
    title: "Outro título",
  });

  const result = decide(current, incoming, 13, 14);
  assert.equal(result.accepted, false);
  assert.equal(result.reason, "NON_LINK_SEMANTIC_DIFFERENCE");
});

test("stage1 never overwrites an already valid public permalink", () => {
  const decide = productionDecision();
  const current = payload({
    blablaPublicUrl: "https://www.blablacar.com.br/trip/abcdefgh12345678",
  });
  const incoming = payload({
    canonicalRevision: 8,
    publicationRevision: 13,
    canonicalStateHash: "tripstate-v1:other",
    blablaPublicUrl: "https://www.blablacar.com/trip/abcdefgh12345678",
  });

  const result = decide(current, incoming, 13, 14);
  assert.equal(result.accepted, false);
  assert.equal(result.reason, "CURRENT_VALID_LINK_PRESERVED");
});

test("stage1 rejects malformed, search and administrative URLs", () => {
  const decide = productionDecision();
  for (const candidate of [
    "https://example.com/trip/abcdefgh12345678",
    "https://www.blablacar.com.br/search?from=A&to=B",
    "https://www.blablacar.com.br/rides/offer/edit/abcdefgh12345678",
    "",
  ]) {
    const current = payload();
    const incoming = payload({
      canonicalRevision: 8,
      publicationRevision: 13,
      canonicalStateHash: "tripstate-v1:new-link",
      blablaPublicUrl: candidate,
    });
    const result = decide(current, incoming, 13, 14);
    assert.equal(result.accepted, false, candidate);
    assert.equal(result.reason, "INCOMING_LINK_INVALID", candidate);
  }
});

test("stage1 rejects transport that is not stale, skipped logical revisions and identity changes", () => {
  const decide = productionDecision();
  const baseIncoming = payload({
    canonicalRevision: 8,
    publicationRevision: 13,
    canonicalStateHash: "tripstate-v1:new-link",
    blablaPublicUrl: "https://www.blablacar.com.br/trip/abcdefgh12345678",
  });

  assert.equal(decide(payload(), { ...baseIncoming, publicationRevision: 14 }, 14, 14).reason, "TRANSPORT_NOT_STALE");
  assert.equal(decide(payload(), { ...baseIncoming, canonicalRevision: 9 }, 13, 14).reason, "LOGICAL_REVISION_NOT_SINGLE_STEP");
  assert.equal(
    decide(payload(), { ...baseIncoming, blablaProfileUuid: "different-profile" }, 13, 14).reason,
    "STRONG_IDENTITY_MISMATCH",
  );
});

test("backend stale branch commits a minimal current-state patch and preserves newer transport revision", () => {
  const gate = between(
    api,
    "const stalePublicUrlEnrichmentStage1 = staleByRevision",
    "if (deterministicRequest && entityRevision === currentEntityRevision",
  );
  assert.match(gate, /staleTransportPublicUrlEnrichmentPatchStage1/);
  assert.match(gate, /tx\.update\(tripRef, stalePublicUrlEnrichmentStage1\.patch\)/);
  assert.match(gate, /entityRevision: currentEntityRevision/);
  assert.match(gate, /publicUrlEnrichedStage1: true/);

  const patch = between(
    api,
    "function staleTransportPublicUrlEnrichmentPatchStage1",
    "async function reconcileDriverCapacitySnapshot",
  );
  assert.match(patch, /publicationRevision: currentTransportRevision/);
  assert.match(patch, /canonicalPublicProjection0434: committedPayload/);
  assert.match(patch, /publicCommittedAt0422: FieldValue\.serverTimestamp\(\)/);
  assert.match(patch, /publicAttestationState0417: "PENDING"/);
  assert.doesNotMatch(patch, /title:\s*incomingPayload/);
  assert.doesNotMatch(patch, /stops:\s*incomingPayload/);
  assert.doesNotMatch(patch, /capacity:\s*incomingPayload/);
});

test("Android adopts newer server transport ACK before public readback and guards logical identity", () => {
  const block = between(
    outbox,
    "val canonicalAckStage1 = PublicAgendaAutoSync0300.syncExternalTripIncremental",
    "if (backendCanonicalVerified0468)",
  );
  assert.match(block, /canonicalAckStage1\.publicationRevision > event\.revision/);
  assert.match(block, /canonicalAckStage1\.canonicalTripId == publicationCanonicalTripId0434/);
  assert.match(block, /canonicalAckStage1\.canonicalRevision == repairedCanonical0456\.canonicalRevision/);
  assert.match(block, /canonicalAckStage1\.canonicalStateHash == repairedCanonical0456\.canonicalStateHash/);
  assert.match(block, /outbox\.ensureRevisionAtLeast/);
  assert.match(block, /publicationRevision = canonicalAckStage1\.publicationRevision/);
  assert.match(block, /TRANSPORT_REVISION_ADOPTION_STAGE1/);
});
