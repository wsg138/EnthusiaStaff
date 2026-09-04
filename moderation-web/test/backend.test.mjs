import test from 'node:test';
import assert from 'node:assert/strict';
import { browserMessageQuery, proxyModerationRead, readRequest } from '../src/backend.js';
import { relayModerationRead } from '../src/relay.js';

const RELAY_ORIGIN = 'https://enthusia-moderation-read-relay-staging.test-account.workers.dev';

function readContext() {
  return {
    env: {
      READ_API_SIGNING_KEY_HEX: '11'.repeat(32),
      READ_API_ORIGIN: 'https://attacker.example',
      READ_RELAY_ORIGIN: RELAY_ORIGIN
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
  const originalFetch = globalThis.fetch;
  const bodies = [];
  globalThis.fetch = async (_input, options) => {
    bodies.push(options.body);
    return new Response('{}', {headers: {'Content-Type': 'application/json'}});
  };
  try {
    const {env, session} = readContext();
    const channel = '1541286004298752091';
    const author = '1049827163345127424';
    await proxyModerationRead(env, session, 'messages', {text:'hello', channel, author});
    await proxyModerationRead(env, session, 'messages', {author, channel, text:'hello'});
    assert.equal(bodies.length, 2);
    assert.equal(bodies[0], bodies[1]);
    assert.deepEqual(JSON.parse(bodies[0]), readRequest(session, 'messages', {channel, author, text:'hello'}));
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('private read egress is pinned to the authenticated workers.dev relay', async () => {
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
    assert.equal(requestedUrl, `${RELAY_ORIGIN}/v1/moderation/bootstrap`);
    assert.equal(requestedOptions.redirect, 'error');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('read egress rejects an unpinned relay origin before network access', async () => {
  const originalFetch = globalThis.fetch;
  let called = false;
  globalThis.fetch = async () => { called = true; return new Response('{}'); };
  try {
    const {env, session} = readContext();
    env.READ_RELAY_ORIGIN = 'https://attacker.example';
    const response = await proxyModerationRead(env, session, 'bootstrap');
    assert.equal(response.status, 503);
    assert.equal(called, false);
    assert.deepEqual(await response.json(), {
      code: 'source_unavailable',
      message: 'Moderation data is temporarily unavailable.',
      diagnostic: 'missing_worker_relay_origin'
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('private backend outage is reported truthfully with a sanitized relay diagnostic', async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error('network unavailable SECRET_INTERNAL_DETAIL'); };
  try {
    const {env, session} = readContext();
    const response = await proxyModerationRead(env, session, 'bootstrap');
    assert.equal(response.status, 503);
    assert.deepEqual(await response.json(), {
      code: 'source_unavailable',
      message: 'Moderation data is temporarily unavailable.',
      diagnostic: 'relay_fetch_exception'
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('non-json relay failures expose only the bounded status diagnostic', async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response('<html>challenge SECRET_EDGE_BODY</html>', {
    status: 403,
    headers: {'Content-Type': 'text/html; charset=UTF-8'}
  });
  try {
    const {env, session} = readContext();
    const response = await proxyModerationRead(env, session, 'bootstrap');
    assert.equal(response.status, 503);
    const payload = await response.json();
    assert.deepEqual(payload, {
      code: 'source_unavailable',
      message: 'Moderation data is temporarily unavailable.',
      diagnostic: 'relay_http_403_non_json'
    });
    assert.doesNotMatch(JSON.stringify(payload), /SECRET_EDGE_BODY/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('relay verifies the existing read proof and forwards only to the pinned tunnel hostname', async () => {
  const originalFetch = globalThis.fetch;
  let requestedUrl = null;
  let requestedOptions = null;
  globalThis.fetch = async (input, options) => {
    requestedUrl = String(input);
    requestedOptions = options;
    return new Response('{"code":"forbidden","message":"Access denied."}', {
      status: 403,
      headers: {'Content-Type': 'application/json; charset=utf-8'}
    });
  };
  try {
    const keyHex = '22'.repeat(32);
    const path = '/v1/moderation/bootstrap';
    const body = '{"actorId":"123","guildId":"1","messages":null,"targetKey":"discord:123"}';
    const now = Math.floor(Date.now() / 1000);
    const timestamp = String(now);
    const nonce = 'A'.repeat(32);
    const signature = await signProof(keyHex, path, body, timestamp, nonce);
    const request = new Request(`https://relay.invalid${path}`, {
      method: 'POST',
      headers: readHeaders(timestamp, nonce, signature),
      body
    });
    const response = await relayModerationRead(request, {READ_API_SIGNING_KEY_HEX: keyHex}, now);
    assert.equal(response.status, 403);
    assert.equal(requestedUrl, 'https://moderation-read-staging.enthusia.info/v1/moderation/bootstrap');
    assert.equal(requestedOptions.redirect, 'error');
    assert.equal(requestedOptions.headers['X-Enthusia-Read-Signature'], signature);
    assert.deepEqual(await response.json(), {code: 'forbidden', message: 'Access denied.'});
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('relay rejects an invalid read proof without touching the tunnel', async () => {
  const originalFetch = globalThis.fetch;
  let called = false;
  globalThis.fetch = async () => { called = true; throw new Error('must not fetch'); };
  try {
    const now = Math.floor(Date.now() / 1000);
    const request = new Request('https://relay.invalid/v1/moderation/bootstrap', {
      method: 'POST',
      headers: readHeaders(String(now), 'B'.repeat(32), 'C'.repeat(43)),
      body: '{}'
    });
    const response = await relayModerationRead(request, {READ_API_SIGNING_KEY_HEX: '22'.repeat(32)}, now);
    assert.equal(response.status, 401);
    assert.equal(called, false);
    assert.deepEqual(await response.json(), {code: 'unauthorized', message: 'Request rejected.'});
  } finally {
    globalThis.fetch = originalFetch;
  }
});

function readHeaders(timestamp, nonce, signature) {
  return {
    'Content-Type': 'application/json; charset=utf-8',
    'X-Enthusia-Read-Timestamp': timestamp,
    'X-Enthusia-Read-Nonce': nonce,
    'X-Enthusia-Read-Signature': signature
  };
}

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
