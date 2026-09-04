'use strict';

const DIRECT_READ_ORIGIN = 'https://moderation-read-staging.enthusia.info';
const DIRECT_READ_PATHS = new Set(['/v1/moderation/bootstrap', '/v1/moderation/messages']);
const DIRECT_PROOF_PATHS = new Set(['/api/bootstrap', '/api/messages']);
const DIRECT_READ_BODY_LIMIT = 65_536;
const DIRECT_READ_TOKEN = /^[A-Za-z0-9_-]+$/;
const DIRECT_READ_TIMESTAMP = /^[0-9]{1,12}$/;

/**
 * Requests a session-bound one-use proof from the protected Worker and sends the
 * exact signed request directly through the staging Cloudflare Tunnel.
 */
async function requestDirectModerationRead(proofPath, requestInit = {}) {
  if (!DIRECT_PROOF_PATHS.has(proofPath)) throw new Error('Moderation read proof is unavailable.');
  const proofResponse = await fetch(proofPath, {...requestInit, cache:'no-store'});
  const proof = await readJsonResponse(proofResponse);
  if (!proofResponse.ok) throw new Error(proof.message || 'Moderation data unavailable');
  return executeDirectRead(proof);
}

async function executeDirectRead(proof) {
  if (!validDirectReadProof(proof)) throw new Error('Moderation read proof is unavailable.');
  return fetch(`${DIRECT_READ_ORIGIN}${proof.path}`, {
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
  });
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
  return DIRECT_READ_TOKEN.test(value);
}
