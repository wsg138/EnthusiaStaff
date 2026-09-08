import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const LIVE_LOADING = new URL('../../staff-bot/src/main/resources/moderation-preview/live-loading.js', import.meta.url);
const LIVE_CONTEXT_PAGINATION = new URL('../../staff-bot/src/main/resources/moderation-preview/live-context-pagination.js', import.meta.url);
const MODEL = new URL('../../staff-bot/src/main/resources/moderation-preview/model.js', import.meta.url);

async function loadPagination(singlePageRead, trigger) {
  const source = await readFile(LIVE_CONTEXT_PAGINATION, 'utf8');
  let domReady;
  const context = {
    document:{addEventListener:(type, callback) => { if (type === 'DOMContentLoaded') domReady = callback; }},
    queueMicrotask:(callback) => callback(),
    baseMessages:[trigger],
    console
  };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(source, context, {filename:'live-context-pagination.js'});
  context.fetchContextPage = singlePageRead;
  assert.equal(typeof domReady, 'function');
  domReady();
  return context;
}

function message(id, time, channelId = 'channel-a', author = `author-${id}`) {
  return {id, time, channelId, author};
}

function fifty(prefix, startMillis, stepMillis, channelId = 'channel-a') {
  return Array.from({length:50}, (_, index) => message(
    `${prefix}-${index}`,
    new Date(startMillis + stepMillis * index).toISOString(),
    channelId
  ));
}

test('context reader paginates until the two-minute boundary and keeps the requested channel', async () => {
  const triggerTime = Date.parse('2026-09-08T16:00:00Z');
  const trigger = message('trigger', new Date(triggerTime).toISOString());
  const calls = [];
  const first = fifty('first', triggerTime - 60_000, 500);
  const second = fifty('second', triggerTime - 90_000, -1_000);
  second[10] = message('wrong-channel', new Date(triggerTime - 100_000).toISOString(), 'channel-b');
  const context = await loadPagination(async (channelId, direction, cursor) => {
    calls.push({channelId, direction, cursor});
    return calls.length === 1 ? first : second;
  }, trigger);

  const result = await context.window.fetchContextPage('channel-a', 'before', 'trigger');

  assert.equal(calls.length, 2);
  assert.deepEqual(calls[0], {channelId:'channel-a', direction:'before', cursor:'trigger'});
  assert.equal(calls[1].cursor, 'first-0');
  assert.ok(result.some((entry) => entry.id === 'first-1'));
  assert.ok(result.some((entry) => entry.id === 'second-49'));
  assert.ok(!result.some((entry) => entry.id === 'wrong-channel'));
  assert.ok(new Date(result.at(-1).time).getTime() <= triggerTime - 120_000);
});

test('context reader rejects a non-advancing cursor instead of repeating the same page', async () => {
  const triggerTime = Date.parse('2026-09-08T16:00:00Z');
  const trigger = message('trigger', new Date(triggerTime).toISOString());
  const page = fifty('fixed', triggerTime - 30_000, 100);
  page[0] = message('trigger', new Date(triggerTime - 30_000).toISOString());
  let calls = 0;
  const context = await loadPagination(async () => {
    calls++;
    return page;
  }, trigger);

  await assert.rejects(
    context.window.fetchContextPage('channel-a', 'before', 'trigger'),
    /pagination did not advance/
  );
  assert.equal(calls, 1);
});

test('context reader fails explicitly when the bounded safety cap cannot reach the time boundary', async () => {
  const triggerTime = Date.parse('2026-09-08T16:00:00Z');
  const trigger = message('trigger', new Date(triggerTime).toISOString());
  let calls = 0;
  const context = await loadPagination(async (_channelId, direction) => {
    calls++;
    const sign = direction === 'before' ? -1 : 1;
    return fifty(`${direction}-${calls}`, triggerTime + sign * 30_000, sign * 10);
  }, trigger);

  await assert.rejects(
    context.window.fetchContextPage('channel-a', 'after', 'trigger'),
    /too dense to display safely/
  );
  assert.equal(calls, 4);
});

test('live loading state stays neutral and pagination does not duplicate context UI orchestration', async () => {
  const [model, loading, pagination] = await Promise.all([
    readFile(MODEL, 'utf8'),
    readFile(LIVE_LOADING, 'utf8'),
    readFile(LIVE_CONTEXT_PAGINATION, 'utf8')
  ]);

  assert.doesNotMatch(model, /RiverAsh|RiverAshMC|sample-river-ash/i);
  assert.match(model, /const LOADING_TEXT = 'Loading…';/);
  assert.match(model, /discordId:LOADING_TEXT/);
  assert.match(model, /const baseMessages = \[\];/);
  assert.match(loading, /discordId:LOADING_TEXT/);
  assert.doesNotMatch(loading, /MAX_CONTEXT_PAGES_PER_DIRECTION/);
  assert.match(pagination, /MAX_CONTEXT_PAGES_PER_DIRECTION = 4/);
  assert.match(pagination, /window\.fetchContextPage/);
  assert.doesNotMatch(pagination, /function showTwoMinuteContext/);
  assert.doesNotMatch(pagination, /function exitLiveContext/);
});
