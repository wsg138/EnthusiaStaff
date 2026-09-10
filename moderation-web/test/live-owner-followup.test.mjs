import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';

const RECORD = new URL('../../staff-bot/src/main/resources/moderation-preview/live-record-usability.js', import.meta.url);
const BROWSE = new URL('../../staff-bot/src/main/resources/moderation-preview/live-browse-workspace.js', import.meta.url);

test('message context loads an around-message neighborhood from every author', async () => {
  const source = await readFile(BROWSE, 'utf8');
  const start = source.indexOf('async function browseShowMessageContext');
  const end = source.indexOf('function browseContextAlertNode', start);
  const contextCode = source.slice(start, end);

  assert.match(contextCode, /fetchBrowseContextAround\(trigger\.channelId,id\)/);
  assert.match(contextCode, /message\.channelId === trigger\.channelId/);
  assert.doesNotMatch(contextCode, /CONTEXT_WINDOW_MS|120_000|boundedTimeContext/);
  assert.match(source, /up to 50 surrounding messages from this channel/);
  assert.match(source, /including messages from every author/);
});

test('channel browse keeps no player selected until staff chooses an author', async () => {
  const source = await readFile(BROWSE, 'utf8');

  assert.match(source, /No player selected/);
  assert.match(source, /workspaceChannelPicker/);
  assert.match(source, /workspacePlayerPicker/);
  assert.match(source, /data-select-player|selectPlayer/);
  assert.match(source, /fetchBrowseBootstrap\(\{browse:true,channel:currentBrowseChannel\(\)\}\)/);
  assert.match(source, /fetchBrowseBootstrap\(\{target:userId,channel:currentBrowseChannel\(\)\}\)/);
  assert.match(source, /TARGET_ONLY_VIEWS/);
  assert.match(source, /state\.activeTargetKey/);
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
  const start = source.indexOf('const DURATION_VALUE_PATTERN');
  const end = source.indexOf('function actionHasDuration', start);
  assert.ok(start >= 0 && end > start);

  const context = {window:{}};
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
