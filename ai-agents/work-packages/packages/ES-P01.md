# `ES-P01` — Exact-sanction appeal isolation

## 1. Package identity

| Field | Value |
| --- | --- |
| Package ID | `ES-P01` |
| Type | Internal ordinary development package |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Priority | `10` |
| Parallel safe | No |

## 2. Status

Canonical final status: `COMPLETE`. `PACKAGE-REGISTRY.md` is authoritative.

PR #68 merged normally after the exact final head passed every executable hosted gate, Codacy and CodeRabbit were clear, zero valid unresolved review threads remained, and the owner-approved zero-execution infrastructure exception was fully recorded. The reviewed head is contained in `main`, no package-only commit remains outside `main`, and the implementation branch was deleted after merge.

## 3. Objective completed

An appeal decision can mutate only the exact appealed sanction and cannot end unrelated sanctions in a combined case.

## 4. Included audit IDs

- `AUD-APPEAL-002`
- `AUD-APPEAL-003`
- `AUD-PUNISH-003`
- `AUD-SEC-005`
- `AUD-WEB-002`

## 5. Completed behavior

- Replaced case-wide appeal mutation with exact-sanction targeting.
- Preserved reviewer authorization while preventing linked appeals from granting general full-overturn authority.
- Constrained hierarchy bypass to Founder or a service-authorized linked MOD/ADMIN appeal reviewer; SYSTEM sanctions remain immutable.
- Bound mutation idempotency to the website key, appeal ID, and punishment ID.
- Added durable pending/final appeal transitions and persisted the original expected revision for restart-safe stale-state detection.
- Centralized the `MUTATION_PENDING_R<revision>` wire format and rejected malformed, negative, signed, and leading-zero encodings.
- Preserved audit linkage, transaction rollback, retry/replay, concurrency, authority fencing, and the existing 10–1,000-character reason contract.
- Added unit and MariaDB/Testcontainers regression coverage for exact targeting, combined cases, authorization, hierarchy, stale decisions, restart recovery, rollback, concurrent retries, missing capability/target, and finalized replay.

## 6. Exclusions preserved

- Website UX changes
- Production authority activation or deployment
- LiteBans replacement, migration, cutover, issue #43 acceptance, or the 168-hour shadow period
- External provider or standalone-component changes
- Private database or player-data access
- ES-V02 execution or completion
- Any package other than ES-P01

## 7. Final repository facts

| Field | Value |
| --- | --- |
| Starting `main` | `e434b3dedc003d1d5b3def64f38cc7465752b0e5` |
| Implementation branch | `package/es-p01-appeal-isolation` — deleted after safe containment verification |
| Implementation PR | `#68 — ES-P01: isolate appeals to the exact sanction` — merged normally |
| Frozen reviewed product head | `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be` |
| Final reviewed and validated PR head | `ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179` |
| Implementation merge commit | `203b2854d5546a6d3744037c367099129654b42a` |
| Resulting implementation `main` | `203b2854d5546a6d3744037c367099129654b42a` |
| Containment | Merge commit has parents `e434b3dedc003d1d5b3def64f38cc7465752b0e5` and `ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179`; reviewed head is contained in `main` |
| Divergence | `main...ffa8ae4e` is `behind`, `ahead_by: 0`, `behind_by: 1`; zero unique package commits remain outside `main` |
| Finalization branch | `package/es-p01-finalization` — documentation only |
| Production authority | LiteBans remains authoritative |

## 8. Acceptance checklist

- [x] Reconciled live GitHub, registry, branch, PR, checks, reviews, migrations, and issue #43.
- [x] Implemented exact-sanction appeal mutation and sibling-sanction isolation.
- [x] Preserved authorization, hierarchy, SYSTEM immutability, authority fencing, and website contract behavior.
- [x] Implemented durable pending revision recovery, deterministic replay, stale-state protection, rollback, and concurrency handling.
- [x] Added focused domain, Velocity, persistence, and MariaDB/Testcontainers tests.
- [x] Completed harsh first-party review of the complete product and policy diff.
- [x] Resolved every valid CodeRabbit/Codacy finding and reached zero valid unresolved review threads.
- [x] Froze and validated final PR head `ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179`.
- [x] Verified the specialized staging attempt allocated no runner, reported `runner_id: 0`, executed zero product steps, and skipped the downstream boot/restart job.
- [x] Recorded owner approval under the formal zero-execution infrastructure-exception policy.
- [x] Deferred missing distributed Pi boot/restart and Java/Bedrock staging evidence to `ES-V02` without starting it.
- [x] Merged PR #68 with a normal merge commit.
- [x] Verified resulting `main`, reviewed-head containment, zero package divergence, and safe implementation-branch deletion.
- [x] Created the documentation-only post-merge finalization record and derived dependent package statuses.

## 9. Exact-head hosted validation

Final reviewed and validated PR head: `ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179`.

- Coverage run `31067403138`, job `92507961739`: success.
- Java: Temurin `21.0.11+10`.
- Command: `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- Result: `BUILD SUCCESSFUL` in 5m33s; 49 tasks, 40 executed and 9 up-to-date.
- All module tests and MariaDB/Testcontainers integration tests passed.
- Migration-integrity coverage passed; no migration changed.
- Runtime-JAR construction and integrity inspection passed.
- Provider API source types checked: 24; provider leaks: 0.
- Paper JAR: 8,897,023 bytes; SHA-256 `c2c44ceb3d2ba9888aa167c3731a746008413336dd62d421f5b16f15dd8bb426`; 4,748 entries.
- Velocity JAR: 7,790,210 bytes; SHA-256 `43135fb89c02f0c2418b08a0ef120987a65e5143373dfc3af0b3d56df0a018d8`; 4,121 entries.
- JaCoCo: 47.07% lines, 38.17% branches, 49.81% instructions.
- Validation artifact `8954423281`: 18,264,384 bytes; SHA-256 `4c90a22bd42fc63ae7f90a268f4847c9181d37ba7d4bc4b2aaa1fad35fc9b514`.
- Codacy static analysis passed.
- Codacy coverage variation passed at +0.17%.
- Codacy diff coverage passed at 89.92%.
- CodeRabbit passed.
- All six review threads were resolved; zero valid unresolved review threads remained.
- Wiki validation was non-applicable because no `docs/wiki/**`, `scripts/wiki/**`, or `wiki-*` workflow path changed.

## 10. Owner-approved infrastructure exception

Status/evidence label: `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED`.

### Approval

Repository owner `wsg138` explicitly approved this narrowly defined infrastructure-only exception for ES-P01 in the assigned-package instruction on 2026-08-05, America/Indiana/Indianapolis.

### Exact final-head evidence

- External specialized environment: private `wsg138/EnthusiaStaff-Staging`.
- Parent run `31067402120`, parent job `92507922737`.
- Dispatched staging run `31067405608` for source SHA `ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179`.
- Staging build job `92507935906`, `Build trusted EnthusiaStaff Paper runtime`: conclusion `failure`, labels `ubuntu-latest`, `runner_id: 0`, empty runner name, and executed steps `[]`.
- Downstream job `92507942018`, `Boot and restart verified Paper runtime on Lincoln-PI-4`: conclusion `skipped`, no runner, and executed steps `[]`.
- Diagnostics artifact `8954313460`: 1,308 bytes; SHA-256 `e5d73eda6fb3481b4a5bb9d78b8e300ebec3351e79748704c3c17fdc5f4bb58b`.

The earlier recorded attempt independently showed the same condition:

- Parent run `31057348145`, parent job `92477622119`.
- Staging run `31057358391`.
- Build job `92477654523`: `runner_id: 0`, empty runner name, and executed steps `[]`.
- Pi job `92477660726`: skipped and executed steps `[]`.
- Diagnostics artifact `8950755524`: SHA-256 `7f4473dd32b89f1ad69c1e0a26379ae76fe92686e92fe83ead67762a7c04dcfb`.

### Disposition

No product build, test, migration, runtime-JAR, server boot, or restart step executed. The specialized staging results are infrastructure-unavailable evidence, not product results. No product boot failure occurred.

The Pi gate is **not passed**. This exception is not staging verification, production verification, or proof that EnthusiaStaff booted. It cannot excuse any allocated runner that executes a failing product step, compile/test/static-analysis/security/review/migration/JAR/documentation failure, private-validation package, issue #43, 168-hour shadow period, final cutover, production activation, ordinary GitHub-hosted build absence, or a failure caused by ES-P01 workflow edits.

Missing distributed Pi build/boot/restart and Java/Bedrock staging evidence is assigned to `ES-V02 — Distributed and Java/Bedrock staging`. ES-V02 remains `DEFERRED`; it is neither started nor complete.

## 11. Migration impact

No migration was added or edited. `V16` remains highest and V1–V16 remain immutable. The pending marker fits the existing `outcome_code VARCHAR(64)` column while appeal state remains `APPLIED`.

## 12. Dependency-derived status

With ES-P01 complete:

- `ES-P02 — Runtime database recovery and Velocity reload`: `READY`.
- `ES-X05 — Website UX, authentication, and appeals`: `READY`.

Neither dependent package was started by this worker.

## 13. Final routing

- Current package status: `COMPLETE`.
- Canonical handoff: [`../../reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md`](../../reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md).
- No ES-P01 implementation branch remains.
- No other package is active.
- Stop after finalization. Do not begin ES-V02, ES-P02, ES-X05, or any other package.
