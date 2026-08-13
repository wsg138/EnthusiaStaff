# Workspace state

Last updated: 2026-08-12

Live GitHub overrides stale records. Detailed package evidence remains in the registry, selected package record, canonical handoff, and PR verification ledger.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages before PR #128 merge | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Selected package | `ES-P08 — Item confiscation and restoration` is the sole `ACTIONABLE_CONTINUATION` / merge lineage in PR #128 on `package/es-p08-item-confiscation`. Its frozen executable-validation head is `27b20bb56e540161f695e624916f91620261457d`. |
| Canonical handoff | `ai-agents/reports/package-handoffs/2026-08-11-es-p08-item-confiscation-complete.md` |
| Package-contract correction | The canonical ES-P08 contract at package start did not require an independent live Sentinel restart and explicitly deferred representative destructive/load acceptance to `ES-V03`. A later worker-added `PAPER_RESTART_OK` tracking requirement was an accidental new merge gate and is not authoritative under `VALIDATION-POLICY.md`. |
| Required executable evidence | Frozen product head `27b20bb56e540161f695e624916f91620261457d` passed Wiki; Java 21 full build/tests with MariaDB/Testcontainers and warnings-as-errors; runtime-JAR/provider-leak inspection; aggregate JaCoCo/Codacy coverage; Codacy static with zero issues; review with zero valid unresolved threads; Sentinel artifact build; and canonical public→private Pi staging. |
| Canonical Pi proof | Public run `31555950970` attempt 1 and correlated private run `31556350997` / job `93989465759` passed exact provenance, V1–V18 first-cycle migration, V18 restart no-op, two Paper/storage-ready `SHADOW_MIGRATION` cycles, clean shutdown/failure scans, sanitized evidence, guarded disposable-database cleanup, and public transfer cleanup. |
| Sentinel diagnostic history | Live restart jobs `150`, `151`, and `153` remain non-passing history: cycle-1 temperature resource-gate failure, timeout, and cycle-2 temperature resource-gate failure respectively. None is called a pass. They are diagnostic because live Sentinel restart was not part of the authoritative ES-P08 start contract. |
| State-only synchronization rule | Any PR #128 head after frozen product head `27b20bb...` may reuse the executable evidence only when exact comparison proves every later change is process/state/documentation-only under `VALIDATION-POLICY.md`; any executable/test/config/workflow/migration/dependency change requires new exact-head executable validation. |
| Migration / production boundary | V18 remains current and immutable; ES-P08 adds no migration. Issue #43 remains open/deferred and LiteBans remains authoritative. No production data, deployment, shadow window, cutover, authority change, private-data acceptance, or source rewrite is authorized. |
| Parked provider package | `ES-X01 — RoseChat provider and communication integration` remains `BLOCKED` / `PARKED_BLOCKED` because the supported RoseChat standalone repository/default branch/source/AGENTS contract is unresolved. This does not block unrelated dependency-complete work. |
| Next dependency state after PR #128 merge | Once ES-P08 is canonical `COMPLETE`, `ES-X02 — EnthusiaCurrency destructive provider` becomes dependency-complete and `READY`, absent a newly discovered higher-precedence actionable continuation. This worker must not activate it. |
| Exact next action | Finish only ES-P08: prove the post-frozen-head delta is state/documentation-only, validate that delta and reviews, normally merge PR #128, verify containment/cleanup, then stop. |

## ES-P08 stop boundary

PR #128 contains the completed ES-P08 product implementation. The earlier documentation-only blocker publication remains valid historical evidence of what the worker believed at that time, but the later reconciliation established that the live Sentinel restart requirement was self-added after package selection and was not part of the authoritative package acceptance contract.

No Sentinel failure has been erased or relabeled. Canonical Pi remains the successful exact-head runtime/restart/MariaDB/Flyway evidence for ES-P08. Broader destructive acceptance remains assigned to `ES-V03`.
