import { chromium } from 'playwright';
import fs from 'node:fs/promises';
import path from 'node:path';

const [requestPath, jsonOut, markdownOut, screenshotOut] = process.argv.slice(2);
if (!requestPath || !jsonOut || !markdownOut || !screenshotOut) {
  console.error('usage: node collect-dom.mjs <request.json> <result.json> <result.md> <screenshot.png>');
  process.exit(64);
}

function fold(value = '') {
  return String(value).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}

function city(value = '') {
  return fold(String(value).split(',')[0]).replace(/\s+/g, ' ');
}

function encodedPlaceId(googlePlaceId) {
  return Buffer.from(JSON.stringify({ i: googlePlaceId, p: 1, v: 1, t: [4] }), 'utf8').toString('base64');
}

async function loadCorridorPlaceIds() {
  const file = path.resolve('collector/places/corridor.json');
  try {
    const parsed = JSON.parse(await fs.readFile(file, 'utf8'));
    const map = new Map();
    for (const item of parsed?.places ?? []) {
      if (item?.address && item?.blablacar_place_id) map.set(city(item.address), item.blablacar_place_id);
    }
    return map;
  } catch {
    return new Map();
  }
}

const CORRIDOR_PLACE_IDS = await loadCorridorPlaceIds();

async function resolvePlaceId(address, explicit) {
  if (explicit) return explicit;
  const staticId = CORRIDOR_PLACE_IDS.get(city(address));
  if (staticId) return staticId;

  const key = process.env.GOOGLE_MAPS_API_KEY?.trim();
  if (!key) throw new Error(`place_id desconhecido para ${address}; não está no corredor pré-resolvido e GOOGLE_MAPS_API_KEY não está configurada`);

  const url = new URL('https://maps.googleapis.com/maps/api/geocode/json');
  url.searchParams.set('address', address);
  url.searchParams.set('region', 'br');
  url.searchParams.set('language', 'pt-BR');
  url.searchParams.set('key', key);
  const response = await fetch(url, { signal: AbortSignal.timeout(30_000) });
  if (!response.ok) throw new Error(`Google Geocoding HTTP ${response.status}`);
  const body = await response.json();
  const googlePlaceId = body?.results?.[0]?.place_id;
  if (!googlePlaceId) throw new Error(`não foi possível resolver place_id para ${address}: ${body?.status ?? 'sem status'}`);
  return encodedPlaceId(googlePlaceId);
}

function buildUrl(request, fromPlaceId, toPlaceId) {
  const u = new URL('https://www.blablacar.com.br/search');
  for (const [key, value] of [
    ['fn', request.from],
    ['tn', request.to],
    ['db', request.date],
    ['seats', String(request.seats ?? 1)],
    ['search_origin', 'SEARCH'],
    ['from_place_id', fromPlaceId],
    ['to_place_id', toPlaceId],
    ['p0[ac]', 'adult'],
  ]) u.searchParams.set(key, value);
  return u.toString();
}

function detectFlags(text) {
  const flags = [];
  for (const value of ['Cheio', 'Esgotará em breve', 'Super Driver', 'Perfil Verificado']) {
    if (fold(text).includes(fold(value))) flags.push(value);
  }
  return flags;
}

function cleanPrice(text) {
  if (!text) return null;
  const match = String(text).replace(/\u00a0/g, ' ').match(/R\$\s*[0-9.]+(?:,[0-9]{2})?/i);
  return match ? match[0].replace(/\s+/g, ' ').trim() : null;
}

function markdown(result) {
  const lines = [
    '# BlaBlaCar — busca pública renderizada',
    '',
    `- Status de validação: **${result.status}**`,
    `- Data: **${result.request.date}**`,
    `- Rota: **${result.request.from} → ${result.request.to}**`,
    `- Motoristas visíveis: **${result.driver_cards_count}**`,
    `- Ezequiel S: **${result.ezequiel_s_visible ? 'VISÍVEL' : 'NÃO VISÍVEL'}**`,
    `- Barbosa: **${result.barbosa_visible ? 'VISÍVEL' : 'NÃO VISÍVEL'}**`,
    '',
  ];
  if (!result.trips.length) {
    lines.push(result.zero_results_confirmed ? 'Nenhuma viagem disponível nesta rota/data.' : 'Nenhum cartão de motorista pôde ser validado.', '');
  } else {
    lines.push('## Motoristas', '');
    result.trips.forEach((trip, index) => {
      const flags = trip.flags.length ? ` — ${trip.flags.join(' · ')}` : '';
      lines.push(`${index + 1}. **${trip.driver_name}** — ${trip.departure_time ?? '?'} — ${trip.actual_departure ?? '?'} → ${trip.actual_arrival ?? '?'} — ${trip.price ?? 'sem preço'}${flags}`);
    });
    lines.push('');
  }
  lines.push('> A origem/destino real de cada cartão é registrada separadamente da rota pesquisada.', '');
  return lines.join('\n');
}

async function ensureParent(file) {
  await fs.mkdir(path.dirname(file), { recursive: true });
}

const request = JSON.parse(await fs.readFile(requestPath, 'utf8'));
if (!request.from || !request.to || !/^\d{4}-\d{2}-\d{2}$/.test(request.date ?? '')) {
  throw new Error('request inválido: from, to e date YYYY-MM-DD são obrigatórios');
}

let browser;
let page;
try {
  const [fromPlaceId, toPlaceId] = await Promise.all([
    resolvePlaceId(request.from, request.from_place_id),
    resolvePlaceId(request.to, request.to_place_id),
  ]);
  const searchUrl = buildUrl(request, fromPlaceId, toPlaceId);

  browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    viewport: { width: 390, height: 844 },
  });
  page = await context.newPage();
  const nav = await page.goto(searchUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 });
  if (!nav || nav.status() >= 400) throw new Error(`página pública retornou HTTP ${nav?.status() ?? 'sem status'}`);

  await page.waitForTimeout(8_000);
  const final = new URL(page.url());
  const body = await page.locator('body').innerText({ timeout: 10_000 });
  const zeroResults = /Ainda não existem viagens entre essas cidades/i.test(body) || /0 viagem disponível/i.test(body);

  const rawCards = await page.locator('[data-testid="e2e-srp-card"]').evaluateAll((cards) => cards.map((card) => {
    const text = (selector) => card.querySelector(selector)?.textContent?.trim() || null;
    return {
      driver_name: text('[data-testid="e2e-tripcard-driver-name"]'),
      departure_time: text('[data-testid="e2e-itinerary-departure-time"]'),
      arrival_time: text('[data-testid="e2e-itinerary-arrival-time"]'),
      actual_departure: text('[data-testid="e2e-itinerary-departure-station"]'),
      actual_arrival: text('[data-testid="e2e-itinerary-arrival-station"]'),
      price_text: text('[data-testid="e2e-tripcard-price"]'),
      text: card.innerText || '',
      href: card.querySelector('a[href*="/trip"]')?.getAttribute('href') || null,
    };
  }));

  const trips = rawCards
    .filter((card) => card.driver_name)
    .map((card) => {
      const flags = detectFlags(card.text);
      return {
        driver_name: card.driver_name,
        departure_time: card.departure_time,
        arrival_time: card.arrival_time,
        actual_departure: card.actual_departure,
        actual_arrival: card.actual_arrival,
        price: cleanPrice(card.price_text),
        flags,
        availability: flags.includes('Cheio') ? 'full' : flags.includes('Esgotará em breve') ? 'scarce' : 'available_or_unspecified',
        trip_href: card.href,
      };
    });

  const exactDate = final.searchParams.get('db') === request.date;
  const exactFrom = city(final.searchParams.get('fn') ?? '') === city(request.from);
  const exactTo = city(final.searchParams.get('tn') ?? '') === city(request.to);
  const routeVisible = fold(body).includes(city(request.from)) && fold(body).includes(city(request.to));
  const contentConfirmed = zeroResults || trips.length > 0;
  const names = trips.map((trip) => fold(trip.driver_name));

  const result = {
    schema_version: 4,
    request_id: request.request_id ?? null,
    collected_at: new Date().toISOString(),
    status: exactDate && exactFrom && exactTo && routeVisible && contentConfirmed ? 'validated' : 'mismatch',
    strategy: 'headed_chromium_rendered_public_page',
    place_resolution: CORRIDOR_PLACE_IDS.has(city(request.from)) && CORRIDOR_PLACE_IDS.has(city(request.to)) ? 'static_corridor' : 'mixed_or_geocoder',
    request: { from: request.from, to: request.to, date: request.date, seats: request.seats ?? 1 },
    resolved_place_ids: { from_place_id: fromPlaceId, to_place_id: toPlaceId },
    validation: {
      http_status: nav.status(),
      exact_date_match: exactDate,
      exact_origin_match: exactFrom,
      exact_destination_match: exactTo,
      route_visible_in_page: routeVisible,
      content_confirmed: contentConfirmed,
      final_url: page.url(),
      page_title: await page.title(),
    },
    zero_results_confirmed: zeroResults,
    driver_cards_count: trips.length,
    ezequiel_s_visible: names.includes('ezequiel s'),
    barbosa_visible: names.includes('barbosa'),
    trips,
  };

  await Promise.all([jsonOut, markdownOut, screenshotOut].map(ensureParent));
  await fs.writeFile(jsonOut, `${JSON.stringify(result, null, 2)}\n`);
  await fs.writeFile(markdownOut, markdown(result));
  await page.screenshot({ path: screenshotOut, fullPage: true }).catch(() => {});
  console.log(JSON.stringify({ status: result.status, drivers: result.driver_cards_count, zero_results: result.zero_results_confirmed, place_resolution: result.place_resolution }));
  if (result.status !== 'validated') process.exitCode = 2;
} catch (error) {
  const result = {
    schema_version: 4,
    request_id: request.request_id ?? null,
    collected_at: new Date().toISOString(),
    status: 'error',
    strategy: 'headed_chromium_rendered_public_page',
    error: String(error?.message ?? error),
    request: { from: request.from, to: request.to, date: request.date, seats: request.seats ?? 1 },
    zero_results_confirmed: false,
    driver_cards_count: 0,
    ezequiel_s_visible: null,
    barbosa_visible: null,
    trips: [],
  };
  await Promise.all([jsonOut, markdownOut].map(ensureParent));
  await fs.writeFile(jsonOut, `${JSON.stringify(result, null, 2)}\n`);
  await fs.writeFile(markdownOut, `# BlaBlaCar — erro de coleta\n\n${result.error}\n`);
  console.error(result.error);
  process.exitCode = 2;
} finally {
  await browser?.close().catch(() => {});
}
