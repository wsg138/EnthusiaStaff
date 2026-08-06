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

Canonical current status: `MERGE_PENDING`. `PACKAGE-REGISTRY.md` is authoritative.

Implementation and product review are frozen at `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be`. Every executable hosted gate was passing at reconciled PR head `7b132a3c0696dfcd4f991d64d75390047bc79a39`, with Codacy and CodeRabbit clear and zero valid unresolved review threads. The owner has accepted the formal zero-execution infrastructure exception below. The final documentation head must pass every applicable exact-head gate and remain unchanged and mergeable before merge.

## 3. Objective

Ensure an appeal decision can mutate only the exact appealed sanction and cannot end unrelated sanctions in a combined case.

## 4. Included audit IDs

- `AUD-APPEAL-002`
- `AUD-APPEAL-003`
- `AUD-PUNISH-003`
- `AUD-SEC-005`
- `AUD-WEB-002`

## 5. Included behavior completed

- Replaced case-wide appeal mutation with exact-sanction targeting.
- Preserved reviewer authorization while preventing linked appeals from granting general full-overturn authority.
- Constrained hierarchy bypass to Founder or a service-authorized linked MOD/ADMIN appeal reviewer; SYSTEM sanctions remain immutable.
- Bound mutation idempotency to the website key, appeal ID, and punishment ID.
- Added durable pending/final appeal transitions and persisted the original expected revision for restart-safe stale-state detection.
- Centralized the `MUTATION_PENDING_R<revision>` wire format and rejected malformed, negative, signed, and leading-zero encodings.
- Preserved audit linkage, transaction rollback, retry/replay, concurrency, authority fencing, and the existing 10–1,000-character reason contract.
- Added unit and MariaDB/Testcontainers regression coverage for exact targeting, combined cases, authorization, hierarchy, stale decisions, restart recovery, rollback, concurrent retries, missing capability/target, and finalized replay.

## 6. Explicit exclusions preserved

- Website UX changes
- Production authority activation or deployment
- LiteBans replacement, migration, cutover, issue #43 acceptance, or the 168-hour shadow period
- External provider or standalone-component changes
- Private database or player-data access
- ES-V02 execution or completion
- Any package other than ES-P01

## 7. Dependencies and boundaries

- Dependencies: none.
- Repository: `wsg138/EnthusiaStaff` only.
- Temporary branch: `package/es-p01-appeal-isolation`.
- Implementation PR: `#68 — ES-P01: isolate appeals to the exact sanction`.
- Starting `main`: `e434b3dedc003d1d5b3def64f38cc7465752b0e5`.
- LiteBans remains authoritative.

## 8. Implementation and acceptance checklist

- [x] Reconcile live GitHub, registry, branch, PR, checks, reviews, migrations, and issue #43.
- [x] Implement exact-sanction appeal mutation and sibling-sanction isolation.
- [x] Preserve authorization, hierarchy, SYSTEM immutability, authority fencing, and website contract behavior.
- [x] Implement durable pending revision recovery, deterministic replay, stale-state protection, rollback, and concurrency handling.
- [x] Add focused domain, Velocity, persistence, and MariaDB/Testcontainers tests.
- [x] Complete harsh first-party review of the complete product diff.
- [x] Resolve every valid CodeRabbit/Codacy finding and require zero valid unresolved review threads.
- [x] Freeze product head `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be` and pass the full hosted Java 21/MariaDB/runtime-JAR/coverage gate.
- [x] Verify the Pi attempt allocated no runner, reported `runner_id: 0`, executed zero product steps, and skipped the downstream boot/restart job.
- [x] Record owner approval under the formal zero-execution infrastructure-exception policy.
- [x] Defer the missing distributed Pi boot/restart and Java/Bedrock staging evidence to `ES-V02` without starting it.
- [ ] Freeze and validate the final policy/documentation PR head with every applicable executable gate.
- [ ] Merge PR #68 normally.
- [ ] Verify resulting `main`, reviewed-head containment, divergence, and safe branch deletion.
- [ ] Complete post-merge canonical finalization and derive dependent package statuses.

## 9. Hosted validation evidence

Frozen product head: `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be`.

Latest reconciled pre-policy PR head: `7b132a3c0696dfcd4f991d64d75390047bc79a39`.

- Coverage run `31064834286`, job `92500281761`: success.
- Java: Temurin `21.0.11+10`.
- Command: `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- Result: `BUILD SUCCESSFUL` in 5m22s; 49 tasks, 40 executed and 9 up-to-date.
- All module tests and MariaDB/Testcontainers integration tests passed.
- Runtime inspection checked 24 provider API source types and found zero leaks.
- Paper JAR: 8,897,023 bytes; SHA-256 `095ce7e763f267be050d5c1d36cb8a1190185937943f7b5272cd6dbc964cae9c`; 4,748 entries.
- Velocity JAR: 7,790,210 bytes; SHA-256 `b23160d83709521b4910860357d4d1ab8019f894f5b61af15b77b97d1cec3229`; 4,121 entries.
- JaCoCo: 47.07% lines, 38.16% branches, 49.81% instructions.
- Validation artifact `8953543716`: 18,264,524 bytes; SHA-256 `a6f83d9977615ec1647b4cfbeaee74827b008db1054e6d71b32ae440582cc031`.
- Codacy static analysis, coverage variation, and diff coverage passed.
- CodeRabbit reported success; all six review threads were resolved and zero valid unresolved threads remained.

The exact final PR head created by policy/package documentation must repeat every applicable executable hosted gate before merge. A later commit invalidates that evidence.

## 10. Owner-approved infrastructure exception

Status/evidence label: `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED`.

### Approval

Repository owner `wsg138` explicitly approved this narrowly defined infrastructure-only exception for ES-P01 in the current assigned-package instruction on 2026-08-05, America/Indiana/Indianapolis.

### Exact evidence

- External specialized environment: private `wsg138/EnthusiaStaff-Staging`.
- Parent run `31057348145`, parent job `92477622119`.
- Dispatched staging run `31057358391`.
- Staging build job `92477654523`: labels `ubuntu-latest`, conclusion `failure`, `runner_id: 0`, empty runner name, and empty executed-step list `[]`.
- Downstream job `92477660726`, `Boot and restart verified Paper runtime on Lincoln-PI-4`: conclusion `skipped`, no runner, and empty executed-step list `[]`.
- Diagnostics artifact `8950755524`: SHA-256 `7f4473dd32b89f1ad69c1e0a26379ae76fe92686e92fe83ead67762a7c04dcfb`.

### Disposition

No product build, test, migration, runtime-JAR, server boot, or restart step executed. The staging result is infrastructure-unavailable evidence, not a product result. No product boot failure occurred.

The Pi gate is not passed. This exception is not staging verification, production verification, or proof that EnthusiaStaff booted. It cannot excuse any allocated runner that executes a failing product step, compile/test/static-analysis/security/review/migration/JAR/documentation failure, private-validation package, issue #43, 168-hour shadow period, final cutover, production activation, ordinary GitHub-hosted build absence, or a failure caused by ES-P01 workflow edits.

Missing distributed Pi build/boot/restart and Java/Bedrock staging evidence is assigned to `ES-V02 — Distributed and Java/Bedrock staging`. ES-V02 remains `DEFERRED`; it is neither started nor complete.

## 11. Migration impact

No migration is required. `V16` remains highest and V1–V16 remain immutable. The pending marker fits the existing `outcome_code VARCHAR(64)` column while appeal state remains `APPLIED`.

## 12. Resume state

- Starting package status: `ACTIVE`.
- Current package status: `MERGE_PENDING`.
- Branch: `package/es-p01-appeal-isolation`.
- PR: `#68`.
- Frozen reviewed product head: `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be`.
- Canonical handoff: [`../../reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md`](../../reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md).
- No merge commit exists yet; starting `main` remains `e434b3dedc003d1d5b3def64f38cc7465752b0e5` until PR #68 merges.
- `ES-P02` and `ES-X05` remain `PLANNED` until post-merge finalization marks ES-P01 `COMPLETE`.

## 13. Exact next action

Harshly review the complete policy/package-state diff, freeze the resulting PR head, require Java 21 clean build, all module and MariaDB/Testcontainers tests, migration integrity, runtime-JAR construction/inspection, provider-leak inspection, aggregate coverage, Codacy, Wiki/documentation/package-orchestration validation, mergeability, and zero valid unresolved review threads. Confirm the head remains unchanged, then merge PR #68 normally, verify containment/divergence, safely delete the implementation branch, and complete a documentation-only finalization PR. Do not call Pi passed and do not start ES-V02, ES-P02, or ES-X05.
