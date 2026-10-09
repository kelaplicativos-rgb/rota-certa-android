"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const {
  createStandaloneCoversRemote0736,
  publicAccessToken0736,
  ACCESS_COLLECTION_0736,
  STATE_COLLECTION_0736,
} = require("../standalone-covers-remote-0736");
const { createBlaBlaTripQueryRemote0737 } = require("../blablacar-trip-query-remote-0737");

test("OFF revoga imediatamente a mesma chave para capas E detalhe e ON restaura", async () => {
  const token = publicAccessToken0736();
  const tokenHash = crypto.createHash("sha256").update(token).digest("hex");
  const now = Date.now();
  const documents = new Map();
  const key = (coll, id) => coll + "/" + id;
  documents.set(key(STATE_COLLECTION_0736, "test-driver"), {
    driverUsername: "test-driver",
    accessToken: token,
    accessTokenHash: tokenHash,
    accessExpiresAtMillis: now + 86400000,
  });
  documents.set(key(ACCESS_COLLECTION_0736, tokenHash), {
    driverUsername: "test-driver",
    enabled: true,
    expiresAtMillis: now + 86400000,
  });
  const db = {
    collection(collection) {
      return {
        doc(id) {
          const refKey = key(collection, id);
          return {
            async get() {
              const data = documents.get(refKey);
              return { exists: !!data, data: () => data };
            },
            async set(value, options) {
              documents.set(refKey, options && options.merge
                ? { ...(documents.get(refKey) || {}), ...value } : value);
            },
          };
        },
      };
    },
    batch() {
      const writes = [];
      return {
        set(ref, value, options) { writes.push([ref, value, options]); },
        async commit() {
          for (const [ref, value, options] of writes) await ref.set(value, options);
        },
      };
    },
  };
  const options = {
    db,
    requireDriver: async () => ({ username: "test-driver" }),
    getMessaging: () => { throw Error("FCM inesperado"); },
    normalizeUsername: x => String(x || "").trim(),
    json: (res, code, body) => { res.status = code; res.body = body; return body; },
    fail: (res, code, errorCode) => {
      res.status = code; res.body = { code: errorCode }; return res.body;
    },
  };
  const covers = createStandaloneCoversRemote0736(options);
  const details = createBlaBlaTripQueryRemote0737(options);
  const req = enabled => ({ body: { enabled } });
  let res = {};
  await covers.setAccessEnabled0764(req(false), res);
  assert.equal(res.status, 200);
  assert.equal(res.body.enabled, false);
  assert.equal(documents.get(key(ACCESS_COLLECTION_0736, tokenHash)).enabled, false);
  res = {};
  await covers.latestPublic0736({}, res, token);
  assert.equal(res.status, 404);
  res = {};
  await details.latestPublic0737({}, res, token,
    "7371f028-9c55-4903-8444-308015823efd",
    "01a10f40-5046-7e0f-a0f1-084aaeb436e9");
  assert.equal(res.status, 404);

  res = {};
  await covers.setAccessEnabled0764(req(true), res);
  assert.equal(res.status, 200);
  assert.equal(res.body.enabled, true);
  res = {};
  await covers.latestPublic0736({}, res, token);
  assert.equal(res.status, 200);
  assert.equal(res.body.state, "PENDING_UNKNOWN");
  res = {};
  await details.latestPublic0737({}, res, token,
    "7371f028-9c55-4903-8444-308015823efd",
    "01a10f40-5046-7e0f-a0f1-084aaeb436e9");
  assert.equal(res.status, 200);
  assert.equal(res.body.state, "PENDING_UNKNOWN");
});

test("rota de toggling exige autenticação do motorista", () => {
  const fs = require("node:fs");
  const path = require("node:path");
  const route = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
  assert.match(route, /\/v1\/driver\/standalone-covers\/access\/state/);
  assert.match(route, /standaloneCoversRemote0736\.setAccessEnabled0764/);
});
