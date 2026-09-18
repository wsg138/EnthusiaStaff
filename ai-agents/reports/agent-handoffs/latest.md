# Latest agent handoff

Current handoff: `ES-D09 — Discord evidence, cases, notes and linked-alt alerts` — `ACTIVE`.

Canonical package handoff: `ai-agents/reports/package-handoffs/2026-09-15-es-d09-active.md`.

D09 was claimed from exact `main` `741c77bb4e82413715e7fdd5eb9c0be0beeea407` on branch `package/es-d09-discord-investigations`. ES-D06 and ES-D07 are complete, so D09 is dependency-complete. D08 remains `PLANNED` because its separate Minecraft integration-readiness proof is absent. D13 remains `BLOCKED` / `PARKED_BLOCKED`; PR #178 stays open/unmerged and untouched.

Live collision preflight preserves PR #199 Paper staff-menu work and PR #139 / ES-X03 Market work. X03 already owns branch-local Staff migration V21, while current `main` ends at V20, so D09 reserves `V22__discord_investigation_state.sql` and does not take V21. The disposable D07 tooling branch is unrelated and is not merged or absorbed.

D09 is implementing the complete private investigation contract: bounded Discord message-context evidence and edit history, capture-more-context, private scoped notes with version history, automatic punishment-case association and investigation-only cases, meaningful-activity tracking and 30-day inactive closure, evidence retention, and durable linked-alt/evasion alerts with manual staff decision only. Private evidence, linked identities, notes and raw moderation state must not leak into public bot/API responses, logs, CI artifacts or handoff evidence.

No D09 exact-head validation or completion is claimed yet. Full clean Java 21 build/tests, migration/integration coverage, analyzers, hosted Codacy, review repair, applicable runtime/staging validation, final-main reconciliation, normal merge, containment, cleanup and terminal publication remain required.

Issue #43 remains open/deferred. Production Discord changes/data access, deployment, LiteBans authority change and cutover are outside this package.
