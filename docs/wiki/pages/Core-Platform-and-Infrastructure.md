# Core Platform and Infrastructure

This hub covers the foundation every other EnthusiaStaff feature depends on: runtime artifacts, architecture, lifecycle, MariaDB, authenticated Paper-Velocity communication, safe-write controls, configuration, identity, health, and validation.

For the overall product picture use [[Implementation Status]]. For detailed source tracing use [[Developer Code Guide]]. For review invariants use [[Code Review Guide]].

## Quick status

| Area | Merged-main state | Main limitation |
| --- | --- | --- |
| Runtime artifacts/packaging | **Implemented, not staging-verified** | Real all-provider/classloader/release-candidate staging remains. |
| Module architecture | **Available with limitations** | Several first-party coordinators remain large; future changes must preserve dependency direction. |
| Paper lifecycle | **Implemented, not staging-verified** | Real Folia/restart/provider/runtime ownership still needs representative validation. |
| Velocity lifecycle | **Partial** | Existing proxy/runtime workers are merged; broader bootstrap/reload recovery and representative distributed acceptance are not complete. |
| MariaDB/Flyway | **Implemented, not staging-verified** | Production-like load, process-kill, latency and multi-runtime acceptance remain. |
| Safe-write controls/recovery | **Partial** | High-risk external/player-state workflows still need complete interruption/recovery proof. |
| Paper-Velocity protocol | **Implemented, not staging-verified** | Real multi-backend reconnect/backpressure/certificate/outage acceptance remains. |
| Operational modes/degradation | **Partial** | Full mode-transition and production-cutover acceptance remain. |
| Configuration/reload | **Partial** | Full modular tree and complete cross-file atomic reload are unfinished. |
| Identity/player directory | **Implemented, not staging-verified** | Representative Geyser/Floodgate multi-backend/client staging remains. |
| Runtime health/verification | **Partial** | Complete dependency/topology release verification remains. |
| Build/quality gates | **Available with limitations** | Hosted checks do not replace private runtime/staging/production acceptance. |

## Runtime artifacts and module boundaries

EnthusiaStaff is designed around exactly two Java 21 Minecraft runtime artifacts:

- `EnthusiaStaff-Paper-<version>.jar`
- `EnthusiaStaff-Velocity-<version>.jar`

Primary paths:

- [root build](https://github.com/wsg138/EnthusiaStaff/blob/main/build.gradle.kts)
- [module settings](https://github.com/wsg138/EnthusiaStaff/blob/main/settings.gradle.kts)
- [Paper build](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/build.gradle.kts)
- [Velocity build](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/build.gradle.kts)

Internal module responsibilities:

```text
common                 shared primitives
domain                 business policy and ports
integration-contracts  compile-time contracts for supported Enthusia providers
persistence            MariaDB/Flyway adapters
protocol               authenticated distributed transport
paper                  Bukkit/Paper runtime adapters
velocity               proxy/runtime adapters
integration-tests      validation only; never deployed
```

`integration-contracts` is a first-class compile-time provider boundary. It does not own moderation policy; provider adapters use it to reach supported provider contracts while application policy remains in `domain`.

The actual dependency graph is described in [[Architecture]]. Reviewer rule: policy belongs in `domain`; commands, GUIs, HTTP handlers and provider adapters should not become alternate business-rule implementations.

## Paper lifecycle

Paper composition and lifecycle are split across focused collaborators rather than one giant main class:

- [Paper plugin](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java)
- [runtime lifecycle](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeLifecycle.java)
- [runtime components](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java)
- [storage bindings](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperStorageBindings.java)
- [command registrar](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java)
- [integration manager](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperIntegrationManager.java)
- [resource closer](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperResourceCloser.java)

Database/provider/network work belongs off the game/entity thread. Player/entity state must return to the supported owning scheduler. Unit/mocked scheduling tests do not prove real Folia behavior; see [[Build and Testing]].

## Velocity lifecycle

Velocity owns network-facing authority and workers, including login/server-switch enforcement, protected network identity, the persistent backend transport server, network/Discord workers, migration coordination, and the restricted website bridge.

Primary paths:

- [Velocity plugin](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java)
- [Velocity package](https://github.com/wsg138/EnthusiaStaff/tree/main/velocity/src/main/java/net/enthusia/staff/velocity)
- [Velocity configuration](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java)
- [network worker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/NetworkOutboxWorker.java)
- [Discord worker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/DiscordOutboxWorker.java)

Velocity event threads must not wait on JDBC, HTTP, filesystem or socket I/O. Do not treat classes or tests present only on an active unmerged branch as current `main` behavior.

## MariaDB and Flyway

MariaDB is the durable authority for core moderation/recovery state. The persistence runtime and stores live under:

- [MariaDb](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDb.java)
- [MariaDbRuntime](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDbRuntime.java)
- [persistence package](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence)
- [Flyway migrations](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/resources/db/migration)

Current merged `main` includes **V17** (`V17__website_appeal_workflow.sql`). V1-V17 are immutable forward history. Future schema changes add a new migration; do not edit applied migrations or rely on repair to hide a checksum change.

MariaDB/Testcontainers is strong evidence for SQL/transaction/migration scenarios actually exercised. It does not establish production volume, process-kill timing, multi-server contention, or production acceptance.

## Safe-write controls

Destructive flows use combinations of:

- idempotency keys/unique constraints;
- optimistic revisions;
- row locks;
- durable leases and fencing tokens;
- before snapshots/journals;
- append-only audit/events;
- durable inbox/outbox delivery;
- bounded retry/backoff;
- recovery/quarantine when external outcome is ambiguous.

The exact mechanism depends on the workflow. The central review requirement is that a timeout, stale callback, duplicate delivery, restart, or partial external failure cannot silently create a second effect or overwrite newer state.

See [[Code Review Guide]] and [[Recovery and Troubleshooting]].

## Paper-Velocity protocol

The protocol provides persistent authenticated communication without depending on an online player.

Primary classes:

- [PersistentChannelClient](https://github.com/wsg138/EnthusiaStaff/blob/main/protocol/src/main/java/net/enthusia/staff/protocol/PersistentChannelClient.java)
- [PersistentChannelServer](https://github.com/wsg138/EnthusiaStaff/blob/main/protocol/src/main/java/net/enthusia/staff/protocol/PersistentChannelServer.java)
- [EnvelopeAuthenticator](https://github.com/wsg138/EnthusiaStaff/blob/main/protocol/src/main/java/net/enthusia/staff/protocol/EnvelopeAuthenticator.java)
- [ReplayGuard](https://github.com/wsg138/EnthusiaStaff/blob/main/protocol/src/main/java/net/enthusia/staff/protocol/ReplayGuard.java)
- [network outbox store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcNetworkOutboxStore.java)

Transport is at-least-once. Durable idempotent consumers and inbox/outbox state provide effect-level duplicate safety. Deep dive: [[Protocol and Network Traffic]].

## Operational modes

The target/runtime modes are:

- `BOOTSTRAP`
- `DEGRADED`
- `SHADOW_MIGRATION`
- `ACTIVE`
- `MAINTENANCE`
- `READ_ONLY_FAILURE`

A mode is an authority/safety boundary, not a cosmetic status string. Missing MariaDB, Velocity, providers, schema health or cutover evidence must block only the unsafe actions whose correctness cannot be proved.

Do not switch modes merely to work around an error. See [[Recovery and Troubleshooting]] and [[Shadow Mode and Cutover]].

## Configuration and reload

Current merged configuration includes reason policy, report policy/GUI settings, Paper runtime settings including staff-tool controls, and Velocity-owned settings. The full modular target in the goals is broader than current merged implementation.

Important paths:

- [Paper config](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/config.yml)
- [reason policies](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/reason-policies.yml)
- [reports policy](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/reports.yml)
- [reports GUI](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/gui/reports.yml)
- [Velocity configuration](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java)

A safe reload validates a complete candidate before publication and preserves the prior valid runtime state on failure. Some settings are restart-only; focused pages document those boundaries. See [[Configuration]].

## Identity and player directory

UUID is authoritative. Current merged platform persistence uses supported Floodgate evidence rather than username heuristics:

- verified Floodgate evidence may persist `JAVA` or `BEDROCK`;
- Geyser with missing/unavailable/incompatible Floodgate remains `UNKNOWN`;
- unverified Velocity presence cannot downgrade a verified platform record;
- `*` current/historical aliases remain searchable but are not platform proof;
- duplicate/out-of-order presence observations must not overwrite newer verified state.

Primary paths:

- [Floodgate integration](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/client/FloodgateIntegration.java)
- [player directory store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcPlayerDirectory.java)
- [player domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/player)

Representative Java/Bedrock/Geyser/Floodgate staging is still required for client/runtime claims. See [[Integrations]].

## Runtime health and verification

Health/verification should explain not just whether the plugin started, but which dependency or authority fact makes a feature safe, degraded, disabled, restart-required, or critical.

Useful entry points:

- `paper/.../RuntimeHealth.java`
- `velocity/.../VelocityRuntimeHealth.java`
- `/estaff status`
- `/estaff verify ...`

Operator procedure: [[Recovery and Troubleshooting]].

## Build and quality evidence

The repository combines Java/unit tests, MariaDB Testcontainers, runtime-JAR inspection, coverage, static analysis, Wiki validation, and private runtime gates. These are different evidence classes; none should be silently promoted into another.

Use [[Build and Testing]] for exact commands and evidence interpretation.

## Go deeper

- [[Architecture]] — dependency/runtime ownership.
- [[Developer Code Guide]] — complete source/feature traces.
- [[Code Review Guide]] — cross-cutting review checklist.
- [[Protocol and Network Traffic]] — network internals.
- [[Configuration]] — operator-facing settings/reload behavior.
- [[Recovery and Troubleshooting]] — failure/recovery procedure.
- [[Implementation Status]] — overall merged-main product status.