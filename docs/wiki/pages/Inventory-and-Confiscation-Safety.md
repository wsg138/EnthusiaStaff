# Inventory and Confiscation Safety

Inventory viewing, editing, confiscation, and restoration can cause item loss or
duplication when used incorrectly. This page explains what staff should do. The
technical transaction and recovery design is covered in [[Developer Code Guide]].

> **Current status:** Important database safety paths have automated tests, but
> live multi-server behavior, concurrent viewers, provider integration, and
> recovery still require staging. Do not assume a command is production-ready
> only because it opens.

## Choose the correct tool

| Need | Correct approach |
| --- | --- |
| Check a player inventory | `/invsee <player>` |
| Check an Ender chest | `/endersee <player>` |
| Make an ordinary authorized correction | Use the inventory editor with edit permission |
| Remove prohibited items or case evidence | Use the case-linked confiscation workflow |
| Remove currency | Use the economy confiscation workflow |
| Return confiscated items | Use the approved case restoration workflow |
| Fix an uncertain or failed operation | Stop and contact an Admin or Founder |

Viewing an inventory does not give permission to edit it.

## Before opening an inventory

Make sure:

- there is a legitimate report, case, or staff reason;
- you are looking at the correct player;
- another staff member is not already handling the same inventory;
- you know whether you only need to view or actually need to change something;
- the player is not being moved between servers or logging in and out repeatedly.

Do not browse inventories out of curiosity or use staff information for normal
gameplay.

## Helper limits

The upcoming Helper role is intended to allow inventory and Ender chest viewing
for investigations, but not inventory mutation in Helper staff mode.

Helpers should ask a Mod or above when an item needs to be removed, restored, or
otherwise changed. Do not try to work around the limit by moving items through
another container or command.

## Viewing and ordinary editing

Use ordinary editing only for a clear authorized correction. Examples may include
fixing a staff-caused mistake or completing an approved support action.

Before changing anything:

1. Tell the staff team what you are changing and why.
2. Take a screenshot or save the relevant before state when practical.
3. Change only the intended slots or items.
4. Recheck the inventory after the edit.
5. Leave a note in the related report or case.

Do not close and reopen the editor repeatedly when changes are not appearing. Stop
and ask for help because another edit or recovery process may already be active.

## Online and offline players

An online player can change their inventory while staff are viewing it. Avoid
making broad edits while the player is actively moving items, changing servers,
or using containers.

Offline editing has additional risks because player data may be saved or loaded by
a server at the same time. Use only the supported workflow. Do not manually edit
player files or reconstruct an inventory from a screenshot.

If a player disconnects during an edit or confiscation, stop and let the approved
recovery process handle it.

## Shulkers, bundles, and nested items

Items inside shulkers and bundles can change after staff select them. Reopen and
recheck the container before confirming a removal.

Do not assume two visually identical stacks are the same selected item. Use the
workflow’s exact selection and preview.

## Item confiscation

Use confiscation when items are evidence, prohibited assets, duplicated items, or
must be removed as part of a case.

Do not manually delete the items through ordinary inventory editing and call that
confiscation. The proper workflow is intended to:

- link the removal to a case;
- save what was removed;
- record who removed it and why;
- prevent the same removal from happening twice;
- allow an authorized restoration later when appropriate.

Before confirming, check the selected items and quantity carefully. Afterward,
confirm that the player no longer has the items and that the case shows the
confiscation.

## Economy confiscation

Currency removal must use the EnthusiaCurrency moderation workflow. Do not run raw
database updates or make a second manual withdrawal when the result is uncertain.

Confirm:

- the amount and reason;
- which forms of currency are included;
- the case that authorizes the action;
- the final amount shown after the operation.

If the balance does not match the expected result, stop and escalate. Do not try to
“balance it out” with another withdrawal or deposit.

## Restoration

Restoration returns items that were saved by a previous case-linked confiscation.
The current permission design reserves this for Founder/owner recovery.

Restoration must use the original case. Never duplicate the saved items manually
or return items from memory.

## When to stop immediately

Stop the action and contact an Admin or Founder when:

- the wrong player or inventory may be open;
- an item moved or changed after selection;
- the player disconnected or switched servers;
- another viewer or operation appears to be active;
- the command reports a conflict, pending operation, or recovery state;
- items or currency appear to have been removed twice;
- the final inventory or balance cannot be confirmed;
- you are unsure whether repeating the command is safe.

Record the report or case, the time, what you selected, what the player had before,
and what you can see now. Do not delete database rows, edit recovery state, or
release locks manually.

## Related pages

- [[Staff Handbook]]
- [[Helper Guide]]
- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Incident Playbooks]]
- [[Recovery and Troubleshooting]]