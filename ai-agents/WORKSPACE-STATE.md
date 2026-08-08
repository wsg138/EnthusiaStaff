# EnthusiaStaff workspace state

Last updated: 2026-08-07

Live GitHub state overrides stale records, but persistent package state must be reconciled here.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P03`, `ES-P04`, `ES-P09`, `ES-P10`, `ES-X05` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-P05 — Report evidence and staff workflow completion` |
| Selected package | `ES-P11 — Fake-base generation and cleanup` |
| ES-P11 status | `REVIEW`; implementation PR #88 on `package/es-p11-fake-bases`; implementation/self-review/docs complete; actual ready-for-review review and exact-head final gates pending |
| ES-P11 starting legitimate main | `68a6d936066383f5b8139304f40b2d01d0dfe036` |
| ES-P11 migration impact | No migration; immutable V18 remains the aggregate boundary |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70; unchanged private Actions Billing & plans zero-runner blocker |
| ES-P05 status | `BLOCKED` / `PARKED_BLOCKED`; PR #81; hosted implementation validation complete at its recorded head; required private staging remains unavailable under the same Billing & plans condition |
| Production boundary | issue #43 remains open/deferred; LiteBans remains authoritative |
| Private acceptance boundary | Representative Java/Bedrock/distributed fake-base acceptance remains `ES-V02`; ES-P11 does not claim the unchanged unavailable private runner as a pass |

## ES-P11 current implementation

- The fake base is client-side virtual block presentation only. No ES-P11 path places, breaks, replaces, pastes, or rolls back a real world block.
- The approved release template is fixed and bounded at 7x7 with a hard block-count cap.
- Placement must fit inside the target's already-loaded chunk and inside world height; the feature never loads or generates a chunk.
- Every virtual template cell must currently be real air and the 5x5 interior floor must be solid/non-hazardous.
- The target must still be in the planned anchor chunk immediately before the final real-block reads, preventing stale cross-region Folia access while durable audit I/O is in flight.
- Operations are exactly fenced to one per target, at most 8 globally and 2 per controlling staff member.
- The target is the only ordinary viewer. Authorized staff become viewers only after a successful Teleport control.
- Fixed lifecycle: five-minute expiry, warning at about four minutes, 48-block distance cutoff, plus clear on world/backend change, disconnect, controller staff-mode exit/disconnect, render or lifecycle-scheduler failure, and plugin lifecycle close.
- Cleanup is idempotent and re-sends current authoritative real block data to connected viewers. Process/server interruption cannot leave a saved-world artifact because the world was never mutated.
- `/cheattester base create|extend|clear|teleport|status` is the text/Bedrock-safe control surface. Active staff mode and `enthusiastaff.cheattester.fake-base` are required; `...manage-any` is required for another controller's operation.
- Async creation/Extend/Teleport paths re-check current staff/control authority before committing. Extend and Teleport record accepted requests before asynchronous work and separate committed evidence only after the action succeeds.
- Durable fake-base audit evidence uses the existing `audit_events` ledger and intentionally excludes coordinates. V18 remains unchanged.

## ES-P11 automated and review evidence

- Direct tests cover template bounds/duplicates/doorway, loaded-chunk placement, conflict/unsafe-floor/world-height refusal, lifecycle warning/extension/expiry/idempotency, audit-domain bounds/privacy, and plugin command/permission metadata.
- MariaDB/Testcontainers integration coverage writes through the production fake-base audit binding, verifies correlation/actor/target/action/outcome/JSON fields, rejects coordinate/location leakage, and proves Flyway remains at V18.
- Wiki/operator documentation and `reports/ES-P11-FAKE-BASE-IMPLEMENTATION.md` are present.
- A complete pre-freeze head `a0fc7c63b547cfa84a89aa116c4297d2c0b25f36` passed Wiki `31233405241` and Coverage `31233405218` / job `93041499563`, including Java 21 build/tests/MariaDB/Testcontainers/runtime-JAR inspection. It is diagnostic only because later self-review fixes changed product code.
- Manual review subsequently fixed approximate admission limits, hand-built audit JSON, expiry consistency, worker-thread Bukkit access, render/restore failure handling, stale cross-chunk Folia reads, stale async authority/manage-any privileges, accepted-vs-committed control evidence, lifecycle scheduler failure after render, and unreachable tab completion.
- CodeRabbit's earlier status is not final review evidence because its comment explicitly skipped review while PR #88 was draft.

## Current worker boundary

This sequential worker owns exactly ES-P11. It must not activate another package. The remaining path is: finish review-state checkpoint → mark PR #88 ready → obtain/reconcile actual review and exact-head hosted/static/coverage gates → fresh `main`/migration reconciliation → normal merge → exact containment → implementation branch deletion → documentation-only terminal COMPLETE publication → stop.

If a valid review or CI defect appears, repair only ES-P11 and restart exact-head validation. If an external blocker newly prevents a required package gate, publish the true blocker and park only after all safe actionable ES-P11 work is complete.

## Existing parked-package boundary

ES-P02 and ES-P05 remain under the same account-level private Actions Billing & plans/zero-runner condition. Do not rerun identical unavailable gates or merge `main` into those parked branches merely to keep them current. Resume them only if their exact unblock condition materially changes or a separate real actionable defect appears.

## Safety boundaries

No production credentials, punishment/player records, raw addresses, private databases, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration/cutover, or ES-V02 execution is authorized or performed by ES-P11.
