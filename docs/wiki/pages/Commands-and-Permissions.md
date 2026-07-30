# Commands and Permissions

This is an administrator and developer reference. Staff-facing instructions are in
[[Staff Handbook]], [[Staff Quick Start|Moderator-Quick-Start]], and [[Helper Guide]].

All declared EnthusiaStaff permission nodes default to `false`. Bukkit permissions
control command discovery and early denial, while application services recheck the
actual action before a mutation.

> **Registered does not mean production-ready.** Check [[Implementation Status]]
> for completeness and staging status.

## Registered Paper commands

| Command | Usage | Purpose | Primary permission |
| --- | --- | --- | --- |
| `/estaff` | `/estaff <status\|verify\|reload>` | Status, verification, and reload | `enthusiastaff.status`; subcommands check their own nodes |
| `/punish` | `/punish <player> [reason-id]` or `/punish resume <player>` | Central punishment workflow | `enthusiastaff.punish` |
| `/ban` | `/ban <player> [reason-id]` | Ban-filtered central workflow | `enthusiastaff.punish` |
| `/mute` | `/mute <player> [reason-id]` | Mute-filtered central workflow | `enthusiastaff.punish` |
| `/warn` | `/warn <player> [reason-id]` | Warning-filtered central workflow | `enthusiastaff.punish` |
| `/kick` | `/kick <player> [reason-id]` | Kick-filtered central workflow | `enthusiastaff.punish` |
| `/ipban` | `/ipban <player> [reason-id]` | Network-ban-filtered workflow | `enthusiastaff.punish.ip` |
| `/removepunishment` | `/removepunishment <player\|case> <action> [expiration] <reason> [CONFIRM]` | Change or overturn while retaining audit | `enthusiastaff.remove` plus action node |
| `/unban` | `/unban <player\|case> <reason> [CONFIRM]` | End latest active ban | `enthusiastaff.remove` |
| `/unmute` | `/unmute <player\|case> <reason> [CONFIRM]` | End latest active mute | `enthusiastaff.remove` |
| `/removewarning` | `/removewarning <player\|case> <reason> [CONFIRM]` | Revoke latest warning | `enthusiastaff.remove` |
| `/unwarn` | Same as `/removewarning` | Warning removal alias | `enthusiastaff.remove` |
| `/report` | `/report <player\|uuid> <reason-id> <description>` | Submit private report | No Bukkit permission declared |
| `/reports` | `/reports` | Staff report management | `enthusiastaff.reports.manage` |
| `/freeze` | `/freeze <player> <reason>` | Apply durable investigation freeze | `enthusiastaff.freeze` |
| `/unfreeze` | `/unfreeze <player> <reason> CONFIRM` | Release freeze | `enthusiastaff.freeze` |
| `/staff` | `/staff` | Toggle durable staff mode | `enthusiastaff.staffmode` |
| `/vanish` | `/vanish` | Toggle rank-aware vanish | `enthusiastaff.vanish` |
| `/staffchat` | `/staffchat` | Toggle RoseChat staff channel | `enthusiastaff.staffchat` |
| `/client` | `/client <player\|uuid> [save CONFIRM]` | Inspect or save client evidence | `enthusiastaff.client` |
| `/invsee` | `/invsee <player\|uuid>` | View/edit inventory as authorized | `enthusiastaff.inventory.view` |
| `/endersee` | `/endersee <player\|uuid>` | View/edit Ender chest as authorized | `enthusiastaff.inventory.view` |
| `/inspect` | `/inspect <player>` | Player inspector and case-linked actions | `enthusiastaff.inspect` |
| `/case` | `/case restoreitems <case-id>` | Confiscated-item restoration | `enthusiastaff.case.restoreitems` |

## Required but not currently registered

The goals require these commands, but current Paper metadata does not register all
of them:

- `/history`
- `/alts`
- `/alt`
- `/fakebase`

The Helper branch registers `/alts` and `/alt` on Velocity, not Paper. Do not add a
command to staff training until the correct runtime, behavior, and staging status
are confirmed.

## Permission nodes

### Read, status, and diagnostics

```text
enthusiastaff.status
enthusiastaff.verify
enthusiastaff.reload
enthusiastaff.diagnostics
enthusiastaff.punishment.read
enthusiastaff.case.read
enthusiastaff.alerts
```

### Punishment creation

```text
enthusiastaff.punish
enthusiastaff.punish.configured
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

### Asset, provider, and recovery authority

```text
enthusiastaff.confiscate.economy
enthusiastaff.confiscate.items
enthusiastaff.case.restoreitems
enthusiastaff.market.restrict
enthusiastaff.reputation.restrict
enthusiastaff.owner.recovery
```

## Rank aggregate nodes

### `enthusiastaff.rank.helper`

Present on `section/helper-rank-authority`, but not yet treated as deployed.
The branch includes status, read access, configured punishment workflow, reports,
alerts, freeze, staff mode, vanish, staff chat, client information, inventory
viewing, and inspection.

Helper-specific policy limits are enforced outside the permission list. The branch
blocks inventory mutation in Helper staff mode, omits advanced staff tools, and
requires approval for permanent punishments.

### `enthusiastaff.rank.mod`

In the Helper branch, Mod inherits the Helper aggregate and adds network-ban,
punishment-change, inventory-edit, and configured confiscation permissions.

### `enthusiastaff.rank.developer`

Developer is a separate technical aggregate. It includes diagnostics and
non-punishment staff tools. Punishment mutation remains denied by application
policy even if an external permission is accidentally granted.

### `enthusiastaff.rank.admin`

Includes Mod plus reload, diagnostics, custom-duration, raising, full-overturn,
overturn approval, market-restriction, and reputation-restriction permissions.

### `enthusiastaff.rank.founder`

Includes Admin plus custom punishment combinations, confiscated-item restoration,
and owner recovery.

See [[Roles and Permissions|Rank-Authority]] for the role overview.

## Verification

`/estaff verify full` is an operator and developer diagnostic. It is not an
ordinary staff task. It is intended to inspect command ownership, conflicts,
integrations, storage, configuration, runtime jars, migration state, and backends.

A verifier must never silently take command ownership from another plugin or run a
destructive command as a test.