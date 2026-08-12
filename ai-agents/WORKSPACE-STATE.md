# Workspace state

Last updated: 2026-08-11

Live GitHub overrides stale records. Detailed package evidence remains in the registry and canonical handoffs.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active package | `ES-P08 — Item confiscation and restoration` is `ACTIVE` / `ACTIONABLE_CONTINUATION` on `package/es-p08-item-confiscation`; exact package start `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`; implementation PR #128 is ready for review. |
| Current implementation checkpoint | ES-P08 implementation, package tests, docs, and harsh manual review are complete and `VALIDATION_READY`. Founder-only case-linked recovery requeues one coherent quarantined item operation for the existing fenced checksum/revision recovery path; it never applies inventory directly. Multiple candidates, target/profile/fence divergence, live competing leases, non-item operations, or unavailable storage fail closed. |
| Review/static checkpoint | Manual review fixed hidden case-target divergence and optional recovery-audit insertion. Four Codacy findings on a superseded head were fixed. CodeRabbit's attempted substantive review was quota-limited; its generic status is not counted as a review pass and no review threads exist. |
| Frozen-head rule | This canonical workspace-state publication is the last planned repository-content change before exact-head validation. Capture its resulting literal branch SHA in PR metadata and treat it as frozen. Any valid final blocker requires an explicit fix, a new freeze, and fresh exact-head evidence. |
| Parked provider package | `ES-X01 — RoseChat provider and communication integration` remains `BLOCKED` / `PARKED_BLOCKED` because the supported RoseChat standalone repository/default branch/source/AGENTS contract remains unresolved. |
| Downstream blockers | `ES-X02`, `ES-X03`, `ES-X04`, `ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked while ES-P08 is active or on their other documented dependencies/external conditions. No downstream package is activated by this worker. |
| Migration boundary | V18 remains current and immutable. ES-P08 adds no migration; the existing quarantine schema already contains explicit resolution fields. |
| Production boundary | Issue #43 remains open/deferred and LiteBans remains authoritative. No production data, destructive production staging, shadow window, deployment, authority change, cutover, source rewrite, or private-data acceptance is authorized by ES-P08. |
| Exact next action | Freeze the literal SHA produced by this state publication, complete hosted build/test/runtime-JAR/Wiki/Codacy/manual-review/Sentinel live-restart/public→private Pi evidence on that exact SHA, merge PR #128 normally, prove containment/cleanup, publish ES-P08 terminal state, and stop without activating ES-X02. |

## ES-P08 validation-ready result

Live reconciliation found no incomplete-package branch/PR continuation and no supported RoseChat repository resolution, so ES-P08 was the only dependency-safe ready package and was claimed from exact `main` `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`. `main` remained on that exact SHA through implementation review, so no upstream merge or rebase was required.

The existing source already provided durable inventory profiles, paired operations/patches, leases/fencing, nested item selection, confiscated-asset snapshots, restoration reservation/finalization, and restart/login recovery. ES-P08 completed the missing owner-recovery boundary without rewriting the established mutation coordinator.

The new bounded recovery transaction accepts only case-linked `CONFISCATION` and `RESTORE_CONFISCATED` operations. It independently rechecks case-target/profile binding, paired state/profile/fence coherence, quarantine identity/resource key, and live leases; more than one unresolved candidate is ambiguous. Success atomically returns the exact pair to `PENDING`, records resolver/time/resolution metadata, and requires exactly one append-only recovery audit write. Normal recovery must then acquire a newer fence and prove the live checksum/revision before any item image can commit. A failed retry re-quarantines and clears prior resolution fields while preserving earlier owner authorization in audit.

New package tests cover authorization, storage loss, generic-operation exclusion, duplicate replay, competing leases, paired-state rollback, case-target corruption, same-case multi-scope ambiguity, no profile revision change on authorization, and re-quarantine/re-recovery. Existing adjacent suites continue to provide exact restoration binding, duplicate finalization, restore-once, nested-path, aggregate-codec, fencing/lease, and restart-style recovery evidence.

Canonical validation-ready handoff: `ai-agents/reports/package-handoffs/2026-08-11-es-p08-item-confiscation-validation-ready.md`.

## Stop boundary

This worker owns exactly ES-P08. If interrupted, PR #128 is the actionable continuation and must be resumed before selecting any other package. ES-X02 and all downstream work remain untouched until ES-P08 is terminally complete. After ES-P08 terminal publication, this worker stops rather than activating the next package.