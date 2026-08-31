import test from 'node:test';
import assert from 'node:assert/strict';
import { browserMessageQuery } from '../src/backend.js';

test('message query allowlists bounded filters', () => {
  const url = new URL('https://staff-staging.enthusia.info/api/messages?channel=1541286004298752091&before=1541300000000000001&author=1049827163345127424&date=2026-08-31&text=hello&limit=50');
  assert.deepEqual(browserMessageQuery(url), {
    channelId: '1541286004298752091',
    beforeMessageId: '1541300000000000001',
    authorId: '1049827163345127424',
    date: '2026-08-31',
    text: 'hello',
    limit: 50
  });
});

test('message query rejects retargeting-shaped and unbounded inputs', () => {
  for (const raw of [
    '?channel=0',
    '?before=1&after=2',
    '?author=abc',
    '?date=August-31',
    '?limit=51',
    `?text=${'x'.repeat(201)}`
  ]) {
    assert.throws(() => browserMessageQuery(new URL(`https://staff-staging.enthusia.info/api/messages${raw}`)));
  }
});
