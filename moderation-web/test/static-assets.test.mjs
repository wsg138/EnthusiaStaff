import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const sourceUrl = new URL('../src/index.js', import.meta.url);

test('protected router serves live moderation enhancement assets', async () => {
  const source = await readFile(sourceUrl, 'utf8');

  assert.match(source, /'\/assets\/live\.css'/);
  assert.match(source, /'\/assets\/live-context-page-policy\.js'/);
  assert.match(source, /'\/assets\/live-context-pagination\.js'/);
  assert.match(source, /'\/assets\/live-enhancements\.js'/);
});

test('content security policy permits only required profile and server logo image origins', async () => {
  const source = await readFile(sourceUrl, 'utf8');

  assert.match(source, /https:\/\/cdn\.discordapp\.com/);
  assert.match(source, /https:\/\/media\.discordapp\.net/);
  assert.match(source, /https:\/\/textures\.minecraft\.net/);
  assert.match(source, /https:\/\/enthusia\.info/);
});
