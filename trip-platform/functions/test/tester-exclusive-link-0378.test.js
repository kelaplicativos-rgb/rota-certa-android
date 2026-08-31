"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");
const androidApi = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripRemoteApi.kt"), "utf8");
const androidUi = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicAgendaSettingsUi.kt"), "utf8");

function block(source, start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(from >= 0, `missing block start: ${start}`);
  assert.ok(to > from, `missing block end: ${end}`);
  return source.slice(from, to);
}

test("bootstrap is cryptographically strong, tenant-bound, hashed at rest and replaceable", () => {
  const generate = block(api, "async function generateDriverTesterLink", "async function revokeDriverTesterLink");
  assert.match(generate, /crypto\.randomBytes\(32\)\.toString\("base64url"\)/);
  assert.match(generate, /const tokenHash = sha256Hex\(bootstrapToken\)/);
  assert.match(generate, /driverUsername: driver\.username/);
  assert.match(generate, /generation/);
  assert.match(generate, /tx\.delete\(testerBootstrapHashRef\(previousTokenHash\)\)/);
  assert.doesNotMatch(generate, /bootstrapToken,\s*generation/);
  assert.match(api, /safeEqual\(tokenHash, cleanText\(link\.tokenHash/);
  assert.match(api, /tester_tenant_mismatch/);
});

test("tester session is a separate expiring technical identity and revocation invalidates open sessions", () => {
  const exchange = block(api, "async function exchangeTesterBootstrap", "async function requireTesterSession");
  assert.match(exchange, /sessionType: "TESTER"/);
  assert.match(exchange, /testSessionId = crypto\.randomUUID\(\)/);
  assert.match(exchange, /testPassengerId = `tester_/);
  assert.match(exchange, /TESTER_SESSION_TTL_MILLIS/);
  const requireTester = block(api, "async function requireTesterSession", "function shadowBookingsFromTesterData");
  assert.match(requireTester, /link\.generation/);
  assert.match(requireTester, /tester_session_revoked/);
  assert.match(api, /purgeTesterSessionsForDriver/);
});

test("TESTER can never become a real passenger session or hit real passenger writes", () => {
  const realSession = block(api, "async function requirePassengerSession", "async function signupPassengerAccount");
  assert.match(realSession, /testerSessionHeader\(req\)/);
  assert.match(realSession, /tester_not_passenger/);
  for (const name of ["createBooking", "cancelPublicBooking", "updatePublicBooking", "updatePassengerBooking", "cancelPassengerBooking", "changePassengerPassword", "createPassengerReferral"]) {
    const start = `async function ${name}`;
    const from = api.indexOf(start);
    assert.ok(from >= 0, `${name} missing`);
    assert.match(api.slice(from, from + 450), /blockTesterFromRealPassengerMutation/);
  }
  assert.match(api, /tester_real_write_forbidden/);
});

test("shadow bookings reuse canonical production capacity math and never write real booking collections", () => {
  const create = block(api, "async function createTesterBooking", "async function updateTesterBooking");
  assert.match(create, /bookingSegmentRange/);
  assert.match(create, /reconciledSegmentLoads/);
  assert.match(create, /availableForBooking/);
  assert.match(create, /reconciledSegmentCapacity/);
  assert.match(create, /assertNoOverbooking/);
  assert.match(create, /assertNoOperationalOverbooking/);
  assert.match(create, /shadowMap\[bookingId\] = candidate/);
  assert.match(create, /db\.runTransaction/);
  assert.match(create, /tx\.set\(tester\.ref, \{/);
  assert.doesNotMatch(create, /collection\("bookings"\)\.doc\([^\n]+\)\.(set|create|update)/);
  assert.doesNotMatch(create, /sendDriverPush|appendTripChangeEvent|passengerBookingIndexRef|appendBookingLedger/);
  const overlay = block(api, "async function testerOverlayPublicTrip", "async function getTesterContext");
  assert.match(overlay, /realRecords/);
  assert.match(overlay, /shadowRecords/);
  assert.match(overlay, /reconciledSegmentCapacity/);
  assert.match(overlay, /canonicalCapacityPersistence/);
});

test("shadow cancellation and reset alter only the tester session namespace", () => {
  const cancel = block(api, "async function cancelTesterBooking", "async function listTesterBookings");
  assert.match(cancel, /status: "CANCELLED"/);
  assert.match(cancel, /db\.runTransaction/);
  assert.match(cancel, /tx\.set\(tester\.ref, \{/);
  const reset = block(api, "async function resetTesterSimulation", "async function blockTesterFromRealPassengerMutation");
  assert.match(reset, /shadowBookings: \{\}/);
  assert.doesNotMatch(reset, /collection\("trips"\)/);
});

test("portal consumes bootstrap without WhatsApp, cleans URL and keeps persistent visible test mode", () => {
  assert.match(web, /testerBootstrapToken/);
  assert.match(web, /\/v1\/public\/tester\/bootstrap/);
  assert.match(web, /history\.replaceState/);
  assert.match(web, /X-Rota-Certa-Tester-Session/);
  assert.match(web, /function hasPrivatePortalSession/);
  assert.match(web, /🧪 Abrir para simular/);
  assert.match(html, /id="testModeBanner"/);
  assert.match(html, /🧪 MODO TESTE/);
  assert.match(html, /id="resetTestSimulation"/);
});

test("browser shadow state is isolated per testSessionId and real external effects stay disabled", () => {
  assert.match(web, /rotacerta-tester-\$\{sessionKey\}-/);
  assert.match(web, /\/v1\/tester\/reset/);
  assert.match(web, /\/v1\/tester\/me\/notifications/);
  assert.match(web, /\/v1\/tester\/me\/credits/);
  assert.match(web, /isTesterMode\(\) \? "\/v1\/tester\/me\/bookings"/);
});


test("tester bypass is fail-closed against WhatsApp/password gates and production passenger access", () => {
  const accessGate = block(web, "function showAccessGate", "async function requestPublicAgendaAccess");
  assert.match(accessGate, /if \(isTesterMode\(\)\) return continueAfterAuthentication\(\)/);
  const requestAccess = block(web, "async function requestPublicAgendaAccess", "async function validatePassengerSession");
  assert.match(requestAccess, /if \(isTesterMode\(\)\)/);
  const privateSubmit = block(web, "async function submitPrivateAuthentication", "function closePrivateAuth");
  assert.match(privateSubmit, /if \(isTesterMode\(\)\) return continueAfterAuthentication\(\)/);
  const portalLogin = block(web, "async function loginPassengerPortal", "async function registerPassengerPortal");
  assert.match(portalLogin, /if \(isTesterMode\(\)\) return openPassengerPortal\(\)/);
  const bootstrap = block(web, "async function bootstrapTesterExperience", "async function bootstrapAuthenticatedExperience");
  assert.match(bootstrap, /showOnly\("loading"\)/);
  assert.match(bootstrap, /savePassengerContact\(""\)/);
});

test("tester blocks external WhatsApp/calendar effects and production read telemetry", () => {
  const whatsapp = block(web, "function setWhatsappLink", "function defaultDriverMessage");
  assert.match(whatsapp, /if \(isTesterMode\(\)\)/);
  const google = block(web, "function openGoogleCalendar", "function escapeIcs");
  assert.match(google, /Nenhum serviço externo foi aberto/);
  const share = block(web, "async function shareCalendarFeed", "function portalHeaders");
  assert.match(share, /Compartilhamento externo simulado/);
  const agenda = block(api, "async function getPublicDriverAgenda", "async function createDriverTrip");
  assert.match(agenda, /if \(!tester\) \{[\s\S]*PUBLIC_AGENDA_LOADED/);
  const tripRead = block(api, "async function getPublicTrip", "function normalizeBrazilWhatsapp");
  assert.match(tripRead, /if \(!tester\) await appendPublicDebugEvent\([\s\S]*PUBLIC_TRIP_LOADED/);
});

test("expired tester sessions never fall back into passenger password login", () => {
  const undo = block(web, "async function undoQuickBooking", "function cancellationStorageKey");
  const update = block(web, "async function updateExistingReservation", "async function refreshTripSilently");
  const cancel = block(web, "async function cancelReservation", "function recomputeLoadsAfterBooking");
  for (const source of [undo, update, cancel]) {
    assert.match(source, /response\.status === 401[\s\S]*if \(isTesterMode\(\)\)[\s\S]*saveTesterSession\(""\)[\s\S]*setError/);
  }
});

test("tester mutations are transactional and keep simulated notifications and credits in the tester session only", () => {
  const create = block(api, "async function createTesterBooking", "async function updateTesterBooking");
  const update = block(api, "async function updateTesterBooking", "async function cancelTesterBooking");
  const cancel = block(api, "async function cancelTesterBooking", "async function listTesterBookings");
  const reset = block(api, "async function resetTesterSimulation", "async function blockTesterFromRealPassengerMutation");
  for (const source of [create, update, cancel, reset]) assert.match(source, /db\.runTransaction/);
  assert.match(create, /shadowCredits: creditResult\.credits/);
  assert.match(create, /shadowNotifications: notifications/);
  assert.match(update, /shadowCredits: credits/);
  assert.match(cancel, /refundTesterCredits/);
  assert.match(reset, /shadowNotifications: \[\]/);
  assert.match(reset, /initialTesterCredits/);
  assert.match(api, /async function getTesterCredits/);
  assert.match(api, /async function listTesterNotifications/);
  assert.match(api, /async function markTesterNotification/);
});

test("normal passenger access and Android admin integration remain alongside tester contracts", () => {
  assert.match(web, /passengerSessionToken/);
  assert.match(web, /passengerAgendaViewToken/);
  assert.match(web, /\/v1\/public\/passenger-access/);
  assert.match(api, /requirePassengerAgendaView/);
  assert.match(androidApi, /suspend fun testerLinkStatus/);
  assert.match(androidApi, /suspend fun generateTesterLink/);
  assert.match(androidApi, /suspend fun revokeTesterLink/);
  assert.match(androidUi, /🧪 Link exclusivo de teste/);
  assert.match(androidUi, /Copiar link de teste/);
  assert.match(androidUi, /Compartilhar link de teste/);
  assert.match(androidUi, /Revogar link de teste/);
  assert.match(androidUi, /Gerar novo link de teste/);
});
