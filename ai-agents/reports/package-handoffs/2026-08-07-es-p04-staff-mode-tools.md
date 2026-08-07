# ES-P04 handoff — Staff-mode operational tools

Date: 2026-08-07
Status: `ACTIVE`
Classification: `ACTIONABLE_CONTINUATION`
Priority: `40`
Worker: generic sequential package worker
PR: #79

## Selection and scope

- Starting aggregate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Branch: `package/es-p04-staff-mode-tools`.
- `ES-P03` is complete; `ES-P02` remains parked on the unchanged private Actions Billing & plans zero-runner blocker.
- `ES-P09` remains dependency-ready at priority 55 and was not started.
- ES-P04 covers random teleport, inspector, freeze, reports, follow/spectate, vanish, staff chat, tools menu, and shared staff-mode safety/recovery behavior. Cheat Tester/fake entities remain ES-P10; fake bases remain ES-P11.
- V17 remains immutable; ES-P04 adds no migration. Issue #43 remains deferred; LiteBans remains authoritative.

## Completed work

Authenticated per-session tool issuance/dispatch, stale/spoofed/transferred/session/rank/slot/material validation, random-target suitability and Folia-safe sampling, cooldowns and restart-scoped settings, durable command/service reuse, Bedrock command/text fallback, and existing snapshot/restoration/reconnect/reload/shutdown/rank-change safety are complete.

Review fixes prevent the deferred Cheat Tester from being issued for any rank, test all four cooldown paths at inclusive 0/60000 ms boundaries with rejection at -1/60001 ms, and correct follow/spectate feedback. Every CodeRabbit thread through records head `7c32b823919fc276f5b8c40a80a5de2893956564` was resolved; CodeRabbit explicitly withdrew scheduler-helper consolidation as optional maintainability follow-up after the compile defect was fixed.

Codacy then found five test-structure warnings on `7c32...` in the boundary test. Commit `7642b7fd56e01cfc678ac615d5fd32a18bd32bc1` replaces the loop/no-assert structure with explicit assertions and no loop allocations.

## Validation history

Supporting but superseded green head `4cc0178a8f908c43aafc6a88cf3014e81f5756f2` passed Wiki `31173610733` / `92850645081`, Coverage `31173610764` / `92850703180` on a real GitHub-hosted Java 21 runner, Codacy static `92850885117`, coverage variation `92852019542`, and diff coverage `92852019707`.

Later exact records head `7c32b823919fc276f5b8c40a80a5de2893956564` passed Wiki but its configured Pi route proved the expected external blocker. Public wrapper `31177696995` dispatched private staging `31177702607`; required Ubuntu build `92863344441` received runner ID `0`, empty runner name, steps `[]`, and GitHub's explicit Billing & plans payment/spending-limit rejection; Pi `92863355649` skipped. No product staging ran. This is not a pass and no ES-P04-specific owner infrastructure exception exists.

Because `7642...` corrected static-analysis findings after `7c32...`, all final gates must run on the last commit produced by this records checkpoint. Prior evidence is history only.

## Terminal rule

Read the final PR #79 head live after this state reconciliation and freeze it. Verify zero valid unresolved review threads and successful exact-head Wiki, Java 21/full-suite/MariaDB-migration/coverage/runtime-JAR/provider-leak, Codacy, and CodeRabbit gates. Record the exact Pi result. If all required gates including Pi pass, merge PR #79 normally, verify containment/cleanup, publish `COMPLETE`, and stop. If ordinary gates pass but Pi again fails before product execution under the same unapproved Billing & plans restriction, preserve PR #79 and its branch and publish `BLOCKED` / `PARKED_BLOCKED` to `main` through a documentation-only status-publication PR. Do not start ES-P09.
