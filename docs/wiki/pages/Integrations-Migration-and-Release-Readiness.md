# Integrations, Migration, and Release Readiness

**Estimated group completion: about 36%.**

This group covers delivery outside the Paper plugin, optional provider APIs, the
private punishment/appeal site, LiteBans migration, shadow comparison, cutover,
platform acceptance, failure testing and the evidence required before production
authority can move.

- Return to [[Feature Completion Status|Implementation-Status]].
- Operator procedures: [[Installation]], [[LiteBans Migration]],
  [[Shadow Mode and Cutover]], and [[Recovery and Troubleshooting]].
- Provider behavior: [[Integrations]].
- Source-level traces: [[Developer Code Guide]].

> Percentages are rounded planning estimates. Exact evidence and blockers remain
> in the
> [requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md).

## Find an integration or release area

| Area | Complete | What it does | Jump to details |
| --- | ---: | --- | --- |
| Durable Discord delivery | **55%** | Persists staff events and retries webhook delivery without losing committed moderation work. | [Discord](#durable-discord-delivery) |
| Restricted website bridge | **65%** | Exposes a small authenticated loopback API for sanitized punishment and appeal operations. | [Website bridge](#restricted-website-bridge) |
| Public punishment projections and access codes | **70%** | Publishes safe fields and one-time access flows without exposing internal evidence. | [Public projections](#public-punishment-projections-and-access-codes) |
| Private punishment and appeal website | **15%** | Provides player lookup/appeal and authenticated staff review outside Minecraft. | [Private site](#private-punishment-and-appeal-website) |
| Enthusia-owned provider APIs | **20%** | Connects Currency, Commend, AutoClicker, RoseChat and Market through supported contracts. | [Providers](#enthusia-owned-provider-apis) |
| Optional third-party integrations | **35%** | Uses Voice, ViaVersion, Floodgate, CombatLogX, ProtocolLib, Polar and other plugins safely. | [Third-party integrations](#optional-third-party-integrations) |
| LiteBans schema inspection and import | **80%** | Discovers supported source variants and imports/matches source records idempotently. | [Migration import](#litebans-schema-inspection-and-import) |
| Shadow comparison | **75%** | Compares counts, checksums, identities, expirations and enforcement decisions while LiteBans remains authoritative. | [Shadow comparison](#shadow-comparison) |
| Cutover coordination and rollback | **45%** | Fences writers, changes authority, records transitions and supports emergency freeze/recovery. | [Cutover](#cutover-coordination-and-rollback) |
| Real-data rehearsal and 168-hour shadow | **10%** | Proves the migration and comparison model against production-like private data over seven days. | [Real-data proof](#real-data-rehearsal-and-168-hour-shadow) |
| Runtime and client acceptance | **20%** | Verifies HUB/SMP/Velocity, providers, Java, Bedrock/Geyser and Folia behavior together. | [Runtime acceptance](#runtime-and-client-acceptance) |
| Load, saturation and process-kill tests | **20%** | Proves bounded behavior and recovery under outage, queue pressure and abrupt termination. | [Failure tests](#load-saturation-and-process-kill-tests) |
| Release manifest and operational approval | **20%** | Binds exact repository revisions, artifacts, configs, environments and acceptance evidence. | [Release evidence](#release-manifest-and-operational-approval) |

## Durable Discord delivery

### What it does

A committed moderation action writes a Discord event into the durable outbox in
the same transaction. A Velocity worker leases, renders and sends the event with
bounded retry. Discord failure must not roll back a valid punishment or silently
lose the notification.

### Primary files

- [Discord domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/discord)
- [Discord outbox store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordOutboxStore.java)
- [Discord outbox worker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/DiscordOutboxWorker.java)
- [Velocity configuration](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java)

### What remains

Route every punishment, request, report, alert, recovery and migration event;
enforce producer-side privacy and mention safety; add circuit open/half-open
status, dead-letter/manual retry and live webhook outage testing.

## Restricted website bridge

### What it does

The Velocity website bridge is a loopback-only HTTP boundary for a trusted local
site process or reverse proxy. It authenticates bearer/HMAC requests, applies a
bounded timestamp and nonce replay window, rejects unknown input and returns
stable sanitized response envelopes.

### Primary files

- [Website API runtime](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRuntime.java)
- [Website API server](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiServer.java)
- [Request decoder](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRequestDecoder.java)
- [Website router](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRouter.java)
- [Website domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/website)
- [Website moderation store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcWebsiteModerationStore.java)

### What remains

Complete production authentication boundaries, secret rotation, overload and
rate behavior, private-site integration, operator status and full end-to-end
staging. The bridge must remain private; it is not a public internet server.

## Public punishment projections and access codes

### What it does

Public projections expose only fields approved for players: safe reason, sanction
type/status, dates, expiration and appeal availability. Access codes provide a
durable limited path from an in-game case to the correct private site record.

### Primary files

- [Website projection domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/website)
- [Website moderation store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcWebsiteModerationStore.java)
- [Public punishment registry](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcPublicPunishmentRegistry.java)
- [Punishment code store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcPunishmentCodeStore.java)

### What remains

Complete every visibility/sanction rule, code expiry and operator controls, then
verify exact site rendering without reporter identity, private messages,
coordinates, staff notes, alt evidence or confiscation details.

## Private punishment and appeal website

### What it does

The private site is intended to let players find their punishment, submit an
appeal and receive a decision. Authorized staff can review sanitized case data and
issue role-checked actions through the restricted bridge.

### Current state

The root contracts and Velocity bridge exist. The complete private site is not
present in this repository and must not be inferred from the bridge alone.

### Primary bridge files

- [Website appeal endpoint](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteAppealEndpoint.java)
- [Appeal store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcWebsiteAppealStore.java)
- [Website router](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRouter.java)

### What remains

Build authenticated sessions, CSRF protection, rate limits, restricted staff
roles, safe media storage, player/staff pages, decisions/reopening,
notifications, privacy controls and integration tests in the private site
repository.

## Enthusia-owned provider APIs

### What it does

Provider plugins remain authoritative for their own data. EnthusiaStaff supplies
or consumes stable contracts rather than issuing raw SQL or reflective calls.

| Provider | Moderation purpose | Current state |
| --- | --- | --- |
| EnthusiaCurrency | Exact economy snapshots, removal plans and restoration | Contract/gateway foundations only |
| EnthusiaCommend | Persistent reputation blacklist enforced at every write path | Contract defined; provider work incomplete |
| EnthusiaAutoClicker | Versioned bounded client handshake/evidence | Contract defined; provider work incomplete |
| Enthusia-RoseChat | Staff/global channels, mute/freeze, PM evidence, automod and vanish recipients | Required API unavailable/incomplete |
| EnthusiaMarket | Supported stall moderation, review and restoration | Contract boundary defined; provider work incomplete |

### Primary files and paths

- [Integration contracts](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-contracts/src/main/java)
- [Paper integration adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/integration)
- [Paper economy adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/economy)
- [Paper client adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/client)

### Related page

- [[Integrations]]

### What remains

Reconstruct each provider implementation, enforce its contract at every command,
GUI and API path, publish compatible API artifacts where appropriate, then stage
all providers together for classloader and degraded-mode behavior.

## Optional third-party integrations

### What it does

Optional integrations provide chat, voice, protocol, Bedrock, combat,
packet-level and anticheat capabilities. A missing provider must disable only its
dependent feature and explain the result through verification.

| Integration | Used for |
| --- | --- |
| Simple Voice Chat | Voice mute and vanish-aware voice recipients |
| ViaVersion/ViaBackwards | Protocol/version evidence |
| Floodgate/Geyser | Bedrock identity and compatibility |
| CombatLogX | Safe staff-mode combat gating |
| ProtocolLib | Supported player-info/entity packet filtering |
| Polar | Evidence and future supported automation |
| DiscordSRV/webhooks | Staff notifications |
| LuckPerms | Command discovery and rank permissions |
| EnthusiaTeleport/PlayTimePlugin | Vanish-aware external behavior |

### Primary paths

- [Paper integrations](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/integration)
- [Client integrations](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/client)
- [Visibility API](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/api/StaffVisibilityService.java)

### What remains

Verify supported API versions, event reception, classloaders, reload/restart
boundaries and isolated failure. Polar automatic punishment remains disabled until
a supported violation-event contract exists.

## LiteBans schema inspection and import

### What it does

Migration first inspects the LiteBans source schema, maps supported aliases and
reports explicit blockers. Import preserves external IDs, identities, sanctions,
expiration and mapping state so dry runs and reruns are idempotent.

### Primary files and paths

- [Migration domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/migration)
- [Persistence migration package](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence/migration)
- [LiteBans migration service search](https://github.com/wsg138/EnthusiaStaff/search?q=LiteBansMigrationService&type=code)
- [Migration integration tests](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-tests/src/test/java)

### Related page

- [[LiteBans Migration]]

### What remains

Validate additional real schema variants, production volume, interruption at
every stage, orphan mappings, conflict resolution, resume and rollback.

## Shadow comparison

### What it does

While LiteBans remains authoritative, EnthusiaStaff mirrors imported state and
compares:

- total and active counts;
- checksums;
- UUID mappings;
- expiration timestamps;
- ban, mute and IP/network enforcement decisions.

Every discrepancy must be stored and explained; comparison must not enforce the
EnthusiaStaff result during shadow mode.

### Primary paths

- [Migration domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/migration)
- [Migration persistence](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence/migration)
- [Velocity migration runtime](https://github.com/wsg138/EnthusiaStaff/tree/main/velocity/src/main/java/net/enthusia/staff/velocity)

### Related pages

- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]

### What remains

Run continuous real-data comparisons, operator mismatch workflows and seven valid
daily summaries spanning at least 168 hours.

## Cutover coordination and rollback

### What it does

Cutover coordination freezes sensitive writers, checks the final incremental
import and shadow evidence, rejects duplicate activation, records every authority
transition and supports emergency freeze or rollback when the outcome is unsafe.

### Current development

PR #37 contains focused cutover transition, final-import, duplicate activation,
shadow-window and emergency-freeze tests. It remains draft work until completed,
reviewed and merged.

### Primary paths

- [Migration domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/migration)
- [Migration persistence](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence/migration)
- [Velocity migration runtime](https://github.com/wsg138/EnthusiaStaff/tree/main/velocity/src/main/java/net/enthusia/staff/velocity)

### What remains

Finish and merge the coordinator, then prove writer fencing, final-import linkage,
restart resume, emergency freeze, founder override, ambiguous-outcome quarantine,
rollback and post-cutover reconciliation.

## Real-data rehearsal and 168-hour shadow

### What it does

Synthetic tests prove algorithms, but production authority requires private
production-like LiteBans data and the real observation environment. The final
shadow must produce seven valid daily summaries over at least 168 continuous
non-enforcing hours.

### Evidence required

- dry run and rerun results;
- interruption/resume evidence;
- final incremental import;
- all count/checksum/identity/expiration/decision comparisons;
- explanations for every mismatch;
- rollback and emergency-freeze rehearsal.

### Current state

The comparison dimensions exist, but the real-data rehearsal and mandatory
168-hour observation have not been completed.

## Runtime and client acceptance

### What it does

Full acceptance tests the exact release candidate in the real distributed
shape:

```text
Velocity
├── HUB + EnthusiaStaff-Paper
└── SMP + EnthusiaStaff-Paper
```

It also covers optional providers and supported Java/Bedrock clients.

### Required groups

- proxy login and server-switch enforcement;
- no-online-player transport;
- HUB/SMP ownership and distinct inventory scopes;
- complete staff, punishment, report, inventory and recovery workflows;
- Java supported versions;
- Bedrock/Geyser GUI, identity and packet behavior;
- Folia-compatible entity/player ownership;
- provider presence and isolated failure.

### Current state

Standalone Paper boot/restart staging exists. Complete Velocity, multi-backend,
provider, Bedrock and Folia acceptance does not.

## Load, saturation, and process-kill tests

### What it does

These tests prove that bounded executors, DB pools, network/Discord queues,
reconnect behavior and destructive journals remain safe when resources are
exhausted or the process terminates between workflow stages.

### Primary locations

- [Protocol](https://github.com/wsg138/EnthusiaStaff/tree/main/protocol)
- [Persistence](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence)
- [Integration tests](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-tests/src/test/java)
- [GitHub workflows](https://github.com/wsg138/EnthusiaStaff/tree/main/.github/workflows)

### What remains

Run queue saturation, DB pool exhaustion, reconnect storms, Discord outages,
report/GUI load and process termination during punishment, notification, asset
and migration operations. Verify exact recovery or visible quarantine.

## Release manifest and operational approval

### What it does

A release manifest prevents evidence from unrelated revisions from being combined
into a fictional release candidate. It must declare one authenticated revision
for EnthusiaStaff, each provider and the private site, plus:

- artifact hashes;
- configuration checksums;
- dependency and environment versions;
- database/migration state;
- all acceptance results;
- backups and rollback evidence;
- explicit authorization record.

### Primary documents

- [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)
- [Workspace manifest](https://github.com/wsg138/EnthusiaStaff/blob/main/WORKSPACE-MANIFEST.md)
- [Installation documentation](https://github.com/wsg138/EnthusiaStaff/tree/main/docs)
- [[Installation]]
- [[Shadow Mode and Cutover]]
- [[Recovery and Troubleshooting]]

### What remains

Produce and test one final manifest across every participating repository, run
clean install/upgrade/rollback drills, complete the 168-hour shadow, record
Founder authorization and observe production before retiring any legacy plugin.

## Related pages

- [[Feature Completion Status|Implementation-Status]]
- [[Remaining Development Map|Development-Blueprint]]
- [[Integrations]]
- [[Installation]]
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]
- [[Recovery and Troubleshooting]]
- [[Build and Testing]]
- [[Developer Code Guide]]
