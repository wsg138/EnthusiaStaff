'use strict';
function openWorkflow() {
  state.workflow = {
    step:'offense', offense:null, recommendation:null, actual:null, custom:false, duration:null, scope:null,
    reason:'', dm:true, restrictMode:'read-only', restrictionTargets:new Set(), recommendationEvidenceRevision:null,
    stale: state.scenario === 'stale'
  };
  renderWorkflow();
  $('#punishmentDialog').showModal();
  $('[data-offense]')?.focus();
}
function closeWorkflow() { if ($('#punishmentDialog').open) $('#punishmentDialog').close(); state.workflow=null; }

function renderWorkflow() {
  if (!state.workflow) return;
  const w = state.workflow;
  const titles = {offense:'Choose offense',recommendation:'Ladder recommendation',options:'Punishment options',review:'Final review',complete:'Simulation complete'};
  $('#workflowTitle').textContent = titles[w.step];
  renderWorkflowSteps(w.step);
  if (w.step === 'offense') renderOffenseStep();
  else if (w.step === 'recommendation') renderRecommendationStep();
  else if (w.step === 'options') renderOptionsStep();
  else if (w.step === 'review') renderReviewStep();
  else renderCompleteStep();
}

function renderWorkflowSteps(step) {
  const order = ['offense','recommendation','options','review'];
  const labels = ['Offense','Recommendation','Options','Review'];
  const current = Math.max(0, order.indexOf(step));
  replaceMarkup($('#workflowSteps'), labels.map((label,index)=>`<div class="workflow-step ${index===current?'active':''} ${index<current?'done':''}"><span>${index+1}</span>${esc(label)}</div>`).join(''));
}

function renderOffenseStep() {
  const suggested = scenarioOffense();
  const options = OFFENSES.map(([key,label])=>`<button type="button" class="choice-card ${key===suggested?'suggested':''}" data-offense="${esc(key)}"><strong>${esc(label)}</strong><span>${esc(offenseHint(key))}</span>${key===suggested?'<small>Suggested for this sample</small>':''}</button>`).join('');
  replaceMarkup($('#workflowBody'), `<div class="step-intro"><h3>What happened?</h3><p>Choose the policy family first. History is evaluated only after the offense is known.</p></div><div class="option-grid">${options}</div>`);
  replaceMarkup($('#workflowFooter'), '<button class="button ghost" type="button" data-cancel>Cancel</button>');
  $$('[data-offense]').forEach((button)=>button.addEventListener('click',()=>chooseOffense(button.dataset.offense)));
  $('[data-cancel]').addEventListener('click',closeWorkflow);
}

function chooseOffense(key) {
  const label = OFFENSES.find(([value])=>value===key)?.[1] || 'Other';
  state.workflow.offense={key,label};
  state.workflow.recommendation=recommend(key);
  state.workflow.recommendationEvidenceRevision=state.evidenceRevision;
  state.workflow.step='recommendation';
  renderWorkflow();
}

function recommend(key) {
  const relevant = state.history.filter((row)=>row.ladderRelevant && row.key===key);
  const base = ['hate','cheating'].includes(key)?3:key==='advertising'?2:1;
  const step = Math.min(4, Math.max(base,relevant.length+1));
  const consequence = consequenceFor(key,step);
  return {...consequence, relevant, total:state.history.length, step, explanation:recommendationReason(key,relevant.length,step,base)};
}

function consequenceFor(key,step) {
  if (key==='hate') return {action:'Ban',scope:'Discord',duration:step>=4?'Permanent':'30 days'};
  if (key==='cheating') return {action:'Ban',scope:'Minecraft',duration:step>=4?'Permanent':'30 days'};
  if (key==='advertising') return step<=2?{action:'Kick',scope:'Discord',duration:'—'}:step===3?{action:'Ban',scope:'Discord',duration:'7 days'}:{action:'Ban',scope:'Discord',duration:'Permanent'};
  if (key==='harassment') return [null,{action:'Warning',scope:'Discord',duration:'—'},{action:'Mute',scope:'Discord',duration:'1 day'},{action:'Mute',scope:'Discord',duration:'7 days'},{action:'Ban',scope:'Discord',duration:'30 days'}][step];
  return [null,{action:'Warning',scope:'Discord',duration:'—'},{action:'Mute',scope:'Discord',duration:'2 hours'},{action:'Mute',scope:'Discord',duration:'3 days'},{action:'Ban',scope:'Discord',duration:'30 days'}][step];
}

function recommendationReason(key,relevant,step,base) {
  const offense = OFFENSES.find(([value])=>value===key)?.[1] || 'Other';
  if (base > 1 && relevant === 0) return `${offense} starts at ladder step ${base} because of severity.`;
  const incident = relevant===0?'first relevant incident':relevant===1?'second relevant incident':relevant===2?'third relevant incident':`${relevant+1}th relevant incident`;
  return `${offense} — ${incident}; ladder step ${step}.`;
}

function renderRecommendationStep() {
  const w=state.workflow, r=w.recommendation;
  const unrelated = r.total-r.relevant.length;
  const approval = approvalFor(r.action,r.duration);
  replaceMarkup($('#workflowBody'), `<div class="recommendation-layout"><section class="recommendation-card"><div class="eyebrow">Recommended</div><div class="recommendation-action">${esc(r.action)}</div><div class="recommendation-duration">${esc(r.duration)} · ${esc(r.scope)}</div><p>${esc(r.explanation)}</p>${approval!=='None'?`<div class="approval-note">${esc(approval)}</div>`:''}</section>
    <section class="card"><h3>Relevant history for ${esc(w.offense.label)}</h3><div class="summary-list"><div class="summary-row"><span>Total history</span><strong>${r.total}</strong></div><div class="summary-row"><span>Relevant history</span><strong>${r.relevant.length}</strong></div><div class="summary-row"><span>Unrelated records</span><strong>${unrelated}</strong></div><div class="summary-row"><span>Ladder step</span><strong>${r.step}</strong></div></div>${r.relevant.length?`<div class="relevant-history">${r.relevant.map(historyCompact).join('')}</div>`:'<p class="muted small">No prior matching incidents.</p>'}</section></div>`);
  replaceMarkup($('#workflowFooter'), '<button class="button ghost" type="button" data-back>Back</button><div class="inline"><button class="button secondary" type="button" data-custom>Custom Punishment</button><button class="button primary" type="button" data-use-recommendation>Use Recommendation</button></div>');
  $('[data-back]').addEventListener('click',()=>{w.step='offense';renderWorkflow();});
  $('[data-use-recommendation]').addEventListener('click',()=>useRecommendation(false));
  $('[data-custom]').addEventListener('click',()=>useRecommendation(true));
}

function useRecommendation(custom) {
  const w=state.workflow, r=w.recommendation;
  w.custom=custom;
  w.actual={action:r.action}; w.scope=r.scope; w.duration=r.duration;
  if (custom) seedCustomScenario(w);
  w.step='options'; renderWorkflow();
}

function seedCustomScenario(w) {
  if (state.scenario==='restrict-one' || state.scenario==='restrict-many') {
    w.actual={action:'Restrict'}; w.scope='Discord'; w.duration='3 days';
    if (state.scenario==='restrict-one') w.restrictionTargets.add('general');
    else { w.restrictionTargets.add('general'); w.restrictionTargets.add('market'); w.restrictionTargets.add('trading'); w.restrictMode='no-access'; }
  } else if (state.scenario==='custom') {
    w.actual={action:'Kick'}; w.scope='Discord'; w.duration='—';
  }
}

function renderOptionsStep() {
  const w=state.workflow;
  const action=w.actual.action;
  replaceMarkup($('#workflowBody'), `<div class="step-intro"><div><span class="eyebrow">${w.custom?'Custom override':'Following recommendation'}</span><h3>${w.custom?'Configure the actual action':'Confirm action options'}</h3></div></div>
    ${w.custom?customControlsHtml(w):summaryActualHtml(w)}
    ${action==='Restrict'?restrictionControlsHtml(w):''}
    <section class="card option-section"><div class="section-heading"><div><h3>Evidence & message actions</h3><p>Evidence and Discord deletion are intentionally separate.</p></div></div><div class="summary-list"><div class="summary-row"><span>Evidence messages</span><strong>${state.evidence.size}</strong></div><div class="summary-row"><span>Delete from Discord</span><strong>${state.deleting.size}</strong></div><div class="summary-row"><span>Preserve evidence</span><strong>${preservedEvidenceCount()}</strong></div></div><button class="button secondary" type="button" data-review-messages>Review selected messages</button></section>
    <section class="card option-section"><label class="checkbox-control prominent"><input id="dmUserOption" type="checkbox" ${w.dm?'checked':''}> DM user with the moderation result</label><label class="field-label">Staff explanation / case note<textarea id="reasonInput" rows="3" maxlength="300" placeholder="Concise context for the case">${esc(w.reason)}</textarea></label></section>`);
  replaceMarkup($('#workflowFooter'), '<button class="button ghost" type="button" data-back>Back</button><button class="button primary" type="button" data-review>Review action</button>');
  bindOptionsEvents();
}

function customControlsHtml(w) {
  const actions=['Warning','Mute','Kick','Ban','Restrict'];
  const restrict=w.actual.action==='Restrict';
  const needsDuration=!['Warning','Kick'].includes(w.actual.action);
  const actionOptions = actions.map((value)=>`<option ${w.actual.action===value?'selected':''}>${esc(value)}</option>`).join('');
  const durations = ['30 minutes','2 hours','1 day','3 days','7 days','14 days','30 days','Permanent'];
  const durationOptions = durations.map((value)=>`<option ${w.duration===value?'selected':''}>${esc(value)}</option>`).join('');
  return `<section class="card option-section"><div class="field-row wrap"><label class="field-label">Punishment<select id="customAction">${actionOptions}</select></label>${restrict?'<label class="field-label">Scope<select id="customScope" disabled><option selected>Discord</option></select></label>':`<label class="field-label">Scope<select id="customScope"><option ${w.scope==='Discord'?'selected':''}>Discord</option><option ${w.scope==='Minecraft'?'selected':''}>Minecraft</option><option ${w.scope==='Both'?'selected':''}>Both</option></select></label>`}${needsDuration?`<label class="field-label">Duration<select id="customDuration">${durationOptions}</select></label>`:''}</div><div class="override-note"><strong>Custom override</strong><span>This differs from the normal ladder path. Review the action, scope, duration, and approval requirement carefully.</span></div></section>`;
}

function summaryActualHtml(w) {
  return `<section class="recommendation-card compact"><div class="eyebrow">Actual action</div><div class="recommendation-action">${esc(w.actual.action)}</div><div class="recommendation-duration">${esc(w.duration)} · ${esc(w.scope)}</div></section>`;
}

function restrictionControlsHtml(w) {
  return `<section class="card option-section"><div class="section-heading"><div><h3>Discord restriction</h3><p>Limit permissions only in the exact channel or category targets selected below.</p></div></div>
    <fieldset class="segmented-field"><legend>Restriction mode</legend><label><input type="radio" name="restrictMode" value="read-only" ${w.restrictMode==='read-only'?'checked':''}><span><strong>Read only</strong><small>Can view, cannot send or respond</small></span></label><label><input type="radio" name="restrictMode" value="no-access" ${w.restrictMode==='no-access'?'checked':''}><span><strong>No access</strong><small>Cannot view or access selected locations</small></span></label></fieldset>
    <label class="field-label">Find channel or category<input id="targetSearch" type="search" placeholder="Search #channel or category"></label><div id="restrictionTargetList" class="target-picker">${restrictionTargetsHtml(w,'')}</div></section>`;
}

function restrictionTargetsHtml(w,term) {
  return RESTRICTION_TARGETS.filter((target)=>`${target.label} ${target.detail}`.toLowerCase().includes(term.toLowerCase())).map((target)=>`<label class="target-option"><input type="checkbox" data-restriction-target="${esc(target.id)}" ${w.restrictionTargets.has(target.id)?'checked':''}><span class="target-type">${target.type==='channel'?'#':'▣'}</span><span><strong>${esc(target.label)}</strong><small>${esc(target.type)} · ${esc(target.detail)}</small></span></label>`).join('') || empty('No matching channel or category');
}

function bindOptionsEvents() {
  const w=state.workflow;
  $('[data-back]').addEventListener('click',()=>{w.step='recommendation';renderWorkflow();});
  $('[data-review]').addEventListener('click',()=>{ captureOptions(); w.step='review'; renderWorkflow(); });
  $('[data-review-messages]').addEventListener('click',()=>{ captureOptions(); closeWorkflow(); switchView('messages'); showToast('Message selections are preserved. Reopen Issue Punishment when ready.'); });
  $('#dmUserOption').addEventListener('change',(event)=>{w.dm=event.target.checked;});
  $('#reasonInput').addEventListener('input',(event)=>{w.reason=event.target.value;});
  $('#customAction')?.addEventListener('change',(event)=>setCustomAction(event.target.value));
  $('#customScope')?.addEventListener('change',(event)=>{w.scope=event.target.value;});
  $('#customDuration')?.addEventListener('change',(event)=>{w.duration=event.target.value;});
  $$('[name="restrictMode"]').forEach((input)=>input.addEventListener('change',(event)=>{w.restrictMode=event.target.value;}));
  $('#targetSearch')?.addEventListener('input',(event)=>{ replaceMarkup($('#restrictionTargetList'),restrictionTargetsHtml(w,event.target.value)); bindRestrictionTargets(); });
  bindRestrictionTargets();
}

function setCustomAction(action) {
  const w=state.workflow;
  w.actual={action};
  if (['Warning','Kick'].includes(action)) w.duration='—';
  else if (!w.duration || w.duration==='—') w.duration='3 days';
  if (action==='Restrict') w.scope='Discord';
  renderWorkflow();
}

function bindRestrictionTargets() { $$('[data-restriction-target]').forEach((input)=>input.addEventListener('change',(event)=>toggleSet(state.workflow.restrictionTargets,event.target.dataset.restrictionTarget,event.target.checked))); }
function captureOptions() {
  const w=state.workflow;
  if ($('#dmUserOption')) w.dm=$('#dmUserOption').checked;
  if ($('#reasonInput')) w.reason=$('#reasonInput').value.trim();
  if ($('#customAction')) w.actual={action:$('#customAction').value};
  if ($('#customScope')) w.scope=$('#customScope').value;
  if ($('#customDuration')) w.duration=$('#customDuration').value;
}
