'use strict';

function installMessageMapperHardening() {
  const previous = window.mapMessage;
  window.mapMessage = function mapHardenedMessage(message) {
    const mapped = previous(message);
    mapped.guildId = optionalText(message?.guildId);
    return mapped;
  };
}

function installProductChrome() {
  $('.scenario-control')?.remove();
  $('.staging-badge')?.remove();
  $('#modeNotice')?.remove();
  const eyebrow = $('.dialog-header .eyebrow');
  if (eyebrow) eyebrow.textContent = 'Issue punishment';
}

function hardenedRenderTargetHeader() {
  const header = $('#targetHeader');
  const identityLine = element('div', {className:'identity-line'},
    element('h1', {id:'targetName', text:identity.displayName}),
    statusBadge(identity.status, liveModeration.sanctions.length ? 'warning' : 'neutral'),
    statusBadge(identity.linkState || 'Link state not provided', 'neutral'));
  const technical = element('details', {className:'technical-meta'},
    element('summary', {text:'Technical IDs'}),
    element('div', {text:`Discord ${identity.discordId} · Minecraft ${identity.minecraftUuid}`}));
  const targetIdentity = element('div', {className:'target-identity'},
    identityLine, element('div', {className:'target-subline', text:linkedIdentitySummary()}), technical);
  const actions = element('div', {className:'target-actions'},
    buttonNode('Review messages', 'button secondary', {openMessages:''}),
    buttonNode('Issue punishment', 'button primary', {punish:''}));
  replaceChildrenOf(header, targetAvatarNode(), targetIdentity, actions);
  $('[data-open-messages]')?.addEventListener('click', () => switchView('messages'));
  $('[data-punish]')?.addEventListener('click', openWorkflow);
}

function targetAvatarNode() {
  if (!identity.avatarUrl) {
    return element('div', {className:'target-avatar', text:initials(identity.displayName), attrs:{'aria-hidden':'true'}});
  }
  const image = document.createElement('img');
  image.className = 'target-avatar';
  image.src = identity.avatarUrl;
  image.alt = `${identity.displayName} avatar`;
  image.referrerPolicy = 'no-referrer';
  return image;
}

function linkedIdentitySummary() {
  const main = liveModeration.accounts.find((account) => account.main) || liveModeration.accounts[0];
  const parts = [`@${identity.username}`];
  if (main) parts.push(`${main.username || main.playerId} · ${friendlyPlatform(main.platform)}`);
  if (identity.alts.length) parts.push(`${identity.alts.length} alt${identity.alts.length === 1 ? '' : 's'}`);
  return parts.join(' • ');
}

function hardenedRenderContextPanel() {
  const sanction = liveModeration.sanctions[0];
  const latestCase = liveModeration.cases[0];
  const latestNote = liveModeration.notes[0];
  const sections = [hardenedContextAccountsSection()];
  if (sanction) {
    sections.push(contextSection('Current sanction', statusBadge('Active', 'warning'),
      friendlyPlatform(sanction.type), sanction.reason));
  }
  if (latestCase) {
    sections.push(contextLinkedSection('Latest case', 'cases', latestCase.caseId,
      `${latestCase.reason} · ${latestCase.actorName}`));
  }
  if (latestNote) sections.push(hardenedLatestNoteSection(latestNote));
  replaceChildrenOf($('#contextPanel'), sections);
  $$('[data-context-view]').forEach((button) => button.addEventListener('click', () => switchView(button.dataset.contextView)));
}

function hardenedContextAccountsSection() {
  const content = [sectionHeading('Linked identities', buttonNode('View', 'text-button', {contextView:'accounts'}))];
  if (!liveModeration.accounts.length) {
    content.push(element('div', {className:'muted small', text:'No linked Minecraft accounts were returned.'}));
  } else {
    liveModeration.accounts.forEach((account) => content.push(accountLine(
      account.username || account.playerId, minecraftRelationshipText(account))));
  }
  return element('div', {className:'context-section compact-context'}, content);
}

function minecraftRelationshipText(account) {
  const platform = friendlyPlatform(account.platform);
  const platformText = platform === 'Unknown' ? 'platform not provided' : platform;
  return account.main ? `Main account · ${platformText}` : `Linked alternate account · ${platformText}`;
}

function hardenedLatestNoteSection(note) {
  return element('div', {className:'context-section'},
    sectionHeading('Latest staff note', buttonNode('View', 'text-button', {contextView:'notes'})),
    element('p', {className:'compact-copy', text:note.text}),
    element('div', {className:'muted small', text:`Staff ${note.actorId} · ${formatExact(note.createdAt)}`}));
}

function contextReadinessSection() {
  return element('div', {className:'context-section'},
    sectionHeading('Case readiness'), readinessChecklistNode(state.workflow));
}

function hardenedOverviewNode() {
  const recentHistory = state.history.slice(0, 3).map(historyCompactNode);
  return element('div', {},
    pageHeading('Player overview', 'Moderation context', 'Account state, recent history, and the current investigation.'),
    element('div', {className:'metric-grid'},
      metricNode('Active sanctions', liveModeration.sanctions.length, liveModeration.sanctions.length ? 'Active records' : 'None'),
      metricNode('Total history', realHistoryTotal(), 'Moderation records for this player'),
      metricNode('Evidence selected', state.evidence.size, 'Evidence in this case')),
    element('div', {className:'two-column'},
      overviewHistoryCard(recentHistory), overviewInvestigationCard()));
}

function overviewHistoryCard(recentHistory) {
  return element('section', {className:'card'},
    sectionHeading('Recent moderation history', buttonNode('View all', 'text-button', {viewLink:'history'})),
    recentHistory.length ? recentHistory : recordEmptyState('history'));
}

function overviewInvestigationCard() {
  return element('section', {className:'card'},
    sectionHeading('Investigation', buttonNode('Open messages', 'text-button', {viewLink:'messages'})),
    summaryList([
      ['Selected messages', state.selected.size], ['Evidence', state.evidence.size],
      ['Marked for deletion', state.deleting.size]
    ]), element('p', {className:'muted small', text:'Use “Issue punishment” in the player header when the case is ready.'}));
}

function hardenedMessagesNode() {
  const messages = filteredMessages();
  const content = [pageHeading('Messages', 'Messages & evidence',
    'Select evidence or search older Discord history.')];
  if (state.contextId) content.push(contextAlertNode());
  if (liveModeration.warning) content.push(element('div', {className:'alert info'},
    element('strong',{text:'Discord read notice'}), element('span',{text:liveModeration.warning})));
  content.push(messageCoverageNode(), hardenedFiltersNode());
  content.push(element('section', {className:'message-investigation'}, messages.length
    ? groupedMessagesNodes(messages)
    : emptyState(state.remoteSearchActive ? 'No Discord history matches this search' : 'No loaded messages match these filters',
      state.remoteSearchActive ? 'Try a broader term, channel, author ID, or date.' : 'Clear a filter or run a Discord history search.')));
  content.push(messagePaginationNode());
  return element('div', {}, content);
}

function messageCoverageNode() {
  const range = loadedMessageRange();
  const remote = state.remoteSearchActive === true;
  const countLabel = remote
    ? `${baseMessages.length} search result${baseMessages.length === 1 ? '' : 's'}`
    : `${baseMessages.length} message${baseMessages.length === 1 ? '' : 's'} loaded`;
  const mode = state.contextId ? 'Context loaded' : remote ? 'History search' : 'Recent messages';
  return element('details',{className:'coverage-summary'},
    element('summary',{},
      element('strong',{text:countLabel}),
      element('span',{text:mode})),
    element('div',{className:'coverage-details'},
      element('div',{className:'muted small',text:range}),
      element('p',{text:messageCoverageExplanation()})));
}

function loadedMessageRange() {
  const times = baseMessages.map((message) => new Date(message.time)).filter((value) => !Number.isNaN(value.getTime()));
  if (!times.length) return 'No messages loaded';
  times.sort((left, right) => left - right);
  return `${formatExact(times[0].toISOString())} → ${formatExact(times[times.length - 1].toISOString())}`;
}

function messageCoverageExplanation() {
  if (state.contextId) return contextCoverageExplanation();
  if (state.remoteSearchActive) {
    return state.channel === 'all'
      ? 'Search queries Discord history beyond the messages already loaded, scanning a bounded amount of readable history across channels. Narrow to one channel for the deepest search and paging.'
      : 'Search queries older Discord history in this channel, not just the messages already on screen. Use Load older from Discord to continue the same search behind the oldest result.';
  }
  if (state.channel === 'all') return initialCoverageExplanation();
  return channelCoverageExplanation();
}

function contextCoverageExplanation() {
  return 'Show context retrieves same-channel messages within ±2 minutes, using pages of up to 50 and a four-page cap per direction. If the cap cannot cover the window, the request fails instead of showing an incomplete result as complete.';
}

function initialCoverageExplanation() {
  return 'The initial cross-channel view is incomplete: it can return up to 50 target messages from at most 8 readable channels while examining up to 20 recent messages per channel. Choose one channel to page deeper into Discord history.';
}

function channelCoverageExplanation() {
  return 'A normal channel page retrieves recent Discord messages. Enter a term and use Search Discord history to scan older messages without manually loading every page.';
}

function hardenedFiltersNode() {
  return element('section', {className:'card filters-card'},
    element('div', {className:'filter-row'},
      filterField('Search Discord history', element('input', {
        id:'messageSearch', type:'search', placeholder:'Search message text beyond the loaded page',
        value:state.search, attrs:{autocomplete:'off'}
      })),
      filterField('Author', element('input', {id:'authorFilter', type:'search', placeholder:'Name or Discord ID', value:state.author ?? ''})),
      filterField('Channel', channelFilterNode()),
      filterField('From date', element('input', {id:'dateFromFilter', type:'date', value:state.dateFrom || ''})),
      filterField('To date', element('input', {id:'dateToFilter', type:'date', value:state.dateTo || ''})),
      selectedFilterNode(),
      buttonNode('Search Discord history', 'button primary filter-search', {runMessageSearch:''}),
      buttonNode('Clear filters', 'button ghost filter-clear', {clearFilters:''})));
}

function filterField(label, control) {
  return element('label', {className:'filter-field'}, element('span', {text:label}), control);
}

function hardenedMatchesDate(message) {
  const key = messageDateKey(message.time);
  const from = state.dateFrom || '';
  const to = state.dateTo || '';
  return (!from || key >= from) && (!to || key <= to);
}

function messagePaginationNode() {
  if (state.contextId) return element('div');
  if (state.channel === 'all') {
    return element('div', {className:'pagination-note'},
      element('span', {text:state.remoteSearchActive
        ? 'Cross-channel search scans bounded readable history. Choose one channel to page farther back.'
        : 'Choose a readable channel to retrieve more Discord history.'}));
  }
  const newer = buttonNode('Load newer from Discord', 'button secondary', {loadDirection:'newer'});
  const older = buttonNode('Load older from Discord', 'button secondary', {loadDirection:'older'});
  newer.disabled = !liveModeration.newerCursor;
  older.disabled = !liveModeration.olderCursor;
  return element('div', {className:'page-actions pagination-actions'}, newer, older);
}

function hardenedBindMessageEvents() {
  $('[data-exit-context]')?.addEventListener('click', exitLiveContext);
  bindMessageFilters();
  $('#channelFilter')?.addEventListener('change', handleChannelFilterChange);
  $('[data-run-message-search]')?.addEventListener('click', runDiscordHistorySearch);
  $('[data-clear-filters]')?.addEventListener('click', clearMessageFilters);
  $$('.message-select').forEach((checkbox) => checkbox.addEventListener('click', selectMessage));
  $$('[data-message-action]').forEach((button) => button.addEventListener('click', hardenedHandleMessageAction));
  $$('[data-load-direction]').forEach((button) => button.addEventListener('click', () => loadMoreMessages(button.dataset.loadDirection)));
  bindMessageMenuKeyboard();
}

function bindMessageFilters() {
  const search = $('#messageSearch');
  const author = $('#authorFilter');
  search?.addEventListener('input', (event) => { state.search = event.target.value; });
  author?.addEventListener('input', (event) => { state.author = event.target.value; });
  search?.addEventListener('keydown', runMessageSearchOnEnter);
  author?.addEventListener('keydown', runMessageSearchOnEnter);
  $('#dateFromFilter')?.addEventListener('change', (event) => { state.dateFrom = event.target.value; renderWorkspace(); });
  $('#dateToFilter')?.addEventListener('change', (event) => { state.dateTo = event.target.value; renderWorkspace(); });
  $('#selectedFilter')?.addEventListener('change', (event) => { state.selectedOnly = event.target.checked; renderWorkspace(); });
}

function runMessageSearchOnEnter(event) {
  if (event.key !== 'Enter') return;
  event.preventDefault();
  runDiscordHistorySearch();
}

async function runDiscordHistorySearch() {
  state.contextId = null;
  state.contextReturn = null;
  const params = discordHistorySearchParams();
  if (!hasDiscordHistorySearchCriteria(params)) {
    state.remoteSearchActive = false;
    state.remoteSearchCriteria = null;
    await loadChannelPage();
    return;
  }
  const previousActive = state.remoteSearchActive;
  const previousCriteria = state.remoteSearchCriteria;
  state.remoteSearchActive = true;
  state.remoteSearchCriteria = submittedHistorySearchCriteria(params);
  const loaded = await loadMessageRequest(params, 'replace');
  if (!loaded) {
    state.remoteSearchActive = previousActive;
    state.remoteSearchCriteria = previousCriteria;
  }
}

function submittedHistorySearchCriteria(params) {
  return Object.freeze({
    text:params.get('text') || '',
    author:params.get('author') || '',
    date:params.get('date') || ''
  });
}

function discordHistorySearchParams() {
  const params = new URLSearchParams({limit:'50'});
  addSearchChannel(params);
  addSearchText(params);
  addSearchAuthor(params);
  addSearchDate(params);
  return params;
}

function addSearchChannel(params) {
  if (state.channel !== 'all') params.set('channel', state.channel);
}

function addSearchText(params) {
  const text = String(state.search || '').trim();
  if (text) params.set('text', text);
}

function addSearchAuthor(params) {
  const author = String(state.author || '').trim();
  if (author) params.set('author', author);
}

function addSearchDate(params) {
  if (state.dateFrom && state.dateFrom === state.dateTo) params.set('date', state.dateFrom);
}

function hasDiscordHistorySearchCriteria(params) {
  return params.has('text') || params.has('author') || params.has('date');
}

function handleChannelFilterChange(event) {
  state.channel = event.target.value;
  state.contextId = null;
  state.contextReturn = null;
  state.remoteSearchActive = false;
  state.remoteSearchCriteria = null;
  loadChannelPage();
}

async function clearMessageFilters() {
  clearLocalMessageFilters();
  const bound = sessionBoundChannelId();
  state.channel = bound && liveModeration.channels.some((channel) => channel.id === bound) ? bound : 'all';
  await loadChannelPage();
}

function clearLocalMessageFilters() {
  state.search = '';
  state.author = '';
  state.date = 'all';
  state.dateFrom = '';
  state.dateTo = '';
  state.selectedOnly = false;
  state.contextId = null;
  state.contextReturn = null;
  state.remoteSearchActive = false;
  state.remoteSearchCriteria = null;
}

state.dateFrom = state.dateFrom || '';
state.dateTo = state.dateTo || '';
state.remoteSearchActive = state.remoteSearchActive || false;
state.remoteSearchCriteria = state.remoteSearchCriteria || null;
installMessageMapperHardening();
window.renderTargetHeader = hardenedRenderTargetHeader;
window.renderContextPanel = hardenedRenderContextPanel;
window.overviewNode = hardenedOverviewNode;
window.messagesNode = hardenedMessagesNode;
window.filtersNode = hardenedFiltersNode;
window.matchesDate = hardenedMatchesDate;
window.bindMessageEvents = hardenedBindMessageEvents;
installProductChrome();
