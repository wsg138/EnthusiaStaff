# ES-X02 — currency provider correctness follow-up

Date: 2026-08-13
Status: `COMPLETE`

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

## Final repository state

- Prior Staff product merge: PR #133 -> `a3b6f2f7c1e9f6b7fe1667974aa0d050533605a9`.
- Prior completion publication: PR #135 -> `0c34478db01cfc9f6f181e47d9fe055e0df84f19`.
- Corrective Staff frozen head: `88bd314da7224a64e6912ab2faa76f9548180584`.
- Corrective Staff PR #137 merged normally as `2150ac1d01849bd67ee97478f64cbcba31e5dc7f` with parents `f887ee17e13d4cab6a063510766fac08889f15df` and `88bd314da7224a64e6912ab2faa76f9548180584`.
- Feature-head containment is exact and the remote `package/es-x02-currency-provider` branch is deleted.
- Corrected standalone source of truth: `2b4c8bf6d8e8ef1c8c6b042cd3147e66ffc660fe`.
- Component state: `IN_SYNC`; post-merge aggregate and standalone hashes are both `c5820e3121372f81c8611de9b6015f77e28f5c2160037da035f650660ed090eb`, with zero added, missing, or modified files.
- Post-merge parity evidence: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-corrected-component-parity.json`.

## Exact-head aggregate gates

- Coverage/full build run `31697097557` passed on the frozen head. Validation artifact `9179936668` has digest `sha256:2cae63d96ffb0d7e71f3f2e4205b80e04677de679fb5858b666bbd9059f20434`; Codacy coverage upload and finalization passed.
- Sentinel artifact run `31697114562` passed. Artifact `9179776135` has digest `sha256:60ff38a3b85ebbbd8e4847b2e4189e2aef6dcd65604dd96cf927460d260842bd`.
- Staff PR #137 Codacy reported up-to-standards status, zero new issues, and passing static/diff-coverage/coverage-variation gates.
- GitHub reports zero review threads. CodeRabbit was rate-limited and produced no substantive review; no CodeRabbit approval is claimed.
- Canonical Pi public run `31697114883` and correlated private run `31697709094` passed on the exact frozen head. Sanitized evidence artifact `9180223345` has digest `sha256:c6218f816349256f0160d2e7cd46bf6ff0892c1736effbe4936763c2d9e15bf3`.
- Sanitized Pi result: `PASS`; Paper 1.21.11 build 132; two starts; two storage-ready `SHADOW_MIGRATION` cycles; both shutdowns exited 0 with save markers; guarded disposable-database reset passed before and after; provider API leaks 0; `failure_count=0`.
- Public bridge transfer release/tag cleanup passed. Live release and tag lookups for `es-r01-staging-31697114883-1` return not found.

## Terminal routing

ES-X02 is `COMPLETE`. Dependency recomputation makes ES-X03 and ES-X04 `READY`; this worker does not activate either package. Representative destructive/latency/load acceptance remains assigned to ES-V03. No production balance, private database, deployment, cutover, or authority change occurred.
