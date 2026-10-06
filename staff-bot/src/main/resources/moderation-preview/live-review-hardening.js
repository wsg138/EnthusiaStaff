'use strict';

function installWorkflowOverrides() {
  window.renderOffenseStep = hardenedRenderOffenseStep;
  window.recommendationCard = hardenedRecommendationCard;
  window.evidenceActionsNode = hardenedEvidenceActionsNode;
  window.communicationOptionsNode = hardenedCommunicationOptionsNode;
  window.bindOptionsEvents = hardenedBindOptionsEvents;
  window.reviewGridNode = hardenedReviewGridNode;
  window.reviewEvidenceNode = hardenedReviewEvidenceNode;
  window.reviewFooterNode = hardenedReviewFooterNode;
  window.staleEvidenceAlert = hardenedStaleEvidenceAlert;
  window.confirmSimulation = hardenedConfirmAction;
  window.renderCompleteStep = hardenedRenderCompleteStep;
}

function hardenedRenderOffenseStep() {
  const w = state.workflow;
  if (!w.offenseTab) w.offenseTab = 'discord';
  const offenses = OFFENSES.filter(([key]) => w.offenseTab === 'game' ? key === 'cheating' : key !== 'cheating');
  replaceChildrenOf($('#workflowBody'),
    stepIntro('What happened?', 'Choose the rule family, then review the exact punishment details.'),
    punishmentScopeTabs(w.offenseTab),
    policyLinkNode({label:'Server rules',href:RULES_BASE_URL},'Open'),
    element('div',{className:'option-grid'},offenses.map(([key,label]) => hardenedOffenseOptionNode(key,label))));
  replaceChildrenOf($('#workflowFooter'),buttonNode('Cancel','button ghost',{cancel:''}));
  $$('[data-offense-tab]').forEach((button) => button.addEventListener('click', () => { w.offenseTab = button.dataset.offenseTab; renderWorkflow(); }));
  $$('[data-offense]').forEach((button) => button.addEventListener('click', () => chooseOffense(button.dataset.offense)));
  $('[data-cancel]').addEventListener('click',closeWorkflow);
}

function hardenedOffenseOptionNode(key, label) {
  const choice = buttonNode('', 'choice-card', {offense:key});
  choice.append(element('strong',{text:label}),element('span',{text:offenseHint(key)}));
  return choice;
}

function hardenedRecommendationCard(recommendation) {
  const approval = approvalFor(recommendation.action,recommendation.duration);
  const card = element('section',{className:'recommendation-card'},
    element('div',{className:'eyebrow',text:'Recommended'}),
    element('div',{className:'recommendation-action',text:recommendation.action}),
    element('div',{className:'recommendation-duration',text:`${recommendation.duration} · ${recommendation.scope}`}),
    element('p',{text:recommendation.explanation}));
  if (approval !== 'None') card.append(element('div',{className:'approval-note',text:approval}));
  return card;
}

function policyLinkNode(policy, label) {
  return element('a',{className:'policy-link',text:`${label}: ${policy.label}`,attrs:{href:policy.href,target:'_blank',rel:'noopener noreferrer'}});
}

function hardenedEvidenceActionsNode() {
  return element('section',{className:'card option-section'},
    sectionHeadingNode('Evidence & message actions','Evidence and deletion choices remain separate.'),
    summaryList([
      ['Discord evidence messages',state.evidence.size],
      ['Marked for deletion',state.deleting.size],
      ['Preserved evidence messages',preservedEvidenceCount()]
    ]),buttonNode('Review selected messages','button secondary',{reviewMessages:''}));
}

function hardenedCommunicationOptionsNode(w) {
  if (w.externalEvidence === undefined) w.externalEvidence = '';
  if (w.approvalConfirmed === undefined) w.approvalConfirmed = false;
  const children = [
    element('label',{className:'checkbox-control prominent'},element('input',{id:'dmUserOption',type:'checkbox',checked:w.dm}),' Include a DM with this action'),
    fieldLabel('Staff explanation / case note',element('textarea',{id:'reasonInput',text:w.reason,placeholder:'Optional — add any useful context for the player or case',attrs:{rows:'3',maxlength:'300'}})),
    element('p',{className:'field-help',text:'Optional. If left blank, the record and player notification will state that no additional staff explanation was provided.'}),
    fieldLabel('Outside-Discord evidence reference',element('textarea',{id:'externalEvidenceInput',text:w.externalEvidence,placeholder:'Ticket, recording, game log, screenshot set, or other evidence location',attrs:{rows:'2',maxlength:'300'}})),
    element('p',{className:'field-help',text:'Use this when the incident evidence is not a Discord message. A reference is required for most non-warning actions when no Discord evidence is selected.'})
  ];
  if (workflowApprovalRequired(w)) children.push(approvalConfirmationNode(w));
  return element('section',{className:'card option-section'},children);
}

function approvalConfirmationNode(w) {
  return element('label',{className:'checkbox-control prominent approval-confirmation'},
    element('input',{id:'approvalConfirmed',type:'checkbox',checked:w.approvalConfirmed}),
    ' Admin+ approval has been verified for this case');
}

function hardenedBindOptionsEvents() {
  const w = state.workflow;
  $('[data-back]').addEventListener('click',() => { w.step = 'recommendation'; renderWorkflow(); });
  $('[data-review]').addEventListener('click',() => reviewOptions(w));
  $('[data-review-messages]').addEventListener('click',reviewMessagesFromWorkflow);
  $('#dmUserOption').addEventListener('change',(event) => { w.dm = event.target.checked; });
  $('#reasonInput').addEventListener('input',(event) => { w.reason = event.target.value; });
  $('#externalEvidenceInput')?.addEventListener('input',(event) => { w.externalEvidence = event.target.value; });
  $('#approvalConfirmed')?.addEventListener('change',(event) => { w.approvalConfirmed = event.target.checked; });
  bindCustomOptionEvents(w);
  $$('[name="restrictMode"]').forEach((input) => input.addEventListener('change',(event) => { w.restrictMode = event.target.value; }));
  $('#targetSearch')?.addEventListener('input',updateRestrictionTargets);
  bindRestrictionTargets();
}

function bindCustomOptionEvents(w) {
  $('#customAction')?.addEventListener('change',(event) => {
    w.approvalConfirmed = false;
    setCustomAction(event.target.value);
  });
  $('#customScope')?.addEventListener('change',(event) => { w.scope = event.target.value; });
  $('#customDuration')?.addEventListener('change',(event) => {
    w.duration = event.target.value;
    w.approvalConfirmed = false;
    renderWorkflow();
  });
}

function reviewOptions(w) {
  captureOptions();
  captureHardeningOptions(w);
  const status = workflowReviewStatus(w);
  if (workflowCanEnterReview(w)) {
    w.step = 'review';
    renderWorkflow();
    return;
  }
  showToast(status.errors[0] || 'Finish the required case fields before Final review.',true);
  focusFirstMissingReviewField(status);
}

function captureHardeningOptions(w) {
  if ($('#externalEvidenceInput')) w.externalEvidence = $('#externalEvidenceInput').value.trim();
  if ($('#approvalConfirmed')) w.approvalConfirmed = $('#approvalConfirmed').checked;
}

function focusFirstMissingReviewField(status) {
  if (!status.reasonReady) $('#reasonInput')?.focus();
  else if (!status.evidenceReady) $('#externalEvidenceInput')?.focus();
  else if (!status.restrictionReady) $('#targetSearch')?.focus();
}

function hardenedReviewGridNode(w, recommendation) {
  const evidence = reviewEvidenceCountText(w);
  const items = [
    ['Target',`${identity.displayName} · ${identity.minecraft}`],
    ['Offense',w.offense.label],
    ['Action',`${w.actual.action}${w.custom ? ' · Custom override' : ''}`],
    ['Platform',w.scope],
    ['Evidence',evidence],
    ['Player notification',w.dm ? 'Included' : 'Not included']
  ];
  if (w.duration !== '—') items.splice(3,0,['Duration',w.duration]);
  if (w.custom) {
    const duration = recommendation.duration && recommendation.duration !== '—'
      ? ' · ' + recommendation.duration : '';
    items.splice(2,0,['Recommended',recommendation.action + duration]);
  }
  if (state.deleting.size) items.push(['Messages to delete',String(state.deleting.size)]);
  if (workflowApprovalRequired(w)) items.push(['Approval',approvalReviewText(w)]);
  return element('div',{className:'review-grid'},items.map(([label,value]) => reviewItemNode(label,value)));
}

function reviewEvidenceCountText(workflow) {
  const parts = [];
  if (state.evidence.size) parts.push(`${state.evidence.size} Discord message${state.evidence.size === 1 ? '' : 's'}`);
  if (workflowExternalEvidenceReady(workflow)) parts.push('Outside reference');
  return parts.length ? parts.join(' + ') : 'None';
}

function approvalReviewText(w) {
  if (!workflowApprovalRequired(w)) return 'Not required';
  return w.approvalConfirmed ? 'Admin+ approval verified' : 'Admin+ approval required · not verified';
}

function hardenedReviewEvidenceNode(w) {
  const status = workflowReviewStatus(w);
  return element('section',{className:'card review-evidence'},
    sectionHeadingNode('Case readiness','Review every item before confirming the action.'),
    readinessChecklistNode(w),
    reviewValidationAlert(status),
    reviewEvidenceSummaryNode(w),
    staffExplanationNode(w),
    dmPreviewNode(w),
    testEnvironmentBoundary());
}

function reviewValidationAlert(status) {
  if (status.ready) return element('div',{className:'alert success'},element('strong',{text:'Required review fields are ready.'}));
  return element('div',{className:'alert warning validation-errors'},
    element('strong',{text:'Cannot confirm yet'}),
    element('ul',{},status.errors.map((error) => element('li',{text:error}))));
}

function reviewEvidenceSummaryNode(w) {
  const outside = String(w.externalEvidence || '').trim();
  return element('div',{className:'review-evidence-groups'},
    element('div',{},element('h4',{text:'Discord evidence'}),evidenceSummaryNode()),
    element('div',{},element('h4',{text:'Outside-Discord evidence'}),
      outside ? element('p',{text:outside}) : element('p',{className:'muted',text:'No outside evidence reference provided.'})));
}

function staffExplanationNode(w) {
  const reason = String(w.reason || '').trim();
  return element('div',{className:'staff-reason'},element('span',{text:'Staff explanation'}),
    element('p',{text:reason || 'No additional staff explanation was provided.'}));
}

function dmPreviewNode(w) {
  return element('div',{className:'dm-preview'},
    element('span',{text:'Notification message'}),
    w.dm ? punishmentNotificationPreview(w) : element('p',{text:'No DM is included with this action.'}));
}

function punishmentNotificationPreview(w) {
  const action = notificationPreviewAction(w);
  const rows = [
    element('strong',{className:'notification-preview-title',text:'Punishment Alert'}),
    element('p',{text:`You have been ${action} on the Enthusia SMP Discord${notificationPreviewDuration(w)}.`}),
    notificationPreviewReason(w),
    notificationPreviewExplanation(w),
    notificationPreviewExpiry(w,action),
    element('p',{className:'muted small',text:
      'Appeal: Enthusia Discord appeal channel or Enthusia.info/appeal'})
  ].filter(Boolean);
  return element('div',{className:'notification-preview-body'},rows);
}

function notificationPreviewAction(w) {
  const actions = {Warning:'warned',Mute:'muted',Kick:'kicked',Ban:'banned',Restrict:'restricted'};
  return actions[w.actual?.action] || String(w.actual?.action || 'action').toLowerCase();
}

function notificationPreviewDuration(w) {
  if (!w.duration || w.duration === '—') return '';
  return ` for ${w.duration}`;
}

function notificationPreviewReason(w) {
  return element('p',{},element('strong',{text:'Reason: '}),
    document.createTextNode(w.offense?.label || 'Custom'));
}

function notificationPreviewExplanation(w) {
  const explanation = String(w.reason || '').trim() || 'No additional staff explanation was provided.';
  return element('p',{},element('strong',{text:'Staff explanation: '}),
    document.createTextNode(explanation));
}

function notificationPreviewExpiry(w,action) {
  if (['warning','kick'].includes(action)) return null;
  const expiry = w.duration === 'Permanent'
    ? 'Permanent'
    : 'Discord timestamp and live countdown generated from the confirmed action time';
  return element('p',{},element('strong',{text:'Expires: '}),document.createTextNode(expiry));
}

function actionDmText(w) {
  const action = String(w.actual?.action || 'action').toLowerCase();
  const duration = w.duration && w.duration !== '—' ? ` for ${w.duration}` : '';
  return `Punishment Alert — You have been ${action} on the Enthusia SMP Discord${duration}. Reason: ${w.offense?.label || 'Custom'}`;
}

function testEnvironmentBoundary() {
  return element('div',{className:'simulation-boundary'},
    element('strong',{text:'Simulation only'}),
    element('span',{text:'This panel reviews the action but does not send punishments or DMs, change Discord permissions, or delete messages.'}));
}

function readinessChecklistNode(workflow) {
  if (!workflow?.actual) return pendingReadinessChecklist();
  const status = workflowReviewStatus(workflow);
  return element('div',{className:'readiness-list'},
    readinessRow('Reason',status.reasonReady ? 'Ready' : 'Missing',status.reasonReady ? 'ready' : 'missing'),
    readinessRow('Evidence',evidenceReadinessText(workflow,status),status.evidenceReady ? 'ready' : 'missing'),
    readinessRow('Notification',workflow.dm ? 'DM included' : 'No DM selected','ready'),
    readinessRow('Approval',approvalReviewText(workflow),status.approvalReady ? 'ready' : 'missing'));
}

function pendingReadinessChecklist() {
  return element('div',{className:'readiness-list'},
    readinessRow('Reason','Not started','pending'),
    readinessRow('Evidence',state.evidence.size ? `${state.evidence.size} Discord message${state.evidence.size === 1 ? '' : 's'} selected` : 'Not selected','pending'),
    readinessRow('Notification','Set during punishment options','pending'),
    readinessRow('Approval','Known after an action is selected','pending'));
}

function evidenceReadinessText(workflow, status) {
  if (!actionNeedsEvidence(workflow)) return state.evidence.size || workflowExternalEvidenceReady(workflow) ? 'Evidence attached' : 'Optional for this warning';
  if (state.evidence.size) return `${state.evidence.size} Discord evidence message${state.evidence.size === 1 ? '' : 's'}`;
  if (workflowExternalEvidenceReady(workflow)) return 'Outside-Discord evidence referenced';
  return status.evidenceReady ? 'Ready' : 'Missing';
}

function readinessRow(label, value, tone) {
  return element('div',{className:'readiness-row'},element('span',{text:label}),element('strong',{className:`readiness-state ${tone}`,text:value}));
}

function hardenedStaleEvidenceAlert() {
  return element('div',{className:'alert warning'},
    element('strong',{text:'Evidence changed after the recommendation.'}),
    element('span',{text:'Recalculate so the final review matches the current incident.'}));
}

function hardenedReviewFooterNode(stale) {
  const status = workflowReviewStatus(state.workflow);
  const right = element('div',{className:'inline'});
  if (stale) right.append(buttonNode('Recalculate','button secondary',{recalculate:''}));
  const confirm = buttonNode('Confirm action','button primary',{confirm:''});
  confirm.disabled = stale || !status.ready;
  if (confirm.disabled) confirm.setAttribute('title',stale ? 'Recalculate after evidence changes.' : status.errors.join(' '));
  right.append(confirm);
  return [buttonNode('Back','button ghost',{back:''}),right];
}

async function hardenedConfirmAction() {
  if (!state.session) {
    showToast('Session unavailable. Reopen from Discord.', true);
    return;
  }
  const button = $('[data-confirm]');
  if (button) button.disabled = true;
  try {
    const response = await fetch('/api/simulate', simulationRequest(state.session));
    if (!response.ok) throw new Error('Action rejected');
    await response.json();
    state.workflow.step = 'complete';
    renderWorkflow();
    showToast('Action review complete.');
  } catch {
    showToast('Action review could not be completed. Reopen the panel from Discord if the session expired.', true);
    if (button) button.disabled = false;
  }
}

function hardenedRenderCompleteStep() {
  $('#workflowTitle').textContent = 'Complete';
  $('#workflowSteps').replaceChildren();
  replaceChildrenOf($('#workflowBody'), element('div', {className:'completion-state'},
    element('div', {className:'completion-icon', text:'✓', attrs:{'aria-hidden':'true'}}),
    element('h3', {text:'Action review complete'}),
    element('p', {text:'Review completed. No changes were sent.'}),
    element('span', {text:'No live moderation action was applied.'})));
  replaceChildrenOf($('#workflowFooter'), buttonNode('Done','button primary',{done:''}));
  $('[data-done]').addEventListener('click',closeWorkflow);
}

installWorkflowOverrides();
