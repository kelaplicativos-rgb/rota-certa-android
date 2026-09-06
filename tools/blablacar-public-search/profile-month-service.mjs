import http from 'node:http';
import { chromium } from 'playwright';
import {
  buildSearchUrl, city, cleanPrice, dedupeRoutes, detectFlags, encodedPlaceId, fold,
  loadStaticPlaces, monthDates, normalizeUuid, resultStatus, targetNames, tripIdFromHref, uuidFromProfileHref,
} from './profile-month-core.mjs';

const PORT = Number(process.env.PORT || 8080);
const TOKEN = String(process.env.COLLECTOR_TOKEN || '').trim();
const GOOGLE_KEY = String(process.env.GOOGLE_MAPS_API_KEY || '').trim();
const MAX_ROUTES = 4;
const MAX_QUERIES = 96;
const REQUEST_TIMEOUT_MS = 60_000;
const DETAIL_WAIT_MS = 900;
const SEARCH_WAIT_MS = 1_800;
const STATIC_PLACES = await loadStaticPlaces(process.cwd());

function json(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'content-length': Buffer.byteLength(body),
  });
  res.end(body);
}

async function readJson(req) {
  let raw = '';
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 256_000) throw new Error('payload muito grande');
  }
  return JSON.parse(raw || '{}');
}

function authorize(req) {
  if (!TOKEN) return true;
  return String(req.headers['x-rota-certa-collector-token'] || '') === TOKEN;
}

async function resolvePlaceId(address, explicit) {
  if (explicit) return explicit;
  const cached = STATIC_PLACES.get(city(address));
  if (cached) return cached;
  if (!GOOGLE_KEY) throw new Error(`place_id desconhecido para ${address}; configure GOOGLE_MAPS_API_KEY`);
  const u = new URL('https://maps.googleapis.com/maps/api/geocode/json');
  u.searchParams.set('address', address);
  u.searchParams.set('region', 'br');
  u.searchParams.set('language', 'pt-BR');
  u.searchParams.set('key', GOOGLE_KEY);
  const response = await fetch(u, { signal: AbortSignal.timeout(30_000) });
  if (!response.ok) throw new Error(`Google Geocoding HTTP ${response.status}`);
  const body = await response.json();
  const googlePlaceId = body?.results?.[0]?.place_id;
  if (!googlePlaceId) throw new Error(`não foi possível resolver ${address}: ${body?.status ?? 'sem status'}`);
  return encodedPlaceId(googlePlaceId);
}

async function resolveProfiles(page, requestedProfiles) {
  const profiles = [];
  for (const input of requestedProfiles) {
    const uuid = normalizeUuid(input?.uuid ?? input);
    const url = `https://www.blablacar.com.br/user/show/${uuid}`;
    const nav = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: REQUEST_TIMEOUT_MS });
    if (!nav || nav.status() >= 400) throw new Error(`perfil ${uuid} retornou HTTP ${nav?.status() ?? 'sem status'}`);
    await page.waitForTimeout(600);
    const title = await page.title();
    const bodyText = await page.locator('body').innerText({ timeout: 10_000 });
    const fromTitle = title.match(/Perfil p[úu]blico de\s+(.+)/i)?.[1]?.trim();
    const h1 = (await page.locator('h1').first().textContent().catch(() => null))?.trim();
    const imageAlt = (await page.locator('img[alt]').first().getAttribute('alt').catch(() => null))?.trim();
    const name = fromTitle || h1 || imageAlt || String(input?.label || '').trim();
    if (!name || /viaje com a blablacar/i.test(name) || !fold(bodyText).includes(fold(name.split(' ')[0]))) {
      throw new Error(`não foi possível validar o nome público do perfil ${uuid}`);
    }
    profiles.push({ uuid, name, profile_url: page.url(), title });
  }
  return profiles;
}

async function extractCards(page) {
  return page.locator('[data-testid="e2e-srp-card"]').evaluateAll((cards) => cards.map((card) => {
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
}

async function verifyTargetUuid(detailPage, tripHref, targetUuids) {
  const absolute = new URL(tripHref, 'https://www.blablacar.com.br').toString();
  const nav = await detailPage.goto(absolute, { waitUntil: 'domcontentloaded', timeout: REQUEST_TIMEOUT_MS });
  if (!nav || nav.status() >= 400) return { uuid: null, status: `http_${nav?.status() ?? 'unknown'}` };
  await detailPage.waitForTimeout(DETAIL_WAIT_MS);
  const hrefs = await detailPage.locator('a[href*="/user/show/"]').evaluateAll((anchors) => anchors.map((anchor) => anchor.getAttribute('href') || ''));
  const found = hrefs.map(uuidFromProfileHref).filter(Boolean);
  const uuid = found.find((value) => targetUuids.has(value)) || null;
  return { uuid, status: uuid ? 'verified' : 'target_uuid_not_found', profile_hrefs_seen: found.length };
}

async function collectProfileMonth(input) {
  const requestedProfiles = Array.isArray(input.profiles) ? input.profiles : [];
  if (requestedProfiles.length < 1 || requestedProfiles.length > 4) throw new Error('informe de 1 a 4 perfis');
  const routes = dedupeRoutes(input.routes, MAX_ROUTES);
  if (!routes.length) {
    return {
      schema_version: 1,
      status: 'scope_required',
      month: input.month ?? null,
      profiles: requestedProfiles.map((p) => ({ uuid: String(p?.uuid ?? p ?? '') })),
      trips: [],
      coverage: { complete_for_scope: false, global_profile_month_complete: false, reason: 'Nenhuma rota dinâmica foi fornecida pela Agenda.' },
    };
  }
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  const dates = monthDates(input.month, input.include_past ? null : today);
  const queryCount = routes.length * dates.length;
  if (!dates.length) throw new Error('o mês informado não possui datas públicas pesquisáveis');
  if (queryCount > MAX_QUERIES) throw new Error(`escopo muito grande: ${queryCount} consultas; limite ${MAX_QUERIES}`);

  // PR105 was proven with a normal rendered Chromium inside Xvfb. Keep the same mode.
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ locale: 'pt-BR', timezoneId: 'America/Sao_Paulo', viewport: { width: 390, height: 844 } });
  const page = await context.newPage();
  const detailPage = await context.newPage();
  try {
    const profiles = await resolveProfiles(page, requestedProfiles);
    const names = targetNames(profiles);
    const targetUuids = new Set(profiles.map((p) => p.uuid));
    const resolvedRoutes = [];
    for (const route of routes) {
      resolvedRoutes.push({
        ...route,
        from_place_id: await resolvePlaceId(route.from, route.from_place_id),
        to_place_id: await resolvePlaceId(route.to, route.to_place_id),
      });
    }

    const queryResults = [];
    const trips = [];
    let unresolvedTargetCards = 0;
    for (const route of resolvedRoutes) {
      for (const date of dates) {
        const url = buildSearchUrl(route, date, route.from_place_id, route.to_place_id);
        let queryStatus = 'error';
        try {
          const nav = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: REQUEST_TIMEOUT_MS });
          if (!nav || nav.status() >= 400) throw new Error(`HTTP ${nav?.status() ?? 'unknown'}`);
          await page.waitForTimeout(SEARCH_WAIT_MS);
          const final = new URL(page.url());
          const body = await page.locator('body').innerText({ timeout: 10_000 });
          const zeroResults = /Ainda não existem viagens entre essas cidades/i.test(body) || /0 viagem disponível/i.test(body);
          const cards = await extractCards(page);
          const exact = final.searchParams.get('db') === date && city(final.searchParams.get('fn') || '') === city(route.from) && city(final.searchParams.get('tn') || '') === city(route.to);
          const contentConfirmed = zeroResults || cards.some((card) => card.driver_name);
          queryStatus = exact && contentConfirmed ? 'validated' : 'mismatch';

          for (const card of cards) {
            const target = names.get(fold(card.driver_name || ''));
            if (!target) continue;
            if (!card.href) {
              unresolvedTargetCards++;
              const flags = detectFlags(card.text);
              trips.push({
                profile_uuid: target.uuid,
                profile_name: target.name,
                date,
                departure_time: card.departure_time,
                arrival_time: card.arrival_time,
                search_from: route.from,
                search_to: route.to,
                actual_departure: card.actual_departure,
                actual_arrival: card.actual_arrival,
                price: cleanPrice(card.price_text),
                flags,
                availability: flags.includes('Cheio') ? 'full' : 'unknown',
                trip_href: null,
                trip_id: null,
                uuid_validation: 'unresolved_no_trip_link',
              });
              continue;
            }
            const verified = await verifyTargetUuid(detailPage, card.href, targetUuids);
            if (!verified.uuid) {
              unresolvedTargetCards++;
              continue;
            }
            const flags = detectFlags(card.text);
            trips.push({
              profile_uuid: verified.uuid,
              profile_name: profiles.find((p) => p.uuid === verified.uuid)?.name || card.driver_name,
              date,
              departure_time: card.departure_time,
              arrival_time: card.arrival_time,
              search_from: route.from,
              search_to: route.to,
              actual_departure: card.actual_departure,
              actual_arrival: card.actual_arrival,
              price: cleanPrice(card.price_text),
              flags,
              availability: flags.includes('Cheio') ? 'full' : flags.includes('Esgotará em breve') ? 'scarce' : 'available_or_unspecified',
              trip_href: new URL(card.href, 'https://www.blablacar.com.br').toString(),
              trip_id: tripIdFromHref(card.href),
              uuid_validation: 'verified_from_trip_detail_profile_link',
            });
          }
        } catch (error) {
          queryStatus = 'error';
          queryResults.push({ from: route.from, to: route.to, date, status: queryStatus, error: String(error?.message || error) });
          continue;
        }
        queryResults.push({ from: route.from, to: route.to, date, status: queryStatus });
      }
    }

    const deduped = new Map();
    for (const trip of trips) {
      const key = trip.trip_id ? `${trip.profile_uuid}|${trip.trip_id}` : `${trip.profile_uuid}|${trip.date}|${trip.departure_time}|${fold(trip.search_from)}|${fold(trip.search_to)}`;
      const current = deduped.get(key);
      if (!current || current.uuid_validation !== 'verified_from_trip_detail_profile_link') deduped.set(key, trip);
    }
    const status = resultStatus(queryResults, unresolvedTargetCards);
    return {
      schema_version: 1,
      collected_at: new Date().toISOString(),
      status,
      month: input.month,
      strategy: 'existing_rendered_public_search_plus_profile_uuid_verification',
      profiles,
      routes: resolvedRoutes.map(({ from, to }) => ({ from, to })),
      trips: [...deduped.values()].sort((a, b) => `${a.date} ${a.departure_time || ''}`.localeCompare(`${b.date} ${b.departure_time || ''}`)),
      coverage: {
        complete_for_scope: status === 'validated',
        global_profile_month_complete: false,
        reason: 'A busca cobre somente as rotas dinâmicas fornecidas pela Agenda; UUID identifica o motorista, mas não enumera sozinho todas as rotas possíveis.',
        requested_queries: queryCount,
        validated_queries: queryResults.filter((q) => q.status === 'validated').length,
        failed_or_mismatched_queries: queryResults.filter((q) => q.status !== 'validated').length,
        unresolved_target_cards: unresolvedTargetCards,
        past_dates_skipped: !input.include_past,
      },
      query_results: queryResults,
    };
  } finally {
    await browser.close().catch(() => {});
  }
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/healthz') return json(res, 200, { ok: true });
  if (req.method !== 'POST' || req.url !== '/v1/blablacar/profile-month') return json(res, 404, { error: 'not_found' });
  if (!authorize(req)) return json(res, 401, { error: 'unauthorized' });
  try {
    const input = await readJson(req);
    const result = await collectProfileMonth(input);
    return json(res, result.status === 'scope_required' ? 422 : 200, result);
  } catch (error) {
    return json(res, 400, { status: 'error', error: String(error?.message || error), trips: [] });
  }
});

server.listen(PORT, '0.0.0.0', () => console.log(`collector listening on ${PORT}`));
