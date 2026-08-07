# Latest AI handoff

Current persistent package handoff:

[`2026-08-07-es-p04-staff-mode-tools.md`](../package-handoffs/2026-08-07-es-p04-staff-mode-tools.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P04 — Staff-mode operational tools` is the selected active package on `package/es-p04-staff-mode-tools`, PR #79, starting from legitimate `main` `5c820c29c2fe5a498ea7f80454579953ac05b436`. `ES-P09` remains `READY` at priority 55 but unassigned and must not be started by this worker.

Implementation, documentation, review repairs, and Codacy test-quality cleanup are complete. Latest product/test head before the final records checkpoint is `7642b7fd56e01cfc678ac615d5fd32a18bd32bc1`. Non-excluded tools are random teleport, inspector, freeze, reports, follow/spectate, vanish, staff chat, and tools menu. Cheat Tester/fake entities remain ES-P10; fake bases remain ES-P11.

All CodeRabbit threads through records head `7c32b823919fc276f5b8c40a80a5de2893956564` were resolved; the scheduler-helper consolidation suggestion was explicitly withdrawn as optional maintainability follow-up. The branch prevents deferred Cheat Tester issuance for every rank, covers 0/60000 ms accepted and -1/60001 ms rejected cooldown boundaries, and fixes inaccurate follow/spectate feedback. Five Codacy warnings introduced by the first boundary-test structure were corrected in `7642...` with explicit assertions and no loop allocations.

Supporting older head `4cc0178a8f908c43aafc6a88cf3014e81f5756f2` passed Wiki, Java 21 Coverage/build/test/runtime-JAR, and Codacy static/coverage gates, but is superseded.

Exact later staging evidence on `7c32...` proves the configured private route remains externally unavailable: public Pi wrapper `31177696995`; private staging `31177702607`; required Ubuntu build `92863344441` runner ID `0`, empty runner name, steps `[]`, explicit Billing & plans failure; Pi `92863355649` skipped. No product staging ran, and no owner-approved ES-P04 infrastructure exception exists. This is not a pass.

Current migration boundary is immutable V17; ES-P04 adds no migration. Issue #43 remains open/deferred; LiteBans remains authoritative. No production, private-data, cutover, deployment, or ES-V02 action is authorized.

Exact next action: read and freeze the final PR #79 head produced by this records reconciliation, verify zero valid unresolved review threads, obtain every required exact-head hosted/static/review gate and exact Pi evidence, then either normal-merge and publish `COMPLETE` or preserve PR #79 and publish `BLOCKED` / `PARKED_BLOCKED` through a documentation-only status-publication PR if Pi remains externally unavailable without an approved exception. Stop without starting ES-P09.
