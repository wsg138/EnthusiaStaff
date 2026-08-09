# EnthusiaStaff workspace state

Last updated: 2026-08-09

Live GitHub state overrides stale records, but persistent package state must be reconciled here. Historical package detail is retained in the canonical package handoffs; this file records the current routing snapshot.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P03`, `ES-P04`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`; `ES-R02 — Report integration fixture clock recovery` becomes terminal `COMPLETE` when this exact validated repair PR merges normally |
| Parked packages | `ES-R01 — Billing-independent staging bridge recovery`; `ES-P02 — Runtime database recovery and Velocity reload`; `ES-P05 — Report evidence and staff workflow completion` |
| Active/selected package | `ES-R02 — Report integration fixture clock recovery`, explicit owner-directed baseline-build deadlock recovery |
| Ready packages | None. Do not start a normal product package in this worker. |
| ES-R02 branch / PR | `package/es-r02-report-fixture-clock-recovery`; PR #103 |
| ES-R02 starting main | `15d37fa1e49b5d4b8403914b6f7a43892dbe417e`, normal merge of ES-R01 terminal publication PR #102 |
| ES-R02 implementation | Only `integration-tests/src/test/java/net/enthusia/staff/integration/ReportIntegrationFixtures.java`: replace historical fixed `NOW` with one JVM-wide `Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS)` and add the `ChronoUnit` import |
| ES-R02 root cause | The fixed `2026-08-01T12:00:00Z` integration-fixture clock aged outside the live seven-day recently-closed/evidence-retention policy windows while production queries correctly use live UTC/database time |
| ES-R02 terminal intent | Exact-head hosted validation, normal merge, containment/no-feature-leak verification, then stop; this package makes no production ReportStore claim and no ES-P05 completion claim |
| ES-R01 status | `BLOCKED` / `PARKED_BLOCKED` during ES-R02. Its exact unblock is current-main trusted public Java build success in the two ReportStore tests; if ES-R02 merges green, that condition materially changes and the next worker must resume ES-R01 as `ACTIONABLE_CONTINUATION` |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70 untouched |
| ES-P05 status | `BLOCKED` / `PARKED_BLOCKED`; PR #81 remains untouched and unmerged at `346e764f40b25c98e7d24ce7f863e5629773e814` |
| Migration boundary | V18 remains immutable/current; ES-R02 changes no migration |
| Production boundary | issue #43 remains open/deferred; LiteBans remains authoritative; no production data/infrastructure change |
| Cleanup boundary | `noop-temp-ignore` remains untouched because it contains two unique commits/work relative to main |
| Exact next worker after a successful ES-R02 merge | Resume `ES-R01 — Billing-independent staging bridge recovery`; obtain one fresh exact-current-main canonical public→private proof. Do not start ES-P05, ES-P02, ES-P06, or ES-X01 first. |

## ES-R01 prerequisite history

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

This made the previous `MariaDB unreachable from Lincoln-PI-4` blocker stale.

### Freshness defect exposed and repaired

ES-P05 final candidate `346e764f40b25c98e7d24ce7f863e5629773e814` produced public run `31330788773`; public build job `93288608088` succeeded; bridge job `93289540403` dispatched private run `31331175023` / job `93289556545` to trusted runner ID `2`. Private verification failed before DB/Paper with `release created_at is expired for the staging bridge`.

Staging PR #75 repaired release-publication freshness to use required Release `published_at` while retaining Release Asset `created_at`, the two-hour maximum age, future-skew guard and all provenance/digest/cleanup checks. Frozen repair head `19e38d6851367d835cfe50fc29e9f95a0936f66d` passed Staging Controls run `31332576934` / job `93293056853` and merged normally as `af1bd6d3ae8214e58eb969c23972f872b15c1f18`.

### Current-main public-build blocker and ES-R02 recovery

Baseline `main` `140d10ef63f3d6761c95afccbead13db53888304` failed canonical Pi Staging `31332055336` / public build job `93291754833` before artifact creation in exactly:

- `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()` — expected `true`, got `false`;
- `ReportStoreIntegrationTest.duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()` — expected `2`, got `0`.

ES-R01 documentation-only heads reproduced those same failures. Live source confirms current main used `ReportIntegrationFixtures.NOW = Instant.parse("2026-08-01T12:00:00Z")`, while `ReportPolicy.defaults()` uses seven-day recently-closed and evidence-retention windows and `JdbcReportQueryStore` uses live `Clock.systemUTC()` / `CURRENT_TIMESTAMP(6)` for those reads.

ES-P05 PR #81 already proved the independently safe test repair on hosted CI: compute one `NOW` per test JVM from current time minus 60 seconds, truncated to microseconds. Its final hosted-green head passed the complete Java 21 build/tests, ReportStore integration, MariaDB/Testcontainers, migrations, coverage, runtime-JAR inspection and Codacy without changing production persistence behavior.

ES-R02 extracts only that fixture repair. The explicit retention test still uses `NOW.minus(Duration.ofDays(9))`, so it remains outside the seven-day window. State transitions add only a few seconds to a value already about one minute in the past; generated times are not accidentally future. Duration/Instant comparisons avoid midnight/day-boundary dependence.

## ES-P05 preservation

PR #81 remains open and parked. ES-R02 does not merge it, synchronize it, modify its branch, rerun its canonical staging, copy report feature behavior, or delete it. The same fixture blob existing in both branches is intentional; a later ES-P05 worker will reconcile main normally.

## Cleanup

`noop-temp-ignore` has unique work relative to main and remains untouched.

## Resume rule

After ES-R02 is legitimately merged and the trusted public Java build is green on current main, the material condition required by ES-R01 has changed. The next worker must resume ES-R01 and obtain one fresh legitimate current-main proof through public Java 21 build → bounded verified transfer → corrected `published_at` / asset freshness verification → correlated private dispatch → trusted `Lincoln-PI-4` → guarded DB pre-reset → Paper cycle 1 → clean shutdown/reap → Paper cycle 2/restart-persistence → clean shutdown/reap → guarded final cleanup → sanitized evidence → correlated public success → transfer cleanup. Only then may ES-R01 become `COMPLETE`.
