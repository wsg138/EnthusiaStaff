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

function productReviewEvidenceNode(workflow) {
  const status = workflowReviewStatus(workflow);
  return element('section',{className:'card review-evidence'},
    sectionHeadingNode('Case readiness','Review every item before confirming the action.'),
    readinessChecklistNode(workflow),
    reviewValidationAlert(status),
    policyLinkNode(offensePolicy(workflow.offense.key),'Open applicable rule'),
    reviewEvidenceSummaryNode(workflow),
    staffExplanationNode(workflow),
    productNotificationMessageNode(workflow),
    testEnvironmentBoundary());
}

function productNotificationMessageNode(workflow) {
  const detail = workflow.dm ? actionDmText(workflow) : 'No DM is included with this action.';
  return element('div',{className:'dm-preview'},
    element('span',{text:'Notification message'}), element('p',{text:detail}));
}

function productReviewFooterNode(stale) {
  const status = workflowReviewStatus(state.workflow);
  const right = element('div',{className:'inline'});
  if (stale) right.append(buttonNode('Recalculate','button secondary',{recalculate:''}));
  const confirm = buttonNode('Confirm action','button primary',{confirm:''});
  confirm.disabled = stale || !status.ready;
  if (confirm.disabled) confirm.setAttribute('title',stale ? 'Recalculate after evidence changes.' : status.errors.join(' '));
  right.append(confirm);
  return [buttonNode('Back','button ghost',{back:''}),right];
}

async function productConfirmAction() {
  if (!state.session) {
    showToast('Session unavailable. Reopen from Discord.', true);
    return;
  }
  const button = $('[data-confirm]');
  if (button) button.disabled = true;
  try {
    const response = await fetch('/api/simulate', simulationRequest(state.session));
    if (!response.ok) throw new Error('Action review rejected');
    await response.json();
    state.workflow.step = 'complete';
    renderWorkflow();
    showToast('Action review complete.');
  } catch {
    showToast('Action review could not be completed. Reopen the panel from Discord if the session expired.', true);
    if (button) button.disabled = false;
  }
}

function productRenderCompleteStep() {
  $('#workflowTitle').textContent = 'Complete';
  $('#workflowSteps').replaceChildren();
  replaceChildrenOf($('#workflowBody'), element('div', {className:'completion-state'},
    element('div', {className:'completion-icon', text:'✓', attrs:{'aria-hidden':'true'}}),
    element('h3', {text:'Action review complete'}),
    element('p', {text:'The review flow completed successfully.'}),
    element('span', {text:'Review completed. No changes were sent.'})));
  replaceChildrenOf($('#workflowFooter'), buttonNode('Done','button primary',{done:''}));
  $('[data-done]').addEventListener('click',closeWorkflow);
}

const LIVE_MESSAGE_PAGE_LIMIT = '50';
const baseWorkflowReviewStatus = window.workflowReviewStatus;
const baseCaptureOptions = window.captureOptions;
const baseFocusFirstMissingReviewField = window.focusFirstMissingReviewField;

async function fasterLoadSession() {
  try {
    const sessionRequest = fetch('/api/session', {headers:{Accept:'application/json'}, cache:'no-store'});
    const bootstrapRequest = requestDirectModerationRead('/api/bootstrap', {headers:{Accept:'application/json'}});
    const [sessionResponse, bootstrapResponse] = await Promise.all([sessionRequest, bootstrapRequest]);
    if (!sessionResponse.ok) throw diagnosticReadError('session_unavailable');
    state.session = await sessionResponse.json();
    const payload = await readJsonResponse(bootstrapResponse);
    if (!bootstrapResponse.ok) throw diagnosticResponseError(bootstrapResponse, payload);
    applyLiveBootstrap(payload);
    $('#actorMeta').textContent = payload.actor?.displayName || 'Verified staff session';
  } catch (error) {
    applyDiagnosticReadFailure(error);
  }
}

async function fasterLoadChannelPage() {
  if (state.channel === 'all') {
    replaceMessagePage(liveModeration.bootstrap?.messages || {messages:[]});
    renderWorkspace();
    renderCounts();
    return;
  }
  await fasterLoadMessageRequest(new URLSearchParams({channel:state.channel, limit:LIVE_MESSAGE_PAGE_LIMIT}), 'replace');
}

function messagePagingButton(direction) {
  return $$('[data-load-direction]').find((button) => button.dataset.loadDirection === direction) || null;
}

async function fasterLoadMoreMessages(direction) {
  if (state.channel === 'all') return;
  const cursor = direction === 'older' ? liveModeration.olderCursor : liveModeration.newerCursor;
  if (!cursor) {
    showToast(`No ${direction} cursor is available.`);
    return;
  }
  const button = messagePagingButton(direction);
  if (button) {
    button.disabled = true;
    button.textContent = `Loading ${direction}…`;
  }
  const params = new URLSearchParams({channel:state.channel, limit:LIVE_MESSAGE_PAGE_LIMIT});
  params.set(direction === 'older' ? 'before' : 'after', cursor);
  await fasterLoadMessageRequest(params, direction, button);
}

async function fasterLoadMessageRequest(params, mode, pendingButton = null) {
  try {
    const response = await requestDirectModerationRead('/api/messages', {
      method:'POST',
      headers:{Accept:'application/json', 'Content-Type':'application/json'},
      body:JSON.stringify(Object.fromEntries(params.entries()))
    });
    const page = await readJsonResponse(response);
    if (!response.ok) throw new Error(page.message || 'Discord messages unavailable');
    if (mode === 'replace') replaceMessagePage(page);
    else appendMessagePage(page, mode);
    renderWorkspace();
    renderCounts();
  } catch (error) {
    showToast(error.message || 'Discord messages are temporarily unavailable.', true);
    if (pendingButton?.isConnected) pendingButton.disabled = false;
  }
}

function freeformDurationField(workflow) {
  const value = workflow.duration && workflow.duration !== '—' ? workflow.duration : '3 days';
  const input = element('input', {
    id:'customDuration', type:'text', value,
    placeholder:'e.g. 60 days, 12 hours, 90 minutes, Permanent',
    attrs:{maxlength:'40', autocomplete:'off', spellcheck:'false'}
  });
  return element('div', {className:'custom-duration-field'},
    fieldLabel('Duration', input),
    element('small', {className:'field-help', text:'Use any positive number of minutes, hours, days, weeks, months, or years — or Permanent.'}));
}

function normalizePunishmentDuration(raw) {
  const value = String(raw || '').trim();
  if (/^permanent$/i.test(value)) return 'Permanent';
  const match = /^([1-9][0-9]*)\s*(m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days|w|wk|wks|week|weeks|mo|mos|month|months|y|yr|yrs|year|years)$/i.exec(value);
  if (!match) return null;
  const amount = Number(match[1]);
  if (!Number.isSafeInteger(amount)) return null;
  const unit = durationUnitName(match[2]);
  return `${amount} ${amount === 1 ? unit : `${unit}s`}`;
}

function durationUnitName(raw) {
  const unit = raw.toLowerCase();
  if (['m','min','mins','minute','minutes'].includes(unit)) return 'minute';
  if (['h','hr','hrs','hour','hours'].includes(unit)) return 'hour';
  if (['d','day','days'].includes(unit)) return 'day';
  if (['w','wk','wks','week','weeks'].includes(unit)) return 'week';
  if (['mo','mos','month','months'].includes(unit)) return 'month';
  return 'year';
}

function actionHasDuration(workflow) {
  return Boolean(workflow?.actual) && !['Warning', 'Kick'].includes(workflow.actual.action);
}

function workflowDurationReady(workflow) {
  return !actionHasDuration(workflow) || normalizePunishmentDuration(workflow.duration) !== null;
}

function durationAwareWorkflowReviewStatus(workflow) {
  const status = baseWorkflowReviewStatus(workflow);
  const durationReady = workflowDurationReady(workflow);
  if (durationReady) return {...status, durationReady};
  const errors = [...status.errors, 'Enter a duration such as 60 days, 12 hours, 90 minutes, or Permanent.'];
  return {...status, durationReady, errors, ready:false};
}

function durationAwareWorkflowCanEnterReview(workflow) {
  const status = durationAwareWorkflowReviewStatus(workflow);
  return status.reasonReady && status.evidenceReady && status.restrictionReady && status.durationReady;
}

function durationAwareCaptureOptions() {
  baseCaptureOptions();
  if (!state.workflow || !actionHasDuration(state.workflow)) return;
  const normalized = normalizePunishmentDuration(state.workflow.duration);
  if (normalized) state.workflow.duration = normalized;
}

function durationAwareFocusFirstMissingReviewField(status) {
  if (!status.reasonReady) $('#reasonInput')?.focus();
  else if (status.durationReady === false) $('#customDuration')?.focus();
  else baseFocusFirstMissingReviewField(status);
}

function durationAwareBindCustomOptionEvents(workflow) {
  $('#customAction')?.addEventListener('change', (event) => {
    workflow.approvalConfirmed = false;
    setCustomAction(event.target.value);
  });
  $('#customScope')?.addEventListener('change', (event) => { workflow.scope = event.target.value; });
  $('#customDuration')?.addEventListener('input', (event) => {
    workflow.duration = event.target.value;
    workflow.approvalConfirmed = false;
  });
  $('#customDuration')?.addEventListener('change', (event) => {
    const normalized = normalizePunishmentDuration(event.target.value);
    if (normalized) {
      workflow.duration = normalized;
      event.target.value = normalized;
    }
    renderWorkflow();
  });
}

window.historyNode = hardenedHistoryNode;
window.casesNode = hardenedCasesNode;
window.notesNode = hardenedNotesNode;
window.accountsNode = hardenedAccountsNode;
window.reviewEvidenceNode = productReviewEvidenceNode;
window.reviewFooterNode = productReviewFooterNode;
window.confirmSimulation = productConfirmAction;
window.renderCompleteStep = productRenderCompleteStep;
window.loadSession = fasterLoadSession;
window.loadChannelPage = fasterLoadChannelPage;
window.loadMoreMessages = fasterLoadMoreMessages;
window.loadMessageRequest = fasterLoadMessageRequest;
window.durationField = freeformDurationField;
window.workflowReviewStatus = durationAwareWorkflowReviewStatus;
window.workflowCanEnterReview = durationAwareWorkflowCanEnterReview;
window.captureOptions = durationAwareCaptureOptions;
window.focusFirstMissingReviewField = durationAwareFocusFirstMissingReviewField;
window.bindCustomOptionEvents = durationAwareBindCustomOptionEvents;