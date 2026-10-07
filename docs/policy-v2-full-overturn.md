# Policy v2 W5C — recoverable full-overturn orchestration

Tracking: #398
Parent: #355
Audit source: #361 / PR #390

Policy v1 remains authoritative. This work does not deploy, activate, or cut over Policy v2.

## Durable operation

A factual full overturn is one durable saga identified by a caller-supplied UUID. The operation stores an immutable plan and moves only through:

`STARTED -> FINDING_OVERTURNED -> SANCTIONS_TERMINATED -> REMEDIES_CLEANED -> COMPLETED`

The operation row uses an optimistic revision fence. Each case may have at most one full-overturn operation. Reusing an operation UUID for different input, starting a second operation for the same case, or resuming against changed finding/sanction/remedy revisions fails with `PolicyV2Store.Conflict`.

Every stage transition appends a `FULL_OVERTURN_*` event to the existing immutable Policy v2 audit stream. The original case, original finding, sanction revisions, remedy records, and appeal events are never erased.

## Stage behavior

1. **Finding** — append a normal W2 `FindingOverturn`. The effective finding becomes absent and `BehavioralHistoryEntry.contributingOffenseId()` becomes empty. An already-overturned case is accepted so W5C can heal a pre-existing partial state.
2. **Sanctions** — call the sanction-termination provider with a deterministic operation UUID, then append a normal W2 sanction revision whose replacement set is empty.
3. **Remedies** — capture every canonical REQUIRED remedy plus any still-active enforcement projection. This includes W3B's recoverable canonical-terminal/enforcement-active split state. Run external cleanup for captured ENFORCED projections, waive canonical REQUIRED remedies, and move active enforcement projections to WAIVED.
4. **Appeal completion** — append APPROVED and REVISION_APPLIED appeal events with deterministic W2 operation keys, then mark the saga COMPLETED.

Provider calls are deliberately outside SQL transactions. Their operation UUIDs are stable across retries, so providers must implement idempotency on that UUID. W2 finding, sanction, remedy, enforcement, and appeal writes keep their existing operation-key replay/collision behavior.

Authorization is checked before the durable operation is created. FULL_OVERTURN is required. If an already-enforced remedy requires privileged cleanup, the existing scope boundary is also checked before any mutation: Market and Reputation cleanup remain Admin-level and asset restoration remains Founder-only. Once an authorized saga exists, restart recovery uses the persisted actor and plan so a crash cannot strand the case merely because no staff session is present.

Before any external provider call, W5C revalidates the captured canonical and enforcement revisions. A non-terminal concurrent enforcement change is rejected before cleanup or canonical waiver, while an already-terminal projection is accepted as converged.

Sanction-only leniency and factual reclassification remain their existing independent W2 operations. If either changes a captured revision before its W5C stage executes, the saga rejects the stale fence instead of converting that change into an overturn.

## Failure/recovery matrix

| Injected failure | Durable state at crash | Retry/restart behavior | Duplicate external effect |
| --- | --- | --- | --- |
| Before finding overturn | STARTED; finding unchanged | executes finding overturn, then continues | none |
| After finding overturn | STARTED; finding already OVERTURNED | W2 finding operation replays / current state is recognized, stage advances | none |
| Before sanction termination | FINDING_OVERTURNED; sanctions unchanged | validates sanction revision, terminates, persists empty sanction set | none |
| After sanction termination | FINDING_OVERTURNED; provider effect may exist while the W2 sanction set is still unchanged | retries the provider with the same UUID, then persists the empty sanction set | none; provider deduplicates the stable UUID |
| Before remedy cleanup | SANCTIONS_TERMINATED; remedies unchanged | validates remedy/enforcement fences, cleans and waives them | none |
| After remedy cleanup | SANCTIONS_TERMINATED; provider cleanup may exist while canonical/enforcement remedies are still active | retries cleanup with the same UUID, then persists canonical/enforcement terminal state | none; provider deduplicates the stable UUID |
| Before final appeal/audit completion | REMEDIES_CLEANED; no final appeal events yet | appends APPROVED and REVISION_APPLIED once, then COMPLETED | none |

The integration test closes the MariaDB-backed runtime after every injected failure, reconstructs all stores/orchestration objects, and resumes by operation UUID. Every row in the matrix must converge to: OVERTURNED finding, no history contribution, empty current Policy v2 sanction set, no REQUIRED remedy, WAIVED enforcement projection, one pair of final appeal events, one audit event per saga stage, and one logical provider effect per provider. The two provider-split rows additionally assert two provider invocations with the same stable UUID but only one recorded external effect.

## Non-authoritative boundary

W5C supplies provider ports for sanction termination and remedy cleanup but does not wire them into live Policy v1 or enable Policy v2. Runtime/provider wiring remains a later integration/cutover concern. No Policy v1 table or behavior is rewritten by this feature.
