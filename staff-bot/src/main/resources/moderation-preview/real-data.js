'use strict';

const liveModeration = {
  bootstrap: null,
  cases: [],
  notes: [],
  sanctions: [],
  accounts: [],
  channels: [],
  warning: '',
  olderCursor: null,
  newerCursor: null
};

function fillScenarioSelect() {
  const control = $('.scenario-control');
  if (control) control.hidden = true;
}

function applyScenario() {
  state.scenario = 'live';
  state.selected.clear();
  state.evidence.clear();
  state.violating.clear();
  state.deleting.clear();
  state.anchor = null;
  state.contextId = null;
  state.history = [];
  state.search = '';
  state.channel = 'all';
  state.date = 'all';
  state.author = '';
  state.selectedOnly = false;
  state.evidenceRevision++;
  state.workflow = null;
  renderAll();
}

async function loadSession() {
  try {
    const sessionResponse = await fetch('/api/session', {headers:{Accept:'application/json'}});
    if (!sessionResponse.ok) throw readFailure('session_unavailable', 'Session unavailable.');
    state.session = await sessionResponse.json();
    const bootstrapResponse = await requestDirectModerationRead('/api/bootstrap', {headers:{Accept:'application/json'}});
    const payload = await readJsonResponse(bootstrapResponse);
    if (!bootstrapResponse.ok) throw responseFailure(bootstrapResponse, payload);
    applyLiveBootstrap(payload);
    $('#actorMeta').textContent = payload.actor?.displayName || 'Verified staff session';
  } catch (error) {
    const failure = classifyReadFailure(error);
    clearLiveData(failure);
    $('#actorMeta').textContent = failure.actorMeta;
    showToast(failure.message, true);
    renderAll();
  }
}

function responseFailure(response, payload) {
  const code = optionalText(payload?.code);
  const message = firstText(payload?.message, 'Moderation data is temporarily unavailable.');
  if (response.status === 403 || code === 'forbidden') return readFailure('forbidden', message);
  if (response.status === 503 || code === 'source_unavailable') return readFailure('source_unavailable', message);
  if (response.status === 401 || code === 'unauthorized') return readFailure('request_rejected', message);
  return readFailure('read_failed', message);
}

function readFailure(code, message) {
  const error = new Error(message);
  error.readCode = code;
  return error;
}

function classifyReadFailure(error) {
  const code = optionalText(error?.readCode);
  if (code === 'forbidden') {
    return {
      actorMeta:'Access denied · staff authority not verified',
      statusDetail:'Current staff identity is not authorized for this read',
      warning:'Access denied. Verify the Discord-to-Minecraft staff link and current Enthusia staff rank.',
      message:'Access denied for this staff session.'
    };
  }
  if (code === 'source_unavailable') {
    return {
      actorMeta:'Backend unavailable · read source failed',
      statusDetail:'Authoritative read source is unavailable',
      warning:'Backend unavailable. The database, Paper authority bridge, or Discord read dependency failed.',
      message:'Moderation backend is temporarily unavailable.'
    };
  }
  if (code === 'session_unavailable' || code === 'request_rejected') {
    return {
      actorMeta:'Session unavailable · reopen from Discord',
      statusDetail:'The signed moderation session is unavailable',
      warning:'Session unavailable. Reopen the moderation preview from Discord.',
      message:'Session unavailable. Reopen the panel from Discord.'
    };
  }
  return {
    actorMeta:'Read transport unavailable',
    statusDetail:'Browser read transport did not complete',
    warning:'Read transport unavailable. The protected request did not complete.',
    message:firstText(error?.message, 'Moderation data is temporarily unavailable.')
  };
}

async function readJsonResponse(response) {
  try {
    return await response.json();
  } catch {
    return {message:'Moderation data is temporarily unavailable.'};
  }
}

function asArray(value) {
    return Array.isArray(value) ? value : [];
}

function firstText(...values) {
    for (const value of values) {
        if (typeof value === 'string' && value.length > 0) return value;
    }
    return '';
}

function optionalText(value) {
    return typeof value === 'string' ? value : '';
}

function nullableText(value) {
    const text = optionalText(value);
    return text.length > 0 ? text : null;
}

function applyLiveBootstrap(payload) {
    liveModeration.bootstrap = payload;
    applyLiveCollections(payload);
    applyIdentity(payload.identity ?? {});
    applyRestrictionTargets();
    state.history = asArray(payload.history).map(mapHistoryRow);
    replaceMessagePage(payload.messages ?? {});
    state.contextId = nullableText(payload.centeredMessageId);
    renderAll();
}

function applyLiveCollections(payload) {
    liveModeration.cases = asArray(payload.cases);
    liveModeration.notes = asArray(payload.notes);
    liveModeration.sanctions = asArray(payload.activeSanctions);
    liveModeration.accounts = asArray(payload.linkedAccounts);
    liveModeration.channels = asArray(payload.channels);
}

function clearLiveData(failure = classifyReadFailure(null)) {
  liveModeration.bootstrap = null;
  liveModeration.cases = [];
  liveModeration.notes = [];
  liveModeration.sanctions = [];
  liveModeration.accounts = [];
  liveModeration.channels = [];
  liveModeration.warning = failure.warning;
  baseMessages.splice(0);
  state.history = [];
  Object.assign(identity, {
    displayName:'Data unavailable', username:'unavailable', discordId:'Unavailable', minecraft:'Unavailable',
    minecraftUuid:'Unavailable', alts:[], status:'Unavailable', statusDetail:failure.statusDetail, avatarUrl:''
  });
  RESTRICTION_TARGETS.splice(0);
}

function applyIdentity(source) {
    const main = liveModeration.accounts.find((account) => account.main);
    const alts = liveModeration.accounts.filter((account) => !account.main).map(mapLinkedAlt);
    Object.assign(identity, {
        displayName: firstText(source.displayName, source.serverName, source.globalName, source.username, source.discordId, 'Unknown Discord user'),
        username: firstText(source.username, 'unknown'),
        discordId: firstText(source.discordId, 'Unavailable'),
        minecraft: firstText(source.minecraftMain, linkedAccountName(main), 'No linked Minecraft main'),
        minecraftUuid: firstText(main?.playerId, 'Unavailable'), alts,
        status: firstText(source.targetStatus, 'Unknown'), statusDetail: sanctionStatusDetail(),
        avatarUrl: optionalText(source.avatarUrl), linkState: firstText(source.linkState, 'Unknown'),
        globalName: optionalText(source.globalName), serverName: optionalText(source.serverName)
    });
}

function mapLinkedAlt(account) {
    return {name:firstText(account.username, account.playerId), platform:friendlyPlatform(account.platform), status:'Linked', playerId:account.playerId};
}

function linkedAccountName(account) {
    return account ? firstText(account.username, account.playerId) : '';
}

function sanctionStatusDetail() {
    const count = liveModeration.sanctions.length;
    return count > 0 ? `${count} active` : 'No active sanctions';
}

function friendlyPlatform(value) {
  if (!value) return 'Unknown';
  return String(value).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function applyRestrictionTargets() {
  RESTRICTION_TARGETS.splice(0);
  const categories = new Map();
  for (const channel of liveModeration.channels) {
    RESTRICTION_TARGETS.push({id:channel.id, type:'channel', label:`#${channel.name}`, detail:channel.categoryName || 'Uncategorized'});
    if (channel.categoryId && channel.categoryName) {
      const current = categories.get(channel.categoryId) || {id:channel.categoryId, name:channel.categoryName, count:0};
      current.count += 1;
      categories.set(channel.categoryId, current);
    }
  }
  for (const category of categories.values()) {
    RESTRICTION_TARGETS.push({id:category.id, type:'category', label:category.name, detail:`Category · ${category.count} channel${category.count === 1 ? '' : 's'}`});
  }
}

function replaceMessagePage(page) {
  baseMessages.splice(0, baseMessages.length, ...(Array.isArray(page.messages) ? page.messages.map(mapMessage) : []));
  liveModeration.olderCursor = page.olderCursor || null;
  liveModeration.newerCursor = page.newerCursor || null;
  liveModeration.warning = page.warning || '';
}

function appendMessagePage(page, direction) {
  const incoming = Array.isArray(page.messages) ? page.messages.map(mapMessage) : [];
  const known = new Set(baseMessages.map((message) => message.id));
  const unique = incoming.filter((message) => !known.has(message.id));
  if (direction === 'older') baseMessages.push(...unique);
  else baseMessages.unshift(...unique);
  liveModeration.olderCursor = page.olderCursor || liveModeration.olderCursor;
  liveModeration.newerCursor = page.newerCursor || liveModeration.newerCursor;
  liveModeration.warning = page.warning || liveModeration.warning;
}

async function loadMessageRequest(input, direction = 'replace') {
  try {
    const response = await requestDirectModerationRead('/api/messages', {
      method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(input)
    });
    const payload = await readJsonResponse(response);
    if (!response.ok) throw responseFailure(response, payload);
    if (direction === 'replace') replaceMessagePage(payload);
    else appendMessagePage(payload, direction);
    renderAll();
  } catch (error) {
    const failure = classifyReadFailure(error);
    liveModeration.warning = failure.warning;
    showToast(failure.message, true);
    renderAll();
  }
}

function mapMessage(message) {
  return {
    id:firstText(message.id, 'unknown'),
    author:firstText(message.authorDisplayName, message.authorUsername, message.authorId, 'Unknown'),
    username:firstText(message.authorUsername, 'unknown'),
    initials:initials(firstText(message.authorDisplayName, message.authorUsername, '?')),
    target:Boolean(message.target), channel:firstText(message.channelName, message.channelId, 'unknown'),
    channelId:firstText(message.channelId), time:firstText(message.createdAt, new Date(0).toISOString()),
    text:firstText(message.content, message.contentAvailable === false ? 'Text content unavailable from Discord' : ''),
    edited:Boolean(message.edited), deleted:false, replyTo:nullableText(message.replyToMessageId),
    attachment:firstAttachment(message.attachments)
  };
}

function firstAttachment(attachments) {
  const item = asArray(attachments)[0];
  if (!item) return null;
  return {name:firstText(item.fileName, 'Attachment'), detail:firstText(item.contentType, 'attachment')};
}

function initials(value) {
  return value.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase() || '').join('') || '?';
}
