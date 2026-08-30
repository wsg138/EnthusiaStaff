'use strict';

function openWorkflow() {
  state.workflow = {
    step: 'offense', offense: null, recommendation: null, actual: null, custom: false, duration: null, scope: null,
    reason: '', dm: true, restrictMode: 'read-only', restrictionTargets: new Set(), recommendationEvidenceRevision: null,
    stale: state.scenario === 'stale'
  };
  renderWorkflow();
  $('#punishmentDialog').showModal();
  const firstOffense = $('[data-offense]');
  if (firstOffense) firstOffense.focus();
}

function closeWorkflow() {
  if ($('#punishmentDialog').open) $('#punishmentDialog').close();
  state.workflow = null;
}

function renderWorkflow() {
  if (!state.workflow) return;
  const titles = {offense: 'Choose offense', recommendation: 'Ladder recommendation', options: 'Punishment options', review: 'Final review', complete: 'Simulation complete'};
  $('#workflowTitle').textContent = titles[state.workflow.step];
  renderWorkflowSteps(state.workflow.step);
  const renderers = {offense: renderOffenseStep, recommendation: renderRecommendationStep, options: renderOptionsStep, review: renderReviewStep};
  (renderers[state.workflow.step] || renderCompleteStep)();
  $('#punishmentDialog').scrollTop = 0;
  const body = $('#workflowBody');
  body.scrollTop = 0;
  body.scrollLeft = 0;
}

function renderWorkflowSteps(step) {
  const order = ['offense', 'recommendation', 'options', 'review'];
  const labels = ['Offense', 'Recommendation', 'Options', 'Review'];
  const current = Math.max(0, order.indexOf(step));
  replaceChildrenOf($('#workflowSteps'), labels.map((label, index) => workflowStepNode(label, index, current)));
}

function workflowStepNode(label, index, current) {
  return element('div', {className: workflowStepClass(index, current)},
    element('span', {text: index + 1}), label);
}

function workflowStepClass(index, current) {
  if (index === current) return 'workflow-step active';
  if (index < current) return 'workflow-step done';
  return 'workflow-step';
}

function renderOffenseStep() {
  const suggested = scenarioOffense();
  replaceChildrenOf($('#workflowBody'),
    stepIntro('What happened?', 'Choose the policy family first. History is evaluated only after the offense is known.'),
    element('div', {className: 'option-grid'}, OFFENSES.map(([key, label]) => offenseChoiceNode(key, label, suggested))));
  replaceChildrenOf($('#workflowFooter'), buttonNode('Cancel', 'button ghost', {cancel: ''}));
  $$('[data-offense]').forEach((button) => button.addEventListener('click', () => chooseOffense(button.dataset.offense)));
  $('[data-cancel]').addEventListener('click', closeWorkflow);
}

function stepIntro(title, detail, eyebrow = null) {
  const content = [];
  if (eyebrow) content.push(element('span', {className: 'eyebrow', text: eyebrow}));
  content.push(element('h3', {text: title}), element('p', {text: detail}));
  return element('div', {className: 'step-intro'}, content);
}

function offenseChoiceNode(key, label, suggested) {
  const isSuggested = key === suggested;
  const choice = buttonNode('', `choice-card${isSuggested ? ' suggested' : ''}`, {offense: key});
  choice.append(element('strong', {text: label}), element('span', {text: offenseHint(key)}));
  if (isSuggested) choice.append(element('small', {text: 'Suggested for this sample'}));
  return choice;
}

function chooseOffense(key) {
  const label = offenseLabel(key);
  state.workflow.offense = {key, label};
  state.workflow.recommendation = recommend(key);
  state.workflow.recommendationEvidenceRevision = state.evidenceRevision;
  state.workflow.step = 'recommendation';
  renderWorkflow();
}

function offenseLabel(key) {
  const match = OFFENSES.find((entry) => entry[0] === key);
  return match ? match[1] : 'Other';
}

function recommend(key) {
  const relevant = state.history.filter((row) => row.ladderRelevant && row.key === key);
  const base = ['hate', 'cheating'].includes(key) ? 3 : key === 'advertising' ? 2 : 1;
  const step = Math.min(4, Math.max(base, relevant.length + 1));
  const consequence = consequenceFor(key, step);
  return {...consequence, relevant, total: state.history.length, step, explanation: recommendationReason(key, relevant.length, step, base)};
}

function consequenceFor(key, step) {
  if (key === 'hate') return {action: 'Ban', scope: 'Discord', duration: step >= 4 ? 'Permanent' : '30 days'};
  if (key === 'cheating') return {action: 'Ban', scope: 'Minecraft', duration: step >= 4 ? 'Permanent' : '30 days'};
  if (key === 'advertising') return advertisingConsequence(step);
  if (key === 'harassment') return [null, warnDiscord(), muteDiscord('1 day'), muteDiscord('7 days'), banDiscord('30 days')][step];
  return [null, warnDiscord(), muteDiscord('2 hours'), muteDiscord('3 days'), banDiscord('30 days')][step];
}

function advertisingConsequence(step) {
  if (step <= 2) return {action: 'Kick', scope: 'Discord', duration: '—'};
  return banDiscord(step === 3 ? '7 days' : 'Permanent');
}

function warnDiscord() { return {action: 'Warning', scope: 'Discord', duration: '—'}; }
function muteDiscord(duration) { return {action: 'Mute', scope: 'Discord', duration}; }
function banDiscord(duration) { return {action: 'Ban', scope: 'Discord', duration}; }

function recommendationReason(key, relevant, step, base) {
  const offense = offenseLabel(key);
  if (base > 1 && relevant === 0) return `${offense} starts at ladder step ${base} because of severity.`;
  const incident = relevant === 0 ? 'first relevant incident'
    : relevant === 1 ? 'second relevant incident'
      : relevant === 2 ? 'third relevant incident' : `${relevant + 1}th relevant incident`;
  return `${offense} — ${incident}; ladder step ${step}.`;
}

function renderRecommendationStep() {
  const w = state.workflow;
  const r = w.recommendation;
  replaceChildrenOf($('#workflowBody'), element('div', {className: 'recommendation-layout'},
    recommendationCard(r), relevantHistoryCard(w, r)));
  replaceChildrenOf($('#workflowFooter'),
    buttonNode('Back', 'button ghost', {back: ''}),
    element('div', {className: 'inline'},
      buttonNode('Custom Punishment', 'button secondary', {custom: ''}),
      buttonNode('Use Recommendation', 'button primary', {useRecommendation: ''})));
  $('[data-back]').addEventListener('click', () => { w.step = 'offense'; renderWorkflow(); });
  $('[data-use-recommendation]').addEventListener('click', () => useRecommendation(false));
  $('[data-custom]').addEventListener('click', () => useRecommendation(true));
}

function recommendationCard(recommendation) {
  const approval = approvalFor(recommendation.action, recommendation.duration);
  const card = element('section', {className: 'recommendation-card'},
    element('div', {className: 'eyebrow', text: 'Recommended'}),
    element('div', {className: 'recommendation-action', text: recommendation.action}),
    element('div', {className: 'recommendation-duration', text: `${recommendation.duration} · ${recommendation.scope}`}),
    element('p', {text: recommendation.explanation}));
  if (approval !== 'None') card.append(element('div', {className: 'approval-note', text: approval}));
  return card;
}

function relevantHistoryCard(workflow, recommendation) {
  const unrelated = recommendation.total - recommendation.relevant.length;
  const card = element('section', {className: 'card'},
    element('h3', {text: `Relevant history for ${workflow.offense.label}`}),
    summaryList([
      ['Total history', recommendation.total], ['Relevant history', recommendation.relevant.length],
      ['Unrelated records', unrelated], ['Ladder step', recommendation.step]
    ]));
  if (recommendation.relevant.length) card.append(element('div', {className: 'relevant-history'}, recommendation.relevant.map(historyCompactNode)));
  else card.append(element('p', {className: 'muted small', text: 'No prior matching incidents.'}));
  return card;
}

function useRecommendation(custom) {
  const w = state.workflow;
  const r = w.recommendation;
  w.custom = custom;
  w.actual = {action: r.action};
  w.scope = r.scope;
  w.duration = r.duration;
  if (custom) seedCustomScenario(w);
  w.step = 'options';
  renderWorkflow();
}

function seedCustomScenario(w) {
  if (state.scenario === 'restrict-one' || state.scenario === 'restrict-many') seedRestrictionScenario(w);
  else if (state.scenario === 'custom') {
    w.actual = {action: 'Kick'};
    w.scope = 'Discord';
    w.duration = '—';
  }
}

function seedRestrictionScenario(w) {
  w.actual = {action: 'Restrict'};
  w.scope = 'Discord';
  w.duration = '3 days';
  if (state.scenario === 'restrict-one') w.restrictionTargets.add('general');
  else {
    ['general', 'market', 'trading'].forEach((target) => w.restrictionTargets.add(target));
    w.restrictMode = 'no-access';
  }
}

function renderOptionsStep() {
  const w = state.workflow;
  const body = [stepIntro(w.custom ? 'Configure the actual action' : 'Confirm action options', '', w.custom ? 'Custom override' : 'Following recommendation')];
  body.push(w.custom ? customControlsNode(w) : actualSummaryNode(w));
  if (w.actual.action === 'Restrict') body.push(restrictionControlsNode(w));
  body.push(evidenceActionsNode(), communicationOptionsNode(w));
  replaceChildrenOf($('#workflowBody'), body);
  replaceChildrenOf($('#workflowFooter'), buttonNode('Back', 'button ghost', {back: ''}), buttonNode('Review action', 'button primary', {review: ''}));
  bindOptionsEvents();
}

function customControlsNode(w) {
  const actions = ['Warning', 'Mute', 'Kick', 'Ban', 'Restrict'];
  const actionSelect = element('select', {id: 'customAction'}, actions.map((value) => optionNode(value, value, w.actual.action === value)));
  const controls = [fieldLabel('Punishment', actionSelect), scopeField(w)];
  if (!['Warning', 'Kick'].includes(w.actual.action)) controls.push(durationField(w));
  return element('section', {className: 'card option-section'},
    element('div', {className: 'field-row wrap'}, controls),
    element('div', {className: 'override-note'}, element('strong', {text: 'Custom override'}),
      element('span', {text: 'This differs from the normal ladder path. Review the action, scope, duration, and approval requirement carefully.'})));
}

function scopeField(w) {
  const restrict = w.actual.action === 'Restrict';
  const select = element('select', {id: 'customScope', disabled: restrict},
    restrict ? optionNode('Discord', 'Discord', true) : [
      optionNode('Discord', 'Discord', w.scope === 'Discord'),
      optionNode('Minecraft', 'Minecraft', w.scope === 'Minecraft'),
      optionNode('Both', 'Both', w.scope === 'Both')
    ]);
  return fieldLabel('Scope', select);
}

function durationField(w) {
  const durations = ['30 minutes', '2 hours', '1 day', '3 days', '7 days', '14 days', '30 days', 'Permanent'];
  return fieldLabel('Duration', element('select', {id: 'customDuration'}, durations.map((value) => optionNode(value, value, w.duration === value))));
}

function fieldLabel(title, control) {
  return element('label', {className: 'field-label'}, title, control);
}

function actualSummaryNode(w) {
  return element('section', {className: 'recommendation-card compact'},
    element('div', {className: 'eyebrow', text: 'Actual action'}),
    element('div', {className: 'recommendation-action', text: w.actual.action}),
    element('div', {className: 'recommendation-duration', text: `${w.duration} · ${w.scope}`}));
}

function evidenceActionsNode() {
  return element('section', {className: 'card option-section'},
    sectionHeadingNode('Evidence & message actions', 'Evidence and Discord deletion are intentionally separate.'),
    summaryList([
      ['Evidence messages', state.evidence.size], ['Delete from Discord', state.deleting.size], ['Preserve evidence', preservedEvidenceCount()]
    ]), buttonNode('Review selected messages', 'button secondary', {reviewMessages: ''}));
}

function communicationOptionsNode(w) {
  return element('section', {className: 'card option-section'},
    element('label', {className: 'checkbox-control prominent'}, element('input', {id: 'dmUserOption', type: 'checkbox', checked: w.dm}), ' DM user with the moderation result'),
    fieldLabel('Staff explanation / case note', element('textarea', {id: 'reasonInput', text: w.reason, placeholder: 'Concise context for the case', attrs: {rows: '3', maxlength: '300'}})));
}

function sectionHeadingNode(title, detail) {
  return element('div', {className: 'section-heading'}, element('div', {}, element('h3', {text: title}), element('p', {text: detail})));
}

function restrictionControlsNode(w) {
  return element('section', {className: 'card option-section'},
    sectionHeadingNode('Discord restriction', 'Limit permissions only in the exact channel or category targets selected below.'),
    restrictionModeField(w),
    fieldLabel('Find channel or category', element('input', {id: 'targetSearch', type: 'search', placeholder: 'Search #channel or category'})),
    element('div', {id: 'restrictionTargetList', className: 'target-picker'}, restrictionTargetNodes(w, '')));
}

function restrictionModeField(w) {
  return element('fieldset', {className: 'segmented-field'}, element('legend', {text: 'Restriction mode'}),
    restrictionModeOption('read-only', 'Read only', 'Can view, cannot send or respond', w.restrictMode === 'read-only'),
    restrictionModeOption('no-access', 'No access', 'Cannot view or access selected locations', w.restrictMode === 'no-access'));
}

function restrictionModeOption(value, title, detail, checked) {
  return element('label', {}, element('input', {type: 'radio', name: 'restrictMode', value, checked}),
    element('span', {}, element('strong', {text: title}), element('small', {text: detail})));
}

function restrictionTargetNodes(w, term) {
  const normalized = term.trim().toLowerCase();
  const targets = RESTRICTION_TARGETS.filter((target) => `${target.label} ${target.detail}`.toLowerCase().includes(normalized));
  if (!targets.length) return emptyState('No matching channel or category');
  return targets.map((target) => restrictionTargetNode(w, target));
}

function restrictionTargetNode(w, target) {
  return element('label', {className: 'target-option'},
    element('input', {type: 'checkbox', checked: w.restrictionTargets.has(target.id), dataset: {restrictionTarget: target.id}}),
    element('span', {className: 'target-type', text: target.type === 'channel' ? '#' : '▣'}),
    element('span', {}, element('strong', {text: target.label}), element('small', {text: `${target.type} · ${target.detail}`})));
}

function bindOptionsEvents() {
  const w = state.workflow;
  $('[data-back]').addEventListener('click', () => { w.step = 'recommendation'; renderWorkflow(); });
  $('[data-review]').addEventListener('click', () => { captureOptions(); w.step = 'review'; renderWorkflow(); });
  $('[data-review-messages]').addEventListener('click', reviewMessagesFromWorkflow);
  $('#dmUserOption').addEventListener('change', (event) => { w.dm = event.target.checked; });
  $('#reasonInput').addEventListener('input', (event) => { w.reason = event.target.value; });
  $('#customAction')?.addEventListener('change', (event) => setCustomAction(event.target.value));
  $('#customScope')?.addEventListener('change', (event) => { w.scope = event.target.value; });
  $('#customDuration')?.addEventListener('change', (event) => { w.duration = event.target.value; });
  $$('[name="restrictMode"]').forEach((input) => input.addEventListener('change', (event) => { w.restrictMode = event.target.value; }));
  $('#targetSearch')?.addEventListener('input', updateRestrictionTargets);
  bindRestrictionTargets();
}

function reviewMessagesFromWorkflow() {
  captureOptions();
  $('#punishmentDialog').close();
  switchView('messages');
  showToast('Message selections are preserved. Reopen Issue Punishment when ready.');
}

function updateRestrictionTargets(event) {
  replaceChildrenOf($('#restrictionTargetList'), restrictionTargetNodes(state.workflow, event.target.value));
  bindRestrictionTargets();
}

function setCustomAction(action) {
  const w = state.workflow;
  w.actual = {action};
  if (['Warning', 'Kick'].includes(action)) w.duration = '—';
  else if (!w.duration || w.duration === '—') w.duration = '3 days';
  if (action === 'Restrict') w.scope = 'Discord';
  renderWorkflow();
}

function bindRestrictionTargets() {
  $$('[data-restriction-target]').forEach((input) => input.addEventListener('change', (event) =>
    toggleSet(state.workflow.restrictionTargets, event.target.dataset.restrictionTarget, event.target.checked)));
}

function captureOptions() {
  const w = state.workflow;
  if ($('#dmUserOption')) w.dm = $('#dmUserOption').checked;
  if ($('#reasonInput')) w.reason = $('#reasonInput').value.trim();
  if ($('#customAction')) w.actual = {action: $('#customAction').value};
  if ($('#customScope')) w.scope = $('#customScope').value;
  if ($('#customDuration')) w.duration = $('#customDuration').value;
}
