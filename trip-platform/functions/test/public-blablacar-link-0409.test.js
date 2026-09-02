const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");

const backend = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const publicApp = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const tripDetail = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "assets", "blablacar", "scripts", "trip_detail.js"), "utf8");
const tripShare = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "assets", "blablacar", "scripts", "trip_public_share.js"), "utf8");

function functionSource(source, name, nextName) {
  const start = source.indexOf("function " + name + "(");
  const end = source.indexOf("\nfunction " + nextName + "(", start);
  assert.ok(start >= 0 && end > start, "missing function " + name);
  return source.slice(start, end);
}

function backendUrlContext() {
  const code = [
    functionSource(backend, "cleanText", "blaBlaExternalTripId"),
    functionSource(backend, "blaBlaExternalTripId", "isOfficialBlaBlaHost"),
    functionSource(backend, "isOfficialBlaBlaHost", "normalizeBlaBlaUrl"),
    functionSource(backend, "normalizeBlaBlaUrl", "normalizeBlaBlaManageUrl"),
    functionSource(backend, "normalizeBlaBlaPublicUrl", "normalizeStops"),
    "result = { normalizeBlaBlaPublicUrl, isOfficialBlaBlaHost };",
  ].join("\n");
  const context = { URL, result: null };
  vm.runInNewContext(code, context);
  return context.result;
}

function publicUrlContext() {
  const code = [
    functionSource(publicApp, "isOfficialBlaBlaHost", "safeBlaBlaPublicUrl"),
    functionSource(publicApp, "safeBlaBlaPublicUrl", "stopMatchesSearch"),
    "result = { safeBlaBlaPublicUrl, isOfficialBlaBlaHost };",
  ].join("\n");
  const context = { URL, result: null };
  vm.runInNewContext(code, context);
  return context.result;
}

test("backend and public client accept exact HTTPS permalinks across BlaBlaCar markets", () => {
  const backendUrl = backendUrlContext();
  const publicUrl = publicUrlContext();
  const tripId = "trip-0409-alpha";
  const cases = [
    "https://www.blablacar.com.br/trip?id=" + tripId + "&search_uuid=temp",
    "https://www.blablacar.fr/trip/" + tripId,
    "https://www.blablacar.co.uk/trip?id=" + tripId,
  ];
  for (const raw of cases) {
    const normalized = backendUrl.normalizeBlaBlaPublicUrl(raw, tripId);
    assert.ok(normalized, raw);
    assert.ok(!normalized.includes("search_uuid"));
    assert.equal(publicUrl.safeBlaBlaPublicUrl({ blablaTripId: tripId, blablaPublicUrl: raw }), normalized);
  }
});

test("invalid host, scheme, search URL, and wrong trip id remain fail-closed", () => {
  const backendUrl = backendUrlContext();
  const publicUrl = publicUrlContext();
  const tripId = "trip-0409-alpha";
  const invalid = [
    "https://blablacar.evil.com/trip?id=" + tripId,
    "http://www.blablacar.fr/trip?id=" + tripId,
    "https://www.blablacar.fr/search?id=" + tripId,
    "https://www.blablacar.fr/trip?id=other-trip",
    "javascript:alert(1)",
  ];
  for (const raw of invalid) {
    assert.equal(backendUrl.normalizeBlaBlaPublicUrl(raw, tripId), "");
    assert.equal(publicUrl.safeBlaBlaPublicUrl({ blablaTripId: tripId, blablaPublicUrl: raw }), "");
  }
});

test("public snapshot contract preserves external trip id beside its permalink", () => {
  const safePublic = functionSource(backend, "safePublicTrip", "clientIp");
  assert.match(safePublic, /blablaTripId: cleanText\(data\.blablaTripId, 160\) \|\| null/);
  assert.match(safePublic, /blablaPublicUrl: normalizeBlaBlaPublicUrl\(data\.blablaPublicUrl, cleanText\(data\.blablaTripId, 160\)\) \|\| null/);
});

test("versioned backend update preserves a valid permalink when an older payload omits it", () => {
  const normalize = functionSource(backend, "normalizeDriverTrip", "isExternalBlaBlaTrip");
  assert.match(normalize, /previousPublicUrl/);
  assert.match(normalize, /normalizeBlaBlaPublicUrl\(previous && previous\.blablaPublicUrl, blablaTripId\)/);
  assert.match(normalize, /normalizeBlaBlaPublicUrl\(raw\.blablaPublicUrl, blablaTripId\) \|\| previousPublicUrl/);
});

test("CTA remains anonymous external navigation and LOTADO policy stays separate", () => {
  assert.match(publicApp, /if \(blablaUrl && canUseExternalActions\)/);
  assert.match(publicApp, /blabla\.rel = "noopener noreferrer external"/);
  assert.match(publicApp, /"BlaBlaCar — indisponível"/);
  assert.match(publicApp, /"BlaBlaCar — link indisponível"/);
  assert.doesNotMatch(functionSource(publicApp, "safeBlaBlaPublicUrl", "stopMatchesSearch"), /passengerSessionToken|WhatsApp|Minha área|login/i);
});

test("collector DOM fallbacks use the same official-host validator and never accept a search URL", () => {
  assert.match(tripDetail, /isOfficialBlaBlaHost/);
  assert.match(tripDetail, /path !== '\/trip'/);
  assert.match(tripShare, /isOfficialBlaBlaHost/);
  assert.match(tripShare, /path !== '\/trip'/);
  assert.match(tripShare, /id !== tripId/);
});
