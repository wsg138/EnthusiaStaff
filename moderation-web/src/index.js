import { DurableObject } from 'cloudflare:workers';
import { inspectLaunchToken } from './security.js';
import { readBoundedBody } from './request-body.js';
import { proxyModerationRead } from './backend.js';

const SESSION_COOKIE = '__Host-enthusia_mod_preview';
const SESSION_TTL_SECONDS = 15 * 60;
const MAX_REQUEST_BYTES = 65_536;
const STATIC_PATHS = new Set([
  '/assets/app.css',
  '/assets/model.js',
  '/assets/app.js',
  '/assets/workflow.js',
  '/assets/review.js',
  '/assets/real-data.js',
  '/assets/real-policy.js'
]);
const encoder = new TextEncoder();

export class ModerationSessionStore extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS used_launches (
        nonce TEXT PRIMARY KEY,
        expires_at INTEGER NOT NULL
      );
      CREATE TABLE IF NOT EXISTS sessions (
        id TEXT PRIMARY KEY,
        actor_id TEXT NOT NULL,
        guild_id TEXT NOT NULL,
        target_key TEXT NOT NULL,
        csrf_token TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        expires_at INTEGER NOT NULL
      );
      CREATE INDEX IF NOT EXISTS sessions_expiry_idx ON sessions(expires_at);
      CREATE INDEX IF NOT EXISTS launches_expiry_idx ON used_launches(expires_at);
    `);
  }

  async consumeLaunch(claims) {
    const now = Math.floor(Date.now() / 1000);
    this.purge(now);
    if (!claims || now >= claims.expiresAt) return { status: 'expired' };
    const inserted = this.sql.exec(
      'INSERT INTO used_launches (nonce, expires_at) VALUES (?, ?) ON CONFLICT(nonce) DO NOTHING RETURNING nonce',
      claims.nonce,
      claims.expiresAt
    ).toArray();
    if (inserted.length !== 1) return { status: 'replayed' };
    const sessionId = randomToken(32);
    const csrfToken = randomToken(24);
    const expiresAt = now + SESSION_TTL_SECONDS;
    this.sql.exec(
      'INSERT INTO sessions (id, actor_id, guild_id, target_key, csrf_token, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
      sessionId,
      claims.actorId,
      claims.guildId,
      claims.targetKey,
      csrfToken,
      now,
      expiresAt
    );
    return { status: 'accepted', sessionId, csrfToken, expiresAt };
  }

  async getSession(sessionId) {
    const now = Math.floor(Date.now() / 1000);
    this.purge(now);
    if (!validOpaqueToken(sessionId)) return null;
    const rows = this.sql.exec(
      'SELECT actor_id, guild_id, target_key, csrf_token, expires_at FROM sessions WHERE id = ? LIMIT 1',
      sessionId
    ).toArray();
    if (rows.length !== 1) return null;
    const row = rows[0];
    return {
      actorId: row.actor_id,
      guildId: row.guild_id,
      targetKey: row.target_key,
      csrfToken: row.csrf_token,
      expiresAt: Number(row.expires_at)
    };
  }

  async authorizeMutation(sessionId, suppliedCsrf) {
    const session = await this.getSession(sessionId);
    if (!session || !constantTimeEqual(session.csrfToken, suppliedCsrf)) return null;
    return session;
  }

  purge(now) {
    this.sql.exec('DELETE FROM used_launches WHERE expires_at <= ?', now);
    this.sql.exec('DELETE FROM sessions WHERE expires_at <= ?', now);
  }
}

export default {
  async fetch(request, env) {
    try {
      return secure(await route(request, env));
    } catch {
      return secure(textResponse('Preview request failed.', 500));
    }
  }
};

async function route(request, env) {
  const url = new URL(request.url);
  if (url.pathname === '/health') return handleHealth(request);
  if (url.pathname === '/launch') return handleLaunch(request, env, url);
  if (url.pathname === '/api/session') return handleSession(request, env);
  if (url.pathname === '/api/bootstrap') return handleRead(request, env, url, 'bootstrap');
  if (url.pathname === '/api/messages') return handleRead(request, env, url, 'messages');
  if (url.pathname === '/api/simulate') return handleSimulation(request, env);
  if (url.pathname === '/' || url.pathname === '/moderation' || STATIC_PATHS.has(url.pathname)) {
    return serveProtectedAsset(request, env, url.pathname);
  }
  return textResponse('Not found.', 404);
}

function handleHealth(request) {
  if (request.method !== 'GET') return methodNotAllowed();
  return jsonResponse({ status: 'ok', environment: 'staging', mode: 'simulation-only' });
}

async function handleLaunch(request, env, url) {
  if (request.method !== 'GET') return methodNotAllowed();
  const token = url.searchParams.get('t') || '';
  const inspection = await inspectLaunchToken(token, env.LAUNCH_SIGNING_KEY_HEX, env.EXPECTED_GUILD_ID);
  if (!inspection.claims) return unauthorizedLaunch();
  const result = await store(env).consumeLaunch(inspection.claims);
  if (!result || result.status !== 'accepted') return unauthorizedLaunch();
  const headers = new Headers({ Location: '/moderation' });
  headers.append('Set-Cookie', sessionCookie(result.sessionId));
  return new Response(null, { status: 303, headers });
}

async function handleSession(request, env) {
  if (request.method !== 'GET') return methodNotAllowed();
  const session = await currentSession(request, env);
  if (!session) return textResponse('Session expired.', 401);
  return jsonResponse({
    actorId: session.actorId,
    guildId: session.guildId,
    targetKey: session.targetKey,
    csrfToken: session.csrfToken,
    expiresAt: new Date(session.expiresAt * 1000).toISOString(),
    staging: true
  });
}

async function handleRead(request, env, url, endpoint) {
  if (request.method !== 'GET') return methodNotAllowed();
  const session = await currentSession(request, env);
  if (!session) return textResponse('Session expired.', 401);
  try {
    return await proxyModerationRead(env, session, endpoint, url);
  } catch {
    return jsonResponse({code: 'invalid_request', message: 'Read request is invalid.'}, 400);
  }
}

async function handleSimulation(request, env) {
  if (request.method !== 'POST') return methodNotAllowed();
  const session = await authorizedMutationSession(request, env);
  if (!session) return textResponse('Session verification failed.', 403);
  const parsed = await readSimulationPayload(request);
  if (parsed.error) return parsed.error;
  if (!validSimulation(parsed.value, session)) return textResponse('Preview request is invalid.', 400);
  return jsonResponse({ status: 'complete', message: 'Simulation complete', detail: 'No live moderation action was performed.' });
}

async function authorizedMutationSession(request, env) {
  const sessionId = cookieValue(request.headers.get('Cookie'), SESSION_COOKIE);
  const csrf = request.headers.get('X-Preview-Csrf') || '';
  return store(env).authorizeMutation(sessionId, csrf);
}

async function readSimulationPayload(request) {
  const body = await readBoundedBody(request, MAX_REQUEST_BYTES);
  if (!body) return { error: textResponse('Preview request is too large.', 413) };
  return parseSimulationJson(body);
}

function parseSimulationJson(body) {
  try {
    return { value: JSON.parse(new TextDecoder().decode(body)) };
  } catch {
    return { error: textResponse('Preview request is invalid.', 400) };
  }
}

async function serveProtectedAsset(request, env, pathname) {
  if (request.method !== 'GET') return methodNotAllowed();
  if (!(await currentSession(request, env))) return textResponse('Open this panel from Discord.', 401);
  const url = new URL(request.url);
  url.pathname = pathname === '/' || pathname === '/moderation' ? '/index.html' : pathname;
  url.search = '';
  return env.ASSETS.fetch(new Request(url, { method: 'GET', headers: request.headers }));
}

async function currentSession(request, env) {
  const sessionId = cookieValue(request.headers.get('Cookie'), SESSION_COOKIE);
  if (!sessionId) return null;
  return store(env).getSession(sessionId);
}

function store(env) {
  return env.SESSION_STORE.getByName('staging-moderation-sessions-v1');
}

function validSimulation(payload, session) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return false;
  if (payload.target !== session.targetKey) return false;
  if (!boundedString(payload.offense, 64) || !boundedString(payload.action, 32)) return false;
  return idList(payload.evidence) && idList(payload.delete);
}

function idList(value) {
  return Array.isArray(value) && value.length <= 100 && value.every((item) => typeof item === 'string' && /^[0-9]{1,24}$/.test(item));
}

function boundedString(value, max) {
  return typeof value === 'string' && value.length > 0 && value.length <= max;
}

function cookieValue(rawCookie, name) {
  if (!rawCookie) return '';
  for (const part of rawCookie.split(';')) {
    const index = part.indexOf('=');
    if (index <= 0) continue;
    if (part.slice(0, index).trim() === name) return part.slice(index + 1).trim();
  }
  return '';
}

function sessionCookie(value) {
  return `${SESSION_COOKIE}=${value}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=${SESSION_TTL_SECONDS}`;
}

function unauthorizedLaunch() {
  return textResponse('This moderation link is invalid, expired, or already used.', 401);
}

function methodNotAllowed() {
  return textResponse('Method not allowed.', 405);
}

function textResponse(text, status) {
  return new Response(text, { status, headers: { 'Content-Type': 'text/plain; charset=utf-8' } });
}

function jsonResponse(value, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json; charset=utf-8' } });
}

function secure(response) {
  const secured = new Response(response.body, response);
  const headers = secured.headers;
  headers.set('Cache-Control', 'private, no-store');
  headers.set('Pragma', 'no-cache');
  headers.set('Content-Security-Policy', "default-src 'self'; img-src 'self' data: https://cdn.discordapp.com https://media.discordapp.net; style-src 'self'; script-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
  headers.set('Referrer-Policy', 'no-referrer');
  headers.set('X-Content-Type-Options', 'nosniff');
  headers.set('X-Frame-Options', 'DENY'); // nosemgrep: javascript.express.security.x-frame-options-misconfiguration.x-frame-options-misconfiguration
  headers.set('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  headers.set('Cross-Origin-Opener-Policy', 'same-origin');
  headers.set('Cross-Origin-Resource-Policy', 'same-origin');
  headers.set('Strict-Transport-Security', 'max-age=31536000');
  return secured;
}

function randomToken(bytes) {
  const value = new Uint8Array(bytes);
  crypto.getRandomValues(value);
  let binary = '';
  for (const byte of value) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function validOpaqueToken(value) {
  return typeof value === 'string' && /^[A-Za-z0-9_-]{32,64}$/.test(value);
}

function constantTimeEqual(left, right) {
  if (typeof left !== 'string' || typeof right !== 'string') return false;
  const leftBytes = encoder.encode(left);
  const rightBytes = encoder.encode(right);
  if (leftBytes.byteLength !== rightBytes.byteLength) return false;
  return crypto.subtle.timingSafeEqual(leftBytes, rightBytes);
}
