# EnthusiaStaff workspace manifest

Last updated: 2026-08-11 (`America/Indiana/Indianapolis`)

This manifest records project orchestration and authority boundaries. It does not authorize deployment, production data, LiteBans cutover, or punishment authority.

## Checkpoint

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Setup baseline | `af9aa3d0d54afc84de7c90cb3fdc5ce3cdf9118a` |
| Current `main` | `7c032c6af32f7281f518a01ed6dc3b0252cabb5b` (merged PR #127) |
| Canonical package registry | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Canonical component registry | `ai-agents/work-packages/COMPONENT-REGISTRY.md` |
| Intended state | `ROOT PLUGIN REMEDIATION AND FUNCTIONAL COMPLETION` |
| Latest completed checkpoints | PR #125 website appeal transactions, PR #126 economy recovery and confiscation coordination, PR #127 exact-sanction transaction boundaries |
| Active implementation | `section/plugin`: network-identity observation and persistence boundaries |
| Codacy checkpoint | 311 active warnings on PR #127 code head `efd8cb507d8b7e4c2d2ac493a98e847442496072`, down from 319 on its base; merge commit `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`; grade is not asserted because the current CLI response does not expose it |
| Migration boundary | V18; V1-V18 immutable |
| Production authority | LiteBans remains authoritative |

## Repository model

`main` is the complete aggregate workspace. The core EnthusiaStaff plugin remains at the repository root. At setup, metadata-only external component directories live under `components/`; no external source has been imported. An assigned external package may later populate its designated directory as the tracked aggregate copy after verifying the standalone repository, source revision, license/history, exclusions, and reviewability. There are no permanent component branches, generated split branches, or isolated-component PRs.

Internal packages normally use one temporary branch and one PR to `EnthusiaStaff:main`. External packages normally use one temporary branch/PR in the standalone repository and one temporary branch/PR to `EnthusiaStaff:main`; the PRs share a package ID, cross-reference each other, and must reach deterministic parity. Temporary branches are deleted after merge when safe.

## Verified standalone repositories

| Component | Repository | Default branch | Setup head |
| --- | --- | --- | --- |
| Site | `wsg138/enthusia-site` | `main` | `9408166c75def0b55caa8d38fb546c6e77ea1f7d` |
| RoseChat | `UNRESOLVED` | `UNRESOLVED` | `UNRESOLVED` |
| Currency | `wsg138/EnthusiaCurrency` | `main` | `9696501a01cc11f6e5220c5297a6f34b64204e61` |
| Market | `wsg138/EnthusiaMarket` | `main` | `bc24f1010642d6042307bc13a32fb33cc94e8883` |
| Commend | `wsg138/EnthusiaCommend` | `main` | `2083061b8aeaa7fb3adaf89746f91a45e3a03e59` |

## Boundaries

No setup action changes product Java/Kotlin/SQL, migrations, runtime configuration, workflows, deployment, authority, private data, issue #43, or production systems. Source import and every implementation package remain separate assigned work.
