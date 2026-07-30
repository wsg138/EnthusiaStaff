# Punishment System

This page explains how staff should use the punishment interface. Technical case,
database, and escalation internals are covered in [[Developer Code Guide]].

> **Current status:** Parts of the punishment workflow are implemented and tested,
but the complete GUI, history view, approval flow, integrations, and live staging
remain incomplete. Use the moderation system currently approved for production.

## Main command

Use:

```text
/punish <player>
```

The direct commands `/warn`, `/mute`, `/kick`, `/ban`, and `/ipban` open filtered
versions of the same central workflow. They should not bypass the normal reason,
evidence, history, confirmation, or audit process.

## Standard staff workflow

1. Make sure you are acting on the correct player.
2. Choose the broad rule category.
3. Choose the exact reason that best matches what happened.
4. Read the examples and the result shown by the interface.
5. Add a clear internal note.
6. Attach or reference the useful evidence.
7. Check the public reason for clarity and privacy.
8. Confirm once.

The interface is intended to calculate the configured result from the selected
reason and the player’s relevant punishment history. Staff should not need to
memorize duration ladders or manually count old punishments.

## Choosing the right action

### Warning

Use for minor or first-time behavior where a clear reminder is likely to solve the
problem.

### Mute

Use for continued or serious chat-related behavior, including repeated spam,
toxicity, slurs, sexually inappropriate comments, or ignoring earlier warnings.

### Kick

Use to stop immediate disruption, get a player’s attention, require a reconnect,
or remove an inappropriate skin or username until it is changed. A kick is not a
replacement for the proper punishment if the behavior continues.

### Ban or network action

Use only when the selected reason and evidence justify removing the player from
the server or network. Severe, permanent, IP/network, or unusual actions should
receive the level of review required by the deployed policy.

## Helper approval

The Helper role is being implemented on `section/helper-rank-authority`.

Helpers should use the same `/punish` interface for ordinary moderation. The
current branch requires approval when a Helper’s calculated punishment includes a
permanent sanction.

When approval is required:

1. Finish the evidence and internal note.
2. Submit the request through the normal interface.
3. Tell a Mod or above what needs review.
4. Wait for the decision.
5. Do not use another command to avoid approval.

Helpers should also escalate complicated, severe, or uncertain cases even when the
interface would technically allow an immediate action.

## Evidence and internal notes

A useful note states:

- what the player did;
- when and where it happened;
- which evidence supports the action;
- any important context another staff member should know.

Do not write insults, jokes, guesses presented as facts, or unrelated comments
about the player.

For chat cases, preserve the captured context or a screenshot. Serious cheating,
exploits, theft, duplication, or economy cases may require video, logs,
CoreProtect, inventory information, or another staff witness.

## Public and private information

The player may see the public reason and ordinary punishment details. They should
not receive:

- reporter identity;
- private messages;
- base coordinates;
- internal staff notes;
- alt or network-identity evidence;
- private appeal material;
- confiscated inventory or balance details.

See [[Privacy and Data Handling]].

## Saved drafts

The intended interface saves an unfinished punishment draft so it can be resumed
after closing the GUI, disconnecting, switching servers, or restarting.

Resume with:

```text
/punish resume <player>
```

Always review the draft again. Do not assume an old recommendation is still valid
if new history, evidence, configuration, or staff decisions have changed.

## Correcting or ending a punishment

Use the normal punishment-change commands rather than deleting records or editing
the database:

```text
/removepunishment <player|case> <action> [expiration] <reason> [CONFIRM]
/unban <player|case> <reason> [CONFIRM]
/unmute <player|case> <reason> [CONFIRM]
/removewarning <player|case> <reason> [CONFIRM]
```

Select the exact case when a player has multiple punishments. Explain whether the
change is correcting a mistake, ending a sanction early, responding to new
evidence, or following an approved appeal.

Changing a punishment should preserve the original case and audit history. A full
overturn means the punishment should no longer count as valid history and may
require a separate approval.

## When to stop and ask for help

Do not confirm or repeat the action when:

- you may have selected the wrong player or reason;
- the evidence is incomplete or conflicting;
- the interface says the recommendation changed;
- the draft is stale or another request is already pending;
- approval is required;
- an error suggests the action may have applied only partially;
- the player disconnects during a sensitive asset-related action;
- you are unsure whether repeating the command is safe.

Record the report or case, save the error message, and contact another staff
member or an administrator.

## Related pages

- [[Staff Handbook]]
- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Helper Guide]]
- [[Reports and Evidence]]
- [[Privacy and Data Handling]]
- [[Incident Playbooks]]