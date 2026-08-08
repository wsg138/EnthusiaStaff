# EnthusiaStaff workspace state

Last updated: 2026-08-08

Live GitHub state overrides stale records, but persistent package state must be reconciled here.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P03`, `ES-P04`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-P05 — Report evidence and staff workflow completion` |
| Active/selected package | None. ES-P11 reached terminal `COMPLETE`; this documentation-only publication must not activate another package. |
| Ready packages | None under current dependency state. `ES-P07` remains blocked by ES-P02; `ES-P06` and `ES-X01` remain blocked by ES-P05; downstream packages remain dependency-blocked. |
| ES-P11 status | `COMPLETE`; implementation PR #88 merged normally as `6cd293d9f1abc3ca6ca8b70e953da936f4a22ab0` from frozen head `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`; exact containment verified; implementation branch deleted |
| ES-P11 starting legitimate main | `68a6d936066383f5b8139304f40b2d01d0dfe036` |
| ES-P11 migration impact | No migration; immutable V18 remains the aggregate boundary |
| ES-P11 canonical handoff | `ai-agents/reports/package-handoffs/2026-08-07-es-p11-fake-bases.md` |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70; unchanged private Actions Billing & plans zero-runner blocker |
| ES-P05 status | `BLOCKED` / `PARKED_BLOCKED`; PR #81; hosted implementation validation complete at its recorded head; required private staging remains unavailable under the same Billing & plans condition |
| Next recommended work | No later package is currently dependency-complete while ES-P02 and ES-P05 remain parked. If the private Actions Billing & plans condition materially changes, canonical priority resumes ES-P02 before ES-P05. This ES-P11 worker does not activate either package. |
| Owner priorities | No separate owner-directed implementation priority is recorded. The owner action that can unlock the next parked work is restoring the private GitHub Actions payment/spending-limit path; until then, keep ES-P02 and ES-P05 parked and do not rerun identical zero-runner gates. |
| Production boundary | issue #43 remains open/deferred; LiteBans remains authoritative |
| Private acceptance boundary | Representative Java/Bedrock/distributed fake-base acceptance remains `ES-V02`; unavailable zero-runner staging is **NOT A PASS** and was not an ES-P11 completion gate |

## ES-P11 terminal implementation record

- Selected as the only live `ACTIONABLE_CONTINUATION` on PR #88 / `package/es-p11-fake-bases`; exactly one package was worked.
- Completed `AUD-TESTER-003` with a fixed 7x7 client-only fake base using Paper multi-block changes. No real world block is written, no schematic is pasted, and no chunk is loaded or generated.
- Placement is restricted to one already-loaded target chunk, valid build height, real-air template cells, and a solid/non-hazardous 5x5 interior floor. Final same-anchor-chunk validation preserves Folia region ownership.
- Exact ownership bounds are one operation per target, 8 globally, and 2 per controlling staff member. Only the target plus staff viewers whose Teleport action commits receive the virtual view.
- Five-minute lifetime, one-minute warning, 48-block cutoff, pre-expiry-only Extend, idempotent authoritative cleanup, current-authority rechecks, default-false least-privilege permissions, and coordinate-free durable `audit_events` evidence are complete.
- Cleanup/recovery covers clear, timeout, distance, disconnect, world/backend change, controlling-staff exit/disconnect, render/scheduler failure, plugin lifecycle, and render/close races without server-world mutation.

## Frozen exact-head validation

Frozen implementation head: `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`.

- Wiki run `31241442832`, job/check `93063008371`: success.
- Coverage/build run `31241442786`, job/check `93063008372`: exact SHA checkout; Java 21 clean build/tests; MariaDB/Testcontainers integration and migration validation; runtime-JAR integrity/provider-leak inspection; aggregate JaCoCo generation; artifact publication; Codacy coverage upload; `BUILD SUCCESSFUL`.
- Aggregate coverage: 47.01% lines / 38.14% branches / 49.64% instructions.
- Paper runtime JAR: 9,123,435 bytes, SHA-256 `0c3d66fb328a041c650968f39d83d4015340142b438200417db30005fa3448fb`, provider API leaks 0.
- Velocity runtime JAR: 7,863,915 bytes, SHA-256 `79fda01365bae9cfdc9faf75e2c1bfbc068bd43eec82d84c065cbf5a25bbfad9`, provider API leaks 0.
- Artifact `9017217821`, digest `sha256:9077a5e6054002663cc0588b7cb87b32de7869ec2881996488e8c06b500b3397`.
- Codacy Static Code Analysis `93063097134`: success with zero annotations.
- Codacy Diff Coverage `93063654061`: success at 26.67%; no diff-coverage gate is configured.
- Codacy Coverage Variation `93063654099`: success at -0.45% against the -1.0% target.
- Exact-head manual reviewer completion review `4888204151`: PASS after full product/security/threading/privacy/world-safety reconciliation. CodeRabbit's requested final one-file rerun was quota-limited, so it is not represented as an exact-head CodeRabbit pass; the package's actual-reviewer alternative was used. All live review threads were resolved/outdated and no valid unresolved finding remained.

## Private staging boundary

The automatic exact-head private attempt did not execute product code. Public wrapper run `31241441649` / check `93063005569` dispatched private run `31241446283`; required Ubuntu job `93063018565` had runner ID `0`, empty runner name, `steps: []`, and GitHub's Billing & plans payment/spending-limit rejection; Pi job `93063023369` was skipped. This is **NOT A PASS**, is not an ES-P11 completion gate, and creates no ES-P11 infrastructure exception. Representative Java/Bedrock/distributed acceptance remains assigned to `ES-V02`.

## Merge and containment record

- Fresh pre-merge reconciliation confirmed `main` still at `68a6d936066383f5b8139304f40b2d01d0dfe036`, PR #88 still on frozen head `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`, issue #43 open/deferred, and V18 still the migration ceiling.
- PR #88 merged with the normal merge-commit method as `6cd293d9f1abc3ca6ca8b70e953da936f4a22ab0`.
- The verified merge commit has exactly two parents: pre-merge `main` `68a6d936066383f5b8139304f40b2d01d0dfe036` and frozen feature head `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`.
- Post-merge containment is exact: resulting `main` is one merge commit ahead of the frozen feature head, zero behind, with zero file differences.
- `package/es-p11-fake-bases` was automatically deleted after containment verification and returns 404.

## Current worker boundary

ES-P11 is terminal `COMPLETE`. This documentation-only finalization publishes that state and recomputes routing without claiming or activating another package. After the terminal publication is merged and its temporary branch is verified cleaned up, this sequential worker stops.

## Safety boundaries

No production credentials, punishment/player records, raw addresses, private databases, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration/cutover, or ES-V02 execution was authorized or performed by ES-P11.