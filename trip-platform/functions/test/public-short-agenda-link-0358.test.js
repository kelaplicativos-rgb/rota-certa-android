"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const firebase = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "..", "firebase.json"), "utf8"));
const androidStore = fs.readFileSync(
  path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripStore.kt"),
  "utf8",
);
const androidUi = fs.readFileSync(
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

test("official Agenda URL is native short slug with no agenda token", () => {
  const urlBuilder = between(api, "function publicAgendaUrlFor", "function publicCalendarUrlFor");
  assert.match(urlBuilder, /encodeURIComponent\(slug\)/);
  assert.doesNotMatch(urlBuilder, /motorista=/);
  assert.doesNotMatch(urlBuilder, /agenda=/);
  assert.match(androidStore, /"\$base\/\$username"/);
  const agendaProperty = between(androidStore, "val publicAgendaUrl", "val publicCalendarUrl");
  assert.doesNotMatch(agendaProperty, /motorista=/);
  assert.doesNotMatch(agendaProperty, /agenda=/);
});

test("browser derives Agenda identity from pathname and calls tokenless slug endpoint", () => {
  assert.match(web, /function publicSlugFromPath\(\)/);
  assert.match(web, /const publicSlug = publicSlugFromPath\(\)/);
  assert.match(web, /\/v1\/public\/agenda\/\$\{encodeURIComponent\(publicSlug\)\}/);
  assert.match(web, /JSON\.stringify\(\{ passengerContact, driverUsername, agendaToken, publicSlug, tripToken \}\)/);
  assert.match(api, /getPublicDriverAgenda\(res, req, parts\[3\], "", true\)/);
  assert.match(api, /if \(!shortRoute\) \{/);
});

test("legacy technical Agenda URL remains accepted", () => {
  assert.match(api, /parts\.length === 6[^\n]+parts\[2\] === "drivers"[^\n]+parts\[5\] === "agenda"/);
  assert.match(api, /getPublicDriverAgenda\(res, req, parts\[3\], parts\[4\]\)/);
  assert.match(web, /\/v1\/public\/drivers\/\$\{encodeURIComponent\(driverUsername\)\}\/\$\{encodeURIComponent\(agendaToken\)\}\/agenda/);
});

test("reserved public identifiers are rejected while normalization remains canonical", () => {
  for (const reserved of ["v1", "calendar", "api", "admin", "login", "assets", "static", "agenda", "config", "settings"]) {
    assert.match(api, new RegExp('"' + reserved + '"'));
    assert.match(web, new RegExp('"' + reserved + '"'));
  }
  assert.match(api, /isReservedPublicUsername\(username\)/);
  assert.match(api, /isReservedPublicUsername\(requestedUsername\)/);
  assert.match(api, /username_reserved/);
});

test("Firebase Hosting supports direct slug and refresh without stealing API/calendar routes", () => {
  const rewrites = firebase.hosting.rewrites;
  assert.equal(rewrites[0].source, "/v1/**");
  assert.equal(rewrites[1].source, "/calendar/**");
  assert.equal(rewrites.at(-1).source, "**");
  assert.equal(rewrites.at(-1).destination, "/index.html");
});

test("shared and referral links do not re-expose agenda token", () => {
  assert.match(androidUi, /Text\("Compartilhar link"\)/);
  assert.match(androidUi, /putExtra\(Intent\.EXTRA_TEXT, agendaUrl\)/);
  const referral = between(web, "async function sharePassengerReferral", "function maskWhatsapp");
  assert.match(referral, /encodeURIComponent\(driverUsername\)/);
  assert.doesNotMatch(referral, /query\.set\("agenda"/);
  assert.doesNotMatch(referral, /motorista:/);
});

test("credential rotation is explicitly separate from short link activation", () => {
  assert.match(androidUi, /Trocar credencial interna/);
  assert.match(androidUi, /O endereço curto continuará o mesmo/);
  assert.match(androidUi, /links técnicos antigos que contenham \?agenda=/);
  assert.match(androidUi, /Confirmar troca de credencial/);
});
