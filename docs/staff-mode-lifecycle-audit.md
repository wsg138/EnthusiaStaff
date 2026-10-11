# Staff Mode lifecycle audit — source-only candidate

Status: **review candidate; not deployed or end-to-end verified**. Tracks [#488](https://github.com/wsg138/EnthusiaStaff/issues/488).

## Event boundaries

- `staff-mode-entered`: emitted after the first local staff-session activation succeeds and the active session matches the durable session. Failed entry, combat-tag rejection, unavailable database, or a profile-application failure must **not** emit success.
- `staff-mode-resumed`: emitted after a successful active-session recovery or a successful detached-session rebind. These are **not** fresh entries. The detail distinguishes `active-recovery` from `detached-rebind`.
- `staff-mode-exited`: emitted only after the player's original state has been restored, its checksum verified, and the durable session store's `completeExit` has returned true. The event includes session UUID, a safe fixed reason (`manual`, `rank-removed`, `profile-reconciliation-failed`, or `recovery`) and whether the local runtime session was active or had to be recovered.

Exit logging uses immutable actor name/rank/vanish data captured on the player's entity scheduler **before** restoration; the background persistence worker does not query Bukkit state. The event's `staffMode` flag is false after confirmed closure. All three event types use the existing staff-action JSONL, MariaDB outbox, and private `logs-staffmode` delivery path.

## Limitations / next acceptance

- **No live Discord delivery has been demonstrated.** Normal backend quit or server transfer is **not** a staff-mode exit; it must not emit `staff-mode-exited`. Terminal recovery of an already detached/closed snapshot without an available player is also not recorded by this incremental source patch.
- The logger is best effort. A crash after the database commits `completeExit` but before the audit append can omit an exit record. Repeated recovery invocation could record multiple resumption attempts; the logger provides stable IDs per emitted record, not an exactly-once lifecycle deduplication guarantee. The existing Discord HTTP delivery remains at least once.
- Synchronous local JSONL append remains on the entity thread for entry/resumption. No new network permissions, punishments, or gameplay policy are introduced.
- Before release, validate in a **private staging environment**: fresh entry, denied entry, normal exit, rank-forced exit, state restoration failure, recovered session, detached rebind, and normal backend handoff. Correlate session UUID and stable eventId through JSONL→MariaDB outbox→private Discord embed, and measure duplicates. Follow [staff-action-discord-recovery.md](staff-action-discord-recovery.md) for rollout, queue, and rollback checks.
- Never claim that every staff action or lifecycle event is covered or successfully delivered solely because this source PR merges.
