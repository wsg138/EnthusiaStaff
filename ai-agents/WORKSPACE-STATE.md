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

`ES-P01 ACTIVE — exact-sanction appeal isolation is implemented on its documented temporary branch and is undergoing hosted validation, full-diff review, and merge preparation.`

| Field | Value |
| --- | --- |
| Canonical package status | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Active implementation package | `ES-P01 — Exact-sanction appeal isolation` |
| Active implementation branch | `package/es-p01-appeal-isolation` |
| Active implementation PR | `#68 — ES-P01: isolate appeals to the exact sanction` |
| Latest recorded implementation head | `5e3f07ee546c4a569f7d27cf2b4e09e1b0c97adf` before state/handoff commits |
| Package handoff | `ai-agents/reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md` |
| Next action | Obtain passing hosted evidence, complete harsh review, freeze exact head, merge normally, verify containment, delete implementation branch, and finalize exact package state. |
| Production authority | LiteBans remains authoritative |

## Validation routing

- Hosted Java 21 build, MariaDB/Testcontainers, runtime-JAR inspection, coverage, and static-analysis evidence are required on the exact final reviewed head.
- The configured Pi staging dispatch currently reaches `EnthusiaStaff-Staging`, but the staging build job receives no runner (`runner_id: 0`, zero steps). This is infrastructure-unavailable evidence, not a pass and not a product failure.
- No production, private-data, distributed, Bedrock, or LiteBans cutover acceptance is claimed by ES-P01.

## Dependency routing

No later package is active. After ES-P01 is fully complete and its exact post-merge state is committed, packages depending only on ES-P01 must be derived to `READY`; they must not be started in the ES-P01 channel.

## Component state

Verified standalone repositories remain site, Currency, Market, and Commend at the heads in `COMPONENT-REGISTRY.md`. RoseChat remains unresolved. ES-P01 is internal and does not modify aggregate or standalone component copies.

No long-lived component branches exist or are part of the design. Temporary package branches are deleted after merge when safe.

## Permanent boundaries

- No package other than ES-P01 begins in this channel.
- No private database or derived rows may be uploaded.
- No production authority, deployment, LiteBans cutover, issue #43 acceptance, website UX, or external-provider work is part of ES-P01.
- V1–V16 remain immutable; no migration is required for ES-P01.
