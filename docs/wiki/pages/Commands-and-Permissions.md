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
| `/estaff` | `/estaff <status\|verify\|reload>` | Runtime status, verification, reload and diagnostics | `enthusiastaff.status`; subcommands check their own nodes |

### Punishment creation and requests

| Command | Usage | Purpose | Primary permission |
| --- | --- | --- | --- |
| `/punish` | `/punish <player> [reason-id]` or `/punish resume <player>` | Central punishment/draft/request workflow | `enthusiastaff.punish` |
| `/ban` | `/ban <player> [reason-id]` | Ban-filtered central workflow | `enthusiastaff.punish` |
| `/mute` | `/mute <player> [reason-id]` | Mute-filtered central workflow | `enthusiastaff.punish` |
| `/warn` | `/warn <player> [reason-id]` | Warning-filtered central workflow | `enthusiastaff.punish` |
| `/kick` | `/kick <player> [reason-id]` | Kick-filtered central workflow | `enthusiastaff.punish` |
| `/ipban` | `/ipban <player> [reason-id]` | Network-ban-filtered central workflow | `enthusiastaff.punish.ip` |

### Punishment changes

| Command | Usage | Purpose | Primary permission |
| --- | --- | --- | --- |
| `/removepunishment` | `/removepunishment <player\|case> <action> [expiration] <reason> [CONFIRM]` | Reduce, end, revoke or overturn while preserving audit | `enthusiastaff.remove` plus action node |
| `/unban` | `/unban <player\|case> <reason> [CONFIRM]` | End the latest active ban | `enthusiastaff.remove` |
| `/unmute` | `/unmute <player\|case> <reason> [CONFIRM]` | End the latest active mute | `enthusiastaff.remove` |
| `/removewarning` | `/removewarning <player\|case> <reason> [CONFIRM]` | Revoke the latest warning | `enthusiastaff.remove` |
| `/unwarn` | Same as `/removewarning` | Warning-removal alias | `enthusiastaff.remove` |

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
| `/vanish` | `/vanish` or `/vanish tab <show\|hide>` | Toggle vanish or spectator tab presentation | `enthusiastaff.vanish` |
| `/staffchat` | `/staffchat` | Toggle the configured RoseChat staff channel | `enthusiastaff.staffchat` |
| `/invsee` | `/invsee <player\|uuid>` | View/edit inventory as authorized | `enthusiastaff.inventory.view` |
| `/endersee` | `/endersee <player\|uuid>` | View/edit Ender chest as authorized | `enthusiastaff.inventory.view` |
| `/inspect` | `/inspect <player>` | Player inspector and case-linked actions | `enthusiastaff.inspect` |
| `/case` | `/case restoreitems <case-id>` | Case-linked confiscated-item restoration | `enthusiastaff.case.restoreitems` |

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

The goals require these top-level commands, but current Paper metadata does not
register them:

```text
/history
/fakebase
```

Do not add them to staff training until registration, behavior, permissions and
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
enthusiastaff.vanish
enthusiastaff.staffchat
enthusiastaff.client
enthusiastaff.inventory.view
enthusiastaff.inventory.edit
enthusiastaff.inspect
```

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
workflow, reports, alerts, freeze, staff mode, vanish, staff chat, inventory view
and inspection. Central policy still limits direct punishment outcomes and
inventory mutation.

### Mod

Inherits Helper and adds request review, network-ban permission, selected sanction
changes, inventory edit and configured confiscation permissions.

### Developer

A separate technical aggregate with diagnostics/reload and investigation tools.
It includes the punishment request entry surface, but central policy must deny
direct punishment mutation and approval.

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
- [[Staff Tools, Investigations, and Player-State Safety]]
- [[Developer Code Guide]]
