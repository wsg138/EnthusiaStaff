# Moderator Quick Start

This is the practical reference for ordinary moderation work. It does not grant
authority beyond [[Rank Authority]] or change the current deployment status.

## Start of shift

1. Check `/estaff status`.
2. Review unread alerts and active recovery warnings.
3. Confirm which moderation system is authoritative.
4. Enter `/staff` only when beginning staff work.
5. Use `/vanish` when the investigation requires hidden presence.
6. Use `/staffchat` for coordination that should not appear in public chat.

Do not begin destructive work if the runtime is in `READ_ONLY_FAILURE`, the
required database is unavailable, or the relevant integration is disabled.

## Handling a report

1. Open `/reports`.
2. Claim the report before acting.
3. Read the stated reason, description, location, time, chat context, client
   stamp, related reports, and relevant history.
4. Spectate before teleporting when practical.
5. Freeze only when movement or evidence destruction must be stopped.
6. Use `/punish <player>` from the report flow when the violation is proven.
7. Close the report as violation or no violation with a clear internal note.

Reporter identity remains staff-only. The target is not entitled to the
reporter’s name, private messages, or coordinates.

## Applying a punishment

1. Run `/punish <player>`.
2. Verify the resolved identity.
3. Choose the broad category.
4. Choose the exact reason whose examples match the evidence.
5. Review related-family history, decay, current recommendation, and ladder.
6. Add a factual internal explanation and link the report/evidence.
7. Change public visibility only when policy allows it.
8. Confirm once.

Mods may use configured steps, lower a recommendation where permitted, end or
revoke sanctions while preserving history, and request a full overturn. Mods
may not raise above the recommendation, invent arbitrary sanction
combinations, or directly fully overturn.

## Ending or correcting a punishment

Use the central sanction-change workflow:

- `/unban <player|case> <reason> [CONFIRM]`
- `/unmute <player|case> <reason> [CONFIRM]`
- `/removewarning <player|case> <reason> [CONFIRM]`
- `/removepunishment <player|case> <action> ...`

Always target the exact case or sanction when multiple active sanctions exist.
“Wrong player,” “wrong reason,” and “new evidence” are not interchangeable
reasons. A full overturn means the punishment should not contribute as valid
history and requires the configured approval path.

## Freezing a player

Use `/freeze <player> <reason>` when immediate restriction is necessary for an
active investigation.

After freezing:

1. Tell staff what is being investigated.
2. Preserve evidence before asking the player to change anything.
3. Do not leave the freeze running without an owner.
4. If the player disconnects, follow the configured reconnect window.
5. Release with `/unfreeze <player> <reason> CONFIRM` when the reason ends.

Freeze is not a punishment substitute. It is a temporary investigation control.

## Viewing inventories

- `/invsee <player|uuid>`
- `/endersee <player|uuid>`

Viewing and editing are different permissions. Before editing, confirm the
target, backend, inventory scope, current revision, and whether another viewer
or recovery operation is active.

Use case-linked confiscation for punishment evidence or asset removal. Do not
simulate confiscation by manually deleting items through ordinary inventory
editing.

## Common stop conditions

Stop and escalate when you see:

- Stale revision or stale draft
- Conflicting operation
- Quarantined inventory/economy work
- Missing MariaDB or Velocity authority
- Unknown command owner
- Integration unavailable
- Target identity ambiguity
- Unexpected server switch
- Confirmation repeated after timeout
- Punishment recommendation changed
- Any sign that the action partially applied

See [[Incident Playbooks]] and [[Recovery and Troubleshooting]].
