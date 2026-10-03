"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "..");
const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(root, "public", "public-agenda-shell-0569.js"), "utf8");
const html = fs.readFileSync(path.join(root, "public", "index.html"), "utf8");
const firebase = JSON.parse(fs.readFileSync(path.join(root, "firebase.json"), "utf8"));

test("0.1.724 canonical agenda invalidation uses an updateTime-backed state token", () => {
  assert.match(api, /function canonicalAgendaChangeToken0724\(docs\)/);
  assert.match(api, /doc\.updateTime/);
  assert.match(api, /changeToken0724: canonicalAgendaChangeToken0724\(canonicalDocs0495\)/);
  assert.match(api, /req\.query && req\.query\.sinceToken/);
  assert.match(api, /tokenChanged \|\| \(!sinceToken && cursorChanged\)/);
});

test("0.1.724 browser acknowledges an invalidation only after applying fresh agenda data", () => {
  assert.match(web, /let agendaChangeToken0724 = "";/);
  assert.match(web, /let agendaReloadPending0724 = false;/);
  assert.match(web, /let agendaLoadPromise0724 = null;/);
  assert.match(web, /if \(agendaLoadPromise0724\) \{[\s\S]*agendaReloadPending0724 = true;[\s\S]*await agendaLoadPromise0724;/);
  assert.match(web, /const appliedToken0724 = String\(body\?\.changeToken0724 \|\| ""\)\.trim\(\);/);
  assert.match(web, /if \(agendaChangeToken0724\) query\.set\("sinceToken", agendaChangeToken0724\);/);
  const watcherStart = web.indexOf("async function watchAgendaCanonicalChanges0632()");
  const loaderStart = web.indexOf("async function loadAgenda0569", watcherStart);
  const watcher = web.slice(watcherStart, loaderStart);
  assert.match(watcher, /if \(change\?\.changed === true\) \{[\s\S]*await loadAgenda0569\(true\);/);
  assert.doesNotMatch(watcher, /agendaChangeCursor0632\s*=/);
  assert.doesNotMatch(watcher, /agendaChangeToken0724\s*=/);
});

test("0.1.724 critical public shell is cache-busted and explicitly revalidated", () => {
  assert.match(html, /public-agenda-shell-0569\.js\?v=0\.1\.724-lossless-realtime/);
  const headers = firebase.hosting.headers || [];
  const indexRule = headers.find((rule) => rule.source === "/index.html");
  const shellRule = headers.find((rule) => rule.source === "/public-agenda-shell-0569.js");
  assert.ok(indexRule);
  assert.ok(shellRule);
  assert.match(indexRule.headers.map((h) => h.value).join(" "), /no-cache/);
  assert.match(shellRule.headers.map((h) => h.value).join(" "), /no-cache/);
});
