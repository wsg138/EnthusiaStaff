# Assigned-package worker protocol

## Assignment is mandatory

Every implementation/validation channel starts with `Assigned package ID: <PACKAGE-ID>`. A worker must not choose a different package when the assigned package exists. A newer explicit owner instruction may reassign the package; the worker must record that change rather than silently switching.

## Required startup order

1. Read `ai-agents/AGENTS.md`.
2. Read `ai-agents/WORKSPACE-STATE.md`.
3. Read `PACKAGE-REGISTRY.md`.
4. Read the assigned package file.
5. Read its latest package handoff, or confirm none exists.
6. Reconcile live GitHub across every required repository.
7. Verify package status, active branches/PRs, review threads, checks, current default heads, and highest Flyway migration.
8. Record exact starting SHAs and determine whether the action is start, resume, review, merge, synchronize, validate, or stop.

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

After each major coherent section, update:

- canonical package status;
- completed checklist and last checkpoint;
- all active temporary branches and PRs;
- latest pushed heads;
- tests/static analysis run;
- review state and unresolved threads;
- blockers and evidence;
- exact next action.

Maintain one timestamped handoff in `ai-agents/reports/package-handoffs/`. The handoff must include package ID/status, starting SHAs, active branches, PR links, completed and incomplete work, failed checks, valid findings, unresolved threads, blocker evidence, exact next action, and systems not to disturb. Link it from the package file and registry. It does not override live GitHub or the registry.

## Stop rules

Stop only when the assigned package is `COMPLETE`, correctly `BLOCKED`, correctly `PARTIAL`, intentionally `DEFERRED`, or the requested review/audit is complete. Never begin the next package in the same channel.
