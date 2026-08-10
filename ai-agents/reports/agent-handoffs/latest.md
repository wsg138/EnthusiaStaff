# Latest AI handoff

Current active package handoff:

[`2026-08-10-es-p07-inventory-runtime-active.md`](../package-handoffs/2026-08-10-es-p07-inventory-runtime-active.md)

Prior terminal package handoff:

[`2026-08-10-es-v01-private-litebans-representative-verification-complete.md`](../package-handoffs/2026-08-10-es-v01-private-litebans-representative-verification-complete.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P07 — Inventory and Ender editing runtime completion` is the only active package on `package/es-p07-inventory-runtime`, PR `#112`. The branch started from `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315` and was later normally synchronized with current target `main` `2d8fcf27b0bac980211149ae8f7f4e7798998ee5` as merge commit `562e8647063c3fa09b4349d167dc41d1a1660553`. No ES-P07 product file changed in that synchronization.

Product implementation is complete: exact dirty-slot inventory/Ender writes, complete dirty-slot-set prevalidation, aggregate snapshot bounds, same-owner lease replay recovery and additional login-recovery mutation guards preserve authoritative target lookup, view/edit permission separation, durable queued offline patches, revision/checksum fencing, Velocity switch locks, nested item serialization and lifecycle ownership.

The synchronized candidate `562e8647063c3fa09b4349d167dc41d1a1660553` passed hosted Java/MariaDB/JAR/Wiki/Codacy-upload checks before CodeRabbit identified two valid final-review issues. Review-correction head `8b055b8851dd185ae5e8969148aaa11e0d1985e4` fixed both and resolved all review threads, but exact-head Codacy static analysis then found one valid maintainability warning because the strengthened permission test repeated the `"default"` field literal four times. This checkpoint replaces those repetitions with one `DEFAULT_FIELD` constant while preserving the explicit non-null field-presence and false-value assertions.

**The commit produced by this final static-analysis correction is the new immutable final validation head.** PR metadata must record its literal SHA. `8b055b8851dd185ae5e8969148aaa11e0d1985e4` and every earlier candidate are superseded. If any exact-head gate identifies another real defect, fix it, repeat harsh review, freeze the new head, and rerun every exact-head gate before merge.

Final acceptance requires that immutable head to independently pass hosted Java 21 clean/full build and tests with MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, Wiki validation, configured static analysis and coverage, CodeRabbit/reviewer completion with zero valid unresolved findings, Sentinel exact-artifact restart with terminal `PAPER_RESTART_OK`, and canonical automatic public→private Pi staging with correlated private provenance, Paper restart/MariaDB/Flyway evidence and cleanup. Skipped, queued, cancelled, merge-ref-only, wrong-head or superseded checks are not passing evidence.

Any Sentinel or canonical Pi work tied to `8b055b...` or an earlier head is stale for final acceptance after this tracked correction even if it later executes. Do not cancel or interfere with unrelated legitimate Sentinel or Staging jobs. Request fresh exact-head runtime gates only through the canonical paths.

ES-P06 remains READY and must not be started by this worker. ES-X01 remains PARKED_BLOCKED because the supported RoseChat integration repository/source contract is unresolved. ES-P08 remains dependency-blocked until ES-P07 completes. V18 remains the immutable migration boundary. Issue #43 remains open/deferred and LiteBans remains authoritative. No production data, deployment, shadow window, cutover, authority change or source rewrite is authorized.
