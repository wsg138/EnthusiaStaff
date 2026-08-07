# Sequential package worker protocol

## Automatic package selection

Every implementation, external-provider, validation, production-acceptance, and final-audit channel begins by reconciling live GitHub and classifying every incomplete package. An owner-supplied package ID is optional. Select exactly one package through the canonical registry and the rules below.

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

Then reconcile live GitHub across every required repository. Verify package status, active branches and PRs, review threads, every check state, current default heads, recent merges, highest Flyway migration, issue #43, standalone availability, and active workers. Record exact starting SHAs.

## Phase A: classify every incomplete package

### `ACTIONABLE_CONTINUATION`

Existing work with a safe action available now. This includes unfinished implementation; an actual compile or test failure; a valid review finding; a merge conflict after all external blockers cleared; exact-head validation that can presently execute; merge-ready work; incomplete external synchronization; incomplete post-merge finalization; containment or safe branch cleanup; or a blocker whose exact unblock condition demonstrably changed.

### `PARKED_BLOCKED`

Work whose required next action still depends on the same unavailable external condition and which has no other actionable defect. Examples include the same unavailable runner, inaccessible repository, missing provider contract, missing private environment, missing owner authorization, or unavailable credentials or dataset.

A parked blocked package may retain an open PR and branch. The open PR or branch does not make it actionable. Being behind `main`, or becoming non-mergeable because `main` advanced, does not make it actionable while the external blocker remains unchanged.

Do not merge `main` into a parked blocked branch merely to keep it current. Synchronize the branch only after the unblock condition changes, or when synchronization is itself necessary to evaluate a newly changed condition.

Do not repeatedly rerun an identical zero-runner or unavailable-infrastructure gate without evidence that runner capacity, billing, authorization, configuration, or environment availability changed. A manual rerun alone is not evidence that the unblock condition changed. Merely updating blocker documentation does not convert a parked package into an actionable continuation.

### `READY`

A dependency-complete package eligible to begin after live conflict, duplicate-work, repository, and package-contract checks.

## Phase B: select

1. Select the highest-priority `ACTIONABLE_CONTINUATION`. When multiple continuations are otherwise equal, use dependency safety, lowest numerical priority, then oldest active PR.
2. Skip every `PARKED_BLOCKED` package.
3. Otherwise claim the eligible `READY` package with the lowest numerical priority.
4. When nothing is actionable or ready, report every incomplete package, its blocker or unmet dependency, and the exact condition required to continue; then stop.

An open PR alone does not receive priority. An open branch alone does not receive priority. Existing actionable unfinished work always takes priority over new work. When a parked package's exact unblock condition changes, or another real actionable defect appears, reclassify it as `ACTIONABLE_CONTINUATION` and resume it before starting another new package. Do not work concurrently on a package marked `Parallel safe: No`.

## Status decision rules

- `READY`: create the documented temporary branch, mark the package `ACTIVE`, record the exact starting SHA, update routing, create a handoff, and open a draft PR after the first coherent checkpoint.
- `ACTIVE`: resume only when live evidence classifies the package as actionable.
- `PARTIAL`: inspect and validate completed work, then continue remaining work when actionable.
- `BLOCKED`: complete all safe actionable work, publish the exact blocker and unblock condition, and park the package while the condition is unchanged.
- `REVIEW`: review and repair only the selected package when review can presently proceed.
- `MERGE_PENDING`: complete merge gates, merge, containment, cleanup, and canonical finalization when all prerequisite blockers are clear.
- `SYNC_PENDING`: synchronize aggregate and standalone copies for the same package and prove parity when required repositories and conditions are available.
- `COMPLETE`: verify evidence and stop unless a later repair package explicitly reopens the area.
- `PLANNED`, `DEFERRED`, `SUPERSEDED`: do not start without legitimate routing.

## Checkpoints and handoffs

After every major coherent section, update all three durable authorities before moving on:

1. `PACKAGE-REGISTRY.md`: canonical status, worker, branches, PRs, heads, date, handoff, and blocker.
2. The selected package file: completed checklist, last checkpoint, resume state, remaining work, blockers, and evidence.
3. One timestamped handoff in `ai-agents/reports/package-handoffs/`: starting SHAs, active branches and PRs, latest heads, completed and incomplete work, tests and static analysis, failed, skipped, or superseded checks, valid findings, unresolved threads, synchronization and parity, blocker evidence, exact next action, and systems not to disturb.

Link the handoff from the package file and registry. The handoff never overrides live GitHub or the registry.

## Persistent status publication

When a package worker stops with an unmerged implementation PR in `PARTIAL`, `BLOCKED`, `REVIEW`, `MERGE_PENDING`, or `SYNC_PENDING`, and `main` does not already reflect that state, the same worker must create and normally merge a small documentation-only status-publication PR to `main` before stopping.

The status-publication PR may update only:

- `PACKAGE-REGISTRY.md`;
- the selected package file;
- `WORKSPACE-STATE.md`;
- the canonical package handoff;
- `agent-handoffs/latest.md`;
- directly necessary routing documentation.

It is not a second implementation package. It must not contain product code, product tests, migrations, workflow changes, or runtime configuration. It must not merge or close the implementation PR and must preserve the implementation branch. Use a normal merge commit.

Publish the true status, implementation branch, PR, current package-record head, frozen product head when applicable, blocker evidence, and exact unblock condition. Live GitHub still overrides stale documentation, but the system must not knowingly leave the canonical registry stale.

A package worker may stop only after persistent state is available from `main`, unless tool loss makes publication impossible. When publication is impossible, the final report must identify the inconsistency as unfinished work.

## Stop rules

Stop only when the selected package is `COMPLETE`, correctly `BLOCKED`, correctly `PARTIAL`, intentionally `DEFERRED`, or the selected review, audit, or acceptance campaign is complete. Publish persistent state and update dependency-derived statuses without activating the next package. Never begin another package in the same channel.
