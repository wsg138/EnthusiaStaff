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
}

function hardenedRenderOffenseStep() {
  const w = state.workflow;
  if (!w.offenseTab) w.offenseTab = 'discord';
  const offenses = OFFENSES.filter(([key]) => w.offenseTab === 'game' ? key === 'cheating' : key !== 'cheating');
  replaceChildrenOf($('#workflowBody'),
    stepIntro('What happened?', 'Choose the rule family. Each option links to the public rule staff should apply.'),
    punishmentScopeTabs(w.offenseTab),
    element('div',{className:'option-grid'},offenses.map(([key,label]) => hardenedOffenseOptionNode(key,label))));
  replaceChildrenOf($('#workflowFooter'),buttonNode('Cancel','button ghost',{cancel:''}));
  $$('[data-offense-tab]').forEach((button) => button.addEventListener('click', () => { w.offenseTab = button.dataset.offenseTab; renderWorkflow(); }));
  $$('[data-offense]').forEach((button) => button.addEventListener('click', () => chooseOffense(button.dataset.offense)));
  $('[data-cancel]').addEventListener('click',closeWorkflow);
}

function hardenedOffenseOptionNode(key, label) {
  const choice = buttonNode('', 'choice-card', {offense:key});
  choice.append(element('strong',{text:label}),element('span',{text:offenseHint(key)}));
  return element('div',{className:'choice-card-wrap'},choice,policyLinkNode(offensePolicy(key),'View applicable rule'));
}

function hardenedRecommendationCard(recommendation) {
  const policy = offensePolicy(state.workflow?.offense?.key || 'other');
  const approval = approvalFor(recommendation.action,recommendation.duration);
  const card = element('section',{className:'recommendation-card'},
    element('div',{className:'eyebrow',text:'Recommended'}),
    element('div',{className:'recommendation-action',text:recommendation.action}),
    element('div',{className:'recommendation-duration',text:`${recommendation.duration} · ${recommendation.scope}`}),
    element('p',{text:recommendation.explanation}), policyLinkNode(policy,'Open rule'));
  if (approval !== 'None') card.append(element('div',{className:'approval-note',text:approval}));
  return card;
}

function policyLinkNode(policy, label) {
  return element('a',{className:'policy-link',text:`${label}: ${policy.label}`,attrs:{href:policy.href,target:'_blank',rel:'noopener noreferrer'}});
}

function hardenedEvidenceActionsNode() {
  return element('section',{className:'card option-section'},
    sectionHeadingNode('Evidence & message actions','Evidence selection and simulated Discord deletion remain separate.'),
    summaryList([
      ['Discord evidence messages',state.evidence.size],
      ['Marked for simulated deletion',state.deleting.size],
      ['Preserved evidence messages',preservedEvidenceCount()]
    ]),buttonNode('Review selected messages','button secondary',{reviewMessages:''}),
    element('p',{className:'muted small',text:'Nothing is deleted from Discord in staging. Deletion choices are previewed only.'}));
}

function hardenedCommunicationOptionsNode(w) {
  if (w.externalEvidence === undefined) w.externalEvidence = '';
  if (w.approvalConfirmed === undefined) w.approvalConfirmed = false;
  const children = [
    element('label',{className:'checkbox-control prominent'},element('input',{id:'dmUserOption',type:'checkbox',checked:w.dm}),' Include a DM in the simulation preview (not sent)'),
    fieldLabel('Staff explanation / case note',element('textarea',{id:'reasonInput',text:w.reason,placeholder:'Required: explain what happened and why this action fits',attrs:{rows:'3',maxlength:'300'}})),
    element('p',{className:'field-help',text:'Required before Final review; use at least 10 characters.'}),
    fieldLabel('Outside-Discord evidence reference',element('textarea',{id:'externalEvidenceInput',text:w.externalEvidence,placeholder:'Ticket, recording, game log, screenshot set, or other evidence location',attrs:{rows:'2',maxlength:'300'}})),
    element('p',{className:'field-help',text:'Use this when the incident evidence is not a Discord message. A reference is required for most non-warning actions when no Discord evidence is selected.'})
  ];
  if (workflowApprovalRequired(w)) children.push(approvalConfirmationNode(w));
  return element('section',{className:'card option-section'},children);
}

function approvalConfirmationNode(w) {
  return element('label',{className:'checkbox-control prominent approval-confirmation'},
    element('input',{id:'approvalConfirmed',type:'checkbox',checked:w.approvalConfirmed}),
    ' Admin+ approval has been verified for this case (required before simulation confirmation)');
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
  const policy = offensePolicy(w.offense.key);
  const items = [
    ['Target',`${identity.displayName} · ${identity.minecraft}`],
    ['Offense',w.offense.label],
    ['Rule',policy.label],
    ['Ladder recommendation',`${recommendation.action} · ${recommendation.duration}`],
    ['Simulated action',`${w.actual.action}${w.custom ? ' · Custom override' : ''}`],
    ['Scope / platform',w.scope],
    ['Discord evidence',String(state.evidence.size)],
    ['Outside-Discord evidence',workflowExternalEvidenceReady(w) ? 'Referenced' : 'None'],
    ['Simulated message deletion',String(state.deleting.size)],
    ['DM behavior',w.dm ? 'Preview only · not sent' : 'No DM selected'],
    ['Approval',approvalReviewText(w)]
  ];
  if (w.duration !== '—') items.splice(5,0,['Duration',w.duration]);
  return element('div',{className:'review-grid'},items.map(([label,value]) => reviewItemNode(label,value)));
}

function approvalReviewText(w) {
  if (!workflowApprovalRequired(w)) return 'Not required';
  return w.approvalConfirmed ? 'Admin+ approval verified' : 'Admin+ approval required · not verified';
}

function hardenedReviewEvidenceNode(w) {
  const status = workflowReviewStatus(w);
  return element('section',{className:'card review-evidence'},
    sectionHeadingNode('Case readiness','Review every item before confirming the simulation.'),
    readinessChecklistNode(w),
    reviewValidationAlert(status),
    policyLinkNode(offensePolicy(w.offense.key),'Open applicable rule'),
    reviewEvidenceSummaryNode(w),
    staffExplanationNode(w),
    dmPreviewNode(w),
    element('div',{className:'simulation-boundary'},
      element('strong',{text:'Simulation boundary'}),
      element('span',{text:'Confirming this preview does not punish the player, send a DM, change Discord permissions, or delete messages.'})));
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
    element('p',{text:reason || 'Missing — return to Punishment options and add an explanation.'}));
}

function dmPreviewNode(w) {
  const detail = w.dm ? simulatedDmText(w) : 'No DM is selected for this simulation.';
  return element('div',{className:'dm-preview'},element('span',{text:'DM preview'}),element('p',{text:detail}),
    element('small',{text:'Preview only. This staging workflow does not send DMs.'}));
}

function simulatedDmText(w) {
  const duration = w.duration && w.duration !== '—' ? ` for ${w.duration}` : '';
  return `Enthusia moderation: ${w.actual.action}${duration} for ${w.offense.label}. Staff explanation: ${String(w.reason || '').trim()}`;
}

function readinessChecklistNode(workflow) {
  if (!workflow?.actual) {
    return element('div',{className:'readiness-list'},
      readinessRow('Reason','Not started','pending'),
      readinessRow('Evidence',state.evidence.size ? `${state.evidence.size} Discord message${state.evidence.size === 1 ? '' : 's'} selected` : 'Not selected','pending'),
      readinessRow('Notification','Set during punishment options','pending'),
      readinessRow('Approval','Known after an action is selected','pending'));
  }
  const status = workflowReviewStatus(workflow);
  return element('div',{className:'readiness-list'},
    readinessRow('Reason',status.reasonReady ? 'Ready' : 'Missing',status.reasonReady ? 'ready' : 'missing'),
    readinessRow('Evidence',evidenceReadinessText(workflow,status),status.evidenceReady ? 'ready' : 'missing'),
    readinessRow('Notification',workflow.dm ? 'DM preview on · not sent' : 'No DM selected','ready'),
    readinessRow('Approval',approvalReviewText(workflow),status.approvalReady ? 'ready' : 'missing'));
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

function hardenedReviewFooterNode(stale) {
  const status = workflowReviewStatus(state.workflow);
  const right = element('div',{className:'inline'});
  if (stale) right.append(buttonNode('Recalculate','button secondary',{recalculate:''}));
  const confirm = buttonNode('Confirm simulation','button primary',{confirm:''});
  confirm.disabled = stale || !status.ready;
  if (confirm.disabled) confirm.setAttribute('title',stale ? 'Recalculate after evidence changes.' : status.errors.join(' '));
  right.append(confirm);
  return [buttonNode('Back','button ghost',{back:''}),right];
}

installWorkflowOverrides();
