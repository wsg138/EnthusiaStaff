# EnthusiaStaff workspace state

Last updated: 2026-08-05

This is a routing record. Live GitHub and `ai-agents/work-packages/PACKAGE-REGISTRY.md` must be reconciled before acting.

## Repository

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Setup starting main | `af9aa3d0d54afc84de7c90cb3fdc5ce3cdf9118a` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper/Leaf backends, Velocity, MariaDB |
| Highest migration | `V16`; V1–V16 remain immutable |
| Issue #43 | Open; owner-led LiteBans cutover acceptance remains deferred |

## Intended post-merge state

`PACKAGE-PLANNING READY — canonical package registry and aggregate/standalone synchronization model are merged; no implementation package is active; ES-P01 is the next assigned package.`

| Field | Value |
| --- | --- |
| Canonical package status | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Next package | `ES-P01 — Exact-sanction appeal isolation` |
| Active implementation package | `NONE` |
| Active implementation branch/PR | `NONE` |
| Setup branch | `package/es-setup-workspace-orchestration` |
| Setup PR | `PENDING` |
| Setup handoff | `ai-agents/reports/package-handoffs/2026-08-05-package-planning-setup.md` |
| Production authority | LiteBans remains authoritative |

## Component state

Verified standalone repositories: site, Currency, Market, and Commend at the heads in `COMPONENT-REGISTRY.md`. RoseChat is unresolved. Aggregate directories contain metadata only; no external source import has occurred.

No long-lived component branches exist or are part of the design. No isolated-component PR is required. Internal packages require one PR; external packages normally require two cross-referenced PRs. Temporary branches are deleted after merge when safe.

## Permanent boundaries

- No product code, migration, workflow, runtime configuration, deployment, authority, private data, LiteBans acceptance, or production behavior changes in setup.
- No implementation package starts until a worker is explicitly assigned a `READY` package.
- No private database or derived rows may be uploaded.
