# ES-D15 — Discord migration/cutover acceptance

Status: `PLANNED`. Priority: 144. Depends on `ES-D01`–`ES-D14` as applicable. Acceptance package.

## Objective
Prove the completed Discord platform is safe to operate and produce the migration/cutover runbook. This package distinguishes staging acceptance from separately authorized production cutover.

## Scope
Reconcile/import strategy for native Discord bans and DiscordSRV links without unban/reban churn; role-sync parity; AutoMod shadow/enforcement acceptance; permission/hierarchy denial; temp expiry; evidence retention; duplicate/replay; process kill/restart; Discord outage/rate limiting; MariaDB outage/recovery; one-platform partial cross-platform failure; public/private trust-boundary attacks; native manual ban/unban drift observation/reconciliation; rollback and operator runbook; release/readiness checklist.

## Hard boundaries
Do not call unexplained native-ban mismatch acceptable. Do not disable DiscordSRV role sync, native AutoMod/day-to-day controls, LiteBans, or current authority merely because code exists. Actual production data import, bot/config deployment, authority/cutover, or issue #43 activity requires the separate explicit authorization mandated by repository policy. If that authorization is absent, complete all code/staging/runbook evidence and publish the exact remaining production gate rather than fabricating a pass.

## Validation
Full multi-runtime acceptance matrix on exact release candidates, staging guild/runtime where applicable, migration idempotency/reconciliation, security/privacy review, all repository gates and zero valid review findings. Completion means every safely testable requirement passes and any production-only gate is explicitly dispositioned under current owner authorization policy.
