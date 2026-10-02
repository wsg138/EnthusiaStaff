'use strict';

let minecraftWorkflow = null;
const originalMinecraftBoundary = window.testEnvironmentBoundary;
window.testEnvironmentBoundary = function () {
  if (state.session?.staging !== false || !liveActionCapabilities?.minecraftEnabled) return originalMinecraftBoundary();
  return element('div',{className:'simulation-boundary'},
    element('strong',{text:'Live Minecraft moderation'}),
    element('span',{text:'Configured punishments apply to the network after confirmation or required staff approval. Discord enforcement is '
      + (liveActionCapabilities.discordEnabled ? 'enabled.' : 'disabled.') + ' Message deletion is unavailable.'}));
};
const originalLiveOpenWorkflow = window.openWorkflow;
const originalLiveRenderWorkflow = window.renderWorkflow;
window.renderWorkflow = function () {
  return minecraftWorkflow ? renderMinecraftPunishment() : originalLiveRenderWorkflow();
};
window.openWorkflow = function () {
  if (state.session?.staging !== false || !liveActionCapabilities?.minecraftEnabled) return originalLiveOpenWorkflow();
  if (state.deleting.size) return showToast('Clear message deletion selections before issuing a Minecraft punishment.', true);
  minecraftWorkflow = {target:'', reason:'', explanation:'', prepared:null, result:null, busy:false, uncertain:false};
  state.workflow = {minecraft:true};
  const accounts = liveModeration.bootstrap?.linkedAccounts || [];
  if (accounts.length === 1) minecraftWorkflow.target = accounts[0].playerId;
  if (liveActionCapabilities.discordEnabled) {
    renderScopeChoice();
    return;
  }
  renderMinecraftPunishment();
  $('#punishmentDialog').showModal();
  $('#minecraftTarget')?.focus();
};

function renderScopeChoice() {
  $('#workflowTitle').textContent = 'Choose punishment scope';
  $('#workflowSteps').replaceChildren();
  $('#workflowBody').replaceChildren(element('p',{text:'Choose the account and service this punishment applies to.'}));
  const minecraft = buttonNode('Minecraft','button primary',{});
  minecraft.addEventListener('click',renderMinecraftPunishment);
  const discord = buttonNode('Discord','button secondary',{});
  discord.addEventListener('click',() => {
    minecraftWorkflow = null;
    $('#punishmentDialog').close();
    originalLiveOpenWorkflow();
  });
  const buttons = [minecraft,discord];
  if (liveActionCapabilities?.bothEnabled) {
    const both = buttonNode('Both','button secondary',{});
    both.addEventListener('click',openBothWorkflow);
    buttons.push(both);
  }
  $('#workflowFooter').replaceChildren(...buttons);
  $('#punishmentDialog').showModal();
}

function openBothWorkflow() {
  const selectedTarget = minecraftWorkflow?.target || '';
  minecraftWorkflow = null;
  $('#punishmentDialog').close();
  originalLiveOpenWorkflow();
  const workflow = state.workflow;
  workflow.liveScope = 'Both';
  workflow.minecraftTarget = selectedTarget;
  workflow.minecraftReason = '';
  workflow.minecraftExplanation = '';
  renderWorkflow();
}

const bothOptionsRenderer = window.renderOptionsStep;
window.renderOptionsStep = function () {
  bothOptionsRenderer();
  const workflow = state.workflow;
  if (!bothOptionsActive(workflow)) return;
  $('#workflowBody').appendChild(bothMinecraftOptions(workflow));
};

function bothOptionsActive(workflow) {
  return state.session?.staging === false && workflow?.liveScope === 'Both';
}

function bothMinecraftOptions(workflow) {
  const target = bothMinecraftTargetSelect(workflow);
  const reason = bothMinecraftReasonSelect(workflow);
  const explanation = bothMinecraftExplanation(workflow);
  bindBothMinecraftInputs(workflow, target, reason, explanation);
  return element('section',{className:'card option-section'},
    sectionHeadingNode('Minecraft side','The selected configured reason is re-evaluated on confirmation.'),
    fieldLabel('Linked Minecraft account',target),
    fieldLabel('Configured Minecraft reason',reason),
    fieldLabel('Minecraft explanation',explanation));
}

function bothMinecraftTargetSelect(workflow) {
  const target = element('select',{id:'bothMinecraftTarget'},optionNode('','Select linked Minecraft account',true));
  for (const account of liveModeration.bootstrap?.linkedAccounts || []) {
    target.appendChild(optionNode(account.playerId,
      (account.username || account.playerId) + (account.main ? ' · main' : ''),
      workflow.minecraftTarget === account.playerId));
  }
  return target;
}

function bothMinecraftReasonSelect(workflow) {
  const reason = element('select',{id:'bothMinecraftReason'},optionNode('','Select configured reason',true));
  for (const option of liveActionCapabilities.minecraftReasons || []) {
    reason.appendChild(optionNode(option.id, option.family + ' — ' + option.label,
      workflow.minecraftReason === option.id));
  }
  return reason;
}

function bothMinecraftExplanation(workflow) {
  return element('textarea',{id:'bothMinecraftExplanation',value:workflow.minecraftExplanation || '',
    attrs:{maxlength:4000,rows:4},placeholder:'Minecraft case explanation'});
}

function bindBothMinecraftInputs(workflow, target, reason, explanation) {
  target.addEventListener('change',() => { workflow.minecraftTarget = target.value; });
  reason.addEventListener('change',() => { workflow.minecraftReason = reason.value; });
  explanation.addEventListener('input',() => { workflow.minecraftExplanation = explanation.value; });
}

const bothReviewRenderer = window.renderReviewStep;
window.renderReviewStep = function () {
  bothReviewRenderer();
  const workflow = state.workflow;
  if (state.session?.staging !== false || workflow?.liveScope !== 'Both') return;
  const selected = (liveActionCapabilities.minecraftReasons || [])
    .find(option => option.id === workflow.minecraftReason);
  $('#workflowBody').appendChild(element('section',{className:'card'},
    element('h3',{text:'Minecraft side'}),
    summaryList([
      ['Player',workflow.minecraftTarget || 'Not selected'],
      ['Reason',selected ? selected.family + ' — ' + selected.label : 'Not selected'],
      ['Explanation',workflow.minecraftExplanation || 'None']
    ])));
};

$('#punishmentDialog').addEventListener('cancel', event => {
  if (minecraftWorkflow?.busy) event.preventDefault();
});
$('#punishmentDialog').addEventListener('close', () => {
  minecraftWorkflow = null;
  $('#closeWorkflow').disabled = false;
});

function minecraftActionPayload(workflow, operation) {
  const payload = {targetKey:liveModeration.bootstrap?.targetKey, minecraftTarget:workflow.prepared?.targetId || workflow.target};
  if (operation === 'prepare') payload.minecraftIntent = {reasonId:workflow.reason, explanation:workflow.explanation};
  else payload.confirmationId = workflow.prepared.confirmationId;
  return payload;
}

async function performMinecraftAction(operation) {
  const workflow = minecraftWorkflow;
  if (!workflow || workflow.busy) return;
  workflow.busy = true;
  renderMinecraftPunishment();
  try {
    const result = await requestModerationAction(operation, minecraftActionPayload(workflow, operation));
    if (minecraftWorkflow !== workflow) return;
    applyMinecraftResult(workflow, operation, result);
  } catch (error) {
    recordMinecraftFailure(workflow, operation, error);
  } finally {
    workflow.busy = false;
    if (minecraftWorkflow === workflow) renderMinecraftPunishment();
  }
}

function applyMinecraftResult(workflow, operation, result) {
  if (operation === 'prepare') {
    workflow.prepared = result;
    return;
  }
  workflow.result = result;
  workflow.uncertain = false;
}

function recordMinecraftFailure(workflow, operation, error) {
  if (operation === 'confirm' || operation === 'status') workflow.uncertain = true;
  showToast(operation === 'prepare' ? error.message
    : 'The action outcome is unconfirmed. Check this confirmation’s status before preparing another punishment.', true);
}

function renderMinecraftPunishment() {
  const workflow = minecraftWorkflow;
  if (!workflow) return;
  const body = $('#workflowBody');
  const footer = $('#workflowFooter');
  prepareMinecraftFrame(workflow, body, footer);
  if (!workflow.prepared) {
    renderMinecraftPrepare(workflow, body, footer);
    return;
  }
  renderPreparedMinecraftSummary(workflow, body);
  if (completedMinecraftResult(workflow)) {
    renderMinecraftResult(workflow, body, footer);
    return;
  }
  renderMinecraftConfirmation(workflow, body, footer);
}

function prepareMinecraftFrame(workflow, body, footer) {
  $('#workflowTitle').textContent = workflow.result && workflow.result.state !== 'PREPARED'
    ? 'Minecraft punishment status' : workflow.prepared ? 'Review Minecraft punishment' : 'Minecraft punishment';
  $('#workflowSteps').replaceChildren();
  $('#closeWorkflow').disabled = workflow.busy;
  $('#punishmentDialog').setAttribute('aria-busy', String(workflow.busy));
  body.replaceChildren();
  footer.replaceChildren();
  body.appendChild(element('p',{className:'muted',text:'Uses the network’s configured reasons, escalation rules, and current staff authority. Discord enforcement stays separate.'}));
}

function renderMinecraftPrepare(workflow, body, footer) {
  const target = element('input',{id:'minecraftTarget',value:workflow.target,placeholder:'Minecraft username or UUID',attrs:{maxlength:36,autocomplete:'off'}});
  const reasons = element('select',{id:'minecraftReason'},optionNode('','Select a configured reason',true));
  for (const reason of liveActionCapabilities.minecraftReasons || []) {
    reasons.appendChild(optionNode(reason.id, reason.family + ' — ' + reason.label, workflow.reason === reason.id));
  }
  const explanation = element('textarea',{id:'minecraftExplanation',value:workflow.explanation,attrs:{maxlength:4000,rows:5},placeholder:'Internal explanation and evidence references'});
  target.addEventListener('input',() => { workflow.target = target.value.trim(); });
  reasons.addEventListener('change',() => { workflow.reason = reasons.value; });
  explanation.addEventListener('input',() => { workflow.explanation = explanation.value; });
  body.appendChild(fieldLabel('Minecraft player',target));
  body.appendChild(fieldLabel('Configured reason',reasons));
  body.appendChild(fieldLabel('Internal explanation',explanation));
  const prepare = buttonNode(workflow.busy ? 'Preparing…' : 'Review punishment','button primary',{});
  prepare.disabled = workflow.busy;
  prepare.addEventListener('click',() => {
    if (!workflow.target || !workflow.reason) return showToast('Select a Minecraft player and configured reason.',true);
    performMinecraftAction('prepare');
  });
  footer.appendChild(prepare);
}

function renderPreparedMinecraftSummary(workflow, body) {
  const prepared = workflow.prepared;
  body.appendChild(summaryList([['Minecraft player',prepared.targetName],['Player UUID',prepared.targetId],['Reason',prepared.reason],
    ['Consequences',(prepared.consequences || []).map(value => value.type + ' · ' + value.duration).join(', ')],
    ['Internal explanation',prepared.explanation || 'None']]));
}

function completedMinecraftResult(workflow) {
  return workflow.result && workflow.result.state !== 'PREPARED';
}

function renderMinecraftResult(workflow, body, footer) {
  body.appendChild(element('h3',{text:workflow.result.state === 'APPLIED' ? 'Punishment committed' : 'Approval requested'}));
  body.appendChild(element('p',{text:workflow.result.state === 'APPLIED' ? 'Case: ' + workflow.result.caseId
    : 'Request: ' + workflow.result.requestId + '. No punishment is applied until an authorized reviewer approves it.'}));
  const done = buttonNode('Done','button primary',{});
  done.addEventListener('click',closeWorkflow);
  footer.appendChild(done);
}

function renderMinecraftConfirmation(workflow, body, footer) {
  const prepared = workflow.prepared;
  body.appendChild(element('p',{text:'Confirm before ' + new Date(prepared.expiresAt).toLocaleTimeString()
    + '. The server rechecks authority, target protection, and current policy when you confirm.'}));
  const status = buttonNode(workflow.busy ? 'Checking…' : 'Check status','button secondary',{});
  status.disabled = workflow.busy;
  status.addEventListener('click',() => performMinecraftAction('status'));
  footer.appendChild(status);
  const confirm = buttonNode(workflow.busy ? 'Working…' : 'Confirm Minecraft punishment','button primary',{});
  confirm.disabled = workflow.busy || workflow.uncertain;
  confirm.addEventListener('click',() => performMinecraftAction('confirm'));
  footer.appendChild(confirm);
}
