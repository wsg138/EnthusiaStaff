'use strict';

import { readBoundedBody } from './request-body.js';

const READ_API_ORIGIN = 'https://moderation-read-staging.enthusia.info';
const MAX_BODY_BYTES = 65_536;
const MAX_RESPONSE_BYTES = 1_048_576;
const MAX_SKEW_SECONDS = 30;
const PATH = /^\/v1\/moderation\/(bootstrap|messages)$/;
const NONCE = /^[A-Za-z0-9_-]{32,64}$/;
const SIGNATURE = /^[A-Za-z0-9_-]{43,44}$/;
const TIMESTAMP = /^[0-9]{1,12}$/;
const KEY_HEX = /^[0-9a-fA-F]{64}$/;
const textEncoder = new TextEncoder();

export default {
  async fetch(request, env) {
    try {
      return secure(await relayModerationRead(request, env));
    } catch {
      return secure(sourceUnavailable());
    }
  }
};

/**
 * Authenticated staging-only egress hop. The relay is intentionally not a general proxy:
 * it accepts only the two moderation read paths and forwards only the already-signed read proof.
 */
export async function relayModerationRead(request, env, nowSeconds = Math.floor(Date.now() / 1000)) {
  const url = new URL(request.url);
  if (request.method !== 'POST' || !PATH.test(url.pathname) || url.search || url.hash) {
    return textResponse('Not found.', 404);
  }

  const body = await readBoundedBody(request, MAX_BODY_BYTES);
  if (body === null) return jsonResponse({code: 'request_too_large', message: 'Request rejected.'}, 413);

  const keyHex = signingKey(env);
  if (!keyHex) return sourceUnavailable();
  const timestamp = request.headers.get('X-Enthusia-Read-Timestamp') || '';
  const nonce = request.headers.get('X-Enthusia-Read-Nonce') || '';
  const signature = request.headers.get('X-Enthusia-Read-Signature') || '';
  if (!(await validProof(keyHex, url.pathname, body, timestamp, nonce, signature, nowSeconds))) {
    return jsonResponse({code: 'unauthorized', message: 'Request rejected.'}, 401);
  }

  let response;
  try {
    response = await fetch(new URL(url.pathname, READ_API_ORIGIN), {
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
  } catch {
    return sourceUnavailable();
  }
  return sanitizedBackendResponse(response);
}

async function validProof(keyHex, path, body, timestamp, nonce, signature, nowSeconds) {
  if (!TIMESTAMP.test(timestamp) || !NONCE.test(nonce) || !SIGNATURE.test(signature)) return false;
  const epoch = Number(timestamp);
  if (!Number.isSafeInteger(epoch) || epoch <= 0 || !Number.isSafeInteger(nowSeconds)) return false;
  if (Math.abs(nowSeconds - epoch) > MAX_SKEW_SECONDS) return false;

  const supplied = decodeBase64Url(signature);
  if (!supplied || supplied.byteLength !== 32) return false;
  const digest = await crypto.subtle.digest('SHA-256', body);
  const canonical = `v1\nPOST\n${path}\n${timestamp}\n${nonce}\n${hex(new Uint8Array(digest))}`;
  const key = await crypto.subtle.importKey(
    'raw', bytesFromHex(keyHex), {name: 'HMAC', hash: 'SHA-256'}, false, ['verify']);
  return crypto.subtle.verify('HMAC', key, supplied, textEncoder.encode(canonical));
}

function signingKey(env) {
  const value = typeof env.READ_API_SIGNING_KEY_HEX === 'string' ? env.READ_API_SIGNING_KEY_HEX : '';
  return KEY_HEX.test(value) ? value : null;
}

async function sanitizedBackendResponse(response) {
  const contentType = response.headers.get('Content-Type') || '';
  if (!contentType.toLowerCase().startsWith('application/json')) return sourceUnavailable();
  const body = await readBoundedResponse(response, MAX_RESPONSE_BYTES);
  if (body === null) return sourceUnavailable();
  return new Response(body, {
    status: response.status,
    headers: {'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'private, no-store'}
  });
}

async function readBoundedResponse(response, maxBytes) {
  const declared = Number(response.headers.get('Content-Length'));
  if (Number.isFinite(declared) && declared > maxBytes) return null;
  if (!response.body) return new Uint8Array();
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (true) {
      const {done, value} = await reader.read();
      if (done) return combine(chunks, total);
      total += value.byteLength;
      if (total > maxBytes) {
        try { await reader.cancel(); } catch { /* best-effort cancellation */ }
        return null;
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
}

function combine(chunks, total) {
  const body = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return body;
}

function decodeBase64Url(value) {
  try {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4);
    const binary = atob(padded);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
    return bytes;
  } catch {
    return null;
  }
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

function sourceUnavailable() {
  return jsonResponse({code: 'source_unavailable', message: 'Moderation data is temporarily unavailable.'}, 503);
}

function jsonResponse(value, status) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'private, no-store'}
  });
}

function textResponse(text, status) {
  return new Response(text, {status, headers: {'Content-Type': 'text/plain; charset=utf-8'}});
}

function secure(response) {
  const secured = new Response(response.body, response);
  secured.headers.set('Cache-Control', 'private, no-store');
  secured.headers.set('Pragma', 'no-cache');
  secured.headers.set('Referrer-Policy', 'no-referrer');
  secured.headers.set('X-Content-Type-Options', 'nosniff');
  secured.headers.set('X-Frame-Options', 'DENY');
  return secured;
}
