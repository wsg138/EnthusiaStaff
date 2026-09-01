'use strict';

const textEncoder = new TextEncoder();
const MAX_FILTER_TEXT = 200;
const MAX_LIMIT = 50;
const READ_API_ORIGIN = 'https://moderation-read-staging.enthusia.info';

export async function proxyModerationRead(env, session, endpoint, browserInput = {}) {
  const keyHex = readSigningKey(env);
  if (!keyHex) return unavailable();
  const path = endpointPath(endpoint);
  const body = JSON.stringify(readRequest(session, endpoint, browserInput));
  const timestamp = String(Math.floor(Date.now() / 1000));
  const nonce = randomToken(24);
  const signature = await signRequest(keyHex, 'POST', path, body, timestamp, nonce);
  const response = await fetch(new URL(path, READ_API_ORIGIN), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      Accept: 'application/json',
      'X-Enthusia-Read-Timestamp': timestamp,
      'X-Enthusia-Read-Nonce': nonce,
      'X-Enthusia-Read-Signature': signature
    },
    body
  });
  return sanitizedBackendResponse(response);
}

const MESSAGE_FILTER_KEYS = new Set(['channel', 'before', 'after', 'author', 'text', 'date', 'limit']);

export function browserMessageQuery(input) {
    if (!input || typeof input !== 'object' || Array.isArray(input)) throw new Error('invalid message filters');
    for (const key of Object.keys(input)) {
        if (!MESSAGE_FILTER_KEYS.has(key)) throw new Error('invalid message filter');
    }
    const query = {};
    const channelId = snowflakeFilter(input.channel, 'channel');
    const beforeMessageId = snowflakeFilter(input.before, 'before');
    const afterMessageId = snowflakeFilter(input.after, 'after');
    const authorId = snowflakeFilter(input.author, 'author');
    if (channelId !== null) query.channelId = channelId;
    if (beforeMessageId !== null) query.beforeMessageId = beforeMessageId;
    if (afterMessageId !== null) query.afterMessageId = afterMessageId;
    if (authorId !== null) query.authorId = authorId;
    if (input.text !== undefined) {
        if (typeof input.text !== 'string' || input.text.length > MAX_FILTER_TEXT) throw new Error('invalid message text filter');
        query.text = input.text;
    }
    if (input.date !== undefined) {
        if (typeof input.date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(input.date)) throw new Error('invalid date filter');
        query.date = input.date;
    }
    query.limit = input.limit === undefined ? 25 : boundedLimit(String(input.limit));
    if (query.beforeMessageId && query.afterMessageId) throw new Error('conflicting cursors');
    return query;
}

export function readRequest(session, endpoint, browserInput = {}) {
    const request = {actorId: session.actorId, guildId: session.guildId, targetKey: session.targetKey, messages: null};
    if (endpoint === 'messages') request.messages = browserMessageQuery(browserInput);
    return request;
}

function readSigningKey(env) {
  const keyHex = typeof env.READ_API_SIGNING_KEY_HEX === 'string' ? env.READ_API_SIGNING_KEY_HEX : '';
  return /^[0-9a-fA-F]{64}$/.test(keyHex) ? keyHex : null;
}

function snowflakeFilter(value, parameter) {
    if (value === undefined || value === null) return null;
    if (typeof value !== 'string' || !/^[1-9][0-9]{0,19}$/.test(value)) throw new Error(`invalid ${parameter} filter`);
    return value;
}

function boundedLimit(raw) {
  if (!/^[1-9][0-9]?$/.test(raw)) throw new Error('invalid limit');
  const value = Number(raw);
  if (!Number.isInteger(value) || value > MAX_LIMIT) throw new Error('invalid limit');
  return value;
}

function endpointPath(endpoint) {
  if (endpoint === 'bootstrap') return '/v1/moderation/bootstrap';
  if (endpoint === 'messages') return '/v1/moderation/messages';
  throw new Error('invalid read endpoint');
}

async function signRequest(keyHex, method, path, body, timestamp, nonce) {
  const digest = await crypto.subtle.digest('SHA-256', textEncoder.encode(body));
  const canonical = `v1\n${method}\n${path}\n${timestamp}\n${nonce}\n${hex(new Uint8Array(digest))}`;
  const key = await crypto.subtle.importKey(
    'raw', bytesFromHex(keyHex), {name: 'HMAC', hash: 'SHA-256'}, false, ['sign']);
  const signature = await crypto.subtle.sign('HMAC', key, textEncoder.encode(canonical));
  return base64Url(new Uint8Array(signature));
}

async function sanitizedBackendResponse(response) {
  const contentType = response.headers.get('Content-Type') || '';
  if (!contentType.toLowerCase().startsWith('application/json')) return unavailable();
  const body = await response.arrayBuffer();
  if (body.byteLength > 1_048_576) return unavailable();
  return new Response(body, {
    status: response.status,
    headers: {'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'private, no-store'}
  });
}

function unavailable() {
  return new Response(JSON.stringify({code: 'source_unavailable', message: 'Moderation data is temporarily unavailable.'}), {
    status: 503,
    headers: {'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'private, no-store'}
  });
}

function randomToken(bytes) {
  const value = new Uint8Array(bytes);
  crypto.getRandomValues(value);
  return base64Url(value);
}

function bytesFromHex(value) {
  const bytes = new Uint8Array(value.length / 2);
  for (let index = 0; index < bytes.length; index += 1) {
    bytes[index] = Number.parseInt(value.slice(index * 2, index * 2 + 2), 16);
  }
  return bytes;
}

function hex(bytes) {
  return [...bytes].map((value) => value.toString(16).padStart(2, '0')).join('');
}

function base64Url(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}
