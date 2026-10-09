# Punishment workflow

## Authority

Every command, GUI click, automation event, website action, and API request must end at an application service that evaluates the current actor rank. Bukkit permissions control discovery and early feedback; they are not the authority boundary.

- Mod may apply the authoritative configured step, lower a recommendation, end or revoke a sanction while retaining history, and request a full overturn.
- Developer may read punishment, case, report, and diagnostic data but cannot create or modify punishment state.
- Admin may apply configured steps and raise or lower a recommendation. A custom duration using configured sanction types is submitted as a durable request requiring Founder approval; Admin may still fully overturn and decide ordinary overturn requests.
- Founder has full punishment and recovery authority.

Historical cases keep the original actor and rank. A current policy change never deletes or rewrites a historical Developer-issued case.

## GUI and command flow

`/punish` opens a paginated online-player picker for authorized in-game staff. `/punish <player>` opens that player's central category screen directly, including offline and historical identities resolved by the authoritative directory. `/ban`, `/mute`, `/warn`, `/kick`, and `/ipban` with only a target open the same workflow with reason policies filtered by sanction type. Console and automation callers may continue to use the explicit `<player> <reason-id>` form.

The inventory UI uses the complete 54-slot page rather than chat walls. Its persistent header exposes the target, punishment-history summary, active sanctions, open-report count and current workflow phase. Category entries include recent related-family counts. Exact reasons show severity, required rank, exact/family history, decay state, ladder length and a bounded example. The review page displays the full configured ladder with prior steps in green, the authoritative current recommendation in gold, future steps in gray and permanent outcomes in red. Boolean states use explicit green `YES` / red `NO` lines. A dedicated History control opens a paginated punishment-history browser and returns to the screen it came from.

The in-game flow is:

1. Select a player (when starting from bare `/punish`), category, and configured exact reason.
2. Calculate the authoritative ladder step from current non-overturned related history.
3. Review the exact reason ID, policy version, raw and effective ordinals, recency contribution, complete configured ladder, sanction types and durations, visibility, internal explanation, and relevant player context.
4. Confirm once. Confirmation re-resolves the draft, reauthorizes the current actor, recalculates the recommendation, and rejects a stale review.

Punishments and warnings are public unless staff explicitly toggles the review to private. Internal explanations remain private regardless of case visibility.

## Durable drafts

A reason selection creates a MariaDB `punishment_drafts` row. The row is bound to the staff UUID and target UUID, contains the complete reviewed recommendation, and expires after 24 hours. One current draft is retained for each actor and target. Creating a new review replaces that pair's older draft.

Closing the review does not create a case. Use the clickable Resume message or `/punish resume <player>` on any Paper backend connected to the same database. Drafts survive logout, server switch, process crash, and restart.

For command confirmation, use `/punish confirm <player>` with the reviewed player's current name (including a Bedrock name prefix). The command resolves the stored player identity and selects only your unexpired draft for that player. Offline players work when their name is known to the player directory. If no matching draft exists, no action is taken. Existing `/punish confirm <draft-id>` commands still work, including for targets without a known username. Confirmation rechecks the selected draft, current authority, target hierarchy, and reviewed recommendation before applying anything.

The draft UUID is also the punishment idempotency identity. Concurrent or retried confirmations can create at most one case. A successful case commit deletes the draft; if only cleanup fails, the committed case ID is reported and retrying the same confirmation is safe. Expired drafts are ignored and pruned.

Ladder edits do not mutate a reviewed snapshot. If the active configuration no longer exactly matches its version, step label, ordinal, and sanctions, confirmation returns `RECOMMENDATION_CHANGED` and opens a fresh review without creating a case.

## Operational behavior

Draft preparation and confirmation require `ACTIVE` mode. MariaDB or policy unavailability blocks the operation. JDBC work runs on the bounded worker pool, while inventory rendering and player messages return to the owning entity scheduler. Closing a draft or losing the optional GUI state never weakens the service-layer checks.
