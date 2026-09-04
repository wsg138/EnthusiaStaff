'use strict';

const textEncoder = new TextEncoder();
const DEFAULT_LIMIT = 25;
const MAX_FILTER_TEXT = 200;
const MAX_LIMIT = 50;
const READ_API_ORIGIN = 'https://moderation-read-staging.enthusia.info';
const MESSAGE_FILTER_KEYS = new Set(['channel', 'before', 'after', 'author', 'text', 'date', 'limit']);
const SIGNED_MESSAGE_FIELDS = Object.freeze(['afterMessageId', 'authorId', 'beforeMessageId', 'channelId', 'date', 'limit', 'text']);
const JSON_ESCAPES = new Map([
  ['"', '\\"'], ['\\', '\\\\'], ['\b', '\\b'], ['\f', '\\f'], ['\n', '\\n'], ['\r', '\\r'], ['\t', '\\t']
]);

export async function proxyModerationRead(env, session, endpoint, browserInput = {}) {
  const keyHex = readSigningKey(env);
  if (!keyHex) return unavailable('missing_worker_read_key');
  const path = endpointPath(endpoint);
  const body = signedRequestBody(readRequest(session, endpoint, browserInput));
  const timestamp = String(Math.floor(Date.now() / 1000));
  const nonce = randomToken(24);
  try {
    const signature = await signRequest(keyHex, 'POST', path, body, timestamp, nonce);
    const response = await fetch(new URL(path, READ_API_ORIGIN), {
      method: 'POST',
      redirect: 'error',
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        Accept: 'application/json',
        'X-Enthusia-Read-Timestamp': timestamp,
        'X-Enthusia-Read-Nonce': nonce,
        'X-Enthusia-Read-Signature': signature
      },
      body
    });
    return await sanitizedBackendResponse(response);
  } catch {
    return unavailable('worker_fetch_exception');
  }
}

export function browserMessageQuery(input) {
  requireFilterObject(input);
  requireFilterKeys(input);
  const query = {};
  addSnowflakeFilter(query, 'channelId', input.channel, 'channel');
  addSnowflakeFilter(query, 'beforeMessageId', input.before, 'before');
  addSnowflakeFilter(query, 'afterMessageId', input.after, 'after');
  addSnowflakeFilter(query, 'authorId', input.author, 'author');
  addTextFilter(query, input.text);
  addDateFilter(query, input.date);
  query.limit = input.limit === undefined ? DEFAULT_LIMIT : boundedLimit(input.limit);
  requireCompatibleCursors(query);
  return query;
}

export function readRequest(session, endpoint, browserInput = {}) {
  const request = {actorId: session.actorId, guildId: session.guildId, targetKey: session.targetKey, messages: null};
  if (endpoint === 'messages') request.messages = browserMessageQuery(browserInput);
  return request;
}

function signedRequestBody(request) {
  return `{"actorId":${jsonString(request.actorId)},"guildId":${jsonString(request.guildId)},"messages":${signedMessages(request.messages)},"targetKey":${jsonString(request.targetKey)}}`;
}

function signedMessages(messages) {
  if (messages === null) return 'null';
  const fields = [];
  for (const key of SIGNED_MESSAGE_FIELDS) {
    if (Object.hasOwn(messages, key)) fields.push(`${jsonString(key)}:${jsonScalar(messages[key])}`);
  }
  return `{${fields.join(',')}}`;
}

function jsonScalar(value) {
  if (typeof value === 'string') return jsonString(value);
  if (Number.isInteger(value)) return String(value);
  throw new Error('invalid signed message value');
}

function jsonString(value) {
  if (typeof value !== 'string') throw new Error('invalid signed string');
  return `"${value.replace(/["\\\u0000-\u001f]/g, escapeJsonCharacter)}"`;
}

function escapeJsonCharacter(character) {
  const escaped = JSON_ESCAPES.get(character);
  if (escaped) return escaped;
  return `\\u${character.charCodeAt(0).toString(16).padStart(4, '0')}`;
}

function requireFilterObject(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) throw new Error('invalid message filters');
}

function requireFilterKeys(input) {
  for (const key of Object.keys(input)) {
    if (!MESSAGE_FILTER_KEYS.has(key)) throw new Error('invalid message filter');
  }
}

function addSnowflakeFilter(query, targetKey, value, label) {
  const parsed = snowflakeFilter(value, label);
  if (parsed !== null) query[targetKey] = parsed;
}

function addTextFilter(query, value) {
  if (value === undefined) return;
  if (typeof value !== 'string' || value.length > MAX_FILTER_TEXT) throw new Error('invalid message text filter');
  query.text = value;
}

function addDateFilter(query, value) {
  if (value === undefined) return;
  if (!validDateFilter(value)) throw new Error('invalid date filter');
  query.date = value;
}

function requireCompatibleCursors(query) {
  if (query.beforeMessageId && query.afterMessageId) throw new Error('conflicting cursors');
}

function readSigningKey(env) {
  const keyHex = typeof env.READ_API_SIGNING_KEY_HEX === 'string' ? env.READ_API_SIGNING_KEY_HEX : '';
  return /^[0-9a-fA-F]{64}$/.test(keyHex) ? keyHex : null;
}

function snowflakeFilter(value, label) {
  if (value === undefined || value === null) return null;
  if (typeof value !== 'string') throw new Error(`invalid ${label} filter`);
  if (!validSnowflakeText(value)) throw new Error(`invalid ${label} filter`);
  return value;
}

function validSnowflakeText(value) {
  if (value.length < 1 || value.length > 20 || value[0] === '0') return false;
  return asciiDigits(value);
}

function validDateFilter(value) {
  if (typeof value !== 'string' || value.length !== 10) return false;
  if (value[4] !== '-' || value[7] !== '-') return false;
  return asciiDigits(value.slice(0, 4) + value.slice(5, 7) + value.slice(8, 10));
}

function asciiDigits(value) {
  for (const character of value) {
    if (character < '0' || character > '9') return false;
  }
  return true;
}

function boundedLimit(raw) {
  const text = limitText(raw);
  if (!validLimitText(text)) throw new Error('invalid limit');
  const value = Number(text);
  if (value > MAX_LIMIT) throw new Error('invalid limit');
  return value;
}

function limitText(raw) {
  return typeof raw === 'number' ? String(raw) : raw;
}

function validLimitText(text) {
  if (typeof text !== 'string') return false;
  if (text.length < 1 || text.length > 2) return false;
  if (text[0] === '0') return false;
  return asciiDigits(text);
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
  if (!contentType.toLowerCase().startsWith('application/json')) {
    return unavailable(`upstream_http_${safeStatus(response.status)}_non_json`);
  }
  const body = await response.arrayBuffer();
  if (body.byteLength > 1_048_576) return unavailable('upstream_body_too_large');
  return new Response(body, {
    status: response.status,
    headers: {'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'private, no-store'}
  });
}

function unavailable(diagnostic) {
  return new Response(JSON.stringify({
    code: 'source_unavailable',
    message: 'Moderation data is temporarily unavailable.',
    diagnostic
  }), {
    status: 503,
    headers: {'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'private, no-store'}
  });
}

function safeStatus(status) {
  return Number.isInteger(status) && status >= 100 && status <= 599 ? String(status) : 'unknown';
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
