"use strict";

const fs = require("node:fs");
const path = require("node:path");

const functionsDir = path.join(__dirname, "..");
const root = path.join(functionsDir, "..", "..");
const indexPath = path.join(functionsDir, "index.js");
const outboxPath = path.join(
  root,
  "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips",
  "TripPublicationOutbox0387.kt",
);

let api = fs.readFileSync(indexPath, "utf8");
let outbox = fs.readFileSync(outboxPath, "utf8");

const helperMarker = "async function reconcileDriverCapacitySnapshot(req, res, token) {";
const decisionName = "staleTransportPublicUrlEnrichmentDecisionStage1";
const patchName = "staleTransportPublicUrlEnrichmentPatchStage1";
const helper = `
/**
 * Stage 1: a stale transport envelope may carry one newer semantic fact: the exact
 * passenger-facing BlaBlaCar permalink. It is accepted only when strong identity
 * is unchanged, the server currently has no valid public permalink, the incoming
 * logical revision advances by exactly one, and every other public semantic field
 * is byte-equivalent after neutralizing revision/hash/link metadata.
 *
 * This does NOT make stale transport generally writable. It only authorizes the
 * isolated URL enrichment; the caller persists a minimal patch over current server
 * state and preserves the newer server transport revision.
 */
function ${decisionName}(currentPayloadRaw, incomingPayloadRaw, incomingTransportRevision, currentTransportRevision) {
  const current = currentPayloadRaw && typeof currentPayloadRaw === "object" ? currentPayloadRaw : {};
  const incoming = incomingPayloadRaw && typeof incomingPayloadRaw === "object" ? incomingPayloadRaw : {};
  const incomingTransport = Math.max(0, Math.floor(Number(incomingTransportRevision || 0)));
  const currentTransport = Math.max(0, Math.floor(Number(currentTransportRevision || 0)));
  const currentCanonicalRevision = Math.max(0, Math.floor(Number(current.canonicalRevision || 0)));
  const incomingCanonicalRevision = Math.max(0, Math.floor(Number(incoming.canonicalRevision || 0)));
  const currentCanonicalTripId = cleanText(current.canonicalTripId, 180);
  const incomingCanonicalTripId = cleanText(incoming.canonicalTripId, 180);
  const currentProfileUuid = cleanText(current.blablaProfileUuid, 160).toLowerCase();
  const incomingProfileUuid = cleanText(incoming.blablaProfileUuid, 160).toLowerCase();
  const currentTripId = cleanText(current.blablaTripId, 160);
  const incomingTripId = cleanText(incoming.blablaTripId, 160);
  const incomingCanonicalStateHash = cleanText(incoming.canonicalStateHash, 160);
  const currentCanonicalStateHash = cleanText(current.canonicalStateHash, 160);

  if (
    !currentCanonicalTripId || currentCanonicalTripId !== incomingCanonicalTripId ||
    !currentProfileUuid || currentProfileUuid !== incomingProfileUuid ||
    !currentTripId || currentTripId !== incomingTripId
  ) {
    return { accepted: false, reason: "STRONG_IDENTITY_MISMATCH" };
  }
  if (incomingTransport <= 0 || currentTransport <= incomingTransport) {
    return { accepted: false, reason: "TRANSPORT_NOT_STALE" };
  }
  if (Math.max(0, Math.floor(Number(incoming.publicationRevision || 0))) !== incomingTransport) {
    return { accepted: false, reason: "TRANSPORT_ENVELOPE_MISMATCH" };
  }
  if (currentCanonicalRevision <= 0 || incomingCanonicalRevision !== currentCanonicalRevision + 1) {
    return { accepted: false, reason: "LOGICAL_REVISION_NOT_SINGLE_STEP" };
  }
  if (!incomingCanonicalStateHash || incomingCanonicalStateHash === currentCanonicalStateHash) {
    return { accepted: false, reason: "CANONICAL_HASH_NOT_ADVANCED" };
  }

  const currentLink = normalizeCanonicalBoundBlaBlaPublicUrl0423(current.blablaPublicUrl, currentTripId);
  const incomingLink = normalizeCanonicalBoundBlaBlaPublicUrl0423(incoming.blablaPublicUrl, incomingTripId);
  if (currentLink) return { accepted: false, reason: "CURRENT_VALID_LINK_PRESERVED" };
  if (!incomingLink) return { accepted: false, reason: "INCOMING_LINK_INVALID" };

  const neutralize = (payload, normalizedLink) => {
    const value = { ...(payload || {}) };
    value.canonicalRevision = 0;
    value.publicationRevision = 0;
    value.canonicalStateHash = "";
    value.blablaPublicUrl = normalizedLink ? "__PUBLIC_URL__" : "";
    return value;
  };
  const currentComparable = neutralize(current, incomingLink);
  const incomingComparable = neutralize(incoming, incomingLink);
  if (JSON.stringify(currentComparable) !== JSON.stringify(incomingComparable)) {
    return { accepted: false, reason: "NON_LINK_SEMANTIC_DIFFERENCE" };
  }

  return {
    accepted: true,
    reason: "STALE_TRANSPORT_PUBLIC_URL_ENRICHMENT_ACCEPTED",
    blablaPublicUrl: incomingLink,
    canonicalRevision: incomingCanonicalRevision,
    canonicalStateHash: incomingCanonicalStateHash,
  };
}

function ${patchName}(token, previous, incomingPayload, incomingTransportRevision, currentTransportRevision, now = Date.now()) {
  const currentPayload = canonicalPublicTripPayload0411(token, previous);
  const decision = ${decisionName}(
    currentPayload,
    incomingPayload,
    incomingTransportRevision,
    currentTransportRevision,
  );
  if (!decision.accepted) return { decision, patch: null };

  const committedPayload = canonicalPublicTripPayloadFromStored0434({
    ...currentPayload,
    canonicalRevision: decision.canonicalRevision,
    canonicalStateHash: decision.canonicalStateHash,
    blablaPublicUrl: decision.blablaPublicUrl,
    publicationRevision: currentTransportRevision,
  });
  const publicProjectionHash = canonicalPublicTripHash0411(committedPayload).toLowerCase();
  return {
    decision,
    patch: {
      blablaPublicUrl: decision.blablaPublicUrl,
      canonicalRevision: decision.canonicalRevision,
      canonicalStateHash: decision.canonicalStateHash,
      publicationRevision: currentTransportRevision,
      canonicalPublicProjection0434: committedPayload,
      publicProjectionHash0434: publicProjectionHash,
      publicProjectionRevision0434: Math.max(0, Math.floor(Number(previous && previous.publicProjectionRevision0434 || 0))) + 1,
      publicCommittedAt0422: FieldValue.serverTimestamp(),
      publicAttestationState0417: "PENDING",
      publicAttestedPublicationRevision0417: 0,
      publicAttestedCanonicalRevision0417: 0,
      publicAttestedHash0417: "",
      publicAttestedAtMillis0417: 0,
      publicAttestationReason0417: "PUBLIC_URL_ENRICHED_STALE_TRANSPORT_STAGE1",
      publicAttestationMismatchFields0417: [],
      publicAttestationCorrelationId0417: "",
      updatedAtMillis: now,
    },
  };
}

`;

if (!api.includes(`function ${decisionName}(`)) {
  const at = api.indexOf(helperMarker);
  if (at < 0) throw new Error("reconcileDriverCapacitySnapshot anchor not found");
  api = api.slice(0, at) + helper + api.slice(at);
}

const staleAnchor = `      if (staleByRevision && sameLogicalSnapshot) {
        const range = capacityAvailabilityRange(previous, Array.isArray(previous.segmentLoads) ? previous.segmentLoads : []);
        return {
          changed: false,
          stale: false,
          logicalReplay: true,
          range,
          entityRevision: currentEntityRevision,
          occupancyRevision: Math.max(0, Number(previous.occupancyRevision || 0)),
        };
      }
      if (staleByRevision || legacyAfterVersioned || tombstoneBlocksLegacy) {`;

const staleReplacement = `      const stalePublicUrlEnrichmentStage1 = staleByRevision && incomingPublicProjection0434
        ? ${patchName}(
            token,
            previous,
            incomingPublicProjection0434,
            entityRevision,
            currentEntityRevision,
          )
        : { decision: { accepted: false, reason: "NOT_APPLICABLE" }, patch: null };
      if (staleByRevision && sameLogicalSnapshot) {
        const range = capacityAvailabilityRange(previous, Array.isArray(previous.segmentLoads) ? previous.segmentLoads : []);
        return {
          changed: false,
          stale: false,
          logicalReplay: true,
          range,
          entityRevision: currentEntityRevision,
          occupancyRevision: Math.max(0, Number(previous.occupancyRevision || 0)),
        };
      }
      if (staleByRevision && stalePublicUrlEnrichmentStage1.patch) {
        tx.update(tripRef, stalePublicUrlEnrichmentStage1.patch);
        const range = capacityAvailabilityRange(previous, Array.isArray(previous.segmentLoads) ? previous.segmentLoads : []);
        return {
          changed: true,
          stale: false,
          logicalReplay: false,
          publicUrlEnrichedStage1: true,
          range,
          entityRevision: currentEntityRevision,
          occupancyRevision: Math.max(0, Number(previous.occupancyRevision || 0)),
          canonicalTripId: cleanText(stalePublicUrlEnrichmentStage1.patch.canonicalPublicProjection0434.canonicalTripId, 180),
          canonicalRevision: stalePublicUrlEnrichmentStage1.patch.canonicalRevision,
          canonicalStateHash: stalePublicUrlEnrichmentStage1.patch.canonicalStateHash,
          publicProjectionHash: stalePublicUrlEnrichmentStage1.patch.publicProjectionHash0434,
          createdCanonical: false,
        };
      }
      if (staleByRevision || legacyAfterVersioned || tombstoneBlocksLegacy) {`;

if (!api.includes("const stalePublicUrlEnrichmentStage1 = staleByRevision")) {
  const count = api.split(staleAnchor).length - 1;
  if (count !== 1) throw new Error(`expected one stale transport anchor, found ${count}`);
  api = api.replace(staleAnchor, staleReplacement);
}

const androidAnchor = `                        PublicAgendaAutoSync0300.syncExternalTripIncremental(
                            context = appContext,
                            store = store,
                            source = sourceTrip,
                            configuredRotaCertaSeatAllocation = event.snapshot.configuredRotaCertaSeatAllocation,
                            entityRevision = event.revision,
                            outboxEventId = event.id,
                            mutationId0421 = event.resolvedMutationId0421(),
                            idempotencyKey0421 = event.resolvedIdempotencyKey0421(),
                            externalAccountId = effectiveExternalAccountId0454,
                            canonicalTripId = publicationCanonicalTripId0434,
                            seatAllocationVersion = event.snapshot.seatAllocationVersion,
                            canonicalTripSnapshot = repairedCanonical0456,
                        )`;

const androidReplacement = `                        val canonicalAckStage1 = PublicAgendaAutoSync0300.syncExternalTripIncremental(
                            context = appContext,
                            store = store,
                            source = sourceTrip,
                            configuredRotaCertaSeatAllocation = event.snapshot.configuredRotaCertaSeatAllocation,
                            entityRevision = event.revision,
                            outboxEventId = event.id,
                            mutationId0421 = event.resolvedMutationId0421(),
                            idempotencyKey0421 = event.resolvedIdempotencyKey0421(),
                            externalAccountId = effectiveExternalAccountId0454,
                            canonicalTripId = publicationCanonicalTripId0434,
                            seatAllocationVersion = event.snapshot.seatAllocationVersion,
                            canonicalTripSnapshot = repairedCanonical0456,
                        )
                        if (canonicalAckStage1.published && canonicalAckStage1.publicationRevision > event.revision) {
                            require(canonicalAckStage1.canonicalTripId == publicationCanonicalTripId0434) {
                                "STAGE1_SERVER_ACK_IDENTITY_MISMATCH"
                            }
                            require(canonicalAckStage1.canonicalRevision == repairedCanonical0456.canonicalRevision) {
                                "STAGE1_SERVER_ACK_LOGICAL_REVISION_MISMATCH"
                            }
                            require(canonicalAckStage1.canonicalStateHash == repairedCanonical0456.canonicalStateHash) {
                                "STAGE1_SERVER_ACK_CANONICAL_HASH_MISMATCH"
                            }
                            require(canonicalAckStage1.publicProjectionHash.startsWith("public-v2:")) {
                                "STAGE1_SERVER_ACK_PUBLIC_HASH_MISSING"
                            }
                            outbox.ensureRevisionAtLeast(
                                event.canonicalTripId,
                                canonicalAckStage1.publicationRevision,
                            )
                            store.recordPublicationCommitted0411(
                                canonicalTripId = repairedCanonical0456.id,
                                publicationRevision = canonicalAckStage1.publicationRevision,
                                publicationEventId = event.id,
                                tombstone = false,
                            )
                            recordEvidence0421(
                                stage = "TRANSPORT_REVISION_ADOPTION_STAGE1",
                                status = "OK",
                                reason = "SERVER_ACK_TRANSPORT_REVISION_ADOPTED",
                                event = event,
                                extra = "oldTransportRevision=" + event.revision +
                                    " serverTransportRevision=" + canonicalAckStage1.publicationRevision +
                                    " logicalRevision=" + canonicalAckStage1.canonicalRevision +
                                    " previousStage=SERVER_ACK nextStage=PUBLIC_IDENTITY_RESOLUTION",
                            )
                        }`;

if (!outbox.includes("val canonicalAckStage1 = PublicAgendaAutoSync0300.syncExternalTripIncremental")) {
  const count = outbox.split(androidAnchor).length - 1;
  if (count !== 1) throw new Error(`expected one non-direct external sync anchor, found ${count}`);
  outbox = outbox.replace(androidAnchor, androidReplacement);
}

const invariants = [
  [api, `function ${decisionName}(`, 1],
  [api, `function ${patchName}(`, 1],
  [api, "const stalePublicUrlEnrichmentStage1 = staleByRevision", 1],
  [api, "publicUrlEnrichedStage1: true", 1],
  [outbox, "val canonicalAckStage1 = PublicAgendaAutoSync0300.syncExternalTripIncremental", 1],
  [outbox, "TRANSPORT_REVISION_ADOPTION_STAGE1", 1],
];
for (const [source, needle, expected] of invariants) {
  const count = source.split(needle).length - 1;
  if (count !== expected) throw new Error(`invariant failed ${needle}: expected ${expected}, found ${count}`);
}

fs.writeFileSync(indexPath, api);
fs.writeFileSync(outboxPath, outbox);
console.log("STALE_PUBLIC_URL_ENRICHMENT_STAGE1_PATCH=PASS backend=1 androidAck=1");
