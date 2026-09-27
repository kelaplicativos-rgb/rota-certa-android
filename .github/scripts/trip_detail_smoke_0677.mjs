import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';

class FakeNode {
  constructor(text = '', attrs = {}, leaves = []) {
    this.innerText = text;
    this.textContent = text;
    this.attrs = attrs;
    this.children = [];
    this.parentElement = null;
    this.isConnected = true;
    this.href = attrs.href || '';
    this._leaves = leaves;
  }
  getAttribute(name) { return this.attrs[name] ?? ''; }
  querySelector() { return null; }
  querySelectorAll(selector) {
    if (selector === 'span, p, div, time') return this._leaves;
    return [];
  }
  closest() { return null; }
  matches() { return false; }
  contains(node) { return node === this; }
  getClientRects() { return [1]; }
}

const departureClock = new FakeNode('10:30');
const arrivalClock = new FakeNode('14:10');
const departure = new FakeNode(
  'São Paulo',
  {'data-testid':'e2e-itinerary-departure-station'},
  [departureClock],
);
const arrival = new FakeNode(
  'Pouso Alegre',
  {'data-testid':'e2e-itinerary-arrival-station'},
  [arrivalClock],
);
departureClock.parentElement = departure;
arrivalClock.parentElement = arrival;

const empty = [];
const documentElement = {
  scrollHeight: 1000,
  clientHeight: 1000,
  cloneNode() {
    return {
      outerHTML: '<html><body>Resumo da viagem</body></html>',
      querySelectorAll() { return []; },
    };
  },
};

const document = {
  readyState: 'complete',
  body: {innerText:'Resumo da viagem', scrollHeight:1000},
  documentElement,
  scrollingElement: {scrollTop:0, scrollHeight:1000},
  querySelector(selector) {
    if (selector.includes('e2e-itinerary-departure-station') || selector === '[data-testid*="departure-station"]') return departure;
    if (selector.includes('e2e-itinerary-arrival-station') || selector === '[data-testid*="arrival-station"]') return arrival;
    if (selector.includes('e2e-itinerary-departure-time') || selector === '[data-testid*="departure-time"]') return departureClock;
    if (selector.includes('e2e-itinerary-arrival-time') || selector === '[data-testid*="arrival-time"]') return arrivalClock;
    return null;
  },
  querySelectorAll(selector) {
    if (selector.includes('[data-testid*="itinerary-departure-station"]')) return [departure, arrival];
    if (selector === '[data-testid*="date"], time, h1, h2, h3') return [departureClock, arrivalClock];
    if (selector === '[data-testid], [aria-label]') return [departure, arrival];
    return empty;
  },
};

const tripId = '01a0359e-de23-78ab-ab26-6cc973c5c3d1';
const context = {
  document,
  location: {href:`https://www.blablacar.com.br/rides/offer?id=${tripId}&source=CARPOOLING`},
  window: {
    scrollY: 0,
    innerHeight: 1000,
    getComputedStyle() { return {display:'block', visibility:'visible', opacity:'1'}; },
  },
  URL,
  console,
};

const source = fs.readFileSync('app/src/main/assets/blablacar/scripts/trip_detail.js', 'utf8');
const raw = vm.runInNewContext(source, context, {timeout: 2000});
assert.equal(typeof raw, 'string', 'trip_detail.js must return a JSON string');
const payload = JSON.parse(raw);
assert.equal(payload.scriptError, undefined, 'valid DOM must not produce scriptError');
assert.deepEqual(Array.from(payload.itineraryStops), ['São Paulo', 'Pouso Alegre']);
assert.deepEqual(Array.from(payload.itineraryStopTimes), ['10:30', '14:10']);
assert.equal(payload.itineraryAuthoritative, true);
assert.match(payload.domHtml, /Resumo da viagem/);

console.log('trip_detail_smoke_0677: PASS');
