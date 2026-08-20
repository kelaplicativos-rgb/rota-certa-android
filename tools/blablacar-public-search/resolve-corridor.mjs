import fs from 'node:fs/promises';
import path from 'node:path';

// Materializa uma tabela estática de IDs públicos para reduzir dependência do geocoder em cada consulta.
const [inputPath, outputPath] = process.argv.slice(2);
if (!inputPath || !outputPath) {
  console.error('usage: node resolve-corridor.mjs <places.json> <out.json>');
  process.exit(64);
}

const key = process.env.GOOGLE_MAPS_API_KEY?.trim();
if (!key) throw new Error('GOOGLE_MAPS_API_KEY não configurada');

const places = JSON.parse(await fs.readFile(inputPath, 'utf8'));
if (!Array.isArray(places) || !places.length) throw new Error('lista de cidades inválida');

function encode(googlePlaceId) {
  return Buffer.from(JSON.stringify({ i: googlePlaceId, p: 1, v: 1, t: [4] }), 'utf8').toString('base64');
}

const resolved = [];
for (const address of places) {
  const url = new URL('https://maps.googleapis.com/maps/api/geocode/json');
  url.searchParams.set('address', address);
  url.searchParams.set('region', 'br');
  url.searchParams.set('language', 'pt-BR');
  url.searchParams.set('key', key);
  const response = await fetch(url, { signal: AbortSignal.timeout(30_000) });
  if (!response.ok) throw new Error(`Google Geocoding HTTP ${response.status} para ${address}`);
  const body = await response.json();
  const first = body?.results?.[0];
  if (!first?.place_id) throw new Error(`sem place_id para ${address}: ${body?.status ?? 'sem status'}`);
  resolved.push({
    address,
    google_place_id: first.place_id,
    blablacar_place_id: encode(first.place_id),
    formatted_address: first.formatted_address ?? null,
  });
}

await fs.mkdir(path.dirname(outputPath), { recursive: true });
await fs.writeFile(outputPath, JSON.stringify({ generated_at: new Date().toISOString(), places: resolved }, null, 2) + '\n');
