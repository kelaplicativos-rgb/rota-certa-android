"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const calendar = fs.readFileSync(path.join(__dirname, "..", "..", "calendar-functions", "index.js"), "utf8");
const ui = fs.readFileSync(
  path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicAgendaSettingsUi.kt"),
  "utf8",
);

function between(source, start, end) {
  const a = source.indexOf(start);
  const b = source.indexOf(end, a + start.length);
  assert.ok(a >= 0, "missing start: " + start);
  assert.ok(b > a, "missing end: " + end);
  return source.slice(a, b);
}

test("A: friendly username changes through one canonical alias without rotating the agenda token", () => {
  const rename = between(api, "async function changeDriverUsername", "async function ensureDriverPublicAgenda");
  assert.match(rename, /db\.runTransaction/);
  assert.match(rename, /driverAliasRef\(requestedUsername\)/);
  assert.match(rename, /canonicalUsername: driver\.username/);
  assert.match(rename, /publicUsername: requestedUsername/);
  assert.match(rename, /publicAgendaToken: currentToken/);
  assert.doesNotMatch(rename, /crypto\.randomBytes/);
  assert.doesNotMatch(rename, /agendaTokenHash\s*:/);
  assert.doesNotMatch(rename, /tx\.(set|update)\([^\n]*tripPublicAgendaLinks/);
});

test("B: occupied canonical username or foreign alias returns a conflict before any split identity", () => {
  const rename = between(api, "async function changeDriverUsername", "async function ensureDriverPublicAgenda");
  assert.match(rename, /candidateDriverSnap\.exists && requestedUsername !== driver\.username/);
  assert.match(rename, /candidateAliasSnap\.data\(\)\.canonicalUsername/);
  assert.match(rename, /code: "username_taken"/);
  assert.match(api, /existing\.exists \|\| existingLink\.exists \|\| existingAlias\.exists/);
});

test("C: username and alias writes are inside the same Firestore transaction", () => {
  const rename = between(api, "async function changeDriverUsername", "async function ensureDriverPublicAgenda");
  const txStart = rename.indexOf("db.runTransaction");
  const aliasWrite = rename.indexOf("tx.set(candidateAliasRef");
  const driverWrite = rename.indexOf("tx.update(driverRef");
  assert.ok(txStart >= 0);
  assert.ok(aliasWrite > txStart);
  assert.ok(driverWrite > txStart);
  assert.doesNotMatch(rename.slice(0, txStart), /\.set\(|\.update\(/);
});

test("D: retry is idempotent and an old alias remains a reference to the same canonical driver", () => {
  const rename = between(api, "async function changeDriverUsername", "async function ensureDriverPublicAgenda");
  assert.match(rename, /requestedUsername === livePublicUsername/);
  assert.match(rename, /return \{ changed: false, publicUsername: livePublicUsername \}/);
  assert.match(rename, /candidateAliasSnap\.exists/);
  assert.match(rename, /normalizeUsername\(candidateAliasSnap\.data\(\)\.canonicalUsername\) !== driver\.username/);
  assert.doesNotMatch(rename, /delete\(/);
});

test("E: public agenda, passenger access and calendar resolve aliases back to canonical storage", () => {
  assert.match(api, /async function resolveDriverUsername/);
  assert.match(api, /tripDriverAliases/);
  const agenda = between(api, "async function getPublicDriverAgenda", "async function createDriverTrip");
  assert.match(agenda, /resolvedDriver\.canonicalUsername/);
  assert.match(agenda, /where\("driverUsername", "==", username\)/);
  const access = between(api, "async function openPassengerAgendaView", "async function signupPassengerAccount");
  assert.match(access, /resolvedDriver\.canonicalUsername/);
  assert.match(calendar, /tripDriverAliases/);
  assert.match(calendar, /resolvedDriver\.canonicalUsername/);
  assert.match(ui, /Identificador atualizado sem alterar o token público/);
});

test("registration also treats a pre-existing alias as occupied", () => {
  const register = between(api, "async function registerDriver(req, res)", "async function changeDriverUsername");
  assert.match(register, /driverAliasRef\(username\)/);
  assert.match(register, /existingAlias/);
  assert.match(register, /publicUsername: username/);
});
