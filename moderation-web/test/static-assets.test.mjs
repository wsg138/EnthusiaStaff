import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const sourceUrl = new URL('../src/index.js', import.meta.url);
const indexUrl = new URL('../../staff-bot/src/main/resources/moderation-preview/index.html', import.meta.url);
const buildUrl = new URL('../scripts/build.mjs', import.meta.url);

test('protected router serves live moderation enhancement assets', async () => {
  const source = await readFile(sourceUrl, 'utf8');

  assert.match(source, /'\/assets\/live\.css'/);
  assert.match(source, /'\/assets\/live-context-page-policy\.js'/);
  assert.match(source, /'\/assets\/live-context-pagination\.js'/);
  assert.match(source, /'\/assets\/live-enhancements\.js'/);
});

test('cloudflare build copies every local asset referenced by the moderation page', async () => {
  const [index, build] = await Promise.all([
    readFile(indexUrl, 'utf8'),
    readFile(buildUrl, 'utf8')
  ]);
  const references = [...index.matchAll(/(?:src|href)="\/assets\/([^"]+)"/g)]
    .map(match => match[1]);

  assert.ok(references.length > 0);
  for (const name of references) {
    assert.ok(build.includes(`'${name}'`), `build is missing referenced asset ${name}`);
  }
});

test('content security policy permits only required profile and server logo image origins', async () => {
  const source = await readFile(sourceUrl, 'utf8');

  assert.match(source, /https:\/\/cdn\.discordapp\.com/);
  assert.match(source, /https:\/\/media\.discordapp\.net/);
  assert.match(source, /https:\/\/textures\.minecraft\.net/);
  assert.match(source, /https:\/\/enthusia\.info/);
});
