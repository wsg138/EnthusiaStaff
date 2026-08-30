'use strict';

function renderAll() {
  renderTargetHeader();
  renderContextPanel();
  renderCounts();
  renderWorkspace();
  renderSelectionBar();
  $$('.nav-item').forEach((button) => button.classList.toggle('active', button.dataset.view === state.view));
}

function renderTargetHeader() {
  const header = $('#targetHeader');
  const identityLine = element('div', {className: 'identity-line'},
    element('h1', {id: 'targetName', text: identity.displayName}),
    statusBadge(identity.status, 'warning'),
    statusBadge('Java + Bedrock linked', 'neutral'));
  const technical = element('details', {className: 'technical-meta'},
    element('summary', {text: 'Technical IDs'}),
    element('div', {text: `Discord ${identity.discordId} · Minecraft ${identity.minecraftUuid}`}));
  const targetIdentity = element('div', {className: 'target-identity'},
    identityLine,
    element('div', {className: 'target-subline', text: `@${identity.username} • ${identity.minecraft} • ${identity.alts.length} linked alt`}),
    technical);
  const actions = element('div', {className: 'target-actions'},
    buttonNode('Review messages', 'button secondary', {openMessages: ''}),
    buttonNode('Issue Punishment', 'button primary', {punish: ''}));
  replaceChildrenOf(header, element('div', {className: 'target-avatar', text: 'RA', attrs: {'aria-hidden': 'true'}}), targetIdentity, actions);
  $('[data-open-messages]').addEventListener('click', () => switchView('messages'));
  $('[data-punish]').addEventListener('click', openWorkflow);
}

function statusBadge(text, tone) {
  return element('span', {className: `status-badge ${tone}`, text});
}

function renderContextPanel() {
  const active = identity.status === 'Discord mute' ? statusBadge('Active', 'warning') : null;
  replaceChildrenOf($('#contextPanel'),
    contextAccountsSection(),
    contextSection('Current sanctions', active, 'Discord mute', '1h 18m remaining · chat only'),
    contextLinkedSection('Open case', 'cases', 'CASE-1187', 'Spam pattern review · Morgan'),
    contextNoteSection(),
    contextSection('Case readiness', null, null, 'Review the offense, evidence, recommendation, and any approval requirement before confirmation.'));
  $$('[data-context-view]').forEach((button) => button.addEventListener('click', () => switchView(button.dataset.contextView)));
}

function contextAccountsSection() {
  return element('div', {className: 'context-section'},
    sectionHeading('Linked accounts', buttonNode('View', 'text-button', {contextView: 'accounts'})),
    accountLine(identity.minecraft, 'Main · Java'),
    accountLine(identity.alts[0].name, `${identity.alts[0].platform} · linked alt`));
}

function contextLinkedSection(title, view, value, detail) {
  return element('div', {className: 'context-section'},
    sectionHeading(title, buttonNode('View', 'text-button', {contextView: view})),
    element('div', {className: 'context-value', text: value}),
    element('div', {className: 'muted small', text: detail}));
}

function contextNoteSection() {
  return element('div', {className: 'context-section'},
    sectionHeading('Latest staff note', buttonNode('View', 'text-button', {contextView: 'notes'})),
    element('p', {className: 'compact-copy', text: 'Repeated flood behavior documented. Player was cooperative during the last contact.'}),
    element('div', {className: 'muted tiny', text: 'Avery · Aug 20, 2026 8:14 PM'}));
}

function contextSection(title, trailing, value, detail) {
  const content = [sectionHeading(title, trailing)];
  if (value) content.push(element('div', {className: 'context-value', text: value}));
  if (detail) content.push(element(value ? 'div' : 'p', {className: value ? 'muted small' : 'compact-copy', text: detail}));
  return element('div', {className: 'context-section'}, content);
}

function sectionHeading(title, trailing = null) {
  return element('div', {className: 'section-heading'}, element('h3', {text: title}), trailing);
}

function accountLine(name, detail) {
  return element('div', {className: 'account-line'}, element('strong', {text: name}), element('span', {text: detail}));
}

function renderCounts() {
  const counts = {messages: baseMessages.length, history: state.history.length, cases: 3, notes: 3, accounts: 2};
  for (const [key, value] of Object.entries(counts)) {
    const target = $(`[data-count="${key}"]`);
    if (target) target.textContent = value;
  }
}

function renderWorkspace() {
  const views = {overview: overviewNode, messages: messagesNode, history: historyNode, cases: casesNode, notes: notesNode, accounts: accountsNode};
  const renderer = views[state.view] || overviewNode;
  replaceChildrenOf($('#workspaceContent'), renderer());
  bindViewEvents();
}

function pageHeading(eyebrow, title, detail, actions = null) {
  return element('div', {className: 'page-heading'},
    element('div', {}, element('div', {className: 'eyebrow', text: eyebrow}), element('h2', {text: title}), element('p', {text: detail})),
    actions);
}

function overviewNode() {
  const recentHistory = state.history.slice(0, 3).map(historyCompactNode);
  const historyCard = element('section', {className: 'card'},
    sectionHeading('Recent moderation history', buttonNode('View all', 'text-button', {viewLink: 'history'})),
    recentHistory.length ? recentHistory : emptyState('No moderation history'));
  const investigationCard = element('section', {className: 'card'},
    sectionHeading('Investigation', buttonNode('Open messages', 'text-button', {viewLink: 'messages'})),
    summaryList([
      ['Selected messages', state.selected.size], ['Evidence', state.evidence.size], ['Delete on confirm', state.deleting.size]
    ]), buttonNode('Issue Punishment', 'button primary full', {punish: ''}));
  return element('div', {},
    pageHeading('Player overview', 'Moderation context', 'Relevant account state, recent history and current investigation activity.'),
    element('div', {className: 'metric-grid'},
      metricNode('Active sanctions', '1', 'Discord mute', 'warning'),
      metricNode('Total history', state.history.length, 'Across all offense families'),
      metricNode('Evidence selected', state.evidence.size, 'Messages attached to this case')),
    element('div', {className: 'two-column'}, historyCard, investigationCard));
}

function metricNode(label, value, detail, tone = '') {
  return element('div', {className: `metric-card ${tone}`.trim()},
    element('span', {text: label}), element('strong', {text: value}), element('small', {text: detail}));
}

function historyCompactNode(row) {
  return element('div', {className: 'compact-row'},
    element('div', {}, element('strong', {text: row.offense}), element('span', {text: `${formatDate(row.date)} · ${row.staff}`})),
    element('div', {className: 'right'}, element('strong', {text: row.action}), element('span', {text: row.duration})));
}

function summaryList(rows) {
  return element('div', {className: 'summary-list'}, rows.map(([label, value]) =>
    element('div', {className: 'summary-row'}, element('span', {text: label}), element('strong', {text: value}))));
}

function messagesNode() {
  const messages = filteredMessages();
  const actions = element('div', {className: 'page-actions'}, buttonNode('Issue Punishment', 'button primary', {punish: ''}));
  const content = [pageHeading('Message investigation', 'Messages & evidence',
    'Inspect exact conversation context, then independently choose evidence and deletion.', actions)];
  if (state.contextId) content.push(contextAlertNode());
  content.push(filtersNode());
  content.push(element('section', {className: 'message-investigation'}, messages.length
    ? groupedMessagesNodes(messages)
    : emptyState('No messages match these filters', 'Try a different date, channel, or search term.')));
  return element('div', {}, content);
}

function contextAlertNode() {
  return element('div', {className: 'alert info'},
    element('strong', {text: 'Conversation context'}),
    element('span', {text: 'Showing the triggering message with nearby messages before and after it.'}),
    buttonNode('Exit context', 'text-button', {exitContext: ''}));
}

function filtersNode() {
  const search = element('input', {id: 'messageSearch', type: 'search', placeholder: 'Search message text', value: state.search});
  const channel = element('select', {id: 'channelFilter'},
    optionNode('all', 'All channels', state.channel === 'all'),
    optionNode('general', '#general', state.channel === 'general'),
    optionNode('market', '#market', state.channel === 'market'));
  const date = element('select', {id: 'dateFilter'},
    optionNode('all', 'All dates', state.date === 'all'),
    optionNode('2026-08-29', 'Aug 29', state.date === '2026-08-29'),
    optionNode('2026-08-28', 'Aug 28', state.date === '2026-08-28'),
    optionNode('2026-08-24', 'Aug 24', state.date === '2026-08-24'));
  const selected = element('input', {id: 'selectedFilter', type: 'checkbox', checked: state.selectedOnly});
  return element('section', {className: 'card filters-card'}, element('div', {className: 'filter-row'},
    element('label', {className: 'search-field'}, element('span', {className: 'sr-only', text: 'Search messages'}), search),
    labeledControl('Channel', channel), labeledControl('Date', date),
    element('label', {className: 'checkbox-control'}, selected, ' Selected only')));
}

function labeledControl(label, control) {
  return element('label', {}, element('span', {className: 'sr-only', text: label}), control);
}

function filteredMessages() {
  const term = state.search.trim().toLowerCase();
  const contextIds = state.contextId ? contextMessageIds(state.contextId) : null;
  return baseMessages.filter((message) => messageMatchesFilters(message, term, contextIds));
}

function messageMatchesFilters(message, term, contextIds) {
  if (contextIds && !contextIds.has(message.id)) return false;
  if (term && !messageSearchText(message).includes(term)) return false;
  if (state.channel !== 'all' && message.channel !== state.channel) return false;
  if (state.date !== 'all' && messageDateKey(message.time) !== state.date) return false;
  return !state.selectedOnly || state.selected.has(message.id);
}

function messageSearchText(message) {
  return `${message.text} ${message.author} ${message.channel}`.toLowerCase();
}

function messageDateKey(iso) {
  return displayDateKey(iso);
}

function groupedMessagesNodes(messages) {
  let lastGroup = '';
  const nodes = [];
  for (const message of messages) {
    const group = `${messageDateKey(message.time)}|${message.channel}`;
    if (group !== lastGroup) nodes.push(groupHeaderNode(message));
    nodes.push(messageNode(message));
    lastGroup = group;
  }
  return nodes;
}

function groupHeaderNode(message) {
  return element('div', {className: 'conversation-divider'},
    element('span', {text: dateHeading(message.time)}), element('span', {text: `#${message.channel}`}));
}

function messageNode(message) {
  const selected = state.selected.has(message.id);
  const classes = ['message-row', selected ? 'selected' : '', message.deleted ? 'deleted' : ''].filter(Boolean).join(' ');
  const checkbox = element('input', {type: 'checkbox', className: 'message-select', checked: selected,
    attrs: {'aria-label': `Select message from ${message.author} at ${formatExact(message.time)}`}});
  const avatar = element('div', {className: `message-avatar${message.target ? ' target' : ''}`, text: message.initials, attrs: {'aria-hidden': 'true'}});
  const body = messageBodyNode(message);
  const context = buttonNode('•••', 'icon-button', {contextMessage: message.id});
  context.setAttribute('aria-label', `Inspect surrounding context for message ${message.id}`);
  return element('article', {className: classes, dataset: {messageId: message.id}}, element('div', {}, checkbox), avatar, body, context);
}

function messageBodyNode(message) {
  const body = element('div', {className: 'message-body'});
  if (message.replyTo) body.append(element('div', {className: 'reply-reference', text: `↳ Replying to message ${message.replyTo}`}));
  body.append(messageMetaNode(message));
  body.append(element('div', {className: 'message-text'}, message.deleted
    ? element('em', {text: 'Message deleted in source context'}) : message.text));
  if (message.attachment) body.append(attachmentNode(message.attachment));
  body.append(messageStatusNodes(message));
  body.append(element('div', {className: 'message-id', text: `Message ID ${message.id}`}));
  return body;
}

function messageMetaNode(message) {
  const meta = element('div', {className: 'message-meta'}, element('strong', {text: message.author}));
  if (message.target) meta.append(element('span', {className: 'target-chip', text: 'Target'}));
  meta.append(element('span', {text: `@${message.username}`}));
  meta.append(element('time', {text: formatExact(message.time), attrs: {datetime: message.time}}));
  if (message.edited) meta.append(element('span', {text: '(edited)'}));
  return meta;
}

function attachmentNode(attachment) {
  return element('div', {className: 'attachment-card'},
    element('div', {className: 'attachment-icon', text: 'IMG'}),
    element('div', {}, element('strong', {text: attachment.name}), element('span', {text: attachment.detail})));
}

function messageStatusNodes(message) {
  const status = element('div', {className: 'message-statuses'});
  if (state.evidence.has(message.id)) status.append(messagePill('Evidence', 'evidence'));
  if (state.violating.has(message.id)) status.append(messagePill('Violating', 'violating'));
  if (state.deleting.has(message.id)) status.append(messagePill('Delete on confirm', 'delete'));
  return status;
}

function messagePill(text, tone) {
  return element('span', {className: `message-pill ${tone}`, text});
}

function renderSelectionBar() {
  const bar = $('#selectionBar');
  if (state.selected.size === 0) {
    bar.hidden = true;
    bar.replaceChildren();
    return;
  }
  bar.hidden = false;
  const summary = element('div', {className: 'selection-summary'},
    element('strong', {text: `${state.selected.size} selected`}),
    element('span', {text: `${state.evidence.size} evidence · ${state.deleting.size} delete on confirm`}));
  const actions = element('div', {className: 'selection-actions'},
    selectionButton(allSelectedIn(state.evidence) ? 'Remove Evidence' : 'Add to Evidence', 'secondary', 'evidence'),
    selectionButton(allSelectedIn(state.violating) ? 'Clear Violating' : 'Mark Violating', 'secondary', 'violating'),
    selectionButton(allSelectedIn(state.deleting) ? 'Preserve Messages' : 'Delete on Confirm', 'danger-secondary', 'delete'),
    selectionButton('Remove Selection', 'ghost', 'clear'));
  replaceChildrenOf(bar, summary, actions);
  $$('[data-selection-action]').forEach((button) => button.addEventListener('click', () => selectionAction(button.dataset.selectionAction)));
}

function selectionButton(text, tone, action) {
  return buttonNode(text, `button ${tone}`, {selectionAction: action});
}

function allSelectedIn(set) {
  return state.selected.size > 0 && [...state.selected].every((id) => set.has(id));
}

function selectionAction(action) {
  const ids = [...state.selected];
  if (action === 'evidence') toggleGroup(state.evidence, ids);
  if (action === 'violating') toggleGroup(state.violating, ids);
  if (action === 'delete') toggleGroup(state.deleting, ids);
  if (action === 'clear') {
    state.selected.clear();
    state.anchor = null;
  } else {
    state.evidenceRevision++;
  }
  renderAll();
}

function toggleGroup(set, ids) {
  const remove = ids.every((id) => set.has(id));
  ids.forEach((id) => toggleSet(set, id, !remove));
}

function bindViewEvents() {
  $$('[data-view-link]').forEach((button) => button.addEventListener('click', () => switchView(button.dataset.viewLink)));
  $$('[data-punish]').forEach((button) => button.addEventListener('click', openWorkflow));
  if (state.view === 'messages') bindMessageEvents();
}

function bindMessageEvents() {
  $('[data-exit-context]')?.addEventListener('click', () => { state.contextId = null; renderWorkspace(); });
  $('#messageSearch').addEventListener('input', (event) => { state.search = event.target.value; renderWorkspace(); });
  $('#channelFilter').addEventListener('change', (event) => { state.channel = event.target.value; renderWorkspace(); });
  $('#dateFilter').addEventListener('change', (event) => { state.date = event.target.value; renderWorkspace(); });
  $('#selectedFilter').addEventListener('change', (event) => { state.selectedOnly = event.target.checked; renderWorkspace(); });
  $$('.message-select').forEach((checkbox) => checkbox.addEventListener('click', selectMessage));
  $$('[data-context-message]').forEach((button) => button.addEventListener('click', () => showContext(button.dataset.contextMessage)));
}

function selectMessage(event) {
  const row = event.target.closest('[data-message-id]');
  const id = row.dataset.messageId;
  if (event.shiftKey && state.anchor) selectRange(state.anchor, id, event.target.checked);
  else toggleSet(state.selected, id, event.target.checked);
  state.anchor = id;
  renderAll();
}

function selectRange(fromId, toId, checked) {
  const ids = filteredMessages().map((message) => message.id);
  const from = ids.indexOf(fromId);
  const to = ids.indexOf(toId);
  if (from < 0 || to < 0) {
    toggleSet(state.selected, toId, checked);
    return;
  }
  ids.slice(Math.min(from, to), Math.max(from, to) + 1).forEach((id) => toggleSet(state.selected, id, checked));
}

function toggleSet(set, id, enabled) {
  if (enabled) set.add(id);
  else set.delete(id);
}

function showContext(id) {
  if (!baseMessages.some((message) => message.id === id)) return;
  state.search = '';
  state.channel = 'all';
  state.date = 'all';
  state.selectedOnly = false;
  state.contextId = id;
  showToast(`Showing conversation context around message ${id}.`);
  renderAll();
}

function contextMessageIds(id) {
  const index = baseMessages.findIndex((message) => message.id === id);
  if (index < 0) return new Set();
  const trigger = baseMessages[index];
  return new Set(baseMessages.filter((message, candidate) =>
    message.channel === trigger.channel && Math.abs(candidate - index) <= 2).map((message) => message.id));
}

function historyNode() {
  const table = element('table', {}, historyTableHead(), historyTableBody());
  return element('div', {},
    pageHeading('Moderation record', 'History', 'Total history remains visible; the punishment workflow separately identifies only ladder-relevant records for the selected offense.'),
    element('section', {className: 'card table-card'}, element('div', {className: 'responsive-table'}, table)));
}

function historyTableHead() {
  return element('thead', {}, element('tr', {}, ['Date', 'Offense', 'Action', 'Duration', 'Staff', 'Status'].map((label) => element('th', {text: label}))));
}

function historyTableBody() {
  return element('tbody', {}, state.history.map((row) => element('tr', {},
    element('td', {text: formatDate(row.date)}), element('td', {text: row.offense}),
    element('td', {}, element('strong', {text: row.action})), element('td', {text: row.duration}),
    element('td', {text: row.staff}), element('td', {}, statusBadge(row.status, 'neutral')))));
}

const CASE_ROWS = [
  ['CASE-1187', 'Aug 29, 2026', 'Spam pattern review', 'Open', 'Morgan', 'Pending punishment'],
  ['CASE-1044', 'Aug 20, 2026', 'Repeated flooding', 'Closed', 'Sam', 'Warning'],
  ['CASE-0911', 'Jun 17, 2026', 'Harassment review', 'Closed', 'Avery', 'Warning']
];

function casesNode() {
  return element('div', {}, pageHeading('Case management', 'Cases', 'Current and historical investigations connected to this player.'),
    element('div', {className: 'card-list'}, CASE_ROWS.map(caseCardNode)));
}

function caseCardNode(row) {
  return element('section', {className: 'card case-card'},
    element('div', {}, element('span', {className: 'eyebrow', text: row[0]}), element('h3', {text: row[2]}), element('p', {text: `${row[1]} · ${row[4]}`})),
    element('div', {className: 'case-status'}, statusBadge(row[3], row[3] === 'Open' ? 'warning' : 'neutral'), element('strong', {text: row[5]})));
}

const NOTE_ROWS = [
  ['Aug 29, 2026 · 5:48 PM', 'Morgan', 'Investigating repeated promotional flooding across #general and #market.'],
  ['Aug 20, 2026 · 8:14 PM', 'Avery', 'Player acknowledged the prior warning and was cooperative.'],
  ['Jun 17, 2026 · 3:02 PM', 'Sam', 'Initial behavior note; no account-link concerns observed.']
];

function notesNode() {
  return element('div', {}, pageHeading('Staff context', 'Notes', 'Concise internal notes attached to the player record.'),
    element('div', {className: 'timeline'}, NOTE_ROWS.map(noteNode)));
}

function noteNode(note) {
  return element('article', {className: 'timeline-item'}, element('div', {className: 'timeline-dot'}),
    element('div', {className: 'card'},
      element('div', {className: 'section-heading'}, element('strong', {text: note[1]}), element('time', {text: note[0]})),
      element('p', {text: note[2]})));
}

function accountsNode() {
  return element('div', {}, pageHeading('Identity graph', 'Accounts', 'Discord and Minecraft identities associated with this moderation target.'),
    element('div', {className: 'account-grid'},
      accountCard('Discord identity', identity.displayName, `@${identity.username}`, [['Discord ID', identity.discordId], ['Link status', 'Verified']]),
      accountCard('Minecraft main', identity.minecraft, 'Java Edition · Main account', [['UUID', identity.minecraftUuid], ['Link status', 'Verified']]),
      accountCard('Linked alt', identity.alts[0].name, 'Bedrock Edition', [['Relationship', 'Linked alt'], ['Status', 'Active link']])));
}

function accountCard(eyebrow, title, detail, rows) {
  return element('section', {className: 'card'}, element('span', {className: 'eyebrow', text: eyebrow}), element('h3', {text: title}),
    element('p', {text: detail}), element('dl', {className: 'detail-list'}, rows.map(([label, value]) =>
      element('div', {}, element('dt', {text: label}), element('dd', {text: value})))));
}
