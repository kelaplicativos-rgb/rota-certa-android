"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(root, "public", "app.js"), "utf8");
const security = fs.readFileSync(path.join(root, "public", "firebase-security.js"), "utf8");
const html = fs.readFileSync(path.join(root, "public", "index.html"), "utf8");
const firebaseJson = JSON.parse(fs.readFileSync(path.join(root, "firebase.json"), "utf8"));
const firebaseRc = JSON.parse(fs.readFileSync(path.join(root, ".firebaserc"), "utf8"));

test("trip platform is bound to the real Rota Certa Firebase project", () => {
  assert.equal(firebaseRc.projects.default, "rota-certa-7ccc8");
  assert.match(api, /PUBLIC_WEB_APP_ID = "1:353336964879:web:e7e6924d865a221d99f07a"/);
});

test("web bootstraps Firebase anonymous auth and reCAPTCHA Enterprise App Check", () => {
  assert.match(security, /ReCaptchaEnterpriseProvider/);
  assert.match(security, /6LdqZpotAAAAANdCM4Uj7UWOi6XSLSUfL_1clvMb/);
  assert.match(security, /signInAnonymously\(\)/);
  assert.match(security, /appCheck\.getToken\(false\)/);
  assert.match(security, /user\.getIdToken\(\)/);
  assert.match(security, /"X-Firebase-AppCheck"/);
  assert.match(security, /Authorization:/);
});

test("hosting loads Firebase from reserved same-origin URLs and allows only required reCAPTCHA CSP endpoints", () => {
  assert.match(html, /\/__\/firebase\/8\.10\.1\/firebase-app\.js/);
  assert.match(html, /\/__\/firebase\/8\.10\.1\/firebase-app-check\.js/);
  assert.match(html, /\/__\/firebase\/8\.10\.1\/firebase-auth\.js/);
  assert.match(html, /\/__\/firebase\/init\.js/);
  assert.match(html, /firebaseappcheck\.googleapis\.com/);
  assert.match(html, /identitytoolkit\.googleapis\.com/);
  assert.match(html, /recaptcha\.google\.com\/recaptcha\//);
  const headers = firebaseJson.hosting.headers.flatMap((entry) => entry.headers || []);
  const csp = headers.find((entry) => entry.key === "Content-Security-Policy");
  assert.ok(csp);
  assert.match(csp.value, /www\.google\.com\/recaptcha\//);
  assert.match(csp.value, /www\.gstatic\.com\/recaptcha\//);
});

test("public booking and cancellation send and verify Firebase attestation", () => {
  assert.match(web, /protectedPublicFetch/);
  assert.equal((web.match(/protectedPublicFetch\(\`\/v1\/public\/trips/g) || []).length, 2);
  assert.match(api, /getAppCheck\(\)\.verifyToken\(appCheckToken\)/);
  assert.match(api, /getAuth\(\)\.verifyIdToken\(match\[1\]\)/);
  assert.match(api, /appCheckResult\.appId !== PUBLIC_WEB_APP_ID/);
  assert.equal((api.match(/if \(!\(await requirePublicFirebaseClient\(req, res\)\)\) return;/g) || []).length, 2);
});

test("Firestore remains deny-by-default and no Firebase API key is hardcoded in the public source", () => {
  const rules = fs.readFileSync(path.join(root, "firestore.rules"), "utf8");
  assert.match(rules, /allow read, write: if false/);
  assert.doesNotMatch(security, /AIza[0-9A-Za-z_-]{20,}/);
  assert.doesNotMatch(web, /AIza[0-9A-Za-z_-]{20,}/);
});
