# Latest AI handoff

Current package handoff:

[`2026-08-09-es-r01-staging-repair-reblocked.md`](../package-handoffs/2026-08-09-es-r01-staging-repair-reblocked.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-R01 — Billing-independent staging bridge recovery` remains `BLOCKED` / `PARKED_BLOCKED`. The staging-only Pi MariaDB service/schema boundary is repaired without production access, but the exact current source cannot produce a fresh trusted bridge artifact because two existing `ReportStoreIntegrationTest` assertions fail in public build job `93206301028` of run `31298080632`.

The required fresh current-`main` Pi Staging proof was public run `31252997554` at `689ff337dd8a33f0bd417d952a7cad5581cb9d9e`. Public build job `93092131811` succeeded; bridge job `93092964130` dispatched private run `31253345564`; private job `93092978141` allocated trusted `Lincoln-PI-4` runner ID `2` and passed exact artifact verification, then the guarded disposable Paper boot/restart step failed. Sanitized evidence artifact `9020680419` (`sha256:5647d2458ab4b1d594e86030d9ffe1a89ac50609093417d3fcb617ecf5b1b677`) uploaded successfully and public transfer cleanup succeeded. This is **not a staging pass**. Earlier captured proof established repeated SQLState `08000` connection failures; the final run's step metadata does not expose a fresh SQLState, so none is invented.

The exact ES-R01 unblock condition is a repaired current-main product build followed by a fresh canonical artifact bridge and guarded two-cycle Paper/MariaDB/Flyway run. Do not manually run Paper outside the workflow, reuse expired transfer assets, change targets/credentials, bypass the reset, or start another package. `ES-P02` PR #70 and `ES-P05` PR #81 remain `BLOCKED` / `PARKED_BLOCKED`; no package is currently `READY`. V18 remains immutable/current, issue #43 remains open/deferred, and LiteBans remains authoritative.
