# ES-P04 handoff — Staff-mode operational tools

Date: 2026-08-07
Status: `COMPLETE`
Priority: `40`
Worker: generic sequential package worker
PR: #79 — merged normally

## Starting state and scope

- Starting aggregate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Implementation branch: `package/es-p04-staff-mode-tools`.
- `ES-P03` satisfied the package dependency. `ES-P02` remained parked on its unchanged private Actions billing blocker.
- Scope completed: random teleport, inspector, freeze, reports, follow/spectate, vanish, staff chat, tools menu, and shared staff-mode safety/recovery behavior.
- Preserved exclusions: Cheat Tester/fake entities remain ES-P10; fake bases remain ES-P11.
- V17 remains immutable; ES-P04 added no migration. Issue #43 remains deferred; LiteBans remains authoritative.

## Implementation and review

Authenticated per-session tool issuance/dispatch, stale/spoofed/transferred/session/rank/slot/material validation, random-target suitability and Folia-safe sampling, cooldowns and restart-scoped settings, durable command/service reuse, Bedrock command/text fallback, and existing snapshot/restoration/reconnect/reload/shutdown/rank-change safety are complete.

Review repairs prevent the deferred Cheat Tester from being issued for any rank, cover all cooldown paths at inclusive 0/60000 ms boundaries with rejection at -1/60001 ms, correct follow/spectate feedback, restore the extracted scheduler overload, and remove Codacy test-structure warnings. Every valid review thread was resolved. CodeRabbit withdrew scheduler-helper consolidation as optional maintainability follow-up.

## Final validation

Frozen implementation head: `15d9428eba454e9ae4a905752129bd18676acdb1`.

- Wiki run `31178353549`, job `92865432750`: success.
- Coverage run `31178353504`, job `92865439305`: success on GitHub-hosted Java 21; full build/tests/coverage/runtime-JAR inspection and validation artifact upload succeeded.
- Codacy static `92865800728`: success, zero issues.
- Codacy coverage variation `92867049954`: success.
- Codacy diff coverage `92867049338`: success.
- Review state: zero valid unresolved threads.

## Pi staging disposition

Public wrapper `31178352312` dispatched private staging run `31178359804`. Required Ubuntu build `92865456267` received runner ID `0`, empty runner name, steps `[]`, and GitHub's explicit Billing & plans payment/spending-limit rejection. Pi job `92865494913` skipped. No product staging executed; this is not a pass.

On 2026-08-07 the owner explicitly instructed the worker to continue ES-P04, mark Pi staging skipped/deferred, and record an internal note to do that testing later when available. This is a package-specific owner-approved infrastructure exception and does not generalize to ES-P02 or other packages.

**Internal deferred test:** once the private Actions billing/runner path is available, rerun ES-P04 Pi boot/restart staging against the merged behavior and record the result. The package remains complete unless that later test exposes a real product defect.

## Merge and routing

PR #79 merged normally as `a530b992232a8a08cbbd13b0eed6606228ceb652` with validated head `15d9428e...` as its package parent. No product code changed after exact-head validation.

ES-P04 is `COMPLETE`. ES-P05 and ES-P10 become dependency-derived READY; ES-P09 remains READY. This worker does not activate or implement another package and stops after completion-state publication.
