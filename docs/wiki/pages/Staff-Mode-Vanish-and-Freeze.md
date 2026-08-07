# Staff Mode, Vanish, and Freeze

These are separate tools:

- **Staff mode** separates staff work from normal gameplay and supplies the operational hotbar.
- **Vanish** hides a staff member when visible observation would interfere.
- **Freeze** temporarily restricts a player during an active investigation.

This page explains staff procedure. For implementation status and source links, use
[[Staff Tools, Investigations, and Player-State Safety]]. For packet and visibility
internals, use [[Vanish Internals]].

> **Deployment boundary:** repository implementation and hosted validation are not
> production acceptance. Representative Java/Bedrock and distributed staging remains
> owned by the later validation package `ES-V02`, and LiteBans remains authoritative
> until the separate cutover process is approved.

## Quick navigation

- Ordinary investigation flow: [[Staff Quick Start|Moderator-Quick-Start]]
- Rank limits: [[Roles and Permissions|Rank-Authority]]
- Command and permission reference: [[Commands and Permissions]]
- Inventory safety: [[Inventory and Confiscation Safety]]
- Incident response: [[Incident Playbooks]]
- Feature status and files: [[Staff Tools, Investigations, and Player-State Safety]]
- Vanish source behavior: [[Vanish Internals]]

## Staff mode

Toggle with:

```text
/staff
```

Use staff mode when actively handling reports, investigating players or using
staff-only tools. Leave it when the work is finished.

### Entry and recovery guarantees

Staff mode saves the normal player state durably before the temporary staff
profile is applied. The saved state includes the inventory, armor, offhand, XP,
health, hunger, effects, location/server, game mode and flight-related state that
must be restored on exit.

Entry fails closed when combat state cannot be verified, storage is unavailable,
the worker queue is full or durable snapshot creation fails. Staff items are not
issued before the durable session exists.

A reconnect, plugin restart or server restart does not create a second normal-state
snapshot. An already-active session is recovered from its durable snapshot and the
staff profile is re-applied. An exiting or recovery-required session restores the
saved state instead. If the staff member loses the explicit staff rank while the
session is active, the runtime starts a durable exit rather than leaving an
unauthorized staff profile active.

### Before entering

- Finish or leave normal combat first.
- Do not move normal items around to “prepare” for staff mode.
- Enter only for a legitimate staff reason.
- Confirm `/estaff status` and `/estaff verify` are not reporting a storage or recovery failure.

Staff mode must never be used to escape death, avoid combat, travel for normal
play or protect ordinary items.

## Operational hotbar

The hotbar uses fixed slots so the same action does not move when rank-specific
tools change:

| Slot | Tool | Normal action | Command/text fallback |
| ---: | --- | --- | --- |
| 1 | Random Player Teleport | Right-click to choose one suitable online player and teleport | `/stafftools random` |
| 2 | Player Inspector | Right-click the player | `/inspect <player>` |
| 3 | Freeze | Right-click the player; the tool supplies the investigation reason | `/freeze <player> <reason>` |
| 4 | Reports | Right-click to open staff report management | `/reports` |
| 5 | Cheat Tester | Reserved for advanced ranks; intentionally not implemented by this package | Future `ES-P10` |
| 6 | Follow or Spectate | Right-click a player | `/stafftools spectate <player>` |
| 7 | Vanish | Right-click to use the normal vanish command path | `/vanish` |
| 8 | Staff Chat | Right-click to use the configured RoseChat staff-channel path | `/staffchat` |
| 9 | Staff Tools Menu | Right-click for a text/clickable command menu | `/stafftools` |

The tool item is only a routing surface. It does **not** grant authority. Every
use is rechecked against the active staff session, current explicit rank, canonical
slot/material and the action permission. Durable moderation actions then pass
through their existing command/service boundary so the hotbar cannot bypass
operational-mode checks, provider availability, hierarchy or audit behavior.

### Stale, copied and transferred tools

Server-issued staff tools carry an owner UUID and a random token for the current
staff-profile application. A tool is rejected when any of these facts is wrong:

- there is no active, non-transitioning staff session;
- the tool ID is unknown;
- the owner is another player;
- the token belongs to an older session/profile;
- the item is in the wrong hotbar slot;
- the material does not match the canonical tool;
- the current rank is not allowed to receive that tool.

Inventory, drag, drop, hand-swap and transfer protections remain in force while
staff mode is active. Exact normal state is restored from the durable snapshot;
staff items are never intentionally merged into the normal inventory.

## Random Player Teleport

Random teleport is an investigation tool, not a free-travel command. Candidate
state is sampled on the target player's Folia entity scheduler. A candidate is
excluded when it is:

- the staff member themself;
- another active staff-mode player;
- vanished;
- frozen/restricted;
- granted `enthusiastaff.stafftools.random-exempt`;
- dead, sleeping, inside a vehicle or in spectator mode;
- in a configured disabled world.

The entire action can also be disabled on selected backend IDs. The staff session,
rank and permission are rechecked before the final teleport. If no suitable player
exists or the asynchronous teleport fails, no durable moderation state is changed.

Restart-scoped configuration:

```yaml
staff-tools:
  random-teleport:
    disabled-servers: []
    disabled-worlds: []
  cooldowns:
    random-teleport-millis: 2000
    target-tool-millis: 750
    toggle-tool-millis: 500
    menu-millis: 500
```

Cooldown values must be between `0` and `60000` milliseconds. Changes in this
section require a server restart; `/estaff reload` does not apply them.

## Follow or Spectate

`/stafftools spectate <player>` and the slot-6 tool use the same path. The target
must be online on the current backend, cannot be the actor, cannot be vanished and
can opt out through `enthusiastaff.stafftools.spectate-exempt`.

The target location is captured on the target entity scheduler. The staff member
then uses Paper's asynchronous teleport. A spectator-profile rank attaches to the
live target when that remains safe and available. A creative staff profile follows
by teleporting without changing its required game mode; staff mode does not weaken
rank-profile enforcement merely to force spectator mode.

## Inspector, Freeze, Reports, Vanish and Staff Chat

These hotbar actions intentionally reuse the existing commands:

- Inspector -> `/inspect <player>`
- Freeze -> `/freeze <player> Staff-mode tool investigation`
- Reports -> `/reports`
- Vanish -> `/vanish`
- Staff Chat -> `/staffchat`

That means a missing provider, non-`ACTIVE` destructive mode, denied hierarchy or
missing permission fails in the same place and with the same safety behavior as a
typed command. The dispatcher does not invent replacement provider callbacks.

## Staff Tools menu and Bedrock fallback

`/stafftools` is deliberately usable without a custom inventory GUI. It prints the
operational actions and clickable command suggestions where the client supports
them. Bedrock/Geyser clients can type the displayed commands directly, so a client
that does not expose Java click events is not blocked from the feature.

Useful fallbacks:

```text
/stafftools
/stafftools random
/stafftools spectate <player>
/inspect <player>
/freeze <player> <reason>
/reports
/vanish
/staffchat
/staff
```

## Permissions

The direct staff-tool nodes are:

```text
enthusiastaff.stafftools.teleport
enthusiastaff.stafftools.spectate
enthusiastaff.stafftools.menu
enthusiastaff.stafftools.random-exempt
enthusiastaff.stafftools.spectate-exempt
```

The first three are included in the current Helper and Developer aggregate ranks;
Mod/Admin/Founder inherit the appropriate staff aggregate. The two exemption nodes
default to `false` and must be assigned deliberately.

The command-backed tools still require their normal permission nodes such as
`enthusiastaff.inspect`, `enthusiastaff.freeze`, `enthusiastaff.reports.manage`,
`enthusiastaff.vanish` and `enthusiastaff.staffchat`.

## Vanish

Toggle with:

```text
/vanish
```

Use vanish for silent observation, such as watching suspected cheating,
observing a reported interaction or checking an active exploit. Vanish is separate
from staff mode and remains governed by its own durable state, rank visibility
matrix and provider behavior.

Vanish does not permit browsing unrelated bases, inventories or private activity.
Information learned through staff observation must not be used for gameplay or
shared outside the moderation purpose.

See [[Vanish Internals]] for packet/tracker behavior and remaining staging limits.

## Freeze

Apply and release with:

```text
/freeze <player> <reason>
/unfreeze <player> <reason> CONFIRM
```

Freeze is an investigation control, not a punishment duration. The staff hotbar
uses the same durable freeze path; it does not have a separate in-memory freeze.

A frozen player should not be left unattended. Preserve relevant evidence, hand
the investigation to another staff member if necessary, and unfreeze when the
restriction is no longer needed. A punishment remains a separate decision through
[[Punishment System]].

## Leaving staff mode

Run `/staff` again. Exit first marks the durable session as exiting, removes
temporary staff tools, restores the saved snapshot, captures the restored state
again and closes the session only after checksum verification succeeds.

Do not manually rebuild inventory or normal state during a recovery incident.

## Troubleshooting and recovery

### A tool says it is stale, belongs to another player, or has the wrong session

Do not copy or retag it. Exit staff mode normally if possible and re-enter only
after the durable exit reports successful restoration. Old-session items are
intentionally rejected.

### A command-backed tool says the action is unavailable

Use `/estaff status` and `/estaff verify`, then try the documented typed command.
The affected provider or storage subsystem may be unavailable. Do not substitute a
raw database or unrelated plugin command to bypass the failure.

### Staff mode does not restore exact normal state

1. stop changing the affected player state;
2. do not move or recreate items manually;
3. preserve the exact message and backend ID;
4. have an Admin/Founder inspect runtime/storage diagnostics;
5. keep the durable recovery record intact until the restore path succeeds.

### Random teleport or follow has no target

Confirm the target is online on the same backend and not excluded by vanish,
freeze, exemption, unsafe state or disabled-world/server configuration. A no-target
result is a safe refusal, not a reason to weaken the filters.

### Restart or reconnect during staff mode

Allow startup/join recovery to resume the durable session. Do not run a second
manual inventory restore. If the runtime reports `RECOVERY_REQUIRED`, follow
[[Recovery and Troubleshooting]] rather than improvising.

## Stop and ask for help when

- staff mode fails to enter or exit cleanly;
- normal state does not restore exactly;
- a staff item appears outside the active owner's session;
- a tool works without its expected permission or rank;
- a vanished/exempt player is selected by a movement tool;
- freeze or another command-backed action reports storage/provider failure;
- retry safety is uncertain.

## Related pages

- [[Staff Handbook]]
- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Helper Guide]]
- [[Commands and Permissions]]
- [[Reports and Evidence]]
- [[Inventory and Confiscation Safety]]
- [[Recovery and Troubleshooting]]
- [[Incident Playbooks]]
- [[Vanish Internals]]
- [[Staff Tools, Investigations, and Player-State Safety]]
