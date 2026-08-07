# `ES-P04` — Staff-mode operational tools

## 1. Package identity
`ES-P04`; Internal; primary `COMP-STAFF`; priority 40; not parallel-safe around staff-mode dispatch/state.

## 2. Status
`COMPLETE` — implementation, review, ordinary exact-head validation, normal merge, and completion publication are complete. Pi boot/restart staging is explicitly owner-deferred for later internal verification because the private Actions route could not allocate its required Ubuntu runner.

## 3. Objective
Make staff-mode hotbar tools functional and complete safe operational entry, use, restoration, and recovery behavior.

## 4. Included behavior
Authorized operational tools for random teleport, inspect, freeze, reports, spectate/follow, vanish, staff chat, and menu; snapshots/restoration, reconnect/reload/shutdown/rank changes, transfer/world/damage isolation, stale tools, cooldowns, and missing dependencies.

## 5. Explicit exclusions
Cheat tester/fake entities (`ES-P10`); fake bases (`ES-P11`); production deployment; unrelated vanish/freeze redesign.

## 6. Dependencies
`ES-P03` is `COMPLETE` through merge `b960e91ea59627a870ff24f89c2f761d0cbb68ab`.

## 7. Branch and PR
Implementation branch `package/es-p04-staff-mode-tools`, created from exact legitimate `main` `5c820c29c2fe5a498ea7f80454579953ac05b436`; PR #79 merged normally as `a530b992232a8a08cbbd13b0eed6606228ceb652` from frozen head `15d9428eba454e9ae4a905752129bd18676acdb1`.

## 8. Completed implementation and review work
- [x] Authenticated per-session staff-tool dispatch and fixed documented slots.
- [x] Stale/transferred/spoofed/old-session/wrong-slot/wrong-material/rank-invalid tools fail closed.
- [x] Random teleport suitability/filtering, Folia-safe sampling, cooldowns, disabled server/world controls.
- [x] Operational actions reuse existing command/service authorization, audit, and provider behavior.
- [x] Durable staff-state restoration/reconnect/reload/shutdown/rank-change behavior remains intact.
- [x] Bedrock text/typed-command fallback documented.
- [x] Deferred Cheat Tester cannot be issued for any current rank.
- [x] Cooldown configuration boundaries: 0 and 60000 ms accepted; -1 and 60001 rejected for every configured path.
- [x] Follow/spectate feedback no longer falsely asserts creative mode.
- [x] Compile drift and all valid CodeRabbit/Codacy findings resolved; scheduler-helper consolidation withdrawn as optional maintainability follow-up.
- [x] Zero valid unresolved review threads on final review state.

## 9. Acceptance criteria
Each non-excluded tool performs its documented action only for authorized active staff; stale/transferred/spoofed tools fail safely; exact player state restores across exit/reconnect/reload/shutdown/rank loss; no duplication/loss or world bypass.

## 10. Validation
Frozen head `15d9428eba454e9ae4a905752129bd18676acdb1` passed:

- Wiki run `31178353549`, job `92865432750`.
- Java 21 Coverage run `31178353504`, job `92865439305`, including full build/tests/coverage/runtime-JAR inspection and artifact upload.
- Codacy static `92865800728` with zero issues.
- Codacy coverage variation `92867049954` and diff coverage `92867049338`.
- CodeRabbit/review disposition with zero valid unresolved threads.

Configured private staging did not run product code. Public wrapper `31178352312` dispatched private run `31178359804`; required Ubuntu build `92865456267` received runner ID `0`, empty runner name, steps `[]`, and GitHub's Billing & plans rejection. Pi job `92865494913` skipped.

## 11. Owner-approved infrastructure exception
On 2026-08-07 the owner explicitly directed the worker to continue ES-P04, mark the unavailable Pi staging as skipped/deferred, and record an internal note to perform the testing later when the route is available. This exception is specific to ES-P04. The failed/skipped staging evidence is **not** called a pass.

Internal follow-up: once the private Actions billing/runner path is available, rerun ES-P04 Pi boot/restart staging against the merged behavior and record the result. Do not reopen the package merely because the deferred check exists; reopen only if that later test reveals a real defect.

## 12. Migration impact
No migration change. Immutable migration boundary remains V17.

## 13. Bedrock and distributed-runtime considerations
Every new staff-tool surface has a command/text fallback. Session ownership/backend switching/reconnect/recovery remain fenced by existing durable runtime. Movement targets are sampled on Folia entity schedulers and actor authority is rechecked before movement.

## 14. Completion record
- Starting `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Frozen validated head: `15d9428eba454e9ae4a905752129bd18676acdb1`.
- Normal merge: `a530b992232a8a08cbbd13b0eed6606228ceb652`.
- Status: `COMPLETE` under the recorded package-specific owner infrastructure exception.
- Newly dependency-ready after completion: ES-P05 and ES-P10; ES-P09 was already READY. No second package was activated by this worker.
