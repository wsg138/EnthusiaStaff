# Latest AI handoff

Current persistent package handoff:

[`2026-08-07-es-p05-report-workflow.md`](../package-handoffs/2026-08-07-es-p05-report-workflow.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P05 — Report evidence and staff workflow completion` is `BLOCKED` / `PARKED_BLOCKED` on open PR #81.

Starting legitimate `main`: `bf9b305ba96d9536f3d111c79eef674bd2e11dc5`.
Frozen implementation / hosted-validation head: `4a38e191395913c6733726e222f0889a2d56d267`.
Implementation branch: `package/es-p05-report-workflow`.

Provider-independent scope is implemented: dedicated sensitive report-evidence permission, bounded `/reports evidence` text review, privacy-safe coordinate/evidence presentation, strict client metadata allow-listing, newest-snapshot default, explicit no-direct-attachment boundary, direct command/GUI/privacy wiring tests, MariaDB restart durability proof and updated report/evidence Wiki guidance. Existing durable cooldown/merge/replay/stale-revision/concurrency/rollback/purge behavior was preserved and revalidated. RoseChat PM capture remains ES-X01; Discord route delivery remains ES-P06.

Exact hosted checks passed on `4a38e191...`: Wiki `31183192145` / `92881243088`; Java 21 Coverage/build/tests/runtime-JAR `31183192068` / `92881313210`; artifact `8995826742` digest `sha256:ed87314d5eda8286928ce64f11027240898a0823333c6ffa5aa6d98f1697dbe4`; Codacy static `92882185524` with zero issues; variation `92882989470`; diff coverage `92882989439`. Harsh self-review found and fixed broad GUI coordinate exposure, raw nested AutoClicker serialization and oldest-snapshot default selection. Zero inline review threads remain. CodeRabbit was quota-limited and must rerun before merge.

Required private staging is **FAILED/UNAVAILABLE, NOT PASSED**. Latest public wrapper `31183283525` / `92881545286` dispatched private run `31183290816`; required Ubuntu build `92881577147` received runner ID `0`, empty runner name, steps `[]`, and GitHub's Billing & plans payment/spending-limit rejection. Pi `92881591391` skipped. An earlier automatic exact-head dispatch produced the same result; no manual duplicate retry followed confirmation.

No ES-P05-specific infrastructure exception exists. ES-P04's exception is package-specific and must not be reused. PR #81 therefore remains open and unmerged.

Exact unblock: resolve the GitHub Actions payment/spending-limit restriction for private `wsg138/EnthusiaStaff-Staging`; then resume ES-P05, reconcile newer `main`, rerun CodeRabbit and every exact-head hosted/static/staging gate, require successful trusted private build plus Pi safe boot/restart on the same candidate, merge normally, verify containment, finalize records/cleanup and stop. Do not modify product code without a newly confirmed defect.

Migration boundary remains immutable V17; ES-P05 added no migration. Issue #43 remains open/deferred and LiteBans remains authoritative. No production deployment, cutover, private-data migration or authority activation occurred.

No package is active after this terminal publication. ES-P02 and ES-P05 are parked on the unchanged external condition. ES-P09 and ES-P10 remain READY and unassigned. A later sequential worker must reconcile live GitHub before selection; absent a material unblock that makes a parked continuation actionable, ES-P09 is the lowest-priority-number READY package. This worker does not start it.
