'use strict';

const browseBaseApplyLiveBootstrap = window.applyLiveBootstrap;
const browseBaseRenderTargetHeader = window.renderTargetHeader;
const browseBaseRenderContextPanel = window.renderContextPanel;
const browseBaseRenderCounts = window.renderCounts;
const browseBaseBindMessageEvents = window.bindMessageEvents;
const browseBaseSimulationRequest = window.simulationRequest;
const browseBaseSessionBoundChannelId = window.sessionBoundChannelId;
const TARGET_ONLY_VIEWS = new Set(['overview', 'history', 'cases', 'notes', 'accounts']);

liveModeration.targetSelected = false;
state.activeTargetKey = '';

function browseSessionBoundChannelId() {
  const key = state.session?.targetKey;
  const parts = typeof key === 'string' ? key.split(':') : [];
  if (parts[0] === 'channel' && parts.length === 2) return parts[1];
  return browseBaseSessionBoundChannelId();
}

function browseApplyLiveBootstrap(payload) {
  browseBaseApplyLiveBootstrap(payload);
  applyBrowseTargetState(payload);
  renderAll();
}

function applyBrowseTargetState(payload) {
  const selected = Boolean(payload?.targetSelected && payload?.identity);
  liveModeration.targetSelected = selected;
  state.activeTargetKey = optionalText(payload?.targetKey) || state.session?.targetKey || '';
  if (selected) return;
  setNoPlayerIdentity();
  if (TARGET_ONLY_VIEWS.has(state.view)) state.view = 'messages';
  resetCaseSelections();
}

function setNoPlayerIdentity() {
  Object.assign(identity, {
    displayName:'No player selected', username:'', discordId:'', minecraft:'', minecraftUuid:'', alts:[],
    status:'Channel browse', statusDetail:'Select a player for moderation history and actions',
    avatarUrl:'', linkState:'No player selected', globalName:'', serverName:''
  });
}

function browseRenderTargetHeader() {
  if (liveModeration.targetSelected) {
    browseBaseRenderTargetHeader();
    $('#targetHeader')?.append(browsePickerNode());
  } else {
    renderChannelBrowseHeader();
  }
  bindBrowsePickers();
}

function renderChannelBrowseHeader() {
  const channel = selectedChannel();
  const identityBlock = element('div',{className:'target-identity'},
    element('div',{className:'identity-line'},element('h1',{id:'targetName',text:'Channel moderation'})),
    element('div',{className:'target-subline',text:channel ? `Browsing #${channel.name} · no player selected` : 'No player selected'}));
  replaceChildrenOf($('#targetHeader'),
    element('div',{className:'target-avatar',text:'#',attrs:{'aria-hidden':'true'}}),
    identityBlock,
    browsePickerNode());
}

function browsePickerNode() {
  return element('div',{className:'target-actions browse-pickers'},
    browsePickerField('Channel', channelPickerNode()),
    browsePickerField('Player', playerPickerNode()));
}

function browsePickerField(label, control) {
  return element('label',{className:'filter-field browse-picker'},element('span',{text:label}),control);
}

function channelPickerNode() {
  return element('select',{id:'workspaceChannelPicker'},
    liveModeration.channels.map((channel) => optionNode(channel.id, `#${channel.name}`, state.channel === channel.id)));
}

function playerPickerNode() {
  const selectedId = liveModeration.targetSelected ? identity.discordId : '';
  const authors = loadedAuthors(selectedId);
  return element('select',{id:'workspacePlayerPicker'},
    optionNode('','No player selected',!selectedId),
    authors.map((author) => optionNode(author.id, author.label, author.id === selectedId)));
}

function loadedAuthors(selectedId) {
  const byId = new Map();
  for (const message of baseMessages) {
    if (message.authorId) byId.set(message.authorId,{id:message.authorId,label:`${message.author} · @${message.username}`});
  }
  if (selectedId && !byId.has(selectedId)) {
    byId.set(selectedId,{id:selectedId,label:`${identity.displayName} · @${identity.username}`});
  }
  return [...byId.values()].sort((left,right) => left.label.localeCompare(right.label));
}

function bindBrowsePickers() {
  $('#workspaceChannelPicker')?.addEventListener('change', changeBrowseChannel);
  $('#workspacePlayerPicker')?.addEventListener('change', changeBrowsePlayer);
}

async function changeBrowseChannel(event) {
  state.channel = event.target.value;
  state.contextId = null;
  state.contextReturn = null;
  clearLocalMessageFilters();
  state.channel = event.target.value;
  await loadChannelPage();
}

async function changeBrowsePlayer(event) {
  const userId = event.target.value;
  if (!userId) await clearBrowsePlayer();
  else await selectBrowsePlayer(userId);
}

async function selectBrowsePlayer(userId) {
  if (!/^[1-9][0-9]{0,19}$/.test(userId)) {
    showToast('That Discord user cannot be selected.',true);
    return;
  }
  const preserved = preserveMessageWorkspace();
  try {
    const payload = await fetchBrowseBootstrap({target:userId,channel:currentBrowseChannel()});
    if (!payload.targetSelected || !payload.identity) throw new Error('Player details were not returned.');
    applySelectedPlayerPayload(payload,preserved);
  } catch (error) {
    showToast(error.message || 'Player details are temporarily unavailable.',true);
  }
}

async function clearBrowsePlayer() {
  try {
    const payload = await fetchBrowseBootstrap({browse:true,channel:currentBrowseChannel()});
    resetCaseSelections();
    browseApplyLiveBootstrap(payload);
    state.view = 'messages';
    renderAll();
  } catch (error) {
    showToast(error.message || 'Channel moderation view is temporarily unavailable.',true);
  }
}

async function fetchBrowseBootstrap(body) {
  const response = await requestDirectModerationRead('/api/bootstrap',{
    method:'POST',headers:{Accept:'application/json','Content-Type':'application/json'},body:JSON.stringify(body)
  });
  const payload = await readJsonResponse(response);
  if (!response.ok) throw new Error(payload.message || 'Moderation data unavailable');
  return payload;
}

function preserveMessageWorkspace() {
  return {
    messages:baseMessages.slice(), channel:state.channel,
    olderCursor:liveModeration.olderCursor, newerCursor:liveModeration.newerCursor,
    warning:liveModeration.warning
  };
}

function applySelectedPlayerPayload(payload,preserved) {
  resetCaseSelections();
  liveModeration.bootstrap = payload;
  applyLiveCollections(payload);
  applyIdentity(payload.identity);
  applyRestrictionTargets();
  state.history = asArray(payload.history).map(mapHistoryRow);
  liveModeration.targetSelected = true;
  state.activeTargetKey = payload.targetKey;
  baseMessages.splice(0,baseMessages.length,...preserved.messages);
  state.channel = preserved.channel;
  liveModeration.olderCursor = preserved.olderCursor;
  liveModeration.newerCursor = preserved.newerCursor;
  liveModeration.warning = preserved.warning;
  renderAll();
}

function resetCaseSelections() {
  state.selected.clear();
  state.evidence.clear();
  state.violating.clear();
  state.deleting.clear();
  state.anchor = null;
  state.contextId = null;
  state.contextReturn = null;
  state.workflow = null;
  state.evidenceRevision++;
  if ($('#punishmentDialog')?.open) $('#punishmentDialog').close();
}

function currentBrowseChannel() {
  if (state.channel && state.channel !== 'all') return state.channel;
  return browseSessionBoundChannelId() || liveModeration.channels[0]?.id || '';
}

function selectedChannel() {
  return liveModeration.channels.find((channel) => channel.id === currentBrowseChannel());
}

function browseRenderContextPanel() {
  if (liveModeration.targetSelected) {
    browseBaseRenderContextPanel();
    return;
  }
  const channel = selectedChannel();
  replaceChildrenOf($('#contextPanel'),
    contextSection('Channel',null,channel ? `#${channel.name}` : 'Unavailable','Switch channels from the player header.'),
    contextSection('Messages loaded',null,String(baseMessages.length),'Select any message to build evidence.'),
    contextSection('Player',null,'None selected','Choose an author from the Player picker or a message menu to view player-specific records.'));
}

function browseRenderCounts() {
  browseBaseRenderCounts();
  updateTargetNavigation();
  if (liveModeration.targetSelected) return;
  for (const key of TARGET_ONLY_VIEWS) {
    const target = $(`[data-count="${key}"]`);
    if (target) target.textContent = '—';
  }
}

function updateTargetNavigation() {
  $$('.nav-item').forEach((button) => {
    const targetOnly = TARGET_ONLY_VIEWS.has(button.dataset.view);
    button.disabled = targetOnly && !liveModeration.targetSelected;
    if (button.disabled) button.title = 'Select a player first';
    else button.removeAttribute('title');
  });
}

function browseMessageNode(message) {
  const selected = state.selected.has(message.id);
  const focused = state.contextId === message.id;
  const classes = ['message-row',selected ? 'selected':'',message.deleted ? 'deleted':'',focused ? 'context-focus':'']
    .filter(Boolean).join(' ');
  const checkbox = element('input',{type:'checkbox',className:'message-select',checked:selected,
    attrs:{'aria-label':`Select message from ${message.author} at ${formatExact(message.time)}`}});
  return element('article',{className:classes,dataset:{messageId:message.id},attrs:{
    tabindex:'0','aria-label':`${selected ? 'Selected' : 'Not selected'} message from ${message.author} at ${formatExact(message.time)}. Press Space to toggle selection.`
  }},element('div',{},checkbox),messageAvatarNode(message),polishedMessageBodyNode(message),browseMessageActionsNode(message));
}

function browseMessageActionsNode(message) {
  const menu = hardenedMessageActionsNode(message);
  if (!message.authorId) return menu;
  const items = menu.querySelector('.message-action-menu');
  const viewPlayer = element('button',{type:'button',className:'message-action-item',text:'View player',
    dataset:{selectPlayer:message.authorId},attrs:{role:'menuitem','aria-label':`View moderation details for ${message.author}`}});
  if (items) items.insertBefore(viewPlayer, items.firstChild);
  return menu;
}

function browseBindMessageEvents() {
  browseBaseBindMessageEvents();
  $$('[data-select-player]').forEach((button) => button.addEventListener('click',async (event) => {
    closeContainingMenu(event.currentTarget);
    await selectBrowsePlayer(event.currentTarget.dataset.selectPlayer);
  }));
}

async function fetchBrowseContextAround(channelId,messageId) {
  const response = await requestDirectModerationRead('/api/messages',{
    method:'POST',
    headers:{Accept:'application/json','Content-Type':'application/json'},
    body:JSON.stringify({channel:channelId,around:messageId,limit:LIVE_MESSAGE_PAGE_LIMIT})
  });
  const page = await readJsonResponse(response);
  if (!response.ok) throw new Error(page.message || 'Discord context unavailable');
  return asArray(page.messages).map(window.mapMessage);
}

async function browseShowMessageContext(id) {
  const trigger = baseMessages.find((message) => message.id === id);
  if (!trigger) {
    showToast('That message is no longer in the loaded view.',true);
    return;
  }
  const previous = rememberMessageView();
  showToast('Loading surrounding conversation…');
  try {
    const around = await fetchBrowseContextAround(trigger.channelId,id);
    const context = surroundingConversation(trigger,around);
    showContextWorkspace(trigger,context,previous);
  } catch (error) {
    showToast(error.message || 'Discord context is temporarily unavailable.',true);
  }
}

function surroundingConversation(trigger,around) {
  const byId = new Map([[trigger.id,trigger]]);
  for (const message of around) {
    if (message.channelId === trigger.channelId) byId.set(message.id,message);
  }
  return [...byId.values()].sort((left,right) => new Date(right.time) - new Date(left.time));
}

function showContextWorkspace(trigger,context,previous) {
  state.contextReturn = previous;
  baseMessages.splice(0,baseMessages.length,...context);
  state.search = '';
  state.author = '';
  state.channel = trigger.channelId;
  state.date = 'all';
  state.dateFrom = '';
  state.dateTo = '';
  state.selectedOnly = false;
  state.contextId = trigger.id;
  liveModeration.olderCursor = null;
  liveModeration.newerCursor = null;
  renderAll();
}

function browseContextAlertNode() {
  return element('div',{className:'alert info'},
    element('strong',{text:'Conversation context'}),
    element('span',{text:'Showing up to 50 surrounding messages from this channel, including messages from every author. The selected message is highlighted.'}),
    buttonNode('Exit context','text-button',{exitContext:''}));
}

function browseMessageCoverageNode() {
  const range = loadedMessageRange();
  const coverage = state.contextId ? 'Up to 50 surrounding messages in this channel' : 'Partial until Discord history is fully paged';
  const detail = state.contextId
    ? 'Show context uses Discord’s around-message history so the conversation includes nearby messages from all authors, not only the selected player.'
    : messageCoverageExplanation();
  return element('section',{className:'card coverage-card'},sectionHeading('Search coverage'),
    summaryList([['Messages loaded',baseMessages.length],['Loaded date range',range],['Filters search','Loaded messages only'],['Coverage',coverage]]),
    element('p',{className:'muted small',text:detail}));
}

function browseRenderSelectionBar() {
  hardenedRenderSelectionBar();
  if (state.selected.size !== 1) return;
  const actions = $('#selectionBar .selection-actions');
  if (!actions) return;
  const button = buttonNode('Show context','button secondary',{selectedContext:''});
  actions.insertBefore(button, actions.firstChild);
  button.addEventListener('click',() => browseShowMessageContext([...state.selected][0]));
}

function browseSimulationRequest(session) {
  const request = browseBaseSimulationRequest(session);
  if (!liveModeration.targetSelected || !state.activeTargetKey) return request;
  const payload = JSON.parse(request.body);
  payload.target = state.activeTargetKey;
  return {...request,body:JSON.stringify(payload)};
}

window.sessionBoundChannelId = browseSessionBoundChannelId;
window.applyLiveBootstrap = browseApplyLiveBootstrap;
window.renderTargetHeader = browseRenderTargetHeader;
window.renderContextPanel = browseRenderContextPanel;
window.renderCounts = browseRenderCounts;
window.messageNode = browseMessageNode;
window.bindMessageEvents = browseBindMessageEvents;
window.showTwoMinuteContext = browseShowMessageContext;
window.contextAlertNode = browseContextAlertNode;
window.messageCoverageNode = browseMessageCoverageNode;
window.renderSelectionBar = browseRenderSelectionBar;
window.simulationRequest = browseSimulationRequest;
