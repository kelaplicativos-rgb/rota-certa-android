"use strict";

const fs = require("node:fs");
const path = require("node:path");

const indexPath = path.join(__dirname, "..", "index.js");
let source = fs.readFileSync(indexPath, "utf8");

const helperName = "publicAgendaExactBlaBlaLinks0570";
const helper = `
/**
 * 0.1.570: enrich the public Agenda with only the exact, canonical-bound
 * BlaBlaCar public permalink. Private mirror fields never leave this helper.
 * If identity/link proof is incomplete, the trip remains inert in the public UI.
 */
async function publicAgendaExactBlaBlaLinks0570(driver, sourceDocs, rawTrips) {
  const docs = Array.isArray(sourceDocs) ? sourceDocs : [];
  const trips = Array.isArray(rawTrips) ? rawTrips : [];
  const driverUsername = cleanText(driver && driver.username, 40);
  if (!driverUsername || !docs.length || !trips.length) return trips;

  let mirrorSnapshot;
  try {
    mirrorSnapshot = await db.collection("tripPrivateMirrors0434")
      .where("driverUsername", "==", driverUsername)
      .limit(300)
      .get();
  } catch (_) {
    return trips;
  }

  const mirrorsByCanonicalId = new Map();
  mirrorSnapshot.docs.forEach((mirrorDoc) => {
    const mirror = mirrorDoc.data() || {};
    const canonicalId = cleanText(mirror.canonicalTripId, 180);
    if (canonicalId) mirrorsByCanonicalId.set(canonicalId, mirror);
  });

  return trips.map((trip, index) => {
    const doc = docs[index];
    if (!doc || typeof doc.data !== "function") return trip;
    const data = doc.data() || {};
    const canonicalTripId = cleanText(data.canonicalTripId || data.localTripId, 180) || doc.id;
    const mirror = mirrorsByCanonicalId.get(canonicalTripId) || null;
    const privatePayload =
      mirror && mirror.payload && typeof mirror.payload === "object" &&
      mirror.payload.schemaVersion === "private-agenda-mirror-v1"
        ? mirror.payload
        : null;
    const mirrorCompatible = canonicalTimelinePrivateMirrorCompatible0523(
      data,
      canonicalTripId,
      mirror,
      privatePayload,
    );
    const canonicalProjection = canonicalPublicTripPayload0411(doc.id, data);
    const externalIdentity = canonicalTimelineExternalIdentity0523(
      data,
      canonicalProjection,
      mirrorCompatible ? privatePayload : null,
      mirrorCompatible,
    );
    const exactPublicUrl = canonicalTimelinePublicUrl0524(
      data,
      canonicalProjection,
      mirrorCompatible ? privatePayload : null,
      externalIdentity,
    );
    return exactPublicUrl ? { ...trip, blablaPublicUrl: exactPublicUrl } : trip;
  });
}

`;

if (!source.includes(`function ${helperName}(`)) {
  const marker = "async function getPublicDriverAgenda(res, req, usernameRaw, agendaToken, shortRoute = false) {";
  const at = source.indexOf(marker);
  if (at < 0) throw new Error("getPublicDriverAgenda anchor not found");
  source = source.slice(0, at) + helper + source.slice(at);
}

const projectionBlock = `  const trips = rawTrips.map((trip, index) =>
    publicTripProjection0491(applyPublicTripVisibility0434(trip, sourceDocs[index].data(), driver))
  );`;

const linkedProjectionBlock = `  const exactLinkedTrips0570 = tester
    ? rawTrips
    : await publicAgendaExactBlaBlaLinks0570(driver, sourceDocs, rawTrips);
  const trips = exactLinkedTrips0570.map((trip, index) =>
    publicTripProjection0491(applyPublicTripVisibility0434(trip, sourceDocs[index].data(), driver))
  );`;

if (!source.includes("const exactLinkedTrips0570 = tester")) {
  const occurrences = source.split(projectionBlock).length - 1;
  if (occurrences !== 1) {
    throw new Error(`expected shared public Agenda projection block once, found ${occurrences}`);
  }
  source = source.replace(projectionBlock, linkedProjectionBlock);
}

const helperOccurrences = source.split(`function ${helperName}(`).length - 1;
const linkedOccurrences = source.split("const exactLinkedTrips0570 = tester").length - 1;
if (helperOccurrences !== 1) throw new Error(`expected exactly one ${helperName}, found ${helperOccurrences}`);
if (linkedOccurrences !== 1) throw new Error(`expected one shared linked Agenda block, found ${linkedOccurrences}`);

fs.writeFileSync(indexPath, source);
console.log(`PUBLIC_AGENDA_EXACT_BLABLACAR_PATCH_0570=PASS helper=${helperOccurrences} sharedRoute=${linkedOccurrences}`);
