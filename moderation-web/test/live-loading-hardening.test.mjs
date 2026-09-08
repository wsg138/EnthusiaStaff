import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const LIVE_LOADING = new URL('../../staff-bot/src/main/resources/moderation-preview/live-loading.js', import.meta.url);
const MODEL = new URL('../../staff-bot/src/main/resources/moderation-preview/model.js', import.meta.url);

async function loadHardening(fetchContextPage) {
  const source = await readFile(LIVE_LOADING, 'utf8');
  let domReady;
  const state = {
    contextId:null, contextReturn:null, contextTruncated:false, search:'term', author:'author', channel:'channel-a',
    date:'2026-09-08', selectedOnly:true, evidence:new Set(), deleting:new Set()
  };
  const baseMessages = [];
  const liveModeration = {olderCursor:'older', newerCursor:'newer'};
  const context = {
    window:{},
    document:{addEventListener:(type, callback) => { if (type === 'DOMContentLoaded') domReady = callback; }},
    queueMicrotask:(callback) => callback(),
    state,
    baseMessages,
    liveModeration,
    fetchContextPage,
    rememberMessageView:() => ({
      messages:baseMessages.slice(), search:state.search, author:state.author, channel:state.channel,
      date:state.date, selectedOnly:state.selectedOnly,
      olderCursor:liveModeration.olderCursor, newerCursor:liveModeration.newerCursor
    }),
    renderAll:() => {},
    showToast:() => {},
    element:() => ({}),
    buttonNode:() => ({}),
    console
  };
  vm.createContext(context);
  vm.runInContext(source, context, {filename:'live-loading.js'});
  assert.equal(typeof domReady, 'function');
  domReady();
  return context;
}

function message(id, time, channelId = 'channel-a', author = `author-${id}`) {
  return {id, time, channelId, author};
}

function fifty(prefix, startMillis, stepMillis) {
  return Array.from({length:50}, (_, index) => message(
    `${prefix}-${index}`,
    new Date(startMillis + stepMillis * index).toISOString()
  ));
}

test('two-minute context paginates until both timestamp boundaries and restores the prior view', async () => {
  const triggerTime = Date.parse('2026-09-08T16:00:00Z');
  const trigger = message('trigger', new Date(triggerTime).toISOString());
  const calls = {before:0, after:0};
  const beforeFirst = fifty('before-one', triggerTime - 60_000, 500);
  const afterFirst = fifty('after-one', triggerTime + 1_000, 500);
  const beforeSecond = [
    message('before-inside', new Date(triggerTime - 119_000).toISOString()),
    message('before-outside', new Date(triggerTime - 121_000).toISOString()),
    message('wrong-channel', new Date(triggerTime - 30_000).toISOString(), 'channel-b')
  ];
  const afterSecond = [
    message('after-inside', new Date(triggerTime + 119_000).toISOString()),
    message('after-outside', new Date(triggerTime + 121_000).toISOString())
  ];
  const context = await loadHardening(async (_channelId, direction) => {
    calls[direction]++;
    if (direction === 'before') return calls.before === 1 ? beforeFirst : beforeSecond;
    return calls.after === 1 ? afterFirst : afterSecond;
  });
  context.baseMessages.push(trigger, message('original', new Date(triggerTime - 10_000).toISOString()));

  await context.window.showTwoMinuteContext('trigger');

  assert.equal(calls.before, 2);
  assert.equal(calls.after, 2);
  assert.equal(context.state.contextId, 'trigger');
  assert.equal(context.state.contextTruncated, false);
  assert.equal(context.state.channel, 'channel-a');
  assert.ok(context.baseMessages.some((entry) => entry.id === 'trigger'));
  assert.ok(context.baseMessages.some((entry) => entry.id === 'before-inside'));
  assert.ok(context.baseMessages.some((entry) => entry.id === 'after-inside'));
  assert.ok(!context.baseMessages.some((entry) => entry.id === 'before-outside'));
  assert.ok(!context.baseMessages.some((entry) => entry.id === 'after-outside'));
  assert.ok(!context.baseMessages.some((entry) => entry.id === 'wrong-channel'));

  context.window.exitLiveContext();
  assert.deepEqual(Array.from(context.baseMessages, (entry) => entry.id), ['trigger', 'original']);
  assert.equal(context.state.search, 'term');
  assert.equal(context.state.author, 'author');
  assert.equal(context.state.channel, 'channel-a');
  assert.equal(context.state.date, '2026-09-08');
  assert.equal(context.state.selectedOnly, true);
  assert.equal(context.liveModeration.olderCursor, 'older');
  assert.equal(context.liveModeration.newerCursor, 'newer');
});

test('context read cap is fail-soft and explicitly marks the view truncated', async () => {
  const triggerTime = Date.parse('2026-09-08T16:00:00Z');
  const trigger = message('trigger', new Date(triggerTime).toISOString());
  const calls = {before:0, after:0};
  const context = await loadHardening(async (_channelId, direction) => {
    calls[direction]++;
    const sign = direction === 'before' ? -1 : 1;
    return fifty(`${direction}-${calls[direction]}`, triggerTime + sign * 30_000, sign * 10);
  });
  context.baseMessages.push(trigger);

  await context.window.showTwoMinuteContext('trigger');

  assert.equal(calls.before, 4);
  assert.equal(calls.after, 4);
  assert.equal(context.state.contextTruncated, true);
  assert.ok(context.baseMessages.some((entry) => entry.id === 'trigger'));
});

test('live browser seed is neutral and context hardening keeps an explicit safety cap', async () => {
  const [model, loading] = await Promise.all([readFile(MODEL, 'utf8'), readFile(LIVE_LOADING, 'utf8')]);

  assert.doesNotMatch(model, /RiverAsh|RiverAshMC|sample-river-ash/i);
  assert.match(model, /const baseMessages = \[\];/);
  assert.match(loading, /MAX_CONTEXT_PAGES_PER_DIRECTION = 4/);
  assert.match(loading, /contextTruncated/);
});
