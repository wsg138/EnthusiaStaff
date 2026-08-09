# EnthusiaStaff workspace state

Last updated: 2026-08-09

Live GitHub state overrides stale records, but persistent package state must be reconciled here. Historical package detail is retained in the canonical package handoffs; this file records the current routing snapshot.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P03`, `ES-P04`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05` |
| Parked packages | `ES-R01 — Billing-independent staging bridge recovery`; `ES-P02 — Runtime database recovery and Velocity reload`; `ES-P05 — Report evidence and staff workflow completion` |
| Active/selected package | None after the ES-R01 terminal publication merges. |
| Ready packages | None. |
| ES-R01 classification during this worker | `ACTIONABLE_CONTINUATION` after live evidence proved the former MariaDB blocker stale. |
| ES-R01 terminal status | `BLOCKED` / `PARKED_BLOCKED` |
| ES-R01 terminal handoff | `ai-agents/reports/package-handoffs/2026-08-09-es-r01-release-freshness-repaired-public-build-blocked.md` |
| Starting EnthusiaStaff main | `140d10ef63f3d6761c95afccbead13db53888304` |
| Starting EnthusiaStaff-Staging main | `19e7c44f646caa51d0a0d97fa15f6596014efadc` |
| Repaired staging main | Contains normal merge `af1bd6d3ae8214e58eb969c23972f872b15c1f18` from staging PR #75 frozen head `19e38d6851367d835cfe50fc29e9f95a0936f66d`. |
| ES-R01 old blocker correction | MariaDB reachability is no longer the exact blocker; ES-P05 run `31301426684` → private `31301734048` / job `93215499833` proved guarded DB reset plus two successful Paper cycles/restart/persistence/final cleanup on `Lincoln-PI-4` runner ID `2`. |
| ES-R01 repaired defect | Transient release freshness verifier incorrectly used GitHub Release `created_at`; repaired to require Release `published_at` while retaining asset `created_at`, two-hour max age, future-skew and all provenance/digest/cleanup checks. |
| ES-R01 new blocker | Current public `main` cannot produce the trusted runtime artifact because two ReportStore integration tests fail before bridge execution. Baseline main Pi run `31332055336` / build `93291754833` and ES-R01 docs Pi run `31333070856` / build `93294291022` reproduce the same failures. |
| ES-R01 exact unblock | Material evidence that current `EnthusiaStaff:main` again passes the canonical trusted public Java build, including `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()` and `duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()`, without weakening/bypassing the gate. |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70. Next sequential worker must reconcile whether the product-side public-build blocker belongs to ES-P02, ES-P05, or another canonical product route. |
| ES-P05 status | `BLOCKED` / `PARKED_BLOCKED`; PR #81 remains untouched at `346e764f40b25c98e7d24ce7f863e5629773e814`. Its shared release-freshness prerequisite is repaired, but its current head has not been rerun or otherwise validated by ES-R01. |
| Migration boundary | V18 remains immutable/current; ES-R01 changed no migration. |
| Production boundary | issue #43 remains open/deferred; LiteBans remains authoritative. |
| Next legitimate action | Reconcile live GitHub first. If current-main public build is still red in the same ReportStore tests, keep ES-R01 parked and route the product-side condition according to the package registry. If material evidence shows that exact condition changed, resume ES-R01 first and obtain one fresh exact-current-main canonical public→private staging proof before declaring it `COMPLETE`. |

## ES-R01 continuation evidence

### Former MariaDB blocker changed

Historical ES-P05 exact-source proof at `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2`:

- public Pi Staging run `31301426684`;
- private run `31301734048`, job `93215499833`;
- trusted `Lincoln-PI-4`, runner ID `2`;
- exact artifact/provenance verification passed;
- guarded pre-reset passed;
- Paper cycle 1 reached readiness, EnthusiaStaff enabled, MariaDB/Flyway through V18;
- clean shutdown/full reap;
- Paper cycle 2 reached readiness with restart/persistence/schema-v18 proof;
- clean shutdown/full reap;
- guarded final database cleanup passed;
- sanitized artifact `9034945235`, digest `sha256:a81af3154c7e561c5ea09ed7072c970d483b25b63e217f34f1976132bab4ef3e`.

This made the previous `MariaDB unreachable from Lincoln-PI-4` package text stale and justified `ACTIONABLE_CONTINUATION`.

### Freshness defect exposed and repaired

ES-P05 final candidate `346e764f40b25c98e7d24ce7f863e5629773e814` produced public run `31330788773`; public build job `93288608088` succeeded; bridge job `93289540403` dispatched private run `31331175023` / job `93289556545` to trusted runner ID `2`. Private verification failed before DB/Paper with `release created_at is expired for the staging bridge`. Sanitized diagnostic artifact `9042975898`, digest `sha256:1b470d2f20a6f263ed424734ff9397663077bbeec2d3ea942e3981ed926d7a93`; transfer cleanup succeeded.

GitHub's Release REST semantics define `created_at` from the commit used for the release, not publication. Staging PR #75 therefore changed release-publication freshness to required Release `published_at` and retained Release Asset `created_at` for upload freshness. The two-hour maximum age and five-minute future-skew protections remain unchanged.

Frozen repair head `19e38d6851367d835cfe50fc29e9f95a0936f66d` passed Staging Controls run `31332576934` / job `93293056853` on `Lincoln-PI-4`, including all required positive/negative freshness and provenance fixtures plus the broader staging suite and 292 Sentinel unit tests. CodeRabbit was green; zero unresolved review threads. Staging PR #75 merged normally as `af1bd6d3ae8214e58eb969c23972f872b15c1f18`.

### New upstream current-main blocker

The mandatory fresh current-main proof cannot reach the repaired transport because the trusted public product build is red before artifact creation.

Baseline `main` `140d10ef63f3d6761c95afccbead13db53888304` automatically ran canonical Pi Staging `31332055336`; public build job `93291754833` failed:

- `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()` — expected `true`, got `false`;
- `ReportStoreIntegrationTest.duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()` — expected `2`, got `0`.

ES-R01's documentation-only public head `c2a80525e964acfaf230169863d835dcf07d3d60` reproduced the same failures in Coverage `31332739840` and its unchanged-head rerun, and in canonical Pi Staging `31333070856` / build job `93294291022`. Bridge job `93295041935` was skipped because no verified artifact existed; no private run was dispatched.

This condition predates the ES-R01 public documentation change. ES-R01 may not change Report Java product/tests or weaken the trusted build. It therefore stops truthfully as `BLOCKED` / `PARKED_BLOCKED`.

## ES-P05 preservation

PR #81 remains open and parked. This ES-R01 worker did not merge it, modify product files, resynchronize it, rerun its staging, or delete its branch. The shared freshness repair does not retroactively validate PR #81.

## Cleanup

`noop-temp-ignore` has no PR but has two unique commits/work relative to `main`; the owner's safe-deletion precondition is false. It remains untouched.

## Resume rule

Resume ES-R01 only when live GitHub shows the exact current-main public-build condition materially changed. Then run one fresh legitimate current-main proof through public Java 21 build → bounded verified transfer → correlated private dispatch → trusted `Lincoln-PI-4` → exact provenance → guarded DB pre-reset → Paper cycle 1 → clean shutdown/reap → Paper cycle 2/restart-persistence → clean shutdown/reap → guarded final cleanup → sanitized evidence → correlated public success → transfer cleanup. Only then mark ES-R01 `COMPLETE` and stop.

The next sequential worker should otherwise reconsider ES-P02/ES-P05 according to current canonical priority/routing and the product-side build failure. Do not start ES-P05 from an ES-R01 worker.
