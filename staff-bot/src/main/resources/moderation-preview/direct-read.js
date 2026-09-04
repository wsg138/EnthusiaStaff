'use strict';

const DIRECT_READ_ORIGIN = 'https://moderation-read-staging.enthusia.info';
const DIRECT_READ_PATHS = new Set(['/v1/moderation/bootstrap', '/v1/moderation/messages']);
const DIRECT_PROOF_PATHS = new Set(['/api/bootstrap', '/api/messages']);
const DIRECT_READ_BODY_LIMIT = 65_536;
const DIRECT_READ_TIMESTAMP = /^[0-9]{1,12}$/;

/**
 * Requests a session-bound one-use proof from the protected Worker and sends the
 * exact signed request directly through the staging Cloudflare Tunnel.
 * Keep both fetch destinations as literals so neither proof data nor caller input can select a URL.
 */
async function requestDirectModerationRead(proofPath, requestInit = {}) {
  if (!DIRECT_PROOF_PATHS.has(proofPath)) throw new Error('Moderation read proof is unavailable.');
  const proofResponse = proofPath === '/api/bootstrap'
    ? await fetch('/api/bootstrap', {...requestInit, cache:'no-store'})
    : await fetch('/api/messages', {...requestInit, cache:'no-store'});
  const proof = await readJsonResponse(proofResponse);
  if (!proofResponse.ok) throw new Error(proof.message || 'Moderation data unavailable');
  return executeDirectRead(proof);
}

async function executeDirectRead(proof) {
  if (!validDirectReadProof(proof)) throw new Error('Moderation read proof is unavailable.');
  const request = directReadRequest(proof);
  if (proof.path === '/v1/moderation/bootstrap') {
    return fetch('https://moderation-read-staging.enthusia.info/v1/moderation/bootstrap', request);
  }
  if (proof.path === '/v1/moderation/messages') {
    return fetch('https://moderation-read-staging.enthusia.info/v1/moderation/messages', request);
  }
  throw new Error('Moderation read route is unavailable.');
}

function directReadRequest(proof) {
  return {
    method:'POST',
    mode:'cors',
    credentials:'omit',
    referrerPolicy:'no-referrer',
    redirect:'error',
    headers:{
      Accept:'application/json',
      'Content-Type':'application/json; charset=utf-8',
      'X-Enthusia-Read-Timestamp':proof.timestamp,
      'X-Enthusia-Read-Nonce':proof.nonce,
      'X-Enthusia-Read-Signature':proof.signature
    },
    body:proof.body
  };
}

function validDirectReadProof(proof) {
  return directReadProofObject(proof)
    && validDirectReadRoute(proof)
    && validDirectReadBody(proof.body)
    && validDirectReadAuthentication(proof);
}

function directReadProofObject(proof) {
  return Boolean(proof) && typeof proof === 'object' && !Array.isArray(proof);
}

function validDirectReadRoute(proof) {
  return proof.origin === DIRECT_READ_ORIGIN
    && proof.method === 'POST'
    && DIRECT_READ_PATHS.has(proof.path);
}

function validDirectReadBody(body) {
  return typeof body === 'string' && body.length <= DIRECT_READ_BODY_LIMIT;
}

function validDirectReadAuthentication(proof) {
  return validTimestamp(proof.timestamp)
    && validToken(proof.nonce, 32, 64)
    && validToken(proof.signature, 43, 44);
}

function validTimestamp(value) {
  return typeof value === 'string' && DIRECT_READ_TIMESTAMP.test(value);
}

function validToken(value, minimumLength, maximumLength) {
  if (typeof value !== 'string') return false;
  if (value.length < minimumLength || value.length > maximumLength) return false;
  return base64UrlCharactersOnly(value);
}

function base64UrlCharactersOnly(value) {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    const letter = (code >= 65 && code <= 90) || (code >= 97 && code <= 122);
    const digit = code >= 48 && code <= 57;
    if (!letter && !digit && code !== 45 && code !== 95) return false;
  }
  return true;
}
