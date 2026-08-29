'use strict';
function renderReviewStep() {
  const w=state.workflow, r=w.recommendation;
  const stale = w.stale || w.recommendationEvidenceRevision !== state.evidenceRevision;
  const approval=approvalFor(w.actual.action,w.duration);
  const restrict=w.actual.action==='Restrict';
  const targets=[...w.restrictionTargets].map((id)=>RESTRICTION_TARGETS.find((target)=>target.id===id)).filter(Boolean);
  replaceMarkup($('#workflowBody'), `${stale?`<div class="alert warning"><strong>Evidence changed after the recommendation.</strong><span>Recalculate before simulation so the review matches the current incident.</span></div>`:''}
    <div class="review-grid">
      ${reviewItem('Target',`${identity.displayName} · ${identity.minecraft}`)}${reviewItem('Offense',w.offense.label)}${reviewItem('Relevant history',`${r.relevant.length} of ${r.total} total records`)}${reviewItem('Ladder recommendation',`${r.action} · ${r.duration}`)}
      ${reviewItem('Actual action',`${w.actual.action}${w.custom?' · Custom override':''}`)}${reviewItem('Following recommendation',w.custom?'No — Custom override':'Yes')}${w.duration!=='—'?reviewItem('Duration',w.duration):''}${reviewItem('Scope / platform',w.scope)}
      ${restrict?reviewItem('Restriction mode',w.restrictMode==='read-only'?'Read only':'No access'):''}${restrict?reviewItem('Restriction targets',targets.length?targets.map((target)=>target.label).join(', '):'None selected'):''}
      ${reviewItem('Evidence messages',String(state.evidence.size))}${reviewItem('Messages to delete',String(state.deleting.size))}${reviewItem('Preserve',`${preservedEvidenceCount()} evidence message${preservedEvidenceCount()===1?'':'s'}`)}${reviewItem('DM user',w.dm?'Yes':'No')}${reviewItem('Approval requirement',approval)}
    </div>
    <section class="card review-evidence"><div class="section-heading"><div><h3>Case & evidence summary</h3><p>Selected Discord messages remain distinct from deletion instructions.</p></div></div>${evidenceSummaryHtml()}${w.reason?`<div class="staff-reason"><span>Staff explanation</span><p>${esc(w.reason)}</p></div>`:''}</section>`);
  replaceMarkup($('#workflowFooter'), `<button class="button ghost" type="button" data-back>Back</button><div class="inline">${stale?'<button class="button secondary" type="button" data-recalculate>Recalculate</button>':''}<button class="button primary" type="button" data-confirm ${stale || (restrict&&targets.length===0)?'disabled':''}>Confirm simulation</button></div>`);
  $('[data-back]').addEventListener('click',()=>{w.step='options';renderWorkflow();});
  $('[data-recalculate]')?.addEventListener('click',()=>{w.recommendation=recommend(w.offense.key);w.recommendationEvidenceRevision=state.evidenceRevision;w.stale=false;renderWorkflow();});
  $('[data-confirm]').addEventListener('click',confirmSimulation);
}

function reviewItem(label,value) { return `<div class="review-item"><span>${esc(label)}</span><strong>${esc(value)}</strong></div>`; }
function evidenceSummaryHtml() {
  const ids=[...state.evidence];
  if (!ids.length) return '<div class="empty-inline">No message evidence selected.</div>';
  return ids.map((id)=>{
    const message=baseMessages.find((item)=>item.id===id);
    if(!message)return'';
    const disposition=state.deleting.has(id)?'<span class="message-pill delete">Delete</span>':'<span class="message-pill preserve">Preserve</span>';
    return `<div class="evidence-line"><div><strong>#${esc(message.channel)} · ${esc(formatExact(message.time))}</strong><span>${esc(message.text)}</span></div><div class="chip-row"><span class="message-pill evidence">Evidence</span>${state.violating.has(id)?'<span class="message-pill violating">Violating</span>':''}${disposition}</div></div>`;
  }).join('');
}
function preservedEvidenceCount() { return [...state.evidence].filter((id)=>!state.deleting.has(id)).length; }
function approvalFor(action,duration) { return duration==='Permanent' && ['Ban','Mute','Restrict'].includes(action)?'Admin+ approval required':'None'; }

async function confirmSimulation() {
  if (!state.session) { showToast('Session unavailable. Reopen from Discord.',true); return; }
  const button=$('[data-confirm]'); if(button) button.disabled=true;
  try {
    const payload={target:state.session.targetKey,offense:state.workflow.offense.key,action:state.workflow.actual.action,evidence:[...state.evidence],delete:[...state.deleting]};
    const response=await fetch('/api/simulate',{method:'POST',headers:{'Content-Type':'application/json','X-Preview-Csrf':state.session.csrfToken},body:JSON.stringify(payload)});
    if(!response.ok) throw new Error('Simulation rejected');
    const result=await response.json();
    state.workflow.step='complete'; renderWorkflow(); showToast(result.message || 'Simulation complete');
  } catch(error) { showToast('Simulation could not be completed. Reopen the panel from Discord if the session expired.',true); if(button)button.disabled=false; }
}

function renderCompleteStep() {
  $('#workflowSteps').replaceChildren();
  replaceMarkup($('#workflowBody'), '<div class="completion-state"><div class="completion-icon" aria-hidden="true">✓</div><h3>Simulation complete</h3><p>The review flow completed successfully.</p><span>No live moderation action was performed.</span></div>');
  replaceMarkup($('#workflowFooter'), '<button class="button primary" type="button" data-done>Done</button>');
  $('[data-done]').addEventListener('click',closeWorkflow);
}

function scenarioOffense() {
  if (['severe','admin','approval'].includes(state.scenario)) return 'hate';
  if (state.scenario==='custom') return 'harassment';
  return 'spam';
}
function offenseHint(key) {
  return {spam:'Flooding, repeated posts, disruptive repetition',harassment:'Targeted hostile or abusive conduct',hate:'Slurs or identity-based abuse',advertising:'Unwanted invites or promotions',cheating:'Unfair gameplay or prohibited client behavior',other:'Policy issue that needs a custom explanation'}[key];
}

function dateHeading(iso) {
  const date=iso.slice(0,10);
  if(date==='2026-08-29') return 'Today · Aug 29, 2026';
  if(date==='2026-08-28') return 'Yesterday · Aug 28, 2026';
  return new Intl.DateTimeFormat('en-US',{month:'short',day:'numeric',year:'numeric'}).format(new Date(`${date}T12:00:00`));
}
function formatExact(iso) { return new Intl.DateTimeFormat('en-US',{month:'short',day:'numeric',year:'numeric',hour:'numeric',minute:'2-digit',second:'2-digit'}).format(new Date(iso)); }
function formatDate(date) { return new Intl.DateTimeFormat('en-US',{month:'short',day:'numeric',year:'numeric'}).format(new Date(`${date}T12:00:00`)); }
function empty(title,detail='') { return `<div class="empty-state"><strong>${esc(title)}</strong>${detail?`<span>${esc(detail)}</span>`:''}</div>`; }
function showToast(message,isError=false) {
  const region=$('#toastRegion');
  replaceMarkup(region, `<div class="toast ${isError?'error':''}">${esc(message)}</div>`);
  clearTimeout(state.toastTimer); state.toastTimer=setTimeout(()=>{region.replaceChildren();},4200);
}

document.addEventListener('DOMContentLoaded', init);
