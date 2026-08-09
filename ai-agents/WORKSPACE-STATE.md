# EnthusiaStaff workspace state

Last updated: 2026-08-09

Live GitHub state overrides stale records, but persistent package state must be reconciled here.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P03`, `ES-P04`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05` |
| Parked packages | `ES-R01 — Billing-independent staging bridge recovery`; `ES-P02 — Runtime database recovery and Velocity reload`; `ES-P05 — Report evidence and staff workflow completion` |
| Active/selected package | None after this worker's terminal ES-P05 repair publication merges. |
| Ready packages | None under the current canonical dependency/blocker state. |
| Current legitimate EnthusiaStaff main before ES-P05 terminal publication | `b0cf67b880856ec7536cf1385fe1559bb18a42a1` |
| Current legitimate staging main | `d5f77c0b9d1e896443054a82f94d7f8741d36fbc` |
| ES-R01 public implementation | PR #93, frozen head `cccadbd1885f78db517ff643f941d04bd0fba2a3`, merged normally as `094838fa221476e0832cf821f7b4908b9402d0d9` |
| ES-R01 private implementation | PR #58 → `570f83e41cb80b498a82c8b5a509c42345558a46`; PR #59 → `313ed2815058eadeb8c823453f4152089cae01d4`; PR #60 → `4036d6e915c2d751bef18849107722dfd1e586a6` |
| ES-R01 checkpoint | PR #94 frozen head `3f90ae4e96e969a7ceac45ee9a385f068c0af14a`, normal merge `689ff337dd8a33f0bd417d952a7cad5581cb9d9e`; containment exact and checkpoint branch auto-deleted |
| ES-R01 terminal status | `BLOCKED` / `PARKED_BLOCKED` |
| ES-R01 terminal handoff | `ai-agents/reports/package-handoffs/2026-08-08-es-r01-final-blocked-current-main.md` |
| ES-R01 blocker | Preserve the current canonical ES-R01 blocker from its own package record/handoff; this ES-P05 repair worker did not reopen or modify ES-R01 infrastructure. |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70; unchanged by this worker. |
| ES-P05 status | `BLOCKED` / `PARKED_BLOCKED`; PR #81; ReportStore hosted regression fixed and final head `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2` is fully hosted-green; correlated private Pi job exists but has zero execution because `Lincoln-PI-4` has not accepted it. |
| ES-P05 terminal handoff | `ai-agents/reports/package-handoffs/2026-08-09-es-p05-reportstore-repair-blocked-pi.md` |
| Migration boundary | V18 remains immutable/current; ES-P05 changed no migration. |
| Production boundary | issue #43 remains open/deferred; LiteBans remains authoritative. |
| Next legitimate action | First inspect existing ES-P05 public run `31301426684` and private run `31301734048`. If the correlated private run later executed, resume ES-P05 and classify its real terminal result; do not create a duplicate run merely because time passed. If the self-hosted environment remains unavailable, keep ES-P05 parked. |

## ES-P05 ReportStore repair evidence

### Hosted regression and root cause

The current public hosted regression belonged to ES-P05 because the failing behavior was inside its report lifecycle/evidence contract and existing PR #81 already owned that work.

The two failures were:

- `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()` — expected queue membership `true`, observed `false`;
- `ReportStoreIntegrationTest.duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()` — expected two evidence rows, observed zero.

The shared root cause was the integration fixture's fixed `NOW = 2026-08-01T12:00:00Z`. The production report policy uses seven-day evidence retention and a seven-day recently-closed window, while production queries correctly compare against the real database/system clock. By 2026-08-09 the fixture rows had legitimately expired/aged out.

The repair changed only the test fixture clock to current time minus 60 seconds, truncated to microseconds. Existing explicit nine-day-old purge coverage remains expired. No `ReportStore`, persistence query/state/transaction, policy, schema, or Flyway behavior was changed for the two failures.

### Branch synchronization and review

PR #81's old product head was synchronized with current main through two-parent merge commit `5d78a9621f7cc3e5f056b417af88424eaa26e555`; no rebase or force-push. Final reviewed/hosted head is `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2`, zero behind starting main.

CodeRabbit found three valid privacy/authorization/bounds issues in the resumed feature diff. All were fixed: delivery-time sensitive-permission recheck, scalar-only client evidence fields, and bounds on every rendered chat text field. All three review threads are resolved and CodeRabbit status is green.

### Exact-head hosted proof

On exact final head `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2`:

- Wiki run `31301427600`, job `93214726543`: success;
- Coverage run `31301427623`, job `93214731253`: success on Java 21 with full build/tests, MariaDB/Testcontainers, migration validation, aggregate coverage, runtime-JAR inspection and artifact/Codacy upload;
- Codacy static `93214975215`: success, zero issues;
- Codacy diff coverage `93215398455`: success, 47.37%, no configured gate;
- zero valid unresolved review threads.

Because the complete integration suite passed, both originally failing ReportStore methods pass on the final head. A separate local focused run is not claimed because the reset execution environment had no checkout/network access.

### Canonical Pi state

Automatic public Pi Staging run `31301426684` is exact-head evidence for `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2`:

- public build job `93214729981`: success, including exact runtime artifact generation/upload;
- bridge job `93215481473`: exact artifact download and bounded transfer succeeded; private workflow dispatch and correlation succeeded;
- correlated private run `31301734048`, title `EnthusiaStaff bridge 31301426684-1 / ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2`;
- private job `93215499833`: queued with `runner_id: 0`, empty runner name, zero steps at terminal publication time.

A private run therefore genuinely exists, but no private prerequisite, database reset, Paper boot, plugin enablement, Flyway, restart, persistence, process-reap, or cleanup assertion executed. This is temporary Pi runner/environment unavailability, **not a Pi product failure and not a staging pass**. No duplicate retry or direct private dispatch was issued.

Sentinel is non-applicable because the PR head has no `.enthusia-test.yml` manifest.

## ES-R01 terminal evidence

### Checkpoint merge and exact-head quality

PR #94 frozen head `3f90ae4e96e969a7ceac45ee9a385f068c0af14a`:

- Coverage run `31252575675`, job `93091088422`: success on Java 21 with full build/tests/runtime inspection/aggregate coverage;
- Codacy static `93091169010`: success;
- Codacy diff coverage `93091677173`: success;
- Codacy coverage variation `93091677101`: success;
- CodeRabbit: success after one valid terminal-status sequencing finding was accepted, fixed, and resolved;
- normal merge commit `689ff337dd8a33f0bd417d952a7cad5581cb9d9e` has exactly two parents, pre-merge `main` `094838fa221476e0832cf821f7b4908b9402d0d9` and frozen head `3f90ae4e96e969a7ceac45ee9a385f068c0af14a`;
- compare frozen head → merge: one commit ahead, zero behind, no file differences;
- the public checkpoint branch was automatically deleted after merge.

### Required fresh current-main proof

Public Pi Staging run `31252997554`, exact source `689ff337dd8a33f0bd417d952a7cad5581cb9d9e`:

- public build job `93092131811` succeeded on ordinary GitHub-hosted `ubuntu-latest` with Java 21 and uploaded the verified runtime package;
- bridge job `93092964130` successfully required the token, downloaded the exact artifact, published bounded transient transfer, dispatched/correlated the private run, collected failure diagnostics, and removed the transient transfer; its final result is failure, correctly reflecting private staging failure;
- private run `31253345564` on staging control SHA `4036d6e915c2d751bef18849107722dfd1e586a6`;
- private job `93092978141` allocated trusted `Lincoln-PI-4`, runner ID `2`;
- runner identity and exact public bridge artifact verification succeeded;
- `Run guarded disposable Paper boot and restart test` failed; runtime acceptance therefore did not pass;
- sanitized evidence upload succeeded as artifact `9020680419`, digest `sha256:5647d2458ab4b1d594e86030d9ffe1a89ac50609093417d3fcb617ecf5b1b677`;
- this current-main run's step metadata does not expose a fresh SQLState, so none is invented. Earlier captured proof `31250170297` → private `31250450219` / `93085892938` established seven guarded connection attempts, all SQLState `08000`, before Paper boot.

The ES-R01 proof above remains **NOT A PASS**. This ES-P05 worker did not reinterpret or repair that separate package's infrastructure state.

## Classification snapshot

- `ES-R01`: `BLOCKED` / `PARKED_BLOCKED`; unchanged by this ES-P05 repair worker.
- `ES-P02`: `BLOCKED` / `PARKED_BLOCKED`; PR #70 unchanged.
- `ES-P05`: `BLOCKED` / `PARKED_BLOCKED`; hosted defect fixed and review/hosted gates green, but canonical Pi runtime has zero execution because the correlated self-hosted job is unallocated.
- `ES-P07`, `ES-P06`, `ES-P08`, `ES-X01`, `ES-X02`, `ES-X03`, `ES-X04`, and `ES-QA01`: dependency-blocked planned work under current registry rules.
- `ES-V01`, `ES-V02`, `ES-V03`, and `ES-A01`: deferred private/acceptance work under their existing contracts. Issue #43 remains open and does not authorize production cutover.

## Resume boundary

For ES-P05, first inspect the already-correlated public/private runs. Do not manually rerun merely because time passed. If private run `31301734048` later completes, use that exact result. If `Lincoln-PI-4` becomes materially available and a fresh run is actually required, use only the normal canonical public workflow. On real private success and unchanged hosted/review evidence, merge PR #81 normally, verify containment, publish COMPLETE state and clean the branch. On a real ES-P05 runtime defect, fix it inside ES-P05 and revalidate.

## Safety boundaries

No production data/configuration, credentials, punishment/player records, raw addresses, private databases, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration/cutover, or unrelated package implementation is authorized by this terminal-state publication.
