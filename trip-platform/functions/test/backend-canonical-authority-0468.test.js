"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

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

function stopShapeMigration0477() {
  const production = between(api, "function canonicalStopSemanticKey0477", "function normalizeDriverTrip");
  const sandbox = {};
  vm.runInNewContext(
    `
      function cleanText(value, maxLength = 1000) {
        return String(value == null ? "" : value).trim().slice(0, maxLength);
      }
      ${production}
      this.migrate0477 = canonicalEndpointStopShapeMigration0439;
    `,
    sandbox,
  );
  return sandbox.migrate0477;
}

function authoritativeRebase0479() {
  const canonical = between(api, "function canonicalStopSemanticKey0477", "function normalizeDriverTrip");
  const managed = between(api, "function managedCapacityClaim", "async function reconcileDriverCapacitySnapshot");
  const sandbox = {};
  vm.runInNewContext(
    `
      function cleanText(value, maxLength = 1000) {
        return String(value == null ? "" : value).trim().slice(0, maxLength);
      }
      ${canonical}
      ${managed}
      this.rebase0479 = authoritativeBlaBlaPreservedBookingMigration0479;
      this.managed0479 = managedCapacityClaim;
    `,
    sandbox,
  );
  return {
    rebase: sandbox.rebase0479,
    managed: sandbox.managed0479,
  };
}

function plain(value) {
  return JSON.parse(JSON.stringify(value));
}

test("0477 canonical stop migration safely rekeys the same ordered four-stop itinerary", () => {
  const migrate = stopShapeMigration0477();
  const previous = [
    { id: "old-0", name: "Santo André", address: "Santo André" },
    { id: "old-1", name: "Extrema", address: "Extrema" },
    { id: "old-2", name: "Pouso Alegre", address: "Pouso Alegre" },
    { id: "old-3", name: "Três Corações", address: "Três Corações" },
  ];
  const next = [
    { id: "new-0", name: "Santo André", address: "Santo André" },
    { id: "new-1", name: "Extrema", address: "Extrema" },
    { id: "new-2", name: "Pouso Alegre", address: "Pouso Alegre" },
    { id: "new-3", name: "Três Corações", address: "Três Corações" },
  ];
  const records = [
    { id: "booking-a", boardingStopId: "old-0", dropoffStopId: "old-3" },
    { id: "booking-b", boardingStopId: "old-1", dropoffStopId: "old-2" },
  ];

  const result = plain(migrate(previous, next, records));

  assert.equal(result.changed, true);
  assert.deepEqual(
    result.records.map(({ boardingStopId, dropoffStopId }) => ({ boardingStopId, dropoffStopId })),
    [
      { boardingStopId: "new-0", dropoffStopId: "new-3" },
      { boardingStopId: "new-1", dropoffStopId: "new-2" },
    ],
  );
  assert.equal(result.changes.length, 2);
});

test("0477 canonical stop migration still rejects a semantic route change", () => {
  const migrate = stopShapeMigration0477();
  const previous = [
    { id: "old-0", name: "Santo André" },
    { id: "old-1", name: "Extrema" },
    { id: "old-2", name: "Pouso Alegre" },
    { id: "old-3", name: "Três Corações" },
  ];
  const changedRoute = [
    { id: "new-0", name: "Santo André" },
    { id: "new-1", name: "Atibaia" },
    { id: "new-2", name: "Pouso Alegre" },
    { id: "new-3", name: "Três Corações" },
  ];

  assert.throws(
    () => migrate(previous, changedRoute, []),
    (error) => error && error.code === "canonical_stop_shape_migration_unsafe" && error.httpStatus === 409,
  );
});

test("0477 preserves the historical two-stop to multi-stop endpoint migration", () => {
  const migrate = stopShapeMigration0477();
  const previous = [
    { id: "old-origin", name: "Santo André" },
    { id: "old-destination", name: "Três Corações" },
  ];
  const next = [
    { id: "new-origin", name: "Santo André" },
    { id: "new-extrema", name: "Extrema" },
    { id: "new-pouso", name: "Pouso Alegre" },
    { id: "new-destination", name: "Três Corações" },
  ];
  const result = plain(migrate(previous, next, [
    { id: "booking-full", boardingStopId: "old-origin", dropoffStopId: "old-destination" },
  ]));

  assert.deepEqual(
    {
      boardingStopId: result.records[0].boardingStopId,
      dropoffStopId: result.records[0].dropoffStopId,
    },
    { boardingStopId: "new-origin", dropoffStopId: "new-destination" },
  );
});

test("0477 multi-stop rekey remains behind deterministic strong-identity authorization", () => {
  const fn = between(api, "const previousStopIds0439", "const normalizedBase0468");
  assert.match(fn, /deterministicRequest/);
  assert.match(fn, /sameStrongExternalIdentity0439/);
  assert.match(fn, /Boolean\(canonicalTripId\)/);
  assert.match(fn, /serverCanonicalAuthority0468/);
  assert.match(fn, /expectedPublicProjectionHash0425/);
});

test("0479 managed BlaBla claims from the obsolete stop shape are replaceable and do not block authoritative rebase", () => {
  const { rebase, managed } = authoritativeRebase0479();
  const previous = [
    { id: "old-sa", name: "Santo André" },
    { id: "old-stl", name: "São Tomé das Letras" },
    { id: "old-tc", name: "Três Corações" },
  ];
  const next = [
    { id: "new-sa", name: "Santo André" },
    { id: "new-extrema", name: "Extrema" },
    { id: "new-pouso", name: "Pouso Alegre" },
    { id: "new-tc", name: "Três Corações" },
  ];
  const records = [
    {
      id: "bbp-bbae1dc05ac20f799f907a5b-old",
      source: "BLABLACAR",
      sourceReference: "BLABLACAR_SYNC:legacy",
      boardingStopId: "old-stl",
      dropoffStopId: "old-tc",
      seats: 1,
    },
  ];

  assert.equal(managed(records[0], "BLABLACAR_SYNC:"), true);
  const preserved = records.filter((record) => !managed(record, "BLABLACAR_SYNC:"));
  assert.equal(preserved.length, 0);

  const result = plain(rebase(previous, next, preserved));
  assert.equal(result.authoritativeRebase0479, true);
  assert.equal(result.changed, true);
  assert.deepEqual(result.records, []);
  assert.deepEqual(result.changes, []);
});

test("0479 preserved Rota Certa booking can survive mixed external itinerary replacement when its semantic stops still exist", () => {
  const { rebase, managed } = authoritativeRebase0479();
  const previous = [
    { id: "old-sa", name: "Santo André" },
    { id: "old-stl", name: "São Tomé das Letras" },
    { id: "old-tc", name: "Três Corações" },
  ];
  const next = [
    { id: "new-sa", name: "Santo André" },
    { id: "new-extrema", name: "Extrema" },
    { id: "new-pouso", name: "Pouso Alegre" },
    { id: "new-tc", name: "Três Corações" },
  ];
  const protectedBooking = {
    id: "rc-booking",
    source: "ROTA_CERTA",
    boardingStopId: "old-sa",
    dropoffStopId: "old-tc",
    seats: 1,
  };

  assert.equal(managed(protectedBooking, "BLABLACAR_SYNC:"), false);
  const result = plain(rebase(previous, next, [protectedBooking]));
  assert.equal(result.records[0].boardingStopId, "new-sa");
  assert.equal(result.records[0].dropoffStopId, "new-tc");
  assert.equal(result.changes.length, 1);
});

test("0479 preserved booking still blocks authoritative rebase when it references a removed stop", () => {
  const { rebase } = authoritativeRebase0479();
  const previous = [
    { id: "old-sa", name: "Santo André" },
    { id: "old-stl", name: "São Tomé das Letras" },
    { id: "old-tc", name: "Três Corações" },
  ];
  const next = [
    { id: "new-sa", name: "Santo André" },
    { id: "new-extrema", name: "Extrema" },
    { id: "new-pouso", name: "Pouso Alegre" },
    { id: "new-tc", name: "Três Corações" },
  ];

  assert.throws(
    () => rebase(previous, next, [{
      id: "rc-booking-stl",
      source: "ROTA_CERTA",
      boardingStopId: "old-stl",
      dropoffStopId: "old-tc",
      seats: 1,
    }]),
    (error) => error && error.code === "canonical_stop_shape_booking_migration_unsafe" && error.httpStatus === 409,
  );
});

test("0479 preserved booking still blocks authoritative rebase when its direction would reverse", () => {
  const { rebase } = authoritativeRebase0479();
  const previous = [
    { id: "old-sa", name: "Santo André" },
    { id: "old-tc", name: "Três Corações" },
  ];
  const next = [
    { id: "new-tc", name: "Três Corações" },
    { id: "new-sa", name: "Santo André" },
  ];

  assert.throws(
    () => rebase(previous, next, [{
      id: "rc-direction",
      source: "ROTA_CERTA",
      boardingStopId: "old-sa",
      dropoffStopId: "old-tc",
      seats: 1,
    }]),
    (error) => error && error.code === "canonical_stop_shape_booking_migration_unsafe" && error.httpStatus === 409,
  );
});

test("0479 authoritative rebase is gated to complete BlaBla replacement and only excludes managed claims from migration", () => {
  const fn = between(api, "const authoritativeManagedReplacement0479", "const stopShapeMigration0439 =");
  assert.match(fn, /bookedStopShapeMigrationAuthorized0439/);
  assert.match(fn, /claimNamespace === "BLABLACAR_SYNC:"/);
  assert.match(fn, /sourceComplete/);
  assert.match(fn, /!preserveManagedClaims0436/);
  assert.match(fn, /records\.filter\(\(record\) => !managedCapacityClaim\(record, claimNamespace\)\)/);
  assert.match(fn, /authoritativeBlaBlaPreservedBookingMigration0479/);
  assert.match(fn, /records\.map\(\(record\) =>[\s\S]*migratedPreservedById0479\.get\(record\.id\) \|\| record/);
});

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
