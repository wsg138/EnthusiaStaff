import test from 'node:test';
import assert from 'node:assert/strict';
import { parseLaunchToken, verifyLaunchToken } from '../src/security.js';

const KEY_HEX = '42'.repeat(32);
const GUILD = '1410303324745371709';
const TARGET = 'discord:1049827163345127424';

async function token({ actor = '123456789012345678', guild = GUILD, target = TARGET, issued = 1_787_000_000, expires = issued + 120, nonce = 'A'.repeat(32) } = {}) {
  const body = `v1|staging|${nonce}|${actor}|${guild}|${target}|${issued}|${expires}`;
  const encodedBody = Buffer.from(body, 'utf8').toString('base64url');
  const key = await crypto.subtle.importKey('raw', Buffer.from(KEY_HEX, 'hex'), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const signature = await crypto.subtle.sign('HMAC', key, Buffer.from(encodedBody, 'utf8'));
  return `${encodedBody}.${Buffer.from(signature).toString('base64url')}`;
}

test('accepts a valid bounded staging user launch token', async () => {
  const value = await token();
  const claims = await verifyLaunchToken(value, KEY_HEX, GUILD, 1_787_000_050);
  assert.equal(claims.actorId, '123456789012345678');
  assert.equal(claims.guildId, GUILD);
  assert.equal(claims.targetKey, TARGET);
});

test('accepts exact message-context target syntax', async () => {
  const target = 'message:1541286004298752091:1541300000000000001:1049827163345127424';
  const value = await token({target});
  const claims = await verifyLaunchToken(value, KEY_HEX, GUILD, 1_787_000_050);
  assert.equal(claims.targetKey, target);
});

test('rejects tampering, expiration, wrong guild and malformed tokens', async () => {
  const value = await token();
  assert.equal(await verifyLaunchToken(`${value.slice(0, -1)}A`, KEY_HEX, GUILD, 1_787_000_050), null);
  assert.equal(await verifyLaunchToken(value, KEY_HEX, GUILD, 1_787_000_120), null);
  assert.equal(await verifyLaunchToken(value, KEY_HEX, '999', 1_787_000_050), null);
  assert.equal(parseLaunchToken('not-a-token'), null);
});

test('rejects excessive lifetime and unsigned target shapes', async () => {
  const longLived = await token({ expires: 1_787_000_500 });
  assert.equal(await verifyLaunchToken(longLived, KEY_HEX, GUILD, 1_787_000_050), null);
  for (const target of ['sample-river-ash', 'bad|target', 'discord:0', 'message:1:2']) {
    assert.equal(parseLaunchToken(await token({target})), null);
  }
});
