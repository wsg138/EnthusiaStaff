# ES-P05 package handoff — ReportStore hosted-test repair, Pi temporarily unavailable

Recorded: 2026-08-09 America/Indiana/Indianapolis

## Package and routing

| Field | Value |
| --- | --- |
| Package | `ES-P05 — Report evidence and staff workflow completion` |
| Classification at terminal publication | `BLOCKED` / `PARKED_BLOCKED` |
| Repository | `wsg138/EnthusiaStaff` |
| Starting live `main` | `b0cf67b880856ec7536cf1385fe1559bb18a42a1` |
| Implementation branch | `package/es-p05-report-workflow` |
| Implementation PR | #81, open and unmerged |
| Current reviewed/hosted-validation head | `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2` |
| Main synchronization merge | `5d78a9621f7cc3e5f056b417af88424eaa26e555` |
| Migration boundary | immutable/current V18; no ES-P05 migration |
| External parity | not applicable; internal package |

Live GitHub and current default-branch source override this handoff if they diverge. This file records only ES-P05 and must not be used to activate another package.

## Why ES-P05 became actionable

The public hosted build on current repository state exposed two real failing report integration tests:

- `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()` — expected queue membership `true`, observed `false`;
- `ReportStoreIntegrationTest.duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()` — expected two retained evidence snapshots, observed zero.

Current package contracts, PR ownership, report persistence scope, and live PR history establish ES-P05 as the owner of duplicate report/evidence retention and report lifecycle/queue/revision behavior. PR #81 already contained the package implementation, so the worker resumed it instead of creating a competing repair package or PR.

## Confirmed root cause

The two failures shared one deterministic test-fixture cause.

`ReportIntegrationFixtures.NOW` was frozen at `2026-08-01T12:00:00Z`. Current `ReportPolicy.defaults()` retains report evidence for seven days and includes closed reports in `RECENTLY_CLOSED` for seven days. The production query/submission code correctly compares those timestamps against the real current database/system clock.

By 2026-08-09:

- evidence created from the frozen fixture had expired under the seven-day retention rule, so retained evidence queries correctly returned zero rows;
- closed reports using the same frozen fixture had aged out of the seven-day recently-closed window, so the lifecycle test correctly could not find the report in `RECENTLY_CLOSED`.

No defect was found in `JdbcReportStore`, duplicate merge/replay, lifecycle state changes, revision predicates, queue semantics, transaction boundaries, commit/rollback behavior, generated IDs, foreign-key behavior, or V18 schema assumptions for these symptoms.

The stale **test clock** was wrong; product persistence behavior was not changed to make the tests pass.

## Repair

`ReportIntegrationFixtures.NOW` now uses one current instant per test JVM:

```java
Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS)
```

The one-minute offset keeps generated state-change timestamps safely behind the real database clock while ordinary report/evidence timestamps remain comfortably inside both seven-day policy windows. Microsecond truncation matches the MariaDB timestamp precision used by this persistence path.

Existing expiry proof remains meaningful: the purge test still creates explicitly expired evidence with `NOW.minus(Duration.ofDays(9))`.

No assertions were weakened and no Flyway migration was modified.

## Main synchronization

The old ES-P05 product head was far behind current `main`, so it was not used as a merge candidate.

The implementation branch was synchronized through the normal two-parent merge commit `5d78a9621f7cc3e5f056b417af88424eaa26e555`, with previous ES-P05 head `4a38e191395913c6733726e222f0889a2d56d267` and current `main` `b0cf67b880856ec7536cf1385fe1559bb18a42a1` as parents. No rebase or force-push occurred.

Current mainline V18/staging/cheat-tester/fake-base work was preserved. The final PR diff against current `main` contains only the ES-P05 report Wiki, report evidence UI/permission/formatting/restart coverage, and the stale-clock test-fixture repair.

## Review findings resolved

A fresh CodeRabbit review found three valid issues in the resumed ES-P05 feature diff. All were fixed and all three threads were resolved:

1. sensitive evidence/coordinate authorization was previously captured before asynchronous work; delivery now rechecks `enthusiastaff.reports.evidence` on the global-region scheduler immediately before any sensitive output;
2. allow-listed client evidence fields previously serialized unexpected object/array values; rendering now accepts only textual/numeric/boolean scalars and withholds structured values;
3. chat timestamp/sender/recipient fields were not bounded consistently with message bodies; all rendered text fields now share the output bound, with oversized-identity regression coverage.

CodeRabbit commit status is green on the final head and zero valid unresolved review threads remain.

Sentinel is non-applicable to this candidate because the PR head has no `.enthusia-test.yml` manifest. No Sentinel result is claimed.

## Exact-head hosted validation

Final reviewed/hosted-validation head: `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2`.

- Validate Wiki run `31301427600`, job `93214726543`: **success**.
- Coverage run `31301427623`, job `93214731253`: **success**. The configured Java 21 build/tests, MariaDB/Testcontainers and migration validation, aggregate coverage, runtime-JAR creation/inspection, validation artifact upload, and Codacy coverage upload all completed successfully.
- Because the full integration suite succeeded on the exact final head, both originally failing `ReportStoreIntegrationTest` methods pass on that head.
- Codacy Static Code Analysis `93214975215`: **success**, zero issues.
- Codacy Diff Coverage `93215398455`: **success**, 47.37% diff coverage; repository gate not defined.
- CodeRabbit: green final-head commit status; three valid findings fixed; zero unresolved review threads.

An earlier post-clock-fix intermediate head `3dc27859f3f14d25524cc3a846b1ff717d88abdb` also passed Coverage run `31301067822` / job `93213845162`, independently confirming the stale-clock change removed the original ReportStore failures before the later review hardening. Only the final-head run is completion evidence.

A separate local focused Gradle/Testcontainers run is not claimed. The reset execution environment had no repository checkout and could not resolve GitHub, so executable proof came from the repository-configured exact-head hosted workflow.

## Canonical Pi staging state

Automatic public Pi Staging run `31301426684` is bound to exact source `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2`.

Public phase:

- public build job `93214729981`: **success**;
- `Validate source, build, and package Paper runtime`: success;
- exact verified runtime artifact upload: success;
- bridge job `93215481473` successfully downloaded the exact artifact, created the bounded transient transfer, dispatched the private workflow, and located the correlated private run.

Correlated private run:

- repository: `wsg138/EnthusiaStaff-Staging`;
- run: `31301734048`;
- deterministic title: `EnthusiaStaff bridge 31301426684-1 / ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2`;
- job: `93215499833`, `Verify bridge and boot/restart runtime on Lincoln-PI-4`;
- observed terminal-publication state: queued, `runner_id: 0`, empty runner name, zero executed steps.

A private run therefore **does exist**, unlike the earlier public-build failures. However, `Lincoln-PI-4` had not accepted the job when this worker stopped waiting under the owner's explicit maintenance/unavailability rule. No private prerequisite, database reset, Paper boot, plugin enablement, Flyway behavior, restart, persistence, process-reap, or cleanup assertion executed.

This is **temporary Pi runner/environment unavailability**. It is not a Pi product failure and it is not a canonical staging pass.

No duplicate canonical retry was issued and `plugin-live-test.yml` was not dispatched directly.

## Terminal package state

ES-P05 remains `BLOCKED` / `PARKED_BLOCKED` because its mandatory canonical Pi acceptance has not executed to a terminal success on the final implementation head.

The implementation branch and PR #81 must remain preserved. The hosted defect itself is fixed and fully green; no further ReportStore product change is justified from the original two failures.

Exact resume condition: material evidence that the existing `Lincoln-PI-4` self-hosted staging runner/environment is available to accept the canonical correlated job. A future worker must first reconcile live GitHub and inspect run `31301734048` and public run `31301426684`; if that exact run later completed, use its real terminal evidence rather than launching a duplicate. Only if a material environment/source change requires a new run should the normal automatic public Pi workflow be allowed to create fresh evidence.

If canonical staging succeeds for the exact merge candidate and all other gates remain current, merge PR #81 normally, verify containment, publish ES-P05 `COMPLETE`, clean the temporary branch, and stop. If staging exposes a real ES-P05 defect, fix that defect inside the same package and revalidate normally.

## Preserved exclusions

- no production infrastructure or credentials;
- no production database data or routes;
- no LiteBans authority change;
- no issue #43 activation;
- no V18-or-older migration rewrite or Flyway repair;
- RoseChat private-message provider capture remains ES-X01;
- Discord route delivery remains ES-P06;
- representative distributed/Java/Bedrock acceptance remains under its existing validation package boundaries.
