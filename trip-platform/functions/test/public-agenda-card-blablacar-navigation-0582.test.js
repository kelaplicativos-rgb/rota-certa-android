"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const root = path.join(__dirname, "..", "..", "..");
const backend = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const shell = fs.readFileSync(path.join(root, "trip-platform", "public", "public-agenda-shell-0569.js"), "utf8");
const html = fs.readFileSync(path.join(root, "trip-platform", "public", "index.html"), "utf8");

function functionSource(source, name, nextName) {
  const start = source.indexOf("function " + name + "(");
  const end = source.indexOf("\nfunction " + nextName + "(", start);
  assert.ok(start >= 0 && end > start, "missing function " + name);
  return source.slice(start, end);
}

function backendShareNormalizer0582() {
  const code = [
    functionSource(backend, "cleanText", "blaBlaExternalTripId"),
    functionSource(backend, "blaBlaExternalTripId", "isOfficialBlaBlaHost"),
    functionSource(backend, "isOfficialBlaBlaHost", "normalizeBlaBlaUrl"),
    functionSource(backend, "normalizeBlaBlaPublicShareUrl0571", "publicAgendaExactBlaBlaLinks0570"),
    "result = { normalizeBlaBlaPublicShareUrl0571 };",
  ].join("\n");
  const context = { URL, Array, Set, String, result: null };
  vm.runInNewContext(code, context);
  return context.result.normalizeBlaBlaPublicShareUrl0571;
}

function shellNormalizer0582() {
  const start = shell.indexOf("function isOfficialBlaBlaHost0569(");
  const end = shell.indexOf("\nfunction whatsappDigits0569(", start);
  assert.ok(start >= 0 && end > start);
  const context = { URL, Array, Set, String, result: null };
  vm.runInNewContext(shell.slice(start, end) + "\nresult = validatedBlaBlaPublicUrl0569;", context);
  return context.result;
}

test("0582 public Agenda canonicalizes search-context parameters instead of disabling the card", () => {
  const raw = "https://www.blablacar.com.br/trip?source=CARPOOLING&id=PublicToken_0582&requested_seats=2&search_origin=SEARCH&search_uuid=temporary&p0%5Bac%5D=adult";
  for (const normalize of [backendShareNormalizer0582(), shellNormalizer0582()]) {
    const value = normalize(raw);
    assert.ok(value, "valid official trip permalink must survive");
    const url = new URL(value);
    assert.equal(url.protocol, "https:");
    assert.equal(url.searchParams.get("id"), "PublicToken_0582");
    assert.equal(url.searchParams.get("source"), "CARPOOLING");
    assert.equal(url.searchParams.get("requested_seats"), null);
    assert.equal(url.searchParams.get("search_origin"), null);
    assert.equal(url.searchParams.get("search_uuid"), null);
    assert.equal(url.searchParams.get("p0[ac]"), "adult");
  }
});

test("0582 shell keeps the whole card as the BlaBlaCar anchor", () => {
  assert.match(shell, /document\.createElement\(publicUrl \? "a" : "article"\)/);
  assert.match(shell, /card\.href = publicUrl/);
  assert.match(html, /public-agenda-shell-0569\.js\?v=0\.1\.581\.2/);
});

test("0582 server-canonical ingestion may retain an authoritative public token distinct from administrative trip id", () => {
  const normalize = functionSource(backend, "normalizeDriverTrip", "isExternalBlaBlaTrip");
  assert.match(normalize, /allowCanonicalBoundBlaBlaPublicUrl0582/);
  assert.match(normalize, /canonicalBoundPublicUrl0582/);
  assert.match(backend, /bookedStopShapeMigrationAuthorized0439, serverCanonicalAuthority0468\)/);
  assert.match(
    backend,
    /normalizeCanonicalBoundBlaBlaPublicUrl0423\(\(data && data\.blablaPublicUrl\) \|\| publicTrip\.blablaPublicUrl, blablaTripId\)/,
  );
});

test("0582 unbound public URL validation remains strict and cannot accept an unrelated token", () => {
  assert.match(backend, /normalizeBlaBlaPublicUrl\(raw\.blablaPublicUrl, blablaTripId\)/);
  assert.match(backend, /normalizeBlaBlaPublicUrl\(previous && previous\.blablaPublicUrl, blablaTripId\)/);
  assert.match(backend, /allowCanonicalBoundBlaBlaPublicUrl0582 = false/);
});
