"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const activity = fs.readFileSync(
  path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripsActivity.kt"),
  "utf8",
);
const autoSync = fs.readFileSync(
  path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicAgendaAutoSync0300.kt"),
  "utf8",
);

function between(source, start, end) {
  const a = source.indexOf(start);
  const b = source.indexOf(end, a + start.length);
  assert.ok(a >= 0, `missing start marker: ${start}`);
  assert.ok(b > a, `missing end marker: ${end}`);
  return source.slice(a, b);
}

test("normal agenda ensure never rotates a mismatched token", () => {
  const ensure = between(api, "async function ensureDriverPublicAgenda", "async function regenerateDriverPublicAgenda");
  assert.match(ensure, /agenda_token_mismatch/);
  assert.match(ensure, /O link atual foi preservado/);
  assert.doesNotMatch(ensure, /crypto\.randomBytes/);
  assert.doesNotMatch(ensure, /agendaTokenHash\s*=/);
});

test("only explicit regeneration creates a new agenda token", () => {
  const regenerate = between(api, "async function regenerateDriverPublicAgenda", "function splitPublicList");
  assert.match(regenerate, /REGENERATE_PUBLIC_AGENDA_LINK/);
  assert.match(regenerate, /explicit_confirmation_required/);
  assert.match(regenerate, /crypto\.randomBytes\(24\)/);
  assert.match(regenerate, /agendaTokenHash: sha256Hex\(publicAgendaToken\)/);
  assert.match(api, /\/v1\/driver\/agenda\/regenerate/);
});

test("Android blocks manual token edits and requires two-step regeneration", () => {
  assert.match(activity, /Token público da agenda de viagens — fixo/);
  assert.match(activity, /enabled = token\.isBlank\(\)/);
  assert.match(activity, /Text\("Gerar novo link"\)/);
  assert.match(activity, /Gerar um novo link invalida imediatamente o link atual/);
  assert.match(activity, /Text\("Confirmar novo link"\)/);
  assert.match(activity, /regeneratePublicAgenda\(\)/);
});

test("normal app save and autosync do not adopt a replacement token", () => {
  const saveSection = between(activity, "TripScreen.SETTINGS ->", "TripScreen.LIST ->");
  assert.doesNotMatch(saveSection, /publicCalendarToken = response\.publicAgendaToken/);
  assert.doesNotMatch(autoSync, /publicCalendarToken = response\.publicAgendaToken/);
  assert.match(autoSync, /stableAgendaToken=true/);
});
