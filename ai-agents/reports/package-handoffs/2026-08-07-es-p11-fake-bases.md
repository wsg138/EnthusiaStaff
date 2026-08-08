# ES-P11 package handoff — fake-base generation and cleanup

Date: 2026-08-07
Package: `ES-P11`
Status: `ACTIVE`
Classification after selection: `ACTIONABLE_CONTINUATION`

## Selection and starting state

- Legitimate starting `main`: `68a6d936066383f5b8139304f40b2d01d0dfe036`.
- Selected after canonical startup/reconciliation found no actionable continuation ahead of it.
- ES-P02 PR #70 and ES-P05 PR #81 remain unchanged `PARKED_BLOCKED` by the recorded private GitHub Actions Billing & plans/zero-runner condition; they were not retried or synchronized.
- Issue #43 remains open/deferred and is outside ES-P11.
- ES-P10 is `COMPLETE`; V18 is the current immutable aggregate migration boundary.
- No pre-existing ES-P11 branch, PR, package handoff, or competing active worker was found.

## Active work

- Branch: `package/es-p11-fake-bases`.
- Package activation commit: `e7bb4c407448d0579c105c9034ba95f765c0943d`.
- Registry routing commit: `b4b08ab55f9df79bf768703b9c37e787e4fb88c3`.
- PR: pending first coherent implementation checkpoint.

## Authoritative design boundary

`ENTHUSIASTAFF-GOALS.md` requires the fake base to use virtual blocks/schematic only, visible only to the suspect and authorized staff, with no real-world mutation. It clears on distance/world/server/disconnect/five-minute timeout, warns at four minutes, and provides Extend/Clear/Teleport controls.

The package therefore treats the real world as read-only. Generated fake-base state must be bounded and operation-owned in memory; cleanup sends the viewer the authoritative real block state again. No cleanup path may delete or replace real blocks. Process/server interruption cannot leave persistent fake blocks because the content is client-side only; ordinary disconnect/reconnect also discards the virtual view. Plugin reload/normal shutdown must explicitly clear active views before losing operation state.

## Completed checkpoint

- Read canonical package system and selected exactly ES-P11.
- Reconciled live PRs/branches/main/migration boundary/issue #43.
- Created required temporary branch from exact legitimate main.
- Persisted package and registry `ACTIVE` state before product implementation.
- Confirmed no migration is currently necessary for a virtual-only implementation.

## Remaining work

1. Reconcile current Paper/Folia scheduling and tester lifecycle APIs.
2. Implement bounded templates, conflict/safety checks, target/staff-scoped virtual rendering, lifecycle/timeout warning, Extend/Clear/Teleport controls, and idempotent cleanup.
3. Wire command/text fallback and staff authorization into the existing Cheat Tester surface without weakening ES-P10 evidence-only behavior.
4. Add direct unit/integration-style tests for bounds, protected real data, viewer isolation, lifecycle termination, duplicate cleanup, partial render failure, authorization, and scheduler behavior.
5. Update Wiki, requirement/audit evidence, package/registry/workspace state, and handoffs.
6. Harshly self-review the exact diff; resolve valid findings.
7. Run exact-head hosted/static/review gates. Do not relabel unavailable private representative staging as a pass; ES-V02 retains that acceptance.
8. Merge normally only if all package merge gates that are actually required for ES-P11 are satisfied; verify containment, delete the temporary branch, publish terminal state, and stop.

## Systems not to disturb

- Do not modify or retry parked ES-P02/ES-P05 private staging without fresh unblock evidence.
- Do not change V1–V18 migrations unless implementation demonstrates a real durable-state need.
- Do not implement general rollback/CoreProtect behavior, real block placement, automatic punishment, production deployment, or unrelated tester features.
- Do not begin ES-X01 or any later package in this worker.
