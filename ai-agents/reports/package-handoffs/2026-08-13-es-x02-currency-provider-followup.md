# ES-X02 — currency provider correctness follow-up

Date: 2026-08-13
Status: `ACTIVE` / `ACTIONABLE_CONTINUATION`

## Trigger

Staff PR #133 and terminal-state PR #135 merged normally for the previously reviewed Currency tree. A later targeted review found two valid fail-closed ordering defects:

1. `applyRemoval` could accept a caller-supplied replacement checksum/final total as an already-committed replay before recalculating and validating the supplied plan.
2. `restore` could report an unchanged before-state as already restored solely because its assets matched, despite the API contract requiring a new monotonic bank revision.

These are real financial recovery defects, not analyzer false positives. No rule, issue, or first-party source path was suppressed.

## Standalone correction

- Currency commit: `fd5ea106f4dc27160810b96a82059bc282cdf3f1`.
- Currency PR #14 merged normally as `2b4c8bf6d8e8ef1c8c6b042cd3147e66ffc660fe`.
- The service now validates the exact provider calculation before committed-replay classification.
- Already-restored classification now requires matching assets and `current.bankRevision() > requested.bankRevision()`; an unchanged before-state must satisfy expected-state fencing and perform a fresh revision-bumping restore.
- Four direct regression tests cover invalid committed replay, valid idempotent replay, unchanged before-state rejection, and advanced idempotent restore.

## Validation completed

- Standalone Java 21 `mvn -B -ntp verify`: 11 tests, zero failures/errors/skips, shaded runtime JAR produced.
- Standalone hosted CI: two verify jobs passed.
- Standalone Codacy: up to standards, zero new issues.
- Focused PMD 7.26.0 and threshold-matched Lizard 1.23.0: zero findings.
- Full standalone Opengrep found one pre-existing unrelated leaderboard result and no result in the changed files; it remains visible and unsuppressed.
- CodeRabbit was rate-limited and produced no review; zero GitHub review threads exist. No CodeRabbit approval is claimed.
- Exact candidate component parity: aggregate hash = standalone hash = `c5820e3121372f81c8611de9b6015f77e28f5c2160037da035f650660ed090eb`; no added, missing, or modified product files.
- Aggregate component Java 21 Maven verification: 11 tests, zero failures/errors/skips, shaded runtime JAR produced.
- Staff Java 21 clean task graph completed with Gradle daemon exit status 0: 218 suites / 936 tests, zero failures/errors/skips. The Docker-backed MariaDB portion executed 48 suites / 189 tests. Aggregate JaCoCo XML and both runtime JARs were produced.
- Focused aggregate PMD 7.26.0 and threshold-matched Lizard 1.23.0: zero findings.

The local full-build client timed out at ten minutes while the WSL Gradle process continued. Completion is supported by final XML counts, generated JaCoCo/runtime artifacts, and the Gradle daemon log recording `Runtime.exit(0)`; the timeout itself is not mislabeled as a passing command result.

## Current repository state

- Prior Staff product merge: PR #133 -> `a3b6f2f7c1e9f6b7fe1667974aa0d050533605a9`.
- Prior completion publication: PR #135 -> `0c34478db01cfc9f6f181e47d9fe055e0df84f19`.
- Reopened branch: `package/es-x02-currency-provider`.
- Corrected standalone source of truth: `2b4c8bf6d8e8ef1c8c6b042cd3147e66ffc660fe`.
- Component state: `SYNC_PENDING`; aggregate-main SHA remains unset until the follow-up merges and post-merge parity passes.

## Remaining gates

1. Complete the normal merge of current Staff `main` into the reopened package branch and publish this corrected state.
2. Open the follow-up Staff PR.
3. Pass exact-head hosted build/coverage, Codacy, review-thread, Sentinel artifact, and canonical Pi staging gates required by the executable component delta.
4. Merge normally; do not squash, rebase, force-push, or enable auto-merge.
5. Verify feature-head containment and post-merge parity against Currency `2b4c8bf...`.
6. Update component metadata with the resulting aggregate-main SHA/hash and republish a truthful terminal handoff.

ES-X03 and ES-X04 remain parked until these ES-X02 gates pass. Representative destructive/latency/load acceptance remains assigned to ES-V03. No production balance, private database, deployment, cutover, or authority change occurred.
