# ES-D09 — Discord evidence, cases, notes and linked-alt alerts

Status: `PLANNED`. Priority: 138. Depends on `ES-D06`, `ES-D07`. Internal package.

## Objective
Complete Discord investigation state on the same authoritative case/audit system.

## Scope
Automatic bounded message-context evidence (message/author/guild/channel/time/IDs/link/attachments metadata, up to five before/five after, edited form when available); `Capture more context`; person/Discord/Minecraft/case private notes with visibility and edit history; automatic punishment case attach/create; investigation-only cases; meaningful-activity tracking and 30-day inactive closure; evidence retention until 30 days after punishment end/case close; linked-alt/evasion durable alerts to online Minecraft staff and Discord staff role `1497476349244211311` with manual decision only.

## Privacy
Linked accounts/evidence are private staff data. Copy/validate evidence into Enthusia-controlled durable storage where required; Discord CDN links alone are not permanent authority. Never put private evidence in public bot/API/logs/artifacts.

## Validation
Retention/expiry/edit-history/case inactivity/evasion alert tests, privacy/redaction, restart/idempotency, bounded context capture and full CI/review.
