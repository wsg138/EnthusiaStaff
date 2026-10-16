# AI moderation private case receiver — Worker S acceptance

**Status: INACTIVE, FAKE ONLY.** Draft stacked on Staff #611 at `2e53d54de7ed449a758c391e0be86bab722a3bd9`. Source contract: API draft #151; coordinator owns integration. No registered HTTP route, Discord listener, Paper listener, task runner, API credential, signature verifier, private channel, player GUI, config, deployment, live alert, deletion, or punishment.

## Ownership and trust

Files are isolated to `staff-bot/.../modcase/receiver/**` and this document. Existing StaffBot owns JDA; do not create a second JDA owner. The canonical API alone owns case state/revision, CLAIM/RESOLVE, CAS, idempotent command authority. The receiver never calls local `CaseNotice.apply`, `FileCaseLedger.action`, punishment paths, or message retraction.

`CaseReceiver` is a **test seam** with injected `Peer(verified, origin, scope, guild)` and a matching server-bound receiver. The Boolean is **not authentication**. No real transport may set it merely because a bearer token or HTTP request claims a server/guild. A future adapter needs independently authenticated TLS/mTLS or equivalently verified peer identity, origin and scope allowlists, replay protection, and a trusted, private destination binding. The StaffBot bearer is not cryptographic staff-interaction proof. Authorization for claiming/closing must remain with the independently verified API/Paper actor-proof design.

`CaseEvent.parse` checks only the 12 explicit API v1 fields. It rejects unknown/duplicate fields, noninteger revision/version, malformed UUID/digest, inappropriate revision 0 events, invalid platform/scope, stale/negative version, unexpected status and timestamps, excessive/invalid/duplicate opaque evidence references, chat text and unsupported payloads. The API sends CLOSED; the older Staff `RESOLVED` enum is **not** used as a second authority. Dedicated `CaseAck.toWire()` returns exactly seven snake-case source receipt fields; HTTP 200 without this exact matching structure is never confirmation.

## Atomic journal and restart

`ReceiverJournal` stores exact operation payloads, one case high-water revision, resync fences and **separate** Discord/Paper jobs in an atomically replaced, fsynced synthetic JSON file. Source ACCEPTED only after commit; matching replay DUPLICATE; operation collision, case/scope collision, stale revision, same-revision other operation or reopening CLOSED returns REJECTED. A persistence error gives **no ACK**. Retrying unchanged operation after uncertain response is safe. No distributed exactly-once UI delivery.

If CASE_CREATED was missed or a revision gap occurs, an event is durably accepted but both destinations stay fenced. Only a future independently authenticated full canonical snapshot can clear the fence, when its operation ID, revision, scope, state and full latest payload match the stored current event. A newer unknown revision must be fetched, never guessed. Older events cannot roll back OPEN after CLOSED.

Synthetic destination queues are scoped **before** the bounded poll limit (1–100), prevent other-server starvation, and select only the current case revision. The first send transition persists POSSIBLY_SENT before any external operation; a restart after send before destination ACK fences that job. Exact job+operation+receipt confirmation is per-surface. Only a *verified known-NOT-SENT* result can trigger bounded exponential backoff; an ambiguous outcome never automatically reposts. Expired OPEN jobs are skipped while canonical CLOSED permits private UI cleanup. No automatic JDA send is implemented.

## Limits and integration gates

- Journal bounded to 4096 operations, 8192 jobs and 3 MB. No compaction, production DB transaction, multi-process lease/file lock, active-active polling, or directory fsync guarantee. **Never activate in production or run two receiver processes.**
- Injected fake peer and snapshot are not cryptographic assertions. There is no actual signed event receiver, sender identity verifier, real snapshot fetch, TLS peer, guild provisioning, or revocation flow.
- Existing JDA delivery must prove a truly private channel and authorized staff membership; Paper must recheck real LuckPerms role, staff duty, scheduler context. No actor IDs or raw chat enter this receiver. No live click or private alert approved.
- Missing end-to-end stalled-peer deadline, bounded network transport, signed event ACK, source multiworker lease and cross-process acceptance. Real staged API→StaffBot→Paper/JDA acceptance is required. API #151 global polling can starve local scopes; verify scoped poll separately.
- Hosted CI, coverage, Codacy zero new valid issues and coordinator prerequisite merge order remain release gates. **Do not merge or deploy this draft.**

## Synthetic acceptance checklist

Rev0 birth, rev1 CLAIMED, rev2 CLOSED; replay/conflicts/stale revision; missed CASE_CREATED and authenticated resync; wrong guild/origin/scope/unverified peer; strict JSON/evidence minimization; restart between accept, send and ACK; independent destination receipts; scoped limited polling; expiry/backoff; no live side effects.

Local checks: `:domain:check :persistence:check :paper:check :staff-bot:check --offline --no-daemon`. Existing Linux-only and actual-API tests may be SKIPPED on Windows; report accurately.
