# `ES-P01` — Exact-sanction appeal isolation

## 1. Package identity

| Field | Value |
| --- | --- |
| Package ID | `ES-P01` |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Priority | `10` |
| Parallel safe | No |

## 2. Status

Canonical current status: `BLOCKED`. `PACKAGE-REGISTRY.md` is authoritative.

Implementation, review, and every currently executable hosted gate are complete at frozen product head `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be`. The remaining required Pi staging gate cannot execute because the private staging build receives no runner, and no verified owner exception has been accepted.

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
- LiteBans replacement, migration, cutover, or issue #43 acceptance
- External provider or standalone-component changes
- Private database or player-data access
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
- [x] Complete harsh first-party review of the complete package diff.
- [x] Resolve every valid CodeRabbit/Codacy finding and require zero valid unresolved review threads.
- [x] Freeze product head `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be` and pass the full hosted Java 21/MariaDB/runtime-JAR/coverage gate.
- [ ] Obtain successful applicable Pi staging build and safe boot/restart evidence, or a verified owner exception permitted by repository policy.
- [ ] Merge PR #68 normally.
- [ ] Verify resulting `main`, reviewed-head containment, divergence, and safe branch deletion.
- [ ] Complete post-merge canonical finalization and derive dependent package statuses.

## 9. Exact-head validation evidence

Frozen product head: `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be`.

- Coverage run `31064171443`, job `92498280092`: success.
- Java: Temurin `21.0.11+10`.
- Command: `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- Result: `BUILD SUCCESSFUL` in 5m28s; 49 tasks, 40 executed and 9 up-to-date.
- All module tests and MariaDB/Testcontainers integration tests passed.
- Runtime inspection checked 24 provider API source types and found zero leaks.
- Paper JAR: 8,897,023 bytes; SHA-256 `ce0e19ae07af278d55db7a56ae65df74aa050aa21a9c010018e5283703c628b9`; 4,748 entries.
- Velocity JAR: 7,790,210 bytes; SHA-256 `9b79c0e215d59a711a778414237789676b42cc083cd973f8c3f505d22e39612e`; 4,121 entries.
- JaCoCo: 47.07% lines, 38.18% branches, 49.81% instructions.
- Validation artifact `8953318443`: 18,264,471 bytes; SHA-256 `19a71478d9e05d1b08e2153d80442f81cc1a9208014adfabf186e9d969bb6e7f`.
- Codacy static analysis and coverage upload passed with zero annotations.
- CodeRabbit reports success; every current review thread is resolved.

## 10. Pi staging blocker

The configured workflow dispatches to private repository `wsg138/EnthusiaStaff-Staging`.

- Parent run `31057348145`, job `92477622119`.
- Staging run `31057358391`.
- Staging build job `92477654523`: `runner_id: 0`, no runner name, zero executed steps.
- Pi boot/restart job `92477660726`: skipped because the build dependency never ran.
- Diagnostics artifact `8950755524`: SHA-256 `7f4473dd32b89f1ad69c1e0a26379ae76fe92686e92fe83ead67762a7c04dcfb`.

This is infrastructure-unavailable evidence, not a passing gate and not a product boot failure. The responsible staging/CI owner is `wsg138`.

Unblock condition: allocate a compatible staging runner and obtain a successful exact-head staging build plus safe Pi boot/restart, or explicitly accept and record a verified validation exception permitted by repository policy.

## 11. Migration impact

No migration is required. `V16` remains highest and V1–V16 remain immutable. The pending marker fits the existing `outcome_code VARCHAR(64)` column while appeal state remains `APPLIED`.

## 12. Resume state

- Starting package status: `ACTIVE`.
- Current package status: `BLOCKED`.
- Preserved branch: `package/es-p01-appeal-isolation`.
- Preserved PR: `#68`.
- Frozen reviewed product head: `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be`.
- Canonical handoff: [`../../reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md`](../../reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md).
- No merge commit exists; `main` remains `e434b3dedc003d1d5b3def64f38cc7465752b0e5`.
- No dependent package is `READY`; `ES-P02` and `ES-X05` remain `PLANNED`.

## 13. Exact next action

Resolve the Pi gate without changing the frozen product head: configure a compatible runner in `wsg138/EnthusiaStaff-Staging` and rerun the exact-head staging build plus safe Pi boot/restart, or record an explicit verified owner exception. Then recheck that PR #68 still points to the validated head with all checks and reviews clear, merge normally, verify containment and divergence, delete the branch when safe, and complete a documentation-only post-merge finalization PR. Any real product commit requires another harsh review and every applicable exact-head gate.
