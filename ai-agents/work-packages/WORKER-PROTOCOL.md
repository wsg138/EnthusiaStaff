# Sequential package worker protocol

## Automatic package selection

Every implementation, external-provider, validation, production-acceptance, and final-audit channel begins by reconciling live GitHub. An owner-supplied package ID is optional. Resume unfinished package work when it exists; otherwise select exactly one package through the canonical registry and the rules below.

A direct current owner instruction may assign or reassign a package. Record that routing change in the registry, selected package file, workspace state, and handoff. Do not invent packages, renumber them, replace their scope, or begin a second package.

## Required startup order

1. `ai-agents/AGENTS.md`
2. `ai-agents/WORKSPACE-STATE.md`
3. `PACKAGE-REGISTRY.md`
4. this protocol
5. `EXECUTION-ORDER.md`
6. `BRANCH-AND-MIRROR-POLICY.md`
7. `VALIDATION-POLICY.md`
8. `COMPONENT-REGISTRY.md`
9. `ai-agents/reports/agent-handoffs/latest.md`
10. the selected package file
11. its latest package handoff, or a recorded confirmation that none exists
12. relevant goals, audit, manifest, requirements matrix, Wiki, contracts, migrations, component metadata, standalone-repository instructions, and provider documentation

Then reconcile live GitHub across every required repository. Verify package status, active branches/PRs, review threads, every check state, current default heads, recent merges, highest Flyway migration, issue #43, standalone availability, and active workers. Record exact starting SHAs and determine whether the action is start, resume, review, merge, synchronize, validate, audit, accept, or stop.

## Selection order

1. Resume an existing package PR or package branch that still needs implementation, repair, exact-head validation, merge, synchronization, finalization, containment, or cleanup.
2. When no live work determines the package, continue the lowest-priority-number package in this status order: `ACTIVE`, `PARTIAL`, `REVIEW`, `MERGE_PENDING`, `SYNC_PENDING`.
3. Recheck a `BLOCKED` package only when its unblock condition may have changed, new authorization exists, live evidence contradicts the stored blocker, or its records are inconsistent. Leave unchanged blockers alone and continue selection.
4. When no continuation work exists, claim the eligible `READY` package with the lowest numerical priority after verifying dependencies, conflicts, duplicate implementation, package accuracy, and required repositories.
5. When no package is eligible, report every incomplete package, its status, blocker or unmet dependency, and the exact condition required to continue; then stop.

Existing unfinished work always takes priority over a new branch. When multiple unfinished packages exist, use continuation status, dependency safety, lowest numerical priority, then oldest active PR. Do not work concurrently on a package marked `Parallel safe: No`.

## Status decision rules

- `READY`: create the documented temporary branch, mark the package `ACTIVE`, record the exact starting SHA, update routing, create a handoff, and open a draft PR after the first coherent checkpoint.
- `ACTIVE`: resume existing branches and PRs; do not replace them because a new channel opened.
- `PARTIAL`: inspect and validate completed work, then continue the remaining checklist on the same branches/PRs unless they are irrecoverably invalid.
- `BLOCKED`: verify the blocker still exists, complete all actionable safe work, update evidence, and stop.
- `REVIEW`: review and repair only the selected package.
- `MERGE_PENDING`: complete merge gates, merge, containment, cleanup, and canonical finalization only.
- `SYNC_PENDING`: synchronize the aggregate and standalone copies for the same package and prove parity.
- `COMPLETE`: verify evidence and stop unless a later repair package explicitly reopens the area.
- `PLANNED`, `DEFERRED`, `SUPERSEDED`: do not start without legitimate routing.

## Checkpoints and handoffs

After every major coherent section, update all three durable authorities before moving on:

1. `PACKAGE-REGISTRY.md`: canonical status, worker, branches, PRs, heads, date, handoff, and blocker.
2. The selected package file: completed checklist, last checkpoint, resume state, remaining work, blockers, and evidence.
3. One timestamped handoff in `ai-agents/reports/package-handoffs/`: starting SHAs, active branches/PRs, latest heads, completed/incomplete work, tests/static analysis, failed/skipped/superseded checks, valid findings, unresolved threads, synchronization/parity, blocker evidence, exact next action, and systems not to disturb.

Link the handoff from the package file and registry. The handoff never overrides live GitHub or the registry.

## Stop rules

Stop only when the selected package is `COMPLETE`, correctly `BLOCKED`, correctly `PARTIAL`, intentionally `DEFERRED`, or the selected review/audit/acceptance campaign is complete. After completion, update dependency-derived statuses without activating the next package. Never begin another package in the same channel.
