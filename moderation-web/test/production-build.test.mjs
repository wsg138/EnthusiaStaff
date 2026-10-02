import test from 'node:test';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

test('production deployment rebuilds both API clients for the production origin', async () => {
  const project = fileURLToPath(new URL('..',import.meta.url));
  const config = JSON.parse(await readFile(new URL('../wrangler.production.jsonc',import.meta.url),'utf8'));
  assert.equal(config.build.command,'node scripts/build.mjs --production');
  execFileSync(process.execPath,['scripts/build.mjs','--production'],{cwd:project,stdio:'pipe'});
  for (const name of ['direct-read.js','live-actions.js']) {
    const source = await readFile(new URL('../dist/assets/'+name,import.meta.url),'utf8');
    assert.match(source,/https:\/\/moderation-read\.enthusia\.info/);
    assert.doesNotMatch(source,/moderation-read-staging\.enthusia\.info/);
  }
});
