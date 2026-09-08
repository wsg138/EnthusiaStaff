'use strict';

const DISCORD_INLINE_PATTERN = /(`[^`\n]+`|\*\*[^*\n]+\*\*|__[^_\n]+__|~~[^~\n]+~~|\|\|[^|\n]+\|\||\*[^*\n]+\*|_[^_\n]+_)/g;

function messageBodyNode(message) {
  const body = element('div', {className:'message-body'});
  if (message.replyTo) body.append(element('div', {className:'reply-reference', text:`↳ Replying to message ${message.replyTo}`}));
  body.append(messageMetaNode(message));
  if (message.deleted) {
    body.append(element('div', {className:'message-text'}, element('em', {text:'Message is known deleted in authoritative source state'})));
  } else if (message.text) {
    body.append(discordMessageContentNode(message.text));
  } else {
    body.append(element('div', {className:'message-text'}, element('em', {text:'Text content unavailable from Discord'})));
  }
  for (const attachment of message.attachments || []) body.append(attachmentNode(attachment));
  body.append(messageStatusNodes(message));
  body.append(element('div', {className:'message-id', text:`Message ID ${message.id}`}));
  return body;
}

function discordMessageContentNode(content) {
  const root = element('div', {className:'message-text discord-message'});
  let codeLines = null;
  for (const line of String(content).split('\n')) {
    if (line.startsWith('```')) {
      if (codeLines === null) codeLines = [];
      else {
        root.append(discordCodeBlock(codeLines));
        codeLines = null;
      }
      continue;
    }
    if (codeLines !== null) codeLines.push(line);
    else root.append(discordLineNode(line));
  }
  if (codeLines !== null) root.append(discordCodeBlock(codeLines));
  return root;
}

function discordLineNode(line) {
  const heading = /^(#{1,3})\s+(.+)$/.exec(line);
  if (heading) {
    return element('div', {className:`discord-heading level-${heading[1].length}`}, discordInlineNodes(heading[2]));
  }
  const quote = /^>\s?(.*)$/.exec(line);
  if (quote) return element('blockquote', {className:'discord-quote'}, discordInlineNodes(quote[1]));
  const bullet = /^[-*]\s+(.+)$/.exec(line);
  if (bullet) {
    return element('div', {className:'discord-list-item'}, element('span', {className:'discord-bullet', text:'•'}), discordInlineNodes(bullet[1]));
  }
  if (line.length === 0) return element('div', {className:'discord-line discord-empty'}, '\u00a0');
  return element('div', {className:'discord-line'}, discordInlineNodes(line));
}

function discordCodeBlock(lines) {
  return element('pre', {className:'discord-code-block'}, element('code', {text:lines.join('\n')}));
}

function discordInlineNodes(text) {
  const nodes = [];
  let index = 0;
  for (const match of String(text).matchAll(DISCORD_INLINE_PATTERN)) {
    if (match.index > index) nodes.push(document.createTextNode(text.slice(index, match.index)));
    nodes.push(discordInlineToken(match[0]));
    index = match.index + match[0].length;
  }
  if (index < text.length) nodes.push(document.createTextNode(text.slice(index)));
  return nodes;
}

function discordInlineToken(token) {
  if (token.startsWith('**')) return element('strong', {text:token.slice(2, -2)});
  if (token.startsWith('__')) return element('u', {text:token.slice(2, -2)});
  if (token.startsWith('~~')) return element('s', {text:token.slice(2, -2)});
  if (token.startsWith('||')) return element('span', {className:'discord-spoiler', text:token.slice(2, -2)});
  if (token.startsWith('`')) return element('code', {className:'discord-inline-code', text:token.slice(1, -1)});
  return element('em', {text:token.slice(1, -1)});
}

function accountsNode() {
  const discordName = firstText(identity.serverName, identity.globalName, identity.displayName, identity.username, 'Unknown Discord user');
  const cards = [liveAccountCard(
    'Discord identity',
    discordName,
    `@${identity.username}`,
    [
      ['Username', `@${identity.username}`],
      ['Display name', discordName],
      ['Discord ID', identity.discordId],
      ['Link state', identity.linkState || 'Unknown']
    ],
    discordProfileAvatar(identity.avatarUrl, discordName))];
  for (const account of liveModeration.accounts) cards.push(minecraftAccountCard(account));
  return element('div', {},
    pageHeading('Identity graph','Accounts','Discord identity and linked Minecraft profiles returned for this moderation target.'),
    element('div',{className:'account-grid'}, cards));
}

function minecraftAccountCard(account) {
  const username = firstText(account.username, account.playerId, 'Unknown Minecraft account');
  return liveAccountCard(
    account.main ? 'Minecraft main' : 'Linked Minecraft account',
    username,
    minecraftAccountDetail(account),
    [
      ['Username', username],
      ['UUID', account.playerId],
      ['Relationship', account.main ? 'Main' : 'Linked account']
    ],
    minecraftProfileAvatar(account.skinTextureUrl, username));
}

function minecraftAccountDetail(account) {
  const platform = friendlyPlatform(account.platform);
  const relationship = account.main ? 'Main account' : 'Linked account';
  return platform && platform !== 'Unknown' ? `${platform} · ${relationship}` : relationship;
}

function liveAccountCard(eyebrow, title, detail, rows, avatar) {
  const summary = element('div', {className:'live-account-summary'},
    avatar,
    element('div', {className:'live-account-copy'},
      element('span', {className:'eyebrow', text:eyebrow}),
      element('h3', {text:title}),
      element('p', {text:detail})));
  const details = element('dl', {className:'detail-list'}, rows.map(([label, value]) =>
    element('div', {}, element('dt', {text:label}), element('dd', {text:value}))));
  return element('section', {className:'card live-account-card'}, summary, details);
}

function discordProfileAvatar(url, label) {
  if (!url) return accountAvatarFallback(label, 'discord');
  const image = document.createElement('img');
  image.className = 'account-profile-avatar discord';
  image.src = url;
  image.alt = `${label} Discord profile picture`;
  image.referrerPolicy = 'no-referrer';
  image.addEventListener('error', () => image.replaceWith(accountAvatarFallback(label, 'discord')));
  return image;
}

function minecraftProfileAvatar(textureUrl, label) {
  if (!textureUrl) return accountAvatarFallback(label, 'minecraft');
  const frame = element('div', {
    className:'minecraft-head-avatar',
    attrs:{role:'img', 'aria-label':`${label} Minecraft skin`}
  });
  const base = minecraftSkinLayer(textureUrl, 'base');
  const overlay = minecraftSkinLayer(textureUrl, 'overlay');
  base.addEventListener('error', () => frame.replaceWith(accountAvatarFallback(label, 'minecraft')));
  overlay.addEventListener('error', () => overlay.remove());
  frame.append(base, overlay);
  return frame;
}

function minecraftSkinLayer(textureUrl, layer) {
  const image = document.createElement('img');
  image.className = `minecraft-skin-layer ${layer}`;
  image.src = textureUrl;
  image.alt = '';
  image.referrerPolicy = 'no-referrer';
  image.draggable = false;
  return image;
}

function accountAvatarFallback(label, kind) {
  return element('div', {
    className:`account-profile-avatar fallback ${kind}`,
    text:initials(label),
    attrs:{'aria-hidden':'true'}
  });
}

const liveBaseMapMessage = window.mapMessage;
window.mapMessage = function mapLiveMessage(message) {
  const mapped = liveBaseMapMessage(message);
  mapped.avatarUrl = optionalText(message?.author?.avatarUrl);
  return mapped;
};

window.applyIdentity = function applyLiveIdentity(source) {
  const main = liveModeration.accounts.find((account) => account.main) || liveModeration.accounts[0];
  const alts = liveModeration.accounts.filter((account) => account !== main).map(mapLinkedAlt);
  Object.assign(identity, {
    displayName:firstText(source.displayName, source.serverName, source.globalName, source.username, source.discordId, 'Unknown Discord user'),
    username:firstText(source.username, 'unknown'),
    discordId:firstText(source.discordId, 'Unavailable'),
    minecraft:firstText(source.minecraftMain, linkedAccountName(main), 'No linked Minecraft account'),
    minecraftUuid:firstText(main?.playerId, 'Unavailable'),
    alts,
    status:firstText(source.targetStatus, 'Unknown'),
    statusDetail:sanctionStatusDetail(),
    avatarUrl:optionalText(source.avatarUrl),
    linkState:firstText(source.linkState, 'Unknown'),
    globalName:optionalText(source.globalName),
    serverName:optionalText(source.serverName)
  });
};

const liveBaseApplyBootstrap = window.applyLiveBootstrap;
window.applyLiveBootstrap = function applyContextualBootstrap(payload) {
  liveBaseApplyBootstrap(payload);
  const channelId = sessionBoundChannelId();
  if (channelId && liveModeration.channels.some((channel) => channel.id === channelId)) {
    state.channel = channelId;
    renderAll();
  }
};

function sessionBoundChannelId() {
  const key = state.session?.targetKey;
  if (typeof key !== 'string') return '';
  const parts = key.split(':');
  if (parts[0] === 'discord-channel' && parts.length === 3) return parts[1];
  if (parts[0] === 'message' && parts.length === 4) return parts[1];
  return '';
}

window.messageNode = function liveMessageNode(message) {
  const selected = state.selected.has(message.id);
  const focused = state.contextId === message.id;
  const classes = ['message-row', selected ? 'selected' : '', message.deleted ? 'deleted' : '', focused ? 'context-focus' : '']
    .filter(Boolean).join(' ');
  const checkbox = element('input', {type:'checkbox', className:'message-select', checked:selected,
    attrs:{'aria-label':`Select message from ${message.author} at ${formatExact(message.time)}`}});
  return element('article', {className:classes, dataset:{messageId:message.id}},
    element('div', {}, checkbox), messageAvatarNode(message), messageBodyNode(message), messageActionsNode(message));
};

function messageAvatarNode(message) {
  if (!message.avatarUrl) {
    return element('div', {className:`message-avatar${message.target ? ' target' : ''}`, text:message.initials, attrs:{'aria-hidden':'true'}});
  }
  const image = document.createElement('img');
  image.className = `message-avatar message-avatar-image${message.target ? ' target' : ''}`;
  image.src = message.avatarUrl;
  image.alt = `${message.author} profile picture`;
  image.referrerPolicy = 'no-referrer';
  image.addEventListener('error', () => image.replaceWith(
    element('div', {className:`message-avatar${message.target ? ' target' : ''}`, text:message.initials, attrs:{'aria-hidden':'true'}})));
  return image;
}

function messageActionsNode(message) {
  const menu = element('details', {className:'message-actions'});
  const summary = element('summary', {className:'icon-button', text:'•••', attrs:{'aria-label':`Message actions for ${message.id}`}});
  const items = element('div', {className:'message-action-menu'},
    messageActionButton('Show context', 'context', message.id),
    messageActionButton(state.evidence.has(message.id) ? 'Remove evidence' : 'Add to evidence', 'evidence', message.id),
    messageActionButton(state.deleting.has(message.id) ? 'Preserve message' : 'Delete on confirm (simulation)', 'delete', message.id));
  menu.append(summary, items);
  return menu;
}

function messageActionButton(label, action, messageId) {
  return buttonNode(label, 'message-action-item', {messageAction:action, messageId});
}

window.bindMessageEvents = function bindLiveMessageEvents() {
  $('[data-exit-context]')?.addEventListener('click', exitLiveContext);
  $('#messageSearch')?.addEventListener('input', (event) => { state.search = event.target.value; renderWorkspace(); });
  $('#authorFilter')?.addEventListener('input', (event) => { state.author = event.target.value; renderWorkspace(); });
  $('#channelFilter')?.addEventListener('change', (event) => {
    state.channel = event.target.value;
    state.contextId = null;
    state.contextReturn = null;
    loadChannelPage();
  });
  $('#dateFilter')?.addEventListener('change', (event) => { state.date = event.target.value || 'all'; renderWorkspace(); });
  $('#selectedFilter')?.addEventListener('change', (event) => { state.selectedOnly = event.target.checked; renderWorkspace(); });
  $$('.message-select').forEach((checkbox) => checkbox.addEventListener('click', selectMessage));
  $$('[data-message-action]').forEach((button) => button.addEventListener('click', handleMessageAction));
  $$('[data-load-direction]').forEach((button) => button.addEventListener('click', () => loadMoreMessages(button.dataset.loadDirection)));
};

async function handleMessageAction(event) {
  const button = event.currentTarget;
  const id = button.dataset.messageId;
  const action = button.dataset.messageAction;
  button.closest('details')?.removeAttribute('open');
  if (action === 'context') {
    await showTwoMinuteContext(id);
    return;
  }
  const target = action === 'evidence' ? state.evidence : state.deleting;
  toggleSet(target, id, !target.has(id));
  state.evidenceRevision++;
  renderAll();
}

async function showTwoMinuteContext(id) {
  const trigger = baseMessages.find((message) => message.id === id);
  if (!trigger) return;
  const previous = rememberMessageView();
  try {
    const [before, after] = await Promise.all([
      fetchContextPage(trigger.channelId, 'before', id),
      fetchContextPage(trigger.channelId, 'after', id)
    ]);
    const context = boundedTimeContext(trigger, before, after);
    state.contextReturn = previous;
    baseMessages.splice(0, baseMessages.length, ...context);
    state.search = '';
    state.author = '';
    state.channel = trigger.channelId;
    state.date = 'all';
    state.selectedOnly = false;
    state.contextId = id;
    liveModeration.olderCursor = null;
    liveModeration.newerCursor = null;
    renderAll();
  } catch (error) {
    showToast(error.message || 'Discord context is temporarily unavailable.', true);
  }
}

function rememberMessageView() {
  return {
    messages:baseMessages.slice(),
    search:state.search,
    author:state.author,
    channel:state.channel,
    date:state.date,
    selectedOnly:state.selectedOnly,
    olderCursor:liveModeration.olderCursor,
    newerCursor:liveModeration.newerCursor
  };
}

async function fetchContextPage(channelId, direction, messageId) {
  const filters = {channel:channelId, limit:'50'};
  filters[direction] = messageId;
  const response = await requestDirectModerationRead('/api/messages', {
    method:'POST',
    headers:{Accept:'application/json', 'Content-Type':'application/json'},
    body:JSON.stringify(filters)
  });
  const page = await readJsonResponse(response);
  if (!response.ok) throw new Error(page.message || 'Discord context unavailable');
  return asArray(page.messages).map(window.mapMessage);
}

function boundedTimeContext(trigger, before, after) {
  const triggerTime = new Date(trigger.time).getTime();
  const byId = new Map([[trigger.id, trigger]]);
  for (const message of [...before, ...after]) {
    if (Math.abs(new Date(message.time).getTime() - triggerTime) <= 120_000) byId.set(message.id, message);
  }
  return [...byId.values()].sort((left, right) => new Date(right.time) - new Date(left.time));
}

function exitLiveContext() {
  const previous = state.contextReturn;
  state.contextId = null;
  state.contextReturn = null;
  if (!previous) {
    renderAll();
    return;
  }
  baseMessages.splice(0, baseMessages.length, ...previous.messages);
  state.search = previous.search;
  state.author = previous.author;
  state.channel = previous.channel;
  state.date = previous.date;
  state.selectedOnly = previous.selectedOnly;
  liveModeration.olderCursor = previous.olderCursor;
  liveModeration.newerCursor = previous.newerCursor;
  renderAll();
}

window.contextMessageIds = function liveContextMessageIds() {
  return new Set(baseMessages.map((message) => message.id));
};

window.contextAlertNode = function liveContextAlertNode() {
  return element('div', {className:'alert info'},
    element('strong', {text:'Two-minute conversation context'}),
    element('span', {text:'The selected message is highlighted with messages from all authors within two minutes before and after it.'}),
    buttonNode('Exit context', 'text-button', {exitContext:''}));
};

const liveBaseOpenWorkflow = window.openWorkflow;
window.openWorkflow = function openOrResumeWorkflow() {
  if (!state.workflow) {
    liveBaseOpenWorkflow();
    return;
  }
  renderWorkflow();
  const dialog = $('#punishmentDialog');
  if (!dialog.open) dialog.showModal();
};

window.renderOffenseStep = function renderLiveOffenseStep() {
  const w = state.workflow;
  if (!w.offenseTab) w.offenseTab = 'discord';
  const suggested = scenarioOffense();
  const offenses = OFFENSES.filter(([key]) => w.offenseTab === 'game' ? key === 'cheating' : key !== 'cheating');
  replaceChildrenOf($('#workflowBody'),
    stepIntro('What happened?', 'Choose Discord/chat policy by default, or switch to in-game policy for gameplay violations.'),
    punishmentScopeTabs(w.offenseTab),
    element('div', {className:'option-grid'}, offenses.map(([key, label]) => offenseChoiceNode(key, label, suggested))));
  replaceChildrenOf($('#workflowFooter'), buttonNode('Cancel', 'button ghost', {cancel:''}));
  $$('[data-offense-tab]').forEach((button) => button.addEventListener('click', () => {
    w.offenseTab = button.dataset.offenseTab;
    renderWorkflow();
  }));
  $$('[data-offense]').forEach((button) => button.addEventListener('click', () => chooseOffense(button.dataset.offense)));
  $('[data-cancel]').addEventListener('click', closeWorkflow);
};

function punishmentScopeTabs(selected) {
  return element('div', {className:'punishment-scope-tabs', attrs:{role:'tablist', 'aria-label':'Punishment policy scope'}},
    punishmentScopeTab('discord', 'Discord / chat', selected),
    punishmentScopeTab('game', 'In-game', selected));
}

function punishmentScopeTab(value, label, selected) {
  return buttonNode(label, `punishment-scope-tab${selected === value ? ' active' : ''}`, {offenseTab:value});
}

function installEnthusiaBrandLogo() {
  const mark = $('.brand-mark');
  if (!mark) return;
  const logo = document.createElement('img');
  logo.className = 'brand-logo-image';
  logo.src = 'https://enthusia.info/assets/enthusia-logo-v2.png';
  logo.alt = 'Enthusia';
  logo.referrerPolicy = 'no-referrer';
  mark.replaceChildren(logo);
}

installEnthusiaBrandLogo();
