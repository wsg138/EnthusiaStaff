import test from 'node:test';
import assert from 'node:assert/strict';
import {createHmac, createHash} from 'node:crypto';
import {prepareModerationAction} from '../src/backend.js';

const key = '11'.repeat(32);
const env = {RUNTIME_ENVIRONMENT:'production', READ_API_SIGNING_KEY_HEX:key};
const session = {actorId:'123',guildId:'456',targetKey:'channel:789',csrfToken:'private-session-csrf'};
const intent = {type:'WARNING',duration:'instant',reason:'Test warning',explanation:'Authorized test only',restriction:null};

test('Minecraft actions bind configured reason and target without accepting client sanction values', async () => {
  const input = {minecraftTarget:'KnownPlayer', minecraftIntent:{reasonId:'chat.spam', explanation:'Reviewed evidence'}};
  const proof = await (await prepareModerationAction(env,session,'prepare',input)).json();
  const body = JSON.parse(proof.body);
  assert.equal(body.minecraftTarget,'KnownPlayer');
  assert.deepEqual(body.minecraftIntent,input.minecraftIntent);
  assert.equal(body.intent,null);
  for (const field of ['duration','type','rank','approved']) {
    await assert.rejects(prepareModerationAction(env,session,'prepare',{
      ...input, minecraftIntent:{...input.minecraftIntent,[field]:'forged'}
    }));
  }
  await assert.rejects(prepareModerationAction(env,session,'prepare',{...input,intent}));
  await assert.rejects(prepareModerationAction(env,session,'confirm',{
    ...input, confirmationId:'11111111-2222-4333-8444-555555555555'
  }));
  await assert.rejects(prepareModerationAction(env,session,'prepare',{...input,minecraftTarget:'../unknown'}));
});

test('action proof binds actor, guild, session, target and exact bytes', async () => {
  const response = await prepareModerationAction(env,session,'prepare',{targetKey:'discord:999',intent});
  const proof = await response.json();
  const body = JSON.parse(proof.body);
  assert.equal(body.actorId,session.actorId);
  assert.equal(body.guildId,session.guildId);
  assert.equal(body.targetKey,'discord:999');
  assert.equal(body.sessionBinding,createHash('sha256').update(session.csrfToken).digest('hex'));
  assert.equal(proof.origin,'https://moderation-read.enthusia.info');
  const canonical = `v1\nPOST\n${proof.path}\n${proof.timestamp}\n${proof.nonce}\n${createHash('sha256').update(proof.body).digest('hex')}`;
  assert.equal(proof.signature,createHmac('sha256',Buffer.from(key,'hex')).update(canonical).digest('base64url'));
});

test('action signer rejects forged authority and unsupported operations', async () => {
  for (const input of [{actorId:'666'}, {guildId:'666'}, {admin:true}, {sessionBinding:'00'},
    {targetKey:'https://attacker.example'}, {intent:{...intent,approved:true}}, {intent:{...intent,delete:['123']}}]) {
    await assert.rejects(prepareModerationAction(env,session,'prepare',input));
  }
  await assert.rejects(prepareModerationAction(env,session,'delete',{}));
  await assert.rejects(prepareModerationAction({...env,RUNTIME_ENVIRONMENT:'staging'},session,'prepare',{intent}));
});

test('confirmation cannot change prepared punishment and missing keys fail closed', async () => {
  const id = '11111111-2222-4333-8444-555555555555';
  await assert.rejects(prepareModerationAction(env,session,'confirm',{confirmationId:id,intent}));
  await assert.rejects(prepareModerationAction(env,session,'confirm',{confirmationId:'invalid'}));
  const proof = await (await prepareModerationAction(env,session,'confirm',{confirmationId:id})).json();
  assert.equal(JSON.parse(proof.body).intent,null);
  assert.equal(JSON.parse(proof.body).confirmationId,id);
  assert.equal((await prepareModerationAction({RUNTIME_ENVIRONMENT:'production'},session,'capabilities',{})).status,503);
});
