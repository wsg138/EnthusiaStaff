# Latest AI handoff

Current package handoff:

[`2026-08-07-es-p11-fake-bases.md`](../package-handoffs/2026-08-07-es-p11-fake-bases.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P11 — Fake-base generation and cleanup` is the selected package and is in `REVIEW` on PR #88 / `package/es-p11-fake-bases`.

The package was selected from legitimate `main` `68a6d936066383f5b8139304f40b2d01d0dfe036` after live reconciliation confirmed ES-P02 PR #70 and ES-P05 PR #81 remained unchanged `PARKED_BLOCKED` by the same private GitHub Actions Billing & plans zero-runner condition. Exactly one package is being worked.

ES-P11 implements `AUD-TESTER-003` as a bounded client-only fake base: fixed 7x7 approved template; one already-loaded target chunk; real-air and safe-floor conflict checks; no chunk loads and no real block writes; one-target/eight-global/two-per-controller bounds; five-minute lifetime with warning and Extend/Clear/Teleport controls; target plus authorized-staff viewer isolation; idempotent authoritative real-block re-send cleanup; coordinate-free durable audit through existing `audit_events`; and `/cheattester base` command/text fallback.

Self-review is complete and fixed exact admission races, JSON/audit issues, expiry consistency, worker-thread Bukkit access, render/restore failures, stale Folia cross-chunk reads, stale async staff/manage-any authorization, accepted-vs-committed control evidence, and post-render lifecycle scheduler failure. V18 remains immutable; ES-P11 added no migration.

A complete pre-freeze head `a0fc7c63b547cfa84a89aa116c4297d2c0b25f36` passed Wiki run `31233405241` and Coverage run `31233405218` / job `93041499563`, including Java 21 build/tests/MariaDB/Testcontainers/runtime-JAR inspection, but that evidence is diagnostic only because later correctness fixes changed product code. The next required action is to finish the REVIEW checkpoint, mark PR #88 ready, obtain an actual CodeRabbit/reviewer pass rather than the draft-skip status, reconcile all threads and fresh `main`, freeze one exact head, and require all hosted/static/coverage gates on that head.

Representative Java/Bedrock/distributed acceptance remains assigned to `ES-V02`; the unchanged unavailable private runner is not a pass and is not being retried without a material condition change.

After a clean exact-head review/validation, merge PR #88 normally, verify exact containment, delete the implementation branch, publish terminal `COMPLETE` state through the established documentation-only terminal-state PR pattern, and stop. Do not activate ES-X01 or any other package in this worker.
