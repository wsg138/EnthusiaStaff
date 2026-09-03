# ES-D08 — Cross-platform moderation integration

Status: `PLANNED`. Priority: 137. Depends on `ES-D07` and live proof that current Minecraft moderation services can accept the integration without changing production authority. Internal package.

## Objective
Allow explicit Discord, Minecraft, or Both moderation while preserving separate sanctions/enforcement state under one case.

## Scope
Discord→Minecraft and Minecraft→Discord staff entry points; explicit scope selector/default-by-origin; separate per-platform consequences on confirmation; same case/history context; independently persisted enforcement intents; partial-success/pending/retry/recovery UI and audit; required Minecraft GUI/ladder/domain integration. Never silently infer `Both`.

## Authority boundary
LiteBans/production authority remains unchanged unless separately authorized. Implement against supported EnthusiaStaff domain/application services and shadow/staging paths as necessary; do not route around current authority fencing.

## Validation
Cross-platform permission/hierarchy tests, missing-identity selector behavior, atomic intent persistence, one-side outage/restart/retry tests, duplicate/replay tests, staged Discord/Minecraft failure matrix and full repository gates.
