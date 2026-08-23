# Roles and Permissions

This page explains the intended authority model behind the rank aggregates.
Ordinary staff should use [[Staff Handbook]],
[[Staff Quick Start|Moderator-Quick-Start]] and their focused procedure pages.

Permission nodes control command discovery and early denial. Important writes are
also reauthorized inside central services, so accidentally granting one node must
not silently turn a role into a higher moderation rank.

> **Planned Discord exception:** [[Discord Moderation Platform]] defines a deliberate platform-specific authority model that is not implemented yet. Developer remains non-punitive for Minecraft, but is planned to have the same **Discord-only** temporary moderation authority as Mod. This exception must be explicit in domain authorization rather than inferred from Discord roles.

## Quick navigation

- Exact permission nodes and command registration: [[Commands and Permissions]]
- Punishment/request behavior and source files: [[Moderation, Punishments, and Reports]]
- Planned Discord authority: [[Discord Moderation Platform]]
- Staff-tool restrictions and source files: [[Staff Tools, Investigations, and Player-State Safety]]
- Helper staff guide: [[Helper Guide]]
- Authoritative policy source:
  [`plugin.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/plugin.yml)
  plus central authorization tests/services

## Rank overview

| Role | Normal purpose | Direct punishment authority | Approval authority | Asset/staff-tool authority |
| --- | --- | --- | --- | --- |
| Helper | Trial moderation and ordinary investigations | Current Minecraft: configured temporary outcomes; permanent outcomes become requests. Planned Discord: warnings and configured short temporary mutes only. | None | Restricted staff mode, freeze, vanish, reports, read-only inventory/Ender and inspection |
| Mod | Full ordinary moderation | Current Minecraft: configured outcomes, including configured permanent steps. Planned Discord: configured temporary/custom-temporary Discord outcomes; permanent Discord ban/mute/restriction requires Admin+. | May review eligible Helper/Developer requests where current policy allows | Inventory editing and configured confiscation; no owner recovery |
| Developer | Technical work and diagnostics | Minecraft: request preparation only; no direct mutation. Planned Discord: same temporary Discord moderation authority as Mod, but no Minecraft authority is gained through Discord. | None unless a separately approved future policy says otherwise | Technical diagnostics and selected investigation tools; still no Minecraft punishment mutation |
| Admin | Advanced moderation, configuration and review | Configured outcomes plus authorized custom durations/raising; planned Discord permanent ban/mute/channel restriction | May review requests and overturns as configured | Advanced tools and selected provider restrictions |
| Founder | Owner-level policy, recovery and release authority | Broadest configured/custom authority | Final approval and exceptional recovery | Owner recovery, confiscated-item restoration, migration/cutover authority |

The table distinguishes current Minecraft policy from the planned Discord-specific
extension. The deployed version, LuckPerms groups, Discord staff-link state and
central service tests determine actual live authority.

## Helper

Helpers handle ordinary moderation and basic investigations.

Current aggregate permissions include status/verification, punishment/read access,
configured punishment workflow, reports, alerts, freeze, staff mode, vanish,
staff chat, inventory view and inspection.

Important current policy limits:

- configured temporary Minecraft punishments may apply directly;
- a configured Minecraft result containing a permanent sanction becomes an approval request;
- Helper cannot approve requests or overturns;
- Helper staff mode must block inventory mutation and advanced recovery tools;
- severe, complex or uncertain cases should be escalated even when a command is visible.

For the planned Discord bot, Helper is intentionally narrower: warnings and
configured short temporary mutes only. Planned Discord kicks, bans, permanent
mutes, channel restrictions, cross-platform punishment and overturn authority are
Mod+ or higher according to the Discord policy.

See [[Helper Guide]].

## Mod

Mods inherit the Helper toolset and add request review, network-ban permission,
selected sanction changes, inventory editing and configured confiscation.

Current Minecraft policy allows Mods to:

- apply configured punishment steps;
- approve/deny eligible Helper or Developer requests;
- lower or end/revoke sanctions where policy allows;
- request a full overturn;
- use configured investigation and asset workflows.

For the planned Discord bot, Mod may use configured temporary punishments and
custom temporary durations within policy. Permanent Discord ban, permanent mute
and permanent channel restriction require Admin+.

Mods should not create arbitrary sanction combinations or bypass case-linked
asset workflows.

## Developer

Developer is a technical role, not a moderation promotion between Mod and Admin.

For Minecraft and current EnthusiaStaff punishment surfaces, Developers may
receive diagnostics, reload, inspection, staff mode, vanish, client and inventory
tools for legitimate development/testing. They may prepare a punishment request
for review. Central policy must deny direct Minecraft punishment mutation,
approval, sanction changes, visibility changes and overturn actions even if an
external permission, website route or GUI accidentally exposes the surface.

The planned Discord bot deliberately adds one exception: a linked Developer gets
the same **Discord-only** temporary moderation authority as Mod. That does not
allow the Developer to use a Discord command to punish Minecraft unless the actor
independently satisfies the required Minecraft/domain authorization. Permanent
Discord ban/mute/channel restriction still requires Admin+.

## Admin

Admins inherit Mod authority and add advanced diagnostics/configuration,
authorized custom durations, raising/lowering, full overturn/approval and selected
Market/Reputation restrictions.

The planned Discord policy also makes Admin the minimum normal rank for permanent
Discord ban, permanent mute and permanent channel restriction.

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

Application policy may be stricter than the permission tree. The planned Discord
Developer exception must be modeled as a platform-specific domain capability,
not by changing Developer into a Mod-equivalent Minecraft rank.

## Review checklist

Before changing a rank or permission:

1. Update platform command/role discovery deliberately.
2. Check central authorization policy, not only command permission checks.
3. Test command, GUI, API, website, Discord and integration entry points.
4. Test accidental extra permissions, staff-link requirements and rank inheritance.
5. Verify offline targets, retries, partial cross-platform failure and multi-server behavior.
6. Update [[Commands and Permissions]], [[Discord Moderation Platform]], the relevant feature hub and the requirements matrix.

## Related pages

- [[Commands and Permissions]]
- [[Discord Moderation Platform]]
- [[Helper Guide]]
- [[Punishment System]]
- [[Moderation, Punishments, and Reports]]
- [[Staff Tools, Investigations, and Player-State Safety]]
- [[Developer Code Guide]]
