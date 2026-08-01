# Reports and Evidence

Reports give staff one place to review what a player reported, investigate it and
record the outcome. This page explains staff procedure. For implementation
percentages, source files and remaining development, use
[[Moderation, Punishments, and Reports]].

> **Current deployment:** `/report` and text-based `/reports` queues exist in the
> current implementation, but the complete GUI, RoseChat private-message bridge
> and production-like multi-server staging are unfinished. Follow the live-server
> procedure actually approved for deployment.

## Quick navigation

- Ordinary staff workflow: [[Staff Quick Start|Moderator-Quick-Start]]
- Punishing from a report: [[Punishment System]]
- Privacy rules: [[Privacy and Data Handling]]
- Incident response: [[Incident Playbooks]]
- Feature status and source files: [[Moderation, Punishments, and Reports]]
- Technical trace: [[Developer Code Guide]]

## Player report command

```text
/report <player> <reason-id> <description>
```

The target is not notified and is not told who submitted the report. A report is
a reason to investigate, not proof that a violation occurred.

Current safeguards include:

- two minutes between ordinary new reports from one reporter;
- thirty minutes before reporting the same target again;
- no more than five open reports per reporter;
- same-reporter, same-target, same-reason reports within two hours merge into the
  existing open investigation;
- idempotency keys cannot be reused for different report content.

A valid duplicate merge is considered before ordinary cooldown rejection so new
evidence can be preserved without creating a second report/notification.

## Staff queue commands

```text
/reports
/reports <open|mine|claimed|review|closed>
/reports view <report-id>
/reports claim <report-id> <revision> <note>
/reports close <report-id> <revision> <note> CONFIRM
```

The report ID and current revision identify the exact state staff reviewed.
State changes use that revision so an older screen cannot overwrite newer work.

The exact uppercase `CONFIRM` is required for close, no-violation and
awaiting-review transitions. Without it, the command displays a review-only
result. Claiming is the exception.

## Practical workflow

1. Read the report and captured context.
2. Claim it with the current revision and a short note.
3. Observe the player or inspect the relevant area before confronting them.
4. Compare the claim with server evidence, logs, chat, CoreProtect, screenshots or clips.
5. Freeze only when the player could change evidence or leave the investigation.
6. Use `/punish <player>` when the violation is reasonably proven.
7. Reopen details if the revision changed.
8. Close with the latest revision, factual outcome note and `CONFIRM`.

A stale revision should be reviewed again, not retried blindly.

## Evidence by incident type

### Chat behavior

- bounded captured chat context;
- screenshots showing the complete exchange;
- relevant private-message evidence kept inside the staff system.

### Griefing, theft or base incidents

- CoreProtect results;
- location and time;
- screenshots or video;
- inventory/transaction context when relevant.

### Cheating

- direct observation;
- video or repeatable behavior;
- supported anticheat/client evidence;
- another staff member's confirmation when uncertain.

### Bugs and exploits

- safe reproduction steps;
- affected server, item, command or feature;
- logs, video, screenshots or before/after state;
- whether abuse is still active.

Do not publish enough exploit detail for other players to reproduce it.

## Captured context and retention

At submission, the current implementation can capture a bounded portion of the
previous 15 minutes of public chat. Private-message context is limited to the
reporter and target and is available only when a supported chat provider supplies
the callback.

Current boundaries include:

- at most 2,000 messages in each captured context;
- individual bodies capped before persistence;
- reporter world/coordinates and online target coordinates may be retained;
- point-in-time client evidence may be linked to the report;
- public-chat, private-message and report-linked client snapshots expire after
  seven days;
- expired evidence is excluded from reads before physical cleanup completes.

A bounded asynchronous maintenance task runs after storage startup. Cleanup
failure is logged and retried; it does not change report state or moderation
authority.

## Evidence quality checklist

Ask:

- Does this show the actual violation?
- Is there enough context to avoid a misleading conclusion?
- Can another staff member understand when and where it occurred?
- Is the source trustworthy?
- Does an automated alert still need human confirmation?

A client name, shared network, single alert, rumor or accusation is normally
investigation context rather than proof by itself.

## Private information

Keep these inside the staff system:

- reporter identity;
- private messages;
- base coordinates;
- internal notes;
- alt/network information;
- private appeal evidence;
- inventory/balance/confiscation details not required by the public reason.

Private-message snapshots must not be copied into report Discord payloads.

## Closing outcomes

Use **close** when the evidence supports a rule reason and the appropriate action
was taken.

Use **no violation** when the behavior is allowed, the report is mistaken or the
evidence is insufficient.

An incorrect report is not automatically report abuse. Punish abuse only when
separate evidence shows deliberate spam, fabrication or misuse.

A good closing note states what staff found, which evidence was reviewed, whether
a violation occurred, what action was taken and whether follow-up remains.

## Stop and ask for help when

- cheating evidence is uncertain;
- an active exploit or duplication issue may exist;
- significant item/economy losses are involved;
- the case involves a friend, guild member or personal conflict;
- the punishment requires approval;
- the report/evidence system reports failure, conflict or recovery state;
- you are unsure whether the evidence is enough.

## Related pages

- [[Staff Handbook]]
- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Helper Guide]]
- [[Punishment System]]
- [[Incident Playbooks]]
- [[Privacy and Data Handling]]
- [[Moderation, Punishments, and Reports]]
