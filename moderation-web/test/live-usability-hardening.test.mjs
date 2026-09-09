import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const SHELL = new URL('../../staff-bot/src/main/resources/moderation-preview/live-shell-usability.js', import.meta.url);
const MESSAGE = new URL('../../staff-bot/src/main/resources/moderation-preview/live-message-usability.js', import.meta.url);
const RECORD = new URL('../../staff-bot/src/main/resources/moderation-preview/live-record-usability.js', import.meta.url);
const REVIEW = new URL('../../staff-bot/src/main/resources/moderation-preview/live-review-hardening.js', import.meta.url);
const POLICY = new URL('../../staff-bot/src/main/resources/moderation-preview/real-policy.js', import.meta.url);
const CSS = new URL('../../staff-bot/src/main/resources/moderation-preview/live.css', import.meta.url);

test('staging UI distinguishes real reads from simulated actions everywhere staff confirms', async () => {
  const [shell, review] = await Promise.all([readFile(SHELL, 'utf8'), readFile(REVIEW, 'utf8')]);

  assert.match(shell, /STAGING · REAL READS \/ SIMULATED ACTIONS/);
  assert.match(shell, /Punishments, DMs, restrictions, and message deletion are previewed only/);
  assert.match(shell, /Simulate punishment/);
  assert.match(review, /Confirm simulation/);
  assert.match(review, /does not punish the player, send a DM, change Discord permissions, or delete messages/);
});

test('message investigation explains partial coverage and supports paging, ranges, clearing, and Discord links', async () => {
  const [shell, message] = await Promise.all([readFile(SHELL, 'utf8'), readFile(MESSAGE, 'utf8')]);

  assert.match(shell, /Filter behavior', 'Loaded messages only/);
  assert.match(shell, /up to 50 target messages from at most 8 readable channels/);
  assert.match(shell, /up to 25 Discord messages at a time/);
  assert.match(shell, /four-page cap per direction/);
  assert.match(shell, /dateFromFilter/);
  assert.match(shell, /dateToFilter/);
  assert.match(shell, /Clear filters/);
  assert.match(shell, /Load older from Discord/);
  assert.match(message, /https:\/\/discord\.com\/channels\//);
});

test('message actions have explicit accessible names and keyboard menu behavior', async () => {
  const source = await readFile(MESSAGE, 'utf8');

  assert.match(source, /aria-label':`Actions for message/);
  assert.match(source, /'aria-haspopup':'menu'/);
  assert.match(source, /role:'menuitem'/);
  assert.match(source, /ArrowDown/);
  assert.match(source, /ArrowUp/);
  assert.match(source, /Escape/);
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

test('readability overrides increase tiny metadata sizes and muted contrast', async () => {
  const css = await readFile(CSS, 'utf8');

  assert.match(css, /--m:#b6c0cd/);
  assert.match(css, /\.tiny\{font-size:11px\}/);
  assert.match(css, /\.message-meta span,.message-meta time,.message-id\{font-size:11px/);
  assert.match(css, /\.message-action-item,.punishment-scope-tab\{font-size:12px\}/);
});
