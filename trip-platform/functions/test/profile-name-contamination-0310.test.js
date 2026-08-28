"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..", "..");
const accounts = fs.readFileSync(
  path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "BlaBlaDynamicAccounts.kt"),
  "utf8",
);
const ui = fs.readFileSync(
  path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "PublicAgendaSettingsUi.kt"),
  "utf8",
);
const policy = fs.readFileSync(
  path.join(root, "app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "BlaBlaDriverProfileNamePolicy.kt"),
  "utf8",
);

test("identity collector never uses a generic h1 as the driver name", () => {
  const identity = fs.readFileSync(
    path.join(root, "app", "src", "main", "assets", "blablacar", "scripts", "session_identity.js"),
    "utf8",
  );
  assert.doesNotMatch(identity, /driver-name"\], h1/);
  assert.doesNotMatch(identity, /profile-name"\], \[data-testid\*="driver-name"\], h1/);
  assert.match(identity, /explicitNameNode/);
  assert.match(identity, /profileAnchor/);
});

test("stored contaminated names are automatically purged", () => {
  assert.match(accounts, /PROFILE_NAME_CONTAMINATION_CLEANED/);
  assert.match(accounts, /BlaBlaDriverProfileNamePolicy\.normalize\(account\.profileName\)/);
  assert.match(accounts, /profileName = cleanName \?: previousCleanName/);
});

test("online settings sanitizes profile labels before rendering", () => {
  assert.match(ui, /BlaBlaDriverProfileNamePolicy\.normalize\(profile\.profileName\)/);
  assert.match(ui, /\?: profile\.displayLabel/);
});

test("profile-name policy explicitly rejects trip/date/route evidence", () => {
  assert.match(policy, /viagem/);
  assert.match(policy, /domingo/);
  assert.match(policy, /janeiro/);
  assert.match(policy, /value\.contains\("->"\)/);
});
