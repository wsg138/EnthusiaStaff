import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const ROUTER = new URL('../src/index.js', import.meta.url);
const PAGE = new URL('../../staff-bot/src/main/resources/moderation-preview/index.html', import.meta.url);
const ASSETS = [
  '/assets/live-review-hardening.js',
  '/assets/live-shell-usability.js',
  '/assets/live-message-usability.js',
  '/assets/live-record-usability.js'
];

test('protected router serves every live usability asset', async () => {
  const source = await readFile(ROUTER, 'utf8');
  for (const asset of ASSETS) assert.ok(source.includes(`'${asset}'`), `${asset} must be explicitly allowlisted`);
});

test('live usability scripts load after base live enhancements in dependency order', async () => {
  const html = await readFile(PAGE, 'utf8');
  let previous = html.indexOf('/assets/live-enhancements.js');
  assert.ok(previous >= 0);
  for (const asset of ASSETS) {
    const current = html.indexOf(asset);
    assert.ok(current > previous, `${asset} must load after its dependencies`);
    previous = current;
  }
});
