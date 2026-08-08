# EnthusiaStaff workspace state

Last updated: 2026-08-07

Live GitHub state overrides stale records, but persistent package state must be reconciled here.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P03`, `ES-P04`, `ES-P09`, `ES-P10`, `ES-X05` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-P05 — Report evidence and staff workflow completion` |
| Selected package | `ES-P11 — Fake-base generation and cleanup` |
| ES-P11 status | `REVIEW`; implementation PR #88 on `package/es-p11-fake-bases`; implementation/self-review/docs complete; actual CodeRabbit review is being reconciled before the final exact-head gates |
| ES-P11 starting legitimate main | `68a6d936066383f5b8139304f40b2d01d0dfe036` |
| ES-P11 migration impact | No migration; immutable V18 remains the aggregate boundary |
| ES-P11 canonical handoff | `ai-agents/reports/package-handoffs/2026-08-07-es-p11-fake-bases.md` |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70; unchanged private Actions Billing & plans zero-runner blocker |
| ES-P05 status | `BLOCKED` / `PARKED_BLOCKED`; PR #81; hosted implementation validation complete at its recorded head; required private staging remains unavailable under the same Billing & plans condition |
| Production boundary | issue #43 remains open/deferred; LiteBans remains authoritative |
| Private acceptance boundary | Representative Java/Bedrock/distributed fake-base acceptance remains `ES-V02`; ES-P11 does not claim the unavailable private runner as a pass or as an ES-P11 completion gate |

## ES-P11 current implementation

- The fake base is client-side virtual block presentation only. No ES-P11 path places, breaks, replaces, pastes, or rolls back a real world block.
- The approved release template is fixed and bounded at 7x7 with a hard block-count cap.
- Placement must fit inside the target's already-loaded chunk and inside world height; the feature never loads or generates a chunk.
- Every virtual template cell must currently be real air and the 5x5 interior floor must be solid/non-hazardous.
- The target must still be in the planned anchor chunk immediately before the final real-block reads, preventing stale cross-region Folia access while durable audit I/O is in flight.
- Operations are exactly fenced to one per target, at most 8 globally and 2 per controlling staff member.
- Only the target and staff viewers whose Teleport action succeeds receive the virtual structure; every other player is excluded.
- Fixed lifecycle: five-minute expiry, warning at about four minutes, 48-block distance cutoff, plus clear on world/backend change, disconnect, controller staff-mode exit/disconnect, render or lifecycle-scheduler failure, and plugin lifecycle close. Extend is accepted only before the current deadline and cannot revive an already-expired operation awaiting its cleanup tick.
- Cleanup is idempotent and re-sends current authoritative real block data to connected viewers. Process/server interruption cannot leave a saved-world artifact because the world was never mutated.
- `/cheattester base create|extend|clear|teleport|status` is the text/Bedrock-safe control surface. Active staff mode and `enthusiastaff.cheattester.fake-base` are required; `...manage-any` is required for another controller's operation.
- Async creation/Extend/Teleport paths re-check current staff/control authority before committing. Extend and Teleport record accepted requests before asynchronous work and separate committed evidence only after the action succeeds.
- Durable fake-base audit evidence uses the existing `audit_events` ledger and intentionally excludes coordinates. V18 remains unchanged.

## ES-P11 automated and review evidence

- Direct tests cover template bounds/duplicates/doorway, loaded-chunk placement, conflict/unsafe-floor/world-height refusal, lifecycle warning/extension/expiry/idempotency including refusal to extend at or after expiry, audit-domain bounds/privacy, and plugin command/permission metadata.
- MariaDB/Testcontainers integration coverage writes through the production fake-base audit binding, verifies correlation/actor/target/action/outcome/JSON fields, and rejects coordinate/location leakage. ES-P11 changes no migration file; repository migration validation remains responsible for V18 clean-install/upgrade/checksum proof.
- Wiki/operator documentation and `reports/ES-P11-FAKE-BASE-IMPLEMENTATION.md` are present.
- Earlier passing heads remain diagnostic only whenever a later product or state fix changes the PR head.
- Manual review and CodeRabbit have fixed approximate admission limits, hand-built audit JSON, warning/extension consistency, worker/region-thread Bukkit access, render/restore failure handling, stale cross-chunk Folia reads, stale async authority/manage-any privileges, accepted-vs-committed control evidence, lifecycle scheduler failure after render, render/close races, permission/test/documentation clarity, and expired-operation extension.

## Exact-head merge gate

Before merging PR #88, freeze one tracked PR head and apply the canonical `VALIDATION-POLICY.md` exact-head checklist to that same SHA: Wiki validation; Java 21 clean build/tests and warnings-as-errors; MariaDB/Testcontainers plus clean-install/upgrade/checksum migration checks; runtime-JAR creation/integrity and provider-leak inspection; configured static analysis and coverage; actual CodeRabbit/reviewer completion with zero valid unresolved findings; and a fresh `main`, PR, branch, issue #43, and migration-ownership reconciliation. Representative Java/Bedrock/distributed Pi/private staging is explicitly assigned to `ES-V02` by the ES-P11 package contract, so it is not an ES-P11 completion gate; any automatically triggered unavailable run must still be recorded as **NOT A PASS** with its runner/step evidence and must not be mislabeled as an exception or successful staging.

After those gates pass on the unchanged head, merge PR #88 only with the normal merge-commit method. Verify the actual result before cleanup: record the exact merge commit and resulting `main` SHA; confirm the merge commit has two parents including the frozen feature head and the pre-merge `main`; confirm the frozen feature head is contained in resulting `main`; re-read live PR/main evidence and confirm the merge result is the expected one. Only after those checks may `package/es-p11-fake-bases` be deleted. Record the exact merge commit, resulting `main` SHA, containment result, live post-merge evidence, and implementation-branch cleanup result before terminal COMPLETE publication.

## Current worker boundary

This sequential worker owns exactly ES-P11. It must not activate another package. If a valid review or CI defect appears, repair only ES-P11 and restart the exact-head validation cycle. Once the implementation merge is proven and cleaned up, publish the documentation-only terminal COMPLETE state, recompute routing without activating another package, verify finalization cleanup, and stop.

## Existing parked-package boundary

ES-P02 and ES-P05 remain under the same account-level private Actions Billing & plans/zero-runner condition. Do not rerun identical unavailable gates or merge `main` into those parked branches merely to keep them current. Resume them only if their exact unblock condition materially changes or a separate real actionable defect appears.

## Safety boundaries

No production credentials, punishment/player records, raw addresses, private databases, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration/cutover, or ES-V02 execution is authorized or performed by ES-P11.
