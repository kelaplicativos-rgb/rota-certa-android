"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const profilePolicy = require("../public-profile-policy");
const profilePolicySource = fs.readFileSync(path.join(__dirname, "..", "public-profile-policy.js"), "utf8");
const linkPolicy = require("../public-agenda-link-policy");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const calendar = fs.readFileSync(path.join(__dirname, "..", "..", "calendar-functions", "index.js"), "utf8");
const ui = fs.readFileSync(
  path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicAgendaSettingsUi.kt"),
  "utf8",
);
const publicProfileKt = fs.readFileSync(
  path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicDriverProfile.kt"),
  "utf8",
);
const dynamicAccounts = fs.readFileSync(
  path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "BlaBlaDynamicAccounts.kt"),
  "utf8",
);

const uuidA = "7371f028-9c55-4903-8444-308015823efd";
const uuidB = "175a7068-50d8-40c3-a27a-214b9c6e0461";

function profileBody(extra = {}) {
  return {
    publicProfileMode: "BLABLACAR",
    selectedPublicProfileUuid: uuidA,
    publicProfileLastSyncedAtMillis: 123,
    driverDisplayName: "Perfil A",
    driverPhotoUrl: "https://example.test/a.jpg",
    driverPublicAbout: "Descrição A",
    driverPublicRating: "4.9",
    driverPublicReviewCount: 99,
    driverPublicBadge: "Verificado",
    vehicleMakeModel: "Carro A",
    vehicleColor: "Prata",
    vehicleAmenities: "Ar-condicionado",
    driverPreferences: "Não fumar",
    paymentInstructions: "Pix",
    ...extra,
  };
}

test("manual public profile uses only Rota Certa fields", () => {
  const plan = profilePolicy.buildProfileUpdate({
    body: profileBody({
      publicProfileMode: "MANUAL",
      selectedPublicProfileUuid: uuidA,
      driverDisplayName: "Meu nome",
      driverPublicAbout: "Minha apresentação",
    }),
    current: { selectedPublicProfileUuid: uuidB },
    driverWhatsapp: "+5511999999999",
  });
  assert.equal(plan.ok, true);
  assert.equal(plan.update.displayName, "Meu nome");
  assert.equal(plan.update.driverPublicAbout, "Minha apresentação");
  assert.equal(plan.update.selectedPublicProfileUuid, "");
  assert.equal(plan.update.driverWhatsapp, "+5511999999999");
  assert.equal(plan.update.paymentInstructions, "Pix");
});

test("automatic profile requires a strong UUID", () => {
  const plan = profilePolicy.buildProfileUpdate({
    body: profileBody({ selectedPublicProfileUuid: "perfil-sem-uuid" }),
    current: {},
    driverWhatsapp: "",
  });
  assert.equal(plan.ok, false);
  assert.equal(plan.code, "public_profile_identity_required");
});

test("partial refresh of the same profile preserves prior confirmed automatic fields", () => {
  const plan = profilePolicy.buildProfileUpdate({
    body: profileBody({ publicProfileLastSyncedAtMillis: 0 }),
    current: { selectedPublicProfileUuid: uuidA, displayName: "Confirmado A", driverPhotoUrl: "https://example.test/old.jpg" },
    driverWhatsapp: "+5511888888888",
  });
  assert.equal(plan.ok, true);
  assert.equal(plan.update.selectedPublicProfileUuid, uuidA);
  assert.equal(Object.hasOwn(plan.update, "displayName"), false);
  assert.equal(Object.hasOwn(plan.update, "driverPhotoUrl"), false);
  assert.equal(plan.update.driverWhatsapp, "+5511888888888");
});

test("switching to an unavailable different profile clears stale automatic fields instead of contaminating", () => {
  const plan = profilePolicy.buildProfileUpdate({
    body: profileBody({ selectedPublicProfileUuid: uuidB, publicProfileLastSyncedAtMillis: 0 }),
    current: { selectedPublicProfileUuid: uuidA, displayName: "Perfil A", driverPhotoUrl: "https://example.test/a.jpg" },
    driverWhatsapp: "",
  });
  assert.equal(plan.ok, true);
  assert.equal(plan.update.selectedPublicProfileUuid, uuidB);
  assert.equal(plan.update.displayName, "");
  assert.equal(plan.update.driverPhotoUrl, "");
  assert.equal(plan.update.driverPublicReviewCount, 0);
});

test("hybrid mode preserves individual overrides while automatic fields refresh", () => {
  const plan = profilePolicy.buildProfileUpdate({
    body: profileBody({
      publicProfileMode: "HYBRID",
      publicProfileOverrideFields: ["about"],
      driverPublicAbout: "Personalizado por mim",
      driverDisplayName: "Nome automático novo",
      publicProfileLastSyncedAtMillis: 456,
    }),
    current: { selectedPublicProfileUuid: uuidA },
    driverWhatsapp: "+5511777777777",
  });
  assert.equal(plan.ok, true);
  assert.deepEqual(plan.update.publicProfileOverrideFields, ["about"]);
  assert.equal(plan.update.driverPublicAbout, "Personalizado por mim");
  assert.equal(plan.update.displayName, "Nome automático novo");
  assert.equal(plan.update.driverWhatsapp, "+5511777777777");
});

test("explicit link rotation is deterministic for retry and changes for a new rotation id", () => {
  const secret = "server-secret-for-test";
  const first = linkPolicy.deriveRotationToken(secret, "viagem-certa", "11111111-1111-4111-8111-111111111111");
  const retry = linkPolicy.deriveRotationToken(secret, "viagem-certa", "11111111-1111-4111-8111-111111111111");
  const second = linkPolicy.deriveRotationToken(secret, "viagem-certa", "22222222-2222-4222-8222-222222222222");
  assert.ok(first.length >= 32);
  assert.equal(first, retry);
  assert.notEqual(first, second);
  assert.equal(linkPolicy.tokenMatches(first, linkPolicy.hashToken(first)), true);
  assert.equal(linkPolicy.tokenMatches(second, linkPolicy.hashToken(first)), false);
});

test("normal profile updates cannot rotate the public link entity", () => {
  const begin = api.indexOf("async function ensureDriverPublicAgenda");
  const end = api.indexOf("async function regenerateDriverPublicAgenda", begin);
  const ensure = api.slice(begin, end);
  assert.match(ensure, /buildProfileUpdate/);
  for (const field of [
    "driverPhotoUrl", "vehicleMakeModel", "paymentInstructions",
    "selectedPublicProfileUuid", "publicProfileMode",
  ]) assert.match(profilePolicySource, new RegExp(field));
  assert.match(ensure, /driverWhatsapp/);
  assert.doesNotMatch(ensure, /tx\.set\(linkRef/);
  assert.doesNotMatch(ensure, /agendaTokenHash:/);
  assert.match(ensure, /agenda_token_mismatch/);
  assert.match(api, /tripPublicAgendaLinks/);
  assert.match(calendar, /tripPublicAgendaLinks/);
});

test("Android UI exposes three source modes, selected profile, per-field reset and stable link", () => {
  assert.match(ui, /Usar dados da BlaBlaCar/);
  assert.match(ui, /Usar meus próprios dados/);
  assert.match(ui, /BlaBlaCar \+ personalizar/);
  assert.match(ui, /Perfil exibido na Agenda Pública/);
  assert.match(ui, /Voltar ao automático/);
  assert.match(ui, /WhatsApp e pagamento nunca são sobrescritos/);
  assert.match(ui, /O token não muda ao salvar dados/);
  assert.match(publicProfileKt, /PublicDriverProfilePolicy/);
});

test("BlaBlaCar public profile persistence is UUID-gated and monotonic", () => {
  assert.match(dynamicAccounts, /persistPublicProfileEvidence/);
  assert.match(dynamicAccounts, /expectedUuid !in observed/);
  assert.match(dynamicAccounts, /PROFILE_UUID_MISMATCH/);
  assert.match(publicProfileKt, /incoming\.trim\(\)\.takeIf\(String::isNotEmpty\) \?: old/);
  assert.match(publicProfileKt, /capture\.reviewCount \?: prior\?\.reviewCount/);
});
