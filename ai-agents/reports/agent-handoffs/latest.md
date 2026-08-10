# Latest AI handoff

Current active package handoff:

[`2026-08-10-es-p07-inventory-runtime-active.md`](../package-handoffs/2026-08-10-es-p07-inventory-runtime-active.md)

Prior terminal package handoff:

[`2026-08-10-es-v01-private-litebans-representative-verification-complete.md`](../package-handoffs/2026-08-10-es-v01-private-litebans-representative-verification-complete.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P07 — Inventory and Ender editing runtime completion` remains the only active package on `package/es-p07-inventory-runtime`, ready PR `#112`, created exactly from reconciled `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`. Implementation, direct command/GUI wiring coverage, substantive CodeRabbit fixes, exact-head Codacy fixes and repeated harsh source review are complete. **The commit produced by this latest-handoff update is the immutable final validation head.** If any exact-head gate identifies a real ES-P07 defect, fix it, repeat the harsh review, freeze the new head, and rerun every exact-head gate before merge.

The package completes exact dirty-slot inventory/Ender writes, complete dirty-slot-set prevalidation, aggregate snapshot bounds, same-owner lease replay recovery and additional login-recovery mutation guards while preserving authoritative target lookup, view/edit permission separation, durable queued offline patches, revision/checksum fencing, Velocity switch locks, nested item serialization and lifecycle ownership. Existing permission-policy tests plus `InventoryWorkflowWiringTest` directly cover the original command/GUI/permission wiring requirement. The encode-local bounded byte stream no longer uses unnecessary method-level synchronization.

The first recovery-guard compile failure remains non-passing evidence. Intermediate/review/freeze heads `321a50f3ca120dba7d7e21542450d4d527dabbd3`, `c3545612d370ea237a63394e6a0401edbe650790`, `6c7ec06622b8ee20d00aa3839e5741f44a0f1976`, and `2c0ad0543f9f39f242e8a75c532a77ca2afb52de` are all superseded. `6c7ec066...` was replaced for the valid CodeRabbit partial-mutation defect; `2c0ad054...` was replaced for two valid Codacy synchronization warnings. No result bound to any superseded revision may be used as final acceptance.

Final acceptance requires the immutable head to independently pass hosted Java 21 clean/full build and tests with MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, Wiki validation, configured static analysis and coverage, review-thread closure with zero valid unresolved findings, Sentinel exact-artifact restart with terminal `PAPER_RESTART_OK`, and canonical automatic public→private Pi staging with correlated private provenance, Paper restart/MariaDB/Flyway evidence and cleanup. Skipped, queued, cancelled, merge-ref-only, wrong-head or superseded checks are not passing evidence.

Sentinel attempts on superseded `2c0ad054...` failed infrastructure/resource preconditions, including 637 MB available versus the required 700 MB. Do not repeat an identical resource-gate attempt on the new head until there is evidence the host resource condition changed. Once changed, wait for the exact immutable-head Sentinel artifact and use only `@enthusia-sentinel test restart`; success requires terminal `PAPER_RESTART_OK`. Canonical Pi remains a separate automatic gate and cannot be substituted by Sentinel success.

ES-P06 remains READY and must not be started by this worker. ES-X01 remains PARKED_BLOCKED because the supported RoseChat integration repository/source contract is unresolved. ES-P08 remains dependency-blocked until ES-P07 completes. V18 remains the immutable migration boundary. Issue #43 remains open/deferred and LiteBans remains authoritative. No production data, deployment, shadow window, cutover, authority change or source rewrite is authorized.
