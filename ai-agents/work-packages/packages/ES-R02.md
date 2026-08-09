# `ES-R02` — Report integration fixture clock recovery

## 1. Package identity
`ES-R02`; internal repository baseline test-infrastructure recovery; primary `COMP-STAFF`; priority 16; no product-feature scope; independently safe; no dependencies; not parallel-safe with other edits to the Report integration fixture.

## 2. Status
`COMPLETE` as the terminal state published by the exact validated PR #103 when that PR merges normally. Before merge, live GitHub remains authoritative and the branch is only a completion candidate.

## 3. Objective
Restore deterministic current-main ReportStore integration validation so the canonical trusted Java build can execute again. This package exists only to repair the integration fixture clock; it makes no production ReportStore behavior claim and no ES-P05 completion claim.

## 4. Confirmed root cause
The starting `main` inherited `ReportIntegrationFixtures.NOW = Instant.parse("2026-08-01T12:00:00Z")`. Default report policy uses seven-day recently-closed and evidence-retention windows, while production reads use live `Clock.systemUTC()` / MariaDB `CURRENT_TIMESTAMP(6)`. As wall time advanced, the fixed fixture aged outside those live windows and caused exactly:

- `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()`;
- `ReportStoreIntegrationTest.duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()`.

ES-P05 PR #81 already proved the narrow repair by computing one JVM-wide timestamp from current time minus 60 seconds and truncating it to microsecond precision; its exact hosted-green head passed the full Java 21/ReportStore/MariaDB/migration/runtime-JAR/Codacy suite without production persistence changes.

## 5. Included scope
Only the minimum test-infrastructure repair in `integration-tests/src/test/java/net/enthusia/staff/integration/ReportIntegrationFixtures.java`, plus this package's canonical state/handoff records.

## 6. Explicit exclusions
No `JdbcReportStore`/query/transaction/schema behavior; no retention-policy change; no Report commands, permissions, GUI, formatter, feature behavior, Flyway/migration, staging workflow, production data/infrastructure, LiteBans authority, issue #43, ES-P05 branch synchronization, ES-P05 staging rerun, ES-P05 merge, or other product package work.

## 7. Implementation
The implementation file now:

- imports `java.time.temporal.ChronoUnit`;
- computes `NOW` exactly once per test JVM as `Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS)`;
- preserves every assertion and all other fixture behavior.

The resulting fixture blob is byte-identical to the already-proven repair in ES-P05 head `346e764f40b25c98e7d24ce7f863e5629773e814`.

## 8. Time-flakiness review
- `NOW` is one static value, not recomputed for assertions or individual operations.
- The one-minute offset keeps generated timestamps in the past; report state transitions add only a few seconds.
- Microsecond truncation matches MariaDB `CURRENT_TIMESTAMP(6)` precision.
- `expiredEvidenceIsPhysicallyPurgedInBoundedBatches()` still uses `NOW.minus(Duration.ofDays(9))`, safely outside the seven-day retention window.
- Recently-closed and evidence checks are `Instant`/`Duration` based, not calendar-day or midnight based.
- No assertion, policy boundary, production query, or purge behavior was weakened.

## 9. Validation requirements and evidence location
The exact completion candidate must pass the two formerly failing methods, complete ReportStore integration suite, full Java 21 build/tests, MariaDB/Testcontainers, migration validation with V18 unchanged, runtime-JAR inspection, Coverage, Codacy/static analysis, and zero valid unresolved review threads. Exact final-head run/job/status identifiers are recorded on PR #103 after the head is frozen rather than creating a self-referential tracked-file validation loop.

Canonical Pi runtime acceptance is not claimed by ES-R02. Automatic Pi evidence may be observed and recorded truthfully, but full public→private staging acceptance remains ES-R01's package boundary.

## 10. Branch and PR
Starting main: `15d37fa1e49b5d4b8403914b6f7a43892dbe417e`.
Branch: `package/es-r02-report-fixture-clock-recovery`.
PR: #103, `test(reports): keep integration fixtures inside live policy windows`.
Merge method: normal merge commit only; no squash, rebase, force-push, or auto-merge.

## 11. Completion definition
The terminal `COMPLETE` publication in this branch becomes true only when the exact final PR head satisfies every applicable hosted/review/static gate, PR #103 merges normally, current `main` contains the fixture repair, containment is exact, and no ES-P05 feature code entered main. The worker then stops.

## 12. Routing after completion
ES-R01 remains a distinct package. A successful ES-R02 merge materially changes ES-R01's current-main trusted-build unblock condition; the next worker must resume `ES-R01 — Billing-independent staging bridge recovery` and obtain one fresh exact-current-main full canonical proof before ES-R01 may become `COMPLETE`.

Do not resume ES-P05, ES-P02, ES-P06, or ES-X01 first.

## 13. Migration/authority boundary
V18 and older migrations remain immutable. LiteBans remains authoritative. Issue #43 remains untouched. `noop-temp-ignore` remains retained because it has unique work relative to main.

## 14. Canonical handoff
`ai-agents/reports/package-handoffs/2026-08-09-es-r02-report-fixture-clock-recovery.md`
