# Reports and Evidence

Reports give staff one place to review what a player reported, investigate it,
and record the outcome.

> **Current status:** `/report` and the text-based `/reports` queues are
> implemented. Submission, merge, state-transition, concurrency, rollback, and
> seven-day evidence-retention behavior have MariaDB coverage. The planned GUI,
> complete RoseChat provider bridge, and production-like multi-server staging
> are not complete, so continue using the approved live process.

## Player reports

Players use:

```text
/report <player> <reason-id> <description>
```

The reported player is not notified and is not told who submitted the report.
A report is a reason to investigate, not proof that the target broke a rule.

The current submission safeguards are:

- two minutes between any two new reports from the same reporter;
- thirty minutes before reporting the same target again;
- no more than five open reports per reporter;
- a same-reporter, same-target, same-reason report within two hours is merged
  into the existing open investigation;
- retries use semantic idempotency checks and cannot reuse a key for different
  report content.

The duplicate merge is evaluated before the ordinary cooldown. This preserves
new evidence without creating a second report or a second creation notification.

## Opening the staff queue

Use:

```text
/reports
/reports <open|mine|claimed|review|closed>
/reports view <report-id>
```

The queue and detail output show the report UUID and current revision. State
changes must use that exact revision so an older staff view cannot overwrite a
newer decision.

Claim a report with:

```text
/reports claim <report-id> <revision> <note>
```

Claim a report before beginning a normal investigation so two staff members do
not unknowingly repeat the same work.

Close a handled report with:

```text
/reports close <report-id> <revision> <note> CONFIRM
```

The exact uppercase `CONFIRM` is required for close, no-violation, and
awaiting-review state changes. Without it, the command displays a review-only
message and does not save the change. Claiming is the exception and does not
require `CONFIRM`.

State changes lock the report row and check the expected revision. Replaying the
same action is safe, while reusing its idempotency key for a different action is
rejected.

## A practical report workflow

1. Read the report and the available context.
2. Claim it with the report ID, current revision, and a short note.
3. Watch the player or inspect the relevant area before confronting them.
4. Compare the report with server evidence, logs, chat, CoreProtect, screenshots,
   or clips.
5. Freeze only when the player could interfere with the investigation or remove
   evidence.
6. Use `/punish <player>` when a violation is reasonably proven.
7. Close the report with the latest revision, a factual note, and `CONFIRM`.

If another staff action changes the report revision, reopen the details and
review the updated state instead of retrying with the stale revision.

## What evidence is useful

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
- another staff member's confirmation for uncertain cases.

### Bugs and exploits

- steps that reproduce the problem;
- affected server, item, command, or feature;
- logs, video, screenshots, or before-and-after state;
- whether the issue is still actively being abused.

Do not spread exploit instructions in public chat or ordinary community
channels. Send enough detail to the staff or development team to reproduce the
issue safely.

## Capture and retention behavior

At submission time, the plugin captures the newest bounded portion of the
previous 15 minutes of public chat. Private-message context is limited to the
reporter and target and is available only when the configured chat provider
delivers its supported callback. Each context is capped at 2,000 messages, and
individual captured message bodies are capped before persistence.

The report may also retain the reporter's world and coordinates, the online
target's coordinates, and a point-in-time client evidence snapshot for the
target. These fields are staff evidence and are not public punishment data.

Public-chat snapshots, private-message snapshots, and report-linked client
evidence expire after seven days. An asynchronous bounded maintenance task runs
after storage startup, normally once per hour. It retries a detected backlog
after one minute and retries cleanup failures after five minutes. Cleanup
failure is logged but does not change moderation authority or report state.
Expired chat and private-message evidence is excluded from reads even before
physical deletion.

## Evidence quality

Ask:

- Does this show the actual violation?
- Is there enough context to avoid a misleading conclusion?
- Can another staff member understand when and where it happened?
- Is the evidence from a trustworthy source?
- Does any automated alert need human confirmation?

A client name, shared network, single alert, rumor, or player accusation is
usually context for investigation rather than proof by itself.

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
reported player. Private-message snapshots are not placed in the report Discord
outbox payload.

## Closing the report

Use **close** when the evidence supports a rule reason and the appropriate staff
action was taken.

Use **no violation** when the report is mistaken, the behavior is allowed, or
the evidence is not enough to prove the claim.

An incorrect report is not automatically report abuse. Punish report abuse only
when there is separate evidence that the player knowingly spammed, fabricated,
or misused the system.

A useful closing note says what staff found, what evidence was reviewed, whether
a violation occurred, what action was taken, and whether another staff member
still needs to follow up. Avoid vague notes such as "handled" when the report may
later be reviewed or appealed.

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
