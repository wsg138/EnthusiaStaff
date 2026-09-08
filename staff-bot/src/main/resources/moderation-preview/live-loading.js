'use strict';

const CONTEXT_WINDOW_MS = 120_000;
const CONTEXT_PAGE_SIZE = 50;
const MAX_CONTEXT_PAGES_PER_DIRECTION = 4;

/** Replaces the legacy sample scenario with a truthful neutral state while live reads load. */
function applyScenario() {
  state.scenario = 'live';
  state.selected.clear();
  state.evidence.clear();
  state.violating.clear();
  state.deleting.clear();
  state.anchor = null;
  state.contextId = null;
  state.history = [];
  state.search = '';
  state.channel = 'all';
  state.date = 'all';
  state.author = '';
  state.selectedOnly = false;
  state.evidenceRevision++;
  state.workflow = null;
  liveModeration.bootstrap = null;
  liveModeration.cases = [];
  liveModeration.notes = [];
  liveModeration.sanctions = [];
  liveModeration.accounts = [];
  liveModeration.channels = [];
  liveModeration.warning = 'Loading moderation data…';
  baseMessages.splice(0);
  RESTRICTION_TARGETS.splice(0);
  Object.assign(identity, {
    displayName:'Loading moderation data…', username:'loading', discordId:LOADING_TEXT, minecraft:LOADING_TEXT,
    minecraftUuid:LOADING_TEXT, alts:[], status:'Loading', statusDetail:'Waiting for authoritative read data',
    avatarUrl:'', linkState:'Loading'
  });
  renderAll();
}

/** Extends the existing single-page context reader without duplicating UI/context orchestration. */
document.addEventListener('DOMContentLoaded', () => queueMicrotask(installContextReadPagination));

function installContextReadPagination() {
  const singlePageRead = window.fetchContextPage;
  window.fetchContextPage = (channelId, direction, triggerId) =>
    paginatedContextRead(singlePageRead, channelId, direction, triggerId);
}

function paginatedContextRead(singlePageRead, channelId, direction, triggerId) {
  return readContextPage({
    singlePageRead,
    trigger: baseMessages.find((message) => message.id === triggerId),
    channelId,
    direction,
    cursor: triggerId,
    pageNumber: 0,
    messages: []
  });
}

async function readContextPage(request) {
  const page = await request.singlePageRead(request.channelId, request.direction, request.cursor);
  const collected = request.messages.concat(
    page.filter((message) => message.channelId === request.channelId)
  );
  if (contextReadComplete(request.trigger, page, request.direction)) return collected;
  const nextCursor = contextPageCursor(page, request.direction);
  return readContextPage(nextContextRequest(request, collected, nextCursor));
}

function contextReadComplete(trigger, page, direction) {
  return page.length < CONTEXT_PAGE_SIZE || contextBoundaryReached(trigger, page, direction);
}

function nextContextRequest(request, messages, nextCursor) {
  if (request.pageNumber + 1 >= MAX_CONTEXT_PAGES_PER_DIRECTION) {
    throw new Error('Discord context is too dense to display safely within the two-minute window.');
  }
  if (nextCursor === request.cursor) throw new Error('Discord context pagination did not advance.');
  return {
    ...request,
    cursor: nextCursor,
    pageNumber: request.pageNumber + 1,
    messages
  };
}

function contextBoundaryReached(trigger, messages, direction) {
  const triggerTime = new Date(trigger.time).getTime();
  const times = messages.map((message) => new Date(message.time).getTime());
  return direction === 'before'
    ? Math.min(...times) <= triggerTime - CONTEXT_WINDOW_MS
    : Math.max(...times) >= triggerTime + CONTEXT_WINDOW_MS;
}

function contextPageCursor(messages, direction) {
  const ordered = messages.slice().sort((left, right) => new Date(left.time) - new Date(right.time));
  return direction === 'before' ? ordered[0].id : ordered[ordered.length - 1].id;
}
