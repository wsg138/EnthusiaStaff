# Signed API-to-Staff case ingress — offline prototype

**INACTIVE ONLY.** SignedCaseIngress is not registered with HTTP, JDA or Paper. It verifies a caller-supplied envelope with a dedicated per-destination service secret; only then does it create the test-only CaseReceiver.Peer with verified=true. It does not authenticate human staff, prove Discord/Paper delivery or activate AI moderation.

## Signed v1 transcript

HMAC-SHA256 uses the UTF-8 encoding of these eight newline-separated components, with no trailing newline:

1. Literal enthusia.case.ingress.v1
2. Literal POST
3. Literal /v1/ai-case-events
4. Exact configured canonical origin: minecraft or discord
5. Exact configured server scope, such as smp
6. Exact private destination guild ID, decimal
7. Unix epoch issue time, decimal seconds
8. Lowercase hex SHA-256 digest of the exact UTF-8 event-body bytes

The optional receiveRaw entry point first checks the exact POST method and fixed route, rejects raw request bodies over 8192 bytes, and uses a strict UTF-8 decoder that rejects malformed sequences rather than silently replacing them. This remains an unregistered API, not a live HTTP endpoint.

The signature is lowercase hex of the 32-byte HMAC output. The verifier uses a constant-time byte comparison, at least 32-byte per-destination secret, immutable receiver origin/scope/guild binding, 30-second absolute clock window and 8192-byte event cap before calling strict canonical CaseEvent.parse and the journal. An invalid proof receives no ACK. A matching authenticated retry with the same canonical event returns DUPLICATE, never a second case.

Unit tests use a synthetic event from the real offline Python API projector and an independently generated deterministic Python hashlib/hmac golden signature. Never reuse that fixture key outside tests.

## Production gates — NOT satisfied

- Implement a real Python API signer for the exact immutable transcript and rotate independently provisioned keys. The API currently has only an injected fake event transport. Other code can still instantiate an in-process Peer(verified=true); the eventual listener must enforce one authenticated ingress path only.
- Implement bounded HTTPS/mTLS or equivalent authenticated networking, reject ambiguous character encoding and oversized HTTP requests at the network boundary, and bind the method/path and peer identity before translating the payload to a Java string. Never trust guild/origin/scope from unverified headers.
- Secure key provisioning outside source/versioned YAML; add rotation, revocation, service monitoring, time synchronization and staged end-to-end negative tests. An HMAC proves a service secret, NOT a human staff actor.
- Replace single-process synthetic ReceiverJournal with production transactional storage/leases and secure cross-process recovery. Independently verify exact Discord/Paper publication receipts. A caller-supplied text receipt is not proof of delivery; never auto-retry uncertain sends.
- Finish authenticated CAS staff actions, staff-only channel/privacy authorization, Linux staging without real chat, model acceptance and explicit owner approval before any live moderation.
