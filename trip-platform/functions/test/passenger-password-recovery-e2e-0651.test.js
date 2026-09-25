"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const shell = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");
const privateJs = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.js"), "utf8");
const privateHtml = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.html"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0651 Área VIP exposes recovery only inside the password stage", () => {
  assert.match(html, /id="vipForgotPassword0651"[^>]*class="accessSecondary0651 hidden"/);
  assert.match(html, /id="vipForcedPasswordChange0651"[^>]*class="forcedPassword0651 hidden"/);
  assert.match(html, /id="vipRecoveredPassword0651"/);
  assert.match(html, /id="vipRecoveredPasswordConfirm0651"/);
  assert.match(html, /0\.1\.651-password-recovery/);

  const login = between(shell, "async function requestPassengerAgendaAccess0589", "async function requestVipReferral0649");
  assert.match(login, /setVisible0569\("vipForgotPassword0651", vipGatePasswordCreated0649\)/);
  assert.match(login, /body\?\.mustChangePassword === true/);
  assert.match(login, /showForcedPasswordChange0651\(\)/);
});

test("0651 forgot-password request is public-safe and never returns credentials", () => {
  const request = between(
    api,
    "async function requestPassengerPasswordRecovery0651",
    "async function resolvePassengerTarget0625",
  );
  assert.match(request, /await enforceBookingRateLimit\(req\)/);
  assert.match(request, /passengerAccessForIdentity\(target\.driverUsername, identity\.passengerId, passengerContact\)/);
  assert.match(request, /passwordRecoveryStatus: "REQUESTED"/);
  assert.match(request, /return json\(res, 202, \{ requested: true \}\)/);
  assert.doesNotMatch(request, /temporaryPassword/);
  assert.doesNotMatch(request, /passwordHash/);
  assert.match(
    api,
    /path === "\/v1\/public\/passenger-password-recovery\/request"\) return await requestPassengerPasswordRecovery0651\(req, res\)/,
  );
});

test("0651 temporary credentials create a restricted session until password replacement", () => {
  const create = between(api, "async function createPassengerSession", "async function touchPassengerSessionActivity0427");
  assert.match(create, /passwordChangeRequired0651 = false/);
  assert.match(create, /passwordChangeRequired0651: passwordChangeRequired0651 === true/);

  const requireSession = between(api, "async function requirePassengerSession", "async function logoutPassengerAccount");
  assert.match(requireSession, /data\.passwordChangeRequired0651 === true/);
  assert.match(requireSession, /"\/v1\/passenger\/me"/);
  assert.match(requireSession, /"\/v1\/passenger\/me\/password"/);
  assert.match(requireSession, /"\/v1\/passenger\/logout"/);
  assert.match(requireSession, /password_change_required/);

  const passwordSession = between(api, "async function openPassengerPasswordSession0625", "function driverPassengerAccessId");
  assert.match(passwordSession, /const passwordChangeRequired0651 = alreadyActivated && account\.mustChangePassword === true/);
  assert.match(passwordSession, /mustChangePassword: passwordChangeRequired0651/);
});

test("0651 recovery lifecycle is REQUESTED -> ISSUED -> COMPLETED", () => {
  const reset = between(api, "async function resetDriverPassengerPassword", "async function updateDriverReferralSettings");
  assert.match(reset, /passwordRecoveryStatus: "ISSUED"/);
  assert.match(reset, /mustChangePassword: true/);
  assert.match(reset, /await invalidatePassengerSessions\(currentContact\)/);

  const change = between(api, "async function changePassengerPassword", "function passengerBookingIndexRef");
  assert.match(change, /mustChangePassword: false/);
  assert.match(change, /passwordChangeRequired0651: false/);
  assert.match(change, /passwordRecoveryStatus: "COMPLETED"/);
});

test("0651 public and private surfaces do not load protected data before mandatory replacement", () => {
  const publicRecovery = between(shell, "function showForcedPasswordChange0651", "function clearPassengerAgendaAccess0589");
  assert.match(publicRecovery, /passengerAuthenticated0626 = false/);
  assert.match(publicRecovery, /\/v1\/passenger\/me\/password/);
  assert.match(publicRecovery, /showPassengerAgendaAccess0589\(\)/);

  const privateRefresh = between(privateJs, "async function refreshPrivateArea0491", "function showEntryStep0625");
  const gateIndex = privateRefresh.indexOf("if (passwordChangeRequired0651)");
  const protectedFetchIndex = privateRefresh.indexOf("Promise.all([");
  assert.ok(gateIndex >= 0 && protectedFetchIndex > gateIndex);
  assert.match(privateRefresh, /return;/);
  assert.match(privateHtml, /Sua credencial temporária precisa ser substituída/);
  assert.match(privateHtml, /0\.1\.651-password-recovery/);
});
