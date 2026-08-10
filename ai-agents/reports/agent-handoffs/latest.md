# Latest AI handoff

Current active package handoff:

[`2026-08-10-es-p07-inventory-runtime-active.md`](../package-handoffs/2026-08-10-es-p07-inventory-runtime-active.md)

Prior terminal package handoff:

[`2026-08-10-es-v01-private-litebans-representative-verification-complete.md`](../package-handoffs/2026-08-10-es-v01-private-litebans-representative-verification-complete.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P07 — Inventory and Ender editing runtime completion` is the only active package on `package/es-p07-inventory-runtime`, PR `#112`. The branch started from `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315` and was later normally synchronized with current target `main` `2d8fcf27b0bac980211149ae8f7f4e7798998ee5` as merge commit `562e8647063c3fa09b4349d167dc41d1a1660553` after the Pi unblock condition materially changed. No ES-P07 product file changed in that synchronization.

Product implementation is complete: exact dirty-slot inventory/Ender writes, complete dirty-slot-set prevalidation, aggregate snapshot bounds, same-owner lease replay recovery and additional login-recovery mutation guards preserve authoritative target lookup, view/edit permission separation, durable queued offline patches, revision/checksum fencing, Velocity switch locks, nested item serialization and lifecycle ownership.

The synchronized candidate `562e8647063c3fa09b4349d167dc41d1a1660553` passed Java 21 clean/full tests with MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, aggregate JaCoCo/Codacy upload, Wiki and exact Sentinel artifact publication. CodeRabbit then identified two valid final-review issues: the wiring test could pass with missing permission `default` fields, and tracked state still treated an older freeze as final. This checkpoint fixes both by requiring explicit non-null `default` fields before value assertions and by marking `562e864...` plus every earlier head as superseded.

**The commit produced by this review-correction checkpoint is the new immutable final validation head.** PR metadata must record its literal SHA. If any exact-head gate identifies a real defect, fix it, repeat harsh review, freeze the new head, and rerun every exact-head gate before merge.

Final acceptance requires that immutable head to independently pass hosted Java 21 clean/full build and tests with MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, Wiki validation, configured static analysis and coverage, CodeRabbit/reviewer completion with zero valid unresolved findings, Sentinel exact-artifact restart with terminal `PAPER_RESTART_OK`, and canonical automatic public→private Pi staging with correlated private provenance, Paper restart/MariaDB/Flyway evidence and cleanup. Skipped, queued, cancelled, merge-ref-only, wrong-head or superseded checks are not passing evidence.

Sentinel restart job 83 and any canonical Pi work tied to `562e864...` are stale for final acceptance after this tracked correction even if they later execute. Do not cancel or interfere with unrelated legitimate Sentinel or Staging jobs. Request fresh exact-head runtime gates only through the canonical paths.

ES-P06 remains READY and must not be started by this worker. ES-X01 remains PARKED_BLOCKED because the supported RoseChat integration repository/source contract is unresolved. ES-P08 remains dependency-blocked until ES-P07 completes. V18 remains the immutable migration boundary. Issue #43 remains open/deferred and LiteBans remains authoritative. No production data, deployment, shadow window, cutover, authority change or source rewrite is authorized.
