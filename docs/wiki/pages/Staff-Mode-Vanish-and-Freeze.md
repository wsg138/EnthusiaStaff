# Staff Mode, Vanish, and Freeze

These tools serve different purposes:

- **Staff mode** separates staff work from normal gameplay.
- **Vanish** hides a staff member when silent observation is needed.
- **Freeze** temporarily restricts a player during an active investigation.

> **Current status:** The commands and persistence code exist, but some visibility,
> recovery, external-plugin, and live-network behavior still require staging.
> Continue following the currently approved production procedure.

## Staff mode

Toggle staff mode with:

```text
/staff
```

Use staff mode when you are actively handling reports, investigating players, or
using staff-only tools. Leave it when the work is finished.

### Before entering

- Finish or leave normal combat first.
- Store nothing by manually moving items around to “prepare.”
- Make sure you are entering for a real staff reason.

Staff mode is intended to save your normal state and restore it when you leave.
Do not use it to avoid death, escape combat, travel for free, or protect normal
items.

### While in staff mode

- Do not move staff items into normal inventories or containers.
- Do not gather resources, trade, or participate in normal gameplay.
- Use inspection tools only for the report or investigation you are handling.
- Keep private locations and player information confidential.

The upcoming Helper role is intended to receive a limited staff-mode toolset.
Helpers should be able to investigate and view relevant information, but not edit
player inventories or use advanced staff tools.

### Leaving staff mode

Run `/staff` again. Wait for the normal state to restore before disconnecting or
switching servers.

If items, location, experience, effects, or game mode do not restore correctly:

1. stop using the affected inventory;
2. do not try to rebuild it manually;
3. record what is missing or unexpected;
4. contact an Admin or Founder.

## Vanish

Toggle vanish with:

```text
/vanish
```

Use vanish when being visible would interfere with an investigation, such as
watching suspected cheating, observing a reported interaction, or checking an
active exploit.

Helpers, Mods, and Developers are intended to enter staff mode before vanishing.
Leaving staff mode should also remove vanish for those roles. Admin and Founder
behavior may differ according to the deployed configuration.

### What staff should expect

The current implementation:

- hides the vanished player from viewers who are not allowed to see them;
- suppresses the vanished player’s join and quit messages;
- restores the saved vanish state after restart when storage is available;
- applies the configured staff visibility hierarchy.

Not every external plugin is automatically covered. Chat, voice, `/seen`,
teleport suggestions, player counts, particles, sounds, and other integrations
must use the shared visibility service or their own approved adapter. Do not
promise that vanish is completely invisible until the deployed integration checks
confirm it.

For the exact events, API calls, packet limitations, and visibility matrix, see
[[Vanish Internals]].

### Vanish conduct

Vanish does not give permission to browse unrelated bases, inventories, private
conversations, or player activity. Information learned through vanish must not be
used for normal gameplay or shared with friends or guild members.

## Freeze

Apply and release a freeze with:

```text
/freeze <player> <reason>
/unfreeze <player> <reason> CONFIRM
```

Use freeze only when an active investigation requires the player to remain in
place or prevents them from changing evidence. It is not a punishment duration.

### Freeze procedure

1. Confirm you are freezing the correct player.
2. Give a factual staff reason.
3. Tell the staff team who is handling the investigation.
4. Preserve the relevant evidence immediately.
5. Continue the investigation without unnecessary delay.
6. Unfreeze the player when the restriction is no longer needed.
7. Apply any punishment separately through the normal punishment workflow.

A frozen player should not be left unattended. If you need to leave, hand the
case to another staff member or release the freeze when safe.

### What freeze is intended to restrict

Depending on the deployed version, freeze is intended to stop movement, item and
inventory changes, block interaction, teleporting, server switching, commands,
and other actions that could interfere with the investigation.

If an action still works when it should be blocked, preserve the evidence and
report the bypass instead of repeatedly testing it on a live player.

### Disconnects

A disconnect does not automatically prove guilt. Record the disconnect and follow
the configured reconnect procedure. The target design supports restoring an
active freeze during the configured offline window, but live behavior must be
verified before staff rely on an exact time limit.

## Related pages

- [[Staff Handbook]]
- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Helper Guide]]
- [[Reports and Evidence]]
- [[Incident Playbooks]]
- [[Vanish Internals]]