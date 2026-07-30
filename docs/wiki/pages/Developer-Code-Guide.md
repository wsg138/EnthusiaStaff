# Developer Code Guide

This page is the practical map for reviewing or modifying the EnthusiaStaff
codebase. It explains where important code lives, which files own each
responsibility, and how requests move through Paper, the domain layer, MariaDB,
Velocity, and external integrations.

> **Review boundary:** this is an orientation guide, not proof that a feature is
> complete. Always compare the code with `ENTHUSIASTAFF-GOALS.md`,
> `reports/REQUIREMENTS-MATRIX.md`, relevant tests, and staging evidence.

## Recommended review order

Do not begin by opening files at random. Use this order:

1. `ENTHUSIASTAFF-GOALS.md` — authoritative intended behavior and safety rules.
2. `reports/REQUIREMENTS-MATRIX.md` — conservative implementation, test, and
   staging status.
3. `settings.gradle.kts` and root `build.gradle.kts` — module graph, Java
   version, testing, and deployable artifacts.
4. `paper/src/main/resources/plugin.yml` — exposed Paper commands,
   permissions, soft dependencies, and the Paper entry point.
5. `paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java`
   — Paper bootstrap and feature wiring.
6. `velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java`
   — proxy bootstrap, login enforcement, network identity, workers, migration,
   and the website bridge.
7. `paper/src/main/java/net/enthusia/staff/paper/PaperStorageBindings.java` and
   `persistence/src/main/java/net/enthusia/staff/persistence/MariaDbRuntime.java`
   — how ports, stores, and application services are assembled.
8. The feature-specific trace in this guide.
9. The corresponding unit, integration, concurrency, and failure tests.

Read [[Architecture]] for dependency rules and runtime ownership. Read
[[Implementation Status]] before treating any path as production-ready.

## Repository map

| Path | Responsibility | Deployment |
| --- | --- | --- |
| `common/` | Shared identifiers, validation, cryptographic primitives, bounded utilities, and platform-neutral result types | Included by runtime modules |
| `domain/` | Business rules, authorization, application services, state machines, requests/results, and ports | Included by runtime modules |
| `integration-contracts/` | Compile-time contracts for optional Enthusia-owned provider plugins | Contract dependency only |
| `persistence/` | MariaDB bootstrap, Flyway migrations, JDBC stores, transactions, leases, journals, inboxes, outboxes, and quarantine | Included by both runtime jars |
| `protocol/` | Authenticated Paper–Velocity transport, replay protection, acknowledgements, TLS, and bounded delivery | Included by both runtime jars |
| `paper/` | Commands, GUIs, listeners, player-state mutation, staff tools, and Paper-side integrations | `EnthusiaStaff-Paper-<version>.jar` |
| `velocity/` | Login and switch enforcement, network identity, network workers, migration, Discord, and website API | `EnthusiaStaff-Velocity-<version>.jar` |
| `integration-tests/` | MariaDB Testcontainers, transaction, idempotency, recovery, migration, and cross-module tests | Never deployed |
| `docs/` | Source-controlled architecture, security, migration, recovery, and operational documents | Documentation only |
| `docs/wiki/pages/` | Published Wiki source | Documentation only |
| `reports/` | Requirements status, Codacy checkpoints, and review evidence | Review material only |

Exactly two Minecraft runtime jars are intended. `integration-tests` and
`integration-contracts` are not separate server plugins.

## Root files reviewers should understand

### `ENTHUSIASTAFF-GOALS.md`

The authoritative specification. Existing code does not override it. When the
implementation and goals disagree, treat the mismatch as unfinished work or a
design change that requires explicit review and documentation.

### `reports/REQUIREMENTS-MATRIX.md`

Maps each requirement to source, configuration, tests, remaining work, and
blockers. Use it to avoid assuming that class existence proves completion.

### `settings.gradle.kts`

Declares the eight Gradle modules. Adding or removing a module is an
architecture-level change.

### Root `build.gradle.kts`

Owns Java 21 compilation, `-Xlint:all -Werror`, JUnit, JaCoCo aggregation, and
the `runtimeJars` task that builds only the Paper and Velocity artifacts.

### `paper/src/main/resources/plugin.yml`

The Paper registration surface for commands, permissions, rank permission
inheritance, soft dependencies, and the Paper main class. Compare it with the
command classes and the goals document. Registration does not prove that the
complete workflow is staging-verified.

### Database migrations

Flyway SQL lives under:

```text
persistence/src/main/resources/db/migration/
```

Review migrations before reviewing JDBC implementations. Constraints, indexes,
unique keys, revisions, and state columns enforce many safety properties.
Migrations are forward-only; do not rewrite an already applied migration.

## Dependency direction

Shared policy must not depend on platform implementations:

```text
Paper/Velocity commands, events, GUIs, and adapters
                         |
                         v
               domain application services
                         |
                         v
                  domain ports/models
                         ^
                         |
             persistence/protocol adapters
```

`domain` must not import Bukkit, Velocity, JDBC, Discord, or a web framework.
Commands and GUIs translate input and display results; they must not independently
implement punishment ladders, authorization, transaction rules, or recovery
policy.

## Paper runtime

### Composition root: `EnthusiaStaffPaperPlugin.java`

Path:

```text
paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java
```

This class wires the Paper runtime. It currently:

- loads Paper configuration and reason policies;
- creates the bounded worker executor;
- constructs freeze, staff-mode, vanish, inventory, economy, confiscation,
  reports, evidence, automod, and integration components;
- registers commands, listeners, and Bukkit services;
- initializes MariaDB off the game thread;
- starts enforcement and network communication after required state is ready;
- records degraded feature state when storage or integrations are unavailable;
- closes coordinators, transport, workers, and database state during disable.

Review it for lifecycle ordering, shutdown races, feature gating, accidental
main-thread I/O, and business policy leaking into bootstrap code.

### Paper lifecycle and service wiring

| File | Responsibility |
| --- | --- |
| `paper/.../PaperRuntimeLifecycle.java` | Coordinates storage publication, runtime start/stop state, and shutdown races |
| `paper/.../PaperStorageBindings.java` | Groups MariaDB stores and constructs `PunishmentService`, `PunishmentDraftWorkflow`, and `SanctionChangeService` |
| `paper/.../RuntimeHealth.java` | Paper operational-mode and feature-issue reporting |
| `paper/.../BoundedExecutorFactory.java` | Creates bounded background execution rather than unbounded pools |

`PaperStorageBindings` is an important review boundary. It shows which persistence
ports are supplied to domain services and helps detect commands or GUIs bypassing
application services.

### Commands

Paper command classes live under:

```text
paper/src/main/java/net/enthusia/staff/paper/command/
```

Important classes include:

| Class | Entry point |
| --- | --- |
| `EstaffCommand` | Status, verification, reload, and diagnostics |
| `PunishmentCommand` | `/punish`, `/ban`, `/mute`, `/warn`, `/kick`, and `/ipban` workflow entry |
| `SanctionChangeCommand` | Reduction, ending, revocation, unban/unmute, warning removal, and overturn paths |
| `ReportCommand` | Player report creation |
| `ReportsCommand` | Staff report queue and management |
| `FreezeCommand` | Durable freeze and unfreeze actions |
| `StaffModeCommand` | Staff-mode entry and exit |
| `VanishCommand` | Rank-aware vanish toggle |
| `StaffChatCommand` | Staff-channel toggle through the configured chat adapter |
| `InventoryCommand` | Inventory and Ender chest inspection entry points |
| `InspectCommand` | Player inspection and case-linked asset actions |
| `ClientCommand` | Client evidence inspection and explicit snapshot capture |
| `CaseCommand` | Case-linked recovery such as item restoration |

For each command, confirm that it resolves the actor and target safely, delegates
to an application service or coordinator, performs no blocking I/O on the server
thread, and does not duplicate authorization or policy.

## Velocity runtime

### Composition root: `EnthusiaStaffVelocityPlugin.java`

Path:

```text
velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java
```

This class currently owns or wires:

- MariaDB and Velocity configuration startup;
- network login-ban enforcement;
- safe server-switch enforcement;
- player presence and server-directory updates;
- protected network identity capture;
- `/estaff`, `/alts`, and `/alt` proxy commands;
- persistent Paper–Velocity channel server;
- network and Discord outbox workers;
- LiteBans migration, shadow comparison, and cutover coordination;
- restricted punishment/appeal website API delivery;
- shutdown of tasks, HTTP, transport, executors, and database resources.

Review it for event-thread blocking, authority-mode checks, raw network-address
leakage, startup/shutdown ordering, oversized responsibilities, and safe behavior
when MariaDB or backends are unavailable.

### Important Velocity files

| File | Responsibility |
| --- | --- |
| `velocity/.../VelocityConfiguration.java` | Parses Velocity-owned configuration and environment-backed secrets |
| `velocity/.../VelocityRuntimeHealth.java` | Proxy health and operational-mode reporting |
| `velocity/.../NetworkOutboxWorker.java` | Claims and delivers durable network messages with retry/fencing |
| `velocity/.../DiscordOutboxWorker.java` | Delivers durable Discord events with bounded retry and failure behavior |
| `velocity/.../WebsiteApiServer.java` | Restricted website-facing moderation API |

## Domain layer

Main path:

```text
domain/src/main/java/net/enthusia/staff/domain/
```

The domain layer contains policy and application behavior. Important areas are:

| Package/path | Responsibility |
| --- | --- |
| `application/` | Use cases such as punishment creation, durable drafts, and sanction changes |
| `auth/` | Rank and action authorization; `DefaultAuthorizationPolicy` is a central boundary |
| `casefile/` | Case identity, visibility, review projections, and case state |
| `sanction/` | Sanction types, status, changes, expectations, and active-sanction projections |
| `escalation/` | Reason families, history contribution, ladders, decay, and recommendations |
| `report/` | Report requests, queues, details, claims, closure, and evidence projections |
| `inventory/` | Inventory revisions, item paths, journal states, patches, confiscation, and restoration models |
| `economy/` | Moderation plans, exact before/after evidence, operation state, and recovery results |
| `staff/` | Durable staff-session records and state snapshots |
| `freeze/` | Freeze records and persistence-facing state |
| `alt/` | Relationship states, inheritance decisions, evidence, and confidence summaries |
| `migration/` | Import, shadow, cutover, reconciliation, and founder-override policy |
| `discord/` | Durable delivery event and retry models |
| `website/` | Sanitized public projections, punishment codes, and website actor requests |
| `ports/` | Interfaces implemented by MariaDB, Paper, Velocity, or provider adapters |
| `runtime/` | Operational-state snapshots and mode coordination |

### Authorization boundary

Start with:

```text
domain/src/main/java/net/enthusia/staff/domain/auth/DefaultAuthorizationPolicy.java
```

Then inspect every application service and adapter that performs a write. The
Developer role must remain punishment read-only even if a command, GUI, website,
or integration supplies a permission node incorrectly. Authorization must be
rechecked inside the authoritative service, not only in presentation code.

## Persistence layer

Main path:

```text
persistence/src/main/java/net/enthusia/staff/persistence/
```

### Runtime assembly

`MariaDb.java` opens the datasource and migration runtime. `MariaDbRuntime.java`
constructs and exposes JDBC implementations for the domain ports. Compare its
store ownership with `PaperStorageBindings` and Velocity bootstrap.

### Important stores

| File | Responsibility |
| --- | --- |
| `JdbcModerationStore.java` | Case and punishment creation persistence |
| `JdbcSanctionMutationStore.java` | Precise sanction changes, revisions, and audit-linked mutations |
| `JdbcCaseReviewStore.java` | Case and sanction review projections |
| `JdbcPunishmentDraftStore.java` | Durable punishment GUI drafts and resume state |
| `JdbcReportStore.java` | Reports, state changes, and evidence projections |
| `JdbcPlayerDirectory.java` | UUID/name/session lookup and player-directory persistence |
| `JdbcInventoryJournalStore.java` | Inventory, confiscation, snapshot, restoration, and recovery journal data |
| `JdbcInventoryPatchTransitions.java` | Coherent paired inventory-patch state transitions |
| `JdbcEconomyJournalStore.java` | Economy removal/restoration operation state and evidence |
| `JdbcStaffSessionStore.java` | Durable staff-mode state snapshots |
| `JdbcVanishStore.java` | Durable vanish intent/state |
| `JdbcFreezeStore.java` | Durable freeze state and reconnect behavior |
| `JdbcNetworkIdentityStore.java` | Protected network identity and alt evidence persistence |
| `JdbcNetworkOutboxStore.java` | Durable Paper–Velocity delivery queue |
| `JdbcDiscordOutboxStore.java` | Durable Discord delivery queue |
| `JdbcWebsiteModerationStore.java` | Sanitized website projections and actions |

### JDBC review checklist

For every destructive store method, verify:

- prepared statements and bounded queries;
- explicit transaction ownership;
- correct isolation and row locking;
- revision or fencing checks in the `WHERE` clause;
- exact affected-row validation;
- unique-key handling for idempotency;
- no success returned before durable commit;
- resources closed on every path;
- duplicate replay returns the original result rather than repeating effects;
- ambiguous external state enters quarantine instead of being guessed.

## Paper–Velocity protocol

Main path:

```text
protocol/src/main/java/net/enthusia/staff/protocol/
```

Important files:

| File | Responsibility |
| --- | --- |
| `PersistentChannelClient.java` | Backend connection, bounded outbound delivery, reconnect, and acknowledgements |
| `PersistentChannelServer.java` | Velocity-side authenticated connection and message handling |
| `EnvelopeAuthenticator.java` | Message authentication and envelope verification |
| `ReplayGuard.java` | Rejects replayed or stale authenticated messages |
| `TlsContextLoader.java` | Loads explicit TLS key and trust material |

Review protocol changes together with `NetworkOutboxWorker`, inbox/outbox schemas,
and tests. Transport is at-least-once; idempotent consumers and unique inbox keys
make repeated delivery safe. An acknowledgement must represent a durably recorded
outcome, not merely receipt of bytes.

## Feature traces

### Punishment creation

Typical path:

```text
/punish or filtered command
  -> paper/command/PunishmentCommand
  -> paper/punishment/PunishmentGuiController or direct request
  -> domain/application/PunishmentDraftWorkflow
  -> domain/application/PunishmentService
  -> domain/auth/DefaultAuthorizationPolicy
  -> domain/escalation/EscalationEngine
  -> persistence/JdbcModerationStore
  -> case + sanctions + audit + durable network outbox
  -> Velocity/network enforcement worker
```

Review together:

- `paper/punishment/`
- `domain/application/PunishmentService.java`
- `domain/application/PunishmentDraftWorkflow.java`
- `domain/escalation/`
- `persistence/JdbcModerationStore.java`
- `persistence/JdbcPunishmentDraftStore.java`
- punishment, escalation, permission, and MariaDB integration tests.

Check atomic combined sanctions, stable reason IDs, history contribution, decay,
public/private visibility, durable drafts, duplicate submission, and restart
recovery.

### Punishment change or removal

Typical path:

```text
/removepunishment, /unban, /unmute, /removewarning
  -> paper/command/SanctionChangeCommand
  -> paper/sanction/SanctionChangeGuiController when applicable
  -> domain/application/SanctionChangeService
  -> persistence/JdbcSanctionMutationStore
  -> audit + outbox + precise sanction state change
```

Verify that the exact case and sanction are selected, revisions are rechecked,
unrelated sanctions are untouched, history is preserved, and Developer remains
unable to mutate punishment state.

### Reports and evidence

Typical path:

```text
/report
  -> paper/command/ReportCommand
  -> domain/report request and policy
  -> persistence/JdbcReportStore
  -> reports queue and staff actions
```

Context capture also involves:

- `paper/report/ChatContextBuffer.java`
- `paper/command/ReportsCommand.java`
- client-evidence adapters under `paper/client/`
- RoseChat integration for private-message evidence where supported.

Review reporter privacy, cooldowns, duplicate merging, retention, coordinate and
client snapshots, claim conflicts, and Discord sanitization.

### Inventory inspection and editing

Typical path:

```text
/invsee or /endersee
  -> paper/command/InventoryCommand
  -> paper/inventory/InventoryCoordinator
  -> domain/inventory revisions, patches, leases, and decisions
  -> persistence/JdbcInventoryJournalStore
  -> main-thread Bukkit mutation or queued login patch
```

Review:

- online authority versus offline authority;
- one coordinator and compatible concurrent viewers per target;
- dirty-slot updates rather than stale full-clone saves;
- nested shulkers and bundles;
- server/scope ownership;
- exact before snapshots;
- atomic offline replacement and reread verification;
- login-time queued patch application;
- quarantine when safety cannot be proved.

### Item confiscation and restoration

Typical path:

```text
Inspect/case action
  -> paper/inventory/ConfiscationCoordinator
  -> domain inventory/confiscation models
  -> persistence/JdbcInventoryJournalStore
  -> durable snapshot before deletion
  -> verified commit or recovery quarantine
```

Restoration enters through `paper/command/CaseCommand` and must be idempotent and
dupe-safe. Review exact case bindings, item paths/fingerprints, lock renewal,
startup recovery, and movement/container bypasses.

### Economy confiscation

Typical path:

```text
case-linked economy action
  -> paper/economy/EconomyCoordinator
  -> paper/economy/EnthusiaCurrencyGateway
  -> domain/economy plan and operation models
  -> persistence/JdbcEconomyJournalStore
  -> EnthusiaCurrency provider API
```

EnthusiaStaff owns moderation intent, journaling, verification, audit, and
recovery. EnthusiaCurrency remains the balance authority. Raw balance database
writes are not an acceptable shortcut.

### Staff mode, vanish, and freeze

Important paths:

| Feature | Paper runtime | Persistence |
| --- | --- | --- |
| Staff mode | `paper/staff/StaffModeManager.java`, `StaffStateCodec.java` | `JdbcStaffSessionStore.java` |
| Vanish | `paper/api/StaffVisibilityService.java`, `paper/visibility/DefaultStaffVisibilityService.java`, `VanishManager.java` | `JdbcVanishStore.java` |
| Freeze | `paper/freeze/FreezeManager.java`, `paper/command/FreezeCommand.java` | `JdbcFreezeStore.java` |

Review crash/reconnect restoration, original snapshot preservation, staff-item
leakage, rank visibility, CombatLogX gating, listener coverage, reload/disable,
server switching, and degraded integration behavior.

### Alts and network identity

Important paths:

- `common/src/main/java/net/enthusia/staff/common/security/NetworkIdentityProtector.java`
- `common/.../HmacTokenService.java`
- `domain/alt/`
- `persistence/JdbcNetworkIdentityStore.java`
- Velocity login/session handling and `/alts`/`/alt` commands.

Raw addresses must never appear in GUI, Discord, site output, logs, API errors, or
ordinary database equality queries. Review encryption envelopes, HMAC tokens,
nonce generation, key versions, rotation, maintenance-event suppression,
relationship states, household exceptions, and exact remaining sanction
inheritance.

### Discord delivery

Typical path:

```text
domain event or committed operation
  -> discord_outbox row in the same durable transaction
  -> persistence/JdbcDiscordOutboxStore
  -> velocity/DiscordOutboxWorker
  -> configured webhook destination
```

Review lease ownership, stale-worker fencing, backoff, circuit behavior,
sanitization, manual retry, and no loss during restart.

### LiteBans migration and cutover

Important paths:

- `domain/migration/`
- `persistence/src/main/java/net/enthusia/staff/persistence/migration/`
- `LiteBansMigrationService.java`
- Velocity migration and shadow tasks
- `docs/litebans-migration.md`, `docs/shadow-mode.md`, `docs/cutover.md`, and
  `docs/rollback.md`.

Review dry-run accuracy, source schema variants, external-ID mappings,
idempotent reruns, checksums, exact expiration preservation, the 168-hour
non-enforcing shadow window, mismatch blockers, writer fencing, and post-cutover
emergency freeze.

### Website bridge

Important paths:

- `domain/website/`
- `persistence/JdbcWebsiteModerationStore.java`
- `persistence/WebsitePunishmentProjection.java`
- `velocity/WebsiteApiServer.java`
- website actor and projection tests.

The bridge may expose only sanitized fields. It must not expose reporter identity,
private messages, coordinates, internal notes, network identity, alt evidence,
confiscation detail, or sensitive automation metadata.

## Configuration locations

Current configuration is still evolving toward the modular target described in
the goals. Review both current resources and the missing target layout.

| Location | Purpose |
| --- | --- |
| `paper/src/main/resources/config.yml` | Current Paper runtime settings |
| `paper/src/main/resources/reason-policies.yml` | Current punishment reason and escalation policy source |
| `paper/src/main/resources/plugin.yml` | Commands, permissions, rank inheritance, and soft dependencies |
| `paper/.../config/ReasonPolicyConfigurationLoader.java` | Parses and validates reason policies |
| `domain/.../ports/AtomicReasonPolicyRepository.java` | Immutable, atomically replaceable policy boundary |
| `velocity/.../VelocityConfiguration.java` | Velocity settings and environment-backed secret references |

A reload must parse into a temporary immutable model, validate all references, and
swap only if the entire configuration is valid. It must not discard active drafts,
sessions, sanctions, reports, leases, or journals.

## Tests and where to look

Unit tests normally sit beside their module under `src/test/java`. Database and
cross-module tests live in `integration-tests` and use MariaDB Testcontainers.

Key test families include:

- authorization and Developer denial;
- punishment creation, escalation, drafts, and sanction changes;
- report requests and chat-context buffering;
- network identity encryption, tamper rejection, key versions, and fresh nonces;
- protocol authentication, replay rejection, reconnect, and duplicate delivery;
- inventory patch decisions, confiscation lifecycle, restoration integrity, and
  journal transitions;
- economy operation codecs, rollback evidence, and conflict behavior;
- Discord and network outbox leasing/fencing;
- LiteBans import, idempotency, shadow mismatch, and cutover gates;
- website projection sanitization and actor authorization.

Before approving a feature change, identify at least one test for policy, one test
for persistence or adapter behavior when relevant, and the staging requirement
that cannot be proven by automated tests.

## Threading and concurrency rules

- Bukkit mutations run only on the owning Paper server thread or supported entity
  scheduler.
- Velocity event threads must not block on JDBC, HTTP, filesystem, or socket I/O.
- Database, network, and provider calls use bounded executors and queues.
- Per-player destructive work uses locks or durable leases with fencing tokens.
- Optimistic updates must reject stale revisions; they must not overwrite newer
  state by retrying blindly.
- Outbox delivery is at-least-once and consumers must be idempotent.
- Shutdown stops new intake, preserves durable pending work, and closes resources
  in dependency order.

## External integration boundaries

Paper adapters live mainly under:

```text
paper/src/main/java/net/enthusia/staff/paper/integration/
paper/src/main/java/net/enthusia/staff/paper/client/
paper/src/main/java/net/enthusia/staff/paper/economy/
```

Provider plugins remain authoritative for their own data. EnthusiaStaff should use
a supported API or explicit adapter, never raw provider database writes, reflective
guessing presented as fact, or command dispatch as a transaction mechanism.
Missing optional integrations must disable only the affected feature and appear in
verification output.

## High-risk review areas

Spend additional review time on:

1. punishment authorization and combined-sanction atomicity;
2. inventory, confiscation, and economy crash windows;
3. stale revisions, lease renewal, fencing, and exact affected-row checks;
4. startup recovery and quarantine resolution;
5. raw network-identity leakage;
6. Paper main-thread and Velocity event-thread blocking;
7. durable outbox duplicate and stale-worker behavior;
8. configuration reload preserving live durable workflows;
9. Developer punishment denial through commands, GUIs, APIs, website, and
   integrations;
10. migration mismatch handling and post-cutover reconciliation;
11. oversized composition, coordinator, and JDBC classes where responsibilities
   may be mixed;
12. provider API packaging and duplicate classes in shaded jars.

## Review completion checklist

A reviewer should be able to answer:

- Which module owns the policy?
- Which class is the authoritative application-service entry point?
- Which port and JDBC store persist the operation?
- What transaction, revision, lease, or idempotency mechanism prevents partial or
  duplicate effects?
- Which thread applies platform state?
- What happens when the database, proxy, provider, or process fails halfway?
- Which audit and outbox records are committed?
- Which tests prove the normal, duplicate, stale, failure, and recovery paths?
- What remains dependent on real Paper/Velocity/provider staging?
- Does the requirements matrix need to be updated?

A change is not fully reviewed merely because its primary class looks correct. The
entire path from input through authorization, durable intent, side effect,
verification, recovery, audit, and external delivery must agree.