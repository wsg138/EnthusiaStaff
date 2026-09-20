# ES-D09 — Discord evidence, cases, notes and linked-alt alerts

Status: `BLOCKED` / `PARKED_BLOCKED`. Priority: 138. Depends on `ES-D06`, `ES-D07`. Internal package.

## Implementation state

Implementation PR: #203 (`package/es-d09-discord-investigations`).

Frozen executable/product head: `a48390c50c6968e75437abd2dd05c0faeece355d`.

PR #203 remains intentionally open and unmerged. Its executable head is frozen while the Staff migration chain is serialized; do not rebase, squash, renumber D09's migration, absorb X03, or merge the implementation while V21 is absent from `main`.

Canonical blocker handoff: `ai-agents/reports/package-handoffs/2026-09-19-es-d09-investigations-blocked.md`.

## Objective
Complete Discord investigation state on the same authoritative case/audit system.

## Scope
Automatic bounded message-context evidence (message/author/guild/channel/time/IDs/link/attachments metadata, up to five before/five after, edited form when available); `Capture more context`; person/Discord/Minecraft/case private notes with visibility and edit history; automatic punishment case attach/create; investigation-only cases; meaningful-activity tracking and 30-day inactive closure; evidence retention until 30 days after punishment end/case close; linked-alt/evasion durable alerts to online Minecraft staff and Discord staff role `1497476349244211311` with manual decision only.

## Privacy
Linked accounts/evidence are private staff data. Copy/validate evidence into Enthusia-controlled durable storage where required; Discord CDN links alone are not permanent authority. Never put private evidence in public bot/API/logs/artifacts. The Paper alert path carries only the generic alert UUID rather than private investigation contents.

## Validation
Retention/expiry/edit-history/case inactivity/evasion alert tests, privacy/redaction, restart/idempotency, bounded context capture and full CI/review.

## Exact frozen-head validation

Exact repair validation workflow `35387277563` / job `105737009460` passed on the candidate promoted as `a48390c50c6968e75437abd2dd05c0faeece355d`: clean Java 21 build/tests, MariaDB/Testcontainers integration tests, StaffBot runtime verification, PMD with zero valid changed-code findings, changed-method complexity bounds, regression bounds, and `git diff --check`.

On the exact frozen PR head `a48390c50c6968e75437abd2dd05c0faeece355d`, Coverage `35388283034` / job `105740323648`, Staff Bot PR Artifact `35388283005`, Staff Bot Configuration Cache `35388283022`, and Sentinel Restart Artifact `35388282989` all completed successfully. Codacy Static Code Analysis check `105740695905` completed successfully with zero annotations / zero new valid findings; Codacy diff-coverage and coverage-variation checks also succeeded. CodeRabbit commit status is successful. All substantive product-code review findings are resolved; the older stale active-handoff thread is superseded by this durable terminal publication.

The final repair set includes regression coverage for expired/future/permanent evasion candidates, administrator-role alert-channel privacy rejection, normal-punishment and LiteBans-import `cases.subject_id` persistence, and compatibility fallback for pre-existing target-only cases lacking `subject_id`.

## Migration serialization blocker

Canonical `main` currently owns Staff migrations only through V20. Concurrent `ES-X03` / PR #139 is the legitimate owner of branch-local `V21__market_compliance_journal.sql` and remains open/unmerged. D09 legitimately reserves `V22__discord_investigation_state.sql`.

D09 must not merge V22 while V21 is absent from `main`. Do not rename D09's V22 to V21 and do not take over X03.

Exact unblock: merge the legitimate owner of Staff migration V21 into `main`, then reconcile D09 with the resulting live migration chain, resolve only legitimate conflicts, rerun all exact-head executable gates affected by reconciliation, refresh review/Codacy evidence, and only then reconsider merging PR #203.

## Production boundary

No production Discord mutation, production/private data access, production deployment, Discord configuration change, LiteBans authority change, cutover, AutoMod enforcement, or issue #43 acceptance was performed by D09. Production Discord Message Content privileged-intent enablement/cutover remains outside this source package.
