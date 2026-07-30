# Reports and Evidence

Reports collect a player allegation, recent context, client information, and
staff handling into one private workflow.

> **Current status:** `/report` and `/reports` are registered and report-domain
> components exist, but the complete queue GUI, cooldown/merge rules, private
> message provider bridge, retention enforcement, and staging verification are
> still partial.

## Player reporting

```text
/report <player|uuid> <reason-id> <description>
```

The intended rules are:

- The target must be known to the network; previously joined offline players
  are allowed.
- A player cannot report themselves.
- Reporter identity is visible only to staff.
- The target is not notified and cannot see internal report status.
- Duplicate and abusive submissions are rate-limited and may be merged.

Default limits in the goals specification:

- Any report: 2-minute cooldown
- Same target: 30-minute cooldown
- Same target and reason: 2-hour cooldown
- Maximum open reports: 5

These limits are target behavior; verify the deployed configuration before
quoting them to players.

## Staff queue

Open:

```text
/reports
```

The planned queue sections are:

- Open
- Claimed by me
- All claimed
- Awaiting review
- Recently closed

Claim a report before taking ownership. A claim prevents duplicated staff work;
it does not prevent emergency intervention by another staff member.

## Detail review

A complete report may include:

- Reporter and target identities
- Reason and description
- Server and world
- Reporter and target coordinates at report time
- Timestamp
- Java or Bedrock platform
- Protocol/version
- Reported client brand
- Floodgate/Geyser state
- AutoClicker handshake/version evidence
- Public chat snapshot
- Relevant private-message snapshot
- Related reports and punishment history

Treat client brand and network/platform metadata as evidence context, not proof
of misconduct by themselves.

## Staff actions

The target workflow provides:

- Claim
- Spectate
- Teleport
- Freeze
- Punish
- Close
- No violation

Recommended order:

1. Read the full captured context.
2. Spectate without alerting the target.
3. Compare the report with direct observation and server evidence.
4. Freeze only when necessary to preserve the investigation.
5. Punish through the central case workflow when proven.
6. Close with a clear internal resolution note.

## Chat and private-message evidence

The target design retains moderation chat and relevant private-message logs for
7 days and captures the previous 15 minutes when a report is created.

Private-message evidence:

- Remains inside the staff system
- Is not copied to public punishment details
- Is not sent to Discord
- Must be limited to what is relevant to the report
- Must not be pasted into ordinary staff channels

If the RoseChat bridge is unavailable, the Wiki must not claim that private
message capture or pre-broadcast handling is working.

## Closing outcomes

Use **violation** when the evidence supports a policy reason and case action.
Use **no violation** when the evidence does not establish a violation, the
behavior is allowed, or the report is mistaken.

Do not punish a reporter merely because a report was incorrect. Report abuse
requires separate evidence that the submission was knowingly malicious,
spammy, or evasive under the configured policy.

## Evidence checklist

Before closing as a violation:

- Target identity verified
- Evidence timestamp and source known
- Context includes preceding and following events when relevant
- Exact reason matches the behavior
- Automated indicators are corroborated as required
- Private information is contained
- Internal note explains the decision
- Linked case/report IDs are correct
