import test from 'node:test';
import assert from 'node:assert/strict';
import { browserMessageQuery, proxyModerationRead, readRequest } from '../src/backend.js';

test('message query allowlists bounded filters', () => {
  const filters = {channel:'1541286004298752091', before:'1541300000000000001', author:'1049827163345127424', date:'2026-08-31', text:'hello', limit:'50'};
  assert.deepEqual(browserMessageQuery(filters), {
    channelId: '1541286004298752091',
    beforeMessageId: '1541300000000000001',
    authorId: '1049827163345127424',
    date: '2026-08-31',
    text: 'hello',
    limit: 50
  });
});

test('message query rejects retargeting-shaped and unbounded inputs', () => {
  for (const filters of [
    {channel:'0'}, {before:'1', after:'2'}, {author:'abc'}, {date:'August-31'},
    {limit:'51'}, {text:'x'.repeat(201)}, {target:'discord:999'}, {actor:'999'}, {guild:'999'}
  ]) {
    assert.throws(() => browserMessageQuery(filters));
  }
});

test('read request binds actor guild and target only from server session', () => {
  const session = {
    actorId: '846729778400460871',
    guildId: '1410303324745371709',
    targetKey: 'discord:1049827163345127424'
  };
  const browser = {channel:'1541286004298752091'};

  assert.deepEqual(readRequest(session, 'messages', browser), {
    actorId: session.actorId,
    guildId: session.guildId,
    targetKey: session.targetKey,
    messages: {
      channelId: '1541286004298752091',
      limit: 25
    }
  });
});

test('private read egress is pinned to the fixed staging backend hostname', async () => {
  const originalFetch = globalThis.fetch;
  let requestedUrl = null;
  globalThis.fetch = async (input) => {
    requestedUrl = String(input);
    return new Response('{}', {headers: {'Content-Type': 'application/json'}});
  };
  try {
    const env = {
      READ_API_SIGNING_KEY_HEX: '11'.repeat(32),
      READ_API_ORIGIN: 'https://attacker.example'
    };
    const session = {
      actorId: '846729778400460871',
      guildId: '1410303324745371709',
      targetKey: 'discord:1049827163345127424'
    };
    await proxyModerationRead(env, session, 'bootstrap');
    assert.equal(requestedUrl, 'https://moderation-read-staging.enthusia.info/v1/moderation/bootstrap');
  } finally {
    globalThis.fetch = originalFetch;
  }
});
