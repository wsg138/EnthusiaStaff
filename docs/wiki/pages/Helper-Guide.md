# Helper Guide

Welcome to the Enthusia staff team. Helpers are the first level of moderation and
are expected to keep the server welcoming and fair while still being able to
enjoy playing normally.

> **Implementation note:** The Helper role is being developed on
> `section/helper-rank-authority`. The final live permissions depend on that work
> being merged, reviewed, and deployed. Follow the permissions actually available
> on the live server.

## What Helpers should focus on

- Help players and answer straightforward questions.
- Handle ordinary chat issues and minor disruption.
- Gather evidence for reports, cheating concerns, bugs, and exploits.
- Use warnings, mutes, and kicks when they clearly fit the situation.
- Ask a Mod or above to take over complicated, severe, or uncertain cases.
- Keep internal staff information private.

Nobody expects a new Helper to know everything. Asking before acting is a good
staff habit, not a failure.

## General expectations

- Treat players respectfully, including when they are upset with you.
- Use common sense and avoid turning small issues into arguments.
- Be fair when friends, enemies, or guild members are involved.
- Never use staff permissions to benefit yourself or another player.
- Do not share staff-chat screenshots, reports, private messages, or base
  coordinates.
- Let the team know when you will be unavailable for a while.
- Raise concerns about staff conduct privately with a Mod, Admin, or Founder.

When a conversation becomes hostile, step away or ask another staff member to
continue it. You do not need to argue with a player to prove the staff decision.

## Warnings

Use a warning for minor or first-time behavior that the player should be able to
correct immediately.

Common examples:

- minor chat misconduct;
- inappropriate language that does not require a stronger response;
- light spam;
- a minor rule reminder.

Use a clear reason. A warning should explain what needs to stop rather than simply
saying “behave.”

## Mutes

Use a mute when a player continues disrupting chat after being warned or commits
a more serious chat-related violation.

Common examples:

- repeated spam;
- excessive toxicity;
- slurs or seriously offensive language;
- sexually inappropriate comments;
- repeatedly ignoring earlier warnings.

Save a screenshot or the relevant captured chat context so another staff member
can understand the action and handle an appeal.

## Kicks

A kick immediately removes a player from the server. It is mainly used to stop
disruption, get the player’s attention, require a reconnect, or remove an
inappropriate username or skin until it is changed.

A kick is not normally the final punishment for continued rule-breaking. If the
behavior continues after the player returns, contact a Mod or above so the proper
punishment can be applied.

## Punishment interface

Use:

```text
/punish <player>
```

Choose the reason that best matches what happened and read the result shown by
the interface. The system is intended to calculate the configured duration from
the reason and relevant history, so Helpers should not need to memorize duration
ladders.

If the interface requests approval, send the request and wait for a Mod or above.
Do not use another command to avoid approval. Permanent or unusually severe
punishments should always receive a second staff review.

## Reports, cheating, bugs, and exploits

When handling a ticket:

1. Gather the facts before deciding what happened.
2. Watch the player or reproduce the issue when safe.
3. Use CoreProtect, logs, screenshots, clips, or other available evidence.
4. Keep one primary staff member responsible when possible.
5. Ask another staff member when you are uncertain.
6. Escalate serious cheating, duplication, coordinate leaks, or active exploits
   immediately.

Do not publicly describe an exploit in enough detail for other players to repeat
it. Preserve the evidence and send it to the appropriate staff channel.

## Staff mode and vanish

The Helper implementation is intended to allow basic staff mode, vanish, report,
freeze, client, inventory-view, and inspection tools. Helper staff mode is also
intended to prevent inventory mutation and hide advanced tools.

Use these tools only for active staff work:

- `/staff` enters or leaves staff mode;
- `/vanish` hides you when an investigation needs silent observation;
- `/freeze <player> <reason>` temporarily restricts a player during an active
  investigation;
- `/invsee` and `/endersee` are for viewing relevant inventories, not casually
  browsing players’ items.

Do not use staff mode to escape combat, locate bases, inspect valuables for
personal reasons, or move staff items into normal play.

## When to ask a Mod or above

Escalate when:

- the evidence is incomplete or conflicting;
- a permanent punishment may be needed;
- the player is suspected of serious cheating or exploiting;
- the situation involves significant item loss, duplication, or economy damage;
- a punishment or staff tool reports an error or partial result;
- the situation involves your friend, guild, or a personal dispute;
- you simply are not sure what the correct decision is.

Send the report, evidence, and a short summary of what you already did. Avoid
making the next staff member restart the investigation from nothing.

## Related pages

- [[Staff Handbook]]
- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Reports and Evidence]]
- [[Punishment System]]
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Privacy and Data Handling]]