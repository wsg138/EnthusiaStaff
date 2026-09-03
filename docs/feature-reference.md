# EnthusiaStaff feature reference

This document is the concise feature index for future Enthusia documentation/wiki generation. The files in `docs/` remain authoritative for security, database, migration, rollback and detailed operational behavior.

## Deployment / authority status

EnthusiaStaff is currently **pre-release** on `main`. The latest production server-state snapshot does not contain an active `plugins/EnthusiaStaff/` deployment. LiteBans remains the production punishment authority until the Staff migration/shadow/cutover gates are completed.

Therefore:

- the features below describe the implemented/target EnthusiaStaff runtime,
- they must not be represented publicly as the current live moderation authority until cutover is recorded,
- public punishment pages and appeals should be described as upcoming until the website/API deployment is confirmed.

## Runtime shape

EnthusiaStaff produces two cooperating runtimes:

- **Paper** — staff tools, punishment workflows, reports, cases, inventories, freezes, inspections and server-local integrations.
- **Velocity** — network login enforcement, protected network identity matching, migration coordination and restricted website/API delivery.

MariaDB is the durable system of record once cutover occurs.

## Punishment system

The central punishment workflow supports configured reasons and sanction types including:

- bans,
- mutes,
- warnings,
- kicks,
- network/IP bans,
- configured combinations,
- duration/escalation policy,
- custom-duration/custom-combination permissions for higher staff ranks.

Entry commands (`/punish`, `/ban`, `/mute`, `/warn`, `/kick`, `/ipban`) route into the same central workflow rather than maintaining unrelated command-specific punishment logic.

The workflow supports durable drafts/resume behavior so interrupted staff actions do not have to be reconstructed from memory.

Punishments retain immutable/auditable history rather than being erased when changed.

## Changing or removing punishments

`/removepunishment`, `/unban`, `/unmute`, `/removewarning` and `/unwarn` use the sanction-change lifecycle.

Depending on permissions and action, staff can:

- shorten/reduce a sanction,
- end it early,
- revoke it while retaining history,
- request an overturn,
- fully overturn it,
- approve appeal-driven overturns.

Sensitive operations require explicit confirmation and hierarchy/permission checks.

## Punishment history

`/history <player|uuid> [page]` provides database-paginated moderation/punishment history. Sensitive history is separately permission-gated.

The history/audit design is intended to preserve the reason, actor, lifecycle changes and case relationships needed for later review and public-safe views.

## Reports

Players can submit private reports with `/report <player|uuid> <reason-id> <description>`.

Staff manage reports through `/reports`. The report system is designed around configured reason policies, recent context, staff queue management and evidence permissions rather than exposing reports publicly.

## Cases

Moderation actions can be grouped/linked to durable cases. `/case` supports case detail and recovery actions, while `/inspect` provides a player-centric entry into case-linked staff actions.

Case records are intended to preserve moderation context and audit history. In the wider Enthusia design, inactive cases should auto-expire after 30 days without activity; deployment documentation should confirm that policy once the final production version is cut over.

## Freeze / investigation

`/freeze <player> <reason>` creates a durable staff investigation restriction. `/unfreeze <player> <reason> CONFIRM` releases it.

Freeze state is designed to survive ordinary runtime transitions rather than being a memory-only toggle. Chat behavior has a separate permission/control boundary.

## Staff mode and tools

`/staff` enters/exits durable staff mode.

`/stafftools` exposes staff operational utilities, including controlled teleport and spectate paths. The command also has text fallbacks (`random`, `spectate <player>`) for operational use.

Staff tools have explicit exemptions/permissions so ordinary moderation helpers do not automatically receive every invasive capability.

## Vanish

`/vanish` controls durable, rank-aware vanish. The implementation separately supports tab-list presentation through `/vanish tab <show|hide>`.

Vanish is designed around staff hierarchy and recovery rather than only `Player#hidePlayer` state.

## Staff chat

`/staffchat` toggles the configured RoseChat staff channel when the integration is available. RoseChat is optional/degradable rather than a hard dependency for the entire staff runtime.

## Client evidence

`/client <player|uuid>` inspects a point-in-time client evidence snapshot. A privileged `save CONFIRM` path persists evidence deliberately rather than silently collecting every client snapshot by default.

This is evidence for staff review; it is not automatic proof of cheating.

## Cheat tester

`/cheattester` provides bounded, evidence-only cheat-testing probes and virtual/fake-base scenarios for staff investigations.

Implemented command areas include selection, run/cancel/status/config and fake-base management. Higher permissions are required to cancel other staff members' work or manage others' fake-base scenarios.

The feature is intended to gather evidence rather than directly issue automatic punishments.

## Inventory and Ender Chest inspection

- `/invsee <player|uuid>` — inspect a live or offline inventory.
- `/endersee <player|uuid>` — inspect a live or offline Ender Chest.

Viewing and editing are separate permissions. Inventory mutation follows the repository's inventory-safety/recovery model rather than treating offline data as an unsafe direct file edit.

## Confiscation and recovery

Staff can have separately granted powers to confiscate:

- economy value,
- physical items.

Case-linked item restoration/recovery exists so destructive moderation actions can be auditable/reversible when policy permits. Founder/owner recovery is a separate high-trust boundary.

## Market and reputation restrictions

EnthusiaStaff contains integration boundaries for:

- EnthusiaMarket restrictions,
- EnthusiaCommend/reputation restrictions.

These are permission-gated and designed to use provider APIs rather than duplicate the authoritative data inside Staff. They should only be advertised after the corresponding provider/cutover is live.

## Other integrations

The Paper plugin declares optional integration with systems including:

- RoseChat,
- Simple Voice Chat,
- ViaVersion,
- Floodgate/Geyser,
- CombatLogX,
- PolarLoader,
- ProtocolLib,
- DiscordSRV,
- LuckPerms,
- EnthusiaCurrency,
- EnthusiaMarket,
- EnthusiaCommend,
- EnthusiaTeleport,
- PlayTimePlugin,
- InventoryRollbackPlus,
- EnthusiaServerAutoClicker.

Optional providers are expected to degrade independently rather than disabling the entire moderation runtime.

### Polar limitation

The currently targeted Polar loader does not expose the supported violation-event API EnthusiaStaff needs, so automatic Polar punishment is intentionally disabled unless a compatible API becomes available. Do not describe Polar as an automatic punishment source in the current feature set.

## Staff rank permission hierarchy

The plugin defines permission bundles for at least:

- Helper,
- Developer,
- Mod,
- Admin,
- Founder.

Higher ranks inherit broader workflows, but particularly sensitive actions (custom combinations, hierarchy bypass, full overturn/appeal approval, item restoration/owner recovery) remain explicit permission boundaries.

The exact LuckPerms production mapping should come from the live server configuration after cutover rather than being inferred solely from `plugin.yml` defaults.

## Website, public punishments and appeals

The architecture includes restricted website/API delivery from the Velocity side. The project direction is for public punishment information and player appeals to live on the Enthusia website rather than inside Discord tickets.

Until that website/API deployment is confirmed, future wiki generation should label these as **planned/pre-release**:

- public punishment/case-safe views,
- web appeal submission and appeal status,
- website-backed moderation history intended for public consumption.

Private reports, staff notes, sensitive evidence, protected identity data and internal case material must never be treated as public merely because a website API exists.

## LiteBans migration and cutover

LiteBans remains authoritative until the migration program completes. The repository contains dedicated documentation for:

- LiteBans import/migration,
- shadow mode,
- cutover,
- rollback.

No build or documentation change itself performs production cutover. A future wiki should switch its wording from “planned” to “live” only when production authority has actually moved to EnthusiaStaff.

## Player-facing impact after cutover

Once active, the pieces ordinary players are expected to notice are primarily:

- consistent punishment enforcement across the network,
- warnings/mutes/bans/kicks from one system,
- `/report` for private player reports,
- public-safe punishment pages on the website,
- web-based appeals,
- clearer auditable moderation changes rather than punishments silently disappearing.

Most other features in this document are staff-only and should not be exposed in unnecessary operational detail on the public wiki.