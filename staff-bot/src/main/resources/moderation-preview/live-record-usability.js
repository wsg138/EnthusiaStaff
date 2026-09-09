'use strict';

function hardenedHistoryNode() {
  const total = realHistoryTotal();
  const body = [pageHeading('Moderation record', 'History', 'Live moderation records returned for this player. Rule links open the public Enthusia rules.')];
  if (!liveModeration.bootstrap) body.push(recordUnavailableOrLoading('history'));
  else if (total === 0) body.push(emptyState('No moderation history', 'The live read completed and returned no moderation records.'));
  else if (!state.history.length) body.push(emptyState('History is only partially available', `${total} total record${total === 1 ? '' : 's'} exist, but no history rows were included in this read.`));
  else body.push(element('section', {className:'card table-card'}, element('div', {className:'responsive-table'}, hardenedHistoryTable())));
  return element('div', {}, body);
}

function hardenedHistoryTable() {
  const head = element('thead', {}, element('tr', {}, ['Date / time','Offense','Action','Staff','Status','Rule'].map((label) => element('th',{text:label}))));
  const rows = state.history.map((row) => element('tr', {},
    element('td',{text:formatExact(row.time || `${row.date}T12:00:00Z`)}),
    element('td',{text:row.offense}), element('td',{},element('strong',{text:row.action})),
    element('td',{text:row.staff}), element('td',{},statusBadge(row.status,'neutral')),
    element('td',{},policyLinkNode(offensePolicy(row.key), 'View rule'))));
  return element('table', {}, head, element('tbody', {}, rows));
}

function hardenedCasesNode() {
  const body = [pageHeading('Case management','Cases','Live cases available for this player, with the fields exposed by the read service.')];
  if (!liveModeration.bootstrap) body.push(recordUnavailableOrLoading('cases'));
  else if (!liveModeration.cases.length) body.push(emptyState('No cases', 'The live read completed and returned no cases.'));
  else body.push(element('div',{className:'card-list'},liveModeration.cases.map(hardenedCaseCardNode)));
  return element('div', {}, body);
}

function hardenedCaseCardNode(row) {
  return element('section',{className:'card case-card detailed-record'},
    element('div',{},element('span',{className:'eyebrow',text:row.caseId}),element('h3',{text:row.reason}),
      element('p',{text:`Opened ${formatExact(row.issuedAt)} · ${row.actorName}`})),
    element('div',{className:'record-meta'},
      statusBadge(row.state || 'Recorded', row.state === 'OPEN' ? 'warning' : 'neutral'),
      element('span',{text:`Policy family: ${row.sanctionFamily || row.exactReasonId || 'not provided'}`}),
      element('span',{text:'Evidence: not exposed by the current case read response'})));
}

function hardenedNotesNode() {
  const body = [pageHeading('Staff context','Notes','Private staff notes available to this staff session.')];
  if (!liveModeration.bootstrap) body.push(recordUnavailableOrLoading('notes'));
  else if (!liveModeration.notes.length) body.push(emptyState('No notes', 'The live read completed and returned no staff notes.'));
  else body.push(element('div',{className:'timeline'},liveModeration.notes.map(hardenedNoteNode)));
  return element('div', {}, body);
}

function hardenedNoteNode(note) {
  return element('article',{className:'timeline-item'},element('div',{className:'timeline-dot'}),
    element('div',{className:'card'},
      element('div',{className:'section-heading'},element('strong',{text:`Staff ${note.actorId}`}),element('time',{text:formatExact(note.createdAt)})),
      element('p',{text:note.text}),
      element('div',{className:'record-meta inline-record-meta'},
        element('span',{text:'Status: private staff note'}), element('span',{text:'Evidence: no evidence link is provided for this note'}))));
}

function recordUnavailableOrLoading(label) {
  if (liveModeration.warning && liveModeration.warning !== 'Loading moderation data…') {
    return emptyState(`${capitalize(label)} unavailable`, 'The live read source could not provide this section. This is different from having no records.');
  }
  return emptyState(`Loading ${label}…`, 'Waiting for the live read to finish.');
}

function recordEmptyState(label) {
  if (!liveModeration.bootstrap) return recordUnavailableOrLoading(label);
  return emptyState(`No ${label}`, `The live read completed and returned no ${label}.`);
}

function capitalize(value) {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function hardenedAccountsNode() {
  const discordName = firstText(identity.serverName, identity.globalName, identity.displayName, identity.username, 'Unknown Discord user');
  const cards = [liveAccountCard('Discord identity',discordName,`@${identity.username}`,[
    ['Username',`@${identity.username}`],['Display name',discordName],['Discord ID',identity.discordId],['Link state',identity.linkState || 'Not provided']
  ],discordProfileAvatar(identity.avatarUrl,discordName))];
  for (const account of liveModeration.accounts) cards.push(minecraftAccountCard(account));
  return element('div', {},
    pageHeading('Linked identities','Accounts',`Accounts ${cards.length} = 1 Discord identity + ${liveModeration.accounts.length} Minecraft identit${liveModeration.accounts.length === 1 ? 'y' : 'ies'}. “${identity.alts.length} linked alts” counts only alternate Minecraft accounts.`),
    element('div',{className:'account-grid'},cards));
}

window.historyNode = hardenedHistoryNode;
window.casesNode = hardenedCasesNode;
window.notesNode = hardenedNotesNode;
window.accountsNode = hardenedAccountsNode;
