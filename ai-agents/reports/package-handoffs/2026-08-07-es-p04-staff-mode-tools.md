# ES-P04 handoff — Staff-mode operational tools

Date: 2026-08-07
Status: `ACTIVE`
Classification: `ACTIONABLE_CONTINUATION` after claim
Priority: `40`
Worker: generic sequential package worker

## Selection and starting state

- Starting aggregate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Required implementation branch: `package/es-p04-staff-mode-tools`.
- Starting branch head before package commits: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Dependency `ES-P03` is `COMPLETE` through normal merge `b960e91ea59627a870ff24f89c2f761d0cbb68ab` and completion publication.
- `ES-P02` was rechecked first because its priority is lower. It remains `BLOCKED` / `PARKED_BLOCKED` on the same private-repository ordinary GitHub-hosted runner restriction. Latest inspected `plugin-live-test.yml` run `31141797380` had build job `92753075216` on `ubuntu-latest`, `runner_id: 0`, empty runner name, steps `[]`, and GitHub's Billing & plans payment/spending-limit annotation; Pi job `92753100652` skipped. The unblock condition has not changed, so no rerun was made.
- `ES-P09` is also dependency-ready at priority 55 but was not started.
- Issue #43 remains open and deferred. LiteBans remains authoritative.
- Highest Flyway migration remains immutable V17.

## Package scope

Implement authorized staff-mode hotbar dispatch for the non-excluded operational tools: random teleport, player inspector, freeze, reports, spectate/follow, vanish, staff chat, and tools menu. Preserve and prove exact session snapshot/restoration, reconnect/reload/shutdown/rank-change recovery, inventory/world/damage isolation, stale/spoofed tool rejection, cooldowns, missing-dependency handling, and Bedrock command/text fallbacks.

Explicitly excluded: cheat tester/fake entities (`ES-P10`), fake bases (`ES-P11`), production deployment, and unrelated freeze/vanish redesign.

## Baseline findings

The existing `StaffModeManager` already performs durable state capture before activation, exact restoration verification, reconnect recovery, rank reconciliation, inventory/damage/world protections, and creates PDC-tagged hotbar items. `StaffToolTransferListener` blocks transfer attempts. No interaction dispatcher currently consumes the `staff_tool` tag, matching audit defect `AUD-STAFF-004`.

Security-sensitive baseline: the current `staff_tool` check validates only presence of the PDC key. ES-P04 acceptance requires stale/transferred/spoofed tools to fail safely, so package review must harden tool identity/session ownership rather than treating a forgeable tag as authority.

## Current evidence

- Main/head reconciliation: complete.
- Selected package branch created from exact legitimate `main`.
- Package file marked active on branch.
- Product implementation: not yet started.
- Hosted validation/static analysis/review: not yet run for an ES-P04 product head.
- Migration changes: none.
- Production/staging actions: none.

## Exact next action

Map existing commands/managers, permissions, provider availability, listener registration, configuration, and staff-mode tests. Then implement the smallest coherent dispatcher/security slice on this branch, checkpoint it, and open the required draft PR.

## Systems not to disturb

Do not start ES-P09 or any second package. Do not resume ES-P02 until its exact private ordinary-runner billing condition materially changes. Do not implement ES-P10/ES-P11. Do not deploy, access private player/production data, change punishment authority, disable LiteBans, start issue #43 acceptance, run ES-V02, rewrite Flyway history, or modify production infrastructure.
