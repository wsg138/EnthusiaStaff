# `ES-X03` — EnthusiaMarket destructive provider

## 1. Package identity
`ES-X03`; External/multi-repository; primary `COMP-STAFF`; other `COMP-MARKET`; priority 120; conditional parallelism only without shared destructive-state overlap.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED` after a live `ACTIONABLE_CONTINUATION` on 2026-08-26. The old thermal/runtime blocker materially changed and the standalone Market half has now merged, but the Staff half cannot be safely reconciled or merged while independent Discord package `ES-D04` owns the next Staff migration number and overlapping shared Staff files. Registry remains the canonical routing index.

## 3. Objective
Implement durable market restriction, reservation, confiscation, rollback, and exact restoration across EnthusiaStaff and EnthusiaMarket.

## 4. Included behavior
Supported versioned provider contract; listing/reservation ownership; durable snapshots/operation IDs; restriction/confiscation; idempotent rollback/restoration; retry/restart/race handling; provider missing/version mismatch; matching aggregate copy and parity.

## 5. Explicit exclusions
Production listings; whole-market rollback; currency/reputation work; unverified reflection against provider internals; representative destructive/load/process-kill acceptance assigned to `ES-V03`.

## 6. Dependencies
`ES-P08` and `ES-X02` are `COMPLETE`.

## 7. Repository and privacy boundaries
Use the existing Staff PR #139 on `package/es-x03-market-provider`. Standalone Market PR #3 is already merged and must not be replaced or rewritten. Market may use only ordinary public repository CI. No private Pi/staging runner config, labels, private bridge/dispatch implementation, staging secrets/topology/credentials, private artifact-transfer mechanism, or Sentinel infrastructure may enter Market or BadgersMC repositories. Preserve `preserve/es-x03-post-candidate-556b4b4-20260814` while it contains unique unrelated work.

## 8. Current live heads and PR state
- EnthusiaMarket `main`: `7dd0a89d3689785f0b70c770e1b7c8efa1d11929`, normal merge commit for PR #3.
- EnthusiaMarket merged product head: `01a1ac70721e5d5c5f0ba73757ec01908cce53ea`.
- EnthusiaStaff `main` at the blocker publication start: `d8cdedf4adcd16e46073bdfbe6d6f8aa309a6d29`.
- EnthusiaStaff PR #139 current package-record head: `702b13438fd95da235b4a87218901be04999aaea` on `package/es-x03-market-provider`.
- Independent Discord PR #151 (`ES-D04`) is open and mergeable at `3df254d69fec59a80df91565297ae9283637b639` and owns `persistence/src/main/resources/db/migration/V20__discord_account_linking.sql` plus overlapping shared Staff files.

No force-push, rebase, squash, or destructive reset is authorized or used.

## 9. 2026-08-26 continuation and repair
Live GitHub proved the old owner-controlled host condition had materially changed: later trusted Pi/Sentinel work succeeded on the same validation path, and Market PR #3 resumed and merged normally. ES-X03 therefore became an `ACTIONABLE_CONTINUATION`.

The resumed Staff branch had a real exact-head hosted build defect at `22ea8395caa421dc9161c84acd58b5b16ca05fc8`: `CheatTesterJournalIntegrationTest` still asserted migration ceiling 19 after X03 had moved its branch-local Staff migration to V20. Public Pi run `32922736904` failed before private Pi execution with `expected: <19> but was: <20>`; that failure remains non-passing history.

The isolated test assertion was corrected without changing product behavior, producing Staff head `702b13438fd95da235b4a87218901be04999aaea`.

## 10. Review and hosted exact-head evidence
At `702b13438fd95da235b4a87218901be04999aaea`:
- Staff PR #139 has zero live inline review threads.
- CodeRabbit status is successful, but its repository policy explicitly skips automatic full review; no automated full-review approval is claimed.
- Canonical public Pi run `32924559285` exact-head source validation and trusted hosted build job `98044698537` succeeded, including Java 21, clean full build/tests, aggregate coverage generation, runtime-JAR packaging, and exact artifact publication.
- The regular `pull_request` Coverage and Sentinel artifact workflows do not currently execute because PR #139 is merge-conflicted with current `main`; queued, absent, merge-ref-only, or different-revision checks are not called passing.

## 11. Canonical Pi / staging state
Public canonical Pi run `32924559285` successfully built and bridged the exact Staff artifact. It dispatched correlated private staging run `32925074087` for exact source `702b13438fd95da235b4a87218901be04999aaea`, artifact identity `enthusiastaff-paper-702b13438fd9-32924559285-1`, runtime SHA-256 `6086f728fdd673346588f2be40c3ec3c6bd80aecbec32602f028eb20c303c604`.

At blocker publication time the private job `98046237374` is still queued behind legitimate concurrent staging work on trusted `Lincoln-PI-4`. This is `PENDING`, not a pass and not a failure waiver. Do not cancel or preempt the other package's staging work merely to advance X03.

## 12. Current synchronization evidence
The aggregate Market mirror was synchronized from the standalone merged product lineage before the current Staff-only test assertion repair. Final canonical standalone↔aggregate parity remains a required post-Staff-merge step; no terminal `IN_SYNC` claim is made yet.

## 13. Migration impact and serialization blocker
Existing migrations remain immutable. Canonical Staff `main` is at V19. X03 currently carries branch-local `V20__market_compliance_journal.sql`, while independent `ES-D04` PR #151 also legitimately carries `V20__discord_account_linking.sql` and edits shared Staff integration/persistence files including `PaperCommandRegistrar`, `PaperStorageBindings`, `plugin.yml`, and `MariaDbRuntime`.

PR #139 is also substantially behind current `main` and merge-conflicted. Resolving that divergence now would require choosing between overlapping live D04 work and X03's stale branch versions. ES-X03 must not steal D04's migration number, rewrite D04, or synthesize an unsafe conflict resolution merely to make checks run.

## 14. Exact unblock condition
`BLOCKED` / `PARKED_BLOCKED`. Do not create a replacement X03 product branch and do not begin another ES-X03 implementation.

Resume after the independent D04 migration/shared-file work has serialized onto Staff `main` (or otherwise reached a durable state that removes the V20/shared-file ambiguity). Then:
1. reconcile fresh `main` into `package/es-x03-market-provider` using an ordinary merge commit;
2. renumber X03's Staff migration to the next free forward-only version without rewriting any merged migration;
3. resolve shared-file conflicts preserving both packages' legitimate behavior;
4. freeze the resulting exact Staff head;
5. run all applicable exact-head hosted/static/review checks plus independent Sentinel and canonical Pi staging, treating queued/missing/failed evidence honestly;
6. merge Staff PR #139 only after every required gate is terminal and green;
7. prove post-merge standalone↔aggregate Market parity, publish terminal component/package state, and clean only safely contained branches.

## 15. Completion definition
Standalone Market PR #3 is complete and normally merged, but ES-X03 as a package is not complete until Staff PR #139 is safely reconciled, fully exact-head validated, normally merged, contained, and post-merge provider parity is exact. Representative destructive/load/process-kill acceptance remains `ES-V03`.

## 16. Historical runtime blocker disposition
The August 14 thermal/readiness failures remain valid non-passing historical evidence, but they are no longer the current routing blocker because live host conditions demonstrably changed enough for X03 to resume. They must not be rewritten as passes.

Historical handoff: `ai-agents/reports/package-handoffs/2026-08-14-es-x03-market-provider-blocked.md`.
Current handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-x03-discord-serialization-blocked.md`.

## 17. Production boundary
No production listing, balance, item, private player row, database, deployment, migration/import execution, cutover, Discord configuration, or authority state changed. LiteBans remains authoritative and issue #43 remains deferred.
