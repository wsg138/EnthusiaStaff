# Exact cross-platform message retraction — Worker C isolated safety core

Status: DRAFT / NOT WIRED / ALL ACTIONS DISABLED (2026-10-14). Issue: API #116; counterpart Staff #599. This patch intentionally changes no live runtime registration, config, bot handlers, permissions or chat transports.

## Provenance and existing behavior

- Canonical moderation events and pure follow-up BLOCK candidate logic already exist in the private API (#133 merged, later Worker A context monitor #142 merged). The public StaffBot has a separate staging-only, disabled-by-default post-JDA-send exact Discord receipt and encrypted durable mirror-alias journal/replay from previous merged work. That registration records the actual snowflake and reconciles PENDING to FINAL; do not duplicate its registration/repost work. This PR does not change its 60-minute/128-record retention or 5-second success-gated replay.
- ExactRetractionPlanner is a new, pure Java package. It accepts authenticated upstream current BLOCK and explicit earlier canonical event IDs, complete immutable current/prior origin tuples, receipts from a trusted actual post-send journal, and a configured destination allowlist. All must be supplied by independent authoritative adapters; this planner cannot establish authenticity itself.
- The planner requires exact event ID, sender ID, source platform, server scope, channel profile, channel and conversation match. It permits a nonnegative 0..15-second preceding event only, for public Minecraft/Discord profiles. No nickname/text matching, private/staff/ticket channels, or keyword deletion.
- It verifies the original receipt's full source tuple before selecting an exact destination scope/channel and remote message ID; Discord IDs and destination scope/channel must be valid snowflakes. It deduplicates records for one exact publication using a deterministic SHA-256 operation ID independent of later re-evaluation. IDs are length-prefixed to prevent delimiter ambiguity.
- Signed Paper candidates require the actual signed message handle; absent signature and system/unsigned broadcasts are counted as UNSUPPORTED. A signed handle does not itself prove a compatible RoseChat/Paper path exists.
- DeliveryStatus: CONFIRMED, RETRYABLE, UNSUPPORTED, EXPIRED, FAILED. Even Discord HTTP deletion ACK is not a guarantee all recipients forgot or never saw the message.

## Integration contract (NOT implemented here)

1. API -> StaffBot: independently verified new BLOCK decision with explicit structured related_messages source event IDs (never legacy related_message_ids) and immutable origin tuples. Verify auth and policy/version/age; cases/notifications are Worker B's responsibility.
2. Receipt journal: securely bind canonical source event to each real post-send remote publication (Paper recipients and Discord snowflake), including origin sender/server/profile/channel/conversation. Existing AiMirror journal is for alias registration; it currently persists original text under AES-GCM for that API call and is NOT a complete persistent retraction receipt store. Do not repurpose it without schema, retention, ACL, privacy and restart review.
3. Per-surface durable outbox: persist exact candidate operations with stable keys, encrypted/ACL-protected metadata, max-age/attempt/queue budgets, bounded retry/backoff, crash-before-ack reconciliation, and aggregate-only metrics. Atomically commit local send/outcome and re-check allowed scope/auth each retry. Preserve terminal statuses. A crash after Discord ACK must not incorrectly claim unverified success; 404 semantics need explicit policy (already absent vs wrong target), not automatic CONFIRMED.
4. Real transport: Discord JDA deletion requires actual post-send snowflake, correct guild/channel, permissions and verified bot-authorized scope. Never delete by searching text or nickname. Investigate installed RoseChat/Paper/ProtocolLib exact delivery and signed-message APIs: unsigned/system, modified/decorated and already displayed chat may be unretractable. Return UNSUPPORTED unless exact-client-target deletion is demonstrated. Do not replace official DiscordSRV-dependent addon or activate removal in this PR.
5. End-to-end contract acceptance: real disposable FastAPI+Java loopback with PENDING->FINAL and transient/terminal conflicts, exactly-once receipt registration, dual-surface synthetic callback/restart replay, wrong sender/server/channel and missing-signature refusal, no raw chat/private IDs logged, no punishments, forced-crash/TTL expiry and measured aggregate loss. Then independent security review and owner-approved non-production staging tests.

## Tests in this change

Run Gradle :staff-bot:test --tests net.enthusia.staff.discordbot.retraction.ExactRetractionPlannerTest --offline --no-daemon.

Synthetic cases cover BLOCK/evidence gating, sender/server/channel/profile/conversation/platform isolation, >15s/future replay, private channel refusal, actual post-send allowlist gating, forged receipt ownership, unsupported unsigned/system, malformed Discord IDs, exact operation replay/deduplication and public result types. No remote Discord/Paper/API call is made.

## Unresolved release blockers

- No live Paper/RoseChat publication receipt capture or confirmed signed-message deletion path.
- No trusted API transport to this planner, no independent deletion authorization, no dual-destination receipt ingestion.
- No encrypted durable deletion outbox, per-surface retry/ACK reconciliation or capability negotiation.
- No integrated cross-process crash/recovery or full API+Java dual-retraction test. Existing registration tests do not prove deletion.
- No actual JDA delete acceptance, Linux permissions/keys, retention decision, server deployment or production cutover.

Do not merge or enable automatic chat deletion on the strength of these unit tests alone. Coordinator must review schema/transport and authorize the next claimed integration increment.
