# Punishment workflow

## Authority

Every command, GUI click, automation event, website action, and API request must end at an application service that evaluates the current actor rank. Bukkit permissions control discovery and early feedback; they are not the authority boundary.

- Mod may apply the authoritative configured step, lower a recommendation, end or revoke a sanction while retaining history, and request a full overturn.
- Developer may read punishment, case, report, and diagnostic data but cannot create or modify punishment state.
- Admin may apply configured steps, raise or lower a recommendation, use custom durations with configured sanction types, fully overturn, and decide overturn requests.
- Founder has full punishment and recovery authority.

Historical cases keep the original actor and rank. A current policy change never deletes or rewrites a historical Developer-issued case.

## GUI and command flow

`/punish <player>` opens the central category screen. `/ban`, `/mute`, `/warn`, `/kick`, and `/ipban` open the same workflow with reason policies filtered by sanction type. Console and automation callers may continue to use the explicit `<player> <reason-id>` form.

The in-game flow is:

1. Choose a category and configured reason.
2. Calculate the authoritative ladder step from current non-overturned related history.
3. Review the exact reason ID, policy version, raw and effective ordinals, recency contribution, sanction types and durations, visibility, and internal explanation.
4. Confirm once. Confirmation re-resolves the draft, reauthorizes the current actor, recalculates the recommendation, and rejects a stale review.

Punishments and warnings are public unless staff explicitly toggles the review to private. Internal explanations remain private regardless of case visibility.

## Durable drafts

A reason selection creates a MariaDB `punishment_drafts` row. The row is bound to the staff UUID and target UUID, contains the complete reviewed recommendation, and expires after 24 hours. One current draft is retained for each actor and target. Creating a new review replaces that pair's older draft.

Closing the review does not create a case. Use the clickable Resume message or `/punish resume <player>` on any Paper backend connected to the same database. Drafts survive logout, server switch, process crash, and restart.

The draft UUID is also the punishment idempotency identity. Concurrent or retried confirmations can create at most one case. A successful case commit deletes the draft; if only cleanup fails, the committed case ID is reported and retrying the same confirmation is safe. Expired drafts are ignored and pruned.

Ladder edits do not mutate a reviewed snapshot. If the active configuration no longer exactly matches its version, step label, ordinal, and sanctions, confirmation returns `RECOMMENDATION_CHANGED` and opens a fresh review without creating a case.

## Operational behavior

Draft preparation and confirmation require `ACTIVE` mode. MariaDB or policy unavailability blocks the operation. JDBC work runs on the bounded worker pool, while inventory rendering and player messages return to the owning entity scheduler. Closing a draft or losing the optional GUI state never weakens the service-layer checks.
