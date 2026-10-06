import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';

const source = await readFile(new URL('../../staff-bot/src/main/resources/moderation-preview/live-actions.js',import.meta.url),'utf8');
function runtime() {
  const context = vm.createContext({
    state:{session:{staging:false},deleting:new Set(),evidence:new Set()},
    liveModeration:{bootstrap:{targetKey:'discord:999'}},
    restrictionTargetSelections:() => [],
    Set,Error,Promise,JSON,
  });
  context.window = context;
  vm.runInContext(source,context);
  vm.runInContext('liveActionCapabilities = {discordEnabled:true}',context);
  return context;
}
const workflow = {scope:'Discord',dm:true,actual:{action:'Mute'},duration:'1 hour',reason:'Verified test incident',offense:{label:'Test reason'}};

test('live action translates exact target, duration and evidence references', () => {
  const context = runtime();
  context.state.evidence.add('123');
  const payload = context.liveActionInput(workflow);
  assert.equal(payload.targetKey,'discord:999');
  assert.equal(payload.intent.type,'MUTE');
  assert.equal(payload.intent.duration,'1h');
  assert.match(payload.intent.explanation,/Discord message reference: 123/);
  assert.equal(payload.intent.restriction,null);
});

test('live action normalizes month dropdown durations to bounded Discord day durations', () => {
  const context = runtime();
  assert.equal(context.actionDuration('1 month'),'30d');
  assert.equal(context.actionDuration('12 months'),'360d');
  assert.equal(context.actionDuration('Permanent'),'permanent');
});

test('live action refuses unsupported scope, deletion, missing DM and multiple restriction targets', () => {
  const context = runtime();
  assert.throws(() => context.liveActionInput({...workflow,scope:'Minecraft'}),/activation/);
  assert.throws(() => context.liveActionInput({...workflow,dm:false}),/notification/);
  context.state.deleting.add('123');
  assert.throws(() => context.liveActionInput(workflow),/deletion/);
  context.state.deleting.clear();
  assert.throws(() => context.liveActionInput({...workflow,actual:{action:'Restrict'}}),/exactly one/);
});

test('disabled backend cannot enter a live action and client approval cannot grant authority', () => {
  const context = runtime();
  vm.runInContext('liveActionCapabilities = {discordEnabled:false}',context);
  assert.throws(() => context.liveActionInput({...workflow,approvalConfirmed:true}),/not enabled/);
  assert.equal(context.workflowApprovalRequired({...workflow,approvalConfirmed:true}),false);
  assert.match(context.approvalReviewText(workflow),/server/);
});

test('live action preparation is bounded and exposes a retry state instead of hanging forever', () => {
  assert.match(source,/LIVE_ACTION_TIMEOUT_MS = 12000/);
  assert.match(source,/AbortController/);
  assert.match(source,/Moderation service timed out\. Retry preparation\./);
  assert.match(source,/Retry preparation/);
  assert.match(source,/workflow\.livePrepareFailed/);
  assert.match(source,/Action could not be prepared/);
  assert.match(source,/liveActionPrepareError/);
  assert.match(source,/No additional staff explanation was provided\./);
  assert.doesNotMatch(source,/Server-prepared live action/);
});
