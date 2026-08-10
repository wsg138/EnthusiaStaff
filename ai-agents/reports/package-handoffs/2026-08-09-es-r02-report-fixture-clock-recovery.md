# ES-R02 handoff — Report integration fixture clock recovery

Date: 2026-08-09
Package: `ES-R02 — Report integration fixture clock recovery`
Owner-directed classification: baseline-build deadlock recovery
Terminal publication state: `COMPLETE` when the exact validated PR #103 merges normally; before merge, live GitHub remains authoritative and this branch is a completion candidate.

## Starting state

- Starting `wsg138/EnthusiaStaff:main`: `15d37fa1e49b5d4b8403914b6f7a43892dbe417e`, the normal merge of ES-R01 terminal publication PR #102.
- PR #102 frozen head: `369a71c84a4fcb1c09cfd43cf32b323ffbc0fbb6`; it was documentation/canonical-state only and merged normally as `15d37fa1e49b5d4b8403914b6f7a43892dbe417e`.
- ES-R01 remains `BLOCKED` / `PARKED_BLOCKED` during this package because current main cannot pass the trusted public Java build.
- ES-P05 PR #81 remains open, unmerged, and intentionally untouched at `346e764f40b25c98e7d24ce7f863e5629773e814`.
- ES-P02 PR #70 remains parked and untouched.
- `noop-temp-ignore` remains untouched because it contains unique work relative to main.

## Circular dependency confirmed

ES-R01 cannot obtain its fresh exact-current-main canonical proof until current main passes the trusted public Java build. Current main fails two ReportStore integration tests. The confirmed test-infrastructure repair already exists inside unmerged ES-P05 PR #81, but ES-P05 cannot legitimately merge until the shared canonical staging path is proven through ES-R01. ES-R02 exists solely to extract the independently valid fixture repair, restore the baseline build, and stop without advancing ES-P05 feature scope.

## Exact failures and root cause

Failing current-main methods:

- `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()`;
- `ReportStoreIntegrationTest.duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()`.

Starting-main fixture uses `Instant.parse("2026-08-01T12:00:00Z")`. `ReportPolicy.defaults()` uses seven-day `recentlyClosedWindow` and seven-day `evidenceRetention`. Production query paths correctly use live `Clock.systemUTC()` and MariaDB `CURRENT_TIMESTAMP(6)`. As wall time advanced, the fixed fixture aged outside those live windows.

PR #81 already contains and hosted-validates the narrow repair: one JVM-wide `Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS)`. Its exact hosted-green candidate passed the full Java 21 build/tests, ReportStore integration, MariaDB/Testcontainers, migration validation, coverage, runtime-JAR inspection and Codacy without changing production ReportStore/query/transaction/schema behavior or weakening assertions.

## Implementation

Implementation change is limited to:

`integration-tests/src/test/java/net/enthusia/staff/integration/ReportIntegrationFixtures.java`

- add `java.time.temporal.ChronoUnit`;
- replace the historical fixed `NOW` with `Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS)`;
- leave all other fixture behavior and assertions unchanged.

The resulting fixture blob is byte-identical to the same file on ES-P05 head `346e764f40b25c98e7d24ce7f863e5629773e814`.

## Time-flakiness review

- `NOW` is computed once as a static final value, not independently per assertion or operation.
- The one-minute subtraction places the fixture safely in the recent past; the lifecycle test adds only a few seconds for revisions, so generated timestamps remain in the past.
- Microsecond truncation matches MariaDB `CURRENT_TIMESTAMP(6)` precision.
- `expiredEvidenceIsPhysicallyPurgedInBoundedBatches()` still creates its intentionally expired fixture with `NOW.minus(Duration.ofDays(9))`, safely beyond the seven-day policy window.
- Recently-closed/evidence logic is based on `Instant`/`Duration`, not calendar dates, so midnight/day rollover does not change intended semantics.
- Assertions, production policy, purge behavior, production report queries and transaction/schema behavior remain unchanged.

## Scope and boundaries

No ES-P05 feature file is copied. No `JdbcReportStore`, query, transaction, schema, report command, formatter, permission, GUI, Flyway migration, staging workflow, production data/infrastructure, issue #43 behavior, or LiteBans authority is changed. PR #81 is not synchronized, modified, rerun through canonical staging, merged, or deleted.

V18 remains current and immutable. `noop-temp-ignore` remains retained.

## Branch and PR

- Branch: `package/es-r02-report-fixture-clock-recovery`.
- PR: #103, `test(reports): keep integration fixtures inside live policy windows`.
- Merge policy: normal merge commit only; no squash, rebase, force-push or auto-merge.

## Required exact-head validation

The frozen completion candidate must prove:

1. both previously failing ReportStore methods pass;
2. complete ReportStore integration suite passes;
3. full Java 21 build/tests pass;
4. MariaDB/Testcontainers validation passes;
5. migration validation passes and V18 is unchanged;
6. runtime-JAR inspection passes;
7. Coverage passes;
8. Codacy/static analysis passes;
9. zero valid unresolved review threads;
10. no valid CodeRabbit/human review finding remains.

Exact final-head run/job/status identifiers are recorded in PR #103 after the head is frozen. This avoids creating a self-referential tracked-file validation loop.

Canonical Pi runtime acceptance is not an ES-R02 completion claim. If the automatic PR/main workflow proceeds into the repaired bridge, its evidence is recorded truthfully, but the full current-main public→private staging acceptance remains ES-R01's package boundary.

## Containment and terminal condition

The terminal `COMPLETE` publication in this branch becomes authoritative only after the exact final PR head passes every applicable hosted/review/static gate, PR #103 merges normally, the resulting main contains the fixture repair, and compare/containment proves no ES-P05 product behavior entered main. Repository `delete_branch_on_merge` may clean the temporary branch only after containment is safe.

## Exact next worker action

Once the ES-R02 merge makes current main genuinely green in the two ReportStore tests, ES-R01's exact unblock condition has materially changed. The next worker must resume:

`ES-R01 — Billing-independent staging bridge recovery`

and obtain one fresh exact-current-main proof through public Java build → bounded transient transfer → corrected Release `published_at` / asset freshness verification → trusted `Lincoln-PI-4` → guarded DB pre-reset → Paper cycle 1 → clean shutdown/reap → Paper cycle 2 → restart/persistence → clean shutdown/reap → final DB cleanup → sanitized evidence → public correlation success → transfer cleanup.

Do not start ES-P05, ES-P02, ES-P06 or ES-X01 first. Only after that ES-R01 proof succeeds may a later worker resume ES-P05.
