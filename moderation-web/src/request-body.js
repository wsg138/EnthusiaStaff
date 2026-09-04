export async function readBoundedBody(request, maxBytes) {
  if (declaredTooLarge(request, maxBytes)) return null;
  if (!request.body) return new Uint8Array();
  const reader = request.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) return combine(chunks, total);
      total += value.byteLength;
      if (total > maxBytes) {
        await cancelQuietly(reader);
        return null;
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
}

async function cancelQuietly(reader) {
  try {
    await reader.cancel();
  } catch {
    // Cancellation is best-effort; the caller still needs to reject the body.
  }
}

function declaredTooLarge(request, maxBytes) {
  const raw = request.headers.get('Content-Length');
  if (raw === null || raw.trim() === '') return false;
  const declared = Number(raw);
  return Number.isFinite(declared) && declared > maxBytes;
}

function combine(chunks, total) {
  const body = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return body;
}
