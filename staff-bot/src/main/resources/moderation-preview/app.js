'use strict';
function renderAll() {
  renderTargetHeader(); renderContextPanel(); renderCounts(); renderWorkspace(); renderSelectionBar();
  $$('.nav-item').forEach((button) => button.classList.toggle('active', button.dataset.view === state.view));
}

function renderTargetHeader() {
  $('#targetHeader').innerHTML = `
    <div class="target-avatar" aria-hidden="true">RA</div>
    <div class="target-identity">
      <div class="identity-line"><h1 id="targetName">${identity.displayName}</h1><span class="status-badge warning">${identity.status}</span><span class="status-badge neutral">Java + Bedrock linked</span></div>
      <div class="target-subline">@${identity.username} <span>•</span> ${identity.minecraft} <span>•</span> ${identity.alts.length} linked alt</div>
      <details class="technical-meta"><summary>Technical IDs</summary><div>Discord ${identity.discordId} · Minecraft ${identity.minecraftUuid}</div></details>
    </div>
    <div class="target-actions"><button class="button secondary" type="button" data-open-messages>Review messages</button><button class="button primary" type="button" data-punish>Issue Punishment</button></div>`;
  $('[data-open-messages]').addEventListener('click', () => switchView('messages'));
  $('[data-punish]').addEventListener('click', openWorkflow);
}

function renderContextPanel() {
  const active = identity.status === 'Discord mute' ? `<span class="status-badge warning">Active</span>` : '';
  $('#contextPanel').innerHTML = `
    <div class="context-section"><div class="section-heading"><h3>Linked accounts</h3><button class="text-button" data-context-view="accounts">View</button></div><div class="account-line"><strong>${identity.minecraft}</strong><span>Main · Java</span></div><div class="account-line"><strong>${identity.alts[0].name}</strong><span>${identity.alts[0].platform} · linked alt</span></div></div>
    <div class="context-section"><div class="section-heading"><h3>Current sanctions</h3>${active}</div><div class="context-value">Discord mute</div><div class="muted small">1h 18m remaining · chat only</div></div>
    <div class="context-section"><div class="section-heading"><h3>Open case</h3><button class="text-button" data-context-view="cases">View</button></div><div class="context-value">CASE-1187</div><div class="muted small">Spam pattern review · Morgan</div></div>
    <div class="context-section"><div class="section-heading"><h3>Latest staff note</h3><button class="text-button" data-context-view="notes">View</button></div><p class="compact-copy">Repeated flood behavior documented. Player was cooperative during the last contact.</p><div class="muted tiny">Avery · Aug 20, 2026 8:14 PM</div></div>
    <div class="context-section"><h3>Authority context</h3><p class="compact-copy">This browser presents options only. Final authority, target protection, duration limits and approvals are server decisions.</p></div>`;
  $$('[data-context-view]').forEach((button) => button.addEventListener('click', () => switchView(button.dataset.contextView)));
}

function renderCounts() {
  const counts = {messages:baseMessages.length, history:state.history.length, cases:3, notes:3, accounts:2};
  for (const [key,value] of Object.entries(counts)) {
    const element = $(`[data-count="${key}"]`); if (element) element.textContent = value;
  }
}

function renderWorkspace() {
  const content = $('#workspaceContent');
  if (state.view === 'overview') content.innerHTML = overviewHtml();
  else if (state.view === 'messages') content.innerHTML = messagesHtml();
  else if (state.view === 'history') content.innerHTML = historyHtml();
  else if (state.view === 'cases') content.innerHTML = casesHtml();
  else if (state.view === 'notes') content.innerHTML = notesHtml();
  else if (state.view === 'accounts') content.innerHTML = accountsHtml();
  bindViewEvents();
}

function overviewHtml() {
  const recent = state.history.slice(0,3);
  return `<div class="page-heading"><div><div class="eyebrow">Player overview</div><h2>Moderation context</h2><p>Relevant account state, recent history and current investigation activity.</p></div></div>
    <div class="metric-grid">
      ${metric('Active sanctions','1','Discord mute', 'warning')}${metric('Total history',state.history.length,'Across all offense families')}${metric('Evidence selected',state.evidence.size,'Messages attached to this case')}
    </div>
    <div class="two-column"><section class="card"><div class="section-heading"><h3>Recent moderation history</h3><button class="text-button" data-view-link="history">View all</button></div>${recent.map(historyCompact).join('') || empty('No moderation history')}</section>
    <section class="card"><div class="section-heading"><h3>Investigation</h3><button class="text-button" data-view-link="messages">Open messages</button></div><div class="summary-list"><div class="summary-row"><span>Selected messages</span><strong>${state.selected.size}</strong></div><div class="summary-row"><span>Evidence</span><strong>${state.evidence.size}</strong></div><div class="summary-row"><span>Delete on confirm</span><strong>${state.deleting.size}</strong></div></div><button class="button primary full" type="button" data-punish>Issue Punishment</button></section></div>`;
}

function metric(label,value,detail,tone='') { return `<div class="metric-card ${tone}"><span>${label}</span><strong>${value}</strong><small>${detail}</small></div>`; }
function historyCompact(row) { return `<div class="compact-row"><div><strong>${esc(row.offense)}</strong><span>${formatDate(row.date)} · ${esc(row.staff)}</span></div><div class="right"><strong>${esc(row.action)}</strong><span>${esc(row.duration)}</span></div></div>`; }

function messagesHtml() {
  const messages = filteredMessages();
  return `<div class="page-heading"><div><div class="eyebrow">Message investigation</div><h2>Messages & evidence</h2><p>Inspect exact conversation context, then independently choose evidence and deletion.</p></div><div class="page-actions"><button class="button primary" type="button" data-punish>Issue Punishment</button></div></div>
    ${state.contextId?`<div class="alert info"><strong>Conversation context</strong><span>Showing the triggering message with nearby messages before and after it.</span><button class="text-button" type="button" data-exit-context>Exit context</button></div>`:''}<section class="card filters-card"><div class="filter-row"><label class="search-field"><span class="sr-only">Search messages</span><input id="messageSearch" type="search" placeholder="Search message text" value="${esc(state.search)}"></label>
      <label><span class="sr-only">Channel</span><select id="channelFilter"><option value="all">All channels</option>${['general','market'].map((ch)=>`<option value="${ch}" ${state.channel===ch?'selected':''}>#${ch}</option>`).join('')}</select></label>
      <label><span class="sr-only">Date</span><select id="dateFilter"><option value="all">All dates</option><option value="2026-08-29" ${state.date==='2026-08-29'?'selected':''}>Aug 29</option><option value="2026-08-28" ${state.date==='2026-08-28'?'selected':''}>Aug 28</option><option value="2026-08-24" ${state.date==='2026-08-24'?'selected':''}>Aug 24</option></select></label>
      <label class="checkbox-control"><input id="selectedFilter" type="checkbox" ${state.selectedOnly?'checked':''}> Selected only</label></div></section>
    <section class="message-investigation">${messages.length ? groupedMessagesHtml(messages) : empty('No messages match these filters','Try a different date, channel, or search term.')}</section>`;
}

function filteredMessages() {
  const term = state.search.trim().toLowerCase();
  const contextIds = state.contextId ? contextMessageIds(state.contextId) : null;
  return baseMessages.filter((message) => {
    const matchContext = !contextIds || contextIds.has(message.id);
    const matchText = !term || `${message.text} ${message.author} ${message.channel}`.toLowerCase().includes(term);
    const matchChannel = state.channel === 'all' || message.channel === state.channel;
    const matchDate = state.date === 'all' || message.time.slice(0,10) === state.date;
    const matchSelected = !state.selectedOnly || state.selected.has(message.id);
    return matchContext && matchText && matchChannel && matchDate && matchSelected;
  });
}

function groupedMessagesHtml(messages) {
  let lastGroup = '';
  return messages.map((message) => {
    const group = `${message.time.slice(0,10)}|${message.channel}`;
    const separator = group !== lastGroup ? groupHeader(message) : '';
    lastGroup = group;
    return separator + messageHtml(message);
  }).join('');
}

function groupHeader(message) {
  return `<div class="conversation-divider"><span>${dateHeading(message.time)}</span><span>#${message.channel}</span></div>`;
}

function messageHtml(message) {
  const selected = state.selected.has(message.id);
  const reply = message.replyTo ? `<div class="reply-reference">↳ Replying to message ${message.replyTo}</div>` : '';
  const attachment = message.attachment ? `<div class="attachment-card"><div class="attachment-icon">IMG</div><div><strong>${esc(message.attachment.name)}</strong><span>${esc(message.attachment.detail)}</span></div></div>` : '';
  const statuses = [state.evidence.has(message.id) ? '<span class="message-pill evidence">Evidence</span>' : '', state.violating.has(message.id) ? '<span class="message-pill violating">Violating</span>' : '', state.deleting.has(message.id) ? '<span class="message-pill delete">Delete on confirm</span>' : ''].join('');
  return `<article class="message-row ${selected?'selected':''} ${message.deleted?'deleted':''}" data-message-id="${message.id}">
    <div><input class="message-select" type="checkbox" aria-label="Select message from ${esc(message.author)} at ${formatExact(message.time)}" ${selected?'checked':''}></div>
    <div class="message-avatar ${message.target?'target':''}" aria-hidden="true">${esc(message.initials)}</div>
    <div class="message-body">${reply}<div class="message-meta"><strong>${esc(message.author)}</strong>${message.target?'<span class="target-chip">Target</span>':''}<span>@${esc(message.username)}</span><time datetime="${message.time}">${formatExact(message.time)}</time>${message.edited?'<span>(edited)</span>':''}</div>
      <div class="message-text">${message.deleted?'<em>Message deleted in source context</em>':esc(message.text)}</div>${attachment}<div class="message-statuses">${statuses}</div><div class="message-id">Message ID ${message.id}</div></div>
    <button class="icon-button context-button" type="button" data-context-message="${message.id}" aria-label="Inspect surrounding context for message ${message.id}">•••</button>
  </article>`;
}

function renderSelectionBar() {
  const bar = $('#selectionBar');
  if (state.selected.size === 0) { bar.hidden = true; bar.innerHTML = ''; return; }
  bar.hidden = false;
  bar.innerHTML = `<div class="selection-summary"><strong>${state.selected.size} selected</strong><span>${state.evidence.size} evidence · ${state.deleting.size} delete on confirm</span></div><div class="selection-actions">
    <button class="button secondary" data-selection-action="evidence" type="button">${allSelectedIn(state.evidence)?'Remove Evidence':'Add to Evidence'}</button><button class="button secondary" data-selection-action="violating" type="button">${allSelectedIn(state.violating)?'Clear Violating':'Mark Violating'}</button><button class="button danger-secondary" data-selection-action="delete" type="button">${allSelectedIn(state.deleting)?'Preserve Messages':'Delete on Confirm'}</button><button class="button ghost" data-selection-action="clear" type="button">Remove Selection</button></div>`;
  $$('[data-selection-action]').forEach((button) => button.addEventListener('click', () => selectionAction(button.dataset.selectionAction)));
}

function allSelectedIn(set){return state.selected.size>0&&[...state.selected].every((id)=>set.has(id));}
function selectionAction(action) {
  const ids = [...state.selected];
  if (action === 'evidence') toggleGroup(state.evidence,ids);
  if (action === 'violating') toggleGroup(state.violating,ids);
  if (action === 'delete') toggleGroup(state.deleting,ids);
  if (action === 'clear') { state.selected.clear(); state.anchor = null; }
  if (action !== 'clear') state.evidenceRevision++;
  renderAll();
}
function toggleGroup(set,ids){const remove=ids.every((id)=>set.has(id));ids.forEach((id)=>remove?set.delete(id):set.add(id));}

function bindViewEvents() {
  $$('[data-view-link]').forEach((button) => button.addEventListener('click', () => switchView(button.dataset.viewLink)));
  $$('[data-punish]').forEach((button) => button.addEventListener('click', openWorkflow));
  if (state.view === 'messages') bindMessageEvents();
}

function bindMessageEvents() {
  $('[data-exit-context]')?.addEventListener('click', () => { state.contextId=null; renderWorkspace(); });
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
  const ids = filteredMessages().map((message)=>message.id);
  const a = ids.indexOf(fromId), b = ids.indexOf(toId);
  if (a < 0 || b < 0) { toggleSet(state.selected,toId,checked); return; }
  ids.slice(Math.min(a,b), Math.max(a,b)+1).forEach((id)=>toggleSet(state.selected,id,checked));
}
function toggleSet(set,id,enabled) { if (enabled) set.add(id); else set.delete(id); }
function showContext(id) {
  if (!baseMessages.some((message)=>message.id===id)) return;
  state.search=''; state.channel='all'; state.date='all'; state.selectedOnly=false; state.contextId=id;
  showToast(`Showing conversation context around message ${id}.`);
  renderAll();
}
function contextMessageIds(id) {
  const index=baseMessages.findIndex((message)=>message.id===id);
  if(index<0)return new Set();
  const trigger=baseMessages[index];
  return new Set(baseMessages.filter((message)=>message.channel===trigger.channel).filter((message)=>{const i=baseMessages.indexOf(message);return Math.abs(i-index)<=2;}).map((message)=>message.id));
}

function historyHtml() {
  return `<div class="page-heading"><div><div class="eyebrow">Moderation record</div><h2>History</h2><p>Total history remains visible; the punishment workflow separately identifies only ladder-relevant records for the selected offense.</p></div></div>
    <section class="card table-card"><div class="responsive-table"><table><thead><tr><th>Date</th><th>Offense</th><th>Action</th><th>Duration</th><th>Staff</th><th>Status</th></tr></thead><tbody>${state.history.map((row)=>`<tr><td>${formatDate(row.date)}</td><td>${esc(row.offense)}</td><td><strong>${esc(row.action)}</strong></td><td>${esc(row.duration)}</td><td>${esc(row.staff)}</td><td><span class="status-badge neutral">${esc(row.status)}</span></td></tr>`).join('')}</tbody></table></div></section>`;
}

function casesHtml() { return `<div class="page-heading"><div><div class="eyebrow">Case management</div><h2>Cases</h2><p>Current and historical investigations connected to this player.</p></div></div><div class="card-list">${[
  ['CASE-1187','Aug 29, 2026','Spam pattern review','Open','Morgan','Pending punishment'],['CASE-1044','Aug 20, 2026','Repeated flooding','Closed','Sam','Warning'],['CASE-0911','Jun 17, 2026','Harassment review','Closed','Avery','Warning']
].map((row)=>`<section class="card case-card"><div><span class="eyebrow">${row[0]}</span><h3>${row[2]}</h3><p>${row[1]} · ${row[4]}</p></div><div class="case-status"><span class="status-badge ${row[3]==='Open'?'warning':'neutral'}">${row[3]}</span><strong>${row[5]}</strong></div></section>`).join('')}</div>`; }

function notesHtml() { return `<div class="page-heading"><div><div class="eyebrow">Staff context</div><h2>Notes</h2><p>Concise internal notes attached to the player record.</p></div></div><div class="timeline">${[
  ['Aug 29, 2026 · 5:48 PM','Morgan','Investigating repeated promotional flooding across #general and #market.'],['Aug 20, 2026 · 8:14 PM','Avery','Player acknowledged the prior warning and was cooperative.'],['Jun 17, 2026 · 3:02 PM','Sam','Initial behavior note; no account-link concerns observed.']
].map((note)=>`<article class="timeline-item"><div class="timeline-dot"></div><div class="card"><div class="section-heading"><strong>${note[1]}</strong><time>${note[0]}</time></div><p>${note[2]}</p></div></article>`).join('')}</div>`; }

function accountsHtml() { return `<div class="page-heading"><div><div class="eyebrow">Identity graph</div><h2>Accounts</h2><p>Discord and Minecraft identities associated with this moderation target.</p></div></div><div class="account-grid"><section class="card"><span class="eyebrow">Discord identity</span><h3>${identity.displayName}</h3><p>@${identity.username}</p><dl class="detail-list"><div><dt>Discord ID</dt><dd>${identity.discordId}</dd></div><div><dt>Link status</dt><dd>Verified</dd></div></dl></section><section class="card"><span class="eyebrow">Minecraft main</span><h3>${identity.minecraft}</h3><p>Java Edition · Main account</p><dl class="detail-list"><div><dt>UUID</dt><dd>${identity.minecraftUuid}</dd></div><div><dt>Link status</dt><dd>Verified</dd></div></dl></section><section class="card"><span class="eyebrow">Linked alt</span><h3>${identity.alts[0].name}</h3><p>Bedrock Edition</p><dl class="detail-list"><div><dt>Relationship</dt><dd>Linked alt</dd></div><div><dt>Status</dt><dd>Active link</dd></div></dl></section></div>`; }
