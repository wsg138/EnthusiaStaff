# Staff Mode, Vanish, and Freeze

These are separate tools:

- **Staff mode** separates staff work from normal gameplay.
- **Vanish** hides a staff member when visible observation would interfere.
- **Freeze** temporarily restricts a player during an active investigation.

This page explains staff procedure. For percentages, known gaps and direct source
links, use [[Staff Tools, Investigations, and Player-State Safety]]. For packet
and visibility internals, use [[Vanish Internals]].

> **Current deployment:** Commands and persistence foundations exist, but some
> recovery, visibility, external-plugin and live-network behavior still requires
> staging. Follow the live-server procedure actually approved for deployment.

## Quick navigation

- Ordinary investigation flow: [[Staff Quick Start|Moderator-Quick-Start]]
- Rank limits: [[Roles and Permissions|Rank-Authority]]
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

### Before entering

- Finish or leave normal combat first.
- Do not move normal items around to “prepare” for staff mode.
- Enter only for a legitimate staff reason.
- Confirm the server is not reporting storage/recovery failure.

Staff mode is intended to save normal state before applying a rank-specific staff
profile. It must never be used to escape death, avoid combat, travel for free or
protect ordinary items.

### While active

- Do not move staff items into normal inventories or containers.
- Do not gather resources, trade or participate in normal gameplay.
- Use inspection tools only for the current report/investigation.
- Keep private locations and player information confidential.
- Follow the restrictions of your actual rank even if another plugin exposes a
  vanilla command accidentally.

### Leaving staff mode

Run `/staff` again and wait for the normal state to restore before disconnecting
or switching servers.

If inventory, location, XP, effects, health, hunger, flight or game mode does not
restore correctly:

1. stop using the affected state;
2. do not rebuild or move items manually;
3. record exactly what is missing/unexpected;
4. contact an Admin or Founder.

The detailed snapshot, restore and crash-recovery gaps are listed in
[[Staff Tools, Investigations, and Player-State Safety]].

## Vanish

Toggle with:

```text
/vanish
```

Use vanish for silent observation, such as watching suspected cheating,
observing a reported interaction or checking an active exploit.

Helpers, Mods and Developers are intended to enter staff mode before vanishing.
Admin/Founder behavior may differ according to deployed policy.

### What vanish is intended to cover

- unauthorized tab/player-info visibility;
- entity spawn/tracking and spectator exposure;
- join/quit messages;
- command suggestions and player lists;
- chat, voice and external-plugin recipient behavior;
- sounds, particles and container side effects;
- public API visibility.

The current implementation covers only part of that complete surface. Bukkit
`hidePlayer` is one layer, not proof of full invisibility.

Do not promise a player or staff member that vanish is undetectable until the
exact deployed integrations and protocol versions have been verified.

### Vanish conduct

Vanish does not permit browsing unrelated bases, inventories or private activity.
Information learned through staff observation must not be used for gameplay or
shared with friends/guild members.

See [[Vanish Internals]] for events, scheduling, Paper visibility calls, packet
handling and known gaps.

## Freeze

Apply and release with:

```text
/freeze <player> <reason>
/unfreeze <player> <reason> CONFIRM
```

Freeze is an investigation control, not a punishment duration.

### Procedure

1. Confirm the correct target.
2. Give a factual staff reason.
3. Tell staff who is handling the investigation.
4. Preserve relevant evidence immediately.
5. Continue without unnecessary delay.
6. Unfreeze when the restriction is no longer needed.
7. Apply any punishment separately through [[Punishment System]].

A frozen player should not be left unattended. Hand the investigation to another
staff member or release the freeze when safe.

### Intended restrictions

The finished feature should block movement, damage, inventory/GUI changes, item
use/drop/pickup, containers, block interaction, teleporting, backend switching and
unauthorized commands. Chat should remain visible to the frozen player and staff
but not ordinary players.

If a bypass is discovered, preserve evidence and report it. Do not repeatedly test
it on a live player.

### Disconnects and recovery

A disconnect does not automatically prove guilt. Record it and follow the deployed
reconnect procedure. Freeze persistence, offline expiry, extension and restart
behavior must be verified before staff rely on exact timing.

Active development and remaining restrictions are listed in
[[Staff Tools, Investigations, and Player-State Safety]].

## Stop and ask for help when

- staff mode fails to enter or exit cleanly;
- normal state does not restore exactly;
- a staff item appears in normal play;
- a vanished player is exposed through tab, entity, command or integration behavior;
- freeze does not block an expected action;
- a player disconnects during a sensitive investigation;
- a command reports conflict, recovery or partial state;
- retry safety is uncertain.

## Related pages

- [[Staff Handbook]]
- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Helper Guide]]
- [[Reports and Evidence]]
- [[Inventory and Confiscation Safety]]
- [[Incident Playbooks]]
- [[Vanish Internals]]
- [[Staff Tools, Investigations, and Player-State Safety]]
