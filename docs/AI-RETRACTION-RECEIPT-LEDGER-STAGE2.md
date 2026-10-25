# Exact receipt/retraction Stage 2 — fake-only journal and safety gates

**Status (2026-10-15): DRAFT, OFF, SYNTHETIC. Do not deploy, merge as a live feature, or treat any test as proof of physical deletion.**
Worker 03; API [#116](https://github.com/wsg138/AI-Moderation-API/issues/116); Staff [#599](https://github.com/wsg138/EnthusiaStaff/issues/599).
Depends on merged Staff [#607](https://github.com/wsg138/EnthusiaStaff/pull/607) (including #608 operation-ID hardening).
This increment exclusively adds `retractionstage2/` and this document. Existing `AiMirror*` registration/recovery, `retraction/` planner, `modcase/`, bootstraps, YAML, RoseChat, Paper, JDA and API are untouched.

## Proof chain and boundaries

The existing `ExactRetractionPlanner` only selects a proposed exact operation when an independently authorized current BLOCK explicitly links an older event in the **same public source** (sender, server, channel, profile, conversation and <=15 seconds). Its SHA-256 v2 operation ID includes the canonical source tuple, actual target handle and Paper signature. It does not prove publication or call deletion.

Stage 2 is deliberately a fake-port **executable design probe**, not an authenticated network service. Its `ReceiptRecord` holds immutable canonical *current/original* source tuples, case ID and pinned revision, the previous planner's exact `Target`, the provided post-send `PublicationReceipt`, the independently supplied publication timestamp and receipt timestamp. Intake recomputes the plan from those values and rejects mismatched event/sender/server/channel/profile/conversation/unsupported output, missing actual-post-send flag, invalid snowflake or signature, and impossible or expired timestamps. No text, named accounts, message body, JWT or API credential is stored.

**Important:** The `actualPostSendReceipt` flag and the timestamps in this synthetic API are supplied by the fake caller. They are NOT cryptographic authenticity evidence or actual callback integration. No untrusted model output or raw HTTP request may instantiate these records in production. An approved ingress adapter will need to verify origin, current authenticated case/revision, signed callback provenance, source FINAL state and destination allowlist **before** Stage 2 intake, independently of the caller's assertions.

`FakeRetractionEngine` must be explicitly constructed with `enabled=true` and two injected `FakeTransport` implementations; no default running instance or live implementation is registered. It asks a separate `CaseAuthority` for a **fresh** `Authorization` on every due dispatch/reconciliation. Status changes to UNSUPPORTED without a current open BLOCK at the pinned case revision, matching current/previous events and operation ID, independently verified sender/ownership/destination, deletion permission and signed-Paper capability. The fake authority's booleans do not constitute genuine permission checks: they identify mandatory duties of a future authority implementation. No sanctions are involved.

## State machine

| State | Meaning | Follow-up |
| --- | --- | --- |
| RETRYABLE / 0 attempts | Persisted exact target; no fake operation called | Reauthorize, then proceed |
| RETRYABLE / in-flight or UNKNOWN ACK | Intent was encrypted and fsynced **before** fake dispatch; after a crash, transport timeout, absent ACK, or explicit UNKNOWN the effect may already exist | Reauthorize, run **fake reconciliation only**, never blindly send twice |
| RETRYABLE / proven no effect | Fake transport **positively proves no destructive effect occurred** (`PROVEN_NO_EFFECT`); timeout or missing ACK is never this result | Retry only after 5 seconds and fresh authorization, max 3 sends |
| CONFIRMED | Injected fake transport reported an exact confirmed acknowledgement or a matching reconciliation proof | Terminal; this is **not proof any real client forgot content** |
| UNSUPPORTED | Lacks current authority, publication capability, owner/guild permission, signed Paper support, etc. | Terminal; do not guess an alternate target |
| EXPIRED | 60-minute cap measured from original source/publication, not renewed by a late record | Terminal; no retransmission |
| FAILED | Exhausted 3 clear retry attempts or transport declared a permanent fake failure | Terminal, requires operator investigation |

`FakeTransport.deleteSynthetic` now returns the separate `SendResult` enum: `CONFIRMED`, `PROVEN_NO_EFFECT`, `UNKNOWN`, `UNSUPPORTED`, or `FAILED`. A plain `DeliveryStatus.RETRYABLE` reply cannot distinguish retry-safe no-effect from ACK loss, so it is **not accepted as a send result**. A returned UNKNOWN durably retains `uncertain=true` just like a thrown exception; subsequent runs only use `reconcileSynthetic`. The read-only fake reconciliation returns `RETRYABLE` if no exact confirmation exists, preserving uncertainty until expiry. A real transport may claim `PROVEN_NO_EFFECT` only from independently proven pre-effect refusal (not 404/timeout/"not found"). New synthetic tests cover UNKNOWN after committed fake effect, UNKNOWN without confirmed effect, null result, and restart/reconciliation without a second delete.\n\nEach entry is idempotently keyed by the planner's source-bound v2 operation ID; repeats never resurrect terminal records. Maximum 64 total entries, including terminal ones. No silent eviction or missing-coverage success claim. A fake transport exception after durable intent leaves an ambiguous in-flight row, so reopening with the same key cannot produce a second blind fake delete; only the exact reconciliation probe may confirm it. If no exact probe exists, the operation remains uncertain until expiry. Deletion 404, Discord target absent, generic transport timeout, or unverified JDA success must **never** be mapped to CONFIRMED by a future adapter.

## Storage, security and limits

`EncryptedReceiptJournal` uses a single versioned snapshot encrypted with **AES-256-GCM**, random 96-bit nonce, authenticated file version, external injected AES key, maximum 256 KiB ciphertext and length-bounded fields. Every mutation creates a new temporary encrypted snapshot, fsyncs its bytes, and atomically renames into place. Corrupt records, wrong keys, unsupported versions, oversized records, duplicate operation IDs and invalid planner replays fail closed at restart. POSIX temporary-file permissions are owner read/write. The key is never saved and there are no raw messages, player chat or identifier-bearing logs in this feature.

**Unresolved storage gates:** This is a single-process reference journal, not distributed or safe for concurrent independent writers. Windows ACL inheritance/key-open security, directory-entry fsync portability, hard-exit/power-cut atomicity and restart on an actual target OS remain unverified. Operator key rotation, audit retention/erasure, secure directory ownership, telemetry/health surfaces, multi-process CAS, crash before first persisted intake and corruption quarantine need a separate approved adapter/review. An invalid journal currently refuses startup rather than silently discarding evidence or retrying deletion. Do not use this class on production volumes.

## Platform support matrix

| Publication | Synthetic planner / fake dispatch | Actual safe removal now | Reason |
| --- | --- | --- | --- |
| Public Minecraft -> Discord bridge (post-JDA-send snowflake) | Eligible only with complete exact scope, current BLOCK and matching authorization | **NOT ENABLED** | Existing `DiscordChatPublication` records a real JDA completion snowflake for alias registration. Stage 2 does not consume that live callback, verify bot ownership/perms, or call JDA deletion. |
| Native public Discord message | Planner may accept a properly scoped receipt | **NOT ENABLED** | No authenticated native post-send bridge/ownership proof or exact JDA delete adapter is integrated. |
| Public Paper signed chat | Only fake receipt with exact signature and explicit capability | **UNSUPPORTED** | Paper SignedMessage / Audience.deleteMessage API exists, but no proven per-recipient RoseChat output signature handle or verified installed transport path. |
| Paper system/unsigned/decorated chat | UNSUPPORTED | **UNSUPPORTED** | No trustworthy exact signed handle; cannot infer one from chat text or sender. |
| Discord private/ticket/staff/exempt or Minecraft private message | Rejected by the existing source planner | **UNSUPPORTED** | Policy v1 scopes/exemptions; cannot elevate private content into a public deletion target. |
| Cross-guild/server/channel/sender or guessed receipt | Rejected/UNSUPPORTED | **UNSUPPORTED** | Independently scoped proof/permission and actual post-send receipt are mandatory. |

Paper's official developer documentation is https://docs.papermc.io/paper/dev/component-api/signed-messages/ . Relevant future API: `AsyncChatEvent.signedMessage()` and `Audience.deleteMessage(SignedMessage)` / `Audience.deleteMessage(SignedMessage.Signature)`, with verified recipient-side treatment. A signed message or deletion packet does not undo prior client visibility. RoseChat's customized routing, ProtocolLib and license constraints must be reviewed before integration. No unreviewed packet spoofing. Discord future adapter needs authenticated JDA event, correct guild/channel/owned message snowflake, channel permissions and verified remote delete ACK semantics, including explicit 404/unknown outcomes.

## Synthetic acceptance and actual gates

`FakeRetractionEngineTest` covers default disabled, encrypted restart and duplicate operation, blocked/current revision reauthorization, closed/changed case, wrong scope/permission/ownership, signature capability, exact Paper fake, Discord fake, late publication after canonical source, absent post-send, expired source age, 3-attempt retry, too-early backoff, crash-after-fake-commit probe, uncertain replay without duplicate delete, corruption/wrong key, capacity=64 and five terminal/result statuses. Tests are deliberately **fake only**; they send no Discord/Paper message and create no punishment.

Required before a real integration PR: choose a claimed non-overlapping ingress/transport namespace; document and verify actual publication callback provenance, Paper signed recipient message API, target-guild delete permission, trusted source-final and BLOCK revision checks; implement case ownership authorization and true encrypted file/DB lifecycle with single writer/CAS; expose aggregate coverage/loss counters; run separate-process kill/restart, timeout-after-real-ACK idempotency, genuine API->StaffBot Java loopback with PENDING->FINAL and two fake surfaces; review hosted Codacy findings and exact-head CI; independently security-test target OS ACL/keys, privacy, retention and rollback. Obtain explicit owner staging approval before any live JDA/Paper deletion experiments. No production activation, server action, private data, player ingestion or chat classification was authorized for this step.
