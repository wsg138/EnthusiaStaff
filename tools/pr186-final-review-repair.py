from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise SystemExit(f"expected one match in {path}: {old!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


def repair_workflows() -> None:
    deploy = ".github/workflows/moderation-web-staging-deploy.yml"
    replace_once(
        deploy,
        "      - name: Check out exact commit\n        uses: actions/checkout@v6\n",
        "      - name: Check out exact commit\n        uses: actions/checkout@v6\n        with:\n          persist-credentials: false\n",
    )
    replace_once(
        deploy,
        "          npm install --no-package-lock --ignore-scripts\n",
        "          npm ci --ignore-scripts\n",
    )

    validation = ".github/workflows/moderation-web-validation.yml"
    replace_once(
        validation,
        "        with:\n          ref: ${{ github.event_name == 'pull_request' && github.event.pull_request.head.sha || github.sha }}\n",
        "        with:\n          persist-credentials: false\n          ref: ${{ github.event_name == 'pull_request' && github.event.pull_request.head.sha || github.sha }}\n",
    )
    replace_once(
        validation,
        "          npm install --no-package-lock --ignore-scripts\n",
        "          npm ci --ignore-scripts\n",
    )


def repair_request_body() -> None:
    index = "moderation-web/src/index.js"
    replace_once(
        index,
        "import { inspectLaunchToken } from './security.js';\n",
        "import { inspectLaunchToken } from './security.js';\nimport { readBoundedBody } from './request-body.js';\n",
    )
    replace_once(
        index,
        """async function readSimulationPayload(request) {
  if (declaredRequestTooLarge(request)) return { error: textResponse('Preview request is too large.', 413) };
  const body = new Uint8Array(await request.arrayBuffer());
  if (body.byteLength > MAX_REQUEST_BYTES) return { error: textResponse('Preview request is too large.', 413) };
  return parseSimulationJson(body);
}

function declaredRequestTooLarge(request) {
  const declaredLength = Number(request.headers.get('Content-Length') || '0');
  if (!Number.isFinite(declaredLength)) return false;
  return declaredLength > MAX_REQUEST_BYTES;
}
""",
        """async function readSimulationPayload(request) {
  const body = await readBoundedBody(request, MAX_REQUEST_BYTES);
  if (!body) return { error: textResponse('Preview request is too large.', 413) };
  return parseSimulationJson(body);
}
""",
    )

    Path("moderation-web/src/request-body.js").write_text(
        """export async function readBoundedBody(request, maxBytes) {
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
        await reader.cancel();
        return null;
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
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
""",
        encoding="utf-8",
    )

    Path("moderation-web/test/request-body.test.mjs").write_text(
        """import test from 'node:test';
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
""",
        encoding="utf-8",
    )


def repair_ipv6_origin() -> None:
    config = "staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationPreviewWebConfig.java"
    replace_once(
        config,
        """    private static boolean loopbackHost(String host) {
        return LOCALHOST.equalsIgnoreCase(host) || IPV4_LOOPBACK_HOST.equals(host) || IPV6_LOOPBACK_HOST.equals(host);
    }
""",
        """    private static boolean loopbackHost(String host) {
        String normalized = unbracketedHost(host);
        return LOCALHOST.equalsIgnoreCase(normalized)
                || IPV4_LOOPBACK_HOST.equals(normalized)
                || IPV6_LOOPBACK_HOST.equals(normalized);
    }

    private static String unbracketedHost(String host) {
        if (host != null && host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }
""",
    )

    test_file = "staff-bot/src/test/java/net/enthusia/staff/discordbot/ModerationPreviewWebConfigTest.java"
    marker = "    @Test\n    void explicitBindPortIsRestrictedToDocumentedStagingPort() {\n"
    addition = """    @Test
    void localIpv6PublicDevelopmentOriginIsAccepted() {
        ModerationPreviewWebConfig config = ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "[::1]:8766",
                ModerationPreviewWebConfig.PUBLIC_URL_ENV, "http://[::1]:8766"));

        assertEquals("http://[::1]:8766", config.publicBaseUri().orElseThrow().toString());
        assertFalse(config.hostedExternally());
        assertFalse(config.secureCookie());
    }

"""
    replace_once(test_file, marker, addition + marker)


def main() -> None:
    repair_workflows()
    repair_request_body()
    repair_ipv6_origin()


if __name__ == "__main__":
    main()
