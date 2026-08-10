# `ES-P07` — Inventory and Ender editing runtime completion

## 1. Package identity
`ES-P07`; Internal; primary `COMP-STAFF`; priority 45; conditionally parallel only after lifecycle files are stable.

## 2. Status
`ACTIVE`; implementation and harsh source review are complete and tracked content is being frozen for exact-head validation. Branch `package/es-p07-inventory-runtime`; PR `#112`; starting `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`.

## 3. Objective
Complete safe online/offline inventory and Ender viewing/editing, revisions, locks, queued patches, server scopes, and runtime recovery.

## 4. Why the package exists
Strong journals existed, but command/GUI/runtime mutation, crash/disconnect, nested/large inventory, and distributed ownership were weakly proved.

## 5. Included audit IDs
`AUD-INV-001`, `AUD-INV-002`, `AUD-INV-003`, development portions of `AUD-INV-004`.

## 6. Included behavior
Permissions for view/edit; online entity-thread mutation; offline authoritative observation/queued login patch; locks/leases/revisions/stale writes; cursor/transfer/nested containers; HUB/SMP scopes and switch fences; restart/crash recovery; bounded payloads and vanilla command/UI fallback.

## 7. Explicit exclusions
Item confiscation/restoration completion (`ES-P08`); production player data; provider-backed destructive work; representative private large-inventory, multi-backend and Java/Bedrock acceptance (`ES-V02`).

## 8. Dependencies
`ES-P02` is `COMPLETE`.

## 9. Component and repository boundaries
Root inventory/domain/persistence/Paper/Velocity/tests/docs only. No external component import or permanent/isolated branch model.

## 10. Required branches
Temporary `package/es-p07-inventory-runtime`; created from exact `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`. Delete only after verified merge containment and no unique package work.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`: draft PR `#112`, to be marked ready only after tracked-content freeze.

## 12. Implementation checklist
- [x] Reconcile live GitHub, open/draft PRs, package branches, issue #43, provider resolution and default heads.
- [x] Select ES-P07 through canonical continuation/parked/ready rules.
- [x] Claim exact branch and publish active handoff/routing state.
- [x] Trace command/GUI/journal/scope paths, Paper lifecycle ownership, Velocity switch fences, shared confiscation callers and current tests.
- [x] Replace stale full-image player writes with exact logical dirty-slot application across storage, armor, offhand and Ender slots.
- [x] Bound aggregate serialized inventory snapshots at 32 MiB while retaining the existing 16 MiB per-item bound.
- [x] Make same-operation APPLYING lease replay idempotent without weakening competing-operation fencing.
- [x] Close additional login-recovery mutation windows for damage/resurrection, consume/durability/mending, entity interaction and Paper pick/equipment-swap paths.
- [x] Add unit and MariaDB/Testcontainers regression coverage for the changed snapshot and lease invariants.
- [x] Update inventory operational/Wiki documentation without overclaiming private acceptance.
- [x] Harsh-review the complete product diff for scope, lifecycle, threading, stale writes, recovery, bounds, shared callers and deferred-provider boundaries.
- [ ] Freeze the resulting tracked head and validate that exact revision through all applicable hosted/static/review/Sentinel/canonical Pi gates.
- [ ] Merge normally only after exact-head evidence is complete, prove containment/divergence, clean the branch safely and publish terminal state.

## 13. Acceptance criteria
Authorized viewers/editors cannot overwrite stale state; online changes occur on the correct entity thread; offline patches are atomic/idempotent; unrelated live slots are not overwritten by stale mirrors; nested item bytes are preserved; backend ownership/switching is fenced; recovery does not duplicate or lose items.

## 14. Test requirements
The existing suite covers image shape/change detection, journals, patch decisions, locks/revisions, quarantine and recovery foundations. This package adds aggregate snapshot-bound tests and MariaDB/Testcontainers same-owner APPLYING-lease replay coverage. Full private multi-backend/Bedrock/large-inventory acceptance remains ES-V02 and is not represented as automated local proof.

## 15. Static-analysis requirements
All configured Java/static-analysis/review-bot gates with zero valid unresolved findings on the final frozen head.

## 16. Documentation requirements
Commands/permissions, online/offline semantics, locks/recovery/quarantine, server scope, fallback, troubleshooting, Wiki, registry/handoff.

## 17. Security and privacy requirements
Authorization remains at service/command boundaries; no private player snapshots are added to repository artifacts; stale ownership or storage uncertainty fails closed.

## 18. Migration impact
Current live immutable boundary is V18. ES-P07 adds no migration and does not rewrite or repair V1–V18.

## 19. Bedrock considerations
The staff surface remains vanilla inventory/command based with text messages rather than a custom Java-only UI. Representative Bedrock acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Velocity already fences backend switches on durable inventory locks and Paper scopes inventory observations/patches by backend scope. Representative multi-backend contention/switch acceptance remains `ES-V02`.

## 21. External-provider considerations
No external destructive provider work was added. Shared confiscation/restoration callers of `InventoryImageCodec.apply` were reviewed only to preserve their existing checksum/lock semantics; ES-P08 behavior was not implemented here.

## 22. Completion definition
Development/runtime scope must be exact-head validated and merged in one implementation PR; no valid review threads; containment/cleanup verified; representative private acceptance remains deferred.

## 23. Resume state
Generic sequential worker remains on `package/es-p07-inventory-runtime`; PR `#112`; start `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`. Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-10-es-p07-inventory-runtime-active.md`.

## 24. Last completed checkpoint
Implementation, documentation and harsh source review are complete. Intermediate sanity head `321a50f3ca120dba7d7e21542450d4d527dabbd3` passed Coverage run `31422933262` / job `93567841848`: exact Java 21 full build/tests, MariaDB/Testcontainers, runtime-JAR inspection, aggregate JaCoCo and Codacy coverage upload. Aggregate coverage was 47.12% lines / 38.19% branches / 49.78% instructions; provider API leaks were zero across Paper and Velocity runtime JARs. Wiki run `31422933273` and Sentinel Restart Artifact run `31422933425` also succeeded on that intermediate head. These runs are sanity evidence only; they do not substitute for final frozen-head validation.

The first recovery-guard compile attempt failed because two Paper events were imported from the Bukkit namespace; the imports were corrected to `io.papermc.paper.event.player` and the subsequent full sanity build passed. No failed run is relabeled as passing evidence.

## 25. Remaining checklist
Freeze the tracked head; mark PR #112 ready; require exact frozen-head hosted/static/review/Sentinel/canonical Pi evidence; merge normally only if unchanged and fully green; verify containment/divergence/cleanup; publish COMPLETE state and dependency-derived routing; stop without starting another package.

## 26. Known blockers
No current ES-P07 implementation blocker. Representative private large-inventory/Java-Bedrock/distributed acceptance remains intentionally deferred to `ES-V02` and is not a blocker to ES-P07 development completion.

## 27. Final evidence
Pending the final frozen head. Intermediate evidence above must not be cited as final if tracked content changes after `321a50f3ca120dba7d7e21542450d4d527dabbd3`.

## 28. Merge and synchronization record
Unset pending completion: normal merge/resulting `main`, containment/divergence, temporary branch cleanup; external parity not applicable.
