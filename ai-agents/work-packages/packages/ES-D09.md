# ES-D09 — Discord evidence, cases, notes and linked-alt alerts

Status: `ACTIVE`. Priority: 138. Depends on `ES-D06`, `ES-D07`. Internal package.

Starting `main`: `741c77bb4e82413715e7fdd5eb9c0be0beeea407`.
Branch: `package/es-d09-discord-investigations`.
Reserved migration: `V22__discord_investigation_state.sql`; live `main` ends at V20 and concurrent ES-X03 PR #139 already owns branch-local V21, so D09 must not claim V21.

## Objective
Complete Discord investigation state on the same authoritative case/audit system.

## Scope
Automatic bounded message-context evidence (message/author/guild/channel/time/IDs/link/attachments metadata, up to five before/five after, edited form when available); `Capture more context`; person/Discord/Minecraft/case private notes with visibility and edit history; automatic punishment case attach/create; investigation-only cases; meaningful-activity tracking and 30-day inactive closure; evidence retention until 30 days after punishment end/case close; linked-alt/evasion durable alerts to online Minecraft staff and Discord staff role `1497476349244211311` with manual decision only.

## Privacy
Linked accounts/evidence are private staff data. Copy/validate evidence into Enthusia-controlled durable storage where required; Discord CDN links alone are not permanent authority. Never put private evidence in public bot/API/logs/artifacts.

## Validation
Retention/expiry/edit-history/case inactivity/evasion alert tests, privacy/redaction, restart/idempotency, bounded context capture and full CI/review.

## Activation checkpoint — 2026-09-15

Live reconciliation confirmed D06 and D07 are complete and D09 is the only dependency-complete READY package in the dedicated Discord lane. D08 remains ineligible because its separate Minecraft integration-readiness proof is absent. D13 remains `BLOCKED` / `PARKED_BLOCKED` on PR #178 and is not touched.

Concurrent collision preflight preserved PR #199 Paper staff-menu work and PR #139 / ES-X03 Market work. PR #199 reports no persistence/schema changes; D09 avoids its staff-tools implementation paths. Live X03 has already serialized its Market migration as V21, so D09 reserves V22 and does not modify or absorb X03. The disposable D07 tooling branch remains unrelated and unmerged.

Issue #43 remains open/deferred. This package does not authorize production Discord mutation, production data access, LiteBans authority change, deployment, or cutover.
