import fs from 'node:fs/promises';
import path from 'node:path';

export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function fold(value = '') {
  return String(value).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/\s+/g, ' ').trim();
}

export function city(value = '') {
  return fold(String(value).split(',')[0]);
}

export function normalizeUuid(value = '') {
  const uuid = String(value).trim().toLowerCase();
  if (!UUID_RE.test(uuid)) throw new Error(`UUID inválido: ${value}`);
  return uuid;
}

export function monthDates(month, today = null) {
  if (!/^\d{4}-\d{2}$/.test(month ?? '')) throw new Error('month deve usar YYYY-MM');
  const [year, number] = month.split('-').map(Number);
  if (number < 1 || number > 12) throw new Error('mês inválido');
  const last = new Date(Date.UTC(year, number, 0)).getUTCDate();
  const all = Array.from({ length: last }, (_, index) => `${month}-${String(index + 1).padStart(2, '0')}`);
  if (!today || !/^\d{4}-\d{2}-\d{2}$/.test(today)) return all;
  return all.filter((date) => date >= today);
}

export function encodedPlaceId(googlePlaceId) {
  return Buffer.from(JSON.stringify({ i: googlePlaceId, p: 1, v: 1, t: [4] }), 'utf8').toString('base64');
}

export function buildSearchUrl(route, date, fromPlaceId, toPlaceId) {
  const u = new URL('https://www.blablacar.com.br/search');
  for (const [key, value] of [
    ['fn', route.from], ['tn', route.to], ['db', date], ['seats', '1'], ['search_origin', 'SEARCH'],
    ['from_place_id', fromPlaceId], ['to_place_id', toPlaceId], ['p0[ac]', 'adult'],
  ]) u.searchParams.set(key, value);
  return u.toString();
}

export function dedupeRoutes(routes = [], limit = 6) {
  const seen = new Set();
  const result = [];
  for (const raw of routes) {
    const from = String(raw?.from ?? '').trim();
    const to = String(raw?.to ?? '').trim();
    if (!from || !to || city(from) === city(to)) continue;
    const key = `${fold(from)}|${fold(to)}`;
    if (seen.has(key)) continue;
    seen.add(key);
    result.push({ from, to, from_place_id: raw?.from_place_id || null, to_place_id: raw?.to_place_id || null });
    if (result.length >= limit) break;
  }
  return result;
}

export function uuidFromProfileHref(href = '') {
  const match = String(href).match(/\/user\/show\/([0-9a-f-]{36})(?:[/?#]|$)/i);
  return match ? normalizeUuid(match[1]) : null;
}

export function tripIdFromHref(href = '') {
  try { return new URL(href, 'https://www.blablacar.com.br').searchParams.get('id'); } catch { return null; }
}

export function detectFlags(text = '') {
  const flags = [];
  for (const value of ['Cheio', 'Esgotará em breve', 'Super Driver', 'Perfil Verificado']) {
    if (fold(text).includes(fold(value))) flags.push(value);
  }
  return flags;
}

export function cleanPrice(text = '') {
  const match = String(text).replace(/\u00a0/g, ' ').match(/R\$\s*[0-9.]+(?:,[0-9]{2})?/i);
  return match ? match[0].replace(/\s+/g, ' ').trim() : null;
}

export function targetNames(profiles) {
  return new Map(profiles.map((profile) => [fold(profile.name), profile]));
}

export function resultStatus(queryResults, unresolvedTargetCards) {
  const complete = queryResults.length > 0 && queryResults.every((item) => item.status === 'validated') && unresolvedTargetCards === 0;
  return complete ? 'validated' : 'partial';
}

export async function loadStaticPlaces(root = process.cwd()) {
  try {
    const parsed = JSON.parse(await fs.readFile(path.resolve(root, 'collector/places/corridor.json'), 'utf8'));
    return new Map((parsed?.places ?? []).filter((item) => item?.address && item?.blablacar_place_id).map((item) => [city(item.address), item.blablacar_place_id]));
  } catch { return new Map(); }
}
