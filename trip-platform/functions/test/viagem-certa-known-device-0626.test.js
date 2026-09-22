"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const publicHtml = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const publicJs = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");
const privateHtml = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.html"), "utf8");
const privateJs = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.js"), "utf8");

function between(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, startMarker + " missing");
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, endMarker + " missing");
  return source.slice(start, end);
}

test("0626 public agenda loads exactly one shell and always exposes Minha área at the top", () => {
  const scripts = publicHtml.match(/public-agenda-shell-0569\.js/g) || [];
  assert.equal(scripts.length, 1);
  assert.match(publicHtml, /0\.1\.626-known-device/);
  assert.doesNotMatch(publicHtml, /0\.1\.624-pin-booking|0\.1\.625-password-steps/);
  assert.match(publicHtml, /id="passengerNav0589" class="passengerNav0589"/);
  assert.match(publicHtml, /id="passengerAreaLink0589"[^>]*>Minha área<\/a>/);
  assert.match(publicHtml, /\.passengerNav0589\{position:absolute;top:0;right:0/);
});

test("0626 known device uses a persistent HttpOnly secure cookie instead of web storage authentication", () => {
  assert.match(api, /__Host-viagem_certa_device_0626/);
  assert.match(api, /PASSENGER_KNOWN_DEVICE_TTL_MILLIS_0626 = 180 \* 24 \* 60 \* 60 \* 1000/);
  assert.match(api, /Secure; HttpOnly; SameSite=Lax/);
  assert.match(api, /passengerSessionCandidates0626/);
  assert.match(api, /passengerKnownDeviceCookieToken0626/);
  assert.match(api, /setPassengerKnownDeviceCookie0626/);
  assert.match(api, /clearPassengerKnownDeviceCookie0626/);
  assert.doesNotMatch(publicJs, /localStorage/);
  assert.doesNotMatch(privateJs, /localStorage/);
  assert.doesNotMatch(publicJs, /sessionStorage\.setItem\(passengerSessionKey0625/);
  assert.doesNotMatch(privateJs, /sessionStorage\.setItem\(sessionKey0625/);
});

test("0626 transparently migrates a still-valid old bearer session to the known-device cookie", () => {
  const requireSession = between(api, "async function requirePassengerSession", "async function logoutPassengerAccount");
  assert.match(requireSession, /passengerSessionCandidates0626/);
  assert.match(requireSession, /cookieToken !== selectedToken/);
  assert.match(requireSession, /setPassengerKnownDeviceCookie0626\(res, selectedToken, expiresAtMillis\)/);
  assert.match(publicJs, /passengerAuthHeaders0626/);
  assert.match(publicJs, /probePassengerSession0626/);
  assert.match(privateJs, /headers\.Authorization = "Bearer " \+ sessionToken0491/);
});

test("0626 known device skips WhatsApp and password during a new reservation", () => {
  assert.match(publicJs, /if \(passengerAuthenticated0626\) return \["seats", "review"\]/);
  assert.match(publicJs, /if \(passengerAuthenticated0626\) showBookingStep0625\("seats"\)/);
  assert.match(publicJs, /if \(passengerAuthenticated0626\) return true/);
  assert.match(publicJs, /probePassengerSession0626\(\)\.finally\(\(\) => loadAgenda0569\(false\)\)/);
});

test("0626 remembered global session can reserve a public trip even without preexisting driver access", () => {
  const helper = between(api, "async function ensurePublicBookingPassengerAccess0626", "async function createBooking");
  assert.match(helper, /passengerAccessForIdentity/);
  assert.match(helper, /PASSENGER_RESTRICTED_ACCESS_STATUSES/);
  assert.match(helper, /existingStatus === "MOVED"/);
  assert.match(helper, /status: "AUTHORIZED"/);
  assert.match(helper, /selfServicePublicBooking0626: true/);
  assert.match(helper, /PUBLIC_BOOKING_ACCESS_0626/);

  const booking = between(api, "async function createBooking", "async function updatePublicBooking");
  assert.match(booking, /ensurePublicBookingPassengerAccess0626/);
  assert.doesNotMatch(booking, /requirePassengerDriverAccess\(req, res, debugDriverUsername, session\)/);
  assert.match(booking, /status: "REQUESTED",\s*operationalStatus: "PENDING"/);
});

test("0626 expired session returns to login inside the flow instead of ending with a generic reservation failure", () => {
  assert.match(publicJs, /Sua identificação expirou\. Entre novamente para continuar; nenhuma reserva foi enviada\./);
  assert.match(publicJs, /showBookingStep0625\("contact"\)/);
  assert.match(publicJs, /passengerAuthenticated0626 = false/);
});

test("0626 private area probes the server cookie on every browser start and does not require a JS token", () => {
  assert.match(privateHtml, /0\.1\.626-known-device/);
  const refresh = between(privateJs, "async function refreshPrivateArea0491", "function showEntryStep0625");
  assert.doesNotMatch(refresh, /if \(!sessionToken0491/);
  assert.match(privateJs, /credentials: "same-origin"/);
  assert.match(privateJs, /renderAuthState0492\("loading"\)/);
  assert.match(privateJs, /refreshPrivateArea0491\(false\)/);
});

test("0626 logout revokes the server session and clears the known-device cookie", () => {
  const logout = between(api, "async function logoutPassengerAccount", "async function passengerBookingIndexEntries0491");
  assert.match(logout, /sessionRefId/);
  assert.match(logout, /clearPassengerKnownDeviceCookie0626\(res\)/);
  assert.match(publicJs, /fetch\("\/v1\/passenger\/logout"/);
  assert.match(privateJs, /request0491\("\/v1\/passenger\/logout"/);
});
