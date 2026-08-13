# Workspace state

Last updated: 2026-08-12

Live GitHub overrides stale records. Detailed package evidence remains in the registry, package records, canonical handoffs, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active package | `ES-X02 — EnthusiaCurrency destructive provider` is `BLOCKED` / `PARKED_BLOCKED`. Standalone Currency PR #11 is open at exact head `5d9dfc7f03d33ee2147141fef4c777ba0e67d939`; the Staff same-ID branch is reserved but has no aggregate product import yet. |
| ES-P08 frozen executable head | `27b20bb56e540161f695e624916f91620261457d`; all package-required executable evidence remains attributed to this head. |
| ES-P08 final synchronized head | `f398fd5bd8bbf4ec62f7f05313dd082948c2561b`; exact comparison from the frozen executable head changed only eight `ai-agents` Markdown process/state/handoff files. The normal merge has zero file delta from this synchronized head. |
| Canonical handoff | `ai-agents/reports/package-handoffs/2026-08-11-es-p08-item-confiscation-complete.md` |
| Required executable evidence | Wiki; Java 21 full build/tests with MariaDB/Testcontainers and warnings-as-errors; runtime-JAR/provider-leak inspection; aggregate JaCoCo/Codacy coverage; Codacy static with zero issues; review with zero valid unresolved threads; Sentinel artifact build; and canonical public→private Pi staging all passed for frozen product head `27b20bb...`. |
| Canonical Pi proof | Public run `31555950970` attempt 1 and correlated private run `31556350997` / job `93989465759` passed exact provenance, V1–V18 first-cycle migration, V18 restart no-op, two Paper/storage-ready `SHADOW_MIGRATION` cycles, clean shutdown/failure scans, sanitized evidence, guarded disposable-database cleanup, and public transfer cleanup. |
| Sentinel diagnostic history | Live restart jobs `150`, `151`, and `153` remain non-passing history: cycle-1 temperature resource-gate failure, timeout, and cycle-2 temperature resource-gate failure respectively. None is called a pass. The authoritative ES-P08 package-start contract did not require that independent live restart; representative destructive/load acceptance remains assigned to `ES-V03`. |
| Migration / production boundary | V18 remains current and immutable; ES-P08 added no migration. Issue #43 remains open/deferred and LiteBans remains authoritative. No production data, deployment, shadow window, cutover, authority change, private-data acceptance, or source rewrite is authorized. |
| Parked provider package | `ES-X01 — RoseChat provider and communication integration` remains `BLOCKED` / `PARKED_BLOCKED` because the supported RoseChat standalone repository/default branch/source/AGENTS contract is unresolved. This does not block unrelated dependency-complete work. |
| ES-X02 validation state | Currency Java 21 `mvn -B -ntp verify` run `31657088614` passed on exact head `5d9dfc7...`. Codacy still reports 29 unresolved new findings (2 critical, 1 high, 26 medium), and individual details are not available through the current GitHub evidence path; no static pass, Pi pass, merge, or parity pass is claimed. |
| Exact next action | Treat ES-X02 as `PARKED_BLOCKED` while the Codacy finding-detail condition is unchanged. Resume it before new work when individual PR #11 findings become accessible: inspect/disposition all findings, fix every valid issue, rerun static/review gates, then continue Pi → standalone merge → exact aggregate import/PR → aggregate gates/merge → parity/cleanup. While unchanged, normal routing may skip this parked package per worker protocol. |

## ES-P08 terminal boundary

ES-P08 is `COMPLETE`. The earlier documentation-only Sentinel-blocker publication remains historical evidence and is not rewritten as passing evidence. The later reconciliation corrected only gate applicability: a worker-added diagnostic restart requirement could not become a new package acceptance dependency after selection.

Post-merge SHA/parent/containment/branch-cleanup facts remain in GitHub/PR #128 verification metadata rather than being used to create self-referential source history. This finalization changes routing state because ES-P08 is now actually complete and ES-X02 is now actually ready; it is not a PR solely to insert merge identifiers.
