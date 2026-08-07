# Latest AI handoff

Current persistent package handoff:

[`2026-08-07-es-p04-staff-mode-tools.md`](../package-handoffs/2026-08-07-es-p04-staff-mode-tools.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P04 — Staff-mode operational tools` is the selected active package. It was claimed by the generic sequential package worker from exact legitimate aggregate `main` `5c820c29c2fe5a498ea7f80454579953ac05b436` on required branch `package/es-p04-staff-mode-tools`; implementation PR #79 is open and ready for review.

Selection followed live continuation classification. `ES-P02 — Runtime database recovery and Velocity reload` remains `BLOCKED` / `PARKED_BLOCKED`: newest inspected private `plugin-live-test.yml` run `31141797380` again failed before the required ordinary `ubuntu-latest` runner allocated. Build job `92753075216` has runner ID `0`, empty runner name, steps `[]`, and GitHub's same Billing & plans payment/spending-limit annotation; Pi job `92753100652` skipped. The exact unblock condition therefore has not changed, and no redundant rerun was made.

`ES-P01`, `ES-P03`, and `ES-X05` remain `COMPLETE`. `ES-P09` remains dependency-derived `READY` at priority 55 but is unassigned and must not be started by this worker. ES-P04 priority 40 was the lowest eligible READY package and is now an `ACTIONABLE_CONTINUATION` until its validation/terminal-state work finishes.

ES-P04 implementation and documentation are complete for the non-excluded operational staff-mode tools and shared safety/recovery behavior: random teleport, player inspector, freeze, reports, spectate/follow, vanish, staff chat, tools menu, stale/transferred/spoofed tool rejection, cooldowns, snapshot/restoration and reconnect/reload/shutdown/rank-change safety, missing dependency behavior, and Bedrock command/text fallback. Cheat Tester/fake entities remain excluded for `ES-P10`; fake bases remain `ES-P11`.

Review after the first green exact-head hosted run found valid follow-up defects. The branch now prevents the deferred Cheat Tester from being issued for any staff rank, tests all four configured cooldown paths at inclusive 0/60000 ms boundaries and rejects -1/60001 ms, and corrects follow/spectate feedback so it no longer falsely claims a creative-mode profile. Completed implementation must not be reopened unless a new valid review or test defect appears.

The prior records head `4cc0178a8f908c43aafc6a88cf3014e81f5756f2` passed Wiki `31173610733` / `92850645081`, Coverage `31173610764` / `92850703180` on a real GitHub-hosted Java 21 runner, Codacy static analysis `92850885117`, Codacy coverage variation `92852019542`, and Codacy diff coverage `92852019707`. Those runs are superseded by the review-fix commits and are supporting history only, not final-head evidence.

The remaining package work is exact-head validation and terminal-state disposition. The configured private Pi route has repeatedly failed before product execution because the private repository cannot allocate its ordinary Ubuntu runner under the Billing & plans restriction; no owner-approved ES-P04 infrastructure exception exists in inspected state. Unavailable infrastructure is not a pass.

Current migration boundary is immutable V17 and ES-P04 adds no migration. Issue #43 remains open/deferred; LiteBans remains authoritative. No production, private-data, cutover, deployment, or ES-V02 action is authorized.

Exact next action: freeze the current records checkpoint, obtain exact-head Wiki, Java 21/full-suite/MariaDB-migration/coverage/runtime-JAR/provider-leak, Codacy, CodeRabbit/review, and configured Pi evidence for PR #79, then either merge normally and publish `COMPLETE` or preserve PR #79 and publish `BLOCKED` / `PARKED_BLOCKED` through the required documentation-only status-publication PR if Pi remains externally unavailable without an approved exception. Do not start ES-P09.
