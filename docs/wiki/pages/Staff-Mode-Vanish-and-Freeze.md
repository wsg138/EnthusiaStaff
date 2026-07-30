# Staff Mode, Vanish, and Freeze

These are separate systems:

- **Staff mode** protects and restores the staff member's normal player state.
- **Vanish** controls who can perceive or interact with the staff member.
- **Freeze** restricts a target player during an investigation.

> **Current status:** Commands and persistence components exist, but complete
> live Paper/Velocity behavior, visibility coverage, crash recovery, provider
> integration, and staging verification remain partial.

## Staff mode

Toggle with:

```text
/staff
```

### Entering

The intended entry sequence is:

1. Check CombatLogX or the configured combat provider.
2. Capture the staff member's full normal state.
3. Persist and verify the snapshot.
4. Clear normal inventory/state.
5. Apply staff state and tools.
6. Mark the durable session active.

The snapshot includes inventory, armor, offhand, XP, health, hunger,
saturation, effects, location, backend, game mode, flight, metadata, checksum,
and revision.

Do not enter staff mode to escape combat, death, travel consequences, or normal
gameplay restrictions.

### Leaving

Exit should:

1. Remove every staff item and temporary capability.
2. Restore the exact normal state.
3. Restore location/backend only when safe.
4. Verify the restored checksum/revision.
5. Close the durable session.

If restoration fails or is ambiguous, do not manually rebuild the inventory
from memory. Keep the session in recovery and escalate.

### Reconnect and crash behavior

An active session is expected to resume after reconnect or restart until a
normal verified exit. The original snapshot must not be replaced by a later
staff-state snapshot.

Staff-mode and vanished players must not combat-tag others or be combat-tagged.

### Rank restrictions

Target defaults:

- Mod: no creative; no Ender chest access
- Developer: no creative; no Ender chest access
- Admin: creative allowed; Ender view-only
- Founder: creative allowed; normal Ender access

Verify actual deployed configuration before relying on these capabilities.

## Vanish

Toggle with:

```text
/vanish
```

Vanish is separate from staff mode. Mod and Developer require staff mode before
vanishing. Admin and Founder may be configured to vanish independently.

### Default visibility hierarchy

| Viewer | Can see |
| --- | --- |
| Mod | Mod and Developer |
| Developer | Mod and Developer |
| Admin | Mod, Developer, and Admin; not Founder |
| Founder | Everyone |

The matrix is configurable.

### What vanish must cover

`hidePlayer` alone is insufficient. Central visibility must govern:

- Tab list and player counts
- Join and quit rendering
- `/seen`
- Teleport, message, and pay completions
- Notifications and playtime
- RoseChat recipients
- Voice recipients
- Sounds and particles
- Container animations
- Entity tracking
- Public plugin APIs

If one of these providers is unavailable, verify output must identify the gap.
Do not tell staff they are fully hidden when only Bukkit visibility works.

### Vanish conduct

Vanish does not authorize unrelated surveillance. Use it for reports,
investigations, approved tests, and staff safety. Do not use it to locate
private bases or gain gameplay information for yourself or others.

## Freeze

Apply and release:

```text
/freeze <player> <reason>
/unfreeze <player> <reason> CONFIRM
```

A frozen player is intended to be unable to:

- Move
- Take damage
- Move inventory
- Keep unrelated GUIs open
- Open containers
- Drop, pick up, or use items
- Break or place blocks
- Teleport
- Switch backend
- Run commands
- Interact with the world

The player sees their own chat normally; staff receive it; ordinary players do
not. The player is not told that the audience is staff-only.

### Freeze procedure

1. Verify the target.
2. State a factual internal reason.
3. Apply freeze once.
4. Preserve evidence immediately.
5. Assign one staff owner.
6. Conduct the investigation without unnecessary delay.
7. Unfreeze when the investigation no longer needs restriction.
8. Punish separately through the case workflow if a violation is proven.

Freeze is not a punishment duration and should not be left unattended.

### Disconnects

The target design restores a freeze when the player reconnects within 10
minutes. The default offline expiration is 10 minutes, with options to extend
or require unfreeze on next login. Verify deployment behavior before promising
a player that disconnecting will or will not clear a freeze.
