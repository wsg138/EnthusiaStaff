# ES-P01 exact-sanction appeal isolation handoff

- Date: 2026-08-05
- Package: `ES-P01 — Exact-sanction appeal isolation`
- Starting status: `ACTIVE`
- Current status: `MERGE_PENDING`
- Starting `main`: `e434b3dedc003d1d5b3def64f38cc7465752b0e5`
- Branch: `package/es-p01-appeal-isolation`
- Pull request: `#68 — ES-P01: isolate appeals to the exact sanction`
- Frozen reviewed product head: `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be`
- Latest reconciled pre-policy PR head: `7b132a3c0696dfcd4f991d64d75390047bc79a39`
- Infrastructure disposition: `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED`
- Deferred validation package: `ES-V02 — Distributed and Java/Bedrock staging`
- Highest Flyway migration: `V16`; V1–V16 remain unchanged
- Production authority: LiteBans remains authoritative

## Resume and live reconciliation

PR #68 remained open, non-draft, and mergeable. Its branch was the only active package branch and no unrelated package was active. The live PR head at reconciliation was `7b132a3c0696dfcd4f991d64d75390047bc79a39`; the frozen product head remained `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be`. All six review threads were resolved. CodeRabbit and all Codacy checks were successful. V16 remained highest and V1–V16 remained immutable.

## Completed implementation

- Website appeal acceptance targets only the submitted punishment ID through the exact-sanction mutation contract.
- A combined case can overturn the appealed sanction while leaving sibling sanctions active and the case open.
- Linked appeal authorization uses `ACCEPT_APPEAL` without granting ordinary full-overturn authority.
- Hierarchy bypass is constrained at the request boundary to Founder or a linked MOD/ADMIN appeal reviewer; SYSTEM sanctions remain immutable.
- Mutation idempotency is bound to the website key, appeal ID, and punishment ID.
- Durable `APPLIED/MUTATION_PENDING_R<revision>` state records the original accepted revision before mutation and finalizes to `APPLIED/APPLIED` afterward.
- Pending state survives restart, retains stale-state protection, and permits recovery after rollback or a post-commit/pre-finalization crash.
- The shared pending-revision codec rejects malformed, negative, explicitly signed, and leading-zero revisions.
- Existing API fields and the 10–1,000-character website reason contract are preserved.
- No migration was required because the marker fits the existing `outcome_code VARCHAR(64)` column.

## Review state

First-party review covered authorization, persistence linkage, transaction boundaries, replay, rollback, stale revisions, idempotency, concurrency, exact-only mutation, privacy, scope, and migration boundaries. CodeRabbit findings were repaired and all six threads were resolved. Codacy static analysis, coverage variation, and diff coverage were successful. Zero valid unresolved review threads remained at reconciliation.

The policy change was reviewed for loopholes. It cannot be used for an allocated runner that executes a failing test/build/migration/JAR/boot step, an actual plugin boot failure, a migration or security failure, a private-validation or acceptance package, issue #43, the 168-hour shadow period, final cutover, production activation, an absent ordinary GitHub-hosted build, or a failure caused by package workflow edits.

## Hosted validation

At reconciled PR head `7b132a3c0696dfcd4f991d64d75390047bc79a39`:

- Coverage run `31064834286`; job `92500281761`; success.
- Temurin Java `21.0.11+10`.
- Command: `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- `BUILD SUCCESSFUL` in 5m22s; 49 tasks, 40 executed and 9 up-to-date.
- All module tests and MariaDB/Testcontainers integration tests passed.
- Runtime inspection checked 24 provider API source types and found zero leaks.
- Paper JAR: 8,897,023 bytes; SHA-256 `095ce7e763f267be050d5c1d36cb8a1190185937943f7b5272cd6dbc964cae9c`; 4,748 entries.
- Velocity JAR: 7,790,210 bytes; SHA-256 `b23160d83709521b4910860357d4d1ab8019f894f5b61af15b77b97d1cec3229`; 4,121 entries.
- Aggregate JaCoCo: 47.07% lines, 38.16% branches, 49.81% instructions.
- Artifact `8953543716`: 18,264,524 bytes; SHA-256 `a6f83d9977615ec1647b4cfbeaee74827b008db1054e6d71b32ae440582cc031`.
- Codacy static analysis, coverage variation, and diff coverage passed.

The final documentation head must repeat every applicable exact-head executable gate. Any later commit invalidates that evidence.

## Owner-approved infrastructure exception

Status/evidence label: `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED`.

Owner approval: repository owner `wsg138` explicitly approved the narrowly defined infrastructure-only exception for ES-P01 in the current assigned-package instruction on 2026-08-05, America/Indiana/Indianapolis.

Exact unavailable-infrastructure evidence:

- external specialized environment: private `wsg138/EnthusiaStaff-Staging`;
- parent run `31057348145`, parent job `92477622119`;
- staging run `31057358391`;
- staging build job `92477654523`, labels `ubuntu-latest`, conclusion `failure`, `runner_id: 0`, empty runner name, and executed steps `[]`;
- downstream Pi job `92477660726`, `Boot and restart verified Paper runtime on Lincoln-PI-4`, conclusion `skipped`, no runner, and executed steps `[]`;
- diagnostics artifact `8950755524`, SHA-256 `7f4473dd32b89f1ad69c1e0a26379ae76fe92686e92fe83ead67762a7c04dcfb`.

No product build, test, migration, artifact, server boot, or restart step executed. The run is infrastructure-unavailable evidence, not a product result. No product boot failure occurred.

The Pi gate is not passed. The exception is not staging verification, production verification, or proof that the plugin booted. Missing distributed Pi build/boot/restart and Java/Bedrock staging evidence is assigned to `ES-V02 — Distributed and Java/Bedrock staging`. ES-V02 remains `DEFERRED` and was not started.

## Boundaries preserved

- No product behavior was changed by the policy correction.
- No migration was added or edited; V16 remains highest.
- No deployment, private data, production authority, LiteBans cutover, issue #43, 168-hour shadow period, production rollback, or cutover action occurred.
- ES-V02, ES-P02, ES-X05, and every other package were not started.

## Merge and finalization state

PR #68 has not yet merged. No implementation merge commit exists. The implementation branch remains present until containment and divergence are verified after merge. A small documentation-only finalization PR will record the merge commit, resulting `main`, reviewed-head containment, divergence, branch deletion, exception disposition, deferred ES-V02 obligation, and dependency-derived READY statuses.

## Exact next action

Freeze the final PR #68 head after all policy/package records and the PR description are updated. Require every applicable exact-head hosted gate, Codacy, Wiki/documentation/package-orchestration checks, mergeability, CodeRabbit, and zero valid unresolved review threads. Confirm the head remains unchanged, merge with a normal merge commit, verify containment and no unique branch commits, safely delete the branch, then create and merge the documentation-only finalization PR. Do not call Pi passed and do not start another package.
