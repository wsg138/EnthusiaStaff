# EnthusiaStaff work-package system

This directory is the durable orchestration authority for future ChatGPT and Codex work.

- `PACKAGE-REGISTRY.md` is the single canonical status index.
- `packages/<PACKAGE-ID>.md` defines scope, evidence, completion, and resume state.
- `EXECUTION-ORDER.md` defines dependencies and parallel-safety rules.
- `COMPONENT-REGISTRY.md` records the aggregate paths and verified standalone repositories.
- `WORKER-PROTOCOL.md` controls assignment, startup, resume, checkpoints, and stopping.
- `BRANCH-AND-MIRROR-POLICY.md` retains its historical filename but now governs temporary package branches and aggregate-versus-standalone synchronization only.
- `VALIDATION-POLICY.md` controls review, tests, static analysis, parity, and merge gates.
- `AUDIT-COVERAGE.md` accounts for all 99 audit IDs.

A handoff records the latest execution attempt. It never overrides live GitHub, the registry, or the assigned package file.

## Repository model

- `wsg138/EnthusiaStaff:main` is the complete aggregate workspace.
- The EnthusiaStaff plugin remains at the repository root.
- External component copies live under `components/<component>/` and retain independent standalone repositories.
- There are no long-lived component branches and no isolated-component PRs.
- Internal packages normally require one temporary branch and one PR to `EnthusiaStaff:main`.
- External packages normally require two temporary branches and two cross-referenced PRs: one in the standalone repository and one to `EnthusiaStaff:main`.
- Temporary package branches are deleted after merge when they contain no unique work.

## Status machine

| Status | Meaning |
| --- | --- |
| `PLANNED` | Defined, but dependency or execution order prevents starting. |
| `READY` | Fully defined, dependencies complete, and no conflicting worker is active. |
| `ACTIVE` | A worker started the package and has an active temporary branch or PR. |
| `PARTIAL` | Meaningful work exists, but completion criteria are unmet; resume the same branches and PRs. |
| `BLOCKED` | A named repository, owner decision, environment, credential, private dataset, or infrastructure limitation prevents progress. |
| `REVIEW` | Implementation is believed complete and the required PR or PRs are under review. |
| `MERGE_PENDING` | Reviews and checks passed, but one or more required PRs have not merged. |
| `SYNC_PENDING` | One repository merged, but the aggregate or standalone copy is not synchronized or parity is not proved. |
| `COMPLETE` | Every included criterion passed, every required PR merged, parity passed when required, final heads/merges are recorded, and no valid review thread remains. |
| `DEFERRED` | Intentionally reserved for a later private, staging, acceptance, or owner-authorized stage. |
| `SUPERSEDED` | Replaced by a named newer package ID. |

Only `PACKAGE-REGISTRY.md` declares canonical current status. Disagreement with a package file or handoff must be reconciled immediately against live GitHub.
