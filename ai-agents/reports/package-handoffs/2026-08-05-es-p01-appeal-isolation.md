# ES-P01 exact-sanction appeal isolation handoff

- Date: 2026-08-05
- Package: `ES-P01 — Exact-sanction appeal isolation`
- Starting status: `ACTIVE`
- Final status: `COMPLETE`
- Starting `main`: `e434b3dedc003d1d5b3def64f38cc7465752b0e5`
- Implementation branch: `package/es-p01-appeal-isolation` — deleted after merge
- Implementation PR: `#68 — merged normally`
- Frozen reviewed product head: `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be`
- Final reviewed and validated PR head: `ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179`
- Implementation merge commit: `203b2854d5546a6d3744037c367099129654b42a`
- Resulting implementation `main`: `203b2854d5546a6d3744037c367099129654b42a`
- Infrastructure disposition: `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED`
- Deferred validation package: `ES-V02 — Distributed and Java/Bedrock staging`
- Highest Flyway migration: `V16`; V1–V16 remain unchanged
- Production authority: LiteBans remains authoritative

## Final reconciliation

PR #68 was non-draft and mergeable at exact head `ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179`. Every executable hosted gate passed, CodeRabbit and Codacy were successful, all six review threads were resolved, and zero valid unresolved review threads remained. PR #68 merged using a normal merge commit.

Merge commit `203b2854d5546a6d3744037c367099129654b42a` has parents:

- starting `main`: `e434b3dedc003d1d5b3def64f38cc7465752b0e5`;
- exact reviewed head: `ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179`.

The reviewed head is contained in `main`. Reverse comparison reports the package head is `behind`, with `ahead_by: 0` and `behind_by: 1`; no unique package commit remains outside `main`. GitHub deleted `package/es-p01-appeal-isolation` after the merge.

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
- No migration was required.

## Policy correction

`ai-agents/work-packages/VALIDATION-POLICY.md` now defines a formal owner-approved infrastructure exception limited to zero-execution infrastructure failures. It requires an external or specialized environment, no allocated runner, zero executed product steps, all executable hosted exact-head gates passing, clean static analysis and review, an unchanged mergeable head, an ordinary development package, explicit owner approval, a named deferred package, and records that never call the unavailable gate passed.

The policy expressly rejects use for any allocated runner with failing steps, actual boot failures, compile or test failures, migration failures, security/static-analysis/review/documentation failures, private or acceptance packages, issue #43, the 168-hour shadow period, cutover, production activation, missing ordinary hosted builds, or package-caused workflow failures.

## Exact-head hosted validation

At final head `ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179`:

- Coverage run `31067403138`; job `92507961739`; success.
- Temurin Java `21.0.11+10`.
- Command: `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- `BUILD SUCCESSFUL` in 5m33s; 49 tasks, 40 executed and 9 up-to-date.
- All module tests and MariaDB/Testcontainers integration tests passed.
- Migration integrity and runtime-JAR inspection passed.
- 24 provider API source types were checked; zero leaks.
- Paper JAR: 8,897,023 bytes; SHA-256 `c2c44ceb3d2ba9888aa167c3731a746008413336dd62d421f5b16f15dd8bb426`; 4,748 entries.
- Velocity JAR: 7,790,210 bytes; SHA-256 `43135fb89c02f0c2418b08a0ef120987a65e5143373dfc3af0b3d56df0a018d8`; 4,121 entries.
- Aggregate JaCoCo: 47.07% lines, 38.17% branches, 49.81% instructions.
- Artifact `8954423281`: 18,264,384 bytes; SHA-256 `4c90a22bd42fc63ae7f90a268f4847c9181d37ba7d4bc4b2aaa1fad35fc9b514`.
- Codacy static analysis, coverage variation, and diff coverage passed.
- CodeRabbit passed; zero valid unresolved review threads remained.
- Wiki validation was non-applicable because no configured Wiki path changed.

## Owner-approved infrastructure exception

Status: `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED`.

Owner approval: repository owner `wsg138`, assigned-package instruction, 2026-08-05, America/Indiana/Indianapolis.

Final-head evidence:

- parent run `31067402120`, parent job `92507922737`;
- staging run `31067405608` for source head `ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179`;
- build job `92507935906`: conclusion `failure`, `runner_id: 0`, empty runner name, and executed steps `[]`;
- Pi boot/restart job `92507942018`: conclusion `skipped`, no runner, and executed steps `[]`;
- diagnostics artifact `8954313460`, SHA-256 `e5d73eda6fb3481b4a5bb9d78b8e300ebec3351e79748704c3c17fdc5f4bb58b`.

The earlier run `31057358391` independently recorded build job `92477654523` with `runner_id: 0` and steps `[]`, plus skipped Pi job `92477660726` with steps `[]`.

No product build, test, migration, artifact, server boot, or restart step executed. No product boot failure occurred.

The Pi gate is **not passed**. The exception is not staging verification, production verification, or proof that the plugin booted. Missing distributed Pi build/boot/restart and Java/Bedrock staging evidence is assigned to `ES-V02`. ES-V02 remains `DEFERRED` and was not started.

## Dependency-derived statuses

- `ES-P02 — Runtime database recovery and Velocity reload`: `READY`.
- `ES-X05 — Website UX, authentication, and appeals`: `READY`.

Neither package was started.

## Boundaries preserved

- No migration was added or edited; V16 remains highest.
- No deployment or private data access occurred.
- No authority activation, LiteBans cutover, issue #43 work, 168-hour shadow period, production rollback, or cutover occurred.
- ES-V02, ES-P02, ES-X05, and every other package were not started.

## Final routing

The documentation-only finalization branch is `package/es-p01-finalization`. Once this finalization record is merged normally and its branch is cleaned up, ES-P01 has no remaining action. Stop after ES-P01; do not begin the next package.
