# `ES-P04` — Staff-mode operational tools

## 1. Package identity
`ES-P04`; Internal; primary `COMP-STAFF`; priority 40; not parallel-safe around staff-mode dispatch/state.

## 2. Status
`ACTIVE` / `ACTIONABLE_CONTINUATION` — implementation, documentation, review repairs, and test-quality cleanup are complete on PR #79. Only final exact-head validation, Pi disposition, and terminal publication remain.

## 3. Objective
Make staff-mode hotbar tools functional and complete safe operational entry, use, restoration, and recovery behavior.

## 4. Included behavior
Authorized operational tools for random teleport, inspect, freeze, reports, spectate/follow, vanish, staff chat, and menu; snapshots/restoration, reconnect/reload/shutdown/rank changes, transfer/world/damage isolation, stale tools, cooldowns, and missing dependencies.

## 5. Explicit exclusions
Cheat tester/fake entities (`ES-P10`); fake bases (`ES-P11`); production deployment; unrelated vanish/freeze redesign.

## 6. Dependencies
`ES-P03` is `COMPLETE` through merge `b960e91ea59627a870ff24f89c2f761d0cbb68ab`.

## 7. Branch and PR
Branch `package/es-p04-staff-mode-tools`, created from exact legitimate `main` `5c820c29c2fe5a498ea7f80454579953ac05b436`; PR #79 to `main`. Preserve both if the package parks blocked. Use normal merge only if every required gate succeeds.

## 8. Completed implementation and review work
- [x] Live-state reconciliation and canonical selection/resume.
- [x] Authenticated per-session staff-tool dispatch and fixed documented slots.
- [x] Stale/transferred/spoofed/old-session/wrong-slot/wrong-material/rank-invalid tools fail closed.
- [x] Random teleport suitability/filtering, Folia-safe sampling, cooldowns, disabled server/world controls.
- [x] Operational actions reuse existing command/service authorization, audit, and provider behavior.
- [x] Durable staff-state restoration/reconnect/reload/shutdown/rank-change behavior remains intact.
- [x] Bedrock text/typed-command fallback documented.
- [x] Deferred Cheat Tester cannot be issued for any current rank.
- [x] Cooldown configuration boundaries: 0 and 60000 ms accepted; -1 and 60001 rejected for every configured path.
- [x] Follow/spectate feedback no longer falsely asserts creative mode.
- [x] All review threads through exact records head `7c32b823919fc276f5b8c40a80a5de2893956564` resolved. CodeRabbit withdrew scheduler-helper consolidation as optional maintainability follow-up after the concrete compile defect was fixed.
- [x] Five Codacy test-structure findings on `7c32...` corrected by `7642b7fd56e01cfc678ac615d5fd32a18bd32bc1` using explicit assertions and eliminating loop allocations.

## 9. Acceptance criteria
Each non-excluded tool performs its documented action only for authorized active staff; stale/transferred/spoofed tools fail safely; exact player state restores across exit/reconnect/reload/shutdown/rank loss; no duplication/loss or world bypass.

## 10. Required validation
Focused tests plus full Java 21 repository suites, MariaDB/Testcontainers migrations, coverage, runtime-JAR/provider-leak checks, Wiki, Codacy/static analysis, CodeRabbit/review, and configured Pi boot/restart staging on one exact frozen head. Missing/skipped/zero-runner/billing-blocked evidence is not a pass.

## 11. Migration impact
No migration change. Immutable migration boundary remains V17.

## 12. Bedrock and distributed-runtime considerations
Every new staff-tool surface has a command/text fallback. Session ownership/backend switching/reconnect/recovery remain fenced by existing durable runtime. Movement targets are sampled on Folia entity schedulers and actor authority is rechecked before movement.

## 13. Completion definition
If every required gate including Pi succeeds on one exact head, normal-merge PR #79, verify containment/cleanup, publish `COMPLETE`, and stop. If ordinary gates succeed but required Pi remains unavailable without an owner-approved ES-P04 exception, preserve PR #79 and branch, publish `BLOCKED` / `PARKED_BLOCKED` to `main` through a documentation-only status-publication PR, and stop.

## 14. Current checkpoint
Latest product/test head before this final records checkpoint: `7642b7fd56e01cfc678ac615d5fd32a18bd32bc1`. The final frozen implementation-branch head is the last commit produced by this records reconciliation and must be read live before validation.

Supporting but superseded green evidence: head `4cc0178a8f908c43aafc6a88cf3014e81f5756f2` passed Wiki `31173610733` / `92850645081`, Coverage `31173610764` / `92850703180` on a real GitHub-hosted Java 21 runner, Codacy static `92850885117`, variation `92852019542`, and diff coverage `92852019707`.

Exact staging evidence on superseded later records head `7c32b823919fc276f5b8c40a80a5de2893956564`: public Pi wrapper `31177696995`; private staging `31177702607`; required Ubuntu build `92863344441` had runner ID `0`, empty runner name, steps `[]`, and GitHub's explicit Billing & plans payment/spending-limit rejection; Pi `92863355649` skipped. No product staging ran and this is not a pass. No owner-approved ES-P04 infrastructure exception exists.

## 15. Exact next action
Freeze the resulting PR #79 head; verify zero valid unresolved review threads; obtain successful exact-head Wiki, Java 21/full-suite/MariaDB-migration/coverage/runtime-JAR/provider-leak, Codacy, and CodeRabbit evidence; record exact Pi disposition. Then normal-merge and publish `COMPLETE`, or preserve PR #79 and publish `BLOCKED` / `PARKED_BLOCKED` if Pi remains externally unavailable and unapproved. Do not start ES-P09.
