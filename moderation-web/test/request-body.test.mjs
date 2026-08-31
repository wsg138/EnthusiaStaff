import test from 'node:test';
import assert from 'node:assert/strict';
import { readBoundedBody } from '../src/request-body.js';

function streamedRequest(chunks, headers = {}) {
  const stream = new ReadableStream({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(new Uint8Array(chunk));
      controller.close();
    }
  });
  return new Request('https://staff-staging.enthusia.info/api/simulate', {
    method: 'POST',
    headers,
    body: stream,
    duplex: 'half'
  });
}

test('reads a streamed request only when it stays within the byte limit', async () => {
  const body = await readBoundedBody(streamedRequest([[1, 2], [3, 4]]), 4);
  assert.deepEqual([...body], [1, 2, 3, 4]);
});

test('rejects an oversized stream without requiring Content-Length', async () => {
  const body = await readBoundedBody(streamedRequest([[1, 2, 3], [4, 5, 6]]), 5);
  assert.equal(body, null);
});

test('rejects an oversized declared request before reading its body', async () => {
  const request = streamedRequest([[1]], { 'Content-Length': '6' });
  assert.equal(await readBoundedBody(request, 5), null);
});
