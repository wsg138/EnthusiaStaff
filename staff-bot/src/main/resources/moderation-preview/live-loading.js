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

/** Extends the existing single-page context reader without duplicating UI/context orchestration. */
document.addEventListener('DOMContentLoaded', () => queueMicrotask(installContextReadPagination));

function installContextReadPagination() {
  const singlePageRead = window.fetchContextPage;
  window.fetchContextPage = (channelId, direction, triggerId) =>
    paginatedContextRead(singlePageRead, channelId, direction, triggerId);
}

async function paginatedContextRead(singlePageRead, channelId, direction, triggerId) {
  const trigger = baseMessages.find((message) => message.id === triggerId);
  const messages = [];
  let cursor = triggerId;
  for (let pageNumber = 0; pageNumber < MAX_CONTEXT_PAGES_PER_DIRECTION; pageNumber++) {
    const page = await singlePageRead(channelId, direction, cursor);
    messages.push(...page.filter((message) => message.channelId === channelId));
    if (page.length < CONTEXT_PAGE_SIZE || contextBoundaryReached(trigger, page, direction)) return messages;
    const nextCursor = contextPageCursor(page, direction);
    if (nextCursor === cursor) throw new Error('Discord context pagination did not advance.');
    cursor = nextCursor;
  }
  throw new Error('Discord context is too dense to display safely within the two-minute window.');
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
