'use strict';

const liveModeration = {
  bootstrap: null,
  cases: [],
  notes: [],
  sanctions: [],
  accounts: [],
  channels: [],
  warning: '',
  olderCursor: null,
  newerCursor: null
};

function fillScenarioSelect() {
  const control = $('.scenario-control');
  if (control) control.hidden = true;
}

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
  renderAll();
}

async function loadSession() {
  try {
    const sessionResponse = await fetch('/api/session', {headers:{Accept:'application/json'}});
    if (!sessionResponse.ok) throw new Error('Session unavailable');
    state.session = await sessionResponse.json();
    const bootstrapResponse = await fetch('/api/bootstrap', {headers:{Accept:'application/json'}});
    const payload = await readJsonResponse(bootstrapResponse);
    if (!bootstrapResponse.ok) throw new Error(payload.message || 'Moderation data unavailable');
    applyLiveBootstrap(payload);
    $('#actorMeta').textContent = payload.actor?.displayName || 'Verified staff session';
  } catch (error) {
    clearLiveData();
    $('#actorMeta').textContent = 'Read data unavailable';
    showToast(error.message || 'Moderation data is temporarily unavailable.', true);
    renderAll();
  }
}

async function readJsonResponse(response) {
  try {
    return await response.json();
  } catch {
    return {message:'Moderation data is temporarily unavailable.'};
  }
}

function asArray(value) {
    return Array.isArray(value) ? value : [];
}

function firstText(...values) {
    for (const value of values) {
        if (typeof value === 'string' && value.length > 0) return value;
    }
    return '';
}

function optionalText(value) {
    return typeof value === 'string' ? value : '';
}

function nullableText(value) {
    const text = optionalText(value);
    return text.length > 0 ? text : null;
}

function applyLiveBootstrap(payload) {
    liveModeration.bootstrap = payload;
    applyLiveCollections(payload);
    applyIdentity(payload.identity ?? {});
    applyRestrictionTargets();
    state.history = asArray(payload.history).map(mapHistoryRow);
    replaceMessagePage(payload.messages ?? {});
    state.contextId = nullableText(payload.centeredMessageId);
    renderAll();
}

function applyLiveCollections(payload) {
    liveModeration.cases = asArray(payload.cases);
    liveModeration.notes = asArray(payload.notes);
    liveModeration.sanctions = asArray(payload.activeSanctions);
    liveModeration.accounts = asArray(payload.linkedAccounts);
    liveModeration.channels = asArray(payload.channels);
}

function clearLiveData() {
  liveModeration.bootstrap = null;
  liveModeration.cases = [];
  liveModeration.notes = [];
  liveModeration.sanctions = [];
  liveModeration.accounts = [];
  liveModeration.channels = [];
  liveModeration.warning = 'Moderation data is unavailable.';
  baseMessages.splice(0);
  state.history = [];
  Object.assign(identity, {
    displayName:'Data unavailable', username:'unavailable', discordId:'Unavailable', minecraft:'Unavailable',
    minecraftUuid:'Unavailable', alts:[], status:'Unavailable', statusDetail:'Read source unavailable', avatarUrl:''
  });
  RESTRICTION_TARGETS.splice(0);
}

function applyIdentity(source) {
    const main = liveModeration.accounts.find((account) => account.main);
    const alts = liveModeration.accounts.filter((account) => !account.main).map(mapLinkedAlt);
    Object.assign(identity, {
        displayName: firstText(source.displayName, source.serverName, source.globalName, source.username, source.discordId, 'Unknown Discord user'),
        username: firstText(source.username, 'unknown'),
        discordId: firstText(source.discordId, 'Unavailable'),
        minecraft: firstText(source.minecraftMain, linkedAccountName(main), 'No linked Minecraft main'),
        minecraftUuid: firstText(main?.playerId, 'Unavailable'), alts,
        status: firstText(source.targetStatus, 'Unknown'), statusDetail: sanctionStatusDetail(),
        avatarUrl: optionalText(source.avatarUrl), linkState: firstText(source.linkState, 'Unknown'),
        globalName: optionalText(source.globalName), serverName: optionalText(source.serverName)
    });
}

function mapLinkedAlt(account) {
    return {name:firstText(account.username, account.playerId), platform:friendlyPlatform(account.platform), status:'Linked', playerId:account.playerId};
}

function linkedAccountName(account) {
    return account ? firstText(account.username, account.playerId) : '';
}

function sanctionStatusDetail() {
    const count = liveModeration.sanctions.length;
    return count > 0 ? `${count} active` : 'No active sanctions';
}

function friendlyPlatform(value) {
  if (!value) return 'Unknown';
  return String(value).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function applyRestrictionTargets() {
  RESTRICTION_TARGETS.splice(0);
  const categories = new Map();
  for (const channel of liveModeration.channels) {
    RESTRICTION_TARGETS.push({id:channel.id, type:'channel', label:`#${channel.name}`, detail:channel.categoryName || 'Uncategorized'});
    if (channel.categoryId && channel.categoryName) {
      const current = categories.get(channel.categoryId) || {id:channel.categoryId, name:channel.categoryName, count:0};
      current.count += 1;
      categories.set(channel.categoryId, current);
    }
  }
  for (const category of categories.values()) {
    RESTRICTION_TARGETS.push({id:category.id, type:'category', label:category.name, detail:`Category · ${category.count} channel${category.count === 1 ? '' : 's'}`});
  }
}

function replaceMessagePage(page) {
  baseMessages.splice(0, baseMessages.length, ...(Array.isArray(page.messages) ? page.messages.map(mapMessage) : []));
  liveModeration.olderCursor = page.olderCursor || null;
  liveModeration.newerCursor = page.newerCursor || null;
  liveModeration.warning = page.warning || '';
}

function appendMessagePage(page, direction) {
  const incoming = Array.isArray(page.messages) ? page.messages.map(mapMessage) : [];
  const known = new Set(baseMessages.map((message) => message.id));
  const unique = incoming.filter((message) => !known.has(message.id));
  if (direction === 'older') baseMessages.push(...unique);
  else baseMessages.unshift(...unique);
  baseMessages.sort((left, right) => new Date(right.time) - new Date(left.time));
  liveModeration.olderCursor = page.olderCursor || liveModeration.olderCursor;
  liveModeration.newerCursor = page.newerCursor || liveModeration.newerCursor;
  liveModeration.warning = page.warning || '';
}

function mapMessage(message) {
    const author = message.author ?? {};
    const authorName = firstText(author.displayName, author.username, author.discordId, 'Unknown author');
    return {
        id:message.id, author:authorName, username:firstText(author.username, 'unknown'), initials:initials(authorName),
        target:Boolean(message.targetAuthor), channel:firstText(message.channelName, message.channelId), channelId:message.channelId,
        category:optionalText(message.categoryName), time:message.createdAt, text:optionalText(message.content),
        edited:Boolean(message.editedAt), editedAt:nullableText(message.editedAt), attachments:asArray(message.attachments).map(mapAttachment),
        replyTo:nullableText(message.replyToMessageId), deleted:Boolean(message.deletedKnown), authorId:optionalText(author.discordId)
    };
}

function mapAttachment(attachment) {
    return {name:firstText(attachment.fileName, 'Attachment'), detail:[firstText(attachment.contentType, 'Attachment'), formatBytes(attachment.size)].filter(Boolean).join(' · '), url:optionalText(attachment.url)};
}

function initials(value) {
    const text = String(value ?? '').trim();
    if (!text) return '?';
    return text.split(/\s+/).slice(0, 2).map((part) => part.charAt(0)).join('').toUpperCase();
}

function formatBytes(value) {
  if (!Number.isFinite(Number(value))) return '';
  const bytes = Number(value);
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function renderTargetHeader() {
  const header = $('#targetHeader');
  const identityLine = element('div', {className:'identity-line'},
    element('h1', {id:'targetName', text:identity.displayName}), statusBadge(identity.status, liveModeration.sanctions.length ? 'warning' : 'neutral'),
    statusBadge(identity.linkState || 'Unknown link state', 'neutral'));
  const technical = element('details', {className:'technical-meta'}, element('summary', {text:'Technical IDs'}),
    element('div', {text:`Discord ${identity.discordId} · Minecraft ${identity.minecraftUuid}`}));
  const targetIdentity = element('div', {className:'target-identity'}, identityLine,
    element('div', {className:'target-subline', text:`@${identity.username} • ${identity.minecraft} • ${identity.alts.length} linked alt${identity.alts.length === 1 ? '' : 's'}`}), technical);
  const actions = element('div', {className:'target-actions'}, buttonNode('Review messages', 'button secondary', {openMessages:''}), buttonNode('Issue Punishment', 'button primary', {punish:''}));
  const avatar = identity.avatarUrl ? document.createElement('img') : element('div', {className:'target-avatar', text:initials(identity.displayName), attrs:{'aria-hidden':'true'}});
  if (avatar instanceof HTMLImageElement) {
    avatar.className = 'target-avatar';
    avatar.src = identity.avatarUrl;
    avatar.alt = `${identity.displayName} avatar`;
    avatar.referrerPolicy = 'no-referrer';
  }
  replaceChildrenOf(header, avatar, targetIdentity, actions);
  $('[data-open-messages]')?.addEventListener('click', () => switchView('messages'));
  $('[data-punish]')?.addEventListener('click', openWorkflow);
}

function renderContextPanel() {
  const sanction = liveModeration.sanctions[0];
  const latestCase = liveModeration.cases[0];
  const latestNote = liveModeration.notes[0];
  replaceChildrenOf($('#contextPanel'),
    contextAccountsSection(),
    contextSection('Current sanctions', sanction ? statusBadge('Active', 'warning') : null,
      sanction ? friendlyPlatform(sanction.type) : 'None', sanction ? sanction.reason : 'No active authoritative sanctions.'),
    latestCase ? contextLinkedSection('Latest case', 'cases', latestCase.caseId, `${latestCase.reason} · ${latestCase.actorName}`)
      : contextSection('Latest case', null, 'None', 'No authoritative case was returned.'),
    latestNote ? element('div', {className:'context-section'}, sectionHeading('Latest staff note', buttonNode('View', 'text-button', {contextView:'notes'})),
      element('p', {className:'compact-copy', text:latestNote.text}), element('div', {className:'muted tiny', text:formatExact(latestNote.createdAt)}))
      : contextSection('Latest staff note', null, 'None', 'No authoritative private note was returned.'),
    contextSection('Case readiness', null, null, 'Review the offense, evidence, recommendation, and any approval requirement before confirmation.'));
  $$('[data-context-view]').forEach((button) => button.addEventListener('click', () => switchView(button.dataset.contextView)));
}

function contextAccountsSection() {
  const content = [sectionHeading('Linked accounts', buttonNode('View', 'text-button', {contextView:'accounts'}))];
  if (!liveModeration.accounts.length) content.push(element('div', {className:'muted small', text:'No linked Minecraft accounts returned.'}));
  else liveModeration.accounts.forEach((account) => content.push(accountLine(account.username || account.playerId, `${account.main ? 'Main · ' : ''}${friendlyPlatform(account.platform)}`)));
  return element('div', {className:'context-section'}, content);
}

function renderCounts() {
  const counts = {messages:baseMessages.length, history:state.history.length, cases:liveModeration.cases.length, notes:liveModeration.notes.length, accounts:liveModeration.accounts.length + 1};
  for (const [key, value] of Object.entries(counts)) {
    const target = $(`[data-count="${key}"]`);
    if (target) target.textContent = value;
  }
}

function overviewNode() {
  const recentHistory = state.history.slice(0, 3).map(historyCompactNode);
  return element('div', {}, pageHeading('Player overview', 'Moderation context', 'Authoritative account state, recent history and current investigation activity.'),
    element('div', {className:'metric-grid'}, metricNode('Active sanctions', liveModeration.sanctions.length, liveModeration.sanctions.length ? 'Authoritative active records' : 'None'),
      metricNode('Total history', state.history.length, 'Bounded authoritative history'), metricNode('Evidence selected', state.evidence.size, 'Messages attached to this simulated review')),
    element('div', {className:'two-column'},
      element('section', {className:'card'}, sectionHeading('Recent moderation history', buttonNode('View all', 'text-button', {viewLink:'history'})), recentHistory.length ? recentHistory : emptyState('No moderation history')),
      element('section', {className:'card'}, sectionHeading('Investigation', buttonNode('Open messages', 'text-button', {viewLink:'messages'})),
        summaryList([['Selected messages',state.selected.size],['Evidence',state.evidence.size],['Delete on confirm',state.deleting.size]]), buttonNode('Issue Punishment','button primary full',{punish:''}))));
}

function filtersNode() {
    return element('section', {className:'card filters-card'}, element('div', {className:'filter-row'},
        labeledControl('Search messages', searchFilterNode()), labeledControl('Author', authorFilterNode()),
        labeledControl('Channel', channelFilterNode()), labeledControl('Date', dateFilterNode()), selectedFilterNode()));
}

function searchFilterNode() { return element('input', {id:'messageSearch', type:'search', placeholder:'Search loaded message text', value:state.search}); }
function authorFilterNode() { return element('input', {id:'authorFilter', type:'search', placeholder:'Author ID or name', value:state.author ?? ''}); }
function channelFilterNode() { return element('select', {id:'channelFilter'}, optionNode('all','All loaded channels',state.channel === 'all'), liveModeration.channels.map(channelFilterOption)); }
function channelFilterOption(item) { const category = item.categoryName ? ` · ${item.categoryName}` : ''; return optionNode(item.id, `#${item.name}${category}`, state.channel === item.id); }
function dateFilterNode() { return element('input', {id:'dateFilter', type:'date', value:state.date === 'all' ? '' : state.date}); }
function selectedFilterNode() { const selected = element('input', {id:'selectedFilter', type:'checkbox', checked:state.selectedOnly}); return element('label', {className:'checkbox-control'}, selected, ' Selected only'); }

function matchesChannel(message) {
  return state.channel === 'all' || message.channelId === state.channel;
}

function matchesDate(message) {
  return state.date === 'all' || state.date === '' || messageDateKey(message.time) === state.date;
}

function messageMatchesFilters(message, term, contextIds) {
  const authorTerm = String(state.author || '').trim().toLowerCase();
  return [matchesContext(message, contextIds), matchesSearchTerm(message, term), matchesChannel(message), matchesDate(message),
    !authorTerm || message.authorId === authorTerm || `${message.author} ${message.username}`.toLowerCase().includes(authorTerm), matchesSelection(message)].every(Boolean);
}

function messagesNode() {
  const messages = filteredMessages();
  const actions = element('div', {className:'page-actions'}, buttonNode('Issue Punishment', 'button primary', {punish:''}));
  const content = [pageHeading('Message investigation','Messages & evidence','Real Discord messages returned through bounded read-only access. Evidence, violating, and future deletion selections remain independent.',actions)];
  if (state.contextId) content.push(contextAlertNode());
  if (liveModeration.warning) content.push(element('div', {className:'alert info'}, element('strong',{text:'Discord read notice'}), element('span',{text:liveModeration.warning})));
  content.push(filtersNode());
  content.push(element('section', {className:'message-investigation'}, messages.length ? groupedMessagesNodes(messages) : emptyState('No messages match these filters','Discord may have returned no content, or the bounded page may not contain a match.')));
  if (state.channel !== 'all') {
    content.push(element('div', {className:'page-actions'},
      buttonNode('Load newer', 'button secondary', {loadDirection:'newer'}), buttonNode('Load older', 'button secondary', {loadDirection:'older'})));
  }
  return element('div', {}, content);
}

function bindMessageEvents() {
  $('[data-exit-context]')?.addEventListener('click', () => { state.contextId = null; renderWorkspace(); });
  $('#messageSearch')?.addEventListener('input', (event) => { state.search = event.target.value; renderWorkspace(); });
  $('#authorFilter')?.addEventListener('input', (event) => { state.author = event.target.value; renderWorkspace(); });
  $('#channelFilter')?.addEventListener('change', (event) => { state.channel = event.target.value; state.contextId = null; loadChannelPage(); });
  $('#dateFilter')?.addEventListener('change', (event) => { state.date = event.target.value || 'all'; renderWorkspace(); });
  $('#selectedFilter')?.addEventListener('change', (event) => { state.selectedOnly = event.target.checked; renderWorkspace(); });
  $$('.message-select').forEach((checkbox) => checkbox.addEventListener('click', selectMessage));
  $$('[data-context-message]').forEach((button) => button.addEventListener('click', () => showContext(button.dataset.contextMessage)));
  $$('[data-load-direction]').forEach((button) => button.addEventListener('click', () => loadMoreMessages(button.dataset.loadDirection)));
}

async function loadChannelPage() {
  if (state.channel === 'all') {
    const page = liveModeration.bootstrap?.messages || {messages:[]};
    replaceMessagePage(page);
    renderAll();
    return;
  }
  await loadMessageRequest(new URLSearchParams({channel:state.channel, limit:'25'}), 'replace');
}

async function loadMoreMessages(direction) {
  if (state.channel === 'all') return;
  const cursor = direction === 'older' ? liveModeration.olderCursor : liveModeration.newerCursor;
  if (!cursor) {
    showToast(`No ${direction} cursor is available.`);
    return;
  }
  const params = new URLSearchParams({channel:state.channel, limit:'25'});
  params.set(direction === 'older' ? 'before' : 'after', cursor);
  await loadMessageRequest(params, direction);
}

async function loadMessageRequest(params, mode) {
  try {
    const response = await fetch('/api/messages', {method:'POST', headers:{Accept:'application/json', 'Content-Type':'application/json'}, body:JSON.stringify(Object.fromEntries(params.entries()))});
    const page = await readJsonResponse(response);
    if (!response.ok) throw new Error(page.message || 'Discord messages unavailable');
    if (mode === 'replace') replaceMessagePage(page);
    else appendMessagePage(page, mode);
    renderAll();
  } catch (error) {
    showToast(error.message || 'Discord messages are temporarily unavailable.', true);
  }
}

function messageBodyNode(message) {
  const body = element('div', {className:'message-body'});
  if (message.replyTo) body.append(element('div', {className:'reply-reference', text:`↳ Replying to message ${message.replyTo}`}));
  body.append(messageMetaNode(message));
  if (message.deleted) body.append(element('div', {className:'message-text'}, element('em', {text:'Message is known deleted in authoritative source state'})));
  else if (message.text) body.append(element('div', {className:'message-text', text:message.text}));
  else body.append(element('div', {className:'message-text'}, element('em', {text:'Text content unavailable from Discord'})));
  for (const attachment of message.attachments || []) body.append(attachmentNode(attachment));
  body.append(messageStatusNodes(message));
  body.append(element('div', {className:'message-id', text:`Message ID ${message.id}`}));
  return body;
}

function casesNode() {
  const cards = liveModeration.cases.map((row) => element('section', {className:'card case-card'},
    element('div', {}, element('span',{className:'eyebrow',text:row.caseId}), element('h3',{text:row.reason}), element('p',{text:`${formatExact(row.issuedAt)} · ${row.actorName}`})),
    element('div',{className:'case-status'}, statusBadge(row.state || 'Recorded', row.state === 'OPEN' ? 'warning' : 'neutral'), element('strong',{text:row.sanctionFamily || row.exactReasonId || 'Case'}))));
  return element('div', {}, pageHeading('Case management','Cases','Authoritative cases currently available for this target.'), element('div',{className:'card-list'}, cards.length ? cards : emptyState('No cases returned')));
}

function notesNode() {
  const notes = liveModeration.notes.map((note) => element('article',{className:'timeline-item'}, element('div',{className:'timeline-dot'}),
    element('div',{className:'card'}, element('div',{className:'section-heading'}, element('strong',{text:`Staff ${note.actorId}`}), element('time',{text:formatExact(note.createdAt)})), element('p',{text:note.text}))));
  return element('div', {}, pageHeading('Staff context','Notes','Authoritative private staff notes available to this actor.'), element('div',{className:'timeline'}, notes.length ? notes : emptyState('No notes returned')));
}

function accountsNode() {
  const cards = [accountCard('Discord identity', identity.displayName, `@${identity.username}`,[['Discord ID',identity.discordId],['Link state',identity.linkState || 'Unknown']])];
  for (const account of liveModeration.accounts) {
    cards.push(accountCard(account.main ? 'Minecraft main' : 'Linked Minecraft account', account.username || account.playerId,
      `${friendlyPlatform(account.platform)}${account.main ? ' · Main account' : ''}`,[['UUID',account.playerId],['Relationship',account.main ? 'Main' : 'Linked account']]));
  }
  return element('div', {}, pageHeading('Identity graph','Accounts','Discord and Minecraft identities returned by the authoritative link service.'), element('div',{className:'account-grid'},cards));
}
