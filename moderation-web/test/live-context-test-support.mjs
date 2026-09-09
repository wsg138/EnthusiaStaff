import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const LIVE_CONTEXT_PAGE_POLICY = new URL('../../staff-bot/src/main/resources/moderation-preview/live-context-page-policy.js', import.meta.url);
const LIVE_CONTEXT_PAGINATION = new URL('../../staff-bot/src/main/resources/moderation-preview/live-context-pagination.js', import.meta.url);

export async function loadPagination(singlePageRead, trigger) {
  const [policySource, paginationSource] = await paginationSources();
  const harness = paginationHarness(trigger);
  runPaginationScripts(harness.context, policySource, paginationSource);
  harness.context.fetchContextPage = singlePageRead;
  assert.equal(typeof harness.lifecycle.domReady, 'function');
  harness.lifecycle.domReady();
  return harness.context;
}

async function paginationSources() {
  return Promise.all([
    readFile(LIVE_CONTEXT_PAGE_POLICY, 'utf8'),
    readFile(LIVE_CONTEXT_PAGINATION, 'utf8')
  ]);
}

function paginationHarness(trigger) {
  const lifecycle = {};
  const context = {
    document:{addEventListener:(type, callback) => {
      if (type === 'DOMContentLoaded') lifecycle.domReady = callback;
    }},
    queueMicrotask:(callback) => callback(),
    baseMessages:[trigger],
    console
  };
  context.window = context;
  vm.createContext(context);
  return {context, lifecycle};
}

function runPaginationScripts(context, policySource, paginationSource) {
  vm.runInContext(policySource, context, {filename:'live-context-page-policy.js'});
  vm.runInContext(paginationSource, context, {filename:'live-context-pagination.js'});
}

export function message(id, time, channelId = 'channel-a', author = `author-${id}`) {
  return {id, time, channelId, author};
}

export function fifty(prefix, startMillis, stepMillis, channelId = 'channel-a') {
  return Array.from({length:50}, (_, index) => message(
    `${prefix}-${index}`,
    new Date(startMillis + stepMillis * index).toISOString(),
    channelId
  ));
}
