# Discord Channel Mapping — Staff Plugin Logging

**Authoritative mapping of Staff plugin Discord outbox destinations to Discord channels.**
Last updated: 2026-10-03 (owner spec: rich punishment logging in #in-game-punishments)

## Destination → Channel Map

| Outbox Destination | Discord Channel | Status | Purpose |
|---|---|---|---|
| `punishments` | #in-game-punishments | ✅ ACTIVE | **Central punishment log.** Every punishment from every source with rich details. |
| `logs-staffmode` | #staff-logs | ✅ ACTIVE | Staff mode enter/exit, vanish changes. |
| `reports` | #reports | ⚠️ DEPRECATED | Owner authorized deletion/repurposing of #reports (stale since Sep 2025). Destination retained in code but should not be configured with a webhook. |
| `alerts` | *(unassigned)* | ❌ UNUSED | No producer writes to this destination. Reserved for future use. |

## What Gets Logged to #in-game-punishments

### Event: `PLAYER_FROZEN` / `PLAYER_UNFROZEN`
Fired for every freeze/unfreeze from **every** source (staff `/freeze` command, API, network
reconciliation). These are punishments: they land in #in-game-punishments with the target,
the acting staffer, and the freeze reason.

**Rich fields included:**
| Field | Description |
|---|---|
| `targetId` | Frozen player's UUID |
| `actorId` | Staff member who froze/unfroze |
| `reason` | Freeze reason |

### Event: `PUNISHMENT_CREATED`
Fired for every new punishment from **every** source:
- Staff commands (`/punish`, `/ban`, `/mute`, `/kick`, `/warn`, `/freeze`)
- RoseChat AI automated moderation (30-day public mutes)
- Any future automated source via `PunishmentService.create()`

**Rich fields included:**
| Field | Description |
|---|---|
| `caseId` | Unique case identifier |
| `targetId` | Punished player's UUID |
| `reasonId` | Configured reason ID (e.g., `chat.ai-moderation`) |
| `family` | Punishment family/category |
| `publicReason` | Human-readable public reason |
| `internalExplanation` | Full internal notes — **includes exact offending message for RoseChat mutes** (truncated to 1,000 chars) |
| `issuedAt` | Timestamp of issuance |
| `actorName` | Display name of issuer (staff name or "Enthusia AI Moderation") |
| `actorRank` | Rank of issuer (HELPER, MOD, ADMIN, FOUNDER, SYSTEM) |
| `sanctionTypes` | List of sanction types (e.g., `["PUBLIC_MUTE"]`) |
| `sanctionDetails` | Human-readable per-sanction details (e.g., `["PUBLIC_MUTE (30 days)"]`) |
| `sanctionIds` | Individual sanction UUIDs |

### Event: `SANCTION_CHANGED`
Fired when a sanction is modified after creation:
- Duration reduced (`REDUCE_DURATION`)
- Ended early (`END_EARLY`)
- Revoked (`REVOKE`)
- Fully overturned on appeal (`FULL_OVERTURN`)

**Fields:** `caseId`, `sanctionId`, `action`, `previousStatus`, `resultingStatus`,
`previousExpiration`, `resultingExpiration`, `reason`, `actorId`, `actorName`

### Event: `PUNISHMENT_REQUEST_*`
Helper punishment request workflow (not final punishments):
- `PUNISHMENT_REQUEST_SUBMITTED` — Helper submitted a request for Mod+ approval
- `PUNISHMENT_REQUEST_CLAIMED` — A Mod+ claimed the request for review
- `PUNISHMENT_REQUEST_APPROVED` / `DENIED` — Decision
- `PUNISHMENT_REQUEST_EXPIRED` — Timed out without review
- `PUNISHMENT_REQUEST_FULFILLED_EXTERNALLY` — Resolved outside the system

## What Gets Logged to #staff-logs

| Event | Producer | Fields |
|---|---|---|
| `STAFF_MODE_ENTERED` / `STAFF_MODE_EXITED` | JdbcStaffSessionStore | staffId, actorId, sessionId, rank, active, reason, serverId |
| `VANISH_CHANGED` | JdbcVanishStore | staffId, actorId, rank, vanished |

## Punishment Sources — Coverage Status

| Source | Integration | Logged? |
|---|---|---|
| Staff `/punish` command | PunishmentService | ✅ Yes |
| Staff `/ban` (Founder only) | PunishmentService | ✅ Yes |
| Staff `/mute`, `/kick`, `/warn` | PunishmentService | ✅ Yes |
| Staff `/freeze` | FreezeStore → punishments | ✅ Yes (in #in-game-punishments) |
| RoseChat AI moderation | RoseChatAutomatedModerationProvider → PunishmentService | ✅ Yes (with message content) |
| Polar anticheat | **No integration** | ❌ Not logged |
| Other automated systems | **No integration** | ❌ Not logged |

### Gap: Anticheat Punishments
Polar (anticheat) has no integration with the Staff plugin. When Polar issues
punishments, they are not logged to #in-game-punishments. Options:
1. **Recommended:** Add a webhook/API endpoint for external systems to report
   punishments (with check/flag details) into the Staff punishment pipeline.
2. Configure Polar to use Staff `/punish` commands (if supported).
3. Manual staff entry.

## Webhook Configuration

Each destination requires a `discord.<destination>.webhook-environment` property
pointing to an environment variable containing the Discord webhook URL:

```properties
discord.punishments.webhook-environment=DISCORD_PUNISHMENTS_WEBHOOK
discord.logs-staffmode.webhook-environment=DISCORD_STAFF_LOGS_WEBHOOK
# discord.reports.webhook-environment — DEPRECATED, do not configure
# discord.alerts.webhook-environment — UNUSED, do not configure
```

Webhooks are disabled by default. The plugin will not post to Discord until
webhook URLs are configured.
