import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const SHELL = new URL('../../staff-bot/src/main/resources/moderation-preview/live-shell-usability.js', import.meta.url);
const MESSAGE = new URL('../../staff-bot/src/main/resources/moderation-preview/live-message-usability.js', import.meta.url);
const RECORD = new URL('../../staff-bot/src/main/resources/moderation-preview/live-record-usability.js', import.meta.url);
const REVIEW = new URL('../../staff-bot/src/main/resources/moderation-preview/live-review-hardening.js', import.meta.url);
const POLICY = new URL('../../staff-bot/src/main/resources/moderation-preview/real-policy.js', import.meta.url);
const CSS = new URL('../../staff-bot/src/main/resources/moderation-preview/live.css', import.meta.url);

test('product chrome removes staging diagnostics while final review keeps one truthful test boundary', async () => {
  const [shell, review] = await Promise.all([readFile(SHELL, 'utf8'), readFile(REVIEW, 'utf8')]);

  assert.match(shell, /\.scenario-control'\)\?\.remove\(\)/);
  assert.match(shell, /\.staging-badge'\)\?\.remove\(\)/);
  assert.match(shell, /Issue punishment/);
  assert.doesNotMatch(shell, /STAGING · REAL READS|Simulate punishment|Real data, simulated actions/);
  assert.match(review, /Testing note/);
  assert.match(review, /does not send punishments or DMs, change Discord permissions, or delete messages/);
  assert.match(review, /Confirm preview/);
});

test('message investigation explains partial coverage and supports paging, ranges, clearing, and Discord links', async () => {
  const [shell, message] = await Promise.all([readFile(SHELL, 'utf8'), readFile(MESSAGE, 'utf8')]);

  assert.match(shell, /Filters search', 'Loaded messages only/);
  assert.match(shell, /up to 50 target messages from at most 8 readable channels/);
  assert.match(shell, /up to 25 Discord messages at a time/);
  assert.match(shell, /four-page cap per direction/);
  assert.match(shell, /dateFromFilter/);
  assert.match(shell, /dateToFilter/);
  assert.match(shell, /Clear filters/);
  assert.match(shell, /Load older from Discord/);
  assert.match(message, /https:\/\/discord\.com\/channels\//);
});

test('message menus are exclusive, dismiss outside, support keyboard use, and copy message IDs', async () => {
  const source = await readFile(MESSAGE, 'utf8');

  assert.match(source, /aria-label':`Actions for message/);
  assert.match(source, /'aria-haspopup':'menu'/);
  assert.match(source, /role:'menuitem'/);
  assert.match(source, /closeOpenMessageMenus\(details\)/);
  assert.match(source, /document\.addEventListener\('pointerdown'/);
  assert.match(source, /Copy message ID/);
  assert.match(source, /navigator\.clipboard\?\.writeText/);
  assert.match(source, /ArrowDown/);
  assert.match(source, /ArrowUp/);
  assert.match(source, /Escape/);
});

test('whole message rows toggle selection without stealing clicks from controls', async () => {
  const source = await readFile(MESSAGE, 'utf8');

  assert.match(source, /row\.addEventListener\('click', handleMessageRowClick\)/);
  assert.match(source, /row\.addEventListener\('keydown', handleMessageRowKeydown\)/);
  assert.match(source, /a,button,input,summary,details/);
  assert.match(source, /selectRange\(state\.anchor, id, checked\)/);
  assert.match(source, /Press Space to toggle selection/);
});

test('message rows keep technical IDs in the menu and visually separate author metadata from content', async () => {
  const [message, css] = await Promise.all([readFile(MESSAGE, 'utf8'), readFile(CSS, 'utf8')]);

  const bodyStart = message.indexOf('function polishedMessageBodyNode');
  const bodyEnd = message.indexOf('function polishedMessageMetaNode', bodyStart);
  assert.ok(bodyStart >= 0 && bodyEnd > bodyStart);
  assert.doesNotMatch(message.slice(bodyStart, bodyEnd), /Message ID/);
  assert.doesNotMatch(message, /target-chip/);
  assert.match(message, /message-author-name/);
  assert.match(css, /\.message-meta \.message-author-name\{font-size:14px;font-weight:800/);
});

test('safe Discord renderer handles headings, inline code, and custom emoji without HTML parsing', async () => {
  const source = await readFile(MESSAGE, 'utf8');

  assert.match(source, /discord-heading/);
  assert.match(source, /discord-inline-code/);
  assert.match(source, /discord-custom-emoji/);
  assert.match(source, /cdn\.discordapp\.com\/emojis/);
  assert.match(source, /document\.createTextNode/);
  assert.doesNotMatch(source, /\.innerHTML|DOMParser|insertAdjacentHTML|createContextualFragment/);
});

test('final review requires explanation and appropriate evidence while allowing outside-Discord evidence', async () => {
  const [review, policy] = await Promise.all([readFile(REVIEW, 'utf8'), readFile(POLICY, 'utf8')]);

  assert.match(review, /Outside-Discord evidence reference/);
  assert.match(review, /Staff explanation/);
  assert.match(review, /DM preview/);
  assert.match(review, /Case readiness/);
  assert.match(policy, /length >= 10/);
  assert.match(policy, /state\.evidence\.size > 0 \|\| workflowExternalEvidenceReady/);
  assert.match(policy, /Verify the required Admin\+ approval/);
});

test('offense choices and recommendations link to public Enthusia rules', async () => {
  const [review, policy] = await Promise.all([readFile(REVIEW, 'utf8'), readFile(POLICY, 'utf8')]);

  assert.match(policy, /https:\/\/enthusia\.info\/rules/);
  assert.match(policy, /#conduct/);
  assert.match(policy, /#mods-clients/);
  assert.match(policy, /#enforcement/);
  assert.match(review, /View applicable rule/);
  assert.match(review, /Open rule/);
});

test('record views distinguish empty and unavailable states and clarify identity counts', async () => {
  const source = await readFile(RECORD, 'utf8');

  assert.match(source, /recordUnavailableOrLoading\('history'\)/);
  assert.match(source, /No moderation history/);
  assert.match(source, /recordUnavailableOrLoading\('cases'\)/);
  assert.match(source, /recordUnavailableOrLoading\('notes'\)/);
  assert.match(source, /capitalize\(label\).*unavailable/);
  assert.match(source, /Accounts .* = 1 Discord identity/);
  assert.match(source, /linked alts.*alternate Minecraft accounts/);
});

test('readability overrides increase metadata sizes and muted contrast', async () => {
  const css = await readFile(CSS, 'utf8');

  assert.match(css, /--m:#b6c0cd/);
  assert.match(css, /\.tiny\{font-size:11px\}/);
  assert.match(css, /\.message-meta span,.message-meta time,.message-id\{font-size:11px/);
  assert.match(css, /\.message-action-item\{[^}]*font-size:12px/);
});
