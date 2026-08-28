# ES-D13 — Discord role-sync replacement

Status: `ACTIVE`. Priority: 142. Depends on `ES-D04`, `ES-D05`. Internal package.

## Claim

Selected 2026-08-28 after live reconciliation. Starting `main` SHA: `500136b37c9acc30b1de8a057feb79d3d16fc400`. Implementation branch: `package/es-d13-role-sync-replacement`.

`ES-D06` remains the higher-priority actionable continuation on PR #177, but another worker is actively changing that exact branch/PR during this run. The Discord program collision rule therefore parks D06 for this worker and permits dependency-complete D13. D13 must not absorb, overwrite, close, supersede, or rewrite D06 or unrelated ES-X03 work.

Migration boundary at claim: `main` contains immutable V1–V20. D13 does not claim V21 because active ES-X03 has a branch migration that must be reconciled against the post-D04 migration ceiling. D13 remains migration-free unless live coordination later proves a new migration version is safely available.

## Objective
Replace DiscordSRV role sync with reliable one-way Enthusia/Minecraft→Discord reconciliation while keeping moderation authorization separate.

## Scope
Evaluate role eligibility across all current linked Minecraft accounts; deterministic desired-role projection; protected/unmanaged role allowlist; durable reconciliation worker; add/remove idempotency; guild/native hierarchy handling; rate-limit/restart recovery; audit/diagnostics; migration parity comparison against DiscordSRV; only remove DiscordSRV role-sync dependency after parity validation. DiscordSRV may remain for console functionality.

## Exclusions
No Discord→Minecraft authority, no moderation permission derived from roles, no production switch until validated/authorized.

## Validation
Multi-account role union/conflict tests, unlink/main-account behavior, hierarchy/rate-limit/outage/restart tests, staging parity with legacy role sync and full CI/review.
