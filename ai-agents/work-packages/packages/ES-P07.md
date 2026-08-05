# `ES-P07` — Inventory and Ender editing runtime completion

## 1. Package identity
`ES-P07`; Internal; primary `COMP-STAFF`; priority 45; conditionally parallel only after lifecycle files are stable.

## 2. Status
Initial `PLANNED`; registry is authoritative.

## 3. Objective
Complete safe online/offline inventory and Ender viewing/editing, revisions, locks, queued patches, server scopes, and runtime recovery.

## 4. Why the package exists
Strong journals exist, but command/GUI/runtime mutation, crash/disconnect, nested/large inventory, and distributed ownership remain weakly proved.

## 5. Included audit IDs
`AUD-INV-001`, `AUD-INV-002`, `AUD-INV-003`, development portions of `AUD-INV-004`.

## 6. Included behavior
Permissions for view/edit; online main-thread mutation; offline atomic replacement/queued login patch; locks/leases/revisions/stale writes; cursor/transfer/nested containers; HUB/SMP scopes and switch fences; restart/crash recovery; bounded payloads and text fallback.

## 7. Explicit exclusions
Item confiscation (`ES-P08`); production player data; full private large-inventory/Bedrock acceptance (`ES-V02`).

## 8. Dependencies
`ES-P02` must be `COMPLETE`.

## 9. Component and repository boundaries
Root inventory/domain/persistence/Paper/Velocity/tests/docs only. No external component import or permanent/isolated branch model.

## 10. Required branches
Temporary `package/es-p07-inventory-runtime`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
Reconcile; trace command/GUI/journal/scope paths; implement safe runtime ownership and recovery; test failures/concurrency/nesting/restart; document; checkpoint/review/freeze/validate/merge/cleanup.

## 13. Acceptance criteria
Authorized viewers/editors cannot overwrite stale state; online changes occur on correct thread; offline patches are atomic/idempotent; cursor/transfer/nested data is preserved; backend ownership and switching are fenced; recovery does not duplicate or lose items.

## 14. Test requirements
Command/GUI wiring, permission split, locks/revisions, concurrent viewers, disconnect/process interruption, nested codecs, large bounds, queued login patch, scope switching, restart and rollback tests.

## 15. Static-analysis requirements
All configured Java/static-analysis/review-bot gates with zero valid unresolved findings.

## 16. Documentation requirements
Commands/permissions, online/offline semantics, locks/recovery/quarantine, server scope, fallback, troubleshooting, Wiki, registry/handoff.

## 17. Security and privacy requirements
Authorization at service boundary; no item/private player snapshots in artifacts; fail closed on stale ownership or storage uncertainty.

## 18. Migration impact
New migration only if essential after live V16 verification; immutable clean/upgrade/checksum tests required.

## 19. Bedrock considerations
Provide command/text fallback and avoid Java-only inventory UI assumptions; representative acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Multiple backends, ownership leases, switches, duplicate patches, reconnect, shutdown, and database latency must be safe.

## 21. External-provider considerations
No external destructive provider work; preserve integration boundaries needed by later asset packages.

## 22. Completion definition
Development/runtime scope is proven and merged in one exact-head PR; no valid threads; branch cleanup verified; private acceptance remains deferred.

## 23. Resume state
Unassigned; no branch/PR/handoff. Start only after `ES-P02` and assignment.

## 24. Last completed checkpoint
Definition only; no product implementation began.

## 25. Remaining checklist
All implementation, tests, review, validation, merge, and evidence remain.

## 26. Known blockers
Dependency `ES-P02`; private/representative staging belongs to `ES-V02`.

## 27. Final evidence
Unset: exact heads, inventory safety matrix, tests/checks, review findings, docs.

## 28. Merge and synchronization record
Unset: normal merge/resulting main/containment/temporary branch cleanup; parity not applicable.
