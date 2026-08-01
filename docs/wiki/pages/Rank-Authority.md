# Roles and Permissions

This page explains the intended authority model behind the rank aggregates.
Ordinary staff should use [[Staff Handbook]],
[[Staff Quick Start|Moderator-Quick-Start]] and their focused procedure pages.

Permission nodes control command discovery and early denial. Important writes are
also reauthorized inside central services, so accidentally granting one node must
not silently turn a role into a higher moderation rank.

## Quick navigation

- Exact permission nodes and command registration: [[Commands and Permissions]]
- Punishment/request behavior and source files: [[Moderation, Punishments, and Reports]]
- Staff-tool restrictions and source files: [[Staff Tools, Investigations, and Player-State Safety]]
- Helper staff guide: [[Helper Guide]]
- Authoritative policy source:
  [`plugin.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/plugin.yml)
  plus central authorization tests/services

## Rank overview

| Role | Normal purpose | Direct punishment authority | Approval authority | Asset/staff-tool authority |
| --- | --- | --- | --- | --- |
| Helper | Trial moderation and ordinary investigations | Configured temporary outcomes; permanent outcomes become requests | None | Restricted staff mode, freeze, vanish, reports, read-only inventory/Ender and inspection |
| Mod | Full ordinary moderation | Configured outcomes, including configured permanent steps | May review eligible Helper/Developer requests | Inventory editing and configured confiscation; no owner recovery |
| Developer | Technical work and diagnostics | Request preparation only; no direct mutation | None | Technical diagnostics and selected investigation tools; still no punishment mutation |
| Admin | Advanced moderation, configuration and review | Configured outcomes plus authorized custom durations/raising | May review requests and overturns as configured | Advanced tools and selected provider restrictions |
| Founder | Owner-level policy, recovery and release authority | Broadest configured/custom authority | Final approval and exceptional recovery | Owner recovery, confiscated-item restoration, migration/cutover authority |

The table summarizes intended policy. The deployed version, LuckPerms groups and
central service tests determine actual live authority.

## Helper

Helpers handle ordinary moderation and basic investigations.

Current aggregate permissions include status/verification, punishment/read access,
configured punishment workflow, reports, alerts, freeze, staff mode, vanish,
staff chat, inventory view and inspection.

Important policy limits:

- configured temporary punishments may apply directly;
- a result containing a permanent sanction becomes an approval request;
- Helper cannot approve requests or overturns;
- Helper staff mode must block inventory mutation and advanced recovery tools;
- severe, complex or uncertain cases should be escalated even when a command is visible.

See [[Helper Guide]].

## Mod

Mods inherit the Helper toolset and add request review, network-ban permission,
selected sanction changes, inventory editing and configured confiscation.

Mods may:

- apply configured punishment steps;
- approve/deny eligible Helper or Developer requests;
- lower or end/revoke sanctions where policy allows;
- request a full overturn;
- use configured investigation and asset workflows.

Mods should not create arbitrary sanction combinations or bypass case-linked
asset workflows.

## Developer

Developer is a technical role, not a moderation promotion between Mod and Admin.

Developers may receive diagnostics, reload, inspection, staff mode, vanish,
client and inventory tools for legitimate development/testing. They may prepare a
punishment request for review.

Central policy must deny direct punishment mutation, approval, sanction changes,
visibility changes and overturn actions even if an external permission, website
route or GUI accidentally exposes the surface.

## Admin

Admins inherit Mod authority and add advanced diagnostics/configuration,
authorized custom durations, raising/lowering, full overturn/approval and selected
Market/Reputation restrictions.

Admin authority should still use the same audited application services rather
than direct storage edits.

## Founder

Founder has the broadest configured policy and recovery authority, including:

- custom punishment combinations;
- confiscated-item restoration;
- owner/recovery controls;
- migration, shadow and cutover authorization;
- emergency freeze and rollback decisions.

Founder access remains audited. Owner authority is not a reason to bypass durable
intent, verification, recovery or case history.

## Aggregate inheritance

Current Paper metadata defines:

```text
enthusiastaff.rank.helper
enthusiastaff.rank.mod
enthusiastaff.rank.developer
enthusiastaff.rank.admin
enthusiastaff.rank.founder
```

Inheritance:

- Mod inherits Helper.
- Admin inherits Mod.
- Founder inherits Admin.
- Developer remains a separate technical aggregate.

Application policy may be stricter than the permission tree. In particular,
Developer direct punishment denial and Helper permanent-request conversion are
service-level boundaries.

## Review checklist

Before changing a rank or permission:

1. Update `plugin.yml` and LuckPerms deliberately.
2. Check central authorization policy, not only command permission checks.
3. Test command, GUI, API, website and integration entry points.
4. Test accidental extra permissions and rank inheritance.
5. Verify offline targets, retries and multi-server behavior.
6. Update [[Commands and Permissions]], the relevant feature hub and the
   requirements matrix.

## Related pages

- [[Commands and Permissions]]
- [[Helper Guide]]
- [[Punishment System]]
- [[Moderation, Punishments, and Reports]]
- [[Staff Tools, Investigations, and Player-State Safety]]
- [[Developer Code Guide]]
