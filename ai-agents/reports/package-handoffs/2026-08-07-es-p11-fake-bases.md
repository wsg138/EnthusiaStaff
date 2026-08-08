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
- Draft PR: #88, `ES-P11: fake-base generation and cleanup`.
- Package activation commit: `e7bb4c407448d0579c105c9034ba95f765c0943d`.
- Registry routing commit: `b4b08ab55f9df79bf768703b9c37e787e4fb88c3`.
- Major implementation checkpoint reached through `9a0b698d71c1a620f328634b3f0c3b33cb0fc00f`; package/registry checkpoint commits follow it. Live branch head remains authoritative.

## Authoritative design boundary

`ENTHUSIASTAFF-GOALS.md` requires the fake base to use virtual blocks/schematic only, visible only to the suspect and authorized staff, with no real-world mutation. It clears on distance/world/server/disconnect/five-minute timeout, warns at four minutes, and provides Extend/Clear/Teleport controls.

The implementation therefore treats the real world as read-only. It uses Paper's supported `sendMultiBlockChange` virtual block API only, not real block setters, NMS, or ProtocolLib packet internals. The fixed 7x7 template must fit wholly inside the target's already-loaded chunk; every virtual template cell must currently be real air and the 5x5 interior floor must be solid/non-hazardous. The planner never loads/generates chunks. Cleanup reads authoritative real block data on the owning region and re-sends it to each still-connected viewer. Disconnect/server transfer/process interruption cannot leave persistent world artifacts because no world state was ever changed.

## Implemented checkpoint

- Fixed bounded 7x7 deepslate/blackstone/tinted-glass template with a two-block doorway; template block count is hard-capped.
- One-loaded-chunk placement planner with world-height, real-air conflict, and safe-floor checks.
- Exact active-operation limits: target uniqueness, eight global operations, two per controlling staff member.
- Client-only target render plus optional authorized staff viewer render after the Teleport control.
- Five-minute expiry, four-minute warning, 48-block distance limit, world/server/disconnect/staff-mode-exit/plugin-disable cleanup.
- Extend, Clear, Teleport, Status, clickable controls, and `/cheattester base ...` command/text fallback.
- Permission boundary: active staff mode plus `enthusiastaff.cheattester.fake-base`; only the owning controller or `...manage-any` can manage another operation.
- Cleanup is idempotent and re-sends current authoritative real block data rather than a stale snapshot.
- Creation requires available durable audit storage and writes an accepted audit before virtual rendering. Lifecycle/control evidence is coordinate-free and uses the existing `audit_events` table, so no Flyway migration was added and V18 remains immutable.
- Exact global/per-staff registration was hardened under one registry lock. Shutdown/lifecycle auditing can still queue after the manager stops accepting new actions.
- Direct tests committed for template bounds/duplicates/doorway, loaded-chunk placement, conflict/unsafe-floor/world-height rejection, warning/extension/expiry/idempotent close, and bounded coordinate-free audit model.

## Current validation status

- Early Coverage runs were superseded/cancelled by newer commits and are **not** passing evidence.
- A clean exact-head build/test/static/review freeze has not happened yet.
- Runtime permission/scheduler/shutdown coverage still needs completion before documentation/freeze.
- Private representative Java/Bedrock/distributed staging remains assigned to ES-V02. The unchanged private Actions Billing & plans/zero-runner condition is not being retried without a material change.

## Remaining work

1. Let the current branch compile and resolve every Java/API/test failure.
2. Add direct runtime wiring/permission/scheduler/shutdown tests where practical without false mocks of Bukkit internals.
3. Harshly self-review the implementation for races, stale viewer leakage, shutdown/reload behavior, unbounded work, privacy, and audit semantics; fix all valid findings.
4. Update Wiki/operator recovery documentation, `PROJECT-COMPLETION-AUDIT.md`, requirements evidence, `WORKSPACE-STATE.md`, and latest handoff.
5. Reconcile fresh `main`, PR review threads, and exact-head statuses.
6. Freeze one exact head and require hosted build/tests/runtime-JAR inspection, Wiki/static analysis/coverage/review gates according to repository policy. Do not count cancelled/superseded/wrong-head runs.
7. Mark PR #88 ready only after implementation/docs are complete; merge normally, verify exact containment, delete `package/es-p11-fake-bases`, publish terminal package/registry/handoff state, and stop.

## Systems not to disturb

- Do not modify or retry parked ES-P02/ES-P05 private staging without fresh unblock evidence.
- Do not change V1–V18 migrations unless implementation demonstrates a real durable-state need.
- Do not implement general rollback/CoreProtect behavior, real block placement, automatic punishment, production deployment, or unrelated tester features.
- Do not begin ES-X01 or any later package in this worker.
