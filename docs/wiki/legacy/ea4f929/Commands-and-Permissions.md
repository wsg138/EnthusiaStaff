# Commands and Permissions

All EnthusiaStaff permission nodes default to `false`. Bukkit permissions
control command discovery and early feedback, but application services
reauthorize every privileged state change. Granting a command node cannot
override rank restrictions such as Developer's read-only punishment policy.

## Registered Paper commands

| Command | Purpose | Primary permission |
| --- | --- | --- |
| `/estaff <status\|verify\|reload>` | Runtime status, verification, and configuration reload | `enthusiastaff.status`; subcommands apply their own checks |
| `/punish <player> [reason-id]` | Central punishment workflow | `enthusiastaff.punish` |
| `/punish resume <player>` | Resume the actor's durable review draft | `enthusiastaff.punish` |
| `/ban`, `/mute`, `/warn`, `/kick` | Open the same workflow filtered by sanction type | `enthusiastaff.punish` |
| `/ipban` | Open the network-ban-filtered workflow | `enthusiastaff.punish.ip` |
| `/removepunishment` | End, reduce, revoke, or overturn while retaining audit | `enthusiastaff.remove` plus action permission |
| `/unban`, `/unmute` | End the latest matching active sanction | `enthusiastaff.remove` |
| `/removewarning`, `/unwarn` | Revoke the latest warning while retaining history | `enthusiastaff.remove` |
| `/report` | Submit a private player report with recent context | No Bukkit node declared |
| `/reports` | Open staff report management | `enthusiastaff.reports.manage` |
| `/freeze`, `/unfreeze` | Apply or release a durable investigation freeze | `enthusiastaff.freeze` |
| `/staff` | Enter or leave durable staff mode | `enthusiastaff.staffmode` |
| `/vanish` | Toggle durable rank-aware vanish | `enthusiastaff.vanish` |
| `/staffchat` | Toggle the configured RoseChat staff channel | `enthusiastaff.staffchat` |
| `/client` | Inspect or save a point-in-time client evidence snapshot | `enthusiastaff.client` |
| `/invsee`, `/endersee` | Inspect live or offline inventory state | `enthusiastaff.inventory.view` |
| `/inspect` | Open staff inspection and case-linked asset actions | `enthusiastaff.inspect` |
| `/case restoreitems <case-id>` | Start Founder-authorized confiscated-item restoration | `enthusiastaff.case.restoreitems` |

The command registry is still incomplete. `/history`, `/alts`, `/alt`, and
`/fakebase` are required by the project specification but are not currently
registered and must not be documented as available.

## Permission groups

### Read and diagnostics

- `enthusiastaff.status`
- `enthusiastaff.verify`
- `enthusiastaff.reload`
- `enthusiastaff.diagnostics`
- `enthusiastaff.punishment.read`
- `enthusiastaff.case.read`
- `enthusiastaff.alerts`

### Punishment creation and change

- `enthusiastaff.punish`
- `enthusiastaff.punish.configured`
- `enthusiastaff.punish.ip`
- `enthusiastaff.punish.custom-duration`
- `enthusiastaff.punish.custom-combination`
- `enthusiastaff.remove`
- `enthusiastaff.remove.lower`
- `enthusiastaff.remove.raise`
- `enthusiastaff.remove.custom-duration`
- `enthusiastaff.remove.end`
- `enthusiastaff.remove.revoke`
- `enthusiastaff.remove.request-overturn`
- `enthusiastaff.remove.full-overturn`
- `enthusiastaff.remove.approve-overturn`

### Staff and investigation

- `enthusiastaff.reports.manage`
- `enthusiastaff.freeze`
- `enthusiastaff.freeze.chat`
- `enthusiastaff.staffmode`
- `enthusiastaff.vanish`
- `enthusiastaff.staffchat`
- `enthusiastaff.client`
- `enthusiastaff.inventory.view`
- `enthusiastaff.inventory.edit`
- `enthusiastaff.inspect`

### Asset and recovery authority

- `enthusiastaff.confiscate.economy`
- `enthusiastaff.confiscate.items`
- `enthusiastaff.case.restoreitems`
- `enthusiastaff.market.restrict`
- `enthusiastaff.reputation.restrict`
- `enthusiastaff.owner.recovery`

## Rank aggregates

| Rank node | Intended authority |
| --- | --- |
| `enthusiastaff.rank.developer` | Diagnostics and non-punishment staff tools; punishment, case, and report reads only |
| `enthusiastaff.rank.mod` | Configured punishment steps, lower/end/revoke actions, overturn requests, reports, and ordinary staff tools |
| `enthusiastaff.rank.admin` | Mod authority plus reload/diagnostics, custom durations, raised recommendations, direct overturn decisions, market, and reputation controls |
| `enthusiastaff.rank.founder` | Admin authority plus custom sanction combinations, confiscated-item restoration, and owner recovery |

Developer denial is enforced inside the punishment and sanction services even
if a stale server permission accidentally grants a mutation command.

## Confirmation and audit

Sensitive commands require explicit confirmation text where shown by their
usage. Permissions and confirmation do not replace durable idempotency,
current-state checks, or audit. A denied, stale, or ambiguous operation must
leave authoritative state unchanged or enter visible recovery quarantine.
