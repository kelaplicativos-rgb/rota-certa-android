"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const root = path.join(__dirname, "..", "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");
const shell = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");
const privateHtml = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.html"), "utf8");
const privateJs = fs.readFileSync(path.join(root, "trip-platform", "public", "minha-area.js"), "utf8");
const gradle = fs.readFileSync(path.join(root, "app", "build.gradle.kts"), "utf8");
function between(source,startMarker,endMarker){const start=source.indexOf(startMarker);assert.notEqual(start,-1,startMarker+" missing");const end=source.indexOf(endMarker,start+startMarker.length);assert.notEqual(end,-1,endMarker+" missing");return source.slice(start,end);}
test("0768 VIP entrance uses only approved WhatsApp and no password form",()=>{
  assert.match(html, /<title>Viagens exclusivas<\/title>/);
  assert.doesNotMatch(html, /VIAGEM CERTA|Acesso exclusivo|ACESSO EXCLUSIVO|Suas viagens em um só lugar\.|♛/);
  assert.match(html, /id="passengerWhatsapp0589"/);
  assert.match(html, /id="passengerAccessContinue0589"/);
  assert.doesNotMatch(html, /id="vipPassword0649"/);
  assert.match(html, /id="passengerNav0589" class="passengerNav0589 hidden"/);
  assert.match(html, /og:title" content="Viagens exclusivas"/);
});
test("0649 agenda trip and change APIs require VIP session plus driver access",()=>{
  const agenda=between(api,"async function getPublicDriverAgenda","async function waitPublicAgendaCanonicalChange0495");
  assert.match(agenda,/requirePassengerSession\(req, res\)/);
  assert.match(agenda,/requirePassengerDriverAccess\(req, res, username, session\)/);
  assert.match(agenda,/VIP_AUTHENTICATED_0649/);
  const changes=between(api,"async function waitPublicAgendaCanonicalChange0495","function buildAdminHomeTrip0471");
  assert.match(changes,/requirePassengerSession\(req, res\)/);
  assert.match(changes,/requirePassengerDriverAccess\(req, res, username, session\)/);
  const trip=between(api,"async function getPublicTrip","function normalizeBrazilWhatsapp");
  assert.match(trip,/requirePassengerSession\(req, res\)/);
  assert.match(trip,/requirePassengerDriverAccess\(req, res, driverUsername, session\)/);
});
test("0649 Minha área preserves driver and shared-trip context",()=>{
  assert.match(shell,/query\.set\("motorista", driverUsername0569\)/);
  assert.match(shell,/query\.set\("viagem", sharedTripToken0623\)/);
  assert.match(privateJs,/returnTripToken0649/);
  assert.match(privateJs,/back\.href = "\/" \+ encodeURIComponent\(driverUsername0491\)/);
  assert.match(privateHtml,/0\.1\.651-password-recovery/);
});
test("0651 package metadata preserves 0651 or newer",()=>{
  const codeMatch = /releaseVersionCode = ([0-9_]+)/.exec(gradle);
  const versionMatch = /releaseVersionName = "0[.]1[.]([0-9]+)"/.exec(gradle);
  assert.ok(codeMatch);
  assert.ok(versionMatch);
  assert.ok(Number(codeMatch[1].replaceAll("_", "")) >= 5942);
  assert.ok(Number(versionMatch[1]) >= 651);
});
