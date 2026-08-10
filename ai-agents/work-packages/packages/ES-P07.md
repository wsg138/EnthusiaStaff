# `ES-P07` — Inventory and Ender editing runtime completion

## 1. Package identity
`ES-P07`; Internal; primary `COMP-STAFF`; priority 45; conditionally parallel only after lifecycle files are stable.

## 2. Status
`ACTIVE`; selected by the 2026-08-10 sequential worker after live reconciliation found no actionable continuation. Branch `package/es-p07-inventory-runtime`; starting `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`.

## 3. Objective
Complete safe online/offline inventory and Ender viewing/editing, revisions, locks, queued patches, server scopes, and runtime recovery.

## 4. Why the package exists
Strong journals exist, but command/GUI/runtime mutation, crash/disconnect, nested/large inventory, and distributed ownership remain weakly proved.

## 5. Included audit IDs
`AUD-INV-001`, `AUD-INV-002`, `AUD-INV-003`, development portions of `AUD-INV-004`.

## 6. Included behavior
Permissions for view/edit; online main-thread/entity-thread mutation; offline atomic replacement/queued login patch; locks/leases/revisions/stale writes; cursor/transfer/nested containers; HUB/SMP scopes and switch fences; restart/crash recovery; bounded payloads and text fallback.

## 7. Explicit exclusions
Item confiscation (`ES-P08`); production player data; full private large-inventory/Bedrock acceptance (`ES-V02`).

## 8. Dependencies
`ES-P02` is `COMPLETE`.

## 9. Component and repository boundaries
Root inventory/domain/persistence/Paper/Velocity/tests/docs only. No external component import or permanent/isolated branch model.

## 10. Required branches
Temporary `package/es-p07-inventory-runtime`; created from exact `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`. Delete only after verified merge containment and no unique package work.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`; draft after the first coherent checkpoint.

## 12. Implementation checklist
- [x] Reconcile live GitHub, open/draft PRs, package branches, issue #43, provider resolution and default heads.
- [x] Select ES-P07 through canonical continuation/parked/ready rules.
- [x] Claim exact branch and publish active handoff/routing state.
- [ ] Trace command/GUI/journal/scope paths and current tests.
- [ ] Implement safe runtime ownership/recovery and bounded behavior.
- [ ] Add command/runtime/persistence/concurrency/nesting/restart tests.
- [ ] Update operational/Wiki/matrix documentation without overclaiming private acceptance.
- [ ] Harsh-review the final diff and resolve every valid review finding/thread.
- [ ] Freeze tracked content and validate the exact final head through every applicable hosted/static/runtime/staging gate.
- [ ] Merge normally only after exact-head evidence is complete, prove containment/divergence, clean the branch safely and publish terminal state.

## 13. Acceptance criteria
Authorized viewers/editors cannot overwrite stale state; online changes occur on the correct thread; offline patches are atomic/idempotent; cursor/transfer/nested data is preserved; backend ownership and switching are fenced; recovery does not duplicate or lose items.

## 14. Test requirements
Command/GUI wiring, permission split, locks/revisions, concurrent viewers, disconnect/process interruption, nested codecs, large bounds, queued login patch, scope switching, restart and rollback tests.

## 15. Static-analysis requirements
All configured Java/static-analysis/review-bot gates with zero valid unresolved findings.

## 16. Documentation requirements
Commands/permissions, online/offline semantics, locks/recovery/quarantine, server scope, fallback, troubleshooting, Wiki, registry/handoff.

## 17. Security and privacy requirements
Authorization at service boundary; no item/private player snapshots in artifacts; fail closed on stale ownership or storage uncertainty.

## 18. Migration impact
Current live immutable boundary is V18, not the older V16 package-definition note. No new migration is planned unless implementation proves one essential; existing V1–V18 history must not be rewritten or repaired.

## 19. Bedrock considerations
Provide command/text fallback and avoid Java-only inventory UI assumptions; representative acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Multiple backends, ownership leases, switches, duplicate patches, reconnect, shutdown, and database latency must be safe.

## 21. External-provider considerations
No external destructive provider work; preserve integration boundaries needed by later asset packages.

## 22. Completion definition
Development/runtime scope is proven and merged in one exact-head PR; no valid threads; branch cleanup verified; private acceptance remains deferred.

## 23. Resume state
Active sequential worker on `package/es-p07-inventory-runtime`; start `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`. Canonical active handoff: `ai-agents/reports/package-handoffs/2026-08-10-es-p07-inventory-runtime-active.md`.

## 24. Last completed checkpoint
Live GitHub reconciliation, classification/selection, branch claim, migration-boundary correction and active handoff publication.

## 25. Remaining checklist
Implementation, tests, documentation, harsh review, exact-head validation/staging, merge, containment, cleanup and terminal publication remain.

## 26. Known blockers
No current package blocker. Representative private large-inventory/Java-Bedrock/distributed acceptance remains intentionally deferred to `ES-V02` and is not a blocker to ES-P07 development completion.

## 27. Final evidence
Unset pending frozen head: inventory safety matrix, test/check runs, static analysis, review findings, staging disposition and exact merged head.

## 28. Merge and synchronization record
Unset pending completion: normal merge/resulting `main`/containment/divergence/temporary branch cleanup; external parity not applicable.
