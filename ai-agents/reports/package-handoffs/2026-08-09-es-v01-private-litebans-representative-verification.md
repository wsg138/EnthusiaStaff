# ES-V01 private LiteBans representative-data verification

Date: 2026-08-09

## Scope and starting point

Selected package: `ES-V01 — Private LiteBans representative-data verification`.

Starting `main`: `b78a62de3876bfde7fa5f57860fedc1415ef3c53`. ES-P05 is `COMPLETE`. ES-V01 is `PARTIAL` / `ACTIONABLE_CONTINUATION`; ES-P07, ES-P06, and ES-X01 were not activated.

The private LiteBans dump remained local. No rows, addresses, credentials, database artifacts, or reconstructable output entered Git, GitHub, CI, or this handoff.

## Product repair

Reviewed local commit `22934e33` and reproduced only its four legitimate ES-V01 changes as product head `ea07f55a` on `package/es-v01-litebans-private-verification`:

- sanction-table usernames are optional when LiteBans supplies a UUID;
- the reader selects a null username when no name column exists;
- synthetic schema coverage verifies UUID-only resolution;
- the migration integration fixture omits sanction-table name columns.

Malformed-source rejection behavior was not weakened. Flyway migrations were not changed. The unrelated untracked `CutoverEvidenceReader.java` in the original worktree was not included.

## Sanitized private execution

The source was a private MariaDB 10.11.6 LiteBans copy with the `litebans_` prefix. Aggregate inspection covered 102 bans, 53 mutes, and 1,747 history rows. On disposable local source/target databases at the reproduced head:

- dry-run found 153 supported sanctions and 7 explicit rejections;
- import created 153 mappings/cases; replay created no duplicate cases or events;
- issue and expiration values matched for mapped bans and mutes;
- abandoned `RUNNING` migration recovery marked the abandoned run failed, then replayed safely;
- warnings and kicks remain intentionally audit-only/unsupported.

The seven rejected rows are unchanged pre-rehearsal data-policy input: 2 `INVALID_SOURCE_ROW` and 5 `INVALID_HISTORY_ROW`. They were neither repaired nor removed. Existing source validation already rejects them; no additional repository behavior is required. A production rehearsal must decide whether to correct source data or retain each durable rejection through the documented operator process.

## Validation and remaining gates

Passed locally: focused persistence migration tests; synthetic UUID-only regression compilation; disposable representative dry-run/import/replay/recovery probe. The portable local database process was stopped after the run.

`gradlew --no-configuration-cache clean build` compiled the Java 21 projects but could not execute the Testcontainers integration suite on this workstation: Docker is unavailable/misconfigured before container startup, so 46 container-backed tests failed at Testcontainers initialization rather than at product assertions. This is not a passing full-build result and requires exact-head hosted container validation.

Still required: full exact-head Java 21 hosted build/test/static checks, relevant Testcontainers integration execution, PR review with zero valid unresolved findings, normal merge, containment, and branch cleanup. Private local success is not a production shadow, cutover, issue #43 acceptance, or 168-hour authority result.

## Exact next action

Push this package branch, open the single ES-V01 draft PR, run/await exact-head hosted validation and review, and merge normally only if every remaining gate passes. Otherwise publish the precise `PARTIAL`/`BLOCKED` state without altering the private source data.
