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

`ES-P01 ACTIVE — exact-sanction appeal isolation is implemented and has passing hosted Java/MariaDB evidence; PR #68 is in automated review and final exact-head validation.`

| Field | Value |
| --- | --- |
| Canonical package status | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Active implementation package | `ES-P01 — Exact-sanction appeal isolation` |
| Active implementation branch | `package/es-p01-appeal-isolation` |
| Active implementation PR | `#68 — ES-P01: isolate appeals to the exact sanction` |
| Passing implementation head | `9acbbe0cb792deb69bd5758b364d9609da9ace58` before review-fix commits |
| Passing hosted run | Coverage run `31059266809`, job `92483396625` |
| Package handoff | `ai-agents/reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md` |
| Next action | Validate the final review-fix head, require zero valid unresolved threads, merge normally, verify containment, delete the implementation branch, and finalize package state. |
| Intended post-merge status | `ES-P01 COMPLETE` only after normal merge, containment verification, and safe branch deletion; `ES-P02` and `ES-X05` then become `READY`. |
| Owner priority | Finish ES-P01 review, exact-head validation, merge, cleanup, and canonical state finalization before starting any dependent package. |
| Production authority | LiteBans remains authoritative |

## Completed work

- Website appeal acceptance targets only the exact appealed sanction and cannot mutate sibling sanctions in the same case.
- Authorization, hierarchy constraints, durable pending revision recovery, idempotency identity binding, replay, stale-state handling, rollback, restart, and concurrent retry behavior are implemented and covered.
- Existing API fields and the 10–1,000-character reason contract are preserved.
- No migration, provider, production-authority, website-UX, or issue #43 change was introduced.

## Validation routing

- Head `9acbbe0cb792deb69bd5758b364d9609da9ace58` passed Java 21 clean build, all module tests, MariaDB/Testcontainers, runtime-JAR integrity/provider-leak inspection, aggregate JaCoCo generation, artifact upload, and Codacy coverage upload.
- Review fixes moved the branch afterward; the final reviewed head must repeat those gates.
- Codacy findings from the first non-draft review cycle were fixed; the current head must return zero valid annotations.
- The configured Pi staging dispatch reaches `EnthusiaStaff-Staging`, but the staging build job receives no runner (`runner_id: 0`, zero steps). This is infrastructure-unavailable evidence, not a pass and not a product failure.
- No production, private-data, distributed, Bedrock, or LiteBans cutover acceptance is claimed by ES-P01.

## Dependency routing

No later package is active. After ES-P01 is fully complete and its exact post-merge state is committed, `ES-P02` and `ES-X05`, which depend only on ES-P01, become `READY`. They must not be started in the ES-P01 channel.

## Component state

Verified standalone repositories remain site, Currency, Market, and Commend at the heads in `COMPONENT-REGISTRY.md`. RoseChat remains unresolved. ES-P01 is internal and does not modify aggregate or standalone component copies.

No long-lived component branches exist or are part of the design. Temporary package branches are deleted after merge when safe.

## Blockers

No product blocker is known. Pi staging remains unavailable because no staging runner is allocated. CodeRabbit availability may be rate-limited, but every produced valid finding must be resolved and all review threads must be closed before merge.

## Permanent boundaries

- No package other than ES-P01 begins in this channel.
- No private database or derived rows may be uploaded.
- No production authority, deployment, LiteBans cutover, issue #43 acceptance, website UX, or external-provider work is part of ES-P01.
- V1–V16 remain immutable; no migration is required for ES-P01.
