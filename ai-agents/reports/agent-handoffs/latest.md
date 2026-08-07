# Latest AI handoff

Current persistent package handoff:

[`2026-08-07-es-p04-staff-mode-tools.md`](../package-handoffs/2026-08-07-es-p04-staff-mode-tools.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P04 — Staff-mode operational tools` is `COMPLETE`.

Frozen reviewed/validated implementation head: `15d9428eba454e9ae4a905752129bd18676acdb1`.
Normal implementation merge: PR #79 as `a530b992232a8a08cbbd13b0eed6606228ceb652`.

Completed scope includes random teleport, inspector, freeze, reports, follow/spectate, vanish, staff chat, tools menu, authenticated/stale-safe tool dispatch, Folia-safe target sampling, cooldowns, durable staff-state recovery behavior, and Bedrock command/text fallbacks. Cheat Tester/fake entities remain ES-P10; fake bases remain ES-P11.

All ordinary exact-head gates passed on `15d9428e...`: Wiki `31178353549` / `92865432750`; Java 21 Coverage/build/tests/runtime-JAR `31178353504` / `92865439305`; Codacy static `92865800728` with zero issues; Codacy coverage variation `92867049954`; Codacy diff coverage `92867049338`; zero valid unresolved review threads.

Pi staging is **OWNER-APPROVED SKIPPED/DEFERRED**, not passed. Public wrapper `31178352312` dispatched private run `31178359804`; required Ubuntu build `92865456267` received runner ID `0`, empty runner name, steps `[]`, and GitHub's Billing & plans rejection; Pi `92865494913` skipped. On 2026-08-07 the owner explicitly authorized ES-P04 to continue and requested an internal note to perform this staging later when available.

Internal follow-up: when the private Actions billing/runner path is available, rerun ES-P04 Pi boot/restart staging against the merged behavior and record the result. Reopen ES-P04 only if that deferred test reveals a real defect.

Migration boundary remains immutable V17; ES-P04 added no migration. Issue #43 remains open/deferred; LiteBans remains authoritative. No production deployment, cutover, private-data migration, or ES-V02 execution occurred.

Dependency-derived state after ES-P04 completion: ES-P05 and ES-P10 are now READY; ES-P09 remains READY. No package is active. A later sequential worker must reconcile live GitHub before selection; absent a higher-priority actionable continuation, ES-P05 is the lowest-priority-number READY package.
