# Reports and Evidence

Reports give staff one place to review what a player reported, investigate it and
record the outcome. This page explains staff procedure. For implementation
percentages, source files and remaining development, use
[[Moderation, Punishments, and Reports]].

> **Current deployment:** the repository contains `/report`, the staff report
> inventory workflow and text-based `/reports` fallbacks. The RoseChat
> private-message bridge and production-like multi-server staging remain
> unfinished. Follow only the live-server procedure separately approved for
> deployment.

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

## Staff report interface

Running `/reports` as a player with `enthusiastaff.reports.manage` opens the staff
inventory interface. It provides these queues:

- open reports;
- reports claimed by the viewer;
- all claimed reports;
- reports awaiting review;
- reports closed or marked no-violation during the previous seven days.

Queue reads are asynchronous and database-bounded. The interface displays up to
100 current results across local pages, supports refresh and queue switching, and
fences overlapping loads so a slower old request cannot replace a newer screen.

Selecting a report shows its reason, state, revision, reporter and target IDs,
server/location context, description and retained evidence counts. Raw public
chat, private-message and client-evidence JSON is deliberately not copied into
item lore. Sensitive evidence remains in staff storage for the dedicated evidence
review surfaces and text/technical investigation tools.

State-changing buttons require a private action note and a separate confirmation
screen. After selecting an action, run `/reports note <private action note>` or
`/reports cancel`; Java players receive a clickable command suggestion. This is a
command input rather than public chat, so the workflow remains reliable when a
chat provider cancels or replaces normal chat events. The confirmation uses the
exact revision shown on the detail screen. A concurrent change produces a
stale-revision rejection and the interface reloads the current report instead of
overwriting it. Inventory clicks and drags are cancelled, inventories are bound
to one viewer and permission is checked again on interaction and presentation.

## Text command fallback

The explicit command forms remain available for console operation and for staff
who prefer or require a plain-text workflow, including Bedrock users:

```text
/reports note <private action note>
/reports cancel
/reports <open|mine|claimed|review|closed>
/reports view <report-id>
/reports claim <report-id> <revision> <note>
/reports awaitreview <report-id> <revision> <note> CONFIRM
/reports close <report-id> <revision> <note> CONFIRM
/reports noviolation <report-id> <revision> <note> CONFIRM
```

`note` and `cancel` complete or abandon a GUI action that is already waiting for
input. The remaining explicit forms can be used without opening the GUI.

The report ID and current revision identify the exact state staff reviewed.
State changes use that revision so an older command or screen cannot overwrite
newer work.

The exact uppercase `CONFIRM` is required for close, no-violation and
awaiting-review transitions. Without it, the command displays a review-only
result. Claiming through the direct text command is the exception; the GUI still
uses a review and confirmation screen.

## Practical workflow

1. Open `/reports` and select the relevant queue.
2. Read the report description, location context and retained-evidence counts.
3. Select the action, enter a factual note with `/reports note ...` and review the confirmation screen.
4. Observe the player or inspect the relevant area before confronting them.
5. Compare the claim with server evidence, logs, chat, CoreProtect, screenshots or clips.
6. Freeze only when the player could change evidence or leave the investigation.
7. Use `/punish <player>` when the violation is reasonably proven.
8. Reopen or refresh details when the revision changed.
9. Close or mark no violation with the latest revision and a factual outcome note.

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

Private-message snapshots must not be copied into report Discord payloads or
ordinary inventory lore.

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
