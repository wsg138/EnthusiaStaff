# `ES-P07` — Inventory and Ender editing runtime completion

## 1. Package identity
`ES-P07`; Internal; primary `COMP-STAFF`; priority 45; conditionally parallel only after lifecycle files are stable.

## 2. Status
`ACTIVE` / `ACTIONABLE_CONTINUATION`; implementation and substantive review fixes are complete. Branch `package/es-p07-inventory-runtime`; ready-for-review PR `#112`; original starting `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`. Current `main` `2d8fcf27b0bac980211149ae8f7f4e7798998ee5` was normally merged into the package branch before final validation. Synchronized candidate `562e8647063c3fa09b4349d167dc41d1a1660553` and review-correction head `8b055b8851dd185ae5e8969148aaa11e0d1985e4` are superseded; the commit containing the final Codacy duplicate-literal correction is the new immutable validation candidate.

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
Temporary `package/es-p07-inventory-runtime`; created from exact `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`, later synchronized by a normal merge from current `main` `2d8fcf27b0bac980211149ae8f7f4e7798998ee5`. Delete only after verified merge containment and no unique package work.

## 11. Required PRs
One implementation PR to `wsg138/EnthusiaStaff:main`: ready-for-review PR `#112`.

## 12. Implementation checklist
- [x] Reconcile live GitHub, open/draft PRs, package branches, issue #43, provider resolution and default heads.
- [x] Select ES-P07 through canonical continuation/parked/ready rules and resume it when the Pi unblock condition materially changed.
- [x] Preserve and reconcile the existing implementation branch/PR rather than starting duplicate work.
- [x] Trace command/GUI/journal/scope paths, Paper lifecycle ownership, Velocity switch fences, shared confiscation callers and current tests.
- [x] Replace stale full-image player writes with exact logical dirty-slot application across storage, armor, offhand and Ender slots.
- [x] Validate every deduplicated dirty logical slot before obtaining/mutating player inventory state, preventing partial application when a later slot is invalid.
- [x] Bound aggregate serialized inventory snapshots at 32 MiB while retaining the existing 16 MiB per-item bound.
- [x] Make same-operation APPLYING lease replay idempotent without weakening competing-operation fencing.
- [x] Close additional login-recovery mutation windows for damage/resurrection, consume/durability/mending, entity interaction and Paper pick/equipment-swap paths.
- [x] Add unit and MariaDB/Testcontainers regression coverage for snapshot bounds, slot-set validation and lease replay invariants.
- [x] Verify direct permission-policy tests cover view/edit rank separation and add structural wiring coverage for `invsee`/`endersee`, authoritative target lookup, entity-scheduler handoff and GUI edit gating.
- [x] Strengthen the wiring test so both permission nodes must explicitly contain a non-null `default` field before asserting that the defaults are false.
- [x] Replace the repeated permission metadata field literal with one test constant after exact-head Codacy correctly reported the four duplicate literals.
- [x] Update inventory operational/Wiki documentation without overclaiming private acceptance.
- [x] Fix the valid CodeRabbit partial-mutation defect and exact-head Codacy synchronization warnings.
- [x] Normally merge current `main` into the package branch to restore mergeability without changing the reviewed ES-P07 product tree.
- [x] Re-run harsh review after synchronization and fix the two still-valid final CodeRabbit findings: explicit permission-default presence coverage and stale tracked freeze-state wording.
- [ ] Validate the resulting immutable head through every required exact-head hosted build/test, static-analysis/coverage, review-thread, Sentinel restart and canonical Pi gate.
- [ ] Merge normally only after exact-head evidence is complete, prove containment/divergence, clean the branch safely and publish terminal state.

## 13. Acceptance criteria
Authorized viewers/editors cannot overwrite stale state; all requested logical slots validate before any mutation; online changes occur on the correct entity thread; offline patches are atomic/idempotent; unrelated live slots are not overwritten by stale mirrors; nested item bytes are preserved; backend ownership/switching is fenced; recovery does not duplicate or lose items.

## 14. Test requirements
Existing tests cover inventory image shape/change detection, command outer permissions, authoritative rank permission inheritance, journals, patch decisions, locks/revisions, quarantine and recovery foundations. ES-P07 adds aggregate snapshot-bound tests, mixed-valid/invalid logical-slot validation coverage, MariaDB/Testcontainers same-owner APPLYING-lease replay coverage, and `InventoryWorkflowWiringTest` proving both inventory commands bind to one workflow, preserve authoritative directory/entity-scheduler routing, keep view/edit GUI authority separate, and explicitly declare non-null default-false metadata for both permissions. Representative multi-backend/Bedrock/large-private-inventory acceptance remains ES-V02.

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
Development/runtime scope must be exact-head validated and merged in one implementation PR; no valid review threads; containment/cleanup verified; representative broader private acceptance remains deferred where assigned to ES-V02.

## 23. Resume state
Generic sequential worker remains on `package/es-p07-inventory-runtime`; PR `#112`; original start `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`; synchronized target main `2d8fcf27b0bac980211149ae8f7f4e7798998ee5`. Canonical active handoff: `ai-agents/reports/package-handoffs/2026-08-10-es-p07-inventory-runtime-active.md`.

## 24. Last completed checkpoint
Product implementation is complete. The branch was normally synchronized with current `main` as merge commit `562e8647063c3fa09b4349d167dc41d1a1660553`, restoring PR mergeability without changing ES-P07 product files. Exact hosted validation on that synchronized candidate passed Java 21 clean/full tests, MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, aggregate JaCoCo, Codacy coverage upload and Wiki; CodeRabbit then found two valid final-review issues. Review-correction head `8b055b8851dd185ae5e8969148aaa11e0d1985e4` fixed both, but exact-head Codacy static analysis then reported one valid maintainability warning because the newly added string literal `"default"` appeared four times in `InventoryWorkflowWiringTest`. The final static correction introduces one `DEFAULT_FIELD` constant and uses it for both presence and value assertions.

Earlier sanity/freeze heads `321a50f3ca120dba7d7e21542450d4d527dabbd3`, `c3545612d370ea237a63394e6a0401edbe650790`, `6c7ec06622b8ee20d00aa3839e5741f44a0f1976`, `2c0ad0543f9f39f242e8a75c532a77ca2afb52de`, `b34aade6ae79c7aaada0ada3c87970f937b6db6a`, `562e8647063c3fa09b4349d167dc41d1a1660553`, and `8b055b8851dd185ae5e8969148aaa11e0d1985e4` are all non-final evidence. No result bound to them may be used for final acceptance.

The first recovery-guard compile attempt remains non-passing. Sentinel restart work, canonical Pi work, hosted tests and review/static results tied to any superseded head remain non-final even if they later execute or succeed; final acceptance requires fresh evidence for the resulting static-correction head.

## 25. Remaining checklist
Treat the commit containing this final Codacy correction as the new immutable freeze. Require fresh exact-head Java 21 build/tests with MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, Wiki, configured static analysis/coverage, CodeRabbit/reviewer completion with zero valid unresolved threads, Sentinel exact artifact plus terminal `PAPER_RESTART_OK`, and canonical automatic public→private Pi evidence with correlated provenance/restart/MariaDB/Flyway/cleanup. Merge normally only if unchanged and fully green; then verify containment/divergence/cleanup, publish COMPLETE state and dependency-derived routing, and stop without starting another package.

## 26. Known blockers
No product blocker remains. Runtime execution depends on the shared Pi becoming available and meeting the existing Sentinel resource gate. Do not cancel or interfere with unrelated legitimate Sentinel or Staging jobs. A queued, stale, wrong-head, cancelled or superseded runtime result is not a pass.

## 27. Final evidence
Pending fresh exact-head evidence for the immutable head produced by this final static-analysis correction. `8b055b8851dd185ae5e8969148aaa11e0d1985e4` and every predecessor are explicitly superseded and cannot be final acceptance.

## 28. Merge and synchronization record
Current `main` `2d8fcf27b0bac980211149ae8f7f4e7798998ee5` was normally merged into the package branch as `562e8647063c3fa09b4349d167dc41d1a1660553`. Final implementation merge/resulting `main`, containment/divergence and temporary branch cleanup remain unset pending the new exact-head gates. External parity is not applicable for internal `COMP-STAFF` scope.
