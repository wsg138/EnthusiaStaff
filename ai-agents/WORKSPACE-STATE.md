# Workspace state

Last updated: 2026-08-11

Live GitHub overrides stale records. Detailed package evidence remains in the registry and canonical handoffs.

## Terminal state carried by PR #128

This tracked workspace state is intentionally precommitted in the single ES-P08 implementation PR. It becomes canonical on `main` only after the final frozen feature SHA passes every required exact-head gate and PR #128 is normally merged.

| Field | Value |
| --- | --- |
| Completed packages on merge of PR #128 | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Current continuation before merge | PR #128 / `package/es-p08-item-confiscation`; no other package may be selected while it is unmerged. |
| ES-P08 result | Founder-authorized `/case recoveritems <case-id>` requeues only one coherent case-linked quarantined `CONFISCATION`/`RESTORE_CONFISCATED` pair for normal fenced checksum/revision recovery. The command itself never applies inventory. |
| Fail-closed recovery guards | Founder service authorization; resolvable actor identity; exact case-target/profile binding; paired state/profile/fence; stored quarantine resource identity; unresolved quarantine evidence; no competing live lease; multiple candidates remain ambiguous. |
| Review result | Valid manual, Codacy, and CodeRabbit findings were fixed. Final merge requires zero valid unresolved review threads; superseded/rate-limited/wrong-revision states are not passing evidence. |
| Migration boundary | V18 remains current and immutable. ES-P08 adds no migration. |
| Production boundary | Issue #43 remains open/deferred and LiteBans remains authoritative. No production data, destructive production acceptance, deployment, authority change, cutover, source rewrite, or private-data acceptance is authorized. |
| Parked provider package | `ES-X01` remains `BLOCKED` / `PARKED_BLOCKED` because the supported RoseChat standalone repository/default branch/source/AGENTS contract remains unresolved. |
| Next dependency-safe package after this worker stops | `ES-X02` may become dependency-ready after ES-P08 is canonical `COMPLETE`, but this worker must not activate or implement it. |
| Final evidence location | PR #128 metadata records the literal frozen SHA plus exact build/test/runtime-JAR/Wiki/Codacy/review/Sentinel/Pi evidence and, after merge, merge/containment/cleanup facts. |

## ES-P08 completion summary

Package start is exact `main` `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`. `main` did not advance during implementation, so no upstream synchronization was required before validation.

The pre-existing durable inventory journal, confiscation snapshot, restoration reservation, nested-item identity, lease/fencing, checksum/revision, and restart/login recovery foundations were retained. ES-P08 added a separate recovery store/coordinator/command rather than enlarging the live mutation coordinator.

Successful owner authorization performs one bounded database transaction: it verifies the exact case/item/quarantine identity and lease conditions, moves the paired journal rows from `QUARANTINED` to `PENDING`, records resolver/time/resolution metadata, and requires one append-only audit write. Missing/mismatched resource evidence fails closed; no recovery resource key is synthesized. Normal recovery must then acquire a newer fence and prove live state before any inventory replacement commits. A failed retry reopens quarantine resolution fields while prior authorization remains preserved in audit.

New tests cover authorization and unresolved identity, missing storage, generic-operation exclusion, duplicate replay, no profile-revision mutation during authorization, competing lease, pair divergence, case-target corruption, same-case multi-scope ambiguity, and re-quarantine/re-recovery. Existing adjacent suites continue to cover exact restoration binding, restore-once semantics, nested paths, aggregate codec limits, fencing/leases, and restart recovery.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-11-es-p08-item-confiscation-complete.md`.

## Merge and stop rule

PR #128 may merge only after one immutable feature SHA passes Wiki, Java 21 full build/tests with MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, JaCoCo/Codacy coverage, Codacy static analysis, review disposition, exact Sentinel artifact plus `PAPER_RESTART_OK`, and canonical public→private Pi staging/provenance.

After the normal merge, verify feature-head containment, resulting-main divergence, merge parents, and deletion of `package/es-p08-item-confiscation`; record those GitHub-generated facts in PR #128 metadata/comments. Do not create a follow-up `main` commit solely to insert self-referential merge evidence. Then stop without activating ES-X02.