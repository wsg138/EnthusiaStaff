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
  minecraftWorkflow = {scope:'MINECRAFT', target:'', family:'', reason:'', explanation:'', prepared:null, result:null, busy:false, uncertain:false};
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
  $('#workflowBody').replaceChildren(element('p',{text:'Choose exactly where this punishment applies. Both uses one atomic case and separate platform consequences.'}));
  const discord = buttonNode('Discord','button secondary',{});
  discord.addEventListener('click',() => {
    minecraftWorkflow = null;
    $('#punishmentDialog').close();
    originalLiveOpenWorkflow();
  });
  const minecraft = buttonNode('Minecraft','button secondary',{});
  minecraft.addEventListener('click',() => {
    minecraftWorkflow.scope = 'MINECRAFT';
    renderMinecraftPunishment();
  });
  const buttons = [discord,minecraft];
  if (liveActionCapabilities?.bothEnabled) {
    const both = buttonNode('Both','button primary',{});
    both.addEventListener('click',() => {
      minecraftWorkflow.scope = 'BOTH';
      renderMinecraftPunishment();
    });
    buttons.push(both);
  }
  $('#workflowFooter').replaceChildren(...buttons);
  $('#punishmentDialog').showModal();
}

$('#punishmentDialog').addEventListener('cancel', event => {
  if (minecraftWorkflow?.busy) event.preventDefault();
});
$('#punishmentDialog').addEventListener('close', () => {
  minecraftWorkflow = null;
  $('#closeWorkflow').disabled = false;
});

function minecraftActionPayload(workflow, operation) {
  if (workflow.scope === 'BOTH') {
    const payload = {targetKey:liveModeration.bootstrap?.targetKey, scope:'BOTH'};
    if (operation === 'prepare') {
      payload.minecraftTarget = workflow.target;
      payload.minecraftIntent = {reasonId:workflow.reason, explanation:workflow.explanation};
      payload.intent = bothDiscordIntent(workflow);
    } else {
      payload.confirmationId = workflow.prepared.confirmationId;
    }
    return payload;
  }
  const payload = {
    targetKey:liveModeration.bootstrap?.targetKey,
    scope:'MINECRAFT',
    minecraftTarget:workflow.prepared?.targetId || workflow.target
  };
  if (operation === 'prepare') payload.minecraftIntent = {reasonId:workflow.reason, explanation:workflow.explanation};
  else payload.confirmationId = workflow.prepared.confirmationId;
  return payload;
}

function bothDiscordIntent(workflow) {
  const reasons = Array.isArray(liveActionCapabilities?.configuredReasons)
    ? liveActionCapabilities.configuredReasons : [];
  const reason = reasons.find(value => value.id === workflow.reason);
  if (!reason) throw new Error('Configured reason is unavailable.');
  const first = Array.isArray(reason.ladder) ? reason.ladder[0] : null;
  const consequence = (first?.consequences || []).find(value =>
    ['WARNING','KICK','MUTE','PUBLIC_MUTE','BAN','NETWORK_BAN','NETWORK_IDENTITY_BAN'].includes(value.type));
  if (!consequence) throw new Error('This configured reason has no Discord-compatible consequence.');
  const type = bothDiscordType(consequence.type);
  return {
    type,
    duration:bothDiscordDuration(type, consequence.duration),
    reason:reason.label,
    explanation:workflow.explanation || '',
    restriction:null
  };
}

function bothDiscordType(type) {
  if (type === 'WARNING') return 'WARNING';
  if (type === 'KICK') return 'KICK';
  if (type === 'MUTE' || type === 'PUBLIC_MUTE') return 'MUTE';
  return 'BAN';
}

function bothDiscordDuration(type, value) {
  if (type === 'WARNING' || type === 'KICK') return 'instant';
  const normalized = String(value || '').trim().toLowerCase();
  if (normalized === 'permanent') return 'permanent';
  const match = /^([1-9][0-9]*)\s+(minutes?|hours?|days?|months?)$/.exec(normalized);
  if (!match) throw new Error('Configured Discord consequence has an unsupported duration.');
  const amount = Number(match[1]);
  if (match[2].startsWith('minute')) return amount + 'm';
  if (match[2].startsWith('hour')) return amount + 'h';
  if (match[2].startsWith('month')) return amount * 30 + 'd';
  return amount + 'd';
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
    workflow.prepared = workflow.scope === 'BOTH'
      ? {...result, targetId:result.minecraftTargetId, targetName:workflow.target, consequences:result.minecraftConsequences}
      : result;
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
  body.appendChild(element('p',{className:'muted',text:workflow.scope === 'BOTH'
    ? 'Both uses one case: Minecraft policy is prepared by Paper and the Discord consequence is persisted atomically with it.'
    : 'Uses the network’s configured reasons, escalation rules, and current staff authority. Discord enforcement stays separate.'}));
}

function renderMinecraftPrepare(workflow, body, footer) {
  const target = element('input',{id:'minecraftTarget',value:workflow.target,placeholder:'Minecraft username or UUID',attrs:{maxlength:36,autocomplete:'off'}});
  target.addEventListener('input',() => { workflow.target = target.value.trim(); });
  body.appendChild(fieldLabel('Minecraft player',target));
  const configured = liveActionCapabilities?.configuredReasons;
  const reasons = Array.isArray(configured)
    ? configured
    : Array.isArray(liveActionCapabilities.minecraftReasons) ? liveActionCapabilities.minecraftReasons : [];
  if (!workflow.family) {
    renderMinecraftReasonFamilies(workflow,body,reasons);
    return;
  }
  renderMinecraftReasonChoices(workflow,body,reasons);
  if (!workflow.reason) return;
  const selected = reasons.find(reason => reason.id === workflow.reason);
  if (selected && typeof catalogLadderCard === 'function') {
    body.appendChild(catalogLadderCard(selected,{relevantCount:typeof realRelevantHistoryCount === 'function'
      ? realRelevantHistoryCount(selected.family) : 0}));
  }
  if (selected?.minecraftSupported === false) {
    body.appendChild(element('div',{className:'alert warning'},
      element('strong',{text:'In-game workflow required'}),
      element('span',{text:'This exact reason includes a configured consequence the website is not allowed to execute. The full ladder is shown for reference; issue it through the in-game punishment workflow.'})));
    return;
  }
  const explanation = element('textarea',{id:'minecraftExplanation',value:workflow.explanation,attrs:{maxlength:4000,rows:5},placeholder:'Internal explanation and evidence references'});
  explanation.addEventListener('input',() => { workflow.explanation = explanation.value; });
  body.appendChild(fieldLabel('Internal explanation',explanation));
  const prepare = buttonNode(workflow.busy ? 'Preparing…' : 'Review punishment','button primary',{});
  prepare.disabled = workflow.busy;
  prepare.addEventListener('click',() => {
    if (!workflow.target || !workflow.reason) return showToast('Select a Minecraft player and configured reason.',true);
    performMinecraftAction('prepare');
  });
  footer.appendChild(prepare);
}

function renderMinecraftReasonFamilies(workflow, body, reasons) {
  const families = new Map();
  for (const reason of reasons) {
    const current = families.get(reason.family) || [];
    current.push(reason);
    families.set(reason.family,current);
  }
  body.appendChild(stepIntro('Choose punishment category','Select a general category, then the exact configured reason.'));
  body.appendChild(catalogRulesLink());
  const grid = element('div',{className:'option-grid catalog-category-grid'});
  for (const [family,items] of [...families.entries()].sort(([left],[right]) => left.localeCompare(right))) {
    const button = buttonNode('', 'choice-card', {});
    button.append(element('strong',{text:catalogFamilyLabel(family)}),
      element('span',{text:`${items.length} configured reason${items.length === 1 ? '' : 's'}`}));
    button.addEventListener('click',() => {
      workflow.family = family;
      workflow.reason = '';
      renderMinecraftPunishment();
    });
    grid.appendChild(button);
  }
  body.appendChild(grid);
}

function renderMinecraftReasonChoices(workflow, body, reasons) {
  const familyReasons = reasons.filter(reason => reason.family === workflow.family);
  const heading = element('div',{className:'section-heading'},
    element('div',{},element('h3',{text:catalogFamilyLabel(workflow.family)}),
      element('p',{text:'Choose the exact configured reason.'})));
  const back = buttonNode('Back to categories','text-button',{});
  back.addEventListener('click',() => {
    workflow.family = '';
    workflow.reason = '';
    renderMinecraftPunishment();
  });
  heading.appendChild(back);
  body.appendChild(heading);
  const grid = element('div',{className:'option-grid catalog-reason-grid'});
  for (const reason of familyReasons) {
    const button = catalogReasonChoice(reason);
    if (workflow.reason === reason.id) button.classList.add('suggested');
    button.removeAttribute('data-policy-reason');
    button.addEventListener('click',() => {
      workflow.reason = reason.id;
      renderMinecraftPunishment();
    });
    grid.appendChild(button);
  }
  body.appendChild(grid);
}

function renderPreparedMinecraftSummary(workflow, body) {
  const prepared = workflow.prepared;
  const minecraftConsequences = (prepared.consequences || []).map(value => formatMinecraftConsequence(value)).join(', ');
  const rows = [
    ['Scope',workflow.scope === 'BOTH' ? 'Discord + Minecraft' : 'Minecraft'],
    ['Minecraft player',prepared.targetName],
    ['Player UUID',prepared.targetId],
    ['Reason',prepared.reason],
    ['Minecraft consequences',minecraftConsequences || 'None'],
    ['Internal explanation',prepared.explanation || workflow.explanation || 'None']
  ];
  if (workflow.scope === 'BOTH') {
    rows.push(['Discord consequence',formatDiscordIntent(prepared.discordIntent)]);
  }
  body.appendChild(summaryList(rows));
}

function formatMinecraftConsequence(value) {
  if (value.duration) return value.type + ' · ' + value.duration;
  if (value.durationSeconds != null) return value.type + ' · ' + value.durationSeconds + ' seconds';
  return value.type + ' · ' + String(value.lengthKind || 'configured').toLowerCase();
}

function formatDiscordIntent(intent) {
  if (!intent) return 'Unavailable';
  const length = intent.length || {};
  let duration = String(length.kind || 'configured').toLowerCase();
  if (length.temporary != null) duration = String(length.temporary);
  return intent.type + ' · ' + duration;
}

function completedMinecraftResult(workflow) {
  return workflow.result && workflow.result.state !== 'PREPARED';
}

function renderMinecraftResult(workflow, body, footer) {
  if (workflow.scope === 'BOTH') {
    body.appendChild(element('h3',{text:workflow.result.state === 'APPLIED'
      ? 'Both-platform punishment applied'
      : workflow.result.state === 'PARTIAL_FAILURE' ? 'Partial failure — recovery required' : 'Both-platform punishment pending'}));
    body.appendChild(summaryList([
      ['Case',workflow.result.caseId || 'Unknown'],
      ['Minecraft delivery',workflow.result.minecraftState || 'Unknown'],
      ['Minecraft attempts',String(workflow.result.minecraftAttempts ?? 0)],
      ['Discord state',workflow.result.discordState || 'Unknown'],
      ['Discord external effect',workflow.result.discordExternalApplied ? 'Confirmed' : 'Not yet confirmed'],
      ['Discord notification',workflow.result.discordDmOutcome || 'Unknown']
    ]));
    if (workflow.result.minecraftError) {
      body.appendChild(element('div',{className:'alert warning',text:'Minecraft delivery error: ' + workflow.result.minecraftError}));
    }
  } else {
    body.appendChild(element('h3',{text:workflow.result.state === 'APPLIED' ? 'Punishment committed' : 'Approval requested'}));
    body.appendChild(element('p',{text:workflow.result.state === 'APPLIED' ? 'Case: ' + workflow.result.caseId
      : 'Request: ' + workflow.result.requestId + '. No punishment is applied until an authorized reviewer approves it.'}));
  }
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
  const confirm = buttonNode(
    workflow.busy ? 'Working…' : workflow.scope === 'BOTH' ? 'Confirm Both-platform punishment' : 'Confirm Minecraft punishment',
    'button primary',{});
  confirm.disabled = workflow.busy || workflow.uncertain;
  confirm.addEventListener('click',() => performMinecraftAction('confirm'));
  footer.appendChild(confirm);
}
