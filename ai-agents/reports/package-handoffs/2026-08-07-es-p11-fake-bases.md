# ES-P11 package handoff — fake-base generation and cleanup

Date: 2026-08-08
Package: `ES-P11`
Status: `REVIEW`
Classification: `ACTIONABLE_CONTINUATION`

## Selection and starting state

- Legitimate starting `main`: `68a6d936066383f5b8139304f40b2d01d0dfe036`.
- This sequential worker resumed PR #88 / `package/es-p11-fake-bases` as the only live `ACTIONABLE_CONTINUATION`; no new package was claimed.
- ES-P02 PR #70 and ES-P05 PR #81 remain unchanged `PARKED_BLOCKED` by the private GitHub Actions Billing & plans/zero-runner condition.
- Issue #43 remains open/deferred and LiteBans remains authoritative.
- ES-P10 is `COMPLETE`; V18 is the immutable aggregate migration boundary. ES-P11 adds no migration.

## Implemented behavior

- Fixed bounded 7x7 virtual fake base rendered client-side only; no real world block mutation, schematic paste, rollback, chunk load, or chunk generation.
- Placement is restricted to one already-loaded target chunk, build height, real-air template cells, and a solid/non-hazardous 5x5 floor. Final same-chunk validation preserves Folia ownership.
- Exact concurrency: one operation per target, 8 globally, 2 per controller.
- Only the target and staff viewers whose Teleport action succeeds receive the virtual structure.
- Lifetime is five minutes with a one-minute warning; Extend starts a fresh five-minute window only before the existing deadline. Expired-but-not-yet-cleaned operations cannot be revived.
- Cleanup covers clear, timeout, distance, world/backend change, disconnect, staff-mode exit, render/scheduler failure, and plugin lifecycle. It is idempotent and sends current authoritative real block states.
- Async creation/Extend/Teleport re-check current authority at commit time; manage-any loss cannot commit stale privilege.
- Coordinate-free lifecycle evidence uses existing `audit_events`; creation is accepted before render and Extend/Teleport distinguish accepted from committed actions.
- `/cheattester base create|extend|clear|teleport|status` is the text/Bedrock-safe operator surface with explicit default-false base/manage-any permissions.

## Review and static findings resolved

Harsh manual review and actual CodeRabbit reviews resolved exact admission, audit JSON, warning/extension synchronization, Bukkit thread-affinity, render/restore failures, stale Folia cross-chunk reads, stale staff/manage-any authority, accepted-vs-committed evidence, lifecycle scheduler retirement, render/close races, viewer wording, handoff routing, permission inheritance, template-height coupling, metadata/integration assertions, unreachable tab completion, and expired-operation extension.

Codacy then reported maintainability/static findings on the expanded fake-base code. Those were reduced 23 → 9 → 5 → 1 → 0 through targeted refactoring and narrow repository-consistent suppression only where structurally justified. Audit persistence, authoritative world-block reads, and presentation helpers were extracted; router/placement/template hotspots were split; lifecycle/ownership decisions stayed in `FakeBaseManager`. A manual diff review after the refactor found no new world mutation, worker-thread Bukkit access, authorization regression, or cleanup defect.

## Validated product head

Product head: `dafa710e44cc3c4ff7af1ee367d679f95ea8fd3f`.

Passing evidence:

- Wiki `31239760132` / `93058657612`.
- Full build/coverage `31239760133` / `93058683147`: Java 21 clean build/tests, MariaDB/Testcontainers, migration checks, runtime-JAR inspection, artifact and JaCoCo/Codacy upload.
- Aggregate JaCoCo: 47.02% lines, 38.15% branches, 49.64% instructions.
- Paper JAR SHA-256 `22ceb5f71b387a7a9bc369aa7a3a7313d696d9477c41e6cb5bb5bc58310a9f1c`; Velocity JAR SHA-256 `8529a2b8c12c8392ccba61735c5b190bc66b09a4f982fc12d3280c29a7ee3a99`; provider API leaks 0.
- Artifact `9016711895`, digest `sha256:fac889493926350db66ca4f5e89c5f356d30d682613b99e483b4d863abf30f82`.
- Codacy static `93059044531`: success, zero annotations.
- Codacy Diff Coverage `93059225105`: 40.81%, success.
- Codacy Coverage Variation `93059224975`: -0.45%, success.

Because package-state files are part of PR #88, this handoff/state checkpoint follows the green product head and becomes the final frozen candidate. The product-head checks above are diagnostic for the final implementation; required merge evidence must rerun on the exact state-inclusive frozen SHA.

## Private acceptance boundary

Representative Java/Bedrock/distributed private/Pi acceptance remains assigned to `ES-V02`, not ES-P11. Product-head public wrapper `31239759170` / check `93058655372` dispatched private run `31239763060`; Ubuntu job `93058666371` had runner ID `0`, empty runner name, `steps: []`, and GitHub's Billing & plans payment/spending-limit rejection; Pi `93058670370` was skipped. This is **NOT A PASS** and no ES-P11 infrastructure exception is claimed.

## Exact next actions

1. Freeze this state-inclusive checkpoint.
2. Require successful exact-head Wiki, full Java 21 build/tests/MariaDB/Testcontainers/migration/runtime-JAR/artifact/coverage, Codacy static/coverage, and an actual CodeRabbit/reviewer completion with zero valid unresolved findings.
3. Reconcile legitimate `main`, PR/branch state, issue #43, and migration ownership on the unchanged frozen head.
4. Merge PR #88 normally; verify the exact two-parent merge commit, resulting `main`, frozen-head containment, and live PR/main result.
5. Delete `package/es-p11-fake-bases` only after those checks.
6. Publish ES-P11 terminal `COMPLETE` state through the established documentation-only finalization PR, recompute routing without activating another package, verify terminal branch cleanup, and stop.

## Systems not to disturb

- Do not resume ES-P02/ES-P05 absent fresh unblock evidence.
- Do not modify V1–V18 migrations.
- Do not implement real schematic/world paste, general rollback/CoreProtect replacement, automatic punishment, production deployment, or unrelated tester features.
- Do not execute or claim ES-V02 acceptance here.
- Do not activate any later package in this worker.
