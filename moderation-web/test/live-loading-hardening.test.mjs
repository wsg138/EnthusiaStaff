import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const LIVE_LOADING = new URL('../../staff-bot/src/main/resources/moderation-preview/live-loading.js', import.meta.url);
const LIVE_CONTEXT_PAGE_POLICY = new URL('../../staff-bot/src/main/resources/moderation-preview/live-context-page-policy.js', import.meta.url);
const LIVE_CONTEXT_PAGINATION = new URL('../../staff-bot/src/main/resources/moderation-preview/live-context-pagination.js', import.meta.url);
const MODEL = new URL('../../staff-bot/src/main/resources/moderation-preview/model.js', import.meta.url);

test('live loading state stays neutral and pagination does not duplicate context UI orchestration', async () => {
  const [model, loading, policy, pagination] = await Promise.all([
    readFile(MODEL, 'utf8'),
    readFile(LIVE_LOADING, 'utf8'),
    readFile(LIVE_CONTEXT_PAGE_POLICY, 'utf8'),
    readFile(LIVE_CONTEXT_PAGINATION, 'utf8')
  ]);

  assert.doesNotMatch(model, /RiverAsh|RiverAshMC|sample-river-ash/i);
  assert.match(model, /const LOADING_TEXT = 'Loading…';/);
  assert.match(model, /discordId:LOADING_TEXT/);
  assert.match(model, /const baseMessages = \[\];/);
  assert.match(loading, /discordId:LOADING_TEXT/);
  assert.doesNotMatch(loading, /MAX_CONTEXT_PAGES_PER_DIRECTION/);
  assert.match(policy, /MAX_CONTEXT_PAGES_PER_DIRECTION = 4/);
  assert.match(policy, /CONTEXT_WINDOW_MS = 120_000/);
  assert.match(pagination, /window\.fetchContextPage/);
  assert.doesNotMatch(policy, /function showTwoMinuteContext/);
  assert.doesNotMatch(pagination, /function showTwoMinuteContext/);
  assert.doesNotMatch(pagination, /function exitLiveContext/);
});
