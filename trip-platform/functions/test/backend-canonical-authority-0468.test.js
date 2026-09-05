"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const admin = fs.readFileSync(path.join(__dirname, "..", "agenda-admin-0417.js"), "utf8");
const outbox = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripPublicationOutbox0387.kt"), "utf8");
const autoSync = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicAgendaAutoSync0300.kt"), "utf8");
const remoteApi = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripRemoteApi.kt"), "utf8");
const activity0468 = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripsActivity.kt"), "utf8");
const navigation0468 = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "AgendaHeaderNavigation0396.kt"), "utf8");
const syncUi0468 = fs.readFileSync(path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "AgendaAutomaticSyncUi0397.kt"), "utf8");
const publicAdmin0468 = fs.readFileSync(path.join(root, "trip-platform", "public", "admin-0417.js"), "utf8");
const publicHtml0468 = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const publicApp0469 = fs.readFileSync(path.join(root, "trip-platform", "public", "app.js"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0468 backend is the logical revision and public projection authority", () => {
  const helper = between(api, "function canonicalServerStateHash0468", "function assertNoOperationalOverbooking");
  assert.match(helper, /server-canonical-v1:/);
  assert.match(helper, /canonicalRevision = currentCanonicalRevision \+ 1/);
  assert.match(helper, /canonicalPublicTripPayload0411/);
  assert.match(helper, /publicProjectionHash0434/);
  assert.match(helper, /publicAttestationState0417: tombstoned \? "UNPROVEN" : "PENDING"/);
});

test("0468 first BlaBla ingestion can create the canonical server trip without a prior Android TripStore row", () => {
  const fn = between(api, "async function reconcileDriverCapacitySnapshot", "async function listDriverTripSyncState0402");
  assert.match(fn, /serverCanonicalAuthority0468/);
  assert.match(fn, /createdByCanonicalIngestion0468 = !tripSnap\.exists && serverCanonicalAuthority0468/);
  assert.match(fn, /canonical_ingestion_identity_required/);
  assert.match(fn, /claimNamespace !== "BLABLACAR_SYNC:"/);
  assert.match(fn, /tx\.set\(tripRef,[\s\S]*createdAtMillis: now/);
  assert.doesNotMatch(fn, /if \(!tripSnap\.exists\) throw Object\.assign\(new Error\("Viagem não encontrada\."\)/);
});

test("0468 server authority does not accept Android public bytes or logical hash as source of truth", () => {
  const fn = between(api, "async function reconcileDriverCapacitySnapshot", "async function listDriverTripSyncState0402");
  assert.match(fn, /!serverCanonicalAuthority0468 && incomingPublicProjection0434/);
  assert.match(fn, /expectedPublicProjectionHash0425 && !serverCanonicalAuthority0468/);
  assert.match(fn, /canonicalRevision: serverCanonicalAuthority0468 \? Math\.max\(0, Number\(previous\.canonicalRevision/);
  assert.match(fn, /canonicalStateHash: serverCanonicalAuthority0468 \? cleanText\(previous\.canonicalStateHash/);
});

test("0468 replay of the same transport mutation is idempotent and cannot advance canonical revision twice", () => {
  const fn = between(api, "async function reconcileDriverCapacitySnapshot", "async function listDriverTripSyncState0402");
  assert.match(fn, /serverCanonicalReplay0468/);
  assert.match(fn, /sameIdempotentMutation/);
  assert.match(fn, /currentCanonicalStateHash\.startsWith\("server-canonical-v1:"\)/);
  assert.match(fn, /logicalReplay: true/);
});

test("0468 booking, operational and capacity mutations rebuild the public projection in the same transaction", () => {
  for (const name of [
    "async function createBooking",
    "async function mutateDriverBookingDecision",
    "async function mutateDriverPassengerOperationalStatus",
    "async function mutateProtectedBooking",
    "async function updatePassengerBooking",
    "async function cancelPassengerBooking",
  ]) {
    const start = api.indexOf(name);
    assert.notEqual(start, -1, name + " missing");
    const next = api.indexOf("\nasync function ", start + name.length);
    const body = api.slice(start, next < 0 ? api.length : next);
    assert.match(body, /canonicalServerProjectionPatch0468/, name + " must update canonical projection atomically");
  }
  assert.match(api, /ROTA_CERTA_SEAT_ALLOCATION_CHANGED/);
});

test("0468 collector outbox derives canonical identity directly from strong provider identity", () => {
  const record = between(outbox, "    private fun recordExternalMutation(", "    fun recordTombstone(");
  assert.match(record, /canonicalBlaBlaTripKey0406/);
  assert.match(record, /transportTrip0468/);
  assert.match(record, /canonicalRevision = 0L/);
  assert.match(record, /canonicalStateHash = ""/);
  assert.doesNotMatch(record, /canonicalMatches\.size != 1/);
  assert.doesNotMatch(record, /store\.saveTrip/);
});

test("0468 direct collection skips private mirror and client-authored public projection", () => {
  const fn = between(autoSync, "    suspend fun syncExternalTripIncremental(", "    private suspend fun syncExternalCapacitySnapshot(");
  assert.match(fn, /serverCanonicalAuthority0468/);
  assert.match(fn, /BACKEND_CANONICAL_DIRECT_INGESTION_0468/);
  assert.match(fn, /if \(!serverCanonicalAuthority0468\) \{[\s\S]*syncPrivateAgendaMirror0434/);
  const cap = between(autoSync, "    private suspend fun syncExternalCapacitySnapshot(", "    private fun saveExternalBinding(");
  assert.match(cap, /serverCanonicalAuthority0468 = serverCanonicalAuthority0468/);
  assert.match(cap, /if \(serverCanonicalAuthority0468\) "" else expectedPublicProjectionHash0425\(\)/);
  assert.match(cap, /isRemoteTripNotFound\(firstError\) && !serverCanonicalAuthority0468/);
});

test("0468 outbox completes only after independent public readback and server attestation, without local canonical read", () => {
  const direct = between(outbox, "val backendCanonicalDirectTransport0468", "} else {\n                        val currentCanonicalMatches0456");
  assert.match(direct, /canonicalAck0468/);
  assert.match(direct, /readPublicTripProjection0411/);
  assert.match(direct, /canonicalPublicProjectionHash0411\(readback0468\.payload\)/);
  assert.match(direct, /readback0468\.agendaVisible/);
  assert.match(direct, /reportPublicTripAttestation0417/);
  assert.match(direct, /serverPublicProjectionConfirmed0469/);
  assert.doesNotMatch(direct, /store\.getTrip/);
  assert.doesNotMatch(direct, /store\.trips\(\)/);

  const completion = between(outbox, "if (backendCanonicalVerified0468)", "val localMirrorSourceId0434");
  assert.match(completion, /outbox\.markDelivered/);
  assert.match(completion, /return@eventLoop/);
});

test("0468 server independently refuses blue when projection is stale, uncommitted, hidden or hash-mismatched", () => {
  const validator = between(api, "async function validatePublicAttestationCurrent0468", "function clientIp");
  assert.match(validator, /publicProjectionCommittedCurrent0434/);
  assert.match(validator, /publicAgendaTripVisibility0466/);
  assert.match(validator, /canonicalPublicTripHash0411/);
  assert.match(validator, /suppliedMatchesCurrent/);

  const attestation = between(admin, "  async function recordDriverPublicAttestation0417", "  async function listAdminLogs0417");
  assert.match(attestation, /validatePublicAttestationCurrent0468/);
  assert.match(attestation, /independent0468\.committed === true/);
  assert.match(attestation, /independent0468\.visible === true/);
  assert.match(attestation, /expectedHash === clean0417\(independent0468\.currentHash/);
  assert.match(attestation, /independent0468\.visible === true/);
});

test("0475 public visibility is canonical list renderability without legacy admin gates", () => {
  const visibility = between(api, "function publicAgendaTripVisibility0466", "async function getPublicDriverAgenda");
  assert.match(visibility, /applyPublicTripVisibility0434/);
  assert.match(visibility, /safePublicTrip\(token, data\)/);
  assert.doesNotMatch(visibility, /tripPublicOnline0471/);
  assert.doesNotMatch(visibility, /publicTripProfileUuids0417/);
  assert.doesNotMatch(visibility, /publicBookingEnabled !== true/);
  assert.match(visibility, /rendered0469\.stops\.length < 2/);
  assert.match(visibility, /PUBLIC_AGENDA_RENDER_STATUS_UNAVAILABLE_0469/);
  assert.match(visibility, /PUBLIC_AGENDA_RENDER_DATETIME_UNAVAILABLE_0469/);
  assert.match(publicApp0469, /PUBLIC_AGENDA_CARD_STATUSES_0469 = new Set\(\["PUBLISHED", "FULL", "STARTING", "ACTIVE"\]\)/);
  assert.match(publicApp0469, /PUBLIC_AGENDA_CARD_STATUSES_0469\.has\(String\(item\?\.status/);
});

test("0469 Admin green and blue derive from the same visible public card state", () => {
  const classifier = between(api, "function adminPublicTripState0469", "async function createDriverTrip");
  assert.match(classifier, /publicAgendaTripVisibility0466/);
  assert.match(classifier, /state: "PUBLISHED"/);
  assert.match(classifier, /BLABLACAR_PUBLIC_URL_PENDING_AGENDA_VISIBLE_0469/);
  assert.match(classifier, /publicProjectionAttestedCurrent0429/);
  assert.match(classifier, /state: "VERIFIED"/);
  assert.match(admin, /classifyPublicTripState0469/);
  assert.match(admin, /effectivePublicState0469/);
  assert.match(admin, /agendaVisible0469: effective0469\.visible === true/);
});

test("0469 direct canonical outbox keeps no-URL public card green and reserves blue for valid URL", () => {
  assert.match(outbox, /blablaUrlPending0469/);
  assert.match(outbox, /canonicalBoundBlaBlaPublicUrl0423/);
  assert.match(outbox, /blablaUrlPending0469 -> "PUBLISHED"/);
  assert.match(outbox, /else -> "VERIFIED"/);
  assert.match(outbox, /BLABLACAR_PUBLIC_URL_PENDING_AGENDA_VISIBLE_0469/);
  assert.match(outbox, /serverPublicProjectionConfirmed0469/);
  assert.match(outbox, /green=" \+ \(!backendCanonicalBlue0469\)/);
});

test("0468 Android transport contract carries server canonical ACK rather than inventing it", () => {
  assert.match(remoteApi, /val serverCanonicalAuthority0468: Boolean = false/);
  assert.match(remoteApi, /val canonicalTripId: String = ""/);
  assert.match(remoteApi, /val canonicalRevision: Long = 0L/);
  assert.match(remoteApi, /val canonicalStateHash: String = ""/);
  assert.match(remoteApi, /val publicProjectionHash: String = ""/);
  assert.match(remoteApi, /serverCanonicalAuthority0468 = serverCanonicalAuthority0468/);
});


test("0468 Android normal navigation opens the BlaBlaCar collector and keeps legacy Timeline out of the drawer", () => {
  assert.match(activity0468, /val initialScreen0396 = TripScreen\.AUTO_SYNC/);
  assert.match(activity0468, /legacyTimelineDeepLink0468/);
  assert.match(activity0468, /A Timeline local foi retirada da operação/);
  assert.match(activity0468, /TripScreen\.AUTO_SYNC -> AgendaAutomaticSyncScreen0397/);
  assert.match(activity0468, /store = store/);
  const drawer = between(navigation0468, "listOf(", ").forEach { section");
  assert.match(drawer, /AgendaRootSection0396\.AUTOMATIC_SYNC/);
  assert.doesNotMatch(drawer, /AgendaRootSection0396\.ALL_TRIPS/);
  assert.match(navigation0468, /AUTOMATIC_SYNC\("BlaBlaCar"\)/);
});

test("0475 collector panel identifies backend as authority and opens only the public Agenda", () => {
  assert.match(syncUi0468, /O servidor é a fonte canônica/);
  assert.match(syncUi0468, /ABRIR AGENDA PÚBLICA/);
  assert.doesNotMatch(syncUi0468, /ABRIR ÁREA ADMINISTRATIVA/);
  assert.match(syncUi0468, /store\.onlineSettings\(\)\.publicAgendaUrl/);
  assert.match(syncUi0468, /Intent\(Intent\.ACTION_VIEW, Uri\.parse\(url\)\)/);
  assert.match(syncUi0468, /O Android mantém apenas cache, sessão e transporte offline/);
});


test("0468 Admin reuses the same booking domain commands instead of implementing parallel transitions", () => {
  assert.match(admin, /mutateDriverBookingDecision0468/);
  assert.match(admin, /mutateDriverPassengerOperationalStatus0468/);
  assert.match(admin, /mutateProtectedBooking0468/);
  assert.match(admin, /listDriverBookings0468/);
  assert.match(admin, /adminActor0468: true/);

  const routes = between(api, "exports.tripApi", "if (path === \"/v1/health\"");
  assert.match(routes, /agendaAdmin0417\.listAdminTripBookings0468/);
  assert.match(routes, /agendaAdmin0417\.mutateAdminBookingDecision0468/);
  assert.match(routes, /agendaAdmin0417\.mutateAdminBookingOperational0468/);

  const decision = between(api, "async function mutateDriverBookingDecision", "async function mutateDriverPassengerOperationalStatus");
  assert.match(decision, /driverOverride0468/);
  assert.match(decision, /adminActor0468 \? "ADMIN" : "DRIVER"/);
  assert.match(decision, /adminActor0468 \? "ADMIN_WEB"/);
});

test("0468 canonical domain blocks cancellation after passenger is in the car", () => {
  const operational = between(api, "async function mutateDriverPassengerOperationalStatus", "async function mutateProtectedBooking");
  assert.match(operational, /beforeOperational === "IN_CAR" && selection === "CANCELLED"/);
  assert.match(operational, /passenger_in_car_not_cancelable/);
  assert.match(publicAdmin0468, /operational !== "IN_CAR" && operational !== "COMPLETED"/);
});

test("0475 canonical admin commands remain internal while public HTML exposes none of them", () => {
  assert.doesNotMatch(publicHtml0468, /adminTripBookings0468|Administração da Agenda|Administrar esta viagem/);
  assert.doesNotMatch(publicHtml0468, /admin-0417\.js/);
  assert.match(publicAdmin0468, /loadAdminTripBookings0468/);
  assert.match(publicAdmin0468, /data-admin-decision0468="APPROVE"/);
  assert.match(publicAdmin0468, /data-admin-decision0468="REJECT"/);
  assert.match(publicAdmin0468, /data-admin-operational0468/);
  assert.match(publicAdmin0468, /\/v1\/admin\/trips\//);
  assert.match(publicAdmin0468, /Estado atualizado no backend canônico/);
});
