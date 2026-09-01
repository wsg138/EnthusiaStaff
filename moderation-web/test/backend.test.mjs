import test from 'node:test';
import assert from 'node:assert/strict';
import { browserMessageQuery, proxyModerationRead, readRequest } from '../src/backend.js';

function readContext() {
  return {
    env: {READ_API_SIGNING_KEY_HEX: '11'.repeat(32), READ_API_ORIGIN: 'https://attacker.example'},
    session: {
      actorId: '846729778400460871', guildId: '1410303324745371709',
      targetKey: 'discord:1049827163345127424'
    }
  };
}

test('message query allowlists bounded filters', () => {
  const filters = {channel:'1541286004298752091', before:'1541300000000000001', author:'1049827163345127424', date:'2026-08-31', text:'hello', limit:'50'};
  assert.deepEqual(browserMessageQuery(filters), {
    channelId: '1541286004298752091', beforeMessageId: '1541300000000000001',
    authorId: '1049827163345127424', date: '2026-08-31', text: 'hello', limit: 50
  });
});

test('message query rejects retargeting-shaped and unbounded inputs', () => {
  for (const filters of [
    {channel:'0'}, {before:'1', after:'2'}, {author:'abc'}, {date:'August-31'},
    {limit:'51'}, {limit:'01'}, {text:'x'.repeat(201)}, {target:'discord:999'}, {actor:'999'}, {guild:'999'}
  ]) assert.throws(() => browserMessageQuery(filters));
});

test('read request binds actor guild and target only from server session', () => {
  const {session} = readContext();
  assert.deepEqual(readRequest(session, 'messages', {channel:'1541286004298752091'}), {
    actorId: session.actorId, guildId: session.guildId, targetKey: session.targetKey,
    messages: {channelId: '1541286004298752091', limit: 25}
  });
});

test('private read egress is pinned and rejects redirects', async () => {
  const originalFetch = globalThis.fetch;
  let requestedUrl = null;
  let requestedOptions = null;
  globalThis.fetch = async (input, options) => {
    requestedUrl = String(input);
    requestedOptions = options;
    return new Response('{}', {headers: {'Content-Type': 'application/json'}});
  };
  try {
    const {env, session} = readContext();
    await proxyModerationRead(env, session, 'bootstrap');
    assert.equal(requestedUrl, 'https://moderation-read-staging.enthusia.info/v1/moderation/bootstrap');
    assert.equal(requestedOptions.redirect, 'error');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('private backend outage is reported truthfully as unavailable', async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error('network unavailable'); };
  try {
    const {env, session} = readContext();
    const response = await proxyModerationRead(env, session, 'bootstrap');
    assert.equal(response.status, 503);
    assert.deepEqual(await response.json(), {
      code: 'source_unavailable', message: 'Moderation data is temporarily unavailable.'
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});
