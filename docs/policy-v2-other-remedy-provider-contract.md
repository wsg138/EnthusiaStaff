# Policy v2 — unsupported OTHER remedy provider and verification contract

Tracking: [#462](https://github.com/wsg138/EnthusiaStaff/issues/462), [#446](https://github.com/wsg138/EnthusiaStaff/issues/446), [#355](https://github.com/wsg138/EnthusiaStaff/issues/355)

Status: **design proposal only**, 2026-10-08. This document is not an approved policy snapshot, provider implementation, runtime activation, or authorization to deploy. Policy v1 remains authoritative; Policy v2 remains disabled by default. Do not edit or reinterpret published snapshot `owner.2026-10-07.1` or the open #455 `.2` proposal.

## Verified source boundaries

- [Remedy binding coverage audit](policy-v2-remedy-binding-audit-2026-10-07.md): open [PR #455](https://github.com/wsg138/EnthusiaStaff/pull/455) proposes 199 rule/remedy occurrences, 142 typed bindings, and **57 deliberately unsupported OTHER occurrences** across six IDs. These are *proposed PR counts*, not a claim about deployed configuration.
- `PolicyV2EnforcementPolicy` currently allows `CORRECT_PROFILE`, `ACCESS_RESTRICTION`, `REMOVE_CONTENT` and `CONFISCATE` with narrow scope/condition combinations; it rejects `RemedySpec.Type.OTHER`. `PolicyV2RemedyEnforcement.Scope` has NETWORK_ACCESS, REPORT_SUBMISSION, MARKET_ACCESS, REPUTATION_ACCESS, CONTENT and ASSET; condition types are USERNAME, PROFILE_COMPONENT, VPN_APPROVAL and MANUAL.
- Merged [#452](https://github.com/wsg138/EnthusiaStaff/pull/452) pins optional typed binding metadata to `RemedySpec` and derives it from the saved finding rather than description text. Merged [#457](https://github.com/wsg138/EnthusiaStaff/pull/457) gives `PolicyV2RemedyService.registerConfigured` the canonical case subject and rejects subject mismatches at the legacy registration path. Do not bypass these fences.
- `PolicyV2RemedyService` already owns registration, ENFORCED/SATISFIED/WAIVED transitions, operation-key fencing, two-phase canonical/projection completion recovery, and the authorization hooks `ENFORCE_POLICY_REMEDY`, `SATISFY_POLICY_REMEDY`, `WAIVE_POLICY_REMEDY`. Scoped checks protect Market and Reputation restriction mutation and asset restoration. Extending OTHER must reuse this service, **not** create a second lifecycle.
- `PolicyV2RemedyActions` supplies deterministic idempotent IDs for known content/restriction gateways, but no generic OTHER gateway.
- The [EnthusiaMarket moderation API v1](https://github.com/wsg138/EnthusiaMarket/blob/main/src/main/java/net/enthusia/market/api/moderation/MarketModerationApi.java) exposes stall lookup, prepare/confiscate/restore/release, operation lookup, and blacklist APIs. **It does not expose an arbitrary stall deletion/repair or one-stall cleanup method.** EnthusiaStaff `MarketIntegration` currently exposes only read-only `status(UUID)` and availability, not a mutation adapter. Existing Market operation statuses include PREPARED, REPLAYED, HELD, RESTORED, RELEASED, REJECTED, CONFLICT and QUARANTINED; an acknowledged *request* is not proof of target cleanup.
- The [EnthusiaCommend API v2](https://github.com/wsg138/EnthusiaCommend/blob/main/src/main/java/org/enthusia/rep/api/ReputationModerationApi.java) exposes reputation entry snapshots and **blacklist** apply/remove APIs, but no API to reverse or delete particular invalid reputation entries. Existing `ReputationIntegration.remove` removes a blacklist, not fraudulent reputation.
- This audit found no typed Paper/WorldGuard cleanup or protected-area rollback adapter in the current Policy v2 enforcement boundary. Do not infer that arbitrary world restoration is available because the server uses world-protection plugins.

## Provider decision matrix — all 57 proposed OTHER occurrences

Every entry below is currently **unsupported at Policy v2 authority cutover**. Decisions specify the intended direction, *not* existing behavior or approved operations.

| Remedy ID (count in #455) | Canonical target / proposed operation | Actor authority and evidence | Acceptance / failure mode | Decision |
| --- | --- | --- | --- | --- |
| `remove-disruption` (12) | Saved case subject UUID; separately identified affected machine, entity, location or server resource. Proposed `TECHNICAL_DISRUPTION_CLEANUP`. | Authorized technical/Admin operator; capture object/resource identity and private before/after evidence. No blanket world-write powers for ordinary Mods. | Server/provider postcondition or independent bounded staff verification of completed cleanup. An opened menu, kick, planned task or command sent is insufficient. Provider absent -> REQUIRED / review. | **B/C**: typed Paper/provider action only where a safe postcondition exists; otherwise restricted, evidence-backed manual verification. |
| `safety-containment` (19) | Saved case subject UUID and separately scoped safety object/capability, not arbitrary player-target mutation. Proposed `SAFETY_CONTAINMENT`. | Admin/Founder authorization with private evidence access; emergency action, if already independently authorized, must remain outside this generic remedy permission. | Explicit containment state / revocation evidence with operator, reason, expiry/review trigger; never mark complete merely for preserving evidence. Unavailable -> REVIEW, no automatic sanction. | **B/C**: high-privilege verified containment workflow; no generic provider mutation until precise controls are approved. |
| `restore-protected-area` (5) | Verified region ID/world and affected change set, with saved offending subject UUID. Proposed `PROTECTED_AREA_REPAIR`. | Existing restoration authority boundary (Founder for asset restoration); require independent area authorization/approval before any world rollback. | Immutable provider rollback/repair receipt, world/region checksum or authorized human verification after completion. Missing safe rollback integration -> REQUIRED/review. | **C then A/B**: no proven WorldGuard restoration API path; design scoped rollback/repair bridge separately. |
| `market-cleanup` (1) | Saved subject UUID, typed Market stall/asset ID and violation-specific cleanup selector. Proposed `MARKET_VIOLATION_CLEANUP`. | `MODIFY_MARKET_RESTRICTION` plus remedy lifecycle authority; Market remains source of stall ownership/state. | Market-owned committed mutation receipt + fresh authoritative status/operation query; no direct Staff SQL. Provider missing, stale ownership or failure -> REQUIRED. | **C until provider API**: existing v1 prepare/confiscate/restore are not equivalent to arbitrary cleanup. |
| `remove-extra-stall` (5) | Subject UUID, specific offending stall ID and expected owner, not just a count. Proposed `MARKET_EXTRA_STALL_REMOVAL`. | Market mutation authority; preserve lawful stall(s), inventory and ownership; staff cannot choose unspecified stall implicitly. | New idempotent Market-owned remove/transfer/close operation with durable receipt and verified one-stall invariant; user presence not required. | **C until provider API**: `findStalls` alone is read-only; no proven delete operation. |
| `remove-invalid-reputation` (15) | Target subject UUID plus exact giver/target/category/entry key(s) and expected reputation snapshot checksum. Proposed `REPUTATION_INVALID_ENTRY_REPAIR`. | `MODIFY_REPUTATION_RESTRICTION` and remedy lifecycle authority; private finding/evidence proving which entries are invalid. | New idempotent Commend-owned corrective API returning changed entry IDs and post-state checksum; snapshot score/entry verification. Removing a blacklist is not completion. | **C until provider API**: no targeted entry mutation in `ReputationModerationApi` v2. |

A = narrowly typed provider-owned completion; B = independently evidenced and authorized staff confirmation; C = unsupported / explicit review until safe contract exists. Where an ID mixes contexts, choose A or B *per incident with pinned, typed attributes*; an unspecified selector must fall back to C, never an inferred operation.

## Proposed shared boundary (not yet a Java API)

An additive **versioned** provider-binding subtype should carry:

- `remedy-id` + stable `operation-type` (one exact allowlisted ID above), rather than arbitrary free-form OTHER text;
- `target-kind`, target reference attribute ID(s), optional expected provider ownership/revision/checksum attribute ID(s), and where necessary an evidence-set reference;
- `minimum-capability` or fixed code-owned authorization check *in addition* to normal remedy lifecycle permission, active-duty authority, and the case's canonical subject;
- `provider-id` / expected API version, acknowledgement schema version, and reconciliation policy;
- no client-controlled operation ID, raw SQL, unsanitized provider payload, or fallback to generic `MANUAL` for unsupported operations.

The validator should reject unknown operation IDs; unknown/missing provider IDs; wrong scopes; incomplete identifying attributes; conflicting provider/operation combinations; and bindings that would let normal Mods perform Admin/Founder actions. Do not overload current `ConditionType.MANUAL` to mean that a provider action succeeded. To preserve the existing known-safe bindings, add a narrow typed operation variant/adapter only after owner and provider contract review. Retain `owner.2026-10-07.1` and proposed `.2` byte-semantics; publish a new `owner.2026-10-07.3` **only after** the schema, provider readiness, and policy approval are established.

### Suggested gateway result contract

This is a **design sketch**, not a currently implemented method:

```text
request: pinned caseId, remedyId, canonical subjectId, typed operationId,
         pinned target reference(s), expected provider revision/checksum,
         stable W3B providerOperationUuid, verified actor/capability
response: typed status {COMMITTED, ALREADY_COMMITTED, PENDING, CONFLICT,
                        REJECTED, UNAVAILABLE}, provider receipt ID,
          provider revision/checksum, exact changed resource IDs, occurredAt
```

A `COMMITTED` response must mean **durably applied** to the provider's authoritative state and be independently queryable using the same operation ID. `ALREADY_COMMITTED` is acceptable only if the provider returns the identical original operation receipt and target. `PENDING` / `PREPARED` / dispatch-success / stale-cache status must **not** advance to SATISFIED. For multi-step operations, persist a durable pending state and reconcile it; never generate a fresh provider UUID on retry. If an acknowledgement is ambiguous, recover using `findOperation(operationId)` or equivalent before sending another mutation.

A verified **manual completion** must have a separate explicit staff assertion contract: permitted capability, case/revision, precise target, timestamp, verified evidence pointer and bounded factual note. It must be durably auditable and independently reviewable; no self-approval where policy requires higher authority. A manual assertion records what was observed, **not** proof that an unsupported remote provider mutated. Public projections reveal only sanitized outcome labels; never expose private evidence links, coordinates, IPs, internal checksums, staff notes, or provider diagnostics.

### Manual completion: separate verified-assertion journal (proposed, not implemented)

**Do not call existing `PolicyV2RemedyService.satisfy(...)` directly for an unsupported `OTHER` remedy.** That method has no evidence-record requirement. Do not pretend `satisfyWithCleanup(...)` is an evidence persistence primitive either: its `CompletionAction` is an idempotent **cleanup effect** performed before canonical completion and may require additional scoped cleanup authority. Neither method currently guarantees a durable, independently reviewed human assertion.

For a *future* owner-approved B-path remedy, introduce a narrow `PolicyV2RemedyService.satisfyWithVerifiedManualAssertion(...)` orchestration method (illustrative name; **not an existing API**) that uses the existing canonical-to-W3B lifecycle sequence, with one new private evidence journal. Its inputs must be pinned to the persisted case ID, remedy ID, canonical subject UUID, effective-finding/case revision, exact affected target, a stable operation key, authorized verifying actor and (where required) a distinct approving actor, private evidence-set reference, observation time, bounded factual note and explicit scope. It must reject unsupported remedy/scope or missing approved manual-verification binding; ordinary `ConditionType.MANUAL` is **not** a substitute.

**Durable order and restart replay:**

1. Re-read the effective case, remedy and W3B record, verify no overturned/stale finding, exact subject/target and expected revisions, active-duty lifecycle **and** remedy-specific authority, and any required second verifier. Validate that evidence was actually reviewed and that this is observed **completed work**, not a pending task or claim of provider mutation.
2. Before transitioning any remedy, commit an immutable private `VERIFIED_PENDING_LINK` assertion in the Staff-owned journal. Key by `(caseId, remedyId, operationKey)` with a uniqueness constraint; include the expected case/enforcement revision, target/evidence fingerprints, verifier(s), time, and encrypted/restricted evidence pointer. A retry with a changed actor/target/evidence/revision for the same key is a conflict, never a new assertion. Keep private details out of public projections.
3. In the same database transaction **where supported**, fence the persisted effective case revision and update the canonical W2 remedy to SATISFIED only if that exact verified assertion still applies. If journal and canonical changes cannot share a transaction, commit the assertion **first**, then replay from the journal with the *same* operation key and a fresh serialized case revision check; an incomplete assertion alone is never successful completion.
4. Transition the existing W3B enforcement record to SATISFIED using the canonical-to-projection recovery logic; replays do not write duplicate assertions or duplicate terminal actions. Mark the private assertion `LINKED` only **after** observing canonical and W3B as SATISFIED for the same operation. No provider write or `CompletionAction` occurs on this manual path.
5. Recovery must scan `VERIFIED_PENDING_LINK` and partially linked records: before canonical completion, recheck the pinned case/finding/authority and either safely retry or mark **STALE/REVIEW_REQUIRED** without satisfying; after canonical completion but before W3B completion, replay the existing projection transition; after both are satisfied, finalize the journal link idempotently. If an overturn/reclassification races a pending assertion, a case-revision/row lock must prevent a late SATISFIED transition. Never repair a mismatch by inventing a fresh operation key.
6. Public views say only that the remedy was resolved, with no evidence URL, target coordinates, staff commentary, verifier identities or hashes. Appeals retain the assertion as **private historical evidence** even if the remedy is later overturned/waived; do not claim the previously observed physical cleanup can be undone automatically.

**Crash matrix:** (a) assertion write fails -> no canonical/W3B completion; (b) durable assertion, canonical write fails -> incomplete and retry/review; (c) canonical succeeds, W3B fails -> replay projection only; (d) both succeed, assertion-link update fails -> replay LINKED without another completion; (e) same key with different evidence/target -> conflict; (f) stale finding/overturn between verify and commit -> reject without completion. Tests must prove these under DB restart and concurrent appeals. This is a **proposed separate adapter/service path**, not permission to register or satisfy `OTHER` using existing APIs today.

## Lifecycle, concurrency, appeal and rollback constraints

1. In `disabled` and `shadow`, no OTHER provider dispatch, world mutation, Market/Reputation write, access denial or staff-prompted live effect. Shadow evaluations may only display unresolved remedies.
2. Start from persisted effective finding, retained policy version and `cases.target_id`; reject overturned findings, stale revisions, mismatch between case subject and operation target, or missing targeted attributes before provider calls.
3. Recheck active-duty actor authority at dispatch **and completion**, including provider-specific permission. Provider backends independently recheck ownership, expected revision/checksum, and target.
4. Use the existing `PolicyV2RemedyService` operation key/journal, provider operation UUID and W3B lifecycle; replay uses the same ID and exact target. Save receipt/evidence in an additive private durable record keyed by case/remedy/operation, with unique constraints, not an unaudited in-memory queue.
5. On provider committed receipt, verify exact target/postcondition and only then transition the canonical W2 remedy and W3B projection using current idempotent two-step recovery. If the provider commit succeeds but the Staff DB update fails, restart/retry fetches the same provider receipt and converges; **never** treat lack of local acknowledgement as permission for a new mutation.
6. A full overturn/reclassification must not automatically undo legitimate environmental restoration, revive removed abusive content, or recreate fraudulent reputation. Distinguish **reversible restrictions** (explicit compensating provider operation, supported by provider) from **irreversible corrections** (retain evidence, mark remedy superseded/waived as approved, don't recreate harm). Test concurrent appeal and pending provider work with a version/revision fence.
7. Missing, downgraded or unavailable providers fail closed: keep REQUIRED or review-needed state, audit a bounded diagnostic, never silently substitute another provider or claim completion. Operators need a bounded retry/reconcile action with permissions; reconciliation must not mint new acts.
8. The public API/Discord/website view is a **sanitized projection**, never a raw provider receipt, target coordinate, investigator note, hidden entry IDs or checksum.

## 2026-10-08 provider-side implementation checkpoint

- [EnthusiaCommend draft PR #26](https://github.com/wsg138/EnthusiaCommend/pull/26) introduces **non-mutating** exact-entry correction preflight with canonical subject checks, CAS snapshot checksum, exact giver/target/category/score/timestamp matching, duplicate/ambiguity refusal, immutable output, and score overflow rejection. Three exact-head GitHub Actions checks passed for its latest commit when reviewed. This is preparatory only: **no targeted mutation API or durable provider receipt yet exists**; it must not be treated as remedy completion.
- [EnthusiaMarket draft PR #15](https://github.com/wsg138/EnthusiaMarket/pull/15) adds **non-mutating** preflight requiring an exact stall ID, canonical SOLO owner UUID, expected world and revision, no conflicting moderation lock or duplicate IDs. It never guesses which stall to remove. As with Commend's preflight, a selection is *not* a durable mutation receipt, and no cleanup/removal provider exists yet. Market implementation must own transaction/recovery and asset guarantees.
- Cross-repository GitHub Issues are disabled in at least the checked Market repository, so [EnthusiaStaff #462](https://github.com/wsg138/EnthusiaStaff/issues/462) remains the canonical integration tracker for provider follow-ups. Do not assume provider issue records were opened.

## Required implementation sequencing

1. **This document / owner design review only (#462).** Confirm the six operation types, scopes, capabilities and any cases requiring manual signoff. No YAML/Java changes here.
2. **Market provider contract PR** in `wsg138/EnthusiaMarket` first: identify supported safe stall operations and define durable idempotent mutation/result lookup with exact target and ownership verification. Then an EnthusiaStaff adapter reusing the existing W3B lifecycle. Do not retrofit `confiscate` or `restore` unless the operations truly match the remedy's semantics.
3. **Reputation provider contract PR** in `wsg138/EnthusiaCommend` first: targeted entry correction with CAS, stable operation ID, audit, affected entry list, post-state checksum and read-back. Then Staff wiring, without direct writes to Commend tables.
4. **Paper/world and safety package**: inventory actual protected-area restoration and server-disruption capability; implement separate narrow providers or evidence-based Admin confirmations. Specify risk/authority and end-user impact before any commands. Safety containment needs a dedicated permission+evidence review, not a general OTHER adapter.
5. **Publication/wiring package**: only after accepted provider contracts, validate new typed bindings, add a new immutable policy version, wire authoritative mode with an independent owner cutover decision and allowlisted provider gateways. Profile reliability [#453](https://github.com/wsg138/EnthusiaStaff/issues/453), #455 review, Paper/Velocity runtime acceptance and production rollback remain separate blockers.

## Minimum regression/acceptance matrix

Run domain/unit and MariaDB/Testcontainers integration tests; for Paper/Market/Commend operations add provider-level and staged runtime tests. Every implemented operation must prove:

| Scenario | Required result |
| --- | --- |
| Wrong subject, cross-case remedy, missing target or unauthorized rank/off-duty actor | Reject before dispatch, no provider writes and no W3B registration/transition. |
| Same operation key, same target, repeated request (including process restart) | Exactly one provider mutation and identical committed receipt; same Staff outcome. |
| Same key with different payload, stale case finding, changed provider owner/checksum | Explicit conflict, not a silent retarget or duplicate. |
| Provider missing, timed out, returns PENDING, partial action, or acknowledgement lost | Remedy stays incomplete, deterministic reconcile from original ID. |
| Provider committed, Staff canonical DB update fails | Retry uses provider receipt, repairs canonical state, then W3B projection. |
| Provider committed, W3B projection update fails | Existing canonical-to-projection replay converges without another mutation. |
| Concurrent full overturn / changed finding during dispatch | Fenced or explicitly compensated; no post-overturn unauthorized side effect. |
| Protected-world partial rollback or unexpected chunk/resource scope | No broad rollback; human review and verifiable bounded recovery. |
| Market one-stall correction with multiple candidate stalls | Explicit target choice; preserve lawful assets and ownership; verify remaining state. |
| Reputation repair where entries have changed since review | CAS conflict; never erase valid/new entries by broad target score adjustment. |
| Safety action without private evidence or higher authority | Fail closed, avoid disclosure in staff/public projections. |
| `shadow`/ `disabled` evaluation | Zero provider calls and no live sanctions/restrictions/cleanup. |
| Public punishment projection after success/failure/appeal | No provider receipts, evidence paths, coordinates, internal attributes or private notes. |

**Exit criteria:** all six remedy IDs have an explicit approved handling strategy; every enabled provider returns durable completion proof and handles replay/rollback; all unsupported cases require review; full matrix passes; owner expressly approves separate deployment/shadow/authority cutover. Passing tests for the design alone does not satisfy any runtime cutover gate.
