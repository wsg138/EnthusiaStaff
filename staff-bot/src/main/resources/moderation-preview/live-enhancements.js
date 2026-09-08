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
