# Helper Guide

Helpers are the first level of moderation. Their main job is to handle ordinary
issues, gather useful evidence and escalate anything severe, unclear or outside
their authority.

> **Deployment note:** Follow the permissions and procedures actually approved on
> the live server. Current design and implementation status are documented in
> [[Moderation, Punishments, and Reports]] and
> [[Staff Tools, Investigations, and Player-State Safety]].

## Quick navigation

- General expectations: [[Staff Handbook]]
- Ordinary workflow: [[Staff Quick Start|Moderator-Quick-Start]]
- Punishments and approval requests: [[Punishment System]]
- Reports and evidence: [[Reports and Evidence]]
- Staff mode, vanish and freeze: [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- Privacy rules: [[Privacy and Data Handling]]
- Exact role boundaries: [[Roles and Permissions|Rank-Authority]]

## What Helpers should focus on

- Help players and answer straightforward questions.
- Handle ordinary chat issues and minor disruption.
- Gather evidence for reports, cheating concerns, bugs and exploits.
- Use warnings, mutes and kicks only when they clearly fit the situation.
- Submit an approval request when the calculated result requires higher authority.
- Ask a Mod or above to take over complicated, severe or uncertain cases.
- Keep reports, private evidence and staff discussion internal.

Asking before acting is a good staff habit. Helpers are not expected to know every
rule, edge case or technical system immediately.

## General expectations

- Treat players respectfully, including when they are upset with you.
- Use common sense and avoid turning small issues into arguments.
- Be fair when friends, enemies or guild members are involved.
- Never use staff permissions for personal benefit.
- Do not share staff-chat screenshots, reports, private messages or base coordinates.
- Raise staff-conduct concerns privately with a Mod, Admin or Founder.
- Step away or hand off a hostile conversation rather than arguing publicly.

## Choosing an ordinary action

### Warning

Use a warning for minor or first-time behavior the player should be able to stop
after a clear reminder.

Examples include light spam, minor chat misconduct or a straightforward rule
reminder. State what needs to stop rather than writing a vague message such as
“behave.”

### Mute

Use a mute for continued or serious chat disruption, including repeated spam,
excessive toxicity, slurs, sexually inappropriate comments or ignoring earlier
warnings. Save the relevant captured context or screenshot.

### Kick

Use a kick to stop immediate disruption, get a player's attention, require a
reconnect or remove an inappropriate username/skin until it is changed. A kick is
not a substitute for the proper punishment if the behavior continues.

## Punishment interface and approval

Use:

```text
/punish <player>
```

1. Confirm the correct target.
2. Choose the closest matching category and exact reason.
3. Read the calculated result and relevant history.
4. Add a factual internal note and evidence reference.
5. Confirm once or submit the requested approval.

When approval is required, send the request and wait for a Mod or above. Do not
use a different command to bypass review. Also escalate voluntarily when the
evidence is uncertain or the situation is unusually serious.

See [[Punishment System]] for the complete staff workflow and
[[Moderation, Punishments, and Reports]] for implementation status and source
files.

## Reports, cheating, bugs and exploits

1. Gather facts before deciding what happened.
2. Watch the player or reproduce the issue when safe.
3. Use CoreProtect, logs, screenshots, clips or supported evidence.
4. Keep one primary staff member responsible when possible.
5. Escalate serious cheating, duplication, coordinate leaks or active exploits.
6. Record what you found so the next staff member does not restart from nothing.

Do not publicly describe an exploit in enough detail for others to reproduce it.

## Staff mode and vanish

Use staff tools only for active staff work:

- `/staff` enters or leaves staff mode;
- `/vanish` hides you when visible observation would interfere;
- `/freeze <player> <reason>` temporarily restricts a player during an active investigation;
- `/invsee` and `/endersee` are for relevant viewing, not casual browsing.

Helper staff mode is intended to remain restricted: no creative advantage, no
advanced recovery tools and no unauthorized player-inventory mutation.

Do not use staff mode to escape combat, locate bases, inspect valuables for
personal reasons or move staff items into normal play.

See [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]] for procedure
and [[Staff Tools, Investigations, and Player-State Safety]] for implementation
status and source files.

## When to ask a Mod or above

Escalate when:

- evidence is incomplete or conflicting;
- a permanent or unusually severe punishment may be needed;
- serious cheating or exploiting is suspected;
- significant item loss, duplication or economy damage is involved;
- a punishment or staff tool reports an error, conflict or partial result;
- the situation involves your friend, guild or personal dispute;
- you are unsure what the correct decision is.

Send the report/case, evidence, what you observed, what you already did and what
still needs a decision.

## Related pages

- [[Staff Handbook]]
- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Punishment System]]
- [[Reports and Evidence]]
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Roles and Permissions|Rank-Authority]]
- [[Moderation, Punishments, and Reports]]
- [[Staff Tools, Investigations, and Player-State Safety]]
