'use strict';

const DISPLAY_TIME_ZONE = 'America/Indiana/Indianapolis';

function renderReviewStep() {
  const w = state.workflow;
  const r = w.recommendation;
  const stale = w.stale || w.recommendationEvidenceRevision !== state.evidenceRevision;
  const restrict = w.actual.action === 'Restrict';
  const targets = restrictionTargetSelections(w);
  const body = [];
  if (stale) body.push(staleEvidenceAlert());
  body.push(reviewGridNode(w, r, restrict, targets));
  body.push(reviewEvidenceNode(w));
  replaceChildrenOf($('#workflowBody'), body);
  replaceChildrenOf($('#workflowFooter'), reviewFooterNode(stale, restrict, targets));
  bindReviewEvents(w);
}

function restrictionTargetSelections(workflow) {
  return [...workflow.restrictionTargets]
    .map((id) => RESTRICTION_TARGETS.find((target) => target.id === id))
    .filter(Boolean);
}

function staleEvidenceAlert() {
  return element('div', {className: 'alert warning'},
    element('strong', {text: 'Evidence changed after the recommendation.'}),
    element('span', {text: 'Recalculate before simulation so the review matches the current incident.'}));
}

function reviewGridNode(w, recommendation, restrict, targets) {
  const items = [
    ['Target', `${identity.displayName} · ${identity.minecraft}`],
    ['Offense', w.offense.label],
    ['Relevant history', `${recommendation.relevant.length} of ${recommendation.total} total records`],
    ['Ladder recommendation', `${recommendation.action} · ${recommendation.duration}`],
    ['Actual action', `${w.actual.action}${w.custom ? ' · Custom override' : ''}`],
    ['Following recommendation', w.custom ? 'No — Custom override' : 'Yes']
  ];
  if (w.duration !== '—') items.push(['Duration', w.duration]);
  items.push(['Scope / platform', w.scope]);
  if (restrict) addRestrictionReviewItems(items, w, targets);
  items.push(
    ['Evidence messages', String(state.evidence.size)],
    ['Messages to delete', String(state.deleting.size)],
    ['Preserve', `${preservedEvidenceCount()} evidence message${preservedEvidenceCount() === 1 ? '' : 's'}`],
    ['DM user', w.dm ? 'Yes' : 'No'],
    ['Approval requirement', approvalFor(w.actual.action, w.duration)]);
  return element('div', {className: 'review-grid'}, items.map(([label, value]) => reviewItemNode(label, value)));
}

function addRestrictionReviewItems(items, workflow, targets) {
  items.push(['Restriction mode', workflow.restrictMode === 'read-only' ? 'Read only' : 'No access']);
  items.push(['Restriction targets', targets.length ? targets.map((target) => target.label).join(', ') : 'None selected']);
}

function reviewItemNode(label, value) {
  return element('div', {className: 'review-item'}, element('span', {text: label}), element('strong', {text: value}));
}

function reviewEvidenceNode(w) {
  const card = element('section', {className: 'card review-evidence'},
    sectionHeadingNode('Case & evidence summary', 'Selected Discord messages remain distinct from deletion instructions.'),
    evidenceSummaryNode());
  if (w.reason) card.append(element('div', {className: 'staff-reason'}, element('span', {text: 'Staff explanation'}), element('p', {text: w.reason})));
  return card;
}

function reviewFooterNode(stale, restrict, targets) {
  const right = element('div', {className: 'inline'});
  if (stale) right.append(buttonNode('Recalculate', 'button secondary', {recalculate: ''}));
  const confirm = buttonNode('Confirm simulation', 'button primary', {confirm: ''});
  confirm.disabled = stale || (restrict && targets.length === 0);
  right.append(confirm);
  return [buttonNode('Back', 'button ghost', {back: ''}), right];
}

function bindReviewEvents(w) {
  $('[data-back]').addEventListener('click', () => { w.step = 'options'; renderWorkflow(); });
  $('[data-recalculate]')?.addEventListener('click', () => recalculateRecommendation(w));
  $('[data-confirm]').addEventListener('click', confirmSimulation);
}

function recalculateRecommendation(w) {
  w.recommendation = recommend(w.offense.key);
  w.recommendationEvidenceRevision = state.evidenceRevision;
  w.stale = false;
  renderWorkflow();
}

function evidenceSummaryNode() {
  const ids = [...state.evidence];
  if (!ids.length) return element('div', {className: 'empty-inline', text: 'No message evidence selected.'});
  return ids.map(evidenceLineNode).filter(Boolean);
}

function evidenceLineNode(id) {
  const message = baseMessages.find((item) => item.id === id);
  if (!message) return null;
  const chips = element('div', {className: 'chip-row'}, messagePill('Evidence', 'evidence'));
  if (state.violating.has(id)) chips.append(messagePill('Violating', 'violating'));
  chips.append(messagePill(state.deleting.has(id) ? 'Delete' : 'Preserve', state.deleting.has(id) ? 'delete' : 'preserve'));
  return element('div', {className: 'evidence-line'},
    element('div', {}, element('strong', {text: `#${message.channel} · ${formatExact(message.time)}`}), element('span', {text: message.text})),
    chips);
}

function preservedEvidenceCount() {
  return [...state.evidence].filter((id) => !state.deleting.has(id)).length;
}

function approvalFor(action, duration) {
  return duration === 'Permanent' && ['Ban', 'Mute', 'Restrict'].includes(action) ? 'Admin+ approval required' : 'None';
}

async function confirmSimulation() {
  if (!state.session) {
    showToast('Session unavailable. Reopen from Discord.', true);
    return;
  }
  const button = $('[data-confirm]');
  if (button) button.disabled = true;
  try {
    const response = await fetch('/api/simulate', simulationRequest(state.session));
    if (!response.ok) throw new Error('Simulation rejected');
    const result = await response.json();
    state.workflow.step = 'complete';
    renderWorkflow();
    showToast(result.message || 'Simulation complete');
  } catch (error) {
    showToast('Simulation could not be completed. Reopen the panel from Discord if the session expired.', true);
    if (button) button.disabled = false;
  }
}

function simulationRequest(session) {
  const payload = {
    target: session.targetKey,
    offense: state.workflow.offense.key,
    action: state.workflow.actual.action,
    evidence: [...state.evidence],
    delete: [...state.deleting]
  };
  return {
    method: 'POST',
    headers: {'Content-Type': 'application/json', 'X-Preview-Csrf': session.csrfToken},
    body: JSON.stringify(payload)
  };
}

function renderCompleteStep() {
  $('#workflowSteps').replaceChildren();
  replaceChildrenOf($('#workflowBody'), element('div', {className: 'completion-state'},
    element('div', {className: 'completion-icon', text: '✓', attrs: {'aria-hidden': 'true'}}),
    element('h3', {text: 'Simulation complete'}),
    element('p', {text: 'The review flow completed successfully.'}),
    element('span', {text: 'No live moderation action was performed.'})));
  replaceChildrenOf($('#workflowFooter'), buttonNode('Done', 'button primary', {done: ''}));
  $('[data-done]').addEventListener('click', closeWorkflow);
}

function scenarioOffense() {
  if (['severe', 'admin', 'approval'].includes(state.scenario)) return 'hate';
  if (state.scenario === 'custom') return 'harassment';
  return 'spam';
}

function offenseHint(key) {
  return {
    spam: 'Flooding, repeated posts, disruptive repetition',
    harassment: 'Targeted hostile or abusive conduct',
    hate: 'Slurs or identity-based abuse',
    advertising: 'Unwanted invites or promotions',
    cheating: 'Unfair gameplay or prohibited client behavior',
    other: 'Policy issue that needs a custom explanation'
  }[key];
}

function dateHeading(iso) {
  const key = displayDateKey(iso);
  if (key === '2026-08-29') return 'Today · Aug 29, 2026';
  if (key === '2026-08-28') return 'Yesterday · Aug 28, 2026';
  return formatDateTime(new Date(iso), {month: 'short', day: 'numeric', year: 'numeric'});
}

function displayDateKey(iso) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: DISPLAY_TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(new Date(iso));
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function formatExact(iso) {
  return formatDateTime(new Date(iso), {month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit', second: '2-digit'});
}

function formatDate(date) {
  return formatDateTime(new Date(`${date}T12:00:00Z`), {month: 'short', day: 'numeric', year: 'numeric'});
}

function formatDateTime(value, options) {
  return new Intl.DateTimeFormat('en-US', {timeZone: DISPLAY_TIME_ZONE, ...options}).format(value);
}

function emptyState(title, detail = '') {
  const empty = element('div', {className: 'empty-state'}, element('strong', {text: title}));
  if (detail) empty.append(element('span', {text: detail}));
  return empty;
}

function showToast(message, isError = false) {
  const region = $('#toastRegion');
  replaceChildrenOf(region, element('div', {className: `toast${isError ? ' error' : ''}`, text: message}));
  clearTimeout(state.toastTimer);
  state.toastTimer = setTimeout(() => region.replaceChildren(), 4200);
}

document.addEventListener('DOMContentLoaded', init);
