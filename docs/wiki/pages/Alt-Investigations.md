# Alt Investigations

Alt accounts are allowed on the Enthusia Network. Staff investigate related
accounts only when there is a moderation reason, such as punishment evasion,
report abuse or another rule violation.

This page explains staff judgment and privacy. For completion percentages,
commands, source files, encryption boundaries and remaining development, use
[[Staff Tools, Investigations, and Player-State Safety]].

> **Current deployment:** `/alts` and `/alt` are registered on Velocity, but the
> complete confidence lifecycle, exception controls, inheritance, GUI, alerts,
> key rotation and real-data staging remain unfinished. Use only the controls
> actually approved on the live network.

## Quick navigation

- General privacy: [[Privacy and Data Handling]]
- Punishment behavior: [[Punishment System]]
- Reports and evidence: [[Reports and Evidence]]
- Commands and permissions: [[Commands and Permissions]]
- Feature status and source files: [[Staff Tools, Investigations, and Player-State Safety]]

## The main rule

A shared network does not prove that two accounts belong to the same person.
Families, roommates, schools, dorms, workplaces, mobile carriers, public Wi-Fi
and VPN services can all produce legitimate overlap.

Do not punish a player only because:

- two accounts used the same network;
- usernames or skins look similar;
- writing styles seem alike;
- they switched accounts quickly;
- another player claims they are the same person.

These facts may justify investigation, but are not sufficient proof by themselves.

## Privacy boundary

Staff should not see or handle raw IP addresses. Use the approved relationship
summary and protected evidence.

Do not:

- request or send raw addresses;
- paste addresses into notes, Discord or tickets;
- keep a separate spreadsheet of network information;
- reveal network/household evidence to players;
- copy sensitive identity data into public API or website output.

The technical design uses protected equality tokens and versioned encryption.
See [[Staff Tools, Investigations, and Player-State Safety]] for the relevant
source files and remaining key-rotation work.

## Evidence to consider

Useful context may include:

- account-switch timing;
- independent simultaneous play;
- shared or distinct gameplay patterns;
- previously approved household/alt decisions;
- repeated behavior connected to an active sanction;
- reports, chat, transactions and server logs;
- a player openly confirming ownership;
- maintenance/restart/mass-reconnect conditions that explain timing.

Independent simultaneous play is meaningful evidence that accounts may belong to
different people.

## Relationship outcomes

The target model distinguishes:

- `SAME_NETWORK`
- `LOW_CONFIDENCE`
- `SEMI_CONFIDENT`
- `CONFIDENT`
- `VERY_CONFIDENT`
- `CONFIRMED_ALT`
- `APPROVED_ALT`
- `SHARED_HOUSEHOLD`
- `NOT_RELATED`

Uncertain states should create context or alerts, not automatic severe action.
Approved-alt, shared-household and not-related decisions suppress automatic
inheritance unless an authorized staff member reopens the relationship with new
evidence.

## Punishment inheritance

When fully deployed, a sufficiently confident related account may inherit the
**exact remaining time** of an active ban or mute and link back to the original
case. It must not restart the full duration.

Intentional punishment evasion is a separate decision. Do not add an evasion
punishment unless evidence shows the player knowingly used another account to
avoid the active restriction.

## Investigation process

1. Start with the moderation reason for review.
2. Read the relationship summary and evidence.
3. Check for an approved-alt, household or not-related decision.
4. Consider maintenance, restart or mass-reconnect timing.
5. Look for independent simultaneous play and long-term separation.
6. Separate automatic inheritance from intentional evasion.
7. Write a factual note explaining the decision.
8. Ask a Mod/Admin/Founder before making a permanent relationship decision when
   evidence is uncertain.

## Command surface

The target command set includes:

```text
/alts <player>
/alt link
/alt approve
/alt household
/alt notrelated
/alt unlink
/alt reopen
```

Exact syntax, permissions and available actions depend on the deployed version.
See [[Commands and Permissions]] before training staff on a command.

## Stop and ask for help when

- the only evidence is network overlap;
- a real household may be involved;
- accounts play independently at the same time;
- a permanent relationship decision is required;
- inheritance does not match the original sanction's remaining state;
- maintenance/restart events may explain the evidence;
- raw network information appears where staff should not see it;
- a command reports conflict, stale state or recovery.

## Related pages

- [[Staff Handbook]]
- [[Reports and Evidence]]
- [[Punishment System]]
- [[Privacy and Data Handling]]
- [[Commands and Permissions]]
- [[Staff Tools, Investigations, and Player-State Safety]]
