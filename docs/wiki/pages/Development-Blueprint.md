# Remaining Development Map

The canonical development order now lives in the work-package system:

- [Package registry](https://github.com/wsg138/EnthusiaStaff/blob/main/ai-agents/work-packages/PACKAGE-REGISTRY.md)
- [Execution order](https://github.com/wsg138/EnthusiaStaff/blob/main/ai-agents/work-packages/EXECUTION-ORDER.md)
- [Audit coverage](https://github.com/wsg138/EnthusiaStaff/blob/main/ai-agents/work-packages/AUDIT-COVERAGE.md)
- [[Feature Completion Status|Implementation-Status]]
- [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)

## Repository checkpoint

- Package planning is the active orchestration work item; read live GitHub for its PR/merge state.
- No product implementation package is active.
- `ES-P01 — Exact-sanction appeal isolation` is the only initially `READY` package and begins only when explicitly assigned after setup merges.
- V16 is the highest Flyway migration; V1–V16 remain immutable.
- LiteBans remains authoritative; issue #43 remains a later owner-led acceptance campaign.

## How work is selected

Workers no longer choose a feature from informal priorities. Each channel receives `Assigned package ID: <PACKAGE-ID>`, reads the registry and package file, and resumes the same package until complete, correctly partial, blocked, deferred, or audited.

The dependency graph preserves the internal, provider, private validation, owner acceptance, and final no-fix audit sequence. Parallel work is allowed only when the execution-order document explicitly permits it and a live path/repository overlap check confirms safety.

## Repository model

`wsg138/EnthusiaStaff:main` is the complete aggregate workspace. The plugin remains at the root. External component copies live under `components/` and retain standalone repositories.

There are no long-lived component branches or isolated-component PRs. Internal packages normally require one PR. External packages normally require two cross-referenced PRs: standalone and aggregate. Temporary package branches are deleted after merge. External completion also requires deterministic aggregate-versus-standalone parity.

## Acceptance order

Private representative LiteBans verification (`ES-V01`), distributed Java/Bedrock/provider staging (`ES-V02`), destructive/load acceptance (`ES-V03`), owner-led LiteBans cutover acceptance (`ES-A01`), and the final no-fix audit (`ES-QA01`) remain later gates. No development merge authorizes production use.
