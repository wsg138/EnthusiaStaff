# Assigned-package worker protocol

## Assignment is mandatory

Every implementation, external-provider, validation, production-acceptance, and final-audit channel starts with `Assigned package ID: <PACKAGE-ID>`. A worker must not choose a different package when the assigned package exists. Reassignment requires an explicit current owner instruction and must be recorded in the registry, assigned package file, and handoff; otherwise stop rather than switching packages.

## Required startup order

1. `ai-agents/AGENTS.md`
2. `ai-agents/WORKSPACE-STATE.md`
3. `PACKAGE-REGISTRY.md`
4. the assigned package file
5. its latest package handoff, or a recorded confirmation that none exists
6. `ai-agents/reports/agent-handoffs/latest.md`
7. relevant portions of `ENTHUSIASTAFF-GOALS.md`
8. `reports/PROJECT-COMPLETION-AUDIT.md`
9. `WORKSPACE-MANIFEST.md`
10. relevant portions of `reports/REQUIREMENTS-MATRIX.md`
11. relevant Wiki/development/status/operator pages
12. relevant contracts, migrations, component metadata, standalone-repository AGENTS, and provider documentation

Then reconcile live GitHub across every required repository. Verify package status, active branches/PRs, review threads, every check state, current default heads, recent merges, highest Flyway migration, issue #43, standalone availability, and active workers. Record exact starting SHAs and determine whether the action is start, resume, review, merge, synchronize, validate, audit, accept, or stop.

## Status decision rules

- `READY`: start the assigned package on its documented temporary branch.
- `ACTIVE`: resume existing branches and PRs; do not replace them because a new channel opened.
- `PARTIAL`: inspect and validate completed work, then continue the remaining checklist on the same branches/PRs unless they are irrecoverably invalid.
- `BLOCKED`: verify the blocker still exists, complete only an explicitly permitted safe subset, update evidence, and stop. Do not switch packages.
- `REVIEW`: review/fix only the assigned package.
- `MERGE_PENDING`: complete merge gates and merges only.
- `SYNC_PENDING`: synchronize the aggregate and standalone copies for the same package and prove parity. Do not start new implementation.
- `COMPLETE`: verify evidence and stop unless a later repair package explicitly reopens the area.
- `PLANNED`, `DEFERRED`, `SUPERSEDED`: do not start without legitimate registry/owner routing.

## Checkpoints and handoffs

After every major coherent section, update all three durable authorities before moving on:

1. `PACKAGE-REGISTRY.md`: canonical status, worker, branches, PRs, heads, date, handoff, and blocker.
2. The assigned package file: completed checklist, last checkpoint, resume state, remaining work, blockers, and evidence.
3. One timestamped handoff in `ai-agents/reports/package-handoffs/`: starting SHAs, active branches/PRs, latest heads, completed/incomplete work, tests/static analysis, failed/skipped/superseded checks, valid findings, unresolved threads, synchronization/parity, blocker evidence, exact next action, and systems not to disturb.

Link the handoff from the package file and registry. The handoff never overrides live GitHub or the registry.

## Stop rules

Stop only when the assigned package is `COMPLETE`, correctly `BLOCKED`, correctly `PARTIAL`, intentionally `DEFERRED`, or the assigned review/audit/acceptance campaign is complete. Never begin another package in the same channel.
