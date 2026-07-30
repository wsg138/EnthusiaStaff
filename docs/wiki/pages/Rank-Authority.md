# Roles and Permissions

This page is an administrator and developer reference. Ordinary staff should use
[[Staff Handbook]], [[Staff Quick Start|Moderator-Quick-Start]], and the guide for
their role rather than memorizing permission matrices.

Permissions control which commands appear, but important moderation decisions are
also checked by the plugin’s authorization policy. Accidentally granting one node
should not silently give a role every higher-rank capability.

> **Helper status:** Helper support is being developed on
> `section/helper-rank-authority`. The exact live permissions remain subject to
> merge, review, configuration, and deployment.

## Helper

Helpers are intended to handle ordinary moderation and basic investigations.
The active implementation branch grants the basic punishment interface, reports,
alerts, freeze, staff mode, vanish, staff chat, client information, inventory
viewing, and inspection.

Important limits in that branch:

- Helper staff mode blocks inventory mutation.
- Helpers do not receive advanced staff tools.
- Permanent punishments require approval from a Mod or above.
- Helpers should escalate severe, complex, or uncertain cases.

See [[Helper Guide]] for the staff-facing version.

## Mod

Mods inherit the Helper toolset and add broader punishment and investigation
capabilities. The current design allows configured punishment actions, punishment
changes that preserve history, inventory editing, and configured confiscation
workflows.

Mods should still follow the interface and policy rather than creating arbitrary
sanction combinations or custom durations.

## Developer

Developer is a technical role, not a normal moderation promotion path.

Developers may receive diagnostics, reload, inspection, staff mode, vanish,
client, and inventory tools for legitimate development or testing. Punishment
mutation must remain blocked even if a command, GUI, website action, or permission
is accidentally exposed.

The Helper branch permits Developers to submit a policy-sanction request for
review, but not to approve or directly issue punishment mutations.

## Admin

Admins can manage more advanced punishment decisions, diagnostics, configuration,
and recovery-related work. This includes configured custom durations, raising or
lowering recommendations where policy allows, approving overturns, and selected
market or reputation restrictions.

## Founder

Founder has the broadest policy and recovery authority, including owner-only
recovery, migration/cutover decisions, exceptional sanction combinations, and
confiscated-asset restoration.

Founder access should still use the same audited workflows rather than raw
storage edits.

## Permission aggregates

The current Helper branch defines these Paper aggregates:

```text
enthusiastaff.rank.helper
enthusiastaff.rank.mod
enthusiastaff.rank.developer
enthusiastaff.rank.admin
enthusiastaff.rank.founder
```

`enthusiastaff.rank.mod` inherits the Helper aggregate. Admin inherits Mod, and
Founder inherits Admin. Developer remains a separate technical branch rather than
sitting between Mod and Admin.

See [[Commands and Permissions]] for individual nodes and current registration.
Always compare documentation with the deployed `plugin.yml`, LuckPerms groups,
and authorization tests.