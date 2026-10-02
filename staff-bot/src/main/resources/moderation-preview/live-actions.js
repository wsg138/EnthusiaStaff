'use strict';

let liveActionCapabilities = null;
const actionLoadSession = window.loadSession;
window.loadSession = async function () {
  await actionLoadSession();
  if (state.session?.staging !== false) return;
  try { liveActionCapabilities = await requestModerationAction('capabilities', {}); }
  catch { liveActionCapabilities = null; }
};

async function requestModerationAction(operation, input) {
  requireModerationActionSession(operation);
  const proofResponse = await requestActionProof(operation, actionRequestOptions(input));
  if (!proofResponse.ok) throw new Error('Action session rejected. Reopen from Discord.');
  const proof = await proofResponse.json();
  requireValidActionProof(proof, operation);
  const result = await submitActionProof(operation, directReadRequest(proof));
  if (!result.ok) throw moderationActionError(result.status);
  return result.json();
}

function requireModerationActionSession(operation) {
  if (!state.session || !['capabilities','prepare','confirm','status'].includes(operation)) {
    throw new Error('Session unavailable');
  }
}

function actionRequestOptions(input) {
  return {
    method:'POST', cache:'no-store', headers:{'Content-Type':'application/json','X-Preview-Csrf':state.session.csrfToken},
    body:JSON.stringify(input)
  };
}

function requestActionProof(operation, options) {
  switch (operation) {
    case 'capabilities': return fetch('/api/actions/capabilities', options);
    case 'prepare': return fetch('/api/actions/prepare', options);
    case 'confirm': return fetch('/api/actions/confirm', options);
    case 'status': return fetch('/api/actions/status', options);
    default: throw new Error('Session unavailable');
  }
}

function requireValidActionProof(proof, operation) {
  if (proof.origin !== DIRECT_READ_ORIGIN || proof.path !== '/v1/moderation/actions/' + operation
      || proof.method !== 'POST' || !validDirectReadBody(proof.body) || !validDirectReadAuthentication(proof)) {
    throw new Error('Action proof rejected');
  }
}

function submitActionProof(operation, request) {
  switch (operation) {
    case 'capabilities': return fetch('https://moderation-read-staging.enthusia.info/v1/moderation/actions/capabilities', request);
    case 'prepare': return fetch('https://moderation-read-staging.enthusia.info/v1/moderation/actions/prepare', request);
    case 'confirm': return fetch('https://moderation-read-staging.enthusia.info/v1/moderation/actions/confirm', request);
    case 'status': return fetch('https://moderation-read-staging.enthusia.info/v1/moderation/actions/status', request);
    default: throw new Error('Session unavailable');
  }
}

function moderationActionError(status) {
  if (status === 403) return new Error('Current staff authority denied this action.');
  if (status === 400) return new Error('Action rejected. Check the target, duration and permissions, then prepare again.');
  return new Error('Moderation service unavailable. Check status before submitting another action.');
}

function liveActionInput(w) {
  requireLiveActionContext(w);
  const type = liveConsequenceType(w.actual.action);
  const duration = type === 'WARNING' || type === 'KICK' ? 'instant' : actionDuration(w.duration);
  const intent = {type, duration, reason:w.offense.label, explanation:liveExplanation(w), restriction:null};
  intent.restriction = liveRestriction(w, type);
  const input = {targetKey:liveModeration.bootstrap?.targetKey, intent};
  if (w.liveScope === 'Both') {
    input.scope = 'BOTH';
    input.minecraftTarget = w.minecraftTarget;
    input.minecraftIntent = {reasonId:w.minecraftReason, explanation:w.minecraftExplanation || ''};
  }
  return input;
}

function requireLiveActionContext(workflow) {
  if (!liveActionCapabilities?.discordEnabled) throw new Error('Discord enforcement is not enabled yet.');
  if (workflow.liveScope === 'Both') {
    if (!liveActionCapabilities?.bothEnabled) throw new Error('Both-platform enforcement is not enabled.');
    if (!workflow.minecraftTarget || !workflow.minecraftReason) {
      throw new Error('Select a linked Minecraft account and configured Minecraft reason.');
    }
  } else if (workflow.scope !== 'Discord') {
    throw new Error('Minecraft enforcement has not passed activation checks.');
  }
  if (state.deleting.size) throw new Error('Clear deletion selections. Message deletion is not enabled.');
  if (!workflow.dm) throw new Error('Live actions require a target notification.');
}

function liveConsequenceType(action) {
  const types = {Warning:'WARNING', Mute:'MUTE', Kick:'KICK', Ban:'BAN', Restrict:'CHANNEL_RESTRICTION'};
  const type = types[action];
  if (!type) throw new Error('Unsupported action');
  return type;
}

function liveExplanation(workflow) {
  const evidence = [...state.evidence].map(id => 'Discord message reference: ' + id);
  if (workflow.externalEvidence) evidence.push('External evidence reference: ' + workflow.externalEvidence);
  const explanation = [workflow.reason, ...evidence].filter(Boolean).join('\n');
  if (explanation.length > 2000) throw new Error('Evidence references and explanation exceed 2000 characters.');
  return explanation;
}

function liveRestriction(workflow, type) {
  if (type !== 'CHANNEL_RESTRICTION') return null;
  const targets = restrictionTargetSelections(workflow);
  if (targets.length !== 1) throw new Error('Select exactly one channel or category per restriction.');
  return {kind:targets[0].type.toUpperCase(), snowflake:targets[0].id,
    mode:workflow.restrictMode === 'read-only' ? 'READ_ONLY' : 'NO_ACCESS'};
}

function actionDuration(label) {
  if (label === 'Permanent') return 'permanent';
  const match = /^([1-9][0-9]*) (minutes?|hours?|days?)$/.exec(label);
  if (!match) throw new Error('Select a valid duration.');
  return match[1] + ({m:'m',h:'h',d:'d'}[match[2][0]]);
}

const simulationReviewStep = window.renderReviewStep;
window.renderReviewStep = function () {
  simulationReviewStep();
  if (state.session?.staging !== false) return;
  const w = state.workflow;
  const confirm = $('[data-confirm]');
  if (confirm) { confirm.disabled = true; confirm.textContent = 'Preparing live action…'; }
  w.livePrepared = null;
  try {
    const input = liveActionInput(w);
    requestModerationAction('prepare', input).then(prepared => {
      if (state.workflow !== w || w.step !== 'review') return;
      w.livePrepared = {...prepared, targetKey:input.targetKey};
      $('#workflowBody').appendChild(element('div',{className:'alert warning'},
        element('strong',{text:'Server-prepared live action'}),
        element('span',{text:`${prepared.intent.type} · ${prepared.targetUserId} · ${prepared.intent.length.kind}. Target notifications are included. Authority is checked again on confirmation.`})));
      if (confirm) { confirm.disabled = false; confirm.textContent = 'Confirm live action'; }
    }).catch(error => { if (state.workflow === w) showToast(error.message, true); });
  } catch (error) {
    if (confirm) confirm.textContent = 'Live action unavailable';
    $('#workflowBody').appendChild(element('div',{className:'alert warning',text:error.message}));
  }
};

const simulationConfirm = window.confirmSimulation;
window.confirmSimulation = async function () {
  if (state.session?.staging !== false) return simulationConfirm();
  const workflow = state.workflow;
  if (!readyForLiveConfirmation(workflow)) return;
  await submitLiveConfirmation(workflow);
};

function readyForLiveConfirmation(workflow) {
  if (!workflow.livePrepared || workflow.submitting) return false;
  if (workflow.stale || workflow.recommendationEvidenceRevision !== state.evidenceRevision) {
    showToast('Evidence changed. Recalculate and prepare the action again.',true);
    return false;
  }
  return true;
}

async function submitLiveConfirmation(workflow) {
  workflow.submitting = true;
  $('[data-confirm]').disabled = true;
  const input = {targetKey:workflow.livePrepared.targetKey, confirmationId:workflow.livePrepared.confirmationId};
  if (workflow.liveScope === 'Both') {
    input.scope = 'BOTH';
    input.minecraftTarget = workflow.minecraftTarget;
  }
  try {
    workflow.liveStatus = await requestModerationAction('confirm', input);
    workflow.step = 'complete';
    renderWorkflow();
    await pollLiveActionStatus(workflow, input);
  } catch (error) {
    await recoverLiveActionStatus(workflow, input, error);
  } finally {
    workflow.submitting = false;
  }
}

async function pollLiveActionStatus(workflow, input) {
  for (let attempt = 0; attempt < 12 && workflow.liveStatus.state === 'PENDING_APPLY'; attempt += 1) {
    await new Promise(resolve => setTimeout(resolve, 1500));
    workflow.liveStatus = await requestModerationAction('status', input);
    if (state.workflow === workflow) renderWorkflow();
  }
}

async function recoverLiveActionStatus(workflow, input, error) {
  try {
    workflow.liveStatus = await requestModerationAction('status', input);
    workflow.step = 'complete';
    renderWorkflow();
  } catch {
    showToast(error.message + ' Do not submit a replacement until its status is checked.',true);
  }
}

const simulationComplete = window.renderCompleteStep;
window.renderCompleteStep = function () {
  if (state.session?.staging !== false) return simulationComplete();
  const result = state.workflow?.liveStatus;
  $('#workflowTitle').textContent = 'Live action status';
  $('#workflowSteps').replaceChildren();
  const rows = [
    element('h3',{text:result?.state || 'Status unavailable'}),
    element('p',{text:result?.externalApplied ? 'Discord applied the action.' : 'Discord has not confirmed the effect.'}),
    element('p',{text:'Target notification: ' + (result?.dmOutcome || 'Unknown')}),
    element('p',{text:'Punishment ID: ' + (result?.punishmentId || 'Unknown')})
  ];
  if (result?.caseId) rows.push(element('p',{text:'Minecraft case: ' + result.caseId
    + (result.minecraftCommitted ? ' · committed' : '')}));
  replaceChildrenOf($('#workflowBody'), element('section',{className:'card'},rows));
  replaceChildrenOf($('#workflowFooter'),buttonNode('Done','button primary',{done:''}));
  $('[data-done]').addEventListener('click',closeWorkflow);
};

const simulationBoundary = window.testEnvironmentBoundary;
window.testEnvironmentBoundary = function () {
  if (state.session?.staging !== false) return simulationBoundary();
  return element('div',{className:'simulation-boundary'},element('strong',{text:liveActionCapabilities?.discordEnabled ? 'Live Discord moderation' : 'Discord enforcement disabled'}),
    element('span',{text:liveActionCapabilities?.discordEnabled
      ? 'Confirming applies a durable Discord punishment and queues a target notification. Evidence references are recorded; message contents are not archived and no messages are deleted.'
      : 'Live messages and staff data are connected. Punishment confirmation is unavailable until the enforcement policy is activated.'}));
};

const simulationApprovalRequired = window.workflowApprovalRequired;
window.workflowApprovalRequired = function (workflow) {
  return state.session?.staging === false ? false : simulationApprovalRequired(workflow);
};
const simulationApprovalText = window.approvalReviewText;
window.approvalReviewText = function (workflow) {
  return state.session?.staging === false ? 'Current authority checked by the server' : simulationApprovalText(workflow);
};
const simulationScopeField = window.scopeField;
window.scopeField = function (workflow) {
  if (state.session?.staging !== false) return simulationScopeField(workflow);
  const both = workflow.liveScope === 'Both';
  workflow.scope = both ? 'Both' : 'Discord';
  return fieldLabel('Scope',element('select',{id:'customScope',disabled:true},
    optionNode(workflow.scope, workflow.scope, true)));
};
const simulationOffenseStep = window.renderOffenseStep;
window.renderOffenseStep = function () {
  simulationOffenseStep();
  if (state.session?.staging !== false) return;
  $('[data-offense-tab="game"]')?.remove();
};
