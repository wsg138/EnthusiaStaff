# EnthusiaStaff workspace state

Last updated: 2026-08-05

This is a routing record. Live GitHub and `ai-agents/work-packages/PACKAGE-REGISTRY.md` must be reconciled before acting.

## Repository

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current package starting main | `e434b3dedc003d1d5b3def64f38cc7465752b0e5` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper/Leaf backends, Velocity, MariaDB |
| Highest migration | `V16`; V1–V16 remain immutable |
| Issue #43 | Open; owner-led LiteBans cutover acceptance remains deferred |

## Current package state

`ES-P01 BLOCKED — implementation, first-party review, CodeRabbit review, hosted Java 21/MariaDB validation, runtime-JAR inspection, static analysis, and coverage all pass at exact product head 5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be. The configured applicable Pi staging gate cannot execute because the private staging build receives no runner, and no owner exception has been accepted.`

| Field | Value |
| --- | --- |
| Canonical package status | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Active implementation package | `ES-P01 — Exact-sanction appeal isolation` |
| Preserved implementation branch | `package/es-p01-appeal-isolation` |
| Preserved implementation PR | `#68 — ES-P01: isolate appeals to the exact sanction` |
| Frozen reviewed product head | `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be` |
| Passing hosted run | Coverage run `31064171443`, job `92498280092` |
| Package handoff | `ai-agents/reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md` |
| Exact next action | The `wsg138/EnthusiaStaff-Staging` owner must allocate a compatible runner and rerun the exact-head staging build plus safe Pi boot/restart successfully, or explicitly accept and record a verified validation exception. Then reconcile the unchanged PR head and all checks/reviews, merge normally, verify containment, delete the branch, and finalize canonical package state. Any real defect requiring a new product commit must trigger another harsh review and every applicable exact-head gate. |
| Intended post-merge status | `ES-P01 COMPLETE` only after normal merge, containment verification, safe branch deletion, and post-merge canonical finalization; `ES-P02` and `ES-X05` then become `READY`. |
| Owner priority | Resolve the ES-P01 Pi gate and complete its merge/finalization before starting a dependent package. |
| Production authority | LiteBans remains authoritative |

## Completed work

- Website appeal acceptance targets only the exact appealed sanction and cannot mutate sibling sanctions in the same case.
- Authorization, hierarchy constraints, durable pending revision recovery, canonical pending-state encoding, idempotency identity binding, replay, stale-state handling, rollback, restart, and concurrent retry behavior are implemented and covered.
- Existing API fields and the 10–1,000-character reason contract are preserved.
- Existing `MUTATION_PENDING_R<revision>` storage is centralized and rejects malformed, negative, signed, or leading-zero revisions.
- No migration, provider, production-authority, website-UX, or issue #43 change was introduced.

## Exact-head validation

Frozen product head `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be`:

- Java 21 clean build, all module tests, MariaDB/Testcontainers, runtime-JAR integrity/provider-leak inspection, aggregate JaCoCo generation, artifact upload, and Codacy coverage upload passed in run `31064171443`, job `92498280092`.
- Build command: `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- Result: `BUILD SUCCESSFUL` in 5m28s; 49 tasks, 40 executed and 9 up-to-date.
- Runtime inspection checked 24 provider API types and found zero leaks.
- Paper JAR: 8,897,023 bytes, SHA-256 `ce0e19ae07af278d55db7a56ae65df74aa050aa21a9c010018e5283703c628b9`, 4,748 entries.
- Velocity JAR: 7,790,210 bytes, SHA-256 `9b79c0e215d59a711a778414237789676b42cc083cd973f8c3f505d22e39612e`, 4,121 entries.
- Aggregate JaCoCo: 47.07% lines, 38.18% branches, 49.81% instructions.
- Validation artifact `8953318443`: 18,264,471 bytes, SHA-256 `19a71478d9e05d1b08e2153d80442f81cc1a9208014adfabf186e9d969bb6e7f`.
- Codacy static analysis and coverage checks passed with zero annotations. CodeRabbit reports success and zero valid unresolved review threads.

## Pi blocker and exception routing

- Parent run `31057348145`, parent job `92477622119`, dispatched staging run `31057358391`.
- Staging build job `92477654523` received `runner_id: 0`, no runner name, and executed zero steps.
- Safe Pi boot/restart job `92477660726` was skipped because its build dependency never ran.
- Diagnostics artifact `8950755524` has SHA-256 `7f4473dd32b89f1ad69c1e0a26379ae76fe92686e92fe83ead67762a7c04dcfb`.
- This is infrastructure-unavailable evidence. It is neither a passing gate nor a product boot failure.
- Responsible owner and focused routing: `wsg138`, owner of `wsg138/EnthusiaStaff-Staging`, through that repository's runner configuration or an explicitly recorded owner exception.
- Required external input: allocate a compatible runner and obtain a successful exact-head staging build plus safe Pi boot/restart, or explicitly accept a verified validation exception allowed by repository policy.
- PR #68 and its branch remain preserved. Merge is prohibited until this condition is resolved.

## Dependency routing

No later package is active or newly `READY`. `ES-P02` and `ES-X05` remain `PLANNED` because ES-P01 is not `COMPLETE`. They must not be started in the ES-P01 channel.

## Component state

Verified standalone repositories remain site, Currency, Market, and Commend at the heads in `COMPONENT-REGISTRY.md`. RoseChat remains unresolved. ES-P01 is internal and does not modify aggregate or standalone component copies.

No long-lived component branches exist or are part of the design. Temporary package branches are deleted after merge only when safe.

## Permanent boundaries

- No package other than ES-P01 began in this channel.
- No private database or derived rows were accessed or uploaded.
- No production authority, deployment, LiteBans cutover, issue #43 acceptance, website UX, or external-provider work is part of ES-P01.
- V1–V16 remain immutable; no migration is required for ES-P01.
