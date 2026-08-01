# Architecture

EnthusiaStaff is a distributed moderation platform, not a single Bukkit command
plugin. It separates policy and durable state from Paper, Velocity, MariaDB,
Discord, the website and optional provider implementations.

## Where to continue

- Foundation percentages and important source files: [[Core Platform and Infrastructure]]
- Moderation feature paths: [[Moderation, Punishments, and Reports]]
- Stateful staff/asset paths: [[Staff Tools, Investigations, and Player-State Safety]]
- Provider/migration/release paths: [[Integrations, Migration, and Release Readiness]]
- Complete file-by-file map and traces: [[Developer Code Guide]]
- Paper–Velocity details: [[Protocol and Network Traffic]]

## Deployable artifacts

Exactly two runtime plugins are intended:

1. `EnthusiaStaff-Paper-<version>.jar`
2. `EnthusiaStaff-Velocity-<version>.jar`

Internal modules:

| Module | Responsibility |
| --- | --- |
| `common` | Shared identifiers, validation, security primitives and bounded utilities |
| `domain` | Business policy, authorization, state machines, application services and ports |
| `integration-contracts` | Compile-time contracts for optional Enthusia-owned providers |
| `persistence` | MariaDB bootstrap, migrations, JDBC stores, journals, leases and outboxes |
| `protocol` | Authenticated Paper–Velocity transport, replay protection and acknowledgements |
| `paper` | Commands, GUIs, listeners and server-local player-state behavior |
| `velocity` | Network enforcement, identity, workers, migration, Discord and website bridge |
| `integration-tests` | MariaDB/cross-module transaction, recovery and migration tests; never deployed |

See the [root build](https://github.com/wsg138/EnthusiaStaff/blob/main/build.gradle.kts)
and [module settings](https://github.com/wsg138/EnthusiaStaff/blob/main/settings.gradle.kts).

## Dependency direction

Policy must not depend on platform implementations:

```text
Paper / Velocity / website / provider adapters
                     |
                     v
          domain application services
                     |
                     v
              domain ports/models
                     ^
                     |
        persistence / protocol adapters
```

Commands and GUIs translate input and display results. They must not independently
implement punishment ladders, authorization, inventory transactions or recovery.

## Bounded contexts

- identity and player directory;
- cases, punishments, sanctions and escalation;
- reports and appeals;
- alts and protected network identity;
- inventory, economy, market and reputation;
- staff sessions, vanish, freeze and tools;
- Discord delivery;
- migration/shadow/cutover;
- verification, audit and configuration;
- external integrations.

Each bounded context should have one authoritative application-service path and
explicit ports to persistence or platform adapters.

## Paper runtime ownership

Paper owns:

- staff commands and GUIs;
- server-local player state;
- staff mode and vanish application;
- inventory/Ender access;
- freeze restrictions;
- report/client evidence capture;
- Bukkit-side provider adapters.

Important entry points:

- [Paper plugin](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java)
- [Runtime lifecycle](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeLifecycle.java)
- [Runtime components](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java)
- [Command registrar](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java)
- [Storage bindings](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperStorageBindings.java)
- [Integration manager](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperIntegrationManager.java)
- [Resource closer](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperResourceCloser.java)

MariaDB/provider work must remain off the game thread. Player/entity mutations
must run on the supported owning scheduler. Standalone Paper boot staging does not
prove complete Folia ownership behavior.

## Velocity runtime ownership

Velocity owns:

- login and server-switch enforcement;
- protected network-identity observations;
- persistent channel server;
- durable network and Discord workers;
- LiteBans migration, shadow and cutover coordination;
- restricted website/API bridge.

Important entry points:

- [Velocity plugin](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java)
- [Velocity configuration](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java)
- [Network worker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/NetworkOutboxWorker.java)
- [Discord worker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/DiscordOutboxWorker.java)
- [Website API server](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiServer.java)

Velocity event threads must not block on JDBC, HTTP, filesystem or socket work.

## MariaDB authority

MariaDB is authoritative for:

- cases and sanctions;
- identities and sessions;
- drafts and approval requests;
- reports and retained evidence;
- staff mode, vanish and freeze state;
- inventory/economy journals and queued patches;
- network/Discord inboxes and outboxes;
- migration runs/mappings/shadow comparisons;
- configuration versions, audit, leases and quarantine.

Primary entry points:

- [MariaDB bootstrap](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDb.java)
- [Runtime bindings](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDbRuntime.java)
- [Flyway migrations](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/resources/db/migration)
- [Persistence package](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence)

## Authoritative write flow

A destructive distributed operation should:

1. validate input and identity;
2. authorize inside the application service;
3. persist durable intent;
4. commit acceptance;
5. apply the local/provider/network effect;
6. verify resulting state;
7. commit terminal state and audit;
8. deliver network/Discord messages from durable outboxes;
9. retry idempotently or quarantine ambiguity.

Transport is at-least-once. Duplicate safety comes from idempotent consumers,
unique inbox/outbox keys, revisions, leases and fencing—not from claiming
exactly-once networking.

## Safe failure principles

- A punishment cannot partially apply one combined case.
- A sanction change cannot alter unrelated sanctions.
- Inventory/economy ambiguity preserves original state or enters quarantine.
- Stale state cannot overwrite newer state.
- Migration mismatch blocks cutover.
- Optional integration failure disables only dependent features.
- Success is reported only after durable commit/verification.

## Restricted website boundary

The Velocity website bridge is an inbound loopback-only boundary for a trusted
local site/reverse proxy. It must not be exposed directly to an untrusted network.
Requests require bearer/HMAC authentication, bounded timestamp and nonce replay
protection.

Important files:

- [Website API runtime](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRuntime.java)
- [Website API server](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiServer.java)
- [Request decoder](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRequestDecoder.java)
- [Router](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRouter.java)
- [Appeal endpoint](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteAppealEndpoint.java)

Only sanitized projections may cross this boundary.

## Stable internal services

Expected Bukkit-facing service boundaries include:

- `StaffVisibilityService`
- `PunishmentQueryService`
- `SanctionQueryService`
- `StaffSessionService`
- `StaffModeQueryService`
- `InventoryLockService`
- `AltRelationshipService`
- `PlayerDirectoryService`

Other plugins should depend on stable service interfaces rather than mutable
runtime internals.

## Review path

1. Read the matching group page for purpose/status/important files.
2. Read [[Developer Code Guide]] for the end-to-end trace.
3. Locate the domain service and authorization boundary.
4. Locate the port, migration and JDBC store.
5. Locate the Paper/Velocity/provider adapter.
6. Inspect duplicate, stale, failure, restart and concurrency tests.
7. Identify the real staging requirement automated tests cannot prove.

## Related pages

- [[Core Platform and Infrastructure]]
- [[Moderation, Punishments, and Reports]]
- [[Staff Tools, Investigations, and Player-State Safety]]
- [[Integrations, Migration, and Release Readiness]]
- [[Developer Code Guide]]
- [[Protocol and Network Traffic]]
- [[Build and Testing]]
