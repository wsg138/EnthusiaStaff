# Staff Handbook

This page defines the operating rules for every EnthusiaStaff feature. Feature
pages explain individual commands; this page explains how staff should reason
before using them.

> **Deployment warning:** Until [[Implementation Status]] marks a workflow as
> available in production, treat these procedures as training and staging
> guidance rather than permission to replace current production tools.

## Before taking action

Ask five questions:

1. **Do I have the right player?** Verify UUID-backed identity, current name,
   previous names, Java/Bedrock status, and server.
2. **Do I have enough evidence?** Separate what you directly observed from
   reports, automated indicators, assumptions, and alt confidence.
3. **Is this my authority level?** Check [[Rank Authority]] and the exact
   command/action permission.
4. **Is the platform healthy?** A destructive action must not proceed when its
   required database, network, provider, or recovery subsystem is unavailable.
5. **Can the result be explained later?** Record a stable reason, internal
   explanation, evidence, and case links before confirming.

If any answer is uncertain, pause the destructive action and ask a higher rank
or use a non-destructive tool such as spectate, client evidence, history, or
report claiming.

## Evidence quality

Use evidence in this order:

1. Direct staff observation with timestamp and context.
2. Server-captured chat, private-message, client, or transaction evidence.
3. Reproducible logs or provider evidence with stable IDs.
4. Corroborated player reports.
5. Automated detection that is explicitly approved for enforcement.
6. Weak indicators, hearsay, or similarity — useful for investigation, not
   enough by themselves for severe action.

Never turn an alt-confidence score, client brand, same-network observation, or
single anticheat signal into certainty without the policy allowing it.

## Case notes

A useful internal explanation answers:

- What happened?
- Where and when did it happen?
- Which evidence proves it?
- Which rule and exact reason ID apply?
- Why is the selected punishment step appropriate?
- Were there related cases, reports, alts, assets, or appeals?
- Did anything fail, retry, or enter recovery?

Avoid insults, speculation, private jokes, or unsupported conclusions. Internal
does not mean disposable; case notes are durable audit records.

## Public versus private information

Punishments and warnings are public by default unless policy and rank permit a
private case. Public punishment information may include the player, broad
reason, public exact reason, type, dates, state, case ID, and appeal
availability.

Never publish:

- Reporter identity
- Private messages
- Coordinates or base locations
- Raw or recoverable network identity
- Alt evidence details
- Internal notes
- Confiscated contents or balances
- Staff-only automation metadata
- Appeal media or private account information

See [[Privacy and Data Handling]] for handling rules.

## Confirmation discipline

A confirmation prompt is the final safety barrier, not a routine click.

Before confirming:

- Re-read the target name and UUID.
- Re-read the exact reason and ladder step.
- Verify duration, sanction type, visibility, and combined effects.
- Check whether inventory, Ender chest, economy, market, reputation, or network
  actions are actually relevant.
- Confirm the recommendation has not changed.
- Confirm required dependencies are healthy.
- Make sure you are not repeating a request that is already pending.

Do not confirm because a GUI “looks right” after a timeout, reconnect, reload,
or server switch. Resume the durable draft and re-review it.

## Failure behavior

When a command reports stale state, conflict, quarantine, read-only mode,
missing dependency, or uncertain external result:

1. Stop repeating the action.
2. Record the case or operation ID and time.
3. Preserve the current player and server state.
4. Check `/estaff status` and the relevant verify output.
5. Escalate using [[Recovery and Troubleshooting]].

Repeatedly clicking or re-running a destructive command can convert a
recoverable incident into item duplication, item loss, double charging, or
conflicting sanctions.

## Staff visibility and conduct

Vanish is not authorization to browse unrelated private information. Use silent
inventory, container, message, and location tools only for a legitimate staff
purpose tied to a report, case, active investigation, safety concern, or
approved test.

Do not use staff mode to:

- Gain gameplay advantage
- Locate valuables for personal use
- Observe private activity without a staff reason
- Transfer staff items into normal inventory
- Avoid legitimate combat consequences after participating normally
- Conceal staff misconduct

Every sensitive action is expected to be audited.

## Handoffs

When another staff member takes over, provide:

- Case/report/operation ID
- Target UUID and current name
- Current server and state
- What has been observed
- What has already been attempted
- Pending sanctions, locks, drafts, or recovery work
- What must not be repeated
- The exact next safe step

A handoff is incomplete if the next staff member must guess whether an
inventory edit, confiscation, unban, or provider action already happened.
