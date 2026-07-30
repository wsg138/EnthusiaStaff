# Reports and Evidence

Reports give staff one place to review what a player reported, investigate it, and
record the outcome.

> **Current status:** `/report` and `/reports` are registered, but the complete
> queue interface, cooldowns, private-message integration, retention, and staging
> behavior are still incomplete. Continue using the approved live process.

## Player reports

Players use:

```text
/report <player> <reason> <description>
```

The reported player should not be told who submitted the report. A report is a
reason to investigate, not proof that the target broke a rule.

## Opening the staff queue

Use:

```text
/reports
```

Claim a report before beginning a normal investigation so two staff members do not
unknowingly repeat the same work. Another staff member may still step in when
something urgent is happening.

## A practical report workflow

1. Read the report and the available context.
2. Claim it when you are taking responsibility.
3. Watch the player or inspect the relevant area before confronting them.
4. Compare the report with server evidence, logs, chat, CoreProtect, screenshots,
   or clips.
5. Freeze only when the player could interfere with the investigation or remove
   evidence.
6. Use `/punish <player>` when a violation is reasonably proven.
7. Close the report with a short factual outcome.

## What evidence is useful

Useful evidence depends on the situation:

### Chat behavior

- captured chat context;
- screenshots showing the full exchange;
- relevant private-message evidence kept inside the staff system.

### Griefing, stealing, or base incidents

- CoreProtect results;
- location and time;
- screenshots or video;
- inventory or transaction information when relevant.

### Cheating

- direct observation;
- video or repeatable behavior;
- approved anticheat or client evidence;
- another staff member’s confirmation for uncertain cases.

### Bugs and exploits

- steps that reproduce the problem;
- affected server, item, command, or feature;
- logs, video, screenshots, or before-and-after state;
- whether the issue is still actively being abused.

Do not spread exploit instructions in public chat or ordinary community channels.
Send enough detail to the staff or development team to reproduce it safely.

## Evidence quality

Ask:

- Does this show the actual violation?
- Is there enough context to avoid a misleading conclusion?
- Can another staff member understand when and where it happened?
- Is the evidence from a trustworthy source?
- Does any automated alert need human confirmation?

A client name, shared network, single alert, rumor, or player accusation is usually
context for investigation rather than proof by itself.

## Private information

Keep the following inside the staff system:

- reporter identity;
- private messages;
- base coordinates;
- internal staff notes;
- alt or network information;
- private appeal evidence;
- inventory, balance, or confiscation details that are not part of the public
  reason.

Do not paste private evidence into public Discord channels or send it to the
reported player.

## Closing the report

Use **violation** when the evidence supports a rule reason and the appropriate
staff action was taken.

Use **no violation** when the report is mistaken, the behavior is allowed, or the
evidence is not enough to prove the claim.

An incorrect report is not automatically report abuse. Punish report abuse only
when there is separate evidence that the player knowingly spammed, fabricated, or
misused the system.

## Writing the outcome note

A useful closing note says:

- what staff found;
- what evidence was reviewed;
- whether a violation occurred;
- what action was taken;
- whether another staff member or developer still needs to follow up.

Avoid vague notes such as “handled” when the report may later be reviewed or
appealed.

## When to ask for help

Escalate when:

- cheating evidence is uncertain;
- a serious exploit or duplication issue may be active;
- large item or economy losses are involved;
- the case involves a friend, guild member, or personal conflict;
- a punishment requires approval;
- the report or evidence system appears to have failed;
- you are unsure whether the available evidence is enough.

## Related pages

- [[Staff Handbook]]
- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Helper Guide]]
- [[Punishment System]]
- [[Incident Playbooks]]
- [[Privacy and Data Handling]]