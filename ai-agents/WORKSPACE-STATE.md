# EnthusiaStaff workspace state

Last updated: 2026-08-08

Live GitHub state overrides stale records, but persistent package state must be reconciled here.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P03`, `ES-P04`, `ES-P09`, `ES-P10`, `ES-X05` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-P05 — Report evidence and staff workflow completion` |
| Selected package | `ES-P11 — Fake-base generation and cleanup` |
| ES-P11 status | `REVIEW`; implementation PR #88 on `package/es-p11-fake-bases`; product implementation, direct/integration tests, docs, self-review, CodeRabbit finding repair, and Codacy remediation are complete; one state-inclusive exact-head validation/reviewer cycle remains before merge |
| ES-P11 starting legitimate main | `68a6d936066383f5b8139304f40b2d01d0dfe036` |
| ES-P11 validated product head | `dafa710e44cc3c4ff7af1ee367d679f95ea8fd3f` |
| ES-P11 migration impact | No migration; immutable V18 remains the aggregate boundary |
| ES-P11 canonical handoff | `ai-agents/reports/package-handoffs/2026-08-07-es-p11-fake-bases.md` |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70; unchanged private Actions Billing & plans zero-runner blocker |
| ES-P05 status | `BLOCKED` / `PARKED_BLOCKED`; PR #81; hosted implementation validation complete at its recorded head; required private staging remains unavailable under the same Billing & plans condition |
| Production boundary | issue #43 remains open/deferred; LiteBans remains authoritative |
| Private acceptance boundary | Representative Java/Bedrock/distributed fake-base acceptance remains `ES-V02`; unavailable zero-runner staging is **NOT A PASS** and is not an ES-P11 completion gate |

## ES-P11 implementation boundary

- Client-only virtual 7x7 fake-base rendering through Paper multi-block changes; no real block writes, schematic paste, rollback, chunk load, or chunk generation.
- Placement is restricted to one already-loaded target chunk, valid build height, real-air template cells, and a solid/non-hazardous 5x5 interior floor. Final same-anchor-chunk validation protects Folia region ownership.
- Exact ownership bounds: one operation per target, 8 globally, 2 per controlling staff member.
- Only the target plus staff viewers whose Teleport action successfully commits receive the virtual view.
- Five-minute lifetime; warning about one minute before expiry; 48-block cutoff. Extend starts a new five-minute window only before the current deadline and cannot revive an expired-but-not-yet-cleaned operation.
- Cleanup is idempotent and re-sends current authoritative real block states. Disconnect, world/backend change, controlling-staff exit/disconnect, timeout, distance, render/scheduler failure, and plugin lifecycle all converge on cleanup.
- Async create/Extend/Teleport re-check current staff/control authority before commit. `manage-any` is default-false and explicitly inherits the base fake-base permission.
- Coordinate-free durable lifecycle evidence uses existing `audit_events`; no fake-base coordinate is persisted and no migration was added.

## Review and static-analysis state

Manual review plus actual CodeRabbit reviews resolved exact concurrency admission, canonical audit JSON, warning/extension synchronization, Bukkit thread-affinity, render/restore failure handling, stale Folia cross-chunk reads, stale async staff/manage-any authority, accepted-vs-committed evidence, lifecycle scheduler retirement, render/close races, viewer documentation, permission inheritance, template-height coupling, metadata/integration assertions, unreachable tab completion, and expired-operation extension.

Codacy remediation on the final product implementation reduced findings from 23 → 9 → 5 → 1 → 0. Small hotspots were refactored; low-level audit persistence, authoritative world-block reads, and presentation helpers were extracted while lifecycle/ownership decisions remain in `FakeBaseManager`. No new world mutation or worker-thread Bukkit access was introduced.

## Validated product-head evidence

Product head `dafa710e44cc3c4ff7af1ee367d679f95ea8fd3f` passed:

- Wiki run `31239760132`, job/check `93058657612`.
- Coverage/build run `31239760133`, job/check `93058683147`: Java 21 clean build/tests, MariaDB/Testcontainers integration coverage, migration checks, runtime-JAR integrity/provider-leak inspection, JaCoCo generation, artifact publication, and Codacy coverage upload.
- Build result: `BUILD SUCCESSFUL`; aggregate coverage 47.02% lines / 38.15% branches / 49.64% instructions.
- Paper runtime JAR: 9,123,435 bytes, SHA-256 `22ceb5f71b387a7a9bc369aa7a3a7313d696d9477c41e6cb5bb5bc58310a9f1c`, provider leaks 0.
- Velocity runtime JAR: 7,863,915 bytes, SHA-256 `8529a2b8c12c8392ccba61735c5b190bc66b09a4f982fc12d3280c29a7ee3a99`, provider leaks 0.
- Artifact `9016711895`, digest `sha256:fac889493926350db66ca4f5e89c5f356d30d682613b99e483b4d863abf30f82`.
- Codacy static `93059044531`: success, zero annotations.
- Codacy Diff Coverage `93059225105`: success, 40.81%.
- Codacy Coverage Variation `93059224975`: success, -0.45%, within the configured >= -1% gate.

Those results are product-head evidence only. Because package-state files are part of PR #88, the next state-only checkpoint becomes the final frozen candidate and must rerun the exact-head gates on that unchanged SHA before merge.

The automatic private attempt for `dafa710e44cc3c4ff7af1ee367d679f95ea8fd3f` did not execute product code: public wrapper `31239759170` / check `93058655372` dispatched private run `31239763060`; required Ubuntu job `93058666371` had runner ID `0`, empty runner name, `steps: []`, and GitHub's Billing & plans payment/spending-limit rejection; Pi job `93058670370` was skipped. This is **NOT A PASS**, is not an ES-P11 completion gate, and does not create an ES-P11 infrastructure exception.

## Exact-head merge gate

Before merging PR #88, freeze one state-inclusive PR head and require on that same unchanged SHA: Wiki; Java 21 clean build/tests/warnings; MariaDB/Testcontainers and migration validation; runtime-JAR/artifact integrity; configured Codacy static and coverage gates; an actual CodeRabbit/reviewer completion with zero valid unresolved findings; and fresh `main`, PR, branch, issue #43, and migration-ownership reconciliation. A generic reviewer status without an actual exact-head review is insufficient.

After those gates pass, merge PR #88 only with the normal merge-commit method. Record the exact merge commit and resulting `main`; verify the merge commit has two parents including the frozen feature head and pre-merge `main`; confirm frozen-head containment in resulting `main`; re-read live PR/main evidence; then and only then delete `package/es-p11-fake-bases`.

## Current worker boundary

This sequential worker owns exactly ES-P11. If a valid final review/CI defect appears, repair only ES-P11 and restart the exact-head cycle. After the verified implementation merge and branch cleanup, publish terminal `COMPLETE` state through the established documentation-only finalization PR pattern, recompute routing without activating another package, verify terminal-branch cleanup, and stop.

## Safety boundaries

No production credentials, punishment/player records, raw addresses, private databases, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration/cutover, or ES-V02 execution is authorized or performed by ES-P11.
