# ES-R01 — Staging-only database repair and fresh reblock

## Status

`BLOCKED` / `PARKED_BLOCKED` on 2026-08-09. This document supersedes only the old "MariaDB endpoint unavailable" classification. It does not claim canonical Pi staging success.

## Staging-only repair

- Pi diagnosis proved that no MariaDB server/service/socket/listener existed; the guarded pre-reset had correctly failed before Paper boot with earlier SQLState `08000` connection evidence.
- MariaDB server was installed on `Lincoln-PI-4` for the dedicated staging boundary only and is active with loopback-only TCP binding. The dedicated disposable schema `enthusiastaff_pi_staging` and restricted local staging account were provisioned. No production database, player data, or production credential was accessed or changed.
- The database-name guard, reset requirement, artifact provenance rules, and private runner boundary were not weakened.

## Fresh canonical evidence

- Canonical public Pi Staging run `31298080632` used current `main` `3ce303ce3097be647091e142e801da9a5fd9a8fc`.
- Its required hosted build job `93206301028` failed before bridge dispatch or Pi execution. `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()` expected `true` but was `false`; `duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()` expected `2` but was `0`. The build reported 177 tests completed and 2 failed.
- A private canonical harness retry `31298391766` did not use a manually supplied artifact; it correctly rejected the earlier bounded transient GitHub release asset with HTTP 404 after expiration. It did not reset the database or start Paper.

## Exact resume condition

The owning product work must restore a successful exact-current-head hosted build. Then ES-R01 resumes with a new exact-SHA artifact and requires the full canonical path: public build, trusted bridge correlation, guarded staging database pre-reset, Paper cycle 1 with MariaDB/Flyway behavior, clean shutdown, Paper cycle 2 with persistence assertions, cleanup, and sanitized evidence. Until then, **ENTHUSIASTAFF CANONICAL PI STAGING IS NOT PASSING**.
