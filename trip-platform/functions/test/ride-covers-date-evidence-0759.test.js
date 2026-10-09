"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const code = fs.readFileSync(path.join(__dirname, "../../../app/src/main/assets/blablacar/scripts/ride_covers_standalone.js"), "utf8");

function makeHarness0759(cardText, parentText = "") {
  const href = "https://www.blablacar.com.br/ride-plan/trip-edit/01a0c657-4609-7e32-9b23-58c79b93734e";
  const anchor = { href };
  const card = {
    innerText: cardText,
    textContent: cardText,
    parentElement: null,
    getAttribute: () => null,
    contains: () => false,
    compareDocumentPosition: () => 0,
    getClientRects: () => [1],
    querySelector(selector) { return null; },
    querySelectorAll(selector) { return selector === "a[href]" ? [anchor] : []; },
  };
  const ancestor = {
    innerText: parentText,
    textContent: parentText,
    parentElement: null,
    querySelectorAll: () => [card],
  };
  card.parentElement = ancestor;
  const body = { innerText: cardText, scrollHeight: 600 };
  const scrollingElement = { scrollHeight: 600, clientHeight: 800 };
  const document = {
    body,
    scrollingElement,
    documentElement: scrollingElement,
    readyState: "complete",
    querySelector: () => null,
    querySelectorAll(selector) {
      return selector.includes("e2e-your-rides-trip-card-") ? [card] : [];
    },
  };
  const window = {
    innerHeight: 800,
    scrollY: 0,
    getComputedStyle: () => ({ display: "block", visibility: "visible", opacity: "1" }),
  };
  const context = vm.createContext({ document, window, Node: { DOCUMENT_POSITION_FOLLOWING: 4 } });
  return {
    card, ancestor,
    read() { return JSON.parse(vm.runInContext(code, context)); },
  };
}

test("date shown only in the card body is extracted without opening details", () => {
  const h = makeHarness0759("Qua., 18 Nov.\\nTrês Corações\\nSanto André");
  assert.match(h.read().covers[0].dateText, /18 Nov/i);
});

test("date in an unambiguous single-card wrapper can be used", () => {
  const h = makeHarness0759("Três Corações Santo André", "Quarta-feira, 18 Nov. 2026");
  assert.match(h.read().covers[0].dateText, /18 Nov/i);
});

test("ambiguous date text is never guessed", () => {
  const h = makeHarness0759("18 Nov. e 19 Nov. - confirmar");
  assert.equal(h.read().covers[0].dateText, "");
});

test("a virtualized re-render cannot erase a previously observed date for the exact href", () => {
  const h = makeHarness0759("18 Nov.\\nTrês Corações");
  assert.match(h.read().covers[0].dateText, /18 Nov/i);
  h.card.innerText = "Três Corações";
  h.card.textContent = "Três Corações";
  h.ancestor.innerText = "";
  h.ancestor.textContent = "";
  assert.match(h.read().covers[0].dateText, /18 Nov/i);
});
