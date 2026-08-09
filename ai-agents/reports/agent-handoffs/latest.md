# Latest AI handoff

Current package handoff:

[`2026-08-09-es-r02-report-fixture-clock-recovery.md`](../package-handoffs/2026-08-09-es-r02-report-fixture-clock-recovery.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-R02 — Report integration fixture clock recovery` is the explicit owner-directed baseline-build deadlock recovery package. Its terminal `COMPLETE` state is published by exact validated PR #103 when that PR merges normally; before merge, live GitHub remains authoritative.

Starting `main` for ES-R02 is `15d37fa1e49b5d4b8403914b6f7a43892dbe417e`, the normal merge of ES-R01 terminal publication PR #102. ES-R01 remains a separate `BLOCKED` / `PARKED_BLOCKED` package during this worker because current main cannot yet pass the trusted public Java build.

The circular dependency is confirmed: current main fails `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()` and `duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()` because `ReportIntegrationFixtures.NOW` is fixed at `2026-08-01T12:00:00Z`, while default recently-closed/evidence-retention windows are seven days and production reads correctly use live UTC/database time. The fixture therefore aged outside the live policy window.

ES-P05 PR #81 already proves the independent repair: one JVM-wide `Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS)`. ES-R02 copies only that test-infrastructure semantic change into current-main lineage. It does not copy ES-P05 report commands, formatter, permission, GUI, Wiki, product behavior, persistence changes, migrations, or staging behavior.

PR #103 changes the integration fixture plus the canonical ES-R02 package/routing records. Assertions remain unchanged. The explicit retention fixture remains nine days old against the seven-day policy. `NOW` is computed once, remains slightly in the past, is truncated to MariaDB-compatible microsecond precision, and does not depend on midnight/day rollover.

ES-P05 PR #81 remains open, unmerged, unsynchronized and otherwise untouched at `346e764f40b25c98e7d24ce7f863e5629773e814`. ES-P02 PR #70 remains parked and untouched. V18 remains immutable/current; issue #43 remains deferred; LiteBans remains authoritative; `noop-temp-ignore` remains retained because it contains unique work.

After PR #103 is legitimately hosted-green and merged normally, current-main trusted-build success materially changes ES-R01's exact unblock condition. The next worker must resume ES-R01 first and obtain one fresh exact-current-main canonical public→private provenance/guarded-DB/two-cycle-Paper/restart-persistence/cleanup proof. Do not start ES-P05, ES-P02, ES-P06 or ES-X01 first.
