"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const { validatedBlaBlaManageIdentity0472 } = require("../agenda-admin-0417");

test("0472 accepts only exact official Editar sua carona URLs", () => {
  const id = "01a058be-73c8-7845-9ad2-076aaef9883c";
  const valid = validatedBlaBlaManageIdentity0472(
    "https://www.blablacar.com.br/rides/offer/edit/" + id + "?search_uuid=noise#fragment",
  );
  assert.ok(valid);
  assert.equal(valid.tripId, id);
  assert.equal(valid.url, "https://www.blablacar.com.br/rides/offer/edit/" + id);
  assert.equal(validatedBlaBlaManageIdentity0472("https://evil.example/rides/offer/edit/" + id), null);
  assert.equal(validatedBlaBlaManageIdentity0472("https://www.blablacar.com.br/trip?id=" + id), null);
  assert.equal(validatedBlaBlaManageIdentity0472("https://www.blablacar.com.br/rides/offer/edit/" + id + "/options"), null);
});

test("0472 requires Android confirmation before strong server promotion", () => {
  const index = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  const admin = fs.readFileSync(path.join(__dirname, "..", "agenda-admin-0417.js"), "utf8");
  assert.ok(index.includes("url.pathname.match(/\\/rides\\/offer\\/edit\\/([^/?#]+)/i)"));
  assert.ok(index.includes("confirmDriverBlaBlaIdentityRecovery0472"));
  assert.ok(index.includes("blablacar_identity_already_bound"));
  assert.ok(index.includes("canonicalServerProjectionPatch0468"));
  assert.ok(admin.includes("BLABLACAR_IDENTITY_RECOVERY_REQUESTED_0472"));
  assert.ok(admin.includes("admin_blablacar_identity_requested"));
  assert.ok(admin.includes("manualIdentityAssignments0472"));
});

test("0472 admin request stores pending evidence, not blablaTripId", () => {
  const admin = fs.readFileSync(path.join(__dirname, "..", "agenda-admin-0417.js"), "utf8");
  const start = admin.indexOf("async function requestAdminTripBlaBlaIdentityRecovery0472");
  const end = admin.indexOf("async function updateAdminTripBlaBlaPublicUrl0465", start);
  assert.ok(start >= 0 && end > start);
  const scope = admin.slice(start, end);
  assert.ok(scope.includes("manualBlaBlaManageUrl0472"));
  assert.ok(scope.includes("manualBlaBlaTripId0472"));
  assert.equal(/\n\s*blablaTripId\s*:/.test(scope), false);
});
