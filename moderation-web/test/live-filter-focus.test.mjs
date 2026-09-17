import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const SCRIPT = new URL('../../staff-bot/src/main/resources/moderation-preview/live-filter-focus.js', import.meta.url);
const INDEX = new URL('../../staff-bot/src/main/resources/moderation-preview/index.html', import.meta.url);
const BUILD = new URL('../scripts/build.mjs', import.meta.url);

test('message text filters restore focus and caret after workspace rerenders', async () => {
  const source = await readFile(SCRIPT, 'utf8');

  assert.ok(source.includes("new Set(['messageSearch', 'authorFilter'])"));
  assert.ok(source.includes('queueMicrotask(() => restoreFilterFocus(target.id, selection))'));
  assert.ok(source.includes("replacement.focus({preventScroll:true})"));
  assert.ok(source.includes('replacement.setSelectionRange(selection.start, selection.end'));
  assert.ok(source.includes("document.addEventListener('input', preserveFilterFocus, true)"));
});

test('filter focus helper is the final workspace behavior layer and is copied by the web build', async () => {
  const [index, build] = await Promise.all([readFile(INDEX, 'utf8'), readFile(BUILD, 'utf8')]);
  const browse = index.indexOf('/assets/live-browse-workspace.js');
  const focus = index.indexOf('/assets/live-filter-focus.js');

  assert.ok(browse >= 0);
  assert.ok(focus > browse);
  assert.ok(build.includes("'live-filter-focus.js'"));
});
