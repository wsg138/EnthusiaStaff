# Commands and Permissions

This is the administrator/developer command reference across the current Minecraft and StaffBot surfaces. Staff procedure belongs in [[Staff Handbook]], [[Punishment System]], [[Reports and Evidence]], [[Discord Moderation Platform]], and the focused feature guides.

Permission nodes and Discord command visibility are early gates. Important mutations are reauthorized in the owning application service against current actor, target, scope, duty/authority state and external preconditions.

> **Registered does not mean production-authorized.** Check [[Implementation Status]] before enabling or training staff on a workflow.

## Find the owning feature

| Command area | Start here |
| --- | --- |
| Status, reload, database, protocol and runtime health | [[Core Platform and Infrastructure]] |
| Punishments, requests, history, reports and evidence | [[Moderation, Punishments, and Reports]] |
| Staff mode, vanish, freeze, inventory, alts and tools | [[Staff Tools, Investigations, and Player-State Safety]] |
| Minecraft/Discord linking and StaffBot moderation | [[Discord Moderation Platform]] |
| StaffBot build/config/recovery | [[Staff Bot Runtime and Operations]] |
| Website/public API/appeals | [[Website and Web API]] |
| Providers, migration and cutover | [[Integrations, Migration, and Release Readiness]] |

# Minecraft commands

## Status and administration

| Command | Usage | Purpose |
| --- | --- | --- |
| `/estaff` | `/estaff <status\|verify [full]\|reload\|sanction>` | Runtime status, verification, reload and exact-sanction lifecycle entry point |

Subcommands have independent permissions. `/estaff verify full` additionally requires diagnostics authority and is not an ordinary staff command.

## Punishment creation

| Command | Usage | Primary permission |
| --- | --- | --- |
| `/punish` | `/punish <player> [reason-id]` or `/punish resume <player>` | `enthusiastaff.punish` |
| `/ban` | `/ban <player> [reason-id]` | `enthusiastaff.punish` |
| `/mute` | `/mute <player> [reason-id]` | `enthusiastaff.punish` |
| `/warn` | `/warn <player> [reason-id]` | `enthusiastaff.punish` |
| `/kick` | `/kick <player> [reason-id]` | `enthusiastaff.punish` |
| `/ipban` | `/ipban <player> [reason-id]` | `enthusiastaff.punish.ip` |

These commands enter the central punishment/request policy; the command name does not bypass hierarchy, request/approval or authority rules.

## History and exact-sanction changes

| Command | Purpose | Primary permission |
| --- | --- | --- |
| `/history <player\|uuid> [page]` | newest-first moderation history | `enthusiastaff.history.view` |
| `/case [view] <case-id>` | case detail | `enthusiastaff.history.view` |
| `/estaff sanction reduce ...` | shorten one exact active sanction | `enthusiastaff.sanction.reduce` |
| `/estaff sanction end ...` | end one exact sanction | `enthusiastaff.sanction.end` |
| `/estaff sanction revoke ...` | administratively withdraw one exact sanction | `enthusiastaff.sanction.revoke` |
| `/estaff sanction overturn ...` | reverse one exact sanction decision | `enthusiastaff.sanction.overturn` |
| `/removepunishment ...` and `/unban`/`/unmute` aliases | compatibility/case-oriented correction paths | `enthusiastaff.remove` plus action-specific authority |

Sensitive actor/private-note history requires the sensitive-history permission. Exact-sanction actions must not silently mutate sibling sanctions in the same case.

## Reports and evidence

| Command | Purpose | Primary permission |
| --- | --- | --- |
| `/report <player\|uuid> <reason-id> <description>` | submit a private report | no Bukkit permission declared for ordinary submitter |
| `/reports ...` | staff report queue/state workflow | `enthusiastaff.reports.manage` |
| `/client <player\|uuid> [save CONFIRM]` | view/save point-in-time client evidence | `enthusiastaff.client` |

## Discord/Minecraft account linking

Current merged Paper commands:

| Command | Purpose |
| --- | --- |
| `/link` | issue a short-lived one-use code from Minecraft |
| `/link <code>` | complete a link challenge that originated from Discord |
| `/unlink CONFIRM` | remove the current Minecraft account's Discord link through the supported audited flow |

`/link` has the alias `/discordlink` in current plugin metadata.

Linking safety rules:

- raw codes are short-lived and one-use;
- only hashes are persisted;
- replacement/expiry/replay/restart/concurrency are handled by the account-linking service/store;
- link ownership/history is preserved rather than manually rewritten;
- one Discord identity may have multiple current Minecraft identities, while a Minecraft identity has one current Discord owner;
- public output must not reveal another player's link/history.

Do not manually edit link rows to recover a normal user flow. See [[Discord Moderation Platform]].

## Staff-state and investigation tools

| Command | Purpose | Primary permission |
| --- | --- | --- |
| `/freeze <player> <reason>` | durable investigation freeze | `enthusiastaff.freeze` |
| `/unfreeze <player> <reason> CONFIRM` | release freeze | `enthusiastaff.freeze` |
| `/staff` | enter/leave durable Staff Mode | `enthusiastaff.staffmode` |
| `/stafftools` | Staff Tools menu and supported direct sub-actions | `enthusiastaff.stafftools.menu` plus action node |
| `/fakebase ...` | bounded fake-base tester workflow | `enthusiastaff.cheattester.fake-base` |
| `/vanish` | toggle vanish / supported tab options | `enthusiastaff.vanish` |
| `/staffchat` | toggle configured staff chat channel | `enthusiastaff.staffchat` |
| `/staffwho` | show staff rank/duty/vanish/request state | `enthusiastaff.staffwho` |
| `/invsee <player\|uuid>` | inventory view/edit as authorized | `enthusiastaff.inventory.view` plus edit authority |
| `/endersee <player\|uuid>` | Ender chest view/edit as authorized | `enthusiastaff.inventory.view` plus edit authority |
| `/inspect <player>` | player inspector and authorized shortcuts | `enthusiastaff.inspect` |
| `/case restoreitems <case-id>` | Founder-level item restoration | `enthusiastaff.case.restoreitems` |
| `/case recoveritems <case-id>` | requeue one coherent quarantined recovery operation | `enthusiastaff.owner.recovery` plus Founder policy |

Player-originated destructive Paper actions may additionally require active Staff Mode duty context. A permission node alone is not the final mutation authority.

## Velocity commands

Current proxy entry points include:

```text
/estaff
/alts
/alt
```

`/alts` may show other currently verified linked Minecraft accounts when authorized, but it must not expose Discord IDs or historical link ownership. Network relationship evidence and Discord account linking remain separate evidence classes.

# StaffBot Discord commands

StaffBot registers its Discord command set in the configured Enthusia guild after the runtime is ready. Command discovery is not final authority; every privileged action resolves the linked Enthusia staff actor and rechecks current policy.

## Read/moderation navigation

Current merged command names include:

| Discord command | Purpose |
| --- | --- |
| `/moderate` | open the Discord-target moderation panel/read view |
| `/moderate-minecraft` | open moderation/read context for a Minecraft player identity |
| `/linked` | inspect authorized linked-account information for a Discord target |
| `/history` | view authorized punishment/history context for a Discord target |
| `/notes` | view authorized staff notes context |
| `/case` | view one case by ID |

StaffBot also supports the registered user/message context commands used for moderation entry points where current runtime configuration exposes them.

Sensitive read visibility is still authorization-controlled. Discord roles do not independently grant private case/note/link access.

## Discord punishment commands

When the punishment runtime is present/enabled for the environment, current merged quick-action command names include:

```text
/warn
/mute
/unmute
/kick
/ban
/unban
/restrict
/unrestrict
```

Exact options vary by consequence. Current runtime supports fields such as target user/user ID, reason, duration, explanation, native-ban message-delete seconds, and restriction scope/mode where applicable.

### Confirmation and reauthorization

Issuing a command does not immediately make the external effect authoritative merely because Discord accepted the interaction.

The runtime:

1. resolves the Discord actor to current linked Enthusia staff identity;
2. checks rank/target/platform/consequence policy;
3. prepares the intended action;
4. uses signed/expiring interaction state where confirmation is required;
5. reauthorizes immediately before the side effect;
6. records/reconciles durable Discord effect state;
7. reports the actual verified/terminal outcome.

Destructive Discord enforcement is explicitly configuration-gated and defaults off. See [[Discord Moderation Platform]] and [[Staff Bot Runtime and Operations]].

# Authority and permission model

## Permanent staff identity

Current authority work distinguishes permanent staff identity from active Paper duty state. Identity nodes include:

```text
enthusiastaff.identity.helper
enthusiastaff.identity.mod
enthusiastaff.identity.developer
enthusiastaff.identity.admin
enthusiastaff.identity.owner
```

Legacy aggregate rank nodes remain relevant during transition/configuration compatibility.

## Active Staff Mode context

Paper and Velocity publish the same active LuckPerms context:

```text
enthusiastaff-duty=active
```

Paper uses the local authoritative session; Velocity requires a durable ACTIVE session owned by the player's current backend. This represents current on-duty Staff Mode state. It is not a replacement for permanent identity and it is not used as a universal Discord/website authority flag.

See [[Rank Authority]].

## Important Paper permission groups

Common nodes include, but are not limited to:

### Status / diagnostics

```text
enthusiastaff.status
enthusiastaff.verify
enthusiastaff.reload
enthusiastaff.diagnostics
enthusiastaff.punishment.read
enthusiastaff.case.read
enthusiastaff.alerts
```

### Punishment / review

```text
enthusiastaff.punish
enthusiastaff.punish.configured
enthusiastaff.punishment.requests.review
enthusiastaff.punish.ip
enthusiastaff.punish.custom-duration
enthusiastaff.punish.custom-combination
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

### Reports / staff tools

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
enthusiastaff.staffwho
enthusiastaff.client
enthusiastaff.inventory.view
enthusiastaff.inventory.edit
enthusiastaff.inspect
```

Target-side `stafftools.*-exempt` nodes are deliberate exemptions; do not assume operators are implicitly exempt.

### History / sanction authority

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

Viewing is independent from mutation authority. Founder bypass remains bounded and does not imply unrestricted mutation of system-issued state.

### Asset / recovery authority

```text
enthusiastaff.confiscate.economy
enthusiastaff.confiscate.items
enthusiastaff.case.restoreitems
enthusiastaff.market.restrict
enthusiastaff.reputation.restrict
enthusiastaff.owner.recovery
```

## Legacy aggregate rank nodes

Current plugin metadata still contains aggregate groups such as:

```text
enthusiastaff.rank.helper
enthusiastaff.rank.mod
enthusiastaff.rank.developer
enthusiastaff.rank.admin
enthusiastaff.rank.founder
```

Normal inheritance remains Mod → Helper, Admin → Mod, Founder → Admin, while Developer remains a separate technical aggregate. Central policy may be stricter than the permission tree.

The Discord-specific Developer exception allows approved **Discord-only temporary moderation** comparable to Mod; it does not make Developer a Minecraft Mod.

## Verification

`/estaff verify full` is an operator/developer diagnostic. It should inspect current runtime/storage/provider facts without taking destructive ownership or claiming unsupported checks passed.

StaffBot uses its own health/readiness surface. Website/moderation-web use their own protected health/deployment checks. Do not collapse all runtimes into one command result.

## Source references

- [`paper/src/main/resources/plugin.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/plugin.yml)
- [`JdaStaffModerationListener.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaStaffModerationListener.java)
- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Rank Authority]]
- [[Implementation Status]]

## Related pages

- [[Staff Handbook]]
- [[Punishment System]]
- [[Reports and Evidence]]
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Discord Moderation Platform]]
- [[Website and Web API]]
- [[Rank Authority]]
- [[Developer Code Guide]]