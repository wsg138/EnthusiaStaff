import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const PAGE = new URL('../../staff-bot/src/main/resources/moderation-preview/index.html', import.meta.url);
const DIRECT = new URL('../../staff-bot/src/main/resources/moderation-preview/direct-read.js', import.meta.url);
const HARDENING = new URL('../../staff-bot/src/main/resources/moderation-preview/live-review-hardening.js', import.meta.url);
const SHELL = new URL('../../staff-bot/src/main/resources/moderation-preview/live-shell-usability.js', import.meta.url);
const FINAL = new URL('../../staff-bot/src/main/resources/moderation-preview/live-record-usability.js', import.meta.url);

test('moderation workspace presents product language on every active final layer', async () => {
  const [html, direct, hardening, shell, finalLayer] = await Promise.all([
    readFile(PAGE, 'utf8'), readFile(DIRECT, 'utf8'), readFile(HARDENING, 'utf8'),
    readFile(SHELL, 'utf8'), readFile(FINAL, 'utf8')
  ]);

  assert.doesNotMatch(html, /STAGING PREVIEW/);
  assert.doesNotMatch(direct, /moderation preview/i);
  assert.doesNotMatch(shell, /Simulated deletions|simulation preview|staging preview/i);
  assert.match(hardening, /Testing note/);
  assert.match(hardening, /Notification message/);
  assert.match(hardening, /Confirm action/);
  assert.match(hardening, /Action review complete/);
  assert.doesNotMatch(hardening, /text:'DM preview'|buttonNode\('Confirm preview'|text:'Action preview complete'|showToast\('Action preview/i);
  assert.match(finalLayer, /Notification message/);
  assert.match(finalLayer, /Confirm action/);
  assert.match(finalLayer, /Action review complete/);
  assert.match(finalLayer, /Review completed\. No changes were sent\./);
  assert.doesNotMatch(finalLayer, /Confirm preview|Action preview|DM preview/i);
});

test('product copy overrides load after the review hardening layer', async () => {
  const [html, finalLayer] = await Promise.all([readFile(PAGE, 'utf8'), readFile(FINAL, 'utf8')]);
  const reviewIndex = html.indexOf('/assets/live-review-hardening.js');
  const finalIndex = html.indexOf('/assets/live-record-usability.js');

  assert.ok(reviewIndex >= 0 && finalIndex > reviewIndex);
  assert.match(finalLayer, /window\.reviewEvidenceNode = productReviewEvidenceNode/);
  assert.match(finalLayer, /window\.reviewFooterNode = productReviewFooterNode/);
  assert.match(finalLayer, /window\.confirmSimulation = productConfirmAction/);
  assert.match(finalLayer, /window\.renderCompleteStep = productRenderCompleteStep/);
});
