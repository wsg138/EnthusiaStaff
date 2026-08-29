'use strict';

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const esc = (value) => String(value ?? '').replace(/[&<>'"]/g, (char) => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));

const OFFENSES = [
  ['spam', 'Spam / flooding'], ['harassment', 'Harassment'], ['hate', 'Hate / slurs'],
  ['advertising', 'Advertising / unwanted invites'], ['cheating', 'Cheating'], ['other', 'Other / custom']
];
const RESTRICTION_TARGETS = [
  {id:'general', type:'channel', label:'#general', detail:'Community chat'},
  {id:'market', type:'channel', label:'#market', detail:'Player trading'},
  {id:'support', type:'channel', label:'#support', detail:'Support requests'},
  {id:'trading', type:'category', label:'Trading', detail:'Category · 4 channels'},
  {id:'community', type:'category', label:'Community', detail:'Category · 7 channels'}
];
const SCENARIOS = [
  ['repeat', 'Repeat spam offense'], ['minor', 'Minor first offense'], ['severe', 'Severe harassment / hate'],
  ['admin', 'Admin-level permanent'], ['custom', 'Custom override'], ['unrelated', 'Unrelated history'],
  ['multi', 'Multi-message evidence'], ['restrict-one', 'Restriction · one channel'],
  ['restrict-many', 'Restriction · channels + category'], ['edited', 'Edited message'],
  ['attachment', 'Message with attachment'], ['stale', 'Stale evidence'], ['approval', 'Approval required']
];
const NAV = [
  ['overview','Overview'], ['messages','Messages'], ['history','History'], ['cases','Cases'], ['notes','Notes'], ['accounts','Accounts']
];

const identity = {
  displayName:'RiverAsh', username:'riverash', discordId:'1049827163345127424',
  minecraft:'RiverAshMC', minecraftUuid:'3a6d1a43-c4e4-4bc4-9d42-a4f7b7a2d9e1',
  alts:[{name:'AshRiverAlt', platform:'Bedrock', status:'Linked'}], status:'Discord mute', statusDetail:'1h 18m remaining'
};

const baseMessages = [
  {id:'19001', author:'Mossy', username:'mossy', initials:'MO', target:false, channel:'general', time:'2026-08-29T17:41:03-04:00', text:'Are we still doing the build event tonight?'},
  {id:'19002', author:'RiverAsh', username:'riverash', initials:'RA', target:true, channel:'general', time:'2026-08-29T17:41:26-04:00', text:'join my shop join my shop join my shop'},
  {id:'19003', author:'RiverAsh', username:'riverash', initials:'RA', target:true, channel:'general', time:'2026-08-29T17:41:39-04:00', text:'seriously everyone check #market right now'},
  {id:'19004', author:'Juniper', username:'juniper', initials:'JU', target:false, channel:'general', time:'2026-08-29T17:41:51-04:00', text:'You already posted that a few times.'},
  {id:'19005', author:'RiverAsh', username:'riverash', initials:'RA', target:true, channel:'general', time:'2026-08-29T17:42:04-04:00', text:'last chance, best prices on the server', edited:true},
  {id:'19006', author:'RiverAsh', username:'riverash', initials:'RA', target:true, channel:'market', time:'2026-08-29T17:44:11-04:00', text:'Selling kits. DM me for bulk prices.', attachment:{name:'price-list.png', detail:'PNG · 184 KB'}},
  {id:'19007', author:'Cedar', username:'cedar', initials:'CE', target:false, channel:'market', time:'2026-08-29T17:44:39-04:00', text:'This should probably stay in one post.', replyTo:'19006'},
  {id:'18890', author:'RiverAsh', username:'riverash', initials:'RA', target:true, channel:'general', time:'2026-08-28T20:06:18-04:00', text:'anyone need iron? I have a lot left over'},
  {id:'18720', author:'RiverAsh', username:'riverash', initials:'RA', target:true, channel:'market', time:'2026-08-24T14:16:01-04:00', text:'Old listing removed by the author.', deleted:true},
  {id:'18721', author:'Spruce', username:'spruce', initials:'SP', target:false, channel:'market', time:'2026-08-24T14:17:44-04:00', text:'I think they already sold it.'}
];

const historyTemplates = {
  repeat: [
    ['2026-08-20','spam','Spam / flooding','Warning','—','Morgan','Closed',true],
    ['2026-07-20','spam','Spam / flooding','Mute','2 hours','Sam','Expired',true],
    ['2026-08-05','harassment','Harassment','Mute','2 hours','Avery','Expired',true],
    ['2026-06-29','cheating','Cheating','Warning','—','Morgan','Closed',true]
  ],
  minor: [['2026-06-17','harassment','Harassment','Warning','—','Avery','Closed',true]],
  severe: [['2026-08-17','spam','Spam / flooding','Warning','—','Sam','Closed',true],['2026-07-15','advertising','Advertising / unwanted invites','Kick','—','Morgan','Closed',true]],
  admin: [['2026-08-21','hate','Hate / slurs','Ban','30 days','AdminFox','Expired',true],['2026-07-10','hate','Hate / slurs','Mute','14 days','Avery','Expired',true],['2026-04-29','hate','Hate / slurs','Mute','7 days','Morgan','Expired',true],['2026-07-29','spam','Spam / flooding','Warning','—','Sam','Closed',true]],
  custom: [['2026-08-14','harassment','Harassment','Mute','1 day','Morgan','Expired',true],['2026-07-08','harassment','Harassment','Warning','—','Avery','Closed',true],['2026-08-09','spam','Spam / flooding','Warning','—','Sam','Closed',true]],
  unrelated: [['2026-08-22','spam','Spam / flooding','Warning','—','Sam','Closed',true],['2026-08-16','harassment','Harassment','Mute','1 day','Morgan','Expired',true],['2026-08-01','cheating','Cheating','Ban','7 days','AdminFox','Expired',true],['2026-07-21','advertising','Advertising / unwanted invites','Kick','—','Avery','Closed',true],['2026-06-19','hate','Hate / slurs','Mute','7 days','AdminFox','Expired',true],['2026-05-26','harassment','Harassment','Warning','—','Morgan','Closed',false]]
};

const state = {
  session:null, scenario:'repeat', view:'messages', search:'', channel:'all', date:'all', selectedOnly:false,
  selected:new Set(), evidence:new Set(), violating:new Set(), deleting:new Set(), anchor:null, contextId:null,
  history:[], workflow:null, evidenceRevision:0, toastTimer:null
};

function init() {
  fillScenarioSelect();
  renderNav();
  bindShellEvents();
  applyScenario('repeat');
  loadSession();
}

async function loadSession() {
  try {
    const response = await fetch('/api/session', {headers:{Accept:'application/json'}});
    if (!response.ok) throw new Error('Session unavailable');
    state.session = await response.json();
    $('#actorMeta').textContent = 'Verified staff session';
  } catch (error) {
    $('#actorMeta').textContent = 'Session unavailable';
    showToast('Open this panel from the Discord launcher.', true);
  }
}

function fillScenarioSelect() {
  $('#scenarioSelect').innerHTML = SCENARIOS.map(([value,label]) => `<option value="${value}">${esc(label)}</option>`).join('');
  $('#scenarioSelect').addEventListener('change', (event) => applyScenario(event.target.value));
}

function renderNav() {
  $('#primaryNav').innerHTML = NAV.map(([key,label]) => `<button class="nav-item" data-view="${key}" type="button"><span>${label}</span><span class="nav-count" data-count="${key}"></span></button>`).join('');
  $$('.nav-item').forEach((button) => button.addEventListener('click', () => switchView(button.dataset.view)));
}

function bindShellEvents() {
  $('#closeWorkflow').addEventListener('click', closeWorkflow);
  $('#punishmentDialog').addEventListener('cancel', (event) => { event.preventDefault(); closeWorkflow(); });
}

function switchView(view) {
  state.view = view;
  renderAll();
}

function applyScenario(name) {
  state.scenario = name;
  $('#scenarioSelect').value = name;
  state.selected.clear(); state.evidence.clear(); state.violating.clear(); state.deleting.clear(); state.anchor = null; state.contextId = null;
  state.history = scenarioHistory(name);
  state.evidenceRevision++;
  state.workflow = null;
  if ($('#punishmentDialog').open) $('#punishmentDialog').close();
  configureScenario(name);
  renderAll();
}

function scenarioHistory(name) {
  const source = historyTemplates[name] || (['approval'].includes(name) ? historyTemplates.admin : historyTemplates.repeat);
  return source.map(([date,key,offense,action,duration,staff,status,ladderRelevant], index) => ({
    id:`CASE-${1301-index}`, date,key,offense,action,duration,staff,status,ladderRelevant
  }));
}

function configureScenario(name) {
  if (name === 'multi') {
    ['19002','19003','19005'].forEach((id) => { state.selected.add(id); state.evidence.add(id); });
    state.deleting.add('19002'); state.deleting.add('19005');
  } else if (name === 'edited') {
    state.search = 'last chance';
  } else if (name === 'attachment') {
    state.channel = 'market'; state.search = 'Selling kits';
  } else {
    state.search = ''; state.channel = 'all'; state.date = 'all'; state.selectedOnly = false;
  }
}
