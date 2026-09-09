'use strict';

const HARDENED_INLINE_TOKEN_PATTERN = /(\[[^\]\n]+\]\(https?:\/\/[^)\s]+\)|<a?:[A-Za-z0-9_]+:[0-9]+>|`[^`\n]+`|\*\*[^*\n]+\*\*|__[^_\n]+__|~~[^~\n]+~~|\|\|[^|\n]+\|\||\*[^*\n]+\*|_[^_\n]+_)/g;

function hardenedMessageActionsNode(message) {
  const menu = element('details', {className:'message-actions'});
  const summary = element('summary', {className:'icon-button', text:'•••', attrs:{
    'aria-label':`Actions for message from ${message.author} at ${formatExact(message.time)}`,
    'aria-haspopup':'menu', 'aria-expanded':'false'
  }});
  const items = element('div', {className:'message-action-menu', attrs:{role:'menu'}},
    hardenedMessageActionButton('Show context', 'context', message),
    hardenedMessageActionButton(state.evidence.has(message.id) ? 'Remove evidence' : 'Add to evidence', 'evidence', message),
    hardenedMessageActionButton(state.deleting.has(message.id) ? 'Remove simulated deletion' : 'Mark for simulated deletion', 'delete', message),
    openInDiscordNode(message));
  menu.append(summary, items);
  return menu;
}

function hardenedMessageActionButton(label, action, message) {
  return element('button', {type:'button', className:'message-action-item', text:label,
    dataset:{messageAction:action, messageId:message.id}, attrs:{role:'menuitem', 'aria-label':`${label} for message ${message.id}`}});
}

function openInDiscordNode(message) {
  const guildId = message.guildId || state.session?.guildId;
  if (!guildId || !message.channelId || !message.id) {
    return element('span', {className:'message-action-item unavailable', text:'Open in Discord unavailable', attrs:{role:'menuitem','aria-disabled':'true'}});
  }
  return element('a', {className:'message-action-item', text:'Open in Discord', attrs:{
    href:`https://discord.com/channels/${guildId}/${message.channelId}/${message.id}`,
    target:'_blank', rel:'noopener noreferrer', role:'menuitem',
    'aria-label':`Open message ${message.id} in Discord`
  }});
}

async function hardenedHandleMessageAction(event) {
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

function bindMessageMenuKeyboard() {
  $$('.message-actions').forEach((details) => {
    const summary = details.querySelector('summary');
    summary?.addEventListener('keydown', (event) => handleMenuSummaryKey(event, details));
    details.addEventListener('keydown', (event) => handleMenuItemKey(event, details));
    details.addEventListener('toggle', () => summary?.setAttribute('aria-expanded', String(details.open)));
  });
}

function handleMenuSummaryKey(event, details) {
  if (!['ArrowDown', 'ArrowUp'].includes(event.key)) return;
  event.preventDefault();
  details.open = true;
  const items = menuItems(details);
  (event.key === 'ArrowUp' ? items.at(-1) : items[0])?.focus();
}

function handleMenuItemKey(event, details) {
  if (event.key === 'Escape') {
    event.preventDefault();
    details.open = false;
    details.querySelector('summary')?.focus();
    return;
  }
  if (!['ArrowDown', 'ArrowUp'].includes(event.key)) return;
  const items = menuItems(details);
  const index = items.indexOf(document.activeElement);
  if (index < 0) return;
  event.preventDefault();
  const delta = event.key === 'ArrowDown' ? 1 : -1;
  items[(index + delta + items.length) % items.length]?.focus();
}

function menuItems(details) {
  return [...details.querySelectorAll('[role="menuitem"]')].filter((item) => item.getAttribute('aria-disabled') !== 'true');
}

function hardenedMessageStatusNodes(message) {
  const status = element('div', {className:'message-statuses'});
  if (state.evidence.has(message.id)) status.append(messagePill('Evidence', 'evidence'));
  if (state.violating.has(message.id)) status.append(messagePill('Violating', 'violating'));
  if (state.deleting.has(message.id)) status.append(messagePill('Simulated delete', 'delete'));
  return status;
}

function hardenedRenderSelectionBar() {
  const bar = $('#selectionBar');
  if (state.selected.size === 0) {
    bar.hidden = true;
    bar.replaceChildren();
    return;
  }
  bar.hidden = false;
  const summary = element('div', {className:'selection-summary'},
    element('strong', {text:`${state.selected.size} selected`}),
    element('span', {text:`${state.evidence.size} evidence · ${state.deleting.size} simulated deletions`}));
  const actions = element('div', {className:'selection-actions'},
    selectionButton(allSelectedIn(state.evidence) ? 'Remove Evidence' : 'Add to Evidence', 'secondary', 'evidence'),
    selectionButton(allSelectedIn(state.violating) ? 'Clear Violating' : 'Mark Violating', 'secondary', 'violating'),
    selectionButton(allSelectedIn(state.deleting) ? 'Remove Simulated Delete' : 'Simulate Delete', 'danger-secondary', 'delete'),
    selectionButton('Remove Selection', 'ghost', 'clear'));
  replaceChildrenOf(bar, summary, actions);
  $$('[data-selection-action]').forEach((button) => button.addEventListener('click', () => selectionAction(button.dataset.selectionAction)));
}

function hardenedDiscordMessageContentNode(content) {
  const root = element('div', {className:'message-text discord-message'});
  let codeLines = null;
  for (const line of String(content).split('\n')) {
    codeLines = appendDiscordLine(root, line, codeLines);
  }
  if (codeLines !== null) root.append(hardenedCodeBlock(codeLines));
  return root;
}

function appendDiscordLine(root, line, codeLines) {
  if (line.startsWith('```')) {
    if (codeLines === null) return [];
    root.append(hardenedCodeBlock(codeLines));
    return null;
  }
  if (codeLines !== null) {
    codeLines.push(line);
    return codeLines;
  }
  root.append(hardenedDiscordLineNode(line));
  return null;
}

function hardenedDiscordLineNode(line) {
  const heading = /^(#{1,3})\s+(.+)$/.exec(line);
  if (heading) return element('div', {className:`discord-heading level-${heading[1].length}`}, hardenedInlineNodes(heading[2]));
  const quote = /^>\s?(.*)$/.exec(line);
  if (quote) return element('blockquote', {className:'discord-quote'}, hardenedInlineNodes(quote[1]));
  const bullet = /^[-*]\s+(.+)$/.exec(line);
  if (bullet) return element('div', {className:'discord-list-item'}, element('span', {className:'discord-bullet', text:'•'}), hardenedInlineNodes(bullet[1]));
  if (!line) return element('div', {className:'discord-line discord-empty'}, '\u00a0');
  return element('div', {className:'discord-line'}, hardenedInlineNodes(line));
}

function hardenedCodeBlock(lines) {
  return element('pre', {className:'discord-code-block'}, element('code', {text:lines.join('\n')}));
}

function hardenedInlineNodes(text) {
  const nodes = [];
  let index = 0;
  for (const match of String(text).matchAll(HARDENED_INLINE_TOKEN_PATTERN)) {
    if (match.index > index) nodes.push(document.createTextNode(text.slice(index, match.index)));
    nodes.push(hardenedInlineToken(match[0]));
    index = match.index + match[0].length;
  }
  if (index < text.length) nodes.push(document.createTextNode(text.slice(index)));
  return nodes;
}

function hardenedInlineToken(token) {
  const emoji = customEmojiNode(token);
  if (emoji) return emoji;
  const link = safeMarkdownLinkNode(token);
  if (link) return link;
  return formattingTokenNode(token);
}

function customEmojiNode(token) {
  const match = /^<(a?):([A-Za-z0-9_]+):([0-9]+)>$/.exec(token);
  if (!match) return null;
  const image = document.createElement('img');
  image.className = 'discord-custom-emoji';
  image.src = `https://cdn.discordapp.com/emojis/${match[3]}.${match[1] ? 'gif' : 'png'}?size=32&quality=lossless`;
  image.alt = `:${match[2]}:`;
  image.referrerPolicy = 'no-referrer';
  return image;
}

function safeMarkdownLinkNode(token) {
  const match = /^\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)$/.exec(token);
  if (!match) return null;
  return element('a', {text:match[1], attrs:{href:match[2], target:'_blank', rel:'noopener noreferrer'}});
}

function formattingTokenNode(token) {
  if (token.startsWith('**')) return element('strong', {text:token.slice(2, -2)});
  if (token.startsWith('__')) return element('u', {text:token.slice(2, -2)});
  if (token.startsWith('~~')) return element('s', {text:token.slice(2, -2)});
  if (token.startsWith('||')) return element('span', {className:'discord-spoiler', text:token.slice(2, -2)});
  if (token.startsWith('`')) return element('code', {className:'discord-inline-code', text:token.slice(1, -1)});
  return element('em', {text:token.slice(1, -1)});
}

window.messageActionsNode = hardenedMessageActionsNode;
window.messageStatusNodes = hardenedMessageStatusNodes;
window.renderSelectionBar = hardenedRenderSelectionBar;
window.discordMessageContentNode = hardenedDiscordMessageContentNode;
