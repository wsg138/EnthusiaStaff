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
  if (!DIRECT_PROOF_PATHS.has(proofPath)) throw diagnosticReadError('read_failed');
  const proofResponse = proofPath === '/api/bootstrap'
    ? await fetch('/api/bootstrap', {...requestInit, cache:'no-store'})
    : await fetch('/api/messages', {...requestInit, cache:'no-store'});
  const proof = await readJsonResponse(proofResponse);
  if (!proofResponse.ok) throw diagnosticResponseError(proofResponse, proof);
  return executeDirectRead(proof);
}

async function executeDirectRead(proof) {
  if (!validDirectReadProof(proof)) throw diagnosticReadError('read_failed');
  const request = directReadRequest(proof);
  if (proof.path === '/v1/moderation/bootstrap') {
    return fetch('https://moderation-read-staging.enthusia.info/v1/moderation/bootstrap', request);
  }
  if (proof.path === '/v1/moderation/messages') {
    return fetch('https://moderation-read-staging.enthusia.info/v1/moderation/messages', request);
  }
  throw diagnosticReadError('read_failed');
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

/**
 * Later-loaded override for the bootstrap path. It keeps the page fail-closed but
 * preserves a sanitized failure class so an authenticated staff user can distinguish
 * authorization, backend, session, and transport failures without seeing private data.
 */
async function loadSession() {
  try {
    const sessionResponse = await fetch('/api/session', {headers:{Accept:'application/json'}});
    if (!sessionResponse.ok) throw diagnosticReadError('session_unavailable');
    state.session = await sessionResponse.json();
    const bootstrapResponse = await requestDirectModerationRead('/api/bootstrap', {headers:{Accept:'application/json'}});
    const payload = await readJsonResponse(bootstrapResponse);
    if (!bootstrapResponse.ok) throw diagnosticResponseError(bootstrapResponse, payload);
    applyLiveBootstrap(payload);
    $('#actorMeta').textContent = payload.actor?.displayName || 'Verified staff session';
  } catch (error) {
    applyDiagnosticReadFailure(error);
  }
}

function diagnosticResponseError(response, payload) {
  const code = typeof payload?.code === 'string' ? payload.code : '';
  if (response.status === 403 || code === 'forbidden') return diagnosticReadError('forbidden');
  if (response.status === 503 || code === 'source_unavailable') return diagnosticReadError('source_unavailable');
  if (response.status === 401 || code === 'unauthorized') return diagnosticReadError('request_rejected');
  return diagnosticReadError('read_failed');
}

function diagnosticReadError(code) {
  const error = new Error('Moderation read failed.');
  error.readDiagnosticCode = code;
  return error;
}

function applyDiagnosticReadFailure(error) {
  const failure = diagnosticReadFailure(error?.readDiagnosticCode);
  clearLiveData();
  liveModeration.warning = failure.warning;
  Object.assign(identity, {status:failure.status, statusDetail:failure.detail, linkState:failure.badge});
  $('#actorMeta').textContent = failure.actorMeta;
  showToast(failure.toast, true);
  renderAll();
}

function diagnosticReadFailure(code) {
  if (code === 'forbidden') return diagnosticAccessDenied();
  if (code === 'source_unavailable') return diagnosticBackendUnavailable();
  if (code === 'session_unavailable' || code === 'request_rejected') return diagnosticSessionUnavailable();
  return diagnosticTransportUnavailable();
}

function diagnosticAccessDenied() {
  return {
    status:'Access denied', badge:'Staff authority not verified',
    actorMeta:'Access denied · staff authority not verified',
    detail:'Current staff identity is not authorized for this read',
    warning:'Access denied. Verify the Discord-to-Minecraft staff link and current Enthusia staff rank.',
    toast:'Access denied for this staff session.'
  };
}

function diagnosticBackendUnavailable() {
  return {
    status:'Backend unavailable', badge:'Read dependency unavailable',
    actorMeta:'Backend unavailable · read source failed',
    detail:'Authoritative database, Paper authority, or Discord read dependency is unavailable',
    warning:'Backend unavailable. A protected moderation read dependency failed.',
    toast:'Moderation backend is temporarily unavailable.'
  };
}

function diagnosticSessionUnavailable() {
  return {
    status:'Session unavailable', badge:'Reopen from Discord',
    actorMeta:'Session unavailable · reopen from Discord',
    detail:'The signed moderation session is unavailable',
    warning:'Session unavailable. Reopen the moderation preview from Discord.',
    toast:'Session unavailable. Reopen the panel from Discord.'
  };
}

function diagnosticTransportUnavailable() {
  return {
    status:'Transport unavailable', badge:'Protected request failed',
    actorMeta:'Read transport unavailable',
    detail:'The protected browser read did not complete',
    warning:'Read transport unavailable. The protected request did not complete.',
    toast:'Moderation read transport is temporarily unavailable.'
  };
}