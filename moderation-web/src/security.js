'use strict';

const TOKEN_VERSION = 'v1';
const TOKEN_ENVIRONMENT = 'staging';
const MAX_TOKEN_LENGTH = 2048;
const MAX_TTL_SECONDS = 180;
const CLOCK_SKEW_SECONDS = 30;
const EXPECTED_TOKEN_PARTS = 2;
const EXPECTED_BODY_FIELDS = 8;
const EXPECTED_SIGNATURE_BYTES = 32;
const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder('utf-8', { fatal: true });

export async function inspectLaunchToken(token, keyHex, expectedGuildId, nowSeconds = Math.floor(Date.now() / 1000)) {
  const parsed = parseLaunchToken(token);
  if (!parsed) return { claims: null, reason: 'malformed' };
  if (!claimsAllowed(parsed.claims, expectedGuildId, nowSeconds)) {
    return { claims: null, reason: 'claims' };
  }
  const keyBytes = hexToBytes(keyHex);
  if (!keyBytes) return { claims: null, reason: 'key' };
  const key = await crypto.subtle.importKey('raw', keyBytes, { name: 'HMAC', hash: 'SHA-256' }, false, ['verify']);
  const valid = await crypto.subtle.verify('HMAC', key, parsed.signature, textEncoder.encode(parsed.encodedBody));
  return valid ? { claims: parsed.claims, reason: null } : { claims: null, reason: 'signature' };
}

export async function verifyLaunchToken(token, keyHex, expectedGuildId, nowSeconds = Math.floor(Date.now() / 1000)) {
  return (await inspectLaunchToken(token, keyHex, expectedGuildId, nowSeconds)).claims;
}

export function parseLaunchToken(token) {
  const parts = tokenParts(token);
  if (!parts) return null;
  try {
    return decodeLaunchToken(parts);
  } catch {
    return null;
  }
}

function tokenParts(token) {
  if (typeof token !== 'string' || token.length === 0 || token.length > MAX_TOKEN_LENGTH) return null;
  const pieces = token.split('.');
  if (pieces.length !== EXPECTED_TOKEN_PARTS || !pieces[0] || !pieces[1]) return null;
  return { encodedBody: pieces[0], encodedSignature: pieces[1] };
}

function decodeLaunchToken(parts) {
  const body = textDecoder.decode(decodeBase64Url(parts.encodedBody));
  const signature = decodeBase64Url(parts.encodedSignature);
  if (signature.byteLength !== EXPECTED_SIGNATURE_BYTES) return null;
  const claims = parseClaims(body);
  if (!claims) return null;
  return { encodedBody: parts.encodedBody, signature, claims };
}

function parseClaims(body) {
  const fields = body.split('|');
  if (fields.length !== EXPECTED_BODY_FIELDS) return null;
  const [version, environment, nonce, actorId, guildId, targetKey, issuedRaw, expiresRaw] = fields;
  if (!validClaimText(version, environment, nonce, actorId, guildId, targetKey)) return null;
  const times = parseClaimTimes(issuedRaw, expiresRaw);
  if (!times) return null;
  return { nonce, actorId, guildId, targetKey, issuedAt: times.issuedAt, expiresAt: times.expiresAt };
}

function validClaimText(version, environment, nonce, actorId, guildId, targetKey) {
  return [
    version === TOKEN_VERSION,
    environment === TOKEN_ENVIRONMENT,
    /^[A-Za-z0-9_-]{32,64}$/.test(nonce),
    /^[1-9][0-9]{0,19}$/.test(actorId),
    /^[1-9][0-9]{0,19}$/.test(guildId),
    validTargetKey(targetKey)
  ].every(Boolean);
}

function validTargetKey(targetKey) {
  if (typeof targetKey !== 'string' || !/^[A-Za-z0-9:_-]{1,96}$/.test(targetKey)) return false;
  if (/^discord:[1-9][0-9]{0,19}$/.test(targetKey)) return true;
  return /^message:[1-9][0-9]{0,19}:[1-9][0-9]{0,19}:[1-9][0-9]{0,19}$/.test(targetKey);
}

function parseClaimTimes(issuedRaw, expiresRaw) {
  const issuedAt = strictEpoch(issuedRaw);
  if (issuedAt === null) return null;
  const expiresAt = strictEpoch(expiresRaw);
  if (expiresAt === null) return null;
  return { issuedAt, expiresAt };
}

function claimsAllowed(claims, expectedGuildId, nowSeconds) {
  if (claims.guildId !== String(expectedGuildId)) return false;
  if (claims.expiresAt <= claims.issuedAt || claims.expiresAt - claims.issuedAt > MAX_TTL_SECONDS) return false;
  if (claims.issuedAt > nowSeconds + CLOCK_SKEW_SECONDS) return false;
  return nowSeconds < claims.expiresAt;
}

function strictEpoch(value) {
  if (!/^[0-9]{1,12}$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function hexToBytes(value) {
  if (typeof value !== 'string' || !/^[0-9a-fA-F]{64}$/.test(value)) return null;
  const bytes = new Uint8Array(EXPECTED_SIGNATURE_BYTES);
  for (let index = 0; index < bytes.length; index += 1) bytes[index] = Number.parseInt(value.slice(index * 2, index * 2 + 2), 16);
  return bytes;
}

function decodeBase64Url(value) {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error('invalid base64url');
  const padded = value.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - value.length % 4) % 4);
  const decoded = atob(padded);
  const bytes = new Uint8Array(decoded.length);
  for (let index = 0; index < decoded.length; index += 1) bytes[index] = decoded.charCodeAt(index);
  return bytes;
}
