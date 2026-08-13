# EnthusiaStaff workspace manifest

Last updated: 2026-08-13 (`America/Indiana/Indianapolis`)

This manifest records project orchestration and authority boundaries. It does not authorize deployment, production data, LiteBans cutover, or punishment authority.

## Checkpoint

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Setup baseline | `af9aa3d0d54afc84de7c90cb3fdc5ce3cdf9118a` |
| Current `main` | `49e5aa999b43193181aafabbb75811c820fa03c7` (PR #138 merge commit) |
| Canonical package registry | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Canonical component registry | `ai-agents/work-packages/COMPONENT-REGISTRY.md` |
| Intended state | `ROOT PLUGIN REMEDIATION AND FUNCTIONAL COMPLETION` |
| Latest completed checkpoints | ES-X02 Currency provider correction PR #137 and terminal-state publication PR #138 |
| Active implementation | ES-X03 on `package/es-x03-market-provider` in Staff and Market; durable provider and Staff coordination implemented, local exact-head validation complete, paired hosted review/merge pending |
| Codacy checkpoint | Hosted `main` reports 311 active Java warnings; grade is not asserted because the available response does not expose it. ES-X03 local delta analysis has zero PMD, Opengrep, or Trivy findings and no new Lizard findings. |
| Migration boundary | Staff `main`: V18 immutable; ES-X03 candidate: V19 added with V1-V18 unchanged. Market candidate adds V025 with V1-V024 unchanged. |
| Production authority | LiteBans remains authoritative |

## Repository model

`main` is the complete aggregate workspace. The core EnthusiaStaff plugin remains at the repository root. Assigned external packages populate their designated `components/` directory only after verifying the standalone repository, source revision, license/history, exclusions, and reviewability. There are no permanent component branches, generated split branches, or isolated-component PRs.

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

ES-X03 changes only project source, tests, V19/V025 forward migrations, documentation, and temporary package branches. It does not authorize deployment, production data, credentials, issue #43, LiteBans cutover, or production authority changes.
