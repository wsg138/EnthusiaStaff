# Latest AI handoff

Current active package handoff:

[`2026-08-10-es-p07-inventory-runtime-active.md`](../package-handoffs/2026-08-10-es-p07-inventory-runtime-active.md)

Prior terminal package handoff:

[`2026-08-10-es-v01-private-litebans-representative-verification-complete.md`](../package-handoffs/2026-08-10-es-v01-private-litebans-representative-verification-complete.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P07 — Inventory and Ender editing runtime completion` remains the only active package on `package/es-p07-inventory-runtime`, ready PR `#112`, created exactly from reconciled `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`. Implementation, documentation, direct command/GUI wiring coverage and harsh source review are complete. **The commit produced by this latest-handoff update is the immutable final validation head.** No further tracked changes are allowed unless an exact-head gate exposes a real ES-P07 defect.

The package completes exact dirty-slot inventory/Ender writes, aggregate snapshot bounds, same-owner lease replay recovery and additional login-recovery mutation guards while preserving authoritative target lookup, view/edit permission separation, durable queued offline patches, revision/checksum fencing, Velocity switch locks, nested item serialization and lifecycle ownership. Existing permission-policy tests plus the new `InventoryWorkflowWiringTest` directly cover the original command/GUI/permission wiring requirement.

Intermediate sanity head `321a50f3ca120dba7d7e21542450d4d527dabbd3` and first freeze `c3545612d370ea237a63394e6a0401edbe650790` are both non-final evidence. The latter was intentionally superseded when the original package contract was re-read and the missing direct wiring test was added. Final acceptance must independently bind every required hosted/static/review/Sentinel/canonical Pi result to the new immutable head.

For Sentinel, wait for the exact new-head `enthusiastaff-sentinel-paper` artifact and then use only the live supported PR command `@enthusia-sentinel test restart`; success requires terminal `PAPER_RESTART_OK`. Canonical Pi remains a separate automatic public→private gate and requires correlated private Paper/MariaDB/Flyway restart and cleanup evidence.

ES-P06 remains READY and must not be started by this worker. ES-X01 remains PARKED_BLOCKED because the supported RoseChat repository/default/source/AGENTS are unresolved. ES-P08 remains dependency-blocked until ES-P07 completes. V18 remains the immutable migration boundary. Issue #43 remains open/deferred and LiteBans remains authoritative. No production data, deployment, shadow window, cutover, authority change or source rewrite is authorized.
