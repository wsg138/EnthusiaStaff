# `ES-P02` — Runtime database recovery and Velocity reload

## 1. Package identity
`ES-P02`; internal; primary component `COMP-STAFF`; priority 20; not parallel-safe around lifecycle/configuration.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED`.

The product implementation is complete and the branch is synchronized with current `main`. Exact synchronized validation head `d671fef9fd14f0c4ae711c83edb29bc9b08ea002` passed every executable hosted gate, Codacy, CodeRabbit, and review-thread reconciliation. Merge is blocked only because the required private staging repository could not start its ordinary `ubuntu-latest` build job: GitHub reported failed account payments or an insufficient Actions spending limit. The job received `runner_id: 0`, an empty runner name, and zero steps on both attempts. The Pi boot/restart job was therefore skipped.

This is infrastructure-unavailable evidence, not a product failure and not a pass. Repository policy explicitly prohibits treating a missing ordinary GitHub-hosted build as an owner-approved infrastructure exception.

## 3. Objective
Recover from transient Paper/Velocity database bootstrap failures without process restart and provide safe Velocity configuration reload.

## 4. Included behavior completed
- Bounded Paper and Velocity bootstrap retry/recovery.
- Cleanup-before-retry and deterministic shutdown.
- Correct worker/global/entity scheduler separation and stale-callback fencing.
- Atomic Velocity reload with validation, rollback, authorization, and restart-required reporting.
- Sanitized health and operator feedback.
- Focused lifecycle, rejection, cleanup, retry, exhaustion, reload, rollback, shutdown, and race tests.
- Operator documentation in `docs/runtime-database-recovery.md`.

## 5. Explicit exclusions preserved
No production database or private-data access; no Flyway repair/history rewrite; no deployment; no authority activation; no issue #43 acceptance; no shadow period, production migration, cutover, or rollback; no provider invention; no ES-X05 work; no second package. LiteBans remains authoritative.

## 6. Dependencies
`ES-P01` is `COMPLETE`.

## 7. Branch and PR
- Branch: `package/es-p02-runtime-db-recovery`.
- PR: #70 to `wsg138/EnthusiaStaff:main`.
- Starting base: `d94d0219a598c9afb7e19c4ea9fddafd554d6469`.
- Current synchronized base: `9b1aac2677049ccc71dbddd963831f270c73dcd0`.
- Synchronization merge commit: `b21cb81b81fdcf0bac5027ae6f6b7901f6b0c175`.
- Exact hosted-validation head: `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`.
- Paper and Velocity product files merged with current `main` without conflicts. Only package-governance records required resolution; current `main` remained authoritative for global rules and state.

## 8. Exact-head hosted validation
Coverage workflow run `31138550369`, job `92743341861`, for exact head `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`: `SUCCESS`.

The job used Java 21 and successfully completed:
- clean build and all configured tests;
- MariaDB/Testcontainers and migration-integrity checks;
- aggregate JaCoCo generation and changed-code coverage enforcement;
- Paper/Velocity runtime-JAR creation and integrity inspection;
- provider/API leak inspection;
- validation artifact upload;
- Codacy coverage upload.

Artifact `java-21-validation`: ID `8979036747`; digest `sha256:18810296fc08695fb5d5f8497f008052161b7a0b0536fc898d27a22d69f65d70`.

Additional exact-head evidence:
- Codacy Static Code Analysis: `SUCCESS`, zero issues/annotations.
- CodeRabbit: `SUCCESS`.
- Valid unresolved review threads: zero; all three historical threads are resolved.

## 9. Blocking staging evidence
Parent workflow run `31138550480`, job `92743298476`, dispatched private staging run `31138555091` in `wsg138/EnthusiaStaff-Staging` for exact source head `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`.

Attempt 1:
- ordinary build job `92743314720`, label `ubuntu-latest`: `FAILURE`;
- `runner_id: 0`, empty runner name, `steps: []`;
- Pi job `92743321663`: `SKIPPED`.

Attempt 2, after one direct rerun:
- ordinary build job `92743621264`, label `ubuntu-latest`: `FAILURE`;
- `runner_id: 0`, empty runner name, `steps: []`;
- Pi job `92743627928`: `SKIPPED`.

GitHub check-run annotation for attempt 2 states: the job was not started because recent account payments failed or the spending limit must be increased, and directs the owner to **Billing & plans**. No product build, test, artifact inspection, Pi boot, or restart step executed in either attempt.

## 10. Exact unblock condition
The repository owner must resolve the GitHub Actions billing/payment or spending-limit restriction affecting the private `wsg138/EnthusiaStaff-Staging` repository. A resumed worker must then:

1. reconcile current `main` and the preserved branch;
2. freeze a current exact PR head;
3. rerun all applicable exact-head hosted gates;
4. obtain a successful ordinary private-staging build and successful Pi build/boot/restart result;
5. confirm Codacy, CodeRabbit, mergeability, and zero valid unresolved review findings;
6. merge normally, verify containment/divergence, update all records, clean the branch when safe, and stop.

Do not use an infrastructure exception for the missing ordinary hosted build, do not relabel either staging attempt as passed, and do not modify product code unless a newly confirmed defect is found.

## 11. Migration and compatibility boundaries
No migration was added. V16 remains the highest migration introduced by this package lineage and all existing migrations remain immutable. No Floodgate identity or player-facing protocol contract changed.

## 12. Handoff
[`2026-08-06-es-p02-resume-validation.md`](../../reports/package-handoffs/2026-08-06-es-p02-resume-validation.md)
