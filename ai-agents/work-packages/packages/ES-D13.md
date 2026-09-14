# ES-D13 — Discord role-sync replacement

Status: `PLANNED`. Priority: 142. Depends on `ES-D04`, `ES-D05`. Internal package.

## Objective
Replace DiscordSRV role sync with reliable one-way Enthusia/Minecraft→Discord reconciliation while keeping moderation authorization separate.

## Scope
Evaluate role eligibility across all current linked Minecraft accounts; deterministic desired-role projection; protected/unmanaged role allowlist; durable reconciliation worker; add/remove idempotency; guild/native hierarchy handling; rate-limit/restart recovery; audit/diagnostics; migration parity comparison against DiscordSRV; only remove DiscordSRV role-sync dependency after parity validation. DiscordSRV may remain for console functionality.

## Exclusions
No Discord→Minecraft authority, no moderation permission derived from roles, no production switch until validated/authorized.

## Validation
Multi-account role union/conflict tests, unlink/main-account behavior, hierarchy/rate-limit/outage/restart tests, staging parity with legacy role sync and full CI/review.
