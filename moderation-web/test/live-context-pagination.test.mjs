import assert from 'node:assert/strict';
import test from 'node:test';
import { fifty, loadPagination, message } from './live-context-test-support.mjs';

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
