import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const WORKER = new URL('../src/index.js', import.meta.url);
const BROWSE = new URL('../../staff-bot/src/main/resources/moderation-preview/live-browse-workspace.js', import.meta.url);
const INDEX = new URL('../../staff-bot/src/main/resources/moderation-preview/index.html', import.meta.url);
const BUILD = new URL('../scripts/build.mjs', import.meta.url);

test('bootstrap supports read-only POST retargeting while messages remain POST-only', async () => {
  const source = await readFile(WORKER,'utf8');

  assert.match(source,/endpoint === 'messages'\) return method === 'POST'/);
  assert.match(source,/return method === 'GET' \|\| method === 'POST'/);
  assert.match(source,/endpoint === 'bootstrap' && request\.method === 'GET'/);
  assert.match(source,/validTargetKey\(target\)/);
  assert.match(source,/target\.startsWith\('channel:'\)/);
});

test('channel browse asset is loaded last and included in Cloudflare packaging', async () => {
  const [html,build,worker] = await Promise.all([
    readFile(INDEX,'utf8'),readFile(BUILD,'utf8'),readFile(WORKER,'utf8')
  ]);

  assert.match(html,/live-record-usability\.js[^]*live-browse-workspace\.js/);
  assert.match(build,/'live-browse-workspace\.js'/);
  assert.match(worker,/\/assets\/live-browse-workspace\.js/);
});

test('channel browse UI exposes channel and player selectors plus message-author selection', async () => {
  const source = await readFile(BROWSE,'utf8');

  assert.match(source,/workspaceChannelPicker/);
  assert.match(source,/workspacePlayerPicker/);
  assert.match(source,/No player selected/);
  assert.match(source,/View player/);
  assert.match(source,/selectBrowsePlayer/);
  assert.match(source,/clearBrowsePlayer/);
});
