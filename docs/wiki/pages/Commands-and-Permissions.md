# Commands and Permissions

This is the administrator/developer command reference. Staff-facing procedure is
in [[Staff Quick Start|Moderator-Quick-Start]], [[Punishment System]],
[[Reports and Evidence]] and the other focused guides.

All declared EnthusiaStaff permissions default to `false`. Bukkit/Velocity
permissions control command discovery and early denial, while authoritative
services recheck rank and action policy before a mutation.

> **Registered does not mean production-ready.** Use
> [[Feature Completion Status|Implementation-Status]] and the relevant feature
> hub before enabling or training staff on a command.

## Find the owning feature

| Command area | Feature details and source files |
| --- | --- |
| Status, reload, database, protocol and runtime health | [[Core Platform and Infrastructure]] |
| Punishments, requests, history, reports, evidence and automod | [[Moderation, Punishments, and Reports]] |
| Staff mode, vanish, freeze, inventory, confiscation, alts and tools | [[Staff Tools, Investigations, and Player-State Safety]] |
| Discord, website, providers, migration and cutover | [[Integrations, Migration, and Release Readiness]] |

## Paper commands

### Status and administration

| Command | Usage | Purpose | Primary permission |
| --- | --- | --- | --- |
| `/estaff` | `/estaff <status\|verify\|reload\|sanction>` | Runtime status, safe reload and exact-sanction lifecycle commands | Subcommands check independent permission nodes |

### Punishment creation and requests

| Command | Usage | Purpose | Primary permission |
| --- | --- | --- | --- |
| `/punish` | `/punish <player> [reason-id]` or `/punish resume <player>` | Central punishment/draft/request workflow | `enthusiastaff.punish` |
| `/ban` | `/ban <player> [reason-id]` | Ban-filtered central workflow | `enthusiastaff.punish` |
| `/mute` | `/mute <player> [reason-id]` | Mute-filtered central workflow | `enthusiastaff.punish` |
| `/warn` | `/warn <player> [reason-id]` | Warning-filtered central workflow | `enthusiastaff.punish` |
| `/kick` | `/kick <player> [reason-id]` | Kick-filtered central workflow | `enthusiastaff.punish` |
| `/ipban` | `/ipban <player> [reason-id]` | Network-ban-filtered central workflow | `enthusiastaff.punish.ip` |

### Punishment history and changes

| Command | Usage | Purpose | Primary permission |
| --- | --- | --- | --- |
| `/history` | `/history <player\|uuid> [page]` | Newest-first, database-paginated moderation timeline for current, historical, offline, Java and known Bedrock identities | `enthusiastaff.history.view`; staff actors/private notes require `enthusiastaff.history.view-sensitive` |
| `/case` | `/case [view] <case-id>` | Complete case detail with every sanction, request, appeal and mutation event | `enthusiastaff.history.view` |
| `/estaff sanction reduce` | `/estaff sanction reduce <sanction-id> <ISO-expiration\|duration> [--request <request-id>] <reason>` | Shorten one active sanction without replacing its original decision | `enthusiastaff.sanction.reduce` |
| `/estaff sanction end` | `/estaff sanction end <sanction-id> [--request <request-id>] <reason>` | End one otherwise-valid sanction immediately | `enthusiastaff.sanction.end` |
| `/estaff sanction revoke` | `/estaff sanction revoke <sanction-id> [--request <request-id>] <reason>` | Administratively withdraw one sanction without declaring the original decision wrong | `enthusiastaff.sanction.revoke` |
| `/estaff sanction overturn` | `/estaff sanction overturn <sanction-id> [--appeal <appeal-id>] [--request <request-id>] <reason>` | Reverse one punishment decision and optionally link an accepted appeal | `enthusiastaff.sanction.overturn`; appeal linkage also requires `enthusiastaff.sanction.overturn.appeal` |
| `/removepunishment` | `/removepunishment <player\|case> <action> [expiration] <reason> [CONFIRM]` | Existing case-oriented GUI workflow retained for compatibility | `enthusiastaff.remove` plus action node |
| `/unban`, `/unmute`, `/removewarning`, `/unwarn` | Existing case/player aliases | Existing convenience paths through the central case-oriented workflow | `enthusiastaff.remove` |

Exact-sanction commands never accept an ambiguous multi-sanction case. Database work runs asynchronously, then the locked transaction rechecks operational mode, action authority, issuing-rank hierarchy and the sanction revision. Console follows the existing explicit system/Founder identity model; it is not an implicit hierarchy bypass.

### Reports and evidence

| Command | Usage | Purpose | Primary permission |
| --- | --- | --- | --- |
| `/report` | `/report <player\|uuid> <reason-id> <description>` | Submit a private player report | No Bukkit permission declared |
| `/reports` | `/reports` and queue/state subcommands | Staff report management | `enthusiastaff.reports.manage` |
| `/client` | `/client <player\|uuid> [save CONFIRM]` | View or save point-in-time client evidence | `enthusiastaff.client` |

### Staff-state and investigation tools

| Command | Usage | Purpose | Primary permission |
| --- | --- | --- | --- |
| `/freeze` | `/freeze <player> <reason>` | Apply durable investigation freeze | `enthusiastaff.freeze` |
| `/unfreeze` | `/unfreeze <player> <reason> CONFIRM` | Release freeze | `enthusiastaff.freeze` |
| `/staff` | `/staff` | Enter or leave durable staff mode | `enthusiastaff.staffmode` |
| `/stafftools` | `/stafftools`, `/stafftools random`, `/stafftools spectate <player>` | Text/Bedrock fallback for staff hotbar menu, random teleport and follow/spectate | `enthusiastaff.stafftools.menu`; sub-actions also require their direct tool node |
| `/vanish` | `/vanish` or `/vanish tab <show\|hide>` | Toggle vanish or spectator tab presentation | `enthusiastaff.vanish` |
| `/staffchat` | `/staffchat` | Toggle the configured RoseChat staff channel | `enthusiastaff.staffchat` |
| `/invsee` | `/invsee <player\|uuid>` | View/edit inventory as authorized | `enthusiastaff.inventory.view` |
| `/endersee` | `/endersee <player\|uuid>` | View/edit Ender chest as authorized | `enthusiastaff.inventory.view` |
| `/inspect` | `/inspect <player>` | Player inspector and case-linked actions | `enthusiastaff.inspect` |
| `/case` | `/case restoreitems <case-id>` | Founder-only confiscated-item restoration; case viewing is documented above | `enthusiastaff.case.restoreitems` |

`/stafftools` requires an active staff-mode session in addition to Bukkit
permissions. The hotbar and command fallback share the same dispatcher. Random
teleport requires `enthusiastaff.stafftools.teleport`; follow/spectate requires
`enthusiastaff.stafftools.spectate`; menu access requires
`enthusiastaff.stafftools.menu`. See [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
for target filters, cooldowns, stale-tool rejection and Bedrock behavior.

## Velocity commands

The proxy currently registers:

```text
/estaff
/alts
/alt
```

`/alts` and `/alt` being registered does not mean the complete confidence,
exception, inheritance, GUI, alert and key-rotation workflow is finished. See
[[Alt Investigations]] and
[[Staff Tools, Investigations, and Player-State Safety]].

## Required but not registered

The goals require this top-level command, but current Paper metadata does not
register it:

```text
/fakebase
```

Do not add it to staff training until registration, behavior, permissions and
staging are complete.

## Permission nodes

### Status, read and diagnostics

```text
enthusiastaff.status
enthusiastaff.verify
enthusiastaff.reload
enthusiastaff.diagnostics
enthusiastaff.punishment.read
enthusiastaff.case.read
enthusiastaff.alerts
```

### Punishment creation and review

```text
enthusiastaff.punish
enthusiastaff.punish.configured
enthusiastaff.punishment.requests.review
enthusiastaff.punish.ip
enthusiastaff.punish.custom-duration
enthusiastaff.punish.custom-combination
```

### Punishment changes

```text
enthusiastaff.remove
enthusiastaff.remove.lower
enthusiastaff.remove.raise
enthusiastaff.remove.custom-duration
enthusiastaff.remove.end
enthusiastaff.remove.revoke
enthusiastaff.remove.request-overturn
enthusiastaff.remove.full-overturn
enthusiastaff.remove.approve-overturn
```

### Reports and staff tools

```text
enthusiastaff.reports.manage
enthusiastaff.freeze
enthusiastaff.freeze.chat
enthusiastaff.staffmode
enthusiastaff.stafftools.teleport
enthusiastaff.stafftools.spectate
enthusiastaff.stafftools.menu
enthusiastaff.stafftools.random-exempt
enthusiastaff.stafftools.spectate-exempt
enthusiastaff.vanish
enthusiastaff.staffchat
enthusiastaff.client
enthusiastaff.inventory.view
enthusiastaff.inventory.edit
enthusiastaff.inspect
```

The two `stafftools.*-exempt` nodes default to `false`. They are target-side
exemptions and should be assigned deliberately; the dispatcher does not treat
operators as implicitly exempt.

### History and exact-sanction authority

```text
enthusiastaff.history.view
enthusiastaff.history.view-sensitive
enthusiastaff.sanction.reduce
enthusiastaff.sanction.end
enthusiastaff.sanction.revoke
enthusiastaff.sanction.overturn
enthusiastaff.sanction.overturn.appeal
enthusiastaff.sanction.bypass-hierarchy
```

Viewing history is independent from mutation authority. The bypass node is Founder-only and still cannot mutate system-issued sanctions.

### Asset, provider and recovery authority

```text
enthusiastaff.confiscate.economy
enthusiastaff.confiscate.items
enthusiastaff.case.restoreitems
enthusiastaff.market.restrict
enthusiastaff.reputation.restrict
enthusiastaff.owner.recovery
```

## Rank aggregate nodes

Current Paper metadata defines:

```text
enthusiastaff.rank.helper
enthusiastaff.rank.mod
enthusiastaff.rank.developer
enthusiastaff.rank.admin
enthusiastaff.rank.founder
```

### Helper

Includes basic status/verification, punishment/read access, configured punishment
workflow, reports, alerts, freeze, staff mode, staff-tool teleport/spectate/menu,
vanish, staff chat, inventory view and inspection. Central policy still limits
direct punishment outcomes and inventory mutation.

### Mod

Inherits Helper and adds request review, network-ban permission, selected sanction
changes, inventory edit and configured confiscation permissions.

### Developer

A separate technical aggregate with diagnostics/reload and investigation tools,
including the direct staff-tool permissions. It includes the punishment request
entry surface, but central policy must deny direct punishment mutation and approval.

### Admin

Inherits Mod and adds advanced diagnostics/configuration, custom durations,
raising, full overturn/approval and selected Market/Reputation restrictions.

### Founder

Inherits Admin and adds custom punishment combinations, confiscated-item
restoration and owner recovery.

See [[Roles and Permissions|Rank-Authority]] for the policy explanation. Always
compare the Wiki with
[`plugin.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/plugin.yml),
LuckPerms groups and authorization tests.

## Verification

`/estaff verify full` is an operator/developer diagnostic, not an ordinary staff
command. It is intended to inspect command ownership/conflicts, integrations,
storage, configuration, runtime artifacts, migration state and backends.

Verification must never silently take command ownership from another plugin or
run a destructive command as a test.

## Related pages

- [[Feature Completion Status|Implementation-Status]]
- [[Roles and Permissions|Rank-Authority]]
- [[Core Platform and Infrastructure]]
- [[Moderation, Punishments, and Reports]]
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Staff Tools, Investigations, and Player-State Safety]]
- [[Developer Code Guide]]
