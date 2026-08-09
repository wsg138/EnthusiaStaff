# `ES-R02` — Report integration fixture clock recovery

## 1. Package identity
`ES-R02`; internal repository baseline test-infrastructure recovery; primary `COMP-STAFF`; priority 16; no product-feature scope; independently safe; no dependencies; not parallel-safe with other edits to the Report integration fixture.

## 2. Status
`ACTIVE` under explicit owner direction on 2026-08-09.

## 3. Objective
Restore deterministic current-main ReportStore integration validation so the canonical trusted Java build can execute again. This package exists only to repair the integration fixture clock; it makes no production ReportStore behavior claim and no ES-P05 completion claim.

## 4. Confirmed root cause
Current `main` inherited `ReportIntegrationFixtures.NOW = Instant.parse("2026-08-01T12:00:00Z")`. Default report policy uses seven-day recently-closed and evidence-retention windows, while production reads use live `Clock.systemUTC()` / MariaDB `CURRENT_TIMESTAMP(6)`. As wall time advanced, the fixed fixture aged outside those live windows and caused two ReportStore integration failures. ES-P05 PR #81 already proves the narrow repair by computing one JVM-wide timestamp from current time minus 60 seconds and truncating it to microsecond precision; its full hosted Java 21/MariaDB/migration/runtime-JAR suite passed without production persistence changes.

## 5. Included scope
Only the minimum test-infrastructure repair in `integration-tests/src/test/java/net/enthusia/staff/integration/ReportIntegrationFixtures.java`, plus this package's canonical state/handoff records.

## 6. Explicit exclusions
No `JdbcReportStore`/query/transaction/schema behavior; no retention-policy change; no Report commands, permissions, GUI, formatter, feature behavior, Flyway/migration, staging workflow, production data/infrastructure, LiteBans authority, issue #43, ES-P05 branch synchronization, ES-P05 staging rerun, or ES-P05 merge.

## 7. Required implementation
- import `java.time.temporal.ChronoUnit`;
- compute `NOW` exactly once per test JVM as current time minus approximately one minute;
- truncate to `ChronoUnit.MICROS`;
- preserve genuinely old/expired fixtures and all assertions.

## 8. Validation requirements
Freeze the exact candidate head and require: the two previously failing ReportStore methods pass; complete ReportStore integration suite passes; full Java 21 build/tests pass; MariaDB/Testcontainers passes; migration validation passes with V18 unchanged; runtime-JAR inspection passes; Coverage passes; Codacy/static analysis passes; zero valid unresolved review threads. Review specifically for time flakiness, future timestamps, MariaDB precision, expiry-window integrity, and day-boundary dependence.

Canonical Pi runtime acceptance is not claimed by this package. If automatic post-merge current-main Pi proceeds, record it truthfully without collapsing the ES-R01 boundary.

## 9. Branch and PR
Branch: `package/es-r02-report-fixture-clock-recovery` from starting main `15d37fa1e49b5d4b8403914b6f7a43892dbe417e`.
PR: to be opened against `main` after the coherent checkpoint.

## 10. Completion definition
`COMPLETE` only after the narrow repair PR passes all applicable exact-head hosted/review/static gates, merges normally, exact containment is verified, current `main` contains the fixture repair and no ES-P05 feature code, and canonical state is published. Stop immediately afterward; the next worker resumes ES-R01 for one fresh exact-current-main canonical public→private proof.

## 11. Migration/authority boundary
V18 and older migrations remain immutable. LiteBans remains authoritative. Issue #43 remains untouched.

## 12. Canonical handoff
`ai-agents/reports/package-handoffs/2026-08-09-es-r02-report-fixture-clock-recovery.md`
