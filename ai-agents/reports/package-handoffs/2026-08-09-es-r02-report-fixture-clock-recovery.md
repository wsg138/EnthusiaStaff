# ES-R02 handoff — Report integration fixture clock recovery

Date: 2026-08-09
Package: `ES-R02 — Report integration fixture clock recovery`
Owner-directed classification: baseline-build deadlock recovery
Current state: `ACTIVE`

## Starting state

- Starting `wsg138/EnthusiaStaff:main`: `15d37fa1e49b5d4b8403914b6f7a43892dbe417e` after normal merge of ES-R01 terminal publication PR #102.
- ES-R01 remains `BLOCKED` / `PARKED_BLOCKED` only because current main cannot pass the trusted public Java build.
- ES-P05 PR #81 remains open, unmerged, and intentionally untouched at `346e764f40b25c98e7d24ce7f863e5629773e814`.
- ES-P02 PR #70 remains parked and untouched.
- `noop-temp-ignore` remains untouched because it contains unique work relative to main.

## Circular dependency confirmed

ES-R01 requires a current-main canonical trusted public Java build before bridge/Pi acceptance can execute. Current main fails two ReportStore integration tests. The already-proven fixture repair exists inside unmerged ES-P05 PR #81, while ES-P05 itself cannot legitimately merge before the repaired shared staging path is proven by ES-R01. This package exists solely to extract the independently valid test-infrastructure repair and restore the baseline build.

## Exact failures and root cause

Failing current-main methods:
- `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()`;
- `ReportStoreIntegrationTest.duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()`.

Current-main fixture uses `Instant.parse("2026-08-01T12:00:00Z")`. Default report policy uses seven-day `recentlyClosedWindow` and seven-day `evidenceRetention`. Production query paths use live `Clock.systemUTC()` and MariaDB `CURRENT_TIMESTAMP(6)`. The fixed fixture therefore ages out as calendar time advances.

PR #81 already contains and hosted-validates the repair: one JVM-wide `Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS)`. No production ReportStore/query/transaction/schema behavior changed to make its full hosted suite pass.

## Scope and boundaries

Only `integration-tests/src/test/java/net/enthusia/staff/integration/ReportIntegrationFixtures.java` may change for the implementation. Package-state files may change only to route and publish ES-R02. No ES-P05 branch synchronization, staging rerun, product feature copy, production persistence change, retention weakening, migration change, issue #43 action, LiteBans change, or production infrastructure/data access is authorized.

## Required validation

Exact-head hosted proof must cover the two formerly failing methods, the complete ReportStore integration suite, full Java 21 build/tests, MariaDB/Testcontainers, migrations, V18 unchanged, runtime-JAR integrity, Coverage, static analysis/Codacy, and zero valid unresolved review threads. Review must confirm one-time clock computation, no future timestamps, microsecond precision, genuine old fixtures remain expired, and no midnight/day-boundary dependency.

## Exact next action

Apply the already-proven fixture repair only, publish ES-R02 routing, open one narrow PR, validate the frozen head, merge normally if green, verify containment/no ES-P05 feature leakage, publish `COMPLETE`, recompute routing so ES-R01 becomes the exact next actionable continuation, and stop.
