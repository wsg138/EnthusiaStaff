# ES-F01004 — R01-004 recovery handoff — 2026-09-20

## State
- Finding: `R01-004` in issue `#216`.
- Package: `ES-F01004`.
- Implementation PR: `#221`.
- Branch: `package/es-f01004-cheat-tester-folia-handoff`.
- Claimed from `main` `c1054da6a8f89b312df2e05e25edc958fceda7ef`.
- Frozen validated product head: `4eab447191ee7a379328ad8d5e7c36ad755e046d`.
- Reconciled live `main`: `a2c835419656adc2ddbf7f77fc49292ca43ee8f3`.
- Classification: `BLOCKED` / `PARKED_BLOCKED` pending representative real Folia acceptance.

## Recovery reconstruction
The original R01-004 report identified off-owner `Player.isOnline()` reads in Cheat Tester and fake-base global-to-entity handoffs. The existing PR replaces those bridges with a package-local `FoliaPlayerHandoff` that resolves a `Player` reference globally, then performs availability reads and product continuations only from that player's entity scheduler.

No product rewrite was needed during recovery review. The implementation already handles the required failure boundaries:
- owner-only live `Player` availability/state reads;
- offline players;
- entity retirement and scheduler rejection;
- duplicate owner execution and duplicate/late retirement callbacks;
- global/entity scheduler dispatch exceptions;
- durable Cheat Tester rows remaining authoritative when recovery cannot obtain an owner callback;
- recovery registration and inventory asset locking only after the owner callback runs;
- target re-resolution by UUID after worker-side evidence checkpointing, avoiding restoration through a stale retained `Player`;
- fake-base handoffs through the same owner-safe primitive;
- exact-once handoff settlement and existing session/journal completion fencing.

## Validation evidence for frozen product head `4eab447191ee7a379328ad8d5e7c36ad755e046d`
- Independent local Java 21 focused regressions: PASS — `FoliaPlayerHandoffTest` and `FoliaTesterHandoffWiringTest`.
- Independent local Java 21 full `:paper:test --rerun-tasks --no-build-cache`: PASS.
- Existing `CheatTesterJournalCompletionTest` covers successful completion, authoritative-revision retry, and conflict refusal.
- Existing `CheatTesterSessionTest` covers cancellation/journal races, duplicate finishing, and submission rollback.
- Hosted Java 21 Coverage run `35537363011`: PASS on the exact product head. It executed `clean build jacocoAggregateReport runtimeJars`, including Testcontainers integration work unavailable on the local workstation.
- Hosted validation artifact `java-21-validation` id `10613299305`, digest `sha256:b5eb129a60d84d619c53b6d702d91d7ba3ada4dd64991c18605d852bbf655bd6`.
- Hosted Sentinel artifact build run `35537362980`: PASS on the exact product head.
- Sentinel Paper artifact id `10612739527`, digest `sha256:1b7aa494bd7378a2c5efcd1c0d137cccb445c96893efaa041bbfc30a6df74041`.
- Authority bridge artifact id `10613283727`, digest `sha256:c99f9c565ee5a313485245f3cdeb131f1ccff5bc559ffa0c089eb1a951963dc9`.
- Codacy: 0 new valid issues; static analysis PASS; reported PR complexity metric 6.
- Independent Lizard changed-production-code gate: PASS, zero warnings at CCN `<= 8`, method length `<= 50`, args `<= 8`.
- `git diff --check origin/main...HEAD`: PASS.
- Review reconciliation: no human review submissions, no inline review threads, no requested changes, and no valid Codacy findings. CodeRabbit did not auto-review this repository; that is not represented as review evidence.
- Sentinel exact-SHA restart command was submitted on PR #221 as source comment `5752881805`; job `473` is bound to `4eab447191ee7a379328ad8d5e7c36ad755e046d`. At this checkpoint its status comment `5752885142` remains non-terminal `RUNNING`, so no Sentinel runtime pass is claimed yet.
- Local root clean build is not separately claimed because the workstation has no usable Docker command; the hosted exact-head clean build above is the applicable executable evidence.

## Persistence, schema, and collision boundary
This repair contains no schema or migration change. The claimed migration boundary remains merged V20 with preserved branch-local migration ownership elsewhere. The product/test diff is confined to the Cheat Tester/fake-base tester package. Reconciliation found no active PR owning those exact product paths. Live `main` advanced only through PR #218's punishment-request Folia repair, which changes punishment GUI paths rather than tester paths.

Shared `PACKAGE-REGISTRY.md`, `WORKSPACE-STATE.md`, and `agent-handoffs/latest.md` are intentionally not modified here because the concurrent T01 package owns those shared orchestration paths, and the owner explicitly directed this recovery worker not to create a replacement branch or PR. This handoff and the unique ES-F01004 package file therefore carry the package-local parked state until shared-state ownership is available.

## Genuine blocker
The finding itself requires representative real Folia acceptance. The repository currently has no Folia staging profile: `.enthusia-test.yml` exposes only the Paper `restart` profile. Current repository status/requirements documentation also explicitly records representative Folia staging as unavailable/staging-pending for staff tools and cheat testers. The Sentinel restart profile is useful Paper runtime evidence but cannot substitute for a real Folia ownership run.

Because unavailable/skipped/stale infrastructure evidence is not a pass, PR #221 must not be merged yet.

## Exact unblock condition and next action
Provision or expose an owner-approved representative Folia staging environment capable of running the frozen product behavior, then exercise Cheat Tester start, finish/restoration, durable restart/recovery, unavailable/retired target handling, and fake-base owner handoffs on a current candidate derived from product head `4eab447191ee7a379328ad8d5e7c36ad755e046d`. Require a genuine terminal Folia pass. Also require the Sentinel restart job to reach a terminal pass if it has not already done so. After the external blocker changes, reconcile current `main` and all path ownership, synchronize only as needed, rerun any exact-head gates invalidated by synchronization, and merge PR #221 with a normal merge commit only.

Do not select another finding from this worker.