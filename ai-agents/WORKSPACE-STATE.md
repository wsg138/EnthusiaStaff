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

`ES-P01 ACTIVE — exact-sanction appeal isolation is implemented in PR #68; current review repairs must be completed and the resulting tracked content frozen before final exact-head validation.`

| Field | Value |
| --- | --- |
| Canonical package status | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Active implementation package | `ES-P01 — Exact-sanction appeal isolation` |
| Active implementation branch | `package/es-p01-appeal-isolation` |
| Active implementation PR | `#68 — ES-P01: isolate appeals to the exact sanction` |
| Last passing reviewed product head | `b0444d60e215f38b8f3d196826fa51cf170aa228` before the current parser/documentation review repairs |
| Last passing hosted run | Coverage run `31063653826`, job `92496717253` |
| Package handoff | `ai-agents/reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md` |
| Next action | Complete every valid current review repair, harshly review the complete resulting diff, freeze all tracked content, and validate that exact head. Any real defect requiring another commit must trigger another harsh review and every applicable exact-head gate before merge. Then require zero valid unresolved threads, resolve the Pi gate described below, merge normally, verify containment, delete the implementation branch, and finalize package state. |
| Intended post-merge status | `ES-P01 COMPLETE` only after normal merge, containment verification, and safe branch deletion; `ES-P02` and `ES-X05` then become `READY`. |
| Owner priority | Finish ES-P01 review, exact-head validation, Pi disposition, merge, cleanup, and canonical state finalization before starting any dependent package. |
| Production authority | LiteBans remains authoritative |

## Completed work

- Website appeal acceptance targets only the exact appealed sanction and cannot mutate sibling sanctions in the same case.
- Authorization, hierarchy constraints, durable pending revision recovery, idempotency identity binding, replay, stale-state handling, rollback, restart, and concurrent retry behavior are implemented and covered.
- Existing API fields and the 10–1,000-character reason contract are preserved.
- No migration, provider, production-authority, website-UX, or issue #43 change was introduced.

## Validation routing

- Head `b0444d60e215f38b8f3d196826fa51cf170aa228` passed Java 21 clean build, all module tests, MariaDB/Testcontainers, runtime-JAR integrity/provider-leak inspection, aggregate JaCoCo generation, artifact upload, Codacy static analysis, and Codacy coverage upload.
- Current parser/documentation review repairs move the branch afterward; the fully reviewed and frozen final head must repeat every applicable gate.
- Codacy reports zero annotations on the last passing head; every current CodeRabbit finding and valid thread must be resolved on the final head.
- The configured Pi staging dispatch reaches `wsg138/EnthusiaStaff-Staging`, but staging build job `92477654523` in run `31057358391` received no runner (`runner_id: 0`, zero steps). Pi boot job `92477660726` was skipped. This is infrastructure-unavailable evidence, not a pass and not a product failure.
- No production, private-data, distributed, Bedrock, or LiteBans cutover acceptance is claimed by ES-P01.

## Dependency routing

No later package is active. After ES-P01 is fully complete and its exact post-merge state is committed, `ES-P02` and `ES-X05`, which depend only on ES-P01, become `READY`. They must not be started in the ES-P01 channel.

## Component state

Verified standalone repositories remain site, Currency, Market, and Commend at the heads in `COMPONENT-REGISTRY.md`. RoseChat remains unresolved. ES-P01 is internal and does not modify aggregate or standalone component copies.

No long-lived component branches exist or are part of the design. Temporary package branches are deleted after merge when safe.

## Blockers and validation exception routing

- Product and review repairs remain `ACTIVE` until the current parser and documentation findings are fixed, reviewed, and validated. The package must not be labeled infrastructure-blocked while any product, test, static-analysis, or review failure remains.
- The Pi merge gate is currently unavailable because `wsg138/EnthusiaStaff-Staging` build job `92477654523` received no runner and executed zero steps; downstream Pi boot job `92477660726` was skipped. The responsible staging/CI owner is `wsg138`, the owner of the private staging repository.
- Required external input: the staging/CI owner must either allocate a compatible runner and obtain a successful exact-head staging build plus safe Pi boot/restart, or explicitly accept and record a verified validation exception permitted by repository policy.
- Focused routing: resolve this only through the `wsg138/EnthusiaStaff-Staging` runner configuration or a documented owner exception; do not weaken product checks or treat the zero-step run as passing evidence.
- Merge remains blocked until one of those two Pi dispositions is verified. If every product/review gate is clean while this condition remains unchanged, the canonical package status must transition from `ACTIVE` to `BLOCKED` and preserve PR #68 and its branch.

## Permanent boundaries

- No package other than ES-P01 begins in this channel.
- No private database or derived rows may be uploaded.
- No production authority, deployment, LiteBans cutover, issue #43 acceptance, website UX, or external-provider work is part of ES-P01.
- V1–V16 remain immutable; no migration is required for ES-P01.
