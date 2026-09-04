'use strict';

const DIRECT_READ_ORIGIN = 'https://moderation-read-staging.enthusia.info';
const DIRECT_READ_PATHS = new Set(['/v1/moderation/bootstrap', '/v1/moderation/messages']);
const DIRECT_READ_BODY_LIMIT = 65_536;
const DIRECT_READ_TOKEN = /^[A-Za-z0-9_-]+$/;
const DIRECT_READ_TIMESTAMP = /^[0-9]{1,12}$/;

/**
 * Cloudflare Workers cannot fetch the Tunnel-backed hostname in this staging topology.
 * The Worker therefore mints a short-lived HMAC proof bound to the server-side moderation
 * session, and this browser sends that exact one-use proof directly through the Tunnel.
 */
loadSession = async function loadDirectModerationSession() {
  try {
    const sessionResponse = await fetch('/api/session', {headers:{Accept:'application/json'}});
    if (!sessionResponse.ok) throw new Error('Session unavailable');
    state.session = await sessionResponse.json();

    const proofResponse = await fetch('/api/bootstrap', {
      headers:{Accept:'application/json'},
      cache:'no-store'
    });
    const proof = await readJsonResponse(proofResponse);
    if (!proofResponse.ok) throw new Error(proof.message || 'Moderation data unavailable');

    const bootstrapResponse = await executeDirectRead(proof);
    const payload = await readJsonResponse(bootstrapResponse);
    if (!bootstrapResponse.ok) throw new Error(payload.message || 'Moderation data unavailable');
    applyLiveBootstrap(payload);
    $('#actorMeta').textContent = payload.actor?.displayName || 'Verified staff session';
  } catch (error) {
    clearLiveData();
    $('#actorMeta').textContent = 'Read data unavailable';
    showToast(error.message || 'Moderation data is temporarily unavailable.', true);
    renderAll();
  }
};

loadMessageRequest = async function loadDirectMessageRequest(params, mode) {
  try {
    const proofResponse = await fetch('/api/messages', {
      method:'POST',
      headers:{Accept:'application/json', 'Content-Type':'application/json'},
      body:JSON.stringify(Object.fromEntries(params.entries())),
      cache:'no-store'
    });
    const proof = await readJsonResponse(proofResponse);
    if (!proofResponse.ok) throw new Error(proof.message || 'Discord messages unavailable');

    const response = await executeDirectRead(proof);
    const page = await readJsonResponse(response);
    if (!response.ok) throw new Error(page.message || 'Discord messages unavailable');
    if (mode === 'replace') replaceMessagePage(page);
    else appendMessagePage(page, mode);
    renderAll();
  } catch (error) {
    showToast(error.message || 'Discord messages are temporarily unavailable.', true);
  }
};

channelFilterNode = function readableChannelFilterNode() {
  return element('select', {id:'channelFilter'},
    optionNode('all', 'All readable channels', state.channel === 'all'),
    liveModeration.channels.map(channelFilterOption));
};

async function executeDirectRead(proof) {
  if (!validDirectReadProof(proof)) {
    throw new Error('Moderation read proof is unavailable.');
  }
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
  if (!proof || typeof proof !== 'object' || Array.isArray(proof)) return false;
  if (proof.origin !== DIRECT_READ_ORIGIN || proof.method !== 'POST' || !DIRECT_READ_PATHS.has(proof.path)) return false;
  if (typeof proof.body !== 'string' || proof.body.length > DIRECT_READ_BODY_LIMIT) return false;
  if (typeof proof.timestamp !== 'string' || !DIRECT_READ_TIMESTAMP.test(proof.timestamp)) return false;
  if (typeof proof.nonce !== 'string' || proof.nonce.length < 32 || proof.nonce.length > 64 || !DIRECT_READ_TOKEN.test(proof.nonce)) return false;
  return typeof proof.signature === 'string'
    && (proof.signature.length === 43 || proof.signature.length === 44)
    && DIRECT_READ_TOKEN.test(proof.signature);
}
