# Rank Authority

Authorization is enforced by the application service, not only by Bukkit
permissions or GUI visibility. Accidentally granting a permission node must not
allow a rank to exceed its policy authority.

## Authority matrix

| Action | Mod | Developer | Admin | Founder/Owner |
| --- | --- | --- | --- | --- |
| Read cases, history, reports, diagnostics | Yes | Yes | Yes | Yes |
| Apply configured punishment step | Yes | **No** | Yes | Yes |
| Lower recommended punishment | Yes, as configured | **No** | Yes | Yes |
| Raise above recommendation | No | **No** | Yes | Yes |
| Use custom duration | No | **No** | Yes, configured types | Yes |
| Use arbitrary configured combination | No | **No** | Limited by policy | Yes |
| End or revoke sanction | Yes, preserving history | **No** | Yes | Yes |
| Request full overturn | Yes | **No** | Yes | Yes |
| Approve/deny overturn | No | **No** | Yes | Yes |
| Direct full overturn | No | **No** | Yes | Yes |
| Staff mode / vanish / freeze tools | Yes | Non-punishment tools only | Yes | Yes |
| Inventory inspection/editing | Configured | Configured non-punishment use | Yes | Yes |
| Market/reputation restriction | No by aggregate default | No | Yes | Yes |
| Confiscated-item restoration | No | No | No by aggregate default | Yes |
| Emergency recovery/cutover controls | No | No | Limited | Yes |

## Developer rule

Developer is a development role, not a moderation rank.

Developer may retain:

- Read-only punishment, case, report, and history access
- Diagnostics and verification
- Configuration reload where granted
- Staff mode, vanish, freeze, staff chat, client evidence, inspection, and
  inventory tools for legitimate non-punishment work

Developer may not:

- Create or confirm punishments
- Raise or lower punishment recommendations
- End, revoke, unban, unmute, remove warnings, or overturn
- Change punishment visibility
- Request or decide overturns
- Mutate punishments through commands, GUIs, APIs, website actions, automation,
  or integrations

Historical Developer-issued cases remain valid historical records; the current
rule does not rewrite old actor data.

## Mod limits

A Mod operates within configured policy:

- Apply configured ladder steps, including a configured permanent final step
- Lower a recommendation when policy allows
- End or revoke while preserving history
- Request a full overturn with a written explanation
- Use report, freeze, staff, vanish, client, inventory, and inspection tools
  granted by the rank aggregate

A Mod cannot invent arbitrary sanctions, raise above the recommendation, use
custom durations, or directly fully overturn.

## Admin and Founder differences

Admin can raise or lower recommendations, use custom durations with configured
sanction types, decide overturn requests, fully overturn, and use configured
market/reputation controls.

Founder/Owner additionally controls custom sanction combinations, confiscated
asset restoration, owner recovery, and emergency migration/cutover actions.

## Permission aggregates

The current Paper metadata defines:

- `enthusiastaff.rank.mod`
- `enthusiastaff.rank.developer`
- `enthusiastaff.rank.admin`
- `enthusiastaff.rank.founder`

See [[Commands and Permissions]] for exact child nodes. Permission aggregates
are defaults, not a substitute for service-level authorization tests.
