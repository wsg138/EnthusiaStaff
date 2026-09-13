import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const BROWSE = new URL('../../staff-bot/src/main/resources/moderation-preview/live-browse-workspace.js', import.meta.url);

test('reply rows render an actual compact preview and jump to the referenced message', async () => {
  const source = await readFile(BROWSE, 'utf8');

  assert.ok(source.includes('message.replyPreview'));
  assert.ok(source.includes("reference.dataset.replyJump = message.replyTo"));
  assert.ok(source.includes("className:'reply-preview-author'"));
  assert.ok(source.includes("className:'reply-preview-text'"));
  assert.ok(source.includes('scrollToBrowseMessage(messageId)'));
  assert.ok(source.includes('fetchBrowseContextAround(channelId,messageId)'));
  assert.ok(source.includes('showContextWorkspace(trigger,context,rememberMessageView())'));
});

test('channel browse selects a player only from their name or avatar double-click target', async () => {
  const source = await readFile(BROWSE, 'utf8');

  assert.ok(source.includes("row.querySelector('.message-avatar')"));
  assert.ok(source.includes("row.querySelector('.message-author-name')"));
  assert.ok(source.includes('target.dataset.doubleSelectPlayer = message.authorId'));
  assert.ok(source.includes("target.addEventListener('dblclick',handleBrowseAuthorDoubleClick)"));
  assert.ok(source.includes('if (liveModeration.targetSelected) return'));
  assert.ok(source.includes('await selectBrowsePlayer(userId)'));
});

test('reply preview data is captured from the same read response instead of extra browser requests', async () => {
  const source = await readFile(BROWSE, 'utf8');

  assert.ok(source.includes('const payload = await browseReplyBaseReadJsonResponse(response)'));
  assert.ok(source.includes('rememberBrowseReplyPayload(payload)'));
  assert.ok(source.includes('browseReplyPreviews.set(message.id'));
  assert.ok(!source.includes('fetchReplyPreview'));
});
