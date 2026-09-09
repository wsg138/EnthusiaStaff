'use strict';

function installMessageMapperHardening() {
  const previous = window.mapMessage;
  window.mapMessage = function mapHardenedMessage(message) {
    const mapped = previous(message);
    mapped.guildId = optionalText(message?.guildId);
    return mapped;
  };
}

function installModeNotice() {
  const badge = $('.staging-badge');
  if (badge) badge.textContent = 'STAGING · REAL READS / SIMULATED ACTIONS';
  const eyebrow = $('.dialog-header .eyebrow');
  if (eyebrow) eyebrow.textContent = 'Punishment simulation';
  if ($('#modeNotice')) return;
  const notice = element('div', {id:'modeNotice', className:'mode-notice', attrs:{role:'note'}},
    element('strong', {text:'Real data, simulated actions'}),
    element('span', {text:'Player identity, account links, sanctions, history, cases, notes, and Discord messages are read from live sources when available. Punishments, DMs, restrictions, and message deletion are previewed only and are not applied from this staging workspace.'}));
  $('#targetHeader')?.after(notice);
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
    buttonNode('Simulate punishment', 'button primary', {punish:''}));
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
    pageHeading('Player overview', 'Moderation context', 'Live account state, recent history, and current investigation activity.'),
    element('div', {className:'metric-grid'},
      metricNode('Active sanctions', liveModeration.sanctions.length, liveModeration.sanctions.length ? 'Live active records' : 'None'),
      metricNode('Total history', realHistoryTotal(), 'Moderation records for this player'),
      metricNode('Evidence selected', state.evidence.size, 'Evidence in this simulation')),
    element('div', {className:'two-column'},
      element('section', {className:'card'},
        sectionHeading('Recent moderation history', buttonNode('View all', 'text-button', {viewLink:'history'})),
        recentHistory.length ? recentHistory : recordEmptyState('history')),
      element('section', {className:'card'},
        sectionHeading('Investigation', buttonNode('Open messages', 'text-button', {viewLink:'messages'})),
        summaryList([
          ['Selected messages', state.selected.size], ['Evidence', state.evidence.size],
          ['Simulated deletions', state.deleting.size]
        ]), element('p', {className:'muted small', text:'Use the persistent “Simulate punishment” action in the player header when the case is ready.'}))));
}

function hardenedMessagesNode() {
  const messages = filteredMessages();
  const content = [pageHeading('Message investigation', 'Messages & evidence',
    'Review live Discord messages. Search and date filters apply only to messages already loaded into this workspace.')];
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
      ['Filter behavior', 'Loaded messages only'],
      ['Result completeness', state.contextId ? 'Complete for the loaded ±2 minute context' : 'Partial until Discord history is fully paged']
    ]),
    element('p', {className:'muted small', text:messageCoverageExplanation()}));
}

function loadedMessageRange() {
  const times = baseMessages.map((message) => new Date(message.time)).filter((value) => !Number.isNaN(value.getTime()));
  if (!times.length) return 'No messages loaded';
  times.sort((left, right) => left - right);
  return `${formatExact(times[0].toISOString())} → ${formatExact(times.at(-1).toISOString())}`;
}

function messageCoverageExplanation() {
  if (state.contextId) {
    return 'Show context retrieves same-channel messages within ±2 minutes, using pages of up to 50 and a four-page cap per direction. If that safety cap cannot cover the window, the context request fails instead of pretending the result is complete.';
  }
  if (state.channel === 'all') {
    return 'The initial cross-channel view is intentionally incomplete: it can return up to 50 target messages from at most 8 readable channels while examining up to 20 recent messages per channel. Choose one channel to page deeper into Discord history.';
  }
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
      element('span', {text:'To retrieve more history, choose a specific readable channel above.'}));
  }
  const newer = buttonNode('Load newer from Discord', 'button secondary', {loadDirection:'newer'});
  const older = buttonNode('Load older from Discord', 'button secondary', {loadDirection:'older'});
  newer.disabled = !liveModeration.newerCursor;
  older.disabled = !liveModeration.olderCursor;
  return element('div', {className:'page-actions pagination-actions'}, newer, older);
}

function hardenedBindMessageEvents() {
  $('[data-exit-context]')?.addEventListener('click', exitLiveContext);
  $('#messageSearch')?.addEventListener('input', (event) => { state.search = event.target.value; renderWorkspace(); });
  $('#authorFilter')?.addEventListener('input', (event) => { state.author = event.target.value; renderWorkspace(); });
  $('#dateFromFilter')?.addEventListener('change', (event) => { state.dateFrom = event.target.value; renderWorkspace(); });
  $('#dateToFilter')?.addEventListener('change', (event) => { state.dateTo = event.target.value; renderWorkspace(); });
  $('#selectedFilter')?.addEventListener('change', (event) => { state.selectedOnly = event.target.checked; renderWorkspace(); });
  $('#channelFilter')?.addEventListener('change', handleChannelFilterChange);
  $('[data-clear-filters]')?.addEventListener('click', clearMessageFilters);
  $$('.message-select').forEach((checkbox) => checkbox.addEventListener('click', selectMessage));
  $$('[data-message-action]').forEach((button) => button.addEventListener('click', hardenedHandleMessageAction));
  $$('[data-load-direction]').forEach((button) => button.addEventListener('click', () => loadMoreMessages(button.dataset.loadDirection)));
  bindMessageMenuKeyboard();
}

function handleChannelFilterChange(event) {
  state.channel = event.target.value;
  state.contextId = null;
  state.contextReturn = null;
  loadChannelPage();
}

async function clearMessageFilters() {
  state.search = '';
  state.author = '';
  state.date = 'all';
  state.dateFrom = '';
  state.dateTo = '';
  state.selectedOnly = false;
  state.contextId = null;
  state.contextReturn = null;
  const bound = sessionBoundChannelId();
  state.channel = bound && liveModeration.channels.some((channel) => channel.id === bound) ? bound : 'all';
  await loadChannelPage();
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
installModeNotice();
