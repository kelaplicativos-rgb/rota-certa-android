import test from 'node:test';
import assert from 'node:assert/strict';
import { buildSearchUrl, dedupeRoutes, monthDates, normalizeUuid, resultStatus, tripIdFromHref, uuidFromProfileHref } from './profile-month-core.mjs';

test('normaliza e valida UUID de perfil', () => {
  assert.equal(normalizeUuid('7371F028-9C55-4903-8444-308015823EFD'), '7371f028-9c55-4903-8444-308015823efd');
  assert.throws(() => normalizeUuid('nao-e-uuid'));
});

test('gera datas do mês e pode pular passado', () => {
  assert.equal(monthDates('2026-08').length, 31);
  assert.deepEqual(monthDates('2026-08', '2026-08-29'), ['2026-08-29', '2026-08-30', '2026-08-31']);
});

test('rotas são dinâmicas, deduplicadas e direcionais', () => {
  const routes = dedupeRoutes([
    { from: 'Cidade A', to: 'Cidade B' },
    { from: ' cidade a ', to: 'cidade b' },
    { from: 'Cidade B', to: 'Cidade A' },
  ]);
  assert.equal(routes.length, 2);
  assert.equal(routes[1].from, 'Cidade B');
});

test('extrai UUID somente de URL pública de perfil', () => {
  assert.equal(uuidFromProfileHref('/user/show/175a7068-50d8-40c3-a27a-214b9c6e0461'), '175a7068-50d8-40c3-a27a-214b9c6e0461');
  assert.equal(uuidFromProfileHref('/trip?id=abc'), null);
});

test('extrai trip id sem confundir search_uuid', () => {
  const href = '/trip?source=CARPOOLING&id=trip-real&search_uuid=3e934093-459d-467c-9b2a-49335118c7b8';
  assert.equal(tripIdFromHref(href), 'trip-real');
});

test('validated exige todas consultas e nenhum alvo não resolvido', () => {
  assert.equal(resultStatus([{ status: 'validated' }, { status: 'validated' }], 0), 'validated');
  assert.equal(resultStatus([{ status: 'validated' }, { status: 'error' }], 0), 'partial');
  assert.equal(resultStatus([{ status: 'validated' }], 1), 'partial');
});

test('URL mantém rota, data e ids exatos', () => {
  const url = new URL(buildSearchUrl({ from: 'Origem X', to: 'Destino Y' }, '2026-08-25', 'FROMID', 'TOID'));
  assert.equal(url.searchParams.get('fn'), 'Origem X');
  assert.equal(url.searchParams.get('tn'), 'Destino Y');
  assert.equal(url.searchParams.get('db'), '2026-08-25');
  assert.equal(url.searchParams.get('from_place_id'), 'FROMID');
  assert.equal(url.searchParams.get('to_place_id'), 'TOID');
});
