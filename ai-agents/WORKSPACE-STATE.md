# EnthusiaStaff workspace state

Last updated: 2026-08-04

This is a routing record, not a substitute for live GitHub reconciliation.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current legitimate `main` at PR #64 start | `f95d5ec404b7a4eca705bdd2ac013eb55af56a11` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `ACTIVE DRAFT — implementation and focused tests complete; exact-head validation and review resolution pending` |
| Pull request | `#64 — Block mounted movement while frozen` |
| Feature branch | `fix/freeze-mounted-movement` |
| Starting main | `f95d5ec404b7a4eca705bdd2ac013eb55af56a11` |
| Work item | Eject existing mounts when freeze becomes active or is restored, and reject new mount attempts while restricted |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-04-freeze-mounted-movement.md` |
| Migration boundary | V16 is highest; PR #64 adds no migration; V1–V16 remain immutable |
| Production authority | LiteBans remains authoritative; no deployment or cutover authority is granted |
| External blocker | RoseChat private-message evidence remains separately blocked pending a supported provider contract; never route it through issue #43 |
| Intended post-merge status | Merge PR #64 normally only after all available applicable exact-head gates pass and exact-head Pi succeeds, or direct evidence proves the permitted GitHub Actions quota/platform-unavailability exception; then verify resulting `main`, feature-head containment and branch cleanup without deploying or accessing production |

## Live start-state reconciliation

- The recorded PR #63 state was stale. Live GitHub showed PR #63 merged normally as `f95d5ec404b7a4eca705bdd2ac013eb55af56a11` from exact feature head `f308a545d0a213712ee9346655778be4d1acebb4`.
- PR #63 already had final evidence and post-merge verification comments, zero valid unresolved threads, feature-head containment, and remote branch cleanup.
- No pull request was open and only `main` existed before PR #64.
- Current `main` and the PR #63 merge commit were identical at PR #64 start.
- V16 remains the live highest migration; PR #64 changes no schema or migration bytes.
- Owner priority remains staff mode, vanish and freeze before report notifications and escalation policy.

## Confirmed defect

`FreezeManager` corrected direct player movement but did not own the mount lifecycle. A player already riding an entity when frozen was not explicitly dismounted, and a restricted player had no explicit `EntityMountEvent` guard preventing another mount attempt.

## Implemented behavior

PR #64:

- applies one shared immediate restriction path when a freeze is newly applied or restored;
- calls `Player.leaveVehicle()` before closing the inventory and sending the freeze notice;
- explicitly cancels `EntityMountEvent` at `HIGHEST` priority with `ignoreCancelled = true` when the mounting entity is a restricted player;
- reuses the existing fail-closed `FreezeRuntimeState.isRestricted` boundary;
- leaves ordinary players and all other existing freeze behavior unchanged.

No new state, persistence, scheduler, permission, command, configuration, migration, vanish, staff-mode, provider or proxy system is introduced.

## Focused tests

`FreezeInteractionCoverageTest` now proves:

- an explicit mount handler exists at the required priority and cancelled-event setting;
- pending/restricted players cannot mount;
- ordinary players retain mount behavior;
- the shared immediate restriction attempts vehicle exit, closes inventory and sends the freeze notice exactly once.

Existing freeze runtime-state tests continue to prove unrestricted defaults, fail-closed pending verification, confirmed restrictions and lifecycle-generation fencing.

## Harsh-review findings

1. **Confirmed defect fixed:** active or restored freezes did not explicitly leave an existing vehicle.
2. **Confirmed defect fixed:** restricted players had no explicit mount-event restriction.
3. **Confirmed test-maintainability defect fixed:** the first focused test duplicated the existing freeze event fixture; mount coverage was folded into `FreezeInteractionCoverageTest` and the duplicate file was removed.
4. **Confirmed test cleanup fixed:** removed an unused import and simplified the test invocation adapter.
5. **Scope preserved:** backend-switch enforcement already exists in Velocity and was not duplicated. RoseChat/provider behavior, command policy, freeze duration semantics, vanish and staff mode remain separate.

## Exact-head completion gate

Tracked content is not frozen until this handoff commit and any valid review or CI repair are complete. Before merge, require one unchanged head synchronized with current `main` and direct terminal evidence for every available applicable configured check: Java 21 build/tests, migration immutability, Paper and Velocity runtime JARs and hashes, provider-leak inspection, aggregate/diff coverage, static analysis/Codacy, Wiki validation when triggered, CodeRabbit/human review and zero valid unresolved threads.

Pi must succeed on the exact head when it executes normally. If direct evidence proves GitHub Actions quota, billing, disabled Actions or equivalent platform unavailability prevented repository code from executing, record `Pi not run — GitHub Actions quota/platform unavailable` with the exact evidence and do not claim Pi passed. A real executed product, test, migration, packaging, startup, restart or shutdown failure remains a merge blocker.

## Production boundary

PR #64 is dormant development work only. It does not authorize deployment, production data or credential access, production Discord use, EnthusiaStaff authority activation, LiteBans changes, issue #43 acceptance, migration repair or cutover.

## Next route

1. Finish PR #64 only: complete exact-head validation, inspect Codacy/CodeRabbit and every review thread, fix confirmed defects, synchronize with live `main`, and merge normally only when every gate permits it.
2. Record merge/resulting `main`, feature-head containment, no unmerged branch commits and branch cleanup in PR metadata.
3. After PR #64 completes, freshly select one bounded remaining priority-one staff-mode, vanish or freeze item.
4. Keep the RoseChat provider blocker separate and do not use issue #43 as a general blocker queue.
5. Do not begin another feature in the PR #64 session.
