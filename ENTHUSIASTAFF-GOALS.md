# EnthusiaStaff Platform — Authoritative Goals and Task Specification

## 0. Purpose

This file is the authoritative goals and requirements document for the complete Enthusia Network staff, moderation, punishment, reporting, vanish, inventory-inspection, alt-detection, LiteBans migration, integration, Discord, and punishment/appeal website platform.

A temporary Codex resume prompt may decide where work begins, but this file defines what the finished project must accomplish. Existing classes, commands, tests, or branches do not prove completion by themselves.

A feature is complete only when its behavior, permissions, persistence, validation, concurrency, failure handling, restart recovery, duplicate safety, audit, configuration, tests, verification output, and operational documentation are all implemented.

Later corrections in this file override older assumptions. Most importantly, **Helper is a tightly restricted trial moderation rank below Mod, and Developer is a separate development role that may prepare requests but has no direct punishment or approval authority**.

Nothing may be merged, released, published, deployed, or applied to production automatically.

---

## 1. Mission and priorities

Build a production-grade distributed moderation platform, not a basic staff plugin.

Priorities:

1. Reliability
2. Architecture
3. Security
4. Data integrity
5. Punishment correctness
6. LiteBans migration and cutover safety
7. Efficiency and performance
8. Scalability
9. Maintainability
10. Meaningful tests
11. Staff/player usability
12. Full scope completion

Migration, punishment removal, inventory editing, confiscation, economy removal, authentication, alt inheritance, and crash recovery must be treated as financial-grade transactional workflows.

The final repositories must reach **Codacy grade A with zero unresolved findings in first-party code**. Proven false positives may be resolved only through narrow, justified suppressions.

---

## 2. Scope

Included:

- Network bans, mutes, warnings, kicks, and IP/network bans
- Cases, sanctions, punishment families, ladders, decay, escalation, and combined sanctions
- Punishment creation/history/reduction/revocation/removal/overturn
- Public/private punishment visibility
- Durable punishment GUI drafts
- Reports with chat, private-message, coordinates, and client evidence
- Staff mode, vanish, freeze, staff chat, staff tools
- Cheat testers, fake entities, and fake bases
- Online/offline inventory and Ender chest editing
- Item and economy confiscation with safe restoration
- Alt/network identity tracking and sanction inheritance
- LiteBans migration, 168-hour shadow mode, cutover, emergency freeze, and rollback
- Discord logs/alerts
- Public punishment and appeal website
- Cross-plugin APIs/adapters
- Reload, verification, CI, documentation, static analysis, and staging tests

Explicitly excluded:

- Whole-server rollback
- Area/block rollback
- CoreProtect replacement
- Unified rollback of homes, claims, permissions, auctions, or all player activity

Those belong to a future separate `EnthusiaRollback` project.

---

## 3. Unified workspace

Keep all related repositories inside one parent folder:

```text
EnthusiaStaffWorkspace/
├── ENTHUSIASTAFF-GOALS.md
├── AGENTS.md
├── WORKSPACE-MANIFEST.md
├── EnthusiaStaff.code-workspace
├── reports/
│   ├── REQUIREMENTS-MATRIX.md
│   ├── CODACY-BASELINE.md
│   └── SESSION-REPORTS/
└── repos/
    ├── EnthusiaStaff/
    ├── enthusia-site/
    ├── EnthusiaCurrency/
    ├── EnthusiaCommend/
    ├── EnthusiaAutoClicker/
    ├── Enthusia-RoseChat/
    └── EnthusiaMarket/
```

Each child remains its own Git repository.

`WORKSPACE-MANIFEST.md` must track:

- Repository and remote
- Main/default branch
- Working branch
- Latest local and pushed SHA
- PR URL
- Build/test/Docker results
- Codacy grade and issue count
- Blockers

`reports/REQUIREMENTS-MATRIX.md` must map every requirement in this file using only:

- `NOT_STARTED`
- `PARTIAL`
- `IMPLEMENTED_UNVERIFIED`
- `TESTED`
- `STAGING_VERIFIED`
- `BLOCKED`

---

## 4. GitHub workflow

All work branches must be based on the latest applicable main/default branch.

For each repository:

1. Fetch remotes/tags.
2. Update main/default by fast-forward.
3. Confirm clean baseline.
4. Create/update feature branch.
5. Preserve existing feature-branch commits.
6. Never discard existing work merely to recreate the branch.

Expected branches:

- EnthusiaStaff: `agent/complete-staff-platform`
- enthusia-site: `agent/punishment-platform`
- EnthusiaCurrency: `agent/moderation-api`
- EnthusiaCommend: `agent/reputation-blacklist-api`
- EnthusiaAutoClicker: `agent/client-evidence-api`
- Enthusia-RoseChat: `agent/staff-bridge-api`
- EnthusiaMarket: `agent/moderation-api`

Rules:

- Do not commit directly to main.
- Do not merge PRs.
- Do not publish releases.
- Do not deploy.
- Do not alter production data.
- Push after every coherent finished section.
- Confirm every pushed SHA exists remotely.
- Update the workspace manifest after each checkpoint.
- Review Codacy after every pushed commit before starting the next major section.

Do not commit build outputs, `.gradle`, `node_modules`, IDE caches, server runtime folders, logs, DBs, `.env`, secrets, tokens, credentials, private jars, or generated reports.

All Enthusia-owned repos must use `wsg138` remotes. Do not add project-specific `BadgersMC` references to `wsg138` repos. References may remain in repositories actually owned by BadgersMC, and legally required upstream attribution must remain.

---

## 5. Deployable project shape

Exactly two deployable Minecraft jars:

1. `EnthusiaStaff-Paper-<version>.jar`
2. `EnthusiaStaff-Velocity-<version>.jar`

Internal modules are expected:

```text
common/
domain/
integration-contracts/
persistence/
protocol/
paper/
velocity/
integration-tests/
docs/
```

Use Java 21.

Target Paper/Leaf/Purpur 1.21.x, especially 1.21.8–1.21.11.

Avoid NMS where supported APIs exist. Isolate unavoidable version behavior behind focused tested adapters.

Inspect final shaded jars. Provider API classes must not be duplicated into EnthusiaStaff when provider plugins already supply them.

---

## 6. Current environment

- Velocity proxy
- HUB backend
- SMP backend
- MariaDB
- Geyser/Floodgate
- Bedrock names prefixed with `*`
- Java 21
- Separate HUB/SMP inventory, Ender chest, world, and player-data scopes

Known integrations:

- RoseChat
- Simple Voice Chat
- ViaVersion/ViaBackwards
- CombatLogX
- Polar 1.7.11-beta
- ProtocolLib
- DiscordSRV
- LuckPerms
- Floodgate/Geyser
- EnthusiaCurrency
- EnthusiaMarket
- EnthusiaCommend
- EnthusiaTeleport
- PlayTimePlugin
- InventoryRollbackPlus
- EnthusiaAutoClicker

Future backends should require only the same Paper jar, configuration, and verification.

---

## 7. Architecture

Use clean/hexagonal architecture.

Domain code must not directly depend on Bukkit, Velocity, Discord, MariaDB implementations, or web frameworks.

Use interfaces for persistence, external plugins, messaging, time, identity lookup, audit, authorization, random codes, encryption, file storage, and notification.

Commands and GUIs delegate to application services. GUI and command classes must not contain punishment policy.

Required bounded contexts:

- Identity/player directory
- Cases/punishments/sanctions/escalation
- Reports/appeals
- Alts/network identity
- Inventory/economy/market/reputation
- Staff sessions/vanish/freeze/tools
- Discord
- Migration
- Verification/audit/configuration
- External integrations

Expose internal Bukkit services such as:

- `StaffVisibilityService`
- `PunishmentQueryService`
- `SanctionQueryService`
- `StaffSessionService`
- `StaffModeQueryService`
- `InventoryLockService`
- `AltRelationshipService`
- `PlayerDirectoryService`

Avoid giant main classes, cyclic dependencies, global mutable state, unbounded executors, main-thread DB/network work, or destructive logic spread through unrelated listeners.

---

## 8. Safe-failure and transactional rules

When uncertain, fail safely.

- Punishment failure cannot partially apply.
- Removal failure cannot leave a combined sanction half-changed.
- Inventory/economy failure preserves original state.
- Stale state cannot overwrite newer state.
- Migration mismatch blocks cutover.
- Optional integration failure disables only that feature.
- Ambiguous destructive work enters quarantine.
- Success is reported only after durable commit.

Use as appropriate:

- MariaDB transactions
- Idempotency keys
- Unique constraints
- Optimistic revisions
- Per-player locks
- Durable leases/journals/outboxes
- Explicit operation states
- Atomic file replacement
- Startup recovery
- Append-only audit
- Bounded retry/backoff
- Circuit breakers
- Recovery quarantine
- Main-thread Bukkit mutation only

Every destructive workflow defines validation, authorization, durable intent, lock/lease, revalidation, before snapshot, commit, verification, audit, notification, retry, crash recovery, rollback/quarantine, and duplicate behavior.

Use at-least-once delivery with idempotent consumers; do not claim true exactly-once transport.

---

## 9. Paper–Velocity communication

MariaDB is the durable source of truth and outbox.

Use a persistent authenticated Paper-to-Velocity connection for low latency. Do not require an online player as transport.

Required:

- Server allowlist
- Protocol negotiation
- HMAC or mutually authenticated transport
- Replay protection
- Message IDs/acks
- Idempotent handlers
- Auto reconnect/backoff
- Bounded queues/backpressure
- Health reporting

Flow:

1. Validate.
2. Persist intent.
3. Commit durable acceptance.
4. Send live.
5. Retry from durable outbox.
6. Apply idempotently.
7. Record ack/final state.

Partial Paper-only or Velocity-only punishment state enters recovery/quarantine.

---

## 10. MariaDB model

MariaDB is authoritative. SQLite is local-test only.

Use Flyway/Liquibase.

Required tables include:

- players, player_names, player_sessions
- client_evidence_snapshots
- cases, case_evidence
- sanctions, sanction_events, sanction_links
- punishment_steps, punishment_overturn_requests
- staff_notes, warnings
- reports, report_messages, report_chat_snapshots, report_private_message_snapshots
- alt_relationships, alt_evidence, network_identity_tokens
- staff_sessions, staff_state_snapshots
- inventory_profiles, inventory_profile_revisions, inventory_operations, inventory_snapshots, inventory_pending_patches
- confiscated_asset_snapshots
- economy_operations
- market_compliance_cases
- reputation_blacklists
- staff_alerts
- discord_outbox, network_outbox, network_inbox
- migration_runs, migration_mappings, shadow_comparisons
- configuration_versions, audit_events, operation_leases, recovery_quarantine

Index UUIDs, current/lowercase/previous names, active sanctions, expirations, families, report states, due market reviews, unread alerts, alt relationships, network tokens, external LiteBans IDs, recovery state, and public website case lookup.

Prepared statements only. Bounded DB executors. No SQL on Bukkit main thread.

---

## 11. Operational modes

Implement:

- `BOOTSTRAP`
- `DEGRADED`
- `SHADOW_MIGRATION`
- `ACTIVE`
- `MAINTENANCE`
- `READ_ONLY_FAILURE`

BOOTSTRAP: generate/validate config, check schema, discover integrations, summarize health.

DEGRADED: retain safe reads/reload/verify, disable unsafe actions, explain missing dependency and reload/restart needs.

SHADOW_MIGRATION: LiteBans authoritative, EnthusiaStaff mirrors/compares, no enforcement.

ACTIVE: EnthusiaStaff authoritative.

MAINTENANCE: suppress restart alt evidence, freeze sensitive writes, support cutover.

READ_ONLY_FAILURE: preserve safe reads, block destructive work, surface recovery.

Dependency behavior:

- MariaDB down: no new punishment/destructive edit.
- Velocity down: no new network sanctions.
- RoseChat down: disable its moderation/channel features.
- Voice down: text mute may remain but voice enforcement is unavailable.
- Currency down: hide economy confiscation.
- Market down: block market enforcement confirmation.
- Polar down: disable Polar automation only.

---

## 12. Modular configuration and reload

Required layout:

```text
plugins/EnthusiaStaff/
  config.yml
  storage.yml
  messages.yml
  discord.yml
  website.yml
  vanish.yml
  staff-mode.yml
  staff-tools.yml
  inventory.yml
  alts.yml
  reports.yml
  automod.yml
  anticheat.yml
  market.yml
  reputation.yml
  escalation.yml
  migration.yml
  integrations.yml
  servers.yml
  gui/
    punish-categories.yml
    punish-reasons.yml
    punish-review.yml
    punishment-history.yml
    remove-punishment.yml
    reports.yml
    report-details.yml
    player-inspector.yml
    staff-tools.yml
    cheat-testers.yml
    alts.yml
  punishments/
    hate-harassment-safety.yml
    spam-noise-language.yml
    inappropriate-content.yml
    politics-irl.yml
    account-security.yml
    complicity-evasion.yml
    exploits.yml
    market.yml
    reputation.yml
    mods-clients.yml
    reports-tickets.yml
    other-extreme.yml
```

Requirements:

- Comments/examples
- Stable IDs
- `6h`, `21d`, `90d`, `permanent` syntax
- File/path-aware validation
- Immutable runtime models
- Config versions and aliases
- Explicit restart-required options
- Atomic reload

Reload reads a temporary tree, validates every file/cross-reference/ladder/alias/GUI slot/permission, rejects the entire invalid reload, and atomically swaps only a valid model. Active sanctions, drafts, sessions, reports, locks, and journals survive reload.

Provide `/estaff reload`.

---

## 13. Identity and offline players

UUID is authoritative.

Support current names, previous names, lowercase lookup, Java, Bedrock, `*` aliases, offline previously joined players, and bounded prefix/fuzzy matching.

All player-target tab completion must use an in-memory index rather than MariaDB per keystroke. Online/recent players rank first.

---

## 14. Case and sanction model

A case contains:

- Case ID
- Target UUID
- Staff/system actor
- Broad public reason and exact stable reason ID
- Internal explanation
- Ruleset/config version
- Evidence and linked reports
- Visibility
- Related-family history
- Applied sanctions
- Confiscation, market, reputation, and inherited-alt actions
- Appeal state
- Audit history

Sanctions include warning, kick, text/voice mute, network ban, IP/network identity ban, report restriction, reputation/market blacklist, inventory/Ender/economy confiscation, content removal, stall removal, permanent mute, and permanent ban.

Combined sanctions start together. Example: 30-day mute + 7-day ban leaves 23 days of mute when the player returns.

Punishments and warnings are public by default; authorized staff may make eligible cases private.

---

## 15. Punishment GUI and durable drafts

Flow:

1. `/punish <player>`
2. Category GUI
3. Exact reason GUI
4. Review/action GUI
5. Confirmation

Category GUI shows identity, totals, active sanctions, warnings, related-family counts, last punishment, linked reports, category icons, and controls.

Reason GUI shows display name/examples, exact and related history, decayed history, next punishment, full ladder, and highlighted step.

Colors:

- Green contributing history
- Aqua decayed
- Gold recommended step
- Gray future
- Red permanent
- Purple override
- Dark red extreme/zero-tolerance

Review shows target, broad/exact reason, recommendation and explanation, history, recency/decay, public toggle, internal note, only relevant inventory/Ender/economy/IP/market/reputation actions, Confirm, and Back.

Politics must not show economy confiscation. Duplication may show inventory, Ender, and economy actions.

Persist drafts in MariaDB. Closing offers clickable resume and `/punish resume <player>`. Drafts survive crash/restart/server switch/logout. Default lifetime 24 hours.

Direct `/ban`, `/mute`, `/warn`, `/kick`, and `/ipban` still use the central case/policy system.

---

## 16. Escalation

Each exact reason belongs to a family and ladder.

Defaults:

- Less-serious related offense: +1 step
- Equal-severity related offense: +1
- More-serious related offense: +2
- Reoffense within 30 days after prior punishment ends: +1
- Configured decay may reduce contribution

Minor/warning decay defaults to one step after 90 clean days and one more each additional 90 days. Serious offenses normally do not decay. History remains visible.

Existing active punishments retain original type/expiration and snapshotted expectation when ladders change. Stored ordinals remain; future cases use the new ladder at that ordinal. Out-of-range ordinals use the final step. Removed IDs remain readable but unselectable. Renames require explicit aliases. Finite ladders repeat the final step unless permanent.

Every reason defines stable ID, name, examples, family, severity, ladder, decay, public default, reportability, confiscation options, required rank, automatic-detection eligibility, and alt inheritance.

---

## 17. Rank authority

Authorization is enforced in authoritative services, not only GUI/commands. Feature permissions do not silently become rank identity, and Developer is never treated as Mod or higher.

### Helper

Helper is the trial period before Mod and must remain tightly restricted.

May investigate reports, freeze when authorized, use staff chat, use restricted staff mode and vanish, inspect player information, view inventories and Ender chests read-only, and apply configured temporary punishment steps.

A configured result containing any permanent sanction must become a durable approval request rather than applying immediately. Helper cannot approve requests, raise/lower recommendations, use custom durations or combinations, edit or confiscate items/economy, remove or overturn punishments, persist client evidence, use owner/recovery controls, receive creative, give items, or move/take/receive items through staff tools, inventories, or Ender chests.

### Mod

May apply configured steps, including configured permanent steps; approve or deny Helper/Developer punishment requests; lower recommended punishment; end/revoke while preserving history; remove escalation contribution when configured; request full overturn.

Cannot raise above recommendation, create arbitrary sanctions/combinations, or directly fully overturn.

### Developer

Developer is development staff, not moderation staff and not part of the Mod-or-higher approval hierarchy.

May retain read-only cases/history/reports/diagnostics and non-punishment staff/development tools. Developer may prepare and submit a punishment request for authorized Mod/Admin/Founder review, but the request itself changes no punishment.

May not directly create, confirm, apply, approve, raise, lower, end, revoke, remove, unban, unmute, remove warnings, change punishment visibility, request overturn, approve/deny overturn, or otherwise mutate punishments through commands, GUIs, APIs, website, or integrations.

Historical Developer-issued cases remain valid and preserve original actor/rank.

### Admin

May apply configured punishments, raise/lower, use custom durations with configured types, fully overturn, approve/deny punishment and overturn requests, and reopen appeals as configured.

### Founder/Owner

May use unrestricted configured/custom combinations, fully overturn, approve/deny, use recovery tools, and perform emergency cutover controls.

---

## 18. Removal and overturns

Support `/history`, `/removepunishment`, `/unban`, `/unmute`, `/removewarning`, `/unwarn`.

Every removal identifies the exact case/sanction, reauthorizes, preserves history, appends audit, handles combined sanctions, is idempotent, survives retry/restart, and does not change unrelated sanctions.

Overturn request:

1. Mod requests with written explanation.
2. One open request per punishment.
3. Discord + Admin/Founder notification.
4. Offline approvers receive unread alert.
5. Punishment remains unchanged until approval.
6. Request expires after 7 days but remains audited.

Developer cannot request overturn.

---

## 19. Reports

Commands: `/report <player>`, `/reports`.

Rules:

- Known player; offline previously joined allowed
- No self-report
- Reporter identity staff-only
- Target not notified and cannot see internal status

Defaults:

- Any report cooldown 2 minutes
- Same target 30 minutes
- Same target/reason 2 hours
- Max open reports 5
- Merge near-duplicates

GUI sections: Open, Claimed by me, All claimed, Awaiting review, Recently closed.

Details:

- Reporter/target
- Reason/description
- Server/world
- Reporter/target coordinates
- Timestamp
- Java/Bedrock
- Protocol/version
- Reported client brand
- Floodgate/Geyser
- AutoClicker handshake
- Public chat snapshot
- Relevant private-message snapshot
- Related reports/history

Actions: Claim, Spectate, Teleport, Freeze, Punish, Close, No violation.

Retain moderation chat/private-message logs 7 days and capture previous 15 minutes for reports. Private-message evidence remains in staff system and is not sent to Discord.

---

## 20. Client evidence and strict automod

Use ViaVersion, Floodgate, Paper client brand, Polar metadata, and AutoClicker handshake.

Show Java/Bedrock, version/protocol, reported spoofable brand, Geyser/Floodgate, and approved AutoClicker version.

Do not keep unnecessary long-term history. Save point-in-time evidence only for reports, cases, or explicit staff capture.

Strict slur detection:

- Runs before RoseChat broadcast
- Cancels before ordinary recipients
- Creates case/punishment/evidence/audit/Discord entry
- Uses strict normalized variants
- Avoids broad fuzzy matching and false positives
- Reloadable and testable

Private messages remain report-driven unless a separately configured exact high-confidence rule applies.

---

## 21. Inventory and Ender chest editing

Commands: `/invsee`, `/endersee`.

Support online/offline, armor, offhand, shulkers, bundles, HUB/SMP scopes, add/remove/rearrange. Target not notified; every edit audited.

Online:

- Player inventory authoritative
- Exact main-thread slot mutation
- Fingerprints/revisions
- Dirty-slot updates
- Multiple staff viewers
- One coordinator per target
- Viewer synchronization
- Closing stops observation; no stale full-clone save

Silent container mirrors update live, use bounded reconciliation only while viewed, support nested containers, respect vanish, and produce no lid animation/sound.

Offline direct editing only when offline network-wide, owner server/scope known, exclusive lease acquired, not saving, revision current, and before snapshot saved.

Flow: read/validate, snapshot, temporary write, flush, atomic replace, reread/verify, commit revision, release lease.

Use queued patch fallback for login, locks, unavailable server, active save, changed revision, uncertain ownership, or unprovable safety. Apply before player interaction. Never let stale snapshots overwrite newer state. Keep snapshots 30 days.

---

## 22. Item confiscation

Separate from ordinary inventory editing.

Acquire network asset lock; close interfaces; block inventory movement, drop/pickup, crafting/equipping, Ender use, currency transfers, and unsafe server switch. Movement remains allowed unless separately frozen.

Normal click opens shulker/bundle; shift-click selects entire container. Selected items display as red glass panes. Track exact nested paths/fingerprints. Stale selections require reselection.

States: `PREPARED`, `LOCKED`, `SNAPSHOT_SAVED`, `VALIDATED`, `COMMITTED`, `UNLOCKED`.

Startup recovery must finish, roll back, or quarantine. Delete items only after durable snapshot.

Provide `/case restoreitems <case-id>`, idempotent and dupe-safe.

---

## 23. Economy confiscation and Currency API

Allow full personal balance or custom amount. Personal total may include bank, physical currency, inventory, Ender, shulkers, and bundles. Market/shop containers require explicit handling.

Flow: calculate total, reject over-removal, build exact plan, acquire locks, save before snapshot, apply, verify final total, commit audit, rollback/quarantine on failure. Removal order configurable.

Recreate EnthusiaCurrency API with durable account snapshots, exact before/after, validated plans, idempotency keys, duplicate replay, conflict detection, safe removal/restoration, offline support, clear result states, and tests. Do not bypass Currency via raw DB writes.

---

## 24. Staff mode

`/staff`.

Entry: CombatLogX check, durable full snapshot, verify commit, clear normal state, apply rank-specific staff state, mark active.

Snapshot inventory, armor, offhand, XP, health, hunger, saturation, effects, location, server, game mode, flight, metadata, checksum, revision.

Exit removes all staff items, restores exact state/location/server where safe, verifies, closes session.

Crash/reconnect resumes until normal exit, preserves original snapshot, resumes vanish, and prevents staff-item leakage.

Staff-mode/vanished players cannot be combat tagged or tag others.

Rank profiles:

- Helper: spectator only; no creative; no item pickup/drop/swap/movement; no inventory or Ender mutation; no giving, receiving, taking, or moving player items; no advanced cheat/client-evidence or recovery tools.
- Mod: no creative; Ender unavailable; only configured moderation tools.
- Developer: no creative; Ender unavailable; technical tools remain available but no direct punishment authority.
- Admin: creative allowed; Ender view-only unless separately authorized by a destructive workflow.
- Founder: creative and normal configured owner access.

An active staff session must reject or immediately correct any game-mode or inventory transition that exceeds the rank profile, even when another permission plugin accidentally grants the underlying vanilla command.

---

## 25. Vanish

Separate from staff mode.

Helper/Mod/Developer require staff mode. Admin/Founder may vanish independently.

Default visibility: Helper sees Helper; Mod/Developer see Helper/Mod/Developer; Admin sees Helper/Mod/Developer/Admin but not Founder; Founder sees all. Configurable, but supervising ranks must never lose visibility of vanished Helpers during configuration migration.

Central `StaffVisibilityService` must cover tab, join/quit, player counts, `/seen`, teleport/message/pay completion/notifications, playtime, RoseChat, voice, sounds, particles, chest animations, entity tracking, and public APIs. Bukkit `hidePlayer` is only one layer.

Spectator/tab requirements:

- Developer, Admin, and Founder/Owner entering spectator are immediately removed from every viewer's tab list.
- They receive a clickable chat choice to enter full vanish or appear normally on tab while remaining actually in spectator.
- A staff member who appears on tab while actually spectating must be packet-presented as a normal non-spectator entry, using a normal game-mode value and preserving profile, display name, latency, hat, list order, and chat-session data.
- No listed staff entry may expose spectator game mode, gray spectator styling, spectator sorting, or other player-info metadata that reveals spectator state.
- Helper and Mod staff-mode spectator entries must also be masked when listed; no actual spectator entry may be exposed on tab.
- ProtocolLib player-info filtering must remove unauthorized vanished entries and mask listed spectator entries. If ProtocolLib is absent, incompatible, or the packet adapter fails, spectator staff remain unlisted and the appear-normally option is disabled fail-closed.
- Leaving spectator restores normal tab handling unless full vanish remains active. Disabling full vanish while still spectating returns the player to the hidden choice state.

Full vanish must suppress unauthorized player-info, entity spawn/tracking, metadata, equipment, join/quit, and integration exposure so ordinary clients and spectator-detection mods cannot determine that staff are watching. Authorized staff visibility remains rank-aware.

Visibility updates should be incremental per changed viewer/target pair; full O(N²) reconciliation is startup/recovery fallback only.

---

## 26. Freeze

`/freeze <player>`.

Frozen players cannot move, take damage, move inventory, keep GUIs open, open containers, drop/pick up/use items, break/place, teleport, switch backend, run commands, or interact with world.

They see their own chat normally; staff receive it; ordinary players do not; player is not told it is staff-only.

Reconnect within 10 minutes restores freeze. Default offline expiration 10 minutes. Staff may unfreeze next login or extend. Persist through restart.

---

## 27. Staff tools, cheat testers, fake systems

Hotbar:

1. Random teleport
2. Player Inspector
3. Freeze
4. Reports
5. Cheat Tester
6. Follow/Spectate
7. Vanish
8. Staff Chat
9. Tools menu

Additional inventory/Ender viewers, fake entity/base, exit staff mode, configured utilities.

Random teleport excludes self, invisible staff, exempt players, disabled worlds/servers, unsuitable states.

Inspector includes identity, server/world, Java/Bedrock, protocol/version, reported brand, Floodgate/Geyser, AutoClicker, punishments/history/reports/warnings/alts, inventory/Ender, freeze/spectate/teleport, Market, Reputation, Economy. Add `/client <player>`.

Cheat Tester controls: right-click choose, left-click player run, shift-right-click configure.

Release testers: Totem refill, No-fall, Velocity/anti-knockback, Auto-armor. Evidence only, no automatic punishment. All tests snapshot and restore exact state, record latency/TPS/geometry/effects as relevant, and journal temporary item/state changes so crashes do not consume or duplicate assets.

Fake base uses virtual blocks/schematic only, visible only to suspect/authorized staff, no real world changes, clears on distance/world/server/disconnect/5 minutes, warns at 4 minutes, and offers Extend/Clear/Teleport.

Fake entity is target/staff-only, nonpersistent, records aim/interaction, and is tested for Java/Bedrock/Geyser.

---

## 28. Alts and network identity

Alts are allowed. Track relationships indefinitely. Staff never see raw IPs.

Use authenticated encryption for recoverable sensitive data, HMAC equality tokens, key rotation, and no raw addresses in logs/Discord/GUI/site/API/exceptions.

States:

- `SAME_NETWORK`
- `LOW_CONFIDENCE`
- `SEMI_CONFIDENT`
- `CONFIDENT`
- `VERY_CONFIDENT`
- `CONFIRMED_ALT`
- `APPROVED_ALT`
- `SHARED_HOUSEHOLD`
- `NOT_RELATED`

Evidence may include network, switching, timing, simultaneous play, independent behavior, long-term changes, client metadata, and staff confirmation. Genuine independent simultaneous play should reduce same-person confidence. Suggested network-change decay requires at least 5 genuine sessions, 14 days, consistent new network, gameplay, and no recent old-network evidence.

Suppress switching evidence during restart/maintenance/startup/mass reconnect events.

Inheritance:

- New same-network account with no established exception inherits exact remaining active ban/mute and links original case.
- `CONFIDENT`, `VERY_CONFIDENT`, `CONFIRMED_ALT` inherit active bans/mutes.
- Lower confidence alerts only.
- `SHARED_HOUSEHOLD`, `NOT_RELATED` do not inherit.
- `APPROVED_ALT` remains recognized but not suspicious.
- Separate evasion punishment only for intentional evasion.

Commands: `/alts`, `/alt link`, `/alt approve`, `/alt household`, `/alt notrelated`, `/alt unlink`, `/alt reopen`.

`NOT_RELATED` permanent until Admin/Founder reopens. Persist unread staff alerts.

---

## 29. Related plugin API reconstruction

Recreate lost branches from EnthusiaStaff contracts/adapters.

### EnthusiaCommend

Persistent blacklist API: `isReputationBlacklisted`, `blacklist`, `removeBlacklist`, `canGiveReputation`, `getBlacklist`. Applies to qualifying alts, blocks giving only, not receiving/viewing/existing score. Enforce GUI, command, API. Restart/reload safe.

### EnthusiaAutoClicker

Versioned evidence API: handshake observed, client/mod version, protocol/evidence version, timestamp, validation state, bounded evidence, safe unknown/unavailable, offline lookup where supported, no secrets.

### Enthusia-RoseChat

Stable API: current/set channel, staff/global, public/private classification, pre-broadcast moderation, private-message report capture, join/quit rendering, vanish recipients, mute enforcement, staff-only frozen chat, staff-chat toggle, reload-safe registration. Preserve existing formatting/channel behavior and attribution.

### EnthusiaMarket

Stable moderation API: find/snapshot player stall, read/remove ownership, mark overdue/unowned, preserve contents through existing overdue behavior, apply/remove blacklist, acquisition permission, blacklist state, case link, idempotent offline/restart-safe operations. Never bypass escrow/transactions.

Seven-day compliance timer persists, alerts in-game/Discord, requires human review, and does not remove ownership solely because time elapsed. Recovery snapshot 30 days.

---

## 30. Other integrations

Simple Voice Chat: muted users cannot transmit; vanished staff hidden from ordinary players; recipients respect visibility; supported API and temporary state; verification reports unavailable enforcement.

RoseChat: vanish-aware join/quit/recipients, staff/global toggle, pre-broadcast moderation, PM evidence, frozen staff-only chat, mute enforcement.

Via/Floodgate/Geyser: correct Java/Bedrock detection, protocol evidence, `*` aliases, Bedrock-compatible GUIs/tools where possible, explicit fake/visibility testing.

CombatLogX: staff mode entry blocked in combat; staff-mode/vanished cannot tag/be tagged.

Optional integration unavailable: disable only affected feature, expose precise verify output, document missing API/dependency, continue unrelated work, never invent behavior.

---

## 31. Polar

Version 1.7.11-beta, command namespace `/enthusia`.

Private `libs/private/PolarLoader.jar` may be compile-only. Never commit/shade/publish/decompile unrelated internals.

Capture available check name/family, violation, metadata, player version, brand, timestamp. Normalize changing names into stable families.

Independent family ladder: `30d -> 30d -> 60d -> 90d -> 90d repeatedly`.

Polar ban requests apply immediately through central punishment service. Punishments remain reducible/revocable/appealable/overturnable by authorized staff.

`/estaff verify polar` reports load/API/check names/violation values/event reception/punishment readiness. Never invent missing metadata.

---

## 32. Discord

Four configurable destinations: punishments, reports, logs-staffmode, alerts.

Punishments: bans, mutes, warnings, reductions, revocations, unbans/unmutes, automod, Polar.

Reports: new/claimed/closed/no violation.

Staff logs: staff mode, vanish, inventory/Ender edits, confiscation, economy, cheat tests, freeze.

Alerts: alt/inheritance/evasion, market review, overturn, integration/Discord failure, migration mismatch.

Use durable outbox, exponential backoff, max attempts, circuit breaker/failure threshold, preserve events in MariaDB, stop hammering unhealthy webhooks, alert staff on login, manual retry/status, future-bot adapter. DiscordSRV is optional.

---

## 33. Website

Repository `wsg138/enthusia-site`, based on `punishments-Page`, separate feature branch, private/unpublished.

Pages: all punishments, active bans/mutes, warnings, search, player history, case details, appeal, account, staff review, privacy/errors.

Search current/previous username and case ID.

Public fields: player, type, broad/exact public reason, issue/expiration/remaining duration, state, case ID, appeal availability.

Never expose network identity, alt evidence, reporter, PMs, coordinates, internal notes, confiscation, sensitive automation.

MariaDB remains punishment authority. Hyperdrive uses sanitized views/restricted API. D1 stores accounts/email/sessions/code bindings/appeals/staff actions/rate limits/security events. R2 private media. Pages/Workers frontend/API. Turnstile. Resend/Brevo. Mature auth/password libraries only.

Account flow: register, Turnstile, verify email, activate, login, enter code, validate eligible punishment, bind account, revalidate on login/appeal.

Punishment codes are random, hashed, first-claim bound, rotatable/revocable, revalidated, and supported for active migrated LiteBans bans/mutes.

One appeal per punishment. Player edits until claimed; claimed is read-only. Staff accepts/denies; Admin/Founder may reopen. Appeal never directly changes punishment; accepted appeal invokes normal removal service. Email status changes.

Appeal fields: username, reason, cheat removal status, why deserve unban/unmute, previous bans/mutes, evidence, issuing staff, optional media. No screenshot requirement.

Media private/signed/staff-only; sniff types; configurable limits; reject HTML/scripts/SVG/executables; strip metadata where practical; rate limit; orphan cleanup; audit.

Imported cases become public only in public mode. Fully overturned hidden; revoked `Revoked`; expired `Expired`; private stays private. Developers are punishment read-only on the site.

---

## 34. LiteBans migration

Import bans, IP bans, mutes, active/expired state, reasons, staff names, issue/expiration, UUID/name. Kicks/warnings/notes are not normal public imported history, but skipped counts may be audited.

Provide schema inspection, dry run, mapping/conflict/count/checksum/active/expiration reports, duplicate detection, resume, idempotent rerun, reconciliation, rollback plan, and final cutover report.

Preserve external IDs, use durable mappings/protected network tokens, never raw addresses, lock concurrent migrations, use repeatable-read source snapshot, recover abandoned runs, avoid duplicate cases/events, reject ambiguous IP ownership.

Shadow duration exactly 168 hours by default. LiteBans remains authoritative; EnthusiaStaff mirrors read-only, calculates expected login/chat decisions, compares, records disagreements, includes new punishments, produces daily summaries, never enforces or writes LiteBans.

Founder override may waive only time/cadence, never mismatch/recovery/final-import/write-fence blockers.

Cutover: maintenance, freeze writers, final import, compare counts/active/UUID/expiration/login/mute/IP decisions, block mismatches, transactional ACTIVE.

Before activation, abort returns to a fresh shadow window. After activation, emergency freeze enters READ_ONLY_FAILURE; do not auto-switch back to LiteBans without reconciling post-cutover sanctions.

Old jars stay during shadow.

Remove after successful cutover:

SMP: LiteBans, staffplusplus-core, staffplusplus-discord, Punishments, TigerReportsSupports.

HUB: LiteBans, PremiumVanish, wStaff, Punishments, TigerReports, TigerReportsSupports.

Velocity: LiteBans.

Keep RoseChat, Voice, CombatLogX, Polar/PolarLogs, InventoryRollbackPlus, Via/ViaBackwards, Floodgate/Geyser, ProtocolLib.

Verifier reports duplicate Skript/ViaVersion/Plan jars but never deletes.

---

## 35. Commands and verification

Commands:

`/punish`, `/ban`, `/mute`, `/warn`, `/kick`, `/ipban`, `/history`, `/removepunishment`, `/unban`, `/unmute`, `/removewarning`, `/unwarn`, `/report`, `/reports`, `/inspect`, `/invsee`, `/endersee`, `/alts`, `/alt`, `/client`, `/staff`, `/vanish`, `/staffchat`, `/freeze`, `/fakebase`, `/case restoreitems`, `/estaff`.

Register namespaced fallbacks. Command verifier inspects Paper/Velocity registries, identifies owning plugin/conflict severity/fallback, keeps plugin safe, never silently steals or executes destructive commands for testing.

`/estaff verify full` checks DB/schema, Velocity, HUB/SMP/future backends, RoseChat, CombatLogX, Voice, Via, Floodgate/Geyser, Polar, Currency/Market/Reputation/AutoClicker APIs, Discord, website config, inventory scopes/data paths, recovery workers, command ownership, duplicates, migration/shadow, config, secrets, file permissions, and duplicate API classes in runtime jars.

Statuses: PASS, WARNING, DISABLED, RESTART REQUIRED, CRITICAL. Normal output concise; debug may add stacks/timings/integration/registry/recovery details.

---

## 36. Security, performance, testing, Codacy, CI

Security: parameterized SQL, service-boundary authorization, signed/replay-protected interserver messages, encrypted/HMAC network identity, secrets outside Git, escaped output, CSRF, secure cookies/session expiry, rate limits, Turnstile, email/reset security, no open redirects/path traversal, strict uploads/type sniffing/signed media, safe YAML/deserialization, Polar sanitization, sanitized public DB access, append-only audit, dependency/secret scanning.

Threat model: privilege abuse, compromised staff, inventory dupes/stale overwrite, economy over-removal, DB tampering, website takeover, code theft, appeal spam/uploads, identity leakage, webhook compromise, interserver spoofing, migration corruption, alt false positives, restart races, staff-item leakage.

Performance: no main-thread DB/network, bounded scans/queues/caches, dirty GUI updates, incremental alts, indexed queries, prepared/batched writes, backpressure/circuit breakers, metrics for punishment/login/mute/inventory/GUI/alt/report/Discord/DB/network delays.

Tests: escalation/history/ladder/decay/permanent/combined/reduction/revocation/overturn, all rank boundaries including restricted Helper and Developer request/direct-issue/approval denial, spectator tab masking and ProtocolLib fail-closed behavior, alt inheritance/exceptions/restart suppression, online/offline inventory races/atomic writes/queued patches, confiscation/economy recovery, staff crash/Ender restrictions/freeze reconnect, Discord circuit breaker, LiteBans dry run/idempotency/shadow mismatch/cutover, website auth/code/appeal/media/sanitization, duplicate/partial/database/network failures.

Use unit, integration, MariaDB Testcontainers, MockBukkit/suitable Paper tools, Velocity abstractions, property/concurrency/failure-injection/mutation tests.

Coverage: critical 80% line/70% branch; overall Java 70% line/60% branch; no trivial getter tests.

CI: Java 21 build, unit/integration, coverage, static analysis, dependency/secret scans, website tests/build, config validation, migration tests, MariaDB service/Testcontainers, no production secrets.

Codacy after every push: inspect actual feature branch and PR, record grade/issues, resolve all first-party findings, rerun tests. Target A and zero unresolved issues. Do not disable tools, exclude first-party source, blanket suppress, lower thresholds, or game metrics. Narrow documented suppression only for proven false positives.

Refactor complexity by extracting real validation/planning, transactions/journals, Bukkit application, recovery/reconciliation, serialization/checksums, commands/presentation, adapters. Centralize real JDBC/row mapping/checksum/auth/recovery/idempotency/test-fixture duplication.

---

## 37. Documentation

Create:

- README
- architecture, security, threat model, database, configuration, commands, permissions, punishments, GUI flow
- inventory safety/offline inventories
- staff mode, vanish, reports, alts, integrations, Polar, Discord
- website, Cloudflare setup, appeals
- LiteBans migration, shadow, cutover, rollback
- troubleshooting, performance, development, ADRs
- UPGRADE-MANIFEST

README covers jars, dependencies, installation order, MariaDB, first startup, degraded mode, verify, shadow/cutover, reload, related plugin updates, website status, production restrictions.

Cloudflare docs cover Pages, Workers, Hyperdrive, D1, R2, Turnstile, auth library/provider, Resend/Brevo, restricted MariaDB user/views, env vars, local/private/public workflows, rollback/backups/security/cost expectations.

---

## 38. Acceptance tests

Before completion verify:

1. Java 21 build.
2. Exactly two jars.
3. HUB/SMP connect without online player.
4. Network ban/mute and voice mute.
5. Sanctions survive restart and reload.
6. Safe offline HUB/SMP edits and login fallback.
7. No item dupes/deletes under concurrency.
8. Live viewers and mirrors update.
9. Confiscation/economy locks and over-removal rejection.
10. Staff state/vanish crash recovery.
11. Helper/Mod/Developer vanish staff-mode requirement, rank visibility hierarchy, senior-staff spectator auto-unlisting, clickable vanish/normal-tab choice, creative tab masking, and fail-closed behavior when packet masking is unavailable.
12. Freeze restrictions/reconnect.
13. Reports capture 15-minute context, hidden reporter, coordinates, client stamp.
14. New same-network ban/mute inheritance, household exception, persistent NOT_RELATED.
15. Unread alerts, market timer, reputation blacklist.
16. Polar metadata/family ladders.
17. Strict slur cancellation without broad fuzzy matching.
18. Discord circuit breaker.
19. Command conflict verification.
20. 168-hour non-enforcing shadow and mismatch block.
21. Exact LiteBans expiration preservation.
22. Website build, registration, email, Turnstile, hashed/bound/revalidated code, one appeal, private media, correct public states.
23. No raw IP anywhere.
24. Recovery, permission-denied, duplicate, partial-operation, DB/network tests.
25. Docker-backed MariaDB tests actually run.
26. Runtime jars have no duplicate provider APIs.
27. Verified Codacy A and zero first-party issues.

---

## 39. Work order after current cleanup

1. Save clean Codacy-remediation checkpoint.
2. Complete unified workspace/manifests.
3. Update latest-main baselines and preserve feature work.
4. Recreate Currency, Commend, AutoClicker, RoseChat, Market, and website branches.
5. Fix contract packaging/classloader safety.
6. Continue persistence/migration/Velocity/channel/config/case/escalation.
7. Complete GUIs/drafts/removal/reports/alerts.
8. Complete online/offline inventory, confiscation, economy.
9. Complete staff mode/vanish/freeze/tools/testers/fakes/alts.
10. Complete Voice/Via/Floodgate/Polar/Discord/website.
11. Add CI, Docker tests, failure injection, static/Codacy final pass.
12. Perform staging/acceptance and finalize runbooks/manifests.

Do not stop after API reconstruction.

---

## 40. Session and final reporting

Every work session reports repositories/branches/commits/pushes/PRs, build/test/Docker/coverage/Codacy results, requirement status changes, unfinished work, blockers, missing secrets/files, and any unpushed work.

Scaffolding, placeholders, TODOs, unsupported branches, skipped tests, and unverified integrations remain incomplete.

Final report includes all PRs/branches/SHAs, tests/coverage/static/Codacy, limitations/secrets, jars to update/remove, install order, migration/shadow/cutover/rollback, and Cloudflare checklist.

Nothing is merged, published, deployed, or applied to production by the autonomous implementation work.
