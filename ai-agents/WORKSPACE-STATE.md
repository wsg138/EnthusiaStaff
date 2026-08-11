# Workspace state

Last updated: 2026-08-10

Live GitHub overrides stale records. Detailed package evidence remains in the registry and canonical handoffs.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Selected package result | `ES-P06 — Discord notification delivery completion` is `COMPLETE`. Frozen head `7e21edb1d32a75727dc65df826f9de964adcfff3`; PR #115 merged normally as `d78a5165493f810dbb3fd4d11e5e9d4b80ffed71`; containment proven; implementation branch deleted. |
| Hosted/review proof | Exact-head Wiki, Java 21 full build/tests with MariaDB/Testcontainers, warnings-as-errors, runtime-JAR/provider-leak inspection, aggregate coverage, and Codacy static/coverage passed. Codacy static found zero issues. Three substantive CodeRabbit findings were fixed and resolved; the final incremental CodeRabbit rerun was rate-limited and is not counted as a pass. Exact-head manual review found no additional valid defect; valid unresolved review-thread count is zero. |
| Runtime proof | Canonical public Pi run `31450682744` attempt 1 and correlated private run `31451077909` / job `93655393387` passed exact provenance on trusted `Lincoln-PI-4`, two Paper/storage-ready `SHADOW_MIGRATION` cycles, V1–V18 then v18 no-op restart, clean shutdown/failure scans, sanitized evidence upload, guarded database cleanup, and public transfer cleanup. |
| Non-passing history | Coverage run `31450684287` attempt 1 / job `93654716868` failed in an untouched punishment-request concurrency test on a transient MariaDB race. It remains non-passing. The unchanged frozen SHA passed the full attempt-2 rerun. Superseded/cancelled earlier candidate checks remain non-final. |
| Ready packages | `ES-P08 — Item confiscation and restoration` remains dependency-complete and `READY` at priority 70. It is not activated by this worker. |
| Parked provider package | `ES-X01 — RoseChat provider and communication integration` remains `BLOCKED` / `PARKED_BLOCKED` because the supported RoseChat standalone repository/default branch/source/AGENTS contract remains unresolved. |
| Downstream blockers | `ES-X02`, `ES-X03`, `ES-X04`, `ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked on their documented dependencies/external conditions. ES-V02 is no longer blocked by ES-P06 but still depends on incomplete ES-X01, ES-X03, and ES-X04. |
| Migration boundary | V18 remains current and immutable. ES-P06 added no migration. |
| Production boundary | No production Discord route was contacted; `discord.enabled=false` remains the safe default. Issue #43 remains open/deferred and LiteBans remains authoritative. No production data, shadow window, deployment, authority change, cutover, or source rewrite occurred. |
| Exact next action | Stop this ES-P06 worker after terminal-state publication. A new sequential worker must reconcile live GitHub; absent a new actionable continuation, the current next ready package is ES-P08 at priority 70. |

## ES-P06 terminal result

The package completed explicit environment-backed Discord route authorization, HTTPS/host/path/redirect controls, bounded destination-specific rendering, privacy redaction, mention suppression, Unicode-safe normalization/truncation, Java 21 transport shutdown ownership, bounded failure/circuit/dead-letter behavior, restart/concurrency proof, operator recovery documentation, and isolated fake delivery testing while preserving the existing durable MariaDB producer/store design.

The final frozen implementation head passed the required executable hosted and canonical staging gates. PR #115 then merged with a normal two-parent merge commit. The merge and frozen feature head share tree `8f7b7dae841779af573012df3e30fb6302580654`, and GitHub auto-deleted `package/es-p06-discord-delivery`.

Webhook delivery is intentionally documented as **at least once**, not exactly once, because the external HTTP-success-before-database-ack crash window cannot be eliminated by the local lease. Broader distributed Java/Bedrock acceptance remains assigned to later validation work.

## Stop boundary

This worker completed exactly ES-P06 and publishes only terminal routing state here. It must not activate, prepare, stage, or partially implement ES-P08, ES-X01, or any other package.