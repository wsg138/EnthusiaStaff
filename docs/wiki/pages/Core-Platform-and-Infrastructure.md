# Core Platform and Infrastructure

**Estimated group completion: about 72%.**

This group contains the foundation that every other EnthusiaStaff feature relies
on: the two runtime plugins, module boundaries, database authority, authenticated
Paper–Velocity communication, safe-write controls, configuration, identity and
runtime health.

- Return to [[Feature Completion Status|Implementation-Status]].
- See [[Remaining Development Map|Development-Blueprint]] for the unfinished-work order.
- See [[Architecture]] for the system design.
- See [[Developer Code Guide]] for the complete repository map and review traces.

> Percentages are rounded planning estimates. Exact implementation and test
> evidence remains in the
> [requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md).

## Find a foundation area

| Area | Complete | What it does | Jump to details |
| --- | ---: | --- | --- |
| Runtime artifacts and packaging | **100%** | Produces the Paper and Velocity plugins without leaking provider-owned APIs. | [Runtime artifacts](#runtime-artifacts-and-packaging) |
| Module boundaries and architecture | **75%** | Keeps policy separate from Bukkit, Velocity, JDBC and optional providers. | [Architecture](#module-boundaries-and-architecture) |
| Paper lifecycle | **75%** | Starts, wires, degrades and shuts down backend-server features safely. | [Paper lifecycle](#paper-lifecycle) |
| Velocity lifecycle | **75%** | Owns proxy enforcement, transport, migration, Discord and the website bridge. | [Velocity lifecycle](#velocity-lifecycle) |
| MariaDB schema and persistence | **88%** | Stores authoritative cases, sanctions, reports, sessions, journals and outboxes. | [MariaDB](#mariadb-schema-and-persistence) |
| Safe-write controls | **70%** | Prevents duplicate, stale or partially applied destructive work. | [Safe-write controls](#safe-write-controls) |
| Paper–Velocity protocol | **84%** | Moves authenticated, replay-protected messages without requiring an online player. | [Protocol](#paper-velocity-protocol) |
| Operational modes and degradation | **43%** | Blocks only unsafe actions when a dependency or authority mode is unavailable. | [Operational modes](#operational-modes-and-degraded-behavior) |
| Configuration and reload | **35%** | Loads versioned policy and is intended to atomically replace one validated configuration model. | [Configuration](#configuration-and-reload) |
| Identity and player directory | **48%** | Resolves UUIDs, current/previous names, offline players and Bedrock aliases. | [Identity](#identity-and-player-directory) |
| Runtime health and verification | **55%** | Explains what is ready, degraded, blocked or restart-required. | [Health](#runtime-health-and-verification) |
| Build, tests and quality gates | **80%** | Builds both jars and runs Java, MariaDB, coverage, static-analysis and Wiki checks. | [Validation](#build-tests-and-quality-gates) |

## Runtime artifacts and packaging

### What it does

EnthusiaStaff deploys exactly two Java 21 artifacts:

- `EnthusiaStaff-Paper-<version>.jar` on every backend;
- `EnthusiaStaff-Velocity-<version>.jar` on the proxy.

Internal modules and test projects must not become extra server plugins. Provider
API classes must not be shaded into EnthusiaStaff when the provider plugin owns
them.

### Primary files

- [Root build](https://github.com/wsg138/EnthusiaStaff/blob/main/build.gradle.kts)
- [Module settings](https://github.com/wsg138/EnthusiaStaff/blob/main/settings.gradle.kts)
- [Paper build](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/build.gradle.kts)
- [Velocity build](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/build.gradle.kts)
- [Integration contracts build](https://github.com/wsg138/EnthusiaStaff/blob/main/integration-contracts/build.gradle.kts)

### What remains

The repository build and leak inspection are complete for the current scope.
Release candidates still need exact-artifact hashes and real classloader testing
with every supported provider installed together.

## Module boundaries and architecture

### What it does

The module layout keeps central policy in `domain`, durable adapters in
`persistence`, transport in `protocol`, and platform behavior in `paper` or
`velocity`. Commands and GUIs should translate requests rather than implement
punishment policy or transaction logic themselves.

### Primary paths

- [Common utilities](https://github.com/wsg138/EnthusiaStaff/tree/main/common)
- [Domain policy and services](https://github.com/wsg138/EnthusiaStaff/tree/main/domain)
- [Persistence adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence)
- [Protocol](https://github.com/wsg138/EnthusiaStaff/tree/main/protocol)
- [Paper runtime](https://github.com/wsg138/EnthusiaStaff/tree/main/paper)
- [Velocity runtime](https://github.com/wsg138/EnthusiaStaff/tree/main/velocity)
- [Integration contracts](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-contracts)
- [Integration tests](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-tests)

### Related documentation

- [[Architecture]]
- [[Developer Code Guide]]

### What remains

Several coordinators, JDBC stores and composition classes still carry broad
responsibilities. Future splits must preserve behavior and avoid creating a
second source of truth.

## Paper lifecycle

### What it does

The Paper side loads configuration, publishes storage-backed services, registers
commands and listeners, discovers optional integrations, starts network work and
closes resources in dependency order.

### Primary files

- [Paper plugin entrypoint](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java)
- [Runtime lifecycle](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeLifecycle.java)
- [Runtime components](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java)
- [Command registrar](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java)
- [Storage bindings](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperStorageBindings.java)
- [Integration manager](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperIntegrationManager.java)
- [Resource closer](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperResourceCloser.java)

### What remains

Complete degraded-startup behavior, partial-startup cleanup, shutdown deadlines,
process-kill recovery and real Paper/Leaf/Folia ownership testing.

## Velocity lifecycle

### What it does

The proxy side owns login and server-switch enforcement, backend presence,
protected network identity, the persistent channel server, network/Discord
workers, LiteBans migration and the restricted website API.

### Primary files

- [Velocity plugin entrypoint](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java)
- [Velocity configuration](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java)
- [Velocity runtime health](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityRuntimeHealth.java)
- [Network outbox worker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/NetworkOutboxWorker.java)
- [Discord outbox worker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/DiscordOutboxWorker.java)
- [Website API server](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiServer.java)

### What remains

Prove startup and shutdown under real proxy/backend topology, dependency-specific
degradation, no-player transport, reconnect storms and provider/website runtime
integration.

## MariaDB schema and persistence

### What it does

MariaDB is the authoritative state for moderation and recovery. Flyway migrations
create the schema; JDBC stores implement domain ports using prepared statements,
transactions, revisions, leases and exact affected-row checks.

### Primary files and paths

- [MariaDB bootstrap](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDb.java)
- [MariaDB runtime bindings](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDbRuntime.java)
- [Flyway migrations](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/resources/db/migration)
- [Moderation store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcModerationStore.java)
- [Sanction mutation store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcSanctionMutationStore.java)
- [Report store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcReportStore.java)
- [Inventory journal](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcInventoryJournalStore.java)
- [Economy journal](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcEconomyJournalStore.java)
- [Network outbox](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcNetworkOutboxStore.java)
- [Discord outbox](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordOutboxStore.java)

### What remains

Complete final indexing/resource-ownership review, production-volume testing,
upgrade rehearsal, multi-server contention and process-kill recovery.

## Safe-write controls

### What it does

High-risk operations use combinations of:

- idempotency keys and unique constraints;
- optimistic revisions;
- leases and fencing tokens;
- durable intent and before snapshots;
- exact commit verification;
- retry or visible quarantine when an outcome is ambiguous.

These controls are spread across domain state machines, migrations and JDBC
stores rather than one universal class.

### Primary review locations

- [Domain application services](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/application)
- [Inventory state models](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/inventory)
- [Economy operation models](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/economy)
- [Migration state models](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/migration)
- [Persistence implementation](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence)

### What remains

Apply equivalent guarantees to every unfinished notification, provider, asset and
cutover workflow; then prove stale-state, duplicate, failure and restart behavior
under concurrent servers.

## Paper–Velocity protocol

### What it does

The protocol provides a persistent authenticated connection between Paper and
Velocity. It does not depend on plugin messaging through an online player.
Messages are authenticated, replay-protected, acknowledged and retried from a
durable MariaDB outbox.

### Primary files

- [Persistent channel client](https://github.com/wsg138/EnthusiaStaff/blob/main/protocol/src/main/java/net/enthusia/staff/protocol/PersistentChannelClient.java)
- [Persistent channel server](https://github.com/wsg138/EnthusiaStaff/blob/main/protocol/src/main/java/net/enthusia/staff/protocol/PersistentChannelServer.java)
- [Envelope authenticator](https://github.com/wsg138/EnthusiaStaff/blob/main/protocol/src/main/java/net/enthusia/staff/protocol/EnvelopeAuthenticator.java)
- [Replay guard](https://github.com/wsg138/EnthusiaStaff/blob/main/protocol/src/main/java/net/enthusia/staff/protocol/ReplayGuard.java)
- [TLS context loader](https://github.com/wsg138/EnthusiaStaff/blob/main/protocol/src/main/java/net/enthusia/staff/protocol/TlsContextLoader.java)
- [Paper message handler](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperNetworkMessageHandler.java)

### Related documentation

- [[Protocol and Network Traffic]]

### What remains

Stage certificate rotation, real allowlists, multiple backends, long proxy/backend
outages, queue saturation, backpressure and no-online-player operation.

## Operational modes and degraded behavior

### What it does

Operational modes describe who is authoritative and which writes are safe:

- `BOOTSTRAP`
- `DEGRADED`
- `SHADOW_MIGRATION`
- `ACTIVE`
- `MAINTENANCE`
- `READ_ONLY_FAILURE`

Dependency-specific degradation should disable only the actions that cannot be
proven safe.

### Primary files

- [Operational mode](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/OperationalMode.java)
- [Runtime state models](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/runtime)
- [Paper runtime health](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/RuntimeHealth.java)
- [Velocity runtime health](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityRuntimeHealth.java)

### What remains

Complete all transitions, emergency freeze, recovery behavior and the exact action
matrix for MariaDB, Velocity, RoseChat, Voice, Currency, Market and Polar failure.

## Configuration and reload

### What it does

Configuration is moving toward a modular, versioned tree that is parsed into one
immutable model. Invalid reloads should reject the entire candidate and preserve
the previous valid runtime state.

### Primary files

- [Current Paper config](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/config.yml)
- [Reason policies](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/reason-policies.yml)
- [Paper plugin metadata](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/plugin.yml)
- [Reason-policy loader](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/config/ReasonPolicyConfigurationLoader.java)
- [Reason-policy bootstrap](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperReasonPolicyBootstrap.java)
- [Atomic policy repository](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/ports/AtomicReasonPolicyRepository.java)

### Related documentation

- [[Configuration]]

### What remains

Create the complete modular file tree, cross-file validation, aliases, GUI
configuration, restart-required reporting and a full reload that preserves every
durable workflow.

## Identity and player directory

### What it does

UUID is authoritative. The player directory supports current names, persisted
sessions and offline targets; the finished system also needs historical names,
Bedrock-prefixed aliases and bounded in-memory completion.

### Primary files

- [Player-directory domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/player)
- [JDBC player directory](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcPlayerDirectory.java)
- [Floodgate integration](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/client/FloodgateIntegration.java)
- [ViaVersion integration](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/client/ViaVersionIntegration.java)

### What remains

Complete previous-name and `*` alias lookup, stale-name reconciliation, ranked
bounded matching, cache refresh and tab completion without SQL per keystroke.

## Runtime health and verification

### What it does

Health and verification should tell operators which features are ready, disabled,
degraded, blocked or restart-required without exposing secrets or overwhelming
ordinary staff.

### Primary files

- [Paper health](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/RuntimeHealth.java)
- [Velocity health](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityRuntimeHealth.java)
- [Operator command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/EstaffCommand.java)

### Related pages

- [[Commands and Permissions]]
- [[Recovery and Troubleshooting]]

### What remains

Report complete schema, queues, recovery/quarantine, modes, integrations,
configuration and backend status with actionable operator guidance.

## Build, tests and quality gates

### What it does

The repository uses Gradle, Java 21, JUnit, MariaDB Testcontainers, JaCoCo,
Codacy/static analysis, runtime-jar inspection and Wiki validation.

### Primary locations

- [Build workflow](https://github.com/wsg138/EnthusiaStaff/tree/main/.github/workflows)
- [Integration tests](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-tests/src/test/java)
- [Wiki validator](https://github.com/wsg138/EnthusiaStaff/blob/main/scripts/wiki/validate_wiki.py)
- [Static-analysis rules](https://github.com/wsg138/EnthusiaStaff/blob/main/ruleset.xml)

### Related documentation

- [[Build and Testing]]
- [[Development Setup]]

### What remains

Add enforced meaningful coverage floors, mutation tests, load/saturation tests,
process-kill tests and complete Paper/Velocity/provider/client acceptance.

## Related pages

- [[Feature Completion Status|Implementation-Status]]
- [[Remaining Development Map|Development-Blueprint]]
- [[Architecture]]
- [[Developer Code Guide]]
- [[Protocol and Network Traffic]]
- [[Configuration]]
- [[Build and Testing]]
