# Core Platform and Infrastructure

This hub covers the foundation every other EnthusiaStaff feature depends on: runtime artifacts, module boundaries, lifecycle, MariaDB/Flyway, authenticated Paper–Velocity communication, authority bridges, configuration, identity, health and validation.

For Discord runtime/product behavior use [[Discord Moderation Platform]] and [[Staff Bot Runtime and Operations]]. For the public site and web APIs use [[Website and Web API]].

## Quick status

| Area | Merged-main state | Main limitation |
| --- | --- | --- |
| Java runtime artifacts/packaging | **Implemented** | Representative all-runtime/provider/release-candidate acceptance remains broader than artifact checks. |
| Module architecture | **Available with limitations** | Boundaries are explicit, but large coordinators still require disciplined review. |
| Paper lifecycle | **Implemented, not fully staging-verified** | Real Folia/provider/restart ownership needs representative validation. |
| Authority bridge runtime | **Implemented transition runtime** | Narrow migration/authority role; must not become a second full moderation implementation. |
| Velocity lifecycle | **Implemented foundations with remaining acceptance** | Distributed reload/outage/provider acceptance remains. |
| StaffBot lifecycle | **Implemented** | Production destructive authority remains separately gated/default-off. |
| MariaDB/Flyway | **Implemented through V20** | Production-like load/process-kill/multi-runtime acceptance remains. |
| Safe-write/recovery controls | **Partial by workflow** | High-risk external/player-state workflows still need complete interruption/recovery evidence. |
| Paper–Velocity protocol | **Implemented, not fully staging-verified** | Multi-backend reconnect/backpressure/certificate/outage acceptance remains. |
| Configuration/reload | **Partial platform-wide** | Paper, Velocity, StaffBot and web components have separate lifecycle/config boundaries. |
| Identity/player directory | **Implemented, not fully staging-verified** | Representative Geyser/Floodgate/client/multi-backend acceptance remains. |
| Build/quality gates | **Available with limitations** | Hosted checks do not replace runtime/production acceptance. |

## Runtime artifacts

The root `runtimeJars` task currently builds/verifies four Java runtime artifacts:

```text
EnthusiaStaff-Paper-<version>.jar
EnthusiaStaff-AuthorityBridge-<version>.jar
EnthusiaStaff-Velocity-<version>.jar
EnthusiaStaff-StaffBot-<version>.jar
```

Paper and Velocity are Minecraft runtimes. StaffBot is a standalone Java/JDA application. The authority bridge is a narrow transition runtime with explicit required/forbidden-content verification; it must not become an alternate general-purpose Paper implementation.

Web components are deployed separately:

- `components/enthusia-site/` — public site + Cloudflare Pages Functions;
- `moderation-web/` — staging-only Cloudflare moderation workspace.

Primary paths:

- [root build](https://github.com/wsg138/EnthusiaStaff/blob/main/build.gradle.kts)
- [module settings](https://github.com/wsg138/EnthusiaStaff/blob/main/settings.gradle.kts)
- [authority bridge build](https://github.com/wsg138/EnthusiaStaff/blob/main/paper-authority-bridge/build.gradle.kts)
- [StaffBot README](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/README.md)

## Module responsibilities

```text
common                 shared primitives/security/bounded utilities
domain                 business policy, authorization and ports
integration-contracts  compile-time contracts for Enthusia-owned providers
discord-platform-api   provider-neutral managed-role API
persistence            MariaDB/Flyway/JDBC stores/recovery
protocol               authenticated Paper-Velocity transport
paper                  Bukkit/Paper commands/UI/player-state adapters
paper-authority-bridge transition authority/migration runtime
velocity               proxy/network workers + website API
staff-bot              Discord JDA/runtime/moderation/read/effect services
integration-tests      validation only; never deployed
```

`integration-contracts` and `discord-platform-api` are contract boundaries, not second homes for moderation policy. The owning domain/application service remains authoritative.

See [[Architecture]] for the full dependency model.

## Paper lifecycle

Important composition paths:

- [Paper plugin](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java)
- [runtime lifecycle](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeLifecycle.java)
- [runtime components](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java)
- [storage bindings](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperStorageBindings.java)
- [command registrar](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java)

Database/provider/network work belongs off the game/entity thread. Player/entity mutation returns to the supported owning scheduler, and callbacks that can outlive a session require fencing.

## Paper authority bridge

`paper-authority-bridge` is built as a separate shaded Paper artifact for transition/authority needs. Its build explicitly verifies required runtime classes/resources and forbids selected full moderation/migration implementations from being pulled into the bridge.

Review it as a **narrow bridge**, not a place to duplicate Paper command/business logic. The root build verifies this containment as part of `runtimeJars`.

## Velocity lifecycle and website API

Velocity owns login/server-switch enforcement, protected network identity, Paper–Velocity transport workers, legacy Discord webhook delivery, migration/shadow coordination, and the authoritative website API server/router.

Primary paths:

- [Velocity plugin](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java)
- [Velocity configuration](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java)
- [network worker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/NetworkOutboxWorker.java)
- [legacy Discord worker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/DiscordOutboxWorker.java)
- [website API router](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRouter.java)

Velocity event threads must not block on JDBC/HTTP/filesystem/socket I/O.

## StaffBot lifecycle

StaffBot owns the privileged Discord Gateway/JDA lifecycle, staff moderation/read UX, Discord effect/reconciliation workers, signed browser-workspace launches, private moderation-read API and health/readiness.

Important paths:

- [StaffBotApplication](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotApplication.java)
- [StaffBotRuntime](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotRuntime.java)
- [JdaDiscordGateway](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaDiscordGateway.java)

The runtime uses bounded workers, explicit environment/application/guild fences and safe shutdown/reconnect behavior. Destructive Discord enforcement is explicitly gated and defaults off.

See [[Staff Bot Runtime and Operations]].

## MariaDB and Flyway

MariaDB is durable authority for moderation/recovery state. Current merged Flyway history reaches:

```text
V20__discord_account_linking.sql
```

Recent schema milestones:

- V17 — website appeal workflow;
- V18 — Cheat Tester session journal;
- V19 — Discord moderation persistence/reconciliation foundations;
- V20 — Discord/Minecraft account linking.

Applied migration history is forward-only/immutable. Later versions visible on open branches are not current `main`.

Primary paths:

- [MariaDb](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDb.java)
- [MariaDbRuntime](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDbRuntime.java)
- [persistence package](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence)
- [Flyway migrations](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/resources/db/migration)

## Safe-write and external-effect controls

High-risk workflows use combinations of idempotency keys, unique constraints, optimistic revisions, row locks, leases/fencing, before-state journals, durable inbox/outbox, bounded retry/backoff and quarantine/reconciliation.

Discord native effects need special care: an ambiguous remote result may not be safely blind-retried. StaffBot verifies/reconciles effects such as bans, kicks and managed roles rather than assuming “request sent” equals success.

## Paper–Velocity protocol

The protocol provides persistent authenticated communication without requiring an online player. It is at-least-once; effect-level safety comes from replay protection, durable inbox/outbox, idempotent handlers, ACK semantics and bounded reconnect/retry.

See [[Protocol and Network Traffic]].

## Configuration and authority modes

Paper, Velocity, StaffBot and web components have different configuration/lifecycle ownership. Some settings are hot-reloadable; database pools, Gateway sessions, listener binds, provider classloading and other resources are restart-owned.

Authority/enforcement modes are safety boundaries, not convenience toggles. A broken dependency must not be bypassed by enabling a destructive mode simply to make a feature respond.

See [[Configuration]].

## Identity and player directory

UUID remains authoritative. Verified Floodgate evidence may establish Java/Bedrock platform; unavailable/incompatible evidence remains `UNKNOWN`; unverified proxy observations cannot downgrade verified platform identity. `*` aliases remain lookup compatibility, not platform proof.

Discord/Minecraft ownership/link history is now separately persisted under V20. Public projections must not leak private link/alt history.

## Runtime health

Health should explain which dependency/authority fact makes a feature safe, degraded, disabled or restart-required.

Relevant surfaces include Paper/Velocity runtime health, `/estaff status`/verification paths, and StaffBot’s private health/readiness server. Health responses/logs must not expose secrets or private moderation data.

## Build and quality evidence

The repository combines Java tests, MariaDB Testcontainers, runtime-JAR verification, coverage/static analysis, StaffBot smoke/runtime checks, Wiki validation, protected staging, and web/component validation. These are different evidence classes.

Use [[Build and Testing]] for exact commands and interpretation.

## Go deeper

- [[Architecture]]
- [[Developer Code Guide]]
- [[Code Review Guide]]
- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Website and Web API]]
- [[Protocol and Network Traffic]]
- [[Configuration]]
- [[Recovery and Troubleshooting]]
- [[Implementation Status]]