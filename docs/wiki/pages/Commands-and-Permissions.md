# Commands and Permissions

All declared EnthusiaStaff permission nodes default to `false`. Bukkit
permissions control command discovery and early denial, but authoritative
services must recheck rank and action authority before every mutation.

> **Registered does not mean production-ready.** This page records current
> Paper command metadata and known planned commands. Check [[Implementation Status]] for completeness and staging status.

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

The goals require these commands, but current Paper metadata does not register
them:

- `/history`
- `/alts`
- `/alt`
- `/fakebase`

Do not add them to staff training as usable commands until registration,
authorization, behavior, and staging verification are complete.

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

### `enthusiastaff.rank.developer`

Includes diagnostics and non-punishment staff tools. It intentionally omits
every punishment mutation permission. Application services must deny Developer
even if a stale external permission grants one accidentally.

### `enthusiastaff.rank.mod`

Includes configured punishment authority, lowering, ending, revoking, overturn
requests, reports, freeze, staff mode, vanish, client, inventory, inspection,
and confiscation permissions.

### `enthusiastaff.rank.admin`

Includes Mod plus reload, diagnostics, custom duration, raising, full overturn,
overturn approval, market restriction, and reputation restriction.

### `enthusiastaff.rank.founder`

Includes Admin plus custom punishment combinations, confiscated-item
restoration, and owner recovery.

See [[Rank Authority]] for policy limits that permissions alone cannot express.

## Verification

`/estaff verify full` is intended to inspect command ownership, namespaced
fallbacks, conflicts, integrations, database/schema, recovery workers, config,
secrets, runtime jars, migration state, and backends.

A verifier must never silently steal a command from another plugin or execute a
destructive command as a test.
