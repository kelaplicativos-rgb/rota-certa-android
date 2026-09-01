import fs from 'node:fs/promises';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const root = 'collector/batch-sep01-10';
const resultsDir = 'collector/results';
const requestPath = 'collector/requests/current.json';
const identityTargets = [
  { name: 'Ezequiel S', uuid: '7371f028-9c55-4903-8444-308015823efd' },
  { name: 'Barbosa', uuid: '175a7068-50d8-40c3-a27a-214b9c6e0461' },
];

async function exists(file) {
  try { await fs.access(file); return true; } catch { return false; }
}
async function copyIfExists(src, dst) {
  if (await exists(src)) await fs.copyFile(src, dst);
}
async function cleanResultFiles() {
  for (const name of await fs.readdir(resultsDir).catch(() => [])) {
    if (name.startsWith('latest.') || (name.startsWith('detail-') && name.endsWith('.html'))) {
      await fs.rm(path.join(resultsDir, name), { force: true });
    }
  }
}

await fs.mkdir(root, { recursive: true });
await fs.mkdir(resultsDir, { recursive: true });

for (const day of ['01','02','03','04','05','06','07','08','09','10']) {
  const date = `2026-09-${day}`;
  for (const direction of ['outbound', 'return']) {
    const outbound = direction === 'outbound';
    const from = outbound ? 'Santo André, SP, Brasil' : 'São Thomé das Letras, MG, Brasil';
    const to = outbound ? 'São Thomé das Letras, MG, Brasil' : 'Santo André, SP, Brasil';
    const outdir = path.join(root, date, direction);
    await fs.mkdir(outdir, { recursive: true });

    const request = {
      request_id: `batch-${date}-${direction}`,
      from, to, date, seats: 1, identity_targets: identityTargets,
    };
    await fs.writeFile(requestPath, JSON.stringify(request, null, 2) + '\n');
    await cleanResultFiles();

    const run = spawnSync('xvfb-run', [
      '-a', 'node', 'tools/blablacar-public-search/collect-dom.mjs',
      requestPath,
      path.join(resultsDir, 'latest.json'),
      path.join(resultsDir, 'latest.md'),
      path.join(resultsDir, 'latest.png'),
    ], { stdio: 'inherit', env: process.env });

    await fs.copyFile(requestPath, path.join(outdir, 'request.json'));
    await copyIfExists(path.join(resultsDir, 'latest.json'), path.join(outdir, 'result.json'));
    await copyIfExists(path.join(resultsDir, 'latest.md'), path.join(outdir, 'result.md'));
    await copyIfExists(path.join(resultsDir, 'latest.html'), path.join(outdir, 'search.html'));
    await copyIfExists(path.join(resultsDir, 'latest.png'), path.join(outdir, 'search.png'));
    for (const name of await fs.readdir(resultsDir).catch(() => [])) {
      if (name.startsWith('detail-') && name.endsWith('.html')) {
        await fs.copyFile(path.join(resultsDir, name), path.join(outdir, name));
      }
    }
    await fs.writeFile(path.join(outdir, 'exit_code.txt'), String(run.status ?? 255) + '\n');
  }
}

const rows = [];
for (const date of (await fs.readdir(root)).sort()) {
  const dateDir = path.join(root, date);
  const stat = await fs.stat(dateDir);
  if (!stat.isDirectory()) continue;
  for (const direction of ['outbound', 'return']) {
    const dir = path.join(dateDir, direction);
    if (!(await exists(dir))) continue;
    let result = null;
    const resultPath = path.join(dir, 'result.json');
    if (await exists(resultPath)) {
      try { result = JSON.parse(await fs.readFile(resultPath, 'utf8')); } catch {}
    }
    rows.push({
      date,
      direction,
      status: result?.status ?? 'missing',
      http_status: result?.validation?.http_status ?? null,
      exact_date_match: result?.validation?.exact_date_match ?? false,
      exact_origin_match: result?.validation?.exact_origin_match ?? false,
      exact_destination_match: result?.validation?.exact_destination_match ?? false,
      ezequiel_s_visible: result?.ezequiel_s_visible ?? null,
      barbosa_visible: result?.barbosa_visible ?? null,
      trips: (result?.trips ?? []).map((t) => ({
        driver_name: t.driver_name,
        departure_time: t.departure_time,
        arrival_time: t.arrival_time,
        actual_departure: t.actual_departure,
        actual_arrival: t.actual_arrival,
        trip_href: t.trip_href,
        identity_check: t.identity_check ?? null,
      })),
    });
  }
}
await fs.writeFile(path.join(root, 'manifest.json'), JSON.stringify({ generated_at: new Date().toISOString(), rows }, null, 2) + '\n');
