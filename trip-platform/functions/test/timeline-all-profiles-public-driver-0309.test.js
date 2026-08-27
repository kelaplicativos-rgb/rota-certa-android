"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const read = (...parts) => fs.readFileSync(path.join(root, ...parts), "utf8");

const stateStore = read("app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripBlaBlaCollector.kt");
const sessionStore = read("app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "BlaBlaCollectorSessionModule.kt");
const collectorUi = read("app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "TripBlaBlaCollectorUi.kt");
const publicSync = read("app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicAgendaAutoSync0300.kt");
const settingsUi = read("app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicAgendaSettingsUi.kt");
const dynamicAccounts = read("app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "BlaBlaDynamicAccounts.kt");
const publicProfile = read("app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicDriverProfile.kt");
const publicHtml = read("trip-platform", "public", "index.html");
const publicJs = read("trip-platform", "public", "app.js");
const api = read("trip-platform", "functions", "index.js");
const profilePolicy = require("../public-profile-policy");

test("Timeline rebuilds from every connected account instead of a last single-account response", () => {
  assert.match(stateStore, /TIMELINE_REBUILT_FROM_ALL_CONNECTED_ACCOUNTS/);
  assert.match(stateStore, /BlaBlaDynamicAccountRegistry\(appContext\)\.list\(\)/);
  assert.match(stateStore, /BlaBlaDynamicSessionStore\(appContext\)\.combinedResponse\(accounts\)/);
  assert.doesNotMatch(
    stateStore,
    /persisted\?\.status == "cleared" \|\| persisted\?\.trips\?\.isNotEmpty\(\) == true/,
  );
});

test("combined response includes all verified connected account snapshots", () => {
  assert.match(sessionStore, /accounts\.mapNotNull/);
  assert.match(sessionStore, /verified\.flatMap/);
  assert.match(sessionStore, /requested_queries = accounts\.size/);
  assert.match(sessionStore, /validated_queries = verified\.size/);
});

test("public agenda publishes trips from all connected accounts without a profile selection filter", () => {
  assert.match(publicSync, /connectedAccounts = BlaBlaDynamicAccountRegistry\(context\)\.list\(\)/);
  assert.match(publicSync, /combinedResponse\(connectedAccounts\)/);
  assert.match(publicSync, /PUBLIC_AGENDA_ALL_CONNECTED_ACCOUNTS/);
  assert.match(publicSync, /selectionFilter=false/);
  assert.match(collectorUi, /Todas as contas conectadas alimentam automaticamente a mesma Timeline e a mesma Agenda Pública/);
});

test("driver identity choice is explicitly separate from trip aggregation", () => {
  assert.match(settingsUi, /Usar um perfil da BlaBlaCar/);
  assert.match(settingsUi, /Criar meu perfil personalizado/);
  assert.match(settingsUi, /todos os cards de todas as contas BlaBlaCar conectadas/);
  assert.match(settingsUi, /As viagens não dependem desta seleção/);
  assert.doesNotMatch(settingsUi, /BlaBlaCar \+ personalizar/);
});

test("selected driver profile uses profile-only sync rather than one-account trip sync", () => {
  assert.match(settingsUi, /BlaBlaDynamicSessionIntents\.profile\(context, profile\)/);
  assert.doesNotMatch(settingsUi, /profileSyncLauncher\.launch\(BlaBlaDynamicSessionIntents\.sync\(context, profile\)\)/);
  assert.match(dynamicAccounts, /MODE_PROFILE/);
  assert.match(dynamicAccounts, /capturePublicProfilePage/);
  assert.match(dynamicAccounts, /captureProfileReviewsPage/);
  assert.match(dynamicAccounts, /trustedDriverProfileLinks/);
});

test("verified driver reviews are monotonic locally and exposed only through the chosen public driver profile", () => {
  assert.match(publicProfile, /data class BlaBlaPublicReview/);
  assert.match(publicProfile, /reviews = cleanedReviews\.takeIf \{ it\.isNotEmpty\(\) \} \?: prior\?\.reviews\.orEmpty\(\)/);
  assert.match(publicProfile, /reviews = automatic\?\.reviews\.orEmpty\(\)/);
  assert.match(api, /reviews: safePublicDriverReviews\(driver\.driverPublicReviews\)/);
});

test("backend review policy preserves same identity partial refresh and clears cross-profile/manual carryover", () => {
  const uuidA = "7371f028-9c55-4903-8444-308015823efd";
  const uuidB = "175a7068-50d8-40c3-a27a-214b9c6e0461";
  const body = {
    publicProfileMode: "BLABLACAR",
    selectedPublicProfileUuid: uuidA,
    publicProfileLastSyncedAtMillis: 0,
    driverPublicReviews: [],
  };
  const same = profilePolicy.buildProfileUpdate({
    body,
    current: { selectedPublicProfileUuid: uuidA, driverPublicReviews: [{ author: "A", text: "Keep" }] },
    driverWhatsapp: "",
  });
  assert.equal(Object.hasOwn(same.update, "driverPublicReviews"), false);

  const switched = profilePolicy.buildProfileUpdate({
    body: { ...body, selectedPublicProfileUuid: uuidB },
    current: { selectedPublicProfileUuid: uuidA, driverPublicReviews: [{ author: "A", text: "Do not leak" }] },
    driverWhatsapp: "",
  });
  assert.deepEqual(switched.update.driverPublicReviews, []);

  const manual = profilePolicy.buildProfileUpdate({
    body: { ...body, publicProfileMode: "MANUAL" },
    current: { selectedPublicProfileUuid: uuidA, driverPublicReviews: [{ author: "A", text: "Do not leak" }] },
    driverWhatsapp: "",
  });
  assert.deepEqual(manual.update.driverPublicReviews, []);
});

test("public passenger page makes rating/reviews clickable and renders synchronized details safely", () => {
  assert.match(publicHtml, /id="driverRatingLine"/);
  assert.match(publicHtml, /id="driverReviews"/);
  assert.match(publicJs, /function renderDriverReviews\(\)/);
  assert.match(publicJs, /function toggleDriverReviews\(\)/);
  assert.match(publicJs, /driverProfile\.reviews/);
  assert.match(publicJs, /textNode\.textContent = String\(review\.text\)/);
});
