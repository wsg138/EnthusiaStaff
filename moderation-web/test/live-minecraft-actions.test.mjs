import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';

const source = await readFile(new URL('../../staff-bot/src/main/resources/moderation-preview/live-minecraft-actions.js',import.meta.url),'utf8');
function runtime(request) {
  const nodes = new Map();
  const context = vm.createContext({
    state:{session:{staging:false},deleting:new Set()},
    liveModeration:{bootstrap:{targetKey:'channel:999'}},
    liveActionCapabilities:{minecraftEnabled:true,discordEnabled:false},
    requestModerationAction:request,
    showToast:() => {},
    $:selector => {
      if (!nodes.has(selector)) nodes.set(selector,{addEventListener:() => {}});
      return nodes.get(selector);
    }
  });
  context.window = context;
  vm.runInContext(source,context);
  context.renderMinecraftPunishment = () => {};
  context.fixture = {target:'KnownPlayer',reason:'chat.spam',explanation:'Reviewed evidence',prepared:null,result:null,busy:false,uncertain:false};
  vm.runInContext('minecraftWorkflow = fixture',context);
  return context;
}

test('Minecraft confirmation uses the prepared UUID and cannot change its configured intent', async () => {
  const calls = [];
  const prepared = {targetId:'11111111-2222-4333-8444-555555555555',confirmationId:'22222222-2222-4333-8444-555555555555'};
  const context = runtime(async (operation,input) => {
    calls.push({operation,input});
    return operation === 'prepare' ? prepared : {state:'APPLIED',caseId:'CASE-1'};
  });
  await context.performMinecraftAction('prepare');
  context.fixture.target = 'ChangedPlayer';
  context.fixture.reason = 'Changed reason';
  await context.performMinecraftAction('confirm');
  assert.equal(calls[0].input.minecraftIntent.reasonId,'chat.spam');
  assert.equal(calls[1].input.minecraftTarget,prepared.targetId);
  assert.equal(calls[1].input.confirmationId,prepared.confirmationId);
  assert.equal(calls[1].input.minecraftIntent,undefined);
  assert.equal(context.fixture.result.caseId,'CASE-1');
});

test('uncertain confirmation retains its draft for status recovery and blocks overlapping calls', async () => {
  let release;
  let calls = 0;
  const context = runtime(async operation => {
    calls++;
    if (operation === 'confirm') {
      await new Promise(resolve => { release = resolve; });
      throw new Error('connection lost after commit');
    }
    return {state:'APPLIED',caseId:'CASE-2'};
  });
  context.fixture.prepared = {targetId:'11111111-2222-4333-8444-555555555555',confirmationId:'22222222-2222-4333-8444-555555555555'};
  const confirmation = context.performMinecraftAction('confirm');
  await context.performMinecraftAction('confirm');
  assert.equal(calls,1);
  release();
  await confirmation;
  assert.equal(context.fixture.uncertain,true);
  assert.equal(context.fixture.busy,false);
  await context.performMinecraftAction('status');
  assert.equal(context.fixture.uncertain,false);
  assert.equal(context.fixture.result.caseId,'CASE-2');
});


test('Both scope sends separate Discord and Minecraft intents and immutable confirmation', async () => {
  const calls = [];
  const context = runtime(async (operation,input) => {
    calls.push({operation,input});
    if (operation === 'prepare') {
      return {
        confirmationId:'33333333-2222-4333-8444-555555555555',
        minecraftTargetId:'11111111-2222-4333-8444-555555555555',
        reasonId:'chat.spam',
        reason:'Chat spam',
        minecraftConsequences:[{type:'MUTE',lengthKind:'TEMPORARY',durationSeconds:3600}],
        discordIntent:{type:'MUTE',length:{kind:'TEMPORARY'}},
        expiresAt:'2026-10-06T05:00:00Z'
      };
    }
    return {
      state:'PENDING',
      caseId:'CASE-BOTH-1',
      minecraftState:'PENDING',
      minecraftAttempts:0,
      discordState:'PENDING_APPLY',
      discordExternalApplied:false,
      discordDmOutcome:'NOT_ATTEMPTED'
    };
  });
  context.liveActionCapabilities.bothEnabled = true;
  context.liveActionCapabilities.configuredReasons = [{
    id:'chat.spam', family:'chat', label:'Chat spam',
    ladder:[{ordinal:0,label:'Mute',consequences:[{type:'MUTE',duration:'1 hour'}]}]
  }];
  context.fixture.scope = 'BOTH';
  const preparedPayload = context.minecraftActionPayload(context.fixture,'prepare');
  assert.equal(preparedPayload.scope,'BOTH');
  assert.equal(preparedPayload.minecraftIntent.reasonId,'chat.spam');
  assert.equal(preparedPayload.intent.type,'MUTE');
  assert.equal(preparedPayload.intent.duration,'1h');
  await context.performMinecraftAction('prepare');
  assert.equal(calls.length,1);
  assert.equal(calls[0].input.scope,'BOTH');
  await context.performMinecraftAction('confirm');
  assert.deepEqual(Object.keys(calls[1].input).sort(),['confirmationId','scope','targetKey']);
  assert.equal(calls[1].input.scope,'BOTH');
  assert.equal(context.fixture.result.caseId,'CASE-BOTH-1');
});
