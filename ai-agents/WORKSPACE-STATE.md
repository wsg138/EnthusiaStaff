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

`ES-P01 ACTIVE — exact-sanction appeal isolation is implemented and has passing hosted Java/MariaDB evidence; PR #68 is entering the non-draft automated review and final exact-head validation cycle.`

| Field | Value |
| --- | --- |
| Canonical package status | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Active implementation package | `ES-P01 — Exact-sanction appeal isolation` |
| Active implementation branch | `package/es-p01-appeal-isolation` |
| Active implementation PR | `#68 — ES-P01: isolate appeals to the exact sanction` |
| Passing implementation head | `9acbbe0cb792deb69bd5758b364d9609da9ace58` before the evidence-only checkpoint commits |
| Passing hosted run | Coverage run `31059266809`, job `92483396625` |
| Package handoff | `ai-agents/reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md` |
| Next action | Complete CodeRabbit/human review, resolve findings, freeze the resulting exact head, repeat all applicable validation, merge normally, verify containment, delete the implementation branch, and finalize package state. |
| Production authority | LiteBans remains authoritative |

## Validation routing

- Head `9acbbe0cb792deb69bd5758b364d9609da9ace58` passed Java 21 clean build, all module tests, MariaDB/Testcontainers, runtime-JAR integrity/provider-leak inspection, aggregate JaCoCo generation, artifact upload, and Codacy coverage upload.
- The final reviewed head must repeat those gates after the non-draft review cycle.
- Codacy currently reports zero new PR issues.
- The configured Pi staging dispatch reaches `EnthusiaStaff-Staging`, but the staging build job receives no runner (`runner_id: 0`, zero steps). This is infrastructure-unavailable evidence, not a pass and not a product failure.
- No production, private-data, distributed, Bedrock, or LiteBans cutover acceptance is claimed by ES-P01.

## Dependency routing

No later package is active. After ES-P01 is fully complete and its exact post-merge state is committed, `ES-P02` and `ES-X05`, which depend only on ES-P01, become `READY`. They must not be started in the ES-P01 channel.

## Component state

Verified standalone repositories remain site, Currency, Market, and Commend at the heads in `COMPONENT-REGISTRY.md`. RoseChat remains unresolved. ES-P01 is internal and does not modify aggregate or standalone component copies.

No long-lived component branches exist or are part of the design. Temporary package branches are deleted after merge when safe.

## Permanent boundaries

- No package other than ES-P01 begins in this channel.
- No private database or derived rows may be uploaded.
- No production authority, deployment, LiteBans cutover, issue #43 acceptance, website UX, or external-provider work is part of ES-P01.
- V1–V16 remain immutable; no migration is required for ES-P01.
