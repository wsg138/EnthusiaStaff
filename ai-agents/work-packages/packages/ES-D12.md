# ES-D12 — Staff website Discord expansion

Status: `PLANNED`. Priority: 141. Depends on `ES-D02`, `ES-D07`, `ES-D09`. Internal/website component work; live collision preflight is mandatory.

## Objective
Expose complex Discord moderation review/configuration through the staff website while keeping the same authoritative services/state as Discord/Minecraft.

## Scope
Discord-aware identity/history/search, evidence review, case ownership/related cases where required, notes, active punishments, revoke/overturn/approval review, website-only appeals integration, AutoMod configuration/audit/shadow review and appropriate filters. Reuse/extract the existing signed internal-service authentication pattern; do not create a second moderation backend.

## Collision rule
The owner may independently change website code. Before claiming D12 inspect all website branches/PRs and changed paths. If material overlap exists, classify D12 `PARKED_BLOCKED` and do not overwrite/rebase/absorb that work. Another dependency-complete Discord package may be selected instead by a fresh worker.

## Validation
Website/service auth/replay/authorization/redaction tests, API contract tests, frontend build/lint/tests, privacy checks and full applicable repository/component review. Public surfaces must never expose linked-alt/private moderation data.
