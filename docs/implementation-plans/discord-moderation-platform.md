# Discord moderation platform implementation plan

Status: **planned only**. This document does not activate a worker package, authorize implementation, deploy a bot, modify production Discord, or change current package routing.

Starting architecture: `wsg138/EnthusiaStaff:main` at `ea689f1bcdf5a615789478db6e90735f6aecc3ab` when this plan was authored. Live GitHub state overrides this SHA when work actually begins.

Authoritative product contract: [`docs/discord-moderation-platform.md`](../discord-moderation-platform.md).

## Program principles

- Do not build a parallel moderation database.
- Settle identity, scope, authorization, and migration foundations before rich Discord UX.
- Keep the staff Discord runtime independent from Paper/Velocity lifecycle.
- Keep the public bot isolated from private moderation credentials/data.
- Preserve existing webhook delivery as a separate notification path.
- Build shadow/migration/reconciliation paths before Discord authority cutover.
- Every package gets its own branch/worker, focused review, exact-head validation, and normal merge. No package below is selectable merely because this file exists.

## Proposed worker packages

| ID | Package | Purpose | Depends on |
| --- | --- | --- | --- |
| `ES-D01` | Discord domain and identity contract | Person/subject model, Discord identity, one-to-many linking cardinality, historical links, main-account semantics, platform scope model, case lifecycle corrections | explicit owner activation |
| `ES-D02` | Discord persistence and migration schema | Forward-only MariaDB migrations/repositories for Discord identities, links/history, scoped enforcement, Discord evidence, security locks, reconciliation state, retention/expiry workers | `ES-D01` |
| `ES-D03` | Authorization and cross-platform policy | Discord-specific Helper/Mod/Developer/Admin rules, custom-duration constraints, permanent-action gates, cross-platform permission checks, self/hierarchy protection | `ES-D01`, `ES-D02` |
| `ES-D04` | Account linking and DiscordSRV migration | Two-direction five-minute code flow, legacy DiscordSRV pair import, main-account selection via PlayTimePlugin API, staff overrides, temporary DiscordSRV main-link mirroring | `ES-D01`–`ES-D03` |
| `ES-D05` | Staff bot runtime foundation | Separate Java 21 process, Gateway lifecycle, JDA/selected library integration, guild lock, intents, REST/rate-limit handling, secrets, health, graceful shutdown, no destructive commands yet | `ES-D01`–`ES-D03` |
| `ES-D06` | Read-only staff moderation UX | `/moderate`, context commands, `/linked`, `/history`, notes/cases views, target resolution, clean ephemeral panels, component replay protection | `ES-D04`, `ES-D05` |
| `ES-D07` | Discord punishment enforcement | Warn/mute/permanent mute/kick/temp+permanent ban/channel restrictions, managed mute/restriction roles, ticket access, temp expiry, DMs, native ban enforcement/reconciliation, quick commands | `ES-D03`, `ES-D05`, `ES-D06` |
| `ES-D08` | Cross-platform moderation integration | Explicit Discord/Minecraft/Both scope, Discord-to-Minecraft and Minecraft-to-Discord entry points, separate sanctions under one case, partial failure/recovery, plugin GUI/ladder changes | `ES-D07` plus required Minecraft authority readiness |
| `ES-D09` | Discord evidence, cases, notes and linked-alt alerts | Automatic message evidence/context, edit history, 30-day retention rules, 30-day inactive case closure, linked-alt/evasion alerts to Discord+Minecraft | `ES-D06`, `ES-D07` |
| `ES-D10` | AutoMod shadow engine | Local normalization/obfuscation/link/invite/spam/reaction logic, ticket/staff exemptions, OpenAI Moderation API signal, confidence model, shadow-only observations and false-positive review | `ES-D05`, `ES-D09` |
| `ES-D11` | AutoMod enforcement and security locks | Delete/warn/mute configured actions, staff action panels/overturn, compromised-account Security Lock, `/unlock`, known-malicious link handling, staff/profile/nickname behavior | accepted `ES-D10` shadow evidence |
| `ES-D12` | Staff website Discord expansion | Discord-aware search/history/evidence/configuration, website-only appeal integration, AutoMod configuration/audit, case ownership/related cases where required | `ES-D02`, `ES-D07`, `ES-D09` |
| `ES-D13` | Discord role-sync replacement | One-way Minecraft/Enthusia to Discord role reconciliation across all current linked Minecraft accounts; remove dependency on DiscordSRV role sync after parity validation | `ES-D04`, `ES-D05` |
| `ES-D14` | Public bot and sanitized public API | Separate installable app, `/player`, `/whois`, `/guild`, `/baltop`, `/playtime`, `/leaderboards`, `/store`, `/website`, `/discord`, `/rules`, `/ip`; no links/alts/private moderation data | sanitized API contracts ready |
| `ES-D15` | Discord migration/cutover acceptance | Import/reconcile native bans, DiscordSRV links, role-sync parity, AutoMod shadow acceptance, outage/restart/process-kill/rate-limit tests, public/private trust-boundary checks, production runbook | `ES-D01`–`ES-D14` as applicable |

## Recommended sequence

The sequence above is intentional. Identity/scope/schema/authorization are difficult to retrofit safely after UI work exists. The runtime should then prove read-only reliability before destructive Discord actions are enabled. Cross-platform work follows stable Discord-only enforcement. AutoMod is split into shadow and enforcement packages so a new detector cannot become authoritative merely because it compiles.

The public bot is late because it is independent of moderation correctness and should not distract from the staff system. It may be advanced earlier only if it does not consume or weaken moderation-critical work.

## Package boundaries

### ES-D01 / ES-D02

Decide and persist the distinction between a moderation subject/person and platform identities. Existing Minecraft UUID records remain valid; do not destructively reinterpret historical cases without a migration plan. Platform scope must not be encoded as a Cartesian explosion of sanction enum values.

### ES-D04

Do not read PlayTimePlugin's SQLite directly. Integrate through its public service/API. Legacy DiscordSRV links are imported, not discarded. A Minecraft UUID cannot have two current Discord owners.

### ES-D05

Prefer a maintained Java Discord library after a fresh implementation-time review. The runtime must be a real service process with reconnect/backoff, health, logging, rate-limit handling, bounded work, and staging/prod isolation. No bot token is committed.

### ES-D07

Normal mute uses managed role/permissions rather than native Discord Timeout so support/ticket channels remain usable. Native Discord ban remains the guild-ban side effect. Permanent Discord ban/mute/channel restriction require Admin+ under the approved policy.

### ES-D08

No command origin may silently imply `Both`. The origin platform is only the default. Final confirmation must display each consequence separately and the domain service must persist intent before external side effects.

### ES-D10 / ES-D11

OpenAI Moderation is a free contextual signal, not the full policy engine. AI-only ambiguity cannot produce severe punishment. Ticket/support channels are fully AutoMod-exempt. Shadow results must be reviewed before enforcement is enabled.

### ES-D15

Do not unban/reban users just to migrate. Compare current native Discord bans to imported Enthusia records and block cutover on unexplained mismatch. Existing native manual ban/unban paths must be observed/reconciled after cutover so authority cannot silently drift.

## Cross-repository work expected later

The program will likely touch:

- `wsg138/EnthusiaStaff` for domain, persistence, Paper/Velocity integration, staff runtime, configuration, tests and docs;
- the current Enthusia website component/repository for Discord-aware staff review and website-only appeals;
- `wsg138/PlayTimePlugin` only if its existing public API proves insufficient; no change is expected merely to read lifetime active playtime;
- DiscordSRV configuration/migration data for link and role-sync transition, without requiring DiscordSRV source changes;
- public data providers only through supported APIs/contracts for the public bot.

## Validation expectations

Each implementation package must have targeted unit/integration tests plus exact-head repository validation. Destructive Discord packages additionally require isolated staging guild/bot validation where production data/users are not affected. Final acceptance covers restart/outage, duplicate/replay, role hierarchy, permission denial, temporary expiry, link races, migration idempotency, retention cleanup, partial cross-platform failure, Discord rate limiting, and sanitized public-bot isolation.

No package completion changes LiteBans or production moderation authority unless the separate applicable cutover gates are explicitly accepted.
