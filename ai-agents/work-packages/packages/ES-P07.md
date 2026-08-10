# `ES-P07` — Inventory and Ender editing runtime completion

## 1. Package identity
`ES-P07`; Internal; primary `COMP-STAFF`; priority 45; conditionally parallel only after lifecycle files are stable.

## 2. Status
`ACTIVE`; implementation, required direct wiring coverage, substantive CodeRabbit fixes, Codacy fixes and repeated harsh review are complete; tracked content is being re-frozen for exact-head validation. Branch `package/es-p07-inventory-runtime`; ready-for-review PR `#112`; starting `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`.

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
One PR to `wsg138/EnthusiaStaff:main`: ready-for-review PR `#112`.

## 12. Implementation checklist
- [x] Reconcile live GitHub, open/draft PRs, package branches, issue #43, provider resolution and default heads.
- [x] Select ES-P07 through canonical continuation/parked/ready rules.
- [x] Claim exact branch and publish active handoff/routing state.
- [x] Trace command/GUI/journal/scope paths, Paper lifecycle ownership, Velocity switch fences, shared confiscation callers and current tests.
- [x] Replace stale full-image player writes with exact logical dirty-slot application across storage, armor, offhand and Ender slots.
- [x] Validate every deduplicated dirty logical slot before obtaining/mutating player inventory state, preventing partial application when a later slot is invalid.
- [x] Bound aggregate serialized inventory snapshots at 32 MiB while retaining the existing 16 MiB per-item bound.
- [x] Make same-operation APPLYING lease replay idempotent without weakening competing-operation fencing.
- [x] Close additional login-recovery mutation windows for damage/resurrection, consume/durability/mending, entity interaction and Paper pick/equipment-swap paths.
- [x] Add unit and MariaDB/Testcontainers regression coverage for snapshot bounds, slot-set validation and lease replay invariants.
- [x] Verify direct permission-policy tests cover view/edit rank separation and add direct structural wiring coverage for `invsee`/`endersee`, authoritative target lookup, entity-scheduler handoff and GUI edit gating.
- [x] Update inventory operational/Wiki documentation without overclaiming private acceptance.
- [x] Harsh-review the product diff; fix the valid CodeRabbit partial-mutation defect; repeat review; resolve all valid review threads.
- [x] Inspect exact-head Codacy annotations and remove unnecessary method-level synchronization from the encode-local bounded byte stream; no lock is required because each stream instance is confined to one `encode` invocation.
- [ ] Freeze the resulting tracked head and validate that exact revision through all applicable hosted build/test, static-analysis/coverage, review-thread, Sentinel restart and canonical Pi gates.
- [ ] Merge normally only after exact-head evidence is complete, prove containment/divergence, clean the branch safely and publish terminal state.

## 13. Acceptance criteria
Authorized viewers/editors cannot overwrite stale state; all requested logical slots validate before any mutation; online changes occur on the correct entity thread; offline patches are atomic/idempotent; unrelated live slots are not overwritten by stale mirrors; nested item bytes are preserved; backend ownership/switching is fenced; recovery does not duplicate or lose items.

## 14. Test requirements
Existing tests cover inventory image shape/change detection, command outer permissions, authoritative rank permission inheritance, journals, patch decisions, locks/revisions, quarantine and recovery foundations. ES-P07 adds aggregate snapshot-bound tests, mixed-valid/invalid logical-slot validation coverage, MariaDB/Testcontainers same-owner APPLYING-lease replay coverage, and `InventoryWorkflowWiringTest` proving both inventory commands bind to one workflow, preserve authoritative directory/entity-scheduler routing, and keep view/edit GUI authority separate. Representative multi-backend/Bedrock/large-private-inventory acceptance remains ES-V02.

## 15. Static-analysis requirements
All configured Java/static-analysis/review-bot gates with zero valid unresolved findings on the final frozen head.

## 16. Documentation requirements
Commands/permissions, online/offline semantics, locks/recovery/quarantine, server scope, fallback, troubleshooting, Wiki, registry/handoff.

## 17. Security and privacy requirements
Authorization remains at service/command boundaries; no private player snapshots are added to repository artifacts; stale ownership or storage uncertainty fails closed.

## 18. Migration impact
Current live immutable boundary is V18. ES-P07 adds no migration and does not rewrite or repair V1–V18.

## 19. Bedrock considerations
The staff surface remains vanilla inventory/command-based with text messages rather than a custom Java-only UI. Representative Bedrock acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Velocity already fences backend switches on durable inventory locks and Paper scopes inventory observations/patches by backend scope. Representative multi-backend contention/switch acceptance remains `ES-V02`.

## 21. External-provider considerations
No external destructive provider work was added. Shared confiscation/restoration callers of `InventoryImageCodec.apply` were reviewed only to preserve their existing checksum/lock semantics; ES-P08 behavior was not implemented here.

## 22. Completion definition
Development/runtime scope must be exact-head validated and merged in one implementation PR; no valid review threads; containment/cleanup verified; representative private acceptance remains deferred.

## 23. Resume state
Generic sequential worker remains on `package/es-p07-inventory-runtime`; PR `#112`; start `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`. Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-10-es-p07-inventory-runtime-active.md`.

## 24. Last completed checkpoint
Implementation, documentation, direct wiring coverage and repeated harsh review are complete. Intermediate sanity head `321a50f3ca120dba7d7e21542450d4d527dabbd3` passed Java 21 full build/tests, MariaDB/Testcontainers, runtime-JAR inspection, aggregate JaCoCo/Codacy upload, Wiki and Sentinel artifact sanity checks.

Freeze `c3545612d370ea237a63394e6a0401edbe650790` was superseded by the direct command/GUI wiring test. Freeze `6c7ec06622b8ee20d00aa3839e5741f44a0f1976` was superseded by the valid CodeRabbit partial-mutation finding. Freeze `2c0ad0543f9f39f242e8a75c532a77ca2afb52de` was superseded when exact-head Codacy static analysis reported two valid warnings on method-level synchronization in the encode-local bounded stream. Those `synchronized` modifiers were removed rather than replaced with unnecessary locks. Every gate tied to those superseded heads remains non-final evidence.

The first recovery-guard compile attempt failed because two Paper events were imported from the Bukkit namespace; the imports were corrected to `io.papermc.paper.event.player`. Exact-head Sentinel attempts on `2c0ad054...` also failed infrastructure resource preconditions (637 MB available versus 700 MB required); those failures are not Paper restart passes. No failed or superseded run is relabeled as passing evidence.

## 25. Remaining checklist
Complete current handoff/workspace records; take the resulting PR head as the new immutable freeze; require exact frozen-head hosted build/test, static-analysis/coverage, review-thread, Sentinel restart and canonical Pi evidence; merge normally only if unchanged and fully green; verify containment/divergence/cleanup; publish COMPLETE state and dependency-derived routing; stop without starting another package.

## 26. Known blockers
No current product blocker. Sentinel previously observed a transient Pi resource precondition below its 700 MB threshold; do not repeat the identical restart gate until evidence shows the host resource condition changed. Representative private large-inventory/Java-Bedrock/distributed acceptance remains intentionally deferred to `ES-V02` and is not a blocker to ES-P07 development completion.

## 27. Final evidence
Pending the new frozen head produced after the Codacy fix. All prior validation heads are superseded and may not be cited as final acceptance.

## 28. Merge and synchronization record
Unset pending completion: normal merge/resulting `main`, containment/divergence, temporary branch cleanup; external parity not applicable.
