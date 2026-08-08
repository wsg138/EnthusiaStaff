# ES-P11 package handoff — fake-base generation and cleanup

Date: 2026-08-07
Package: `ES-P11`
Status: `REVIEW`
Classification: `ACTIONABLE_CONTINUATION`

## Selection and starting state

- Legitimate starting `main`: `68a6d936066383f5b8139304f40b2d01d0dfe036`.
- This sequential worker resumed PR #88 / `package/es-p11-fake-bases` as the only live `ACTIONABLE_CONTINUATION` rather than claiming a new package.
- ES-P02 PR #70 and ES-P05 PR #81 remain unchanged `PARKED_BLOCKED` by the private GitHub Actions Billing & plans/zero-runner condition; they were not resumed.
- Issue #43 remains open/deferred and LiteBans remains authoritative.
- ES-P10 is `COMPLETE`; V18 is the immutable aggregate migration boundary. ES-P11 adds no migration.

## Current live work

- Branch: `package/es-p11-fake-bases`.
- PR: #88, `ES-P11: fake-base generation and cleanup`; non-draft.
- Product/review hardening is complete through `3564d6942e669f9c79c7c952a32285f98f46fcf3`.
- This handoff/state publication follows that fix and is intended to become the next frozen exact head unless validation finds another valid defect.

## Implemented behavior

- Fixed bounded 7x7 virtual fake-base template; client-only Paper block changes, no real world writes.
- Placement is restricted to one already-loaded target chunk and world height; no chunk loading/generation.
- Every virtual cell must be real air; the 5x5 interior floor must be solid/non-hazardous; final same-chunk revalidation protects Folia region ownership.
- Exact ownership/concurrency: one operation per target, 8 global, 2 per controller.
- Only the target and staff viewers whose Teleport action succeeds receive the virtual structure.
- Lifetime is five minutes with a one-minute warning; Extend starts a fresh five-minute window only while the prior deadline is still in the future. Expired-but-not-yet-cleaned operations cannot be revived.
- Cleanup covers clear, timeout, distance, world/backend change, disconnect, staff-mode exit, render/scheduler failure, and plugin lifecycle. It is idempotent and sends current authoritative real block data rather than mutating world state.
- Async creation/Extend/Teleport re-check current authority at commit time; manage-any loss cannot commit stale privilege.
- Coordinate-free lifecycle evidence uses existing `audit_events`; creation is accepted before render and Extend/Teleport distinguish accepted from committed actions.
- `/cheattester base create|extend|clear|teleport|status` is the text/Bedrock-safe operator surface with explicit default-false base/manage-any permissions.

## Review findings resolved

Harsh manual review and actual CodeRabbit reviews resolved exact admission, audit JSON, warning/extension synchronization, worker/region-thread Bukkit access, render/restore failures, stale Folia cross-chunk reads, stale staff/manage-any authority, accepted-vs-committed evidence, lifecycle scheduler retirement, render/close races, viewer wording, canonical handoff routing, permission inheritance, template-height coupling, semantic permission tests, audit integration assertions, unreachable tab completion, and expired-operation extension.

The latest functional finding was CodeRabbit's observation that an operation could be extended at/after its deadline before its cleanup tick. `3564d6942e669f9c79c7c952a32285f98f46fcf3` rejects that case and adds direct coverage. The review thread was explicitly resolved after the fix. `WORKSPACE-STATE.md` was also expanded to require a normal two-parent merge result, resulting-main verification, containment/live-evidence checks, and branch deletion only after those checks.

Two low-value top-level suggestions remain intentionally outside correctness scope: replacing bounded staff-facing UUID status with online names, and adding a typed runtime accessor for a capability already safely exposed by the composite store. Neither changes package safety, authorization, persistence correctness, or acceptance criteria.

## Validation boundary

Final merge evidence must apply to one unchanged post-state-checkpoint SHA and include the canonical `VALIDATION-POLICY.md` exact-head set: Wiki; Java 21 clean build/tests/warnings; MariaDB/Testcontainers and migration checks; runtime-JAR/artifact integrity; configured Codacy/static/coverage checks; actual CodeRabbit/reviewer completion; zero valid unresolved findings; and fresh `main`, PR/branch, issue #43, and migration reconciliation.

Representative Java/Bedrock/distributed private/Pi acceptance remains assigned to `ES-V02`, not ES-P11. Automatic private runs that receive runner ID `0`, no steps, and the account Billing & plans rejection are **NOT A PASS** and are recorded only as unavailable external evidence; no ES-P11 infrastructure exception is claimed.

## Merge/cleanup boundary

After exact-head gates pass, merge PR #88 with the normal merge method only. Record and verify the exact two-parent merge commit, resulting `main` SHA, feature-head containment, and live PR/main evidence. Delete `package/es-p11-fake-bases` only after those checks. Then publish terminal `COMPLETE` package state through the established documentation-only terminal-state PR pattern, recompute dependency routing without activating another package, verify finalization branch cleanup, and stop.

## Systems not to disturb

- Do not resume ES-P02/ES-P05 absent fresh unblock evidence.
- Do not modify V1–V18 migrations.
- Do not implement real schematic/world paste, general rollback/CoreProtect replacement, automatic punishment, production deployment, or unrelated tester features.
- Do not execute or claim ES-V02 acceptance here.
- Do not activate any later package in this worker.
