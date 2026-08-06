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

`ES-P01 MERGE_PENDING — implementation and product review are frozen at 5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be. The owner approved the narrowly defined zero-execution infrastructure exception for the unavailable Pi staging gate. Final policy/package documentation and every executable exact-head gate must remain clear before PR #68 may merge.`

| Field | Value |
| --- | --- |
| Canonical package status | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Active implementation package | `ES-P01 — Exact-sanction appeal isolation` |
| Implementation branch | `package/es-p01-appeal-isolation` |
| Implementation PR | `#68 — ES-P01: isolate appeals to the exact sanction` |
| Frozen reviewed product head | `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be` |
| Latest reconciled pre-policy PR head | `7b132a3c0696dfcd4f991d64d75390047bc79a39` |
| Latest passing hosted run at reconciliation | Coverage run `31064834286`, job `92500281761` |
| Package handoff | `ai-agents/reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md` |
| Infrastructure disposition | `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED` |
| Deferred evidence package | `ES-V02 — Distributed and Java/Bedrock staging` |
| Exact next action | Harshly review the policy/package documentation, freeze the resulting PR head, require every executable hosted exact-head gate, Codacy, CodeRabbit, documentation/package validation, mergeability, and zero valid unresolved threads, then merge PR #68 normally. Any new commit invalidates the current-head evidence. |
| Intended post-merge status | `ES-P01 COMPLETE` only after normal merge, containment verification, safe branch deletion, and post-merge canonical finalization; `ES-P02` and `ES-X05` then become `READY`. |
| Owner priority | Finish ES-P01 merge and finalization without starting ES-V02 or any dependent package. |
| Production authority | LiteBans remains authoritative |

## Completed work

- Website appeal acceptance targets only the exact appealed sanction and cannot mutate sibling sanctions in the same case.
- Authorization, hierarchy constraints, durable pending revision recovery, canonical pending-state encoding, idempotency identity binding, replay, stale-state handling, rollback, restart, and concurrent retry behavior are implemented and covered.
- Existing API fields and the 10–1,000-character reason contract are preserved.
- Existing `MUTATION_PENDING_R<revision>` storage is centralized and rejects malformed, negative, signed, or leading-zero revisions.
- No migration, provider, production-authority, website-UX, or issue #43 change was introduced.

## Hosted validation evidence

Frozen product head `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be` passed the full hosted Java/MariaDB gate. At live reconciliation, PR head `7b132a3c0696dfcd4f991d64d75390047bc79a39` also passed exact-head Coverage run `31064834286`, job `92500281761`:

- Temurin Java `21.0.11+10`.
- Command: `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- `BUILD SUCCESSFUL` in 5m22s; 49 tasks, 40 executed and 9 up-to-date.
- All module tests and MariaDB/Testcontainers integration tests passed.
- Runtime inspection checked 24 provider API source types and found zero leaks.
- Paper JAR: 8,897,023 bytes, SHA-256 `095ce7e763f267be050d5c1d36cb8a1190185937943f7b5272cd6dbc964cae9c`, 4,748 entries.
- Velocity JAR: 7,790,210 bytes, SHA-256 `b23160d83709521b4910860357d4d1ab8019f894f5b61af15b77b97d1cec3229`, 4,121 entries.
- Aggregate JaCoCo: 47.07% lines, 38.16% branches, 49.81% instructions.
- Validation artifact `8953543716`: 18,264,524 bytes, SHA-256 `a6f83d9977615ec1647b4cfbeaee74827b008db1054e6d71b32ae440582cc031`.
- Codacy static analysis, coverage variation, and diff coverage passed. CodeRabbit was successful and zero valid unresolved review threads remained.

The final documentation head must repeat every applicable exact-head gate before merge.

## Owner-approved infrastructure exception

Status/evidence label: `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED`.

- Owner approval: repository owner `wsg138` explicitly approved this narrowly defined infrastructure-only exception for ES-P01 in the current assigned-package instruction on 2026-08-05, America/Indiana/Indianapolis.
- Affected external environment: private `wsg138/EnthusiaStaff-Staging` workflow.
- Parent run `31057348145`, parent job `92477622119`, dispatched staging run `31057358391`.
- Staging build job `92477654523`: labels `ubuntu-latest`, `runner_id: 0`, empty runner name, and `steps: []`.
- Safe Pi boot/restart job `92477660726`: skipped, no runner, and `steps: []` because the build dependency never executed.
- Diagnostics artifact `8950755524`: SHA-256 `7f4473dd32b89f1ad69c1e0a26379ae76fe92686e92fe83ead67762a7c04dcfb`.
- Reason: no runner was allocated and zero product build, test, migration, artifact, boot, or restart step executed. The evidence is infrastructure-unavailable, not a product result.
- Deferred obligation: obtain distributed Pi build, boot, restart, and Java/Bedrock staging evidence in `ES-V02 — Distributed and Java/Bedrock staging` when that package is legitimately started.

The Pi gate is not passed. This exception is not staging verification, production verification, or proof that the plugin booted. It cannot cover an allocated runner, an executed failing product step, a product boot failure, migration failure, security/static-analysis/review failure, issue #43, the 168-hour shadow period, cutover, production activation, or a failure caused by ES-P01 workflow edits.

## Dependency routing

No later package is active. `ES-P02` and `ES-X05` remain `PLANNED` until ES-P01 reaches `COMPLETE`. `ES-V02` remains `DEFERRED` and is not started by this exception.

## Component state

Verified standalone repositories remain site, Currency, Market, and Commend at the heads in `COMPONENT-REGISTRY.md`. RoseChat remains unresolved. ES-P01 is internal and does not modify aggregate or standalone component copies.

No long-lived component branches exist or are part of the design. Temporary package branches are deleted after merge only when safe.

## Permanent boundaries

- No package other than ES-P01 began in this channel.
- No private database or derived rows were accessed or uploaded.
- No deployment, production authority, LiteBans cutover, issue #43 acceptance, 168-hour shadow period, website UX, or external-provider work is part of ES-P01.
- ES-V02, ES-P02, and ES-X05 were not started.
- V1–V16 remain immutable; no migration is required for ES-P01.
