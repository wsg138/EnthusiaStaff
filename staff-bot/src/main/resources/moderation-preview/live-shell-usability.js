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
  const minecraftCount = liveModeration.accounts.length;
  const altCount = identity.alts.length;
  return `@${identity.username} • Accounts: 1 Discord + ${minecraftCount} Minecraft • ${altCount} linked Minecraft alt${altCount === 1 ? '' : 's'}`;
}

function hardenedRenderContextPanel() {
  const sanction = liveModeration.sanctions[0];
  const latestCase = liveModeration.cases[0];
  const latestNote = liveModeration.notes[0];
  replaceChildrenOf($('#contextPanel'),
    hardenedContextAccountsSection(),
    contextSection('Current sanctions', sanction ? statusBadge('Active', 'warning') : null,
      sanction ? friendlyPlatform(sanction.type) : 'None', sanction ? sanction.reason : 'No active sanctions were returned.'),
    latestCase ? contextLinkedSection('Latest case', 'cases', latestCase.caseId, `${latestCase.reason} · ${latestCase.actorName}`)
      : contextSection('Latest case', null, 'None', 'No case record was returned.'),
    latestNote ? hardenedLatestNoteSection(latestNote) : contextSection('Latest staff note', null, 'None', 'No private staff note was returned.'),
    contextReadinessSection());
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
  content.push(element('p', {className:'muted small', text:'Accounts includes the Discord identity plus linked Minecraft identities. Linked alts counts only alternate Minecraft accounts.'}));
  return element('div', {className:'context-section'}, content);
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
  const content = [pageHeading('Message investigation', 'Messages & evidence',
    'Review Discord messages, select evidence, and inspect surrounding context.')];
  if (state.contextId) content.push(contextAlertNode());
  if (liveModeration.warning) content.push(element('div', {className:'alert info'},
    element('strong',{text:'Discord read notice'}), element('span',{text:liveModeration.warning})));
  content.push(messageCoverageNode(), hardenedFiltersNode());
  content.push(element('section', {className:'message-investigation'}, messages.length
    ? groupedMessagesNodes(messages)
    : emptyState('No loaded messages match these filters', 'Clear a filter or retrieve more Discord history for a specific channel.')));
  content.push(messagePaginationNode());
  return element('div', {}, content);
}

function messageCoverageNode() {
  const range = loadedMessageRange();
  return element('section', {className:'card coverage-card'},
    sectionHeading('Search coverage'),
    summaryList([
      ['Messages loaded', baseMessages.length],
      ['Loaded date range', range],
      ['Filters search', 'Loaded messages only'],
      ['Coverage', state.contextId ? 'Complete for the loaded ±2 minute context' : 'Partial until Discord history is fully paged']
    ]),
    element('p', {className:'muted small', text:messageCoverageExplanation()}));
}

function loadedMessageRange() {
  const times = baseMessages.map((message) => new Date(message.time)).filter((value) => !Number.isNaN(value.getTime()));
  if (!times.length) return 'No messages loaded';
  times.sort((left, right) => left - right);
  return `${formatExact(times[0].toISOString())} → ${formatExact(times[times.length - 1].toISOString())}`;
}

function messageCoverageExplanation() {
  if (state.contextId) return contextCoverageExplanation();
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
  return 'A channel page retrieves up to 25 Discord messages at a time. Text, author, and date-range filters do not fetch older history; use Load older/newer from Discord to extend the loaded range.';
}

function hardenedFiltersNode() {
  return element('section', {className:'card filters-card'},
    element('div', {className:'filter-row'},
      filterField('Search loaded messages', element('input', {id:'messageSearch', type:'search', placeholder:'Text in loaded messages', value:state.search})),
      filterField('Author', element('input', {id:'authorFilter', type:'search', placeholder:'Name or Discord ID', value:state.author ?? ''})),
      filterField('Channel', channelFilterNode()),
      filterField('From date', element('input', {id:'dateFromFilter', type:'date', value:state.dateFrom || ''})),
      filterField('To date', element('input', {id:'dateToFilter', type:'date', value:state.dateTo || ''})),
      selectedFilterNode(),
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
      element('span', {text:'Choose a readable channel to retrieve more Discord history.'}));
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
  $('[data-clear-filters]')?.addEventListener('click', clearMessageFilters);
  $$('.message-select').forEach((checkbox) => checkbox.addEventListener('click', selectMessage));
  $$('[data-message-action]').forEach((button) => button.addEventListener('click', hardenedHandleMessageAction));
  $$('[data-load-direction]').forEach((button) => button.addEventListener('click', () => loadMoreMessages(button.dataset.loadDirection)));
  bindMessageMenuKeyboard();
}

function bindMessageFilters() {
  $('#messageSearch')?.addEventListener('input', (event) => { state.search = event.target.value; renderWorkspace(); });
  $('#authorFilter')?.addEventListener('input', (event) => { state.author = event.target.value; renderWorkspace(); });
  $('#dateFromFilter')?.addEventListener('change', (event) => { state.dateFrom = event.target.value; renderWorkspace(); });
  $('#dateToFilter')?.addEventListener('change', (event) => { state.dateTo = event.target.value; renderWorkspace(); });
  $('#selectedFilter')?.addEventListener('change', (event) => { state.selectedOnly = event.target.checked; renderWorkspace(); });
}

function handleChannelFilterChange(event) {
  state.channel = event.target.value;
  state.contextId = null;
  state.contextReturn = null;
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
}

state.dateFrom = state.dateFrom || '';
state.dateTo = state.dateTo || '';
installMessageMapperHardening();
window.renderTargetHeader = hardenedRenderTargetHeader;
window.renderContextPanel = hardenedRenderContextPanel;
window.overviewNode = hardenedOverviewNode;
window.messagesNode = hardenedMessagesNode;
window.filtersNode = hardenedFiltersNode;
window.matchesDate = hardenedMatchesDate;
window.bindMessageEvents = hardenedBindMessageEvents;
installProductChrome();
