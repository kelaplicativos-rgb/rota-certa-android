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
  assert.match(create, /tester\.ref\.set\(\{ shadowBookings: shadowMap/);
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
  assert.match(cancel, /tester\.ref\.set\(\{ shadowBookings: shadowMap/);
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
  assert.match(web, /Notificações reais ficam desativadas no Modo Teste/);
  assert.match(web, /if \(isTesterMode\(\)\) return;/);
  assert.match(web, /isTesterMode\(\) \? "\/v1\/tester\/me\/bookings"/);
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
