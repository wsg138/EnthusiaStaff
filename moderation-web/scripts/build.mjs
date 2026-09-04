import { cp, mkdir, rm } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const project = resolve(here, '..');
const source = resolve(project, '../staff-bot/src/main/resources/moderation-preview');
const output = resolve(project, 'dist');
const assets = resolve(output, 'assets');

await rm(output, { recursive: true, force: true });
await mkdir(assets, { recursive: true });
await cp(resolve(source, 'index.html'), resolve(output, 'index.html'));
for (const name of ['app.css', 'model.js', 'app.js', 'workflow.js', 'review.js', 'real-data.js', 'live-loading.js', 'real-policy.js']) {
  await cp(resolve(source, name), resolve(assets, name));
}
