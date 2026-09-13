import assert from 'node:assert/strict';
import test from 'node:test';
import { fifty, loadPagination, message } from './live-context-test-support.mjs';

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
