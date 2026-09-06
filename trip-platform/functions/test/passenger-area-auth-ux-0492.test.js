"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");
const { randomUUID } = require("node:crypto");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "minha-area.html"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "minha-area.js"), "utf8");
const publicHtml = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

function block(source, start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(from >= 0, `missing block start: ${start}`);
  assert.ok(to > from, `missing block end: ${end}`);
  return source.slice(from, to);
}

function makeElement(initialClass = "") {
  const classes = new Set(initialClass.split(/\s+/).filter(Boolean));
  return {
    value: "",
    textContent: "",
    innerHTML: "",
    className: initialClass,
    href: "",
    disabled: false,
    classList: {
      toggle(name, force) {
        if (force === undefined) {
          if (classes.has(name)) classes.delete(name); else classes.add(name);
          return classes.has(name);
        }
        if (force) classes.add(name); else classes.delete(name);
        return force;
      },
      contains(name) { return classes.has(name); },
    },
    addEventListener() {},
    appendChild() {},
    append() {},
  };
}

function initialClassFor(id) {
  const match = new RegExp(`id="${id}"[^>]*class="([^"]*)"`).exec(html);
  return match ? match[1] : "";
}

function runPassengerArea({ sessionToken = "", responseFactory = null } = {}) {
  const ids = [
    "backToAgenda0491", "contextError0491", "authLoading0492", "authLoadingMessage0492",
    "loginPanel0491", "privatePanel0491", "passwordPanel0491", "contact0491", "password0491",
    "loginMessage0491", "login0491", "newPassword0491", "newPasswordConfirm0491",
    "passwordMessage0491", "changePassword0491", "markRead0491", "notifications0491",
    "upcoming0491", "history0491", "refreshMessage0491", "logout0491",
  ];
  const elements = Object.fromEntries(ids.map((id) => [id, makeElement(initialClassFor(id))]));
  const store = new Map();
  if (sessionToken) store.set("rotaCertaPassengerSession0491:driver-test", sessionToken);

  const context = {
    URLSearchParams,
    Intl,
    Date,
    Error,
    Promise,
    encodeURIComponent,
    crypto: { randomUUID },
    location: { search: "?motorista=driver-test" },
    navigator: { onLine: true },
    sessionStorage: {
      getItem(key) { return store.get(key) || null; },
      setItem(key, value) { store.set(key, String(value)); },
      removeItem(key) { store.delete(key); },
    },
    document: {
      visibilityState: "visible",
      getElementById(id) { return elements[id] || null; },
      addEventListener() {},
      createElement() { return makeElement(""); },
    },
    window: {
      addEventListener() {},
      setInterval() { return 1; },
      clearInterval() {},
    },
    fetch: responseFactory || (() => new Promise(() => {})),
    setTimeout,
    clearTimeout,
  };
  vm.runInNewContext(web, context, { filename: "minha-area.js" });
  return { context, elements, store };
}

test("0492 phone UX is simple while explicit international normalization remains supported", () => {
  assert.match(html, /placeholder="Digite seu telefone\/WhatsApp"/);
  assert.doesNotMatch(html, /\+ código do país e número/);
  assert.doesNotMatch(html, /\+55/);
  assert.doesNotMatch(web, /\+55/);\n  assert.equal(html.includes("\\\\n"), false, "Minha Área HTML must not contain literal \\\\n escape text");

  const source = block(api, "function normalizeBrazilWhatsapp", "function publicBookingIdempotencyKey");
  const sandbox = {};
  vm.runInNewContext(source + "; this.normalizePhone = normalizeBrazilWhatsapp;", sandbox);
  assert.equal(sandbox.normalizePhone("+351 912 345 678"), "+351912345678");
  assert.equal(sandbox.normalizePhone("+1 (415) 555-0123"), "+14155550123");
  assert.match(source, /explicitInternational/);
  assert.match(source, /\^\[1-9\]\\d\{7,14\}\$/);
});

test("0492 password contract accepts four or more characters without breaking existing longer credentials", () => {
  assert.equal((html.match(/minlength="4"/g) || []).length, 3);
  assert.doesNotMatch(html, /minlength="8"/);
  assert.match(web, /password\.length < 4/);
  assert.doesNotMatch(web, /password\.length < 8/);

  const source = block(api, "function passengerPassword", "function passengerPasswordDigest");
  const sandbox = {};
  vm.runInNewContext(source + "; this.passengerPassword = passengerPassword;", sandbox);
  assert.throws(() => sandbox.passengerPassword(""));
  assert.throws(() => sandbox.passengerPassword("1"));
  assert.throws(() => sandbox.passengerPassword("123"));
  assert.equal(sandbox.passengerPassword("1234"), "1234");
  assert.equal(sandbox.passengerPassword("12345"), "12345");
  assert.equal(sandbox.passengerPassword("123456"), "123456");
  assert.equal(sandbox.passengerPassword("existing-Long_Pass!"), "existing-Long_Pass!");
});

test("0492 password hashing and server-side rate limiting remain intact", () => {
  const digest = block(api, "function passengerPasswordDigest", "function temporaryPassengerPassword");
  assert.match(digest, /crypto\.scryptSync/);

  const rate = block(api, "async function enforceBookingRateLimit", "async function enforcePublicDebugRateLimit");
  assert.match(rate, /clientIp\(req\)/);
  assert.match(rate, /count >= 10/);
  assert.match(rate, /httpStatus: 429/);

  const login = block(api, "async function loginPassengerAccount", "function passengerPrivateTrip0491");
  assert.match(login, /await enforceBookingRateLimit\(req\)/);
  assert.match(login, /passengerPasswordDigest/);
  assert.match(login, /safeEqual/);
});

test("0492 Minha Área never paints Login while an existing session is unresolved", () => {
  assert.match(html, /id="loginPanel0491" class="card hidden"/);
  assert.match(html, /id="authLoading0492" class="card hidden"/);
  const { elements } = runPassengerArea({ sessionToken: "session-token-abcdefghijklmnopqrstuvwxyz" });
  assert.equal(elements.loginPanel0491.classList.contains("hidden"), true);
  assert.equal(elements.authLoading0492.classList.contains("hidden"), false);
  assert.equal(elements.privatePanel0491.classList.contains("hidden"), true);
});

test("0492 no-session state renders Login immediately without a fake loading delay", () => {
  const { elements } = runPassengerArea();
  assert.equal(elements.loginPanel0491.classList.contains("hidden"), false);
  assert.equal(elements.authLoading0492.classList.contains("hidden"), true);
  assert.equal(elements.privatePanel0491.classList.contains("hidden"), true);
});

test("0492 validated session transitions loading directly to private content without Login", async () => {
  const responseFactory = async (url) => ({
    ok: true,
    status: 200,
    async json() {
      if (url.includes("/bookings")) return { bookings: [] };
      if (url.includes("/notifications")) return { notifications: [], unreadCount: 0 };
      return { mustChangePassword: false };
    },
  });
  const { elements } = runPassengerArea({
    sessionToken: "session-token-abcdefghijklmnopqrstuvwxyz",
    responseFactory,
  });
  await new Promise((resolve) => setImmediate(resolve));
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(elements.loginPanel0491.classList.contains("hidden"), true);
  assert.equal(elements.authLoading0492.classList.contains("hidden"), true);
  assert.equal(elements.privatePanel0491.classList.contains("hidden"), false);
});

test("0492 invalid or expired session resolves to Login only after server rejection", async () => {
  const responseFactory = async () => ({
    ok: false,
    status: 401,
    async json() { return { message: "Sua sessão expirou.", code: "passenger_session_expired" }; },
  });
  const { elements, store } = runPassengerArea({
    sessionToken: "session-token-abcdefghijklmnopqrstuvwxyz",
    responseFactory,
  });
  assert.equal(elements.loginPanel0491.classList.contains("hidden"), true);
  await new Promise((resolve) => setImmediate(resolve));
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(elements.loginPanel0491.classList.contains("hidden"), false);
  assert.equal(elements.authLoading0492.classList.contains("hidden"), true);
  assert.equal(store.has("rotaCertaPassengerSession0491:driver-test"), false);
});

test("0492 Voltar à Agenda is a direct public navigation and remains distinct from logout", () => {
  const { elements, store } = runPassengerArea({ sessionToken: "session-token-abcdefghijklmnopqrstuvwxyz" });
  assert.equal(elements.backToAgenda0491.href, "/driver-test");
  assert.equal(store.has("rotaCertaPassengerSession0491:driver-test"), true);

  const init = block(web, "function init0491", "init0491();");
  assert.match(init, /backToAgenda0491"\)\.href = "\/" \+ encodeURIComponent\(driverUsername0491\)/);
  assert.doesNotMatch(init, /backToAgenda0491[\s\S]{0,180}logout0491/);
  assert.match(web, /async function logout0491/);
});

test("0492 public Agenda remains password-free and no admin UI is reintroduced", () => {
  assert.doesNotMatch(publicHtml, /type="password"/i);
  assert.doesNotMatch(publicHtml, /Administrar esta viagem/i);
  assert.doesNotMatch(publicHtml, /agenda-admin/i);
  assert.doesNotMatch(web, /\/v1\/admin\//);
});
