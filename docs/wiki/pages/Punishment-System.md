# Punishment System

This page explains the **staff procedure** for creating and correcting
punishments. For completion percentages, technical behavior and direct source
links, use [[Moderation, Punishments, and Reports]].

> **Current deployment:** Continue using the moderation workflow currently
> approved for the live server. A command or GUI existing in source does not mean
> the complete workflow is production-ready.

## Quick navigation

- Ordinary staff workflow: [[Staff Quick Start|Moderator-Quick-Start]]
- Helper authority and approval: [[Helper Guide]]
- Reports and evidence: [[Reports and Evidence]]
- Rank boundaries: [[Roles and Permissions|Rank-Authority]]
- Commands and nodes: [[Commands and Permissions]]
- Feature status and files: [[Moderation, Punishments, and Reports]]
- Source trace: [[Developer Code Guide]]

## Main commands

```text
/punish <player>
/punish resume <player>
/ban <player> [reason-id]
/mute <player> [reason-id]
/warn <player> [reason-id]
/kick <player> [reason-id]
/ipban <player> [reason-id]
```

The filtered commands should enter the same central reason, history,
authorization, evidence, confirmation and audit workflow. They should not bypass
policy.

## Standard workflow

1. **Confirm the target.** Check current name, UUID/offline match and any Bedrock
   alias shown by the interface.
2. **Choose the broad category.** Do not force unrelated behavior into the wrong
   category simply because the result looks stronger.
3. **Choose the exact stable reason.** Read the examples and related history.
4. **Review the calculated result.** Check sanction type, duration, public reason,
   contributing history and whether approval is required.
5. **Add a factual internal note.** Explain what happened, when/where it happened
   and which evidence supports the action.
6. **Attach or reference evidence.** Use the linked report, captured context,
   screenshot, video, logs or other approved source.
7. **Check privacy.** Reporter identity, private messages, coordinates, internal
   notes, alt/network data and confiscation details must remain private.
8. **Confirm once.** Do not repeatedly click or rerun after an error, conflict or
   pending result.

The interface is intended to calculate the configured result from the selected
reason and relevant history. Staff should not need to memorize duration ladders.

## Choosing the action

### Warning

Use for minor or first-time behavior where a clear reminder is likely to solve the
problem.

### Mute

Use for continued or serious chat behavior, such as repeated spam, toxicity,
slurs, sexually inappropriate comments or ignoring earlier warnings.

### Kick

Use to stop immediate disruption, require a reconnect, get a player's attention
or remove an inappropriate skin/username until it is changed. Continued behavior
may still require a normal punishment.

### Ban or network action

Use only when the selected reason and evidence justify removing the player from
the server/network. Permanent, IP/network or unusual actions require the level of
review configured for the actor's rank.

## Approval requests

Some results do not apply immediately:

- Helper results containing a permanent sanction become approval requests.
- Developer may prepare a request but cannot directly punish or approve.
- Self-approval and unauthorized approval must remain blocked.

When review is required:

1. Finish the note and evidence.
2. Submit through the normal interface.
3. Tell an eligible reviewer what needs attention.
4. Wait for the recorded decision.
5. Do not use another command to work around approval.

The detailed request lifecycle, active development and source files are listed in
[[Moderation, Punishments, and Reports]].

## Durable drafts

An unfinished punishment may be saved and resumed:

```text
/punish resume <player>
```

Review the draft again before confirming. History, evidence, configuration,
authority or another staff decision may have changed since the draft was created.
A stale recommendation should be recalculated rather than trusted blindly.

## Evidence and notes

A useful note states:

- what the player did;
- when and where it happened;
- which evidence supports the decision;
- any context another reviewer or appeal handler needs.

Do not include insults, jokes, rumors or guesses presented as fact.

## Public and private information

Players may receive the public reason and ordinary sanction details. They should
not receive:

- reporter identity;
- private messages;
- base coordinates;
- staff notes or internal discussion;
- alt/network-identity evidence;
- private appeal material;
- confiscated item/balance details;
- sensitive client or automation metadata.

See [[Privacy and Data Handling]].

## Correcting or ending a punishment

Use the approved mutation commands rather than deleting rows or editing the
database:

```text
/removepunishment <player|case> <action> [expiration] <reason> [CONFIRM]
/unban <player|case> <reason> [CONFIRM]
/unmute <player|case> <reason> [CONFIRM]
/removewarning <player|case> <reason> [CONFIRM]
/unwarn <player|case> <reason> [CONFIRM]
```

Select the exact case/sanction when multiple actions exist. A change should
preserve the original record and explain whether it is a reduction, early ending,
revocation, correction, appeal result or full overturn.

Use `/history <player|uuid> [page]` for the newest-first moderation timeline and
`/case [view] <case-id>` for complete case, sanction, request, appeal and mutation
detail. Exact sanction changes use `/estaff sanction reduce|end|revoke|overturn`
and always preserve the original decision and append audit history. See
[[Moderation, Punishments, and Reports]] for source files and remaining staging
work.

## Stop and ask for help when

- the wrong player or reason may be selected;
- evidence is incomplete or conflicting;
- the recommendation changed or the draft is stale;
- another request is already pending;
- approval is required;
- an error suggests partial application or recovery state;
- a player disconnects during a related asset operation;
- you are unsure whether retrying is safe.

Record the report/case, time, exact error and visible result. Do not delete
storage rows or repeat a destructive action to “see whether it works.”

## Related pages

- [[Staff Handbook]]
- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Helper Guide]]
- [[Reports and Evidence]]
- [[Privacy and Data Handling]]
- [[Incident Playbooks]]
- [[Moderation, Punishments, and Reports]]
- [[Commands and Permissions]]
