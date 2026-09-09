import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';

const RECORD = new URL('../../staff-bot/src/main/resources/moderation-preview/live-record-usability.js', import.meta.url);

test('message context uses an around-message fast path with bounded fallback', async () => {
  const source = await readFile(RECORD, 'utf8');

  assert.match(source, /around:messageId/);
  assert.match(source, /contextFallbackDirections/);
  assert.match(source, /CONTEXT_WINDOW_MS/);
  assert.match(source, /fetchContextPage\(trigger\.channelId, 'before', id\)/);
  assert.match(source, /fetchContextPage\(trigger\.channelId, 'after', id\)/);
  assert.match(source, /Show context/);
});

test('message paging and initial session loading avoid unnecessary serial and full-shell work', async () => {
  const source = await readFile(RECORD, 'utf8');

  assert.match(source, /const LIVE_MESSAGE_PAGE_LIMIT = '50'/);
  assert.match(source, /Promise\.all\(\[sessionRequest, bootstrapRequest\]\)/);
  assert.match(source, /renderWorkspace\(\);\n    renderCounts\(\);/);
  assert.match(source, /Loading \$\{direction\}…/);
});

test('custom punishment duration accepts arbitrary positive lengths and permanent', async () => {
  const source = await readFile(RECORD, 'utf8');
  const start = source.indexOf('function normalizePunishmentDuration');
  const end = source.indexOf('function actionHasDuration', start);
  assert.ok(start >= 0 && end > start);

  const context = {};
  vm.runInNewContext(`${source.slice(start, end)}; result = [
    normalizePunishmentDuration('60 days'),
    normalizePunishmentDuration('12 hours'),
    normalizePunishmentDuration('12h'),
    normalizePunishmentDuration('1 month'),
    normalizePunishmentDuration('permanent'),
    normalizePunishmentDuration('0 days'),
    normalizePunishmentDuration('tomorrow')
  ];`, context);

  assert.deepEqual(Array.from(context.result), [
    '60 days', '12 hours', '12 hours', '1 month', 'Permanent', null, null
  ]);
  assert.match(source, /60 days, 12 hours, 90 minutes, Permanent/);
  assert.match(source, /workflowDurationReady/);
  assert.match(source, /Enter a duration such as 60 days/);
});
