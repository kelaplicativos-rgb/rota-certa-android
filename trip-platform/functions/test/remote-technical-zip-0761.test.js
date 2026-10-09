"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const {validateTechnicalZip0761, MAX_TECHNICAL_ZIP_BYTES_0761} = require("../remote-health-0747");

function package0761() {
  // A small ZIP with a local file header, central directory and EOCD.
  const z = Buffer.from("504b030414000000000000000000000000000000000000000000000000504b01021400140000000000000000000000000000000000000000000000000000504b050600000000010001002e0000001e0000000000", "hex");
  return {
    schemaVersion: "rota-certa-remote-technical-zip-v1",
    archiveBase64: z.toString("base64"),
    archiveSha256: crypto.createHash("sha256").update(z).digest("hex"),
    archiveBytes: z.length,
    capturedAtMillis: 1791500000000,
    sourceAppVersion: "0.1.761",
    sourceCommitSha: "a".repeat(40),
    fileName: "rota-certa-saude-0.1.761-completo-20261009-123456.zip",
  };
}

test("accepts bounded ZIP with integrity metadata", () => {
  const input = package0761();
  const output = validateTechnicalZip0761(input);
  assert.equal(output.archiveSha256, input.archiveSha256);
  assert.equal(output.archiveBytes, input.archiveBytes);
});

test("rejects modified ZIP, unexpected fields and mismatched SHA", () => {
  const modified = package0761();
  modified.archiveSha256 = "f".repeat(64);
  assert.throws(() => validateTechnicalZip0761(modified), /SHA-256/i);
  const extra = package0761();
  extra.html = "<secret>";
  assert.throws(() => validateTechnicalZip0761(extra), /campo nao permitido/i);
  const invalid = package0761();
  invalid.archiveBase64 = Buffer.from("random text").toString("base64");
  invalid.archiveBytes = 11;
  assert.throws(() => validateTechnicalZip0761(invalid), /ZIP/i);
});

test("enforces hard ZIP cap and no arbitrary filename", () => {
  const tooLarge = package0761();
  tooLarge.archiveBase64 = Buffer.alloc(MAX_TECHNICAL_ZIP_BYTES_0761 + 1).toString("base64");
  assert.throws(() => validateTechnicalZip0761(tooLarge), /grande|limite/i);
  const badFile = package0761();
  badFile.fileName = "../../secrets.zip";
  assert.throws(() => validateTechnicalZip0761(badFile), /Nome/i);
});

test("private technical routes and consent-gated device code are wired", () => {
  const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  const svc = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app/src/main/java/br/com/mapeiaia/rotacerta/trips/RemoteCoversPollService0758.kt"), "utf8");
  assert.match(api, /refreshTechnicalPublic0761/);
  assert.match(api, /latestTechnicalPublic0761/);
  assert.match(api, /downloadTechnicalPublic0761/);
  assert.match(api, /pendingDriver0761/);
  assert.match(svc, /showConsent\("remote_health_collect", jobId\)/);
  assert.match(svc, /Aceitar/);
  assert.match(svc, /Recusar/);
});
