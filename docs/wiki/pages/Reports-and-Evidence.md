# Reports and Evidence

Reports give staff one place to review what a player reported, investigate it and
record the outcome. A report is an investigation lead, not proof by itself. For
implementation status and source navigation, see [[Moderation, Punishments, and Reports]].

> **Current deployment boundary:** the repository contains `/report`, the staff
> report inventory workflow, revision-safe state changes, retained provider-independent
> evidence and text fallbacks. RoseChat private-message capture remains a later
> integration package, Discord delivery remains separate, and production-like
> multi-server acceptance is still deferred to staging.

## Quick navigation

- Ordinary staff workflow: [[Staff Quick Start|Moderator-Quick-Start]]
- Punishing from a report: [[Punishment System]]
- Privacy rules: [[Privacy and Data Handling]]
- Incident response: [[Incident Playbooks]]
- Feature status and source files: [[Moderation, Punishments, and Reports]]
- Technical trace: [[Developer Code Guide]]

## Player report command

```text
/report <player|uuid> <reason-id> <description>
```

The target is not notified and is not told who submitted the report. The target
may be offline as long as it exists in the authoritative player directory.
Self-reports and reports submitted while a report-restriction sanction is active
are rejected.

Current safeguards include:

- two minutes between ordinary new reports from one reporter;
- thirty minutes before reporting the same target again;
- no more than five open reports per reporter;
- same-reporter, same-target, same-reason reports within two hours merge into the
  existing open investigation;
- idempotency keys cannot be reused for different report content;
- the durable store serializes competing submissions for one reporter and keeps
  report/evidence/outbox/audit writes in one transaction.

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
inventory lore.

State-changing buttons require a private action note and a separate confirmation
screen. After selecting an action, run `/reports note <private action note>` or
`/reports cancel`; Java players receive a clickable command suggestion. This is
command input rather than public chat, so it remains usable when a chat provider
cancels or replaces normal chat events and works as a Bedrock-safe text path.

The confirmation uses the exact revision shown on the detail screen. A concurrent
change produces a stale-revision rejection and the interface reloads the current
report instead of overwriting newer work. Inventory clicks and drags are
cancelled, inventories are bound to one viewer and permission is checked again on
interaction and presentation.

## Text and Bedrock fallback

The explicit command forms remain available for console operation and for staff
who prefer or require a plain-text workflow:

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

`note` and `cancel` complete or abandon a GUI action already waiting for input.
The remaining forms can be used without opening the GUI. State changes use the
exact report revision so an older command or screen cannot overwrite newer work.

The exact uppercase `CONFIRM` is required for close, no-violation and
awaiting-review transitions. Without it, the command displays a review-only
result. Claiming through the direct text command is the exception; the GUI still
uses a review and confirmation screen.

## Sensitive evidence review

Ordinary triage and sensitive evidence access are separate permissions:

- `enthusiastaff.reports.manage` — list, view, claim and resolve reports;
- `enthusiastaff.reports.evidence` — inspect retained evidence contents and exact
  coordinates through the text evidence surface.

The built-in rank tree grants ordinary report management to helper-level staff,
while the sensitive evidence permission starts at moderator and developer roles
and is inherited by higher moderator ranks.

Use:

```text
/reports evidence <report-id> <public|private|client> [snapshot] [page]
```

Evidence output is deliberately bounded. Public and private chat snapshots are
rendered as individual messages in small pages instead of dumping stored JSON.
Client evidence uses an allow-list of operational fields such as platform,
protocol/version, brand and integration availability. Opaque Polar metadata is
not copied into in-game chat. If a retained record has an unexpected format, the
command fails closed and directs staff to approved database recovery tooling
instead of echoing raw data.

The command never makes private evidence public; output is sent only to the
invoking staff command sender after the sensitive permission check. Do not paste
its output into public chat, public Discord channels, tickets or screenshots that
players can access.

## Practical workflow

1. Open `/reports` and select the relevant queue.
2. Read the report description and retained-evidence counts.
3. If the case requires stored evidence, use `/reports evidence ...` only when
   your role has the sensitive permission.
4. Observe the player or inspect the relevant area before confronting them.
5. Compare the claim with server evidence, logs, chat, CoreProtect, screenshots or clips.
6. Freeze only when the player could change evidence or leave the investigation.
7. Use `/punish <player>` when the violation is reasonably proven.
8. Reopen or refresh details when the revision changed.
9. Close or mark no violation with the latest revision and a factual outcome note.

A stale revision should be reviewed again, not retried blindly.

## Captured context and retention

At submission, the current implementation can capture a bounded portion of the
previous 15 minutes of public chat. Private-message context is limited to the
reporter/target pair and remains empty unless a supported provider supplies those
callbacks; the RoseChat provider bridge itself belongs to the later integration
package.

Current boundaries include:

- at most 2,000 messages in each captured context;
- individual message bodies capped at 1,000 characters before persistence;
- reporter world/coordinates and online target coordinates may be retained;
- a point-in-time target client snapshot may be linked to the report;
- public-chat, private-message and report-linked client snapshots expire after
  seven days under the default policy;
- expired evidence is excluded from reads before physical cleanup completes;
- cleanup is bounded and asynchronous after storage startup; failure is logged
  and retried without changing moderation authority or report state.

Evidence, state and revisions are database-backed and survive runtime restart.
Duplicate/replay and state-change idempotency are also durable rather than
process-local.

## Attachment decision

**Direct file/screenshot attachments are intentionally unsupported by the
provider-independent report store in ES-P05.** No arbitrary file path, URL,
binary blob or remote-download behavior is accepted by `/report` or `/reports`.
This is a deliberate privacy and abuse boundary, not an unfinished hidden upload
endpoint.

Use the server's separately approved staff evidence system for screenshots or
video and reference that material in the private staff workflow. Adding first-class
attachments later requires a separate design with authenticated storage,
content/size/type limits, malware handling, retention/purge rules, audit access
and explicit authorization. Do not add ad-hoc URLs or local file paths to report
notes as a substitute.

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
ordinary inventory lore. Sensitive evidence text should only be opened when it is
needed for the investigation and the staff member has the evidence permission.

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
