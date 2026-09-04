import test from 'node:test';
import assert from 'node:assert/strict';
import { browserMessageQuery, prepareModerationRead, readRequest } from '../src/backend.js';

const READ_ORIGIN = 'https://moderation-read-staging.enthusia.info';
const READ_KEY_HEX = '11'.repeat(32);

function readContext() {
  return {
    env: {
      READ_API_SIGNING_KEY_HEX: READ_KEY_HEX,
      READ_API_ORIGIN: 'https://attacker.example'
    },
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
    {limit:'51'}, {limit:'01'}, {limit:1.5}, {text:'x'.repeat(201)}, {target:'discord:999'}, {actor:'999'}, {guild:'999'}
  ]) assert.throws(() => browserMessageQuery(filters));
});

test('read request binds actor guild and target only from server session', () => {
  const {session} = readContext();
  assert.deepEqual(readRequest(session, 'messages', {channel:'1541286004298752091'}), {
    actorId: session.actorId, guildId: session.guildId, targetKey: session.targetKey,
    messages: {channelId: '1541286004298752091', limit: 25}
  });
});

test('signed message request serialization is canonical across browser key order', async () => {
  const {env, session} = readContext();
  const channel = '1541286004298752091';
  const author = '1049827163345127424';
  const first = await prepareModerationRead(env, session, 'messages', {text:'hello', channel, author});
  const second = await prepareModerationRead(env, session, 'messages', {author, channel, text:'hello'});
  const firstEnvelope = await first.json();
  const secondEnvelope = await second.json();

  assert.equal(firstEnvelope.body, secondEnvelope.body);
  assert.deepEqual(JSON.parse(firstEnvelope.body), readRequest(session, 'messages', {channel, author, text:'hello'}));
  assert.notEqual(firstEnvelope.nonce, secondEnvelope.nonce);
});

test('direct read envelope is pinned to the tunnel origin and exact endpoint', async () => {
  const originalFetch = globalThis.fetch;
  let fetched = false;
  globalThis.fetch = async () => { fetched = true; throw new Error('must not fetch'); };
  try {
    const {env, session} = readContext();
    const response = await prepareModerationRead(env, session, 'bootstrap');
    assert.equal(response.status, 200);
    assert.equal(fetched, false);
    const envelope = await response.json();
    assert.equal(envelope.origin, READ_ORIGIN);
    assert.equal(envelope.path, '/v1/moderation/bootstrap');
    assert.equal(envelope.method, 'POST');
    assert.equal(envelope.signature, await signProof(
      READ_KEY_HEX, envelope.path, envelope.body, envelope.timestamp, envelope.nonce));
    assert.deepEqual(JSON.parse(envelope.body), {
      actorId: session.actorId,
      guildId: session.guildId,
      messages: null,
      targetKey: session.targetKey
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('missing signing key fails closed without exposing diagnostics', async () => {
  const {session} = readContext();
  const response = await prepareModerationRead({READ_API_SIGNING_KEY_HEX:'not-a-key'}, session, 'bootstrap');
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), {
    code: 'source_unavailable',
    message: 'Moderation data is temporarily unavailable.'
  });
});

test('unknown read endpoint is rejected before an envelope is minted', async () => {
  const {env, session} = readContext();
  await assert.rejects(() => prepareModerationRead(env, session, 'admin-export'));
});

async function signProof(keyHex, path, body, timestamp, nonce) {
  const encoder = new TextEncoder();
  const digest = await crypto.subtle.digest('SHA-256', encoder.encode(body));
  const digestHex = [...new Uint8Array(digest)].map((value) => value.toString(16).padStart(2, '0')).join('');
  const canonical = `v1\nPOST\n${path}\n${timestamp}\n${nonce}\n${digestHex}`;
  const keyBytes = new Uint8Array(keyHex.match(/../g).map((pair) => Number.parseInt(pair, 16)));
  const key = await crypto.subtle.importKey('raw', keyBytes, {name:'HMAC', hash:'SHA-256'}, false, ['sign']);
  const signature = await crypto.subtle.sign('HMAC', key, encoder.encode(canonical));
  return Buffer.from(signature).toString('base64url');
}
