'use strict';

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

/** Installs final live-only UI behavior after the base preview scripts register. */
document.addEventListener('DOMContentLoaded', () => queueMicrotask(installLiveHardening));

function installLiveHardening() {
  const contextWindowMs = 120_000;
  const contextPageSize = 50;
  const maxContextPagesPerDirection = 4;

  window.messageActionsNode = function hardenedMessageActionsNode(message) {
    const menu = element('details', {className:'message-actions'});
    const summary = element('summary', {
      className:'icon-button',
      text:'•••',
      attrs:{'aria-label':`Message actions for ${message.id}`, 'aria-haspopup':'menu'}
    });
    const items = element('div', {className:'message-action-menu', attrs:{role:'menu'}},
      hardenedMessageActionButton('Show context', 'context', message.id),
      hardenedMessageActionButton(state.evidence.has(message.id) ? 'Remove evidence' : 'Add to evidence', 'evidence', message.id),
      hardenedMessageActionButton(state.deleting.has(message.id) ? 'Preserve message' : 'Delete on confirm (simulation)', 'delete', message.id));
    menu.append(summary, items);
    return menu;
  };

  window.messageActionButton = hardenedMessageActionButton;
  window.showTwoMinuteContext = showHardenedTwoMinuteContext;
  window.exitLiveContext = exitHardenedContext;
  window.contextAlertNode = hardenedContextAlertNode;
  window.punishmentScopeTab = hardenedPunishmentScopeTab;
  renderAll();

  function hardenedMessageActionButton(label, action, messageId) {
    const button = buttonNode(label, 'message-action-item', {messageAction:action, messageId});
    button.setAttribute('role', 'menuitem');
    return button;
  }

  async function showHardenedTwoMinuteContext(id) {
    const trigger = baseMessages.find((message) => message.id === id);
    if (!trigger) return;
    const previous = rememberMessageView();
    previous.contextTruncated = Boolean(state.contextTruncated);
    try {
      const [before, after] = await Promise.all([
        fetchContextDirection(trigger, 'before'),
        fetchContextDirection(trigger, 'after')
      ]);
      const context = boundedTimeContext(trigger, before.messages, after.messages);
      state.contextReturn = previous;
      baseMessages.splice(0, baseMessages.length, ...context);
      state.search = '';
      state.author = '';
      state.channel = trigger.channelId;
      state.date = 'all';
      state.selectedOnly = false;
      state.contextId = id;
      state.contextTruncated = !(before.complete && after.complete);
      liveModeration.olderCursor = null;
      liveModeration.newerCursor = null;
      renderAll();
    } catch (error) {
      showToast(error.message || 'Discord context is temporarily unavailable.', true);
    }
  }

  async function fetchContextDirection(trigger, direction) {
    const messages = [];
    let cursor = trigger.id;
    for (let pageNumber = 0; pageNumber < maxContextPagesPerDirection; pageNumber++) {
      const page = await fetchContextPage(trigger.channelId, direction, cursor);
      messages.push(...page);
      if (page.length < contextPageSize || contextBoundaryReached(trigger, page, direction)) {
        return {messages, complete:true};
      }
      const nextCursor = contextPageCursor(page, direction);
      if (!nextCursor || nextCursor === cursor) return {messages, complete:false};
      cursor = nextCursor;
    }
    return {messages, complete:false};
  }

  function contextPageCursor(messages, direction) {
    const ordered = messages
      .filter((message) => Number.isFinite(new Date(message.time).getTime()))
      .slice()
      .sort((left, right) => new Date(left.time) - new Date(right.time));
    if (!ordered.length) return '';
    return direction === 'before' ? ordered[0].id : ordered[ordered.length - 1].id;
  }

  function contextBoundaryReached(trigger, messages, direction) {
    const triggerTime = new Date(trigger.time).getTime();
    if (!Number.isFinite(triggerTime)) return true;
    const times = messages.map((message) => new Date(message.time).getTime()).filter(Number.isFinite);
    if (!times.length) return true;
    return direction === 'before'
      ? Math.min(...times) <= triggerTime - contextWindowMs
      : Math.max(...times) >= triggerTime + contextWindowMs;
  }

  function boundedTimeContext(trigger, before, after) {
    const triggerTime = new Date(trigger.time).getTime();
    const byId = new Map([[trigger.id, trigger]]);
    for (const message of [...before, ...after]) {
      if (message.channelId !== trigger.channelId) continue;
      const messageTime = new Date(message.time).getTime();
      if (Number.isFinite(messageTime) && Math.abs(messageTime - triggerTime) <= contextWindowMs) {
        byId.set(message.id, message);
      }
    }
    return [...byId.values()].sort((left, right) => new Date(right.time) - new Date(left.time));
  }

  function exitHardenedContext() {
    const previous = state.contextReturn;
    state.contextId = null;
    state.contextReturn = null;
    if (!previous) {
      state.contextTruncated = false;
      renderAll();
      return;
    }
    baseMessages.splice(0, baseMessages.length, ...previous.messages);
    state.search = previous.search;
    state.author = previous.author;
    state.channel = previous.channel;
    state.date = previous.date;
    state.selectedOnly = previous.selectedOnly;
    state.contextTruncated = Boolean(previous.contextTruncated);
    liveModeration.olderCursor = previous.olderCursor;
    liveModeration.newerCursor = previous.newerCursor;
    renderAll();
  }

  function hardenedContextAlertNode() {
    const detail = state.contextTruncated
      ? 'The selected message is highlighted with bounded context. The safety read limit was reached, so additional messages inside the two-minute window may exist.'
      : 'The selected message is highlighted with messages from all authors within two minutes before and after it.';
    return element('div', {className:'alert info'},
      element('strong', {text:'Two-minute conversation context'}),
      element('span', {text:detail}),
      buttonNode('Exit context', 'text-button', {exitContext:''}));
  }

  function hardenedPunishmentScopeTab(value, label, selected) {
    const button = buttonNode(label, `punishment-scope-tab${selected === value ? ' active' : ''}`, {offenseTab:value});
    button.setAttribute('role', 'tab');
    button.setAttribute('aria-selected', selected === value ? 'true' : 'false');
    return button;
  }
}
