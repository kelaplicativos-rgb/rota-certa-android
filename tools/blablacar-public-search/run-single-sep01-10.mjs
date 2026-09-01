import fs from 'node:fs/promises';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const [date, direction] = process.argv.slice(2);
if (!/^2026-09-(0[1-9]|10)$/.test(date ?? '') || !['outbound','return'].includes(direction ?? '')) {
  console.error('usage: node run-single-sep01-10.mjs <2026-09-DD> <outbound|return>');
  process.exit(64);
}
const outbound = direction === 'outbound';
const from = outbound ? 'Santo André, SP, Brasil' : 'São Thomé das Letras, MG, Brasil';
const to = outbound ? 'São Thomé das Letras, MG, Brasil' : 'Santo André, SP, Brasil';
const outdir = path.join('collector', 'matrix-sep01-10', date, direction);
const resultsDir = path.join('collector', 'results');
const requestPath = path.join('collector', 'requests', 'current.json');
await fs.mkdir(outdir, { recursive: true });
await fs.mkdir(resultsDir, { recursive: true });

for (const name of await fs.readdir(resultsDir).catch(() => [])) {
  if (name.startsWith('latest.') || (name.startsWith('detail-') && name.endsWith('.html'))) {
    await fs.rm(path.join(resultsDir, name), { force: true });
  }
}

const request = {
  request_id: `matrix-${date}-${direction}`,
  from,
  to,
  date,
  seats: 1,
  identity_targets: [
    { name: 'Ezequiel S', uuid: '7371f028-9c55-4903-8444-308015823efd' },
    { name: 'Barbosa', uuid: '175a7068-50d8-40c3-a27a-214b9c6e0461' }
  ]
};
await fs.writeFile(requestPath, JSON.stringify(request, null, 2) + '\n');

const run = spawnSync('xvfb-run', [
  '-a', 'node', 'tools/blablacar-public-search/collect-dom.mjs',
  requestPath,
  path.join(resultsDir, 'latest.json'),
  path.join(resultsDir, 'latest.md'),
  path.join(resultsDir, 'latest.png')
], { stdio: 'inherit', env: process.env });

async function copyIfExists(src, dst) {
  try { await fs.copyFile(src, dst); } catch {}
}
await fs.copyFile(requestPath, path.join(outdir, 'request.json'));
await copyIfExists(path.join(resultsDir, 'latest.json'), path.join(outdir, 'result.json'));
await copyIfExists(path.join(resultsDir, 'latest.md'), path.join(outdir, 'result.md'));
await copyIfExists(path.join(resultsDir, 'latest.html'), path.join(outdir, 'search.html'));
await copyIfExists(path.join(resultsDir, 'latest.png'), path.join(outdir, 'search.png'));
for (const name of await fs.readdir(resultsDir).catch(() => [])) {
  if (name.startsWith('detail-') && name.endsWith('.html')) {
    await copyIfExists(path.join(resultsDir, name), path.join(outdir, name));
  }
}
await fs.writeFile(path.join(outdir, 'exit_code.txt'), String(run.status ?? 255) + '\n');
process.exit(run.status ?? 1);
