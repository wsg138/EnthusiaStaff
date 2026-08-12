# Workspace state

Last updated: 2026-08-11

Live GitHub overrides stale records. Detailed package evidence remains in the registry and canonical handoffs.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active package | `ES-P08 — Item confiscation and restoration` is `ACTIVE` / `ACTIONABLE_CONTINUATION` on `package/es-p08-item-confiscation`; exact package start `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`; draft implementation PR #128. |
| Current implementation checkpoint | Existing durable confiscation/restoration foundations were retained. ES-P08 adds a dedicated Founder-authorized case-linked quarantine retry path that only requeues one coherent item operation for normal checksum/revision recovery; it does not directly mutate inventory. MariaDB lifecycle and authorization tests have been added; exact final validation is not yet claimed. |
| Diagnostic evidence only | An early pre-documentation PR head `3c27bcc7e1d0ec67295bb3a5c1225defb4803e02` passed the Java 21 Paper artifact build in Sentinel run `31553184745`. Coverage for that superseded head was cancelled after newer commits and is not passing/final evidence. |
| Parked provider package | `ES-X01 — RoseChat provider and communication integration` remains `BLOCKED` / `PARKED_BLOCKED` because the supported RoseChat standalone repository/default branch/source/AGENTS contract remains unresolved. |
| Downstream blockers | `ES-X02`, `ES-X03`, `ES-X04`, `ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked while ES-P08 is active or on their other documented dependencies/external conditions. No downstream package is activated by this worker. |
| Migration boundary | V18 remains current and immutable. ES-P08 has not added a migration; the existing quarantine schema already contains explicit resolution fields. |
| Production boundary | Issue #43 remains open/deferred and LiteBans remains authoritative. No production data, destructive production staging, shadow window, deployment, authority change, cutover, source rewrite, or private-data acceptance is authorized by ES-P08. |
| Exact next action | Resume PR #128 on `package/es-p08-item-confiscation`; finish package tests/docs/review, freeze one exact head, complete hosted/static/Sentinel/Pi evidence, merge normally, prove containment/cleanup, publish ES-P08 terminal state, and stop without activating ES-X02. |

## ES-P08 active result

Live reconciliation found no incomplete-package branch/PR continuation and no supported RoseChat repository resolution, so ES-P08 was the only dependency-safe ready package and was claimed from exact `main` `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`.

The current source already had durable inventory profiles, paired operations/patches, leases/fencing, nested item selection, confiscated-asset snapshots, restoration reservation/finalization, and restart/login recovery. The confirmed item-package gap was explicit recovery after a journal operation entered `QUARANTINED`.

The active implementation introduces a bounded recovery transaction for case-linked `CONFISCATION` and `RESTORE_CONFISCATED` operations. It independently rechecks case-target/profile binding, paired state/profile/fence coherence and competing leases, resolves the quarantine with actor/time metadata, appends an idempotent audit, and returns the pair to `PENDING`. The ordinary recovery path must still acquire a newer fence and prove the live checksum/revision; ambiguity quarantines again and reopens the quarantine resolution fields.

## Stop boundary

This worker owns exactly ES-P08. If interrupted, PR #128 is the actionable continuation and must be resumed before selecting any other package. ES-X02 and all downstream work remain untouched until ES-P08 is terminally complete.