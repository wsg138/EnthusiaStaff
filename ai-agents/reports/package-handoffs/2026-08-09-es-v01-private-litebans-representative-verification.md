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
- the migration integration fixture omits sanction-table name columns and, after review, covers both the retained IP-only ban path and UUID-backed ban/mute paths.

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

`gradlew --no-configuration-cache clean build` compiled the Java 21 projects but could not execute the Testcontainers integration suite on this workstation: Docker is unavailable/misconfigured before container startup, so 46 container-backed tests failed at Testcontainers initialization rather than at product assertions. This is not a product defect; hosted Java 21/Testcontainers validation is authoritative where the package policy permits it.

PR `#110` on `package/es-v01-litebans-private-verification` reached pre-review exact head `2485c8b7a4a80ae306216eb9f66f1e9415d9eac0`, where Coverage `31351570626`, canonical Pi Staging `31351570636`, Codacy, and the hosted Java 21/Testcontainers suite passed. Substantive review then found valid documentation consistency findings and a missing UUID-backed ban integration fixture. The review-fix commit intentionally advances the PR head, so all invalidated gates must rerun on the resulting frozen exact head; no validation from `2485c8b7a4a80ae306216eb9f66f1e9415d9eac0` may be reused to merge the changed head.

Still required: freeze the post-review-fix PR head; rerun all invalidated hosted build/test/static/canonical staging gates; resolve every valid review thread; require zero valid unresolved findings; normal merge; containment; branch cleanup; and terminal package-state publication. Private local success is not a production shadow, cutover, issue #43 acceptance, or 168-hour authority result.

## Exact next action

Continue existing PR `#110` on branch `package/es-v01-litebans-private-verification`; do not create another package PR. Freeze its live head after these review fixes, rerun every invalidated exact-head gate, inspect and resolve all substantive review findings, and merge normally only if the frozen head remains unchanged and every required gate passes. Otherwise publish the precise `PARTIAL`/`BLOCKED` state without altering the private source data.
