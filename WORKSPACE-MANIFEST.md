# EnthusiaStaff workspace manifest

Last updated: 2026-08-05 (`America/Indiana/Indianapolis`)

This manifest records project orchestration and authority boundaries. It does not authorize deployment, production data, LiteBans cutover, or punishment authority.

## Checkpoint

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Setup baseline | `af9aa3d0d54afc84de7c90cb3fdc5ce3cdf9118a` |
| Canonical package registry | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Canonical component registry | `ai-agents/work-packages/COMPONENT-REGISTRY.md` |
| Intended state | `PACKAGE-PLANNING READY` |
| Next assigned package | `ES-P01` |
| Active implementation | `NONE` |
| Migration boundary | V16; V1–V16 immutable |
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
