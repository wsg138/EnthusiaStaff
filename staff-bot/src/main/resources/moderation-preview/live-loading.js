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
    displayName:'Loading moderation data…', username:'loading', discordId:'Loading…', minecraft:'Loading…',
    minecraftUuid:'Loading…', alts:[], status:'Loading', statusDetail:'Waiting for authoritative read data',
    avatarUrl:'', linkState:'Loading'
  });
  renderAll();
}

/** Replaces the single-page context read after every deferred live script is loaded. */
document.addEventListener('DOMContentLoaded', () => queueMicrotask(installContextHardening));

function installContextHardening() {
  window.showTwoMinuteContext = showPaginatedTwoMinuteContext;
  window.exitLiveContext = exitPaginatedContext;
  window.contextAlertNode = paginatedContextAlertNode;
}

async function showPaginatedTwoMinuteContext(id) {
  const trigger = baseMessages.find((message) => message.id === id);
  if (!trigger) return;
  const previous = rememberMessageView();
  previous.contextTruncated = Boolean(state.contextTruncated);
  try {
    const contextRead = await readContextWindow(trigger);
    applyContextView(trigger, id, previous, contextRead);
  } catch (error) {
    showToast(error.message || 'Discord context is temporarily unavailable.', true);
  }
}

async function readContextWindow(trigger) {
  const [before, after] = await Promise.all([
    fetchContextDirection(trigger, 'before'),
    fetchContextDirection(trigger, 'after')
  ]);
  return {
    messages:boundedContextMessages(trigger, before.messages, after.messages),
    complete:before.complete && after.complete
  };
}

function applyContextView(trigger, id, previous, contextRead) {
  state.contextReturn = previous;
  baseMessages.splice(0, baseMessages.length, ...contextRead.messages);
  resetContextFilters(trigger.channelId);
  state.contextId = id;
  state.contextTruncated = !contextRead.complete;
  liveModeration.olderCursor = null;
  liveModeration.newerCursor = null;
  renderAll();
}

function resetContextFilters(channelId) {
  state.search = '';
  state.author = '';
  state.channel = channelId;
  state.date = 'all';
  state.selectedOnly = false;
}

async function fetchContextDirection(trigger, direction) {
  const messages = [];
  let cursor = trigger.id;
  for (let pageNumber = 0; pageNumber < MAX_CONTEXT_PAGES_PER_DIRECTION; pageNumber++) {
    const page = await fetchContextPage(trigger.channelId, direction, cursor);
    messages.push(...page);
    if (contextReadComplete(trigger, page, direction)) return {messages, complete:true};
    const nextCursor = contextPageCursor(page, direction);
    if (!nextCursor || nextCursor === cursor) return {messages, complete:false};
    cursor = nextCursor;
  }
  return {messages, complete:false};
}

function contextReadComplete(trigger, page, direction) {
  return page.length < CONTEXT_PAGE_SIZE || contextBoundaryReached(trigger, page, direction);
}

function contextPageCursor(messages, direction) {
  const ordered = messages.filter(hasValidMessageTime).slice().sort(compareMessageTimeAscending);
  if (!ordered.length) return '';
  return direction === 'before' ? ordered[0].id : ordered[ordered.length - 1].id;
}

function hasValidMessageTime(message) {
  return Number.isFinite(new Date(message.time).getTime());
}

function compareMessageTimeAscending(left, right) {
  return new Date(left.time) - new Date(right.time);
}

function contextBoundaryReached(trigger, messages, direction) {
  const triggerTime = new Date(trigger.time).getTime();
  if (!Number.isFinite(triggerTime)) return true;
  const times = messages.map((message) => new Date(message.time).getTime()).filter(Number.isFinite);
  if (!times.length) return true;
  if (direction === 'before') return Math.min(...times) <= triggerTime - CONTEXT_WINDOW_MS;
  return Math.max(...times) >= triggerTime + CONTEXT_WINDOW_MS;
}

function boundedContextMessages(trigger, before, after) {
  const triggerTime = new Date(trigger.time).getTime();
  const byId = new Map([[trigger.id, trigger]]);
  for (const message of [...before, ...after]) addContextMessage(byId, trigger, triggerTime, message);
  return [...byId.values()].sort(compareMessageTimeDescending);
}

function addContextMessage(byId, trigger, triggerTime, message) {
  if (message.channelId !== trigger.channelId) return;
  const messageTime = new Date(message.time).getTime();
  if (!Number.isFinite(messageTime)) return;
  if (Math.abs(messageTime - triggerTime) <= CONTEXT_WINDOW_MS) byId.set(message.id, message);
}

function compareMessageTimeDescending(left, right) {
  return new Date(right.time) - new Date(left.time);
}

function exitPaginatedContext() {
  const previous = state.contextReturn;
  state.contextId = null;
  state.contextReturn = null;
  state.contextTruncated = previous ? Boolean(previous.contextTruncated) : false;
  if (!previous) {
    renderAll();
    return;
  }
  restoreMessageView(previous);
  renderAll();
}

function restoreMessageView(previous) {
  baseMessages.splice(0, baseMessages.length, ...previous.messages);
  state.search = previous.search;
  state.author = previous.author;
  state.channel = previous.channel;
  state.date = previous.date;
  state.selectedOnly = previous.selectedOnly;
  liveModeration.olderCursor = previous.olderCursor;
  liveModeration.newerCursor = previous.newerCursor;
}

function paginatedContextAlertNode() {
  const detail = state.contextTruncated
    ? 'The selected message is highlighted with bounded context. The safety read limit was reached, so additional messages inside the two-minute window may exist.'
    : 'The selected message is highlighted with messages from all authors within two minutes before and after it.';
  return element('div', {className:'alert info'},
    element('strong', {text:'Two-minute conversation context'}),
    element('span', {text:detail}),
    buttonNode('Exit context', 'text-button', {exitContext:''}));
}
