# Developer Code Guide

This is the detailed source map for EnthusiaStaff. Use [[Developer Guide Index]] when you only need to know where to start, and [[Code Review Guide]] when you need a cross-cutting review checklist.

> **Evidence boundary:** a path existing in source does not prove that the complete feature is staging-verified or production-ready. Reconcile this map with [[Implementation Status]], current merged code, the requirements/evidence ledger, and exact-SHA validation.

## Repository map

| Path | Owns | Deployment |
| --- | --- | --- |
| `common/` | identifiers, validation, security/crypto primitives, bounded utilities | shared into runtime modules |
| `domain/` | business policy, authorization, application services, state machines and ports | shared into runtime modules |
| `integration-contracts/` | stable compile-time contracts for Enthusia-owned providers | contract dependency only |
| `persistence/` | MariaDB bootstrap, Flyway, JDBC stores, transactions, leases, journals, inboxes/outboxes, recovery state | shared into runtime jars |
| `protocol/` | authenticated Paper-Velocity transport, replay protection, ACKs, reconnect | shared into runtime jars |
| `paper/` | commands, GUIs/listeners, server-local player state and Bukkit/provider adapters | Paper runtime jar |
| `velocity/` | proxy enforcement, network identity, transport server, workers, migration and website bridge | Velocity runtime jar |
| `integration-tests/` | MariaDB/cross-module/concurrency/recovery/migration tests | never deployed |
| `components/` | aggregate copies of external components when present | component-specific |
| `docs/` | architecture, security, migration and operational reference | documentation |
| `docs/wiki/pages/` | repository-managed GitHub Wiki source | documentation |

Current merged `main` has exactly two Minecraft runtime plugins: Paper and Velocity. Do not infer the planned Discord staff-bot runtime from domain/schema foundations or from the existing webhook worker.

## Read these before source diving

1. [`ENTHUSIASTAFF-GOALS.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md) — intended finished behavior.
2. [[Implementation Status]] — merged-main product state and evidence class.
3. [[Architecture]] — dependency direction and runtime ownership.
4. [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md) plus current legitimate PR/runtime evidence — requirement-level proof/blockers. Reconcile it with live `main` after recent merges.
5. [[Code Review Guide]] — cross-cutting invariants and failure modes.

## Dependency direction

```text
Paper / Velocity / website / future Discord / provider adapters
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

`domain` must not depend on Bukkit, Velocity, JDBC, JDA/Discord SDKs, or a web framework. Commands, GUIs, listeners, HTTP routes and provider adapters translate runtime input/output; they should not own a second copy of moderation policy.

## Paper composition roots

Main package:

```text
paper/src/main/java/net/enthusia/staff/paper/
```

| File | Responsibility |
| --- | --- |
| `EnthusiaStaffPaperPlugin.java` | plugin entrypoint and top-level lifecycle handoff |
| `PaperRuntimeLifecycle.java` | storage/runtime publication and lifecycle coordination |
| `PaperRuntimeComponents.java` | server-local manager/coordinator construction |
| `PaperStorageBindings.java` | JDBC/domain service bindings |
| `PaperCommandRegistrar.java` | command and GUI registration |
| `PaperIntegrationManager.java` | optional integration discovery/lifecycle |
| `PaperResourceCloser.java` | owned resource shutdown |
| `RuntimeHealth.java` | operational health/degraded state |

Start here when a change affects startup, shutdown, service ownership, feature publication, scheduler ownership, or integration lifecycle. Business rules should normally lead from these files into a domain service rather than remain in composition code.

## Paper commands and presentation

Command classes live under:

```text
paper/src/main/java/net/enthusia/staff/paper/command/
```

Important entry points include:

| Command area | Primary class/path |
| --- | --- |
| status/verify/reload | `EstaffCommand` |
| punishment creation and filtered commands | `PunishmentCommand` plus `paper/punishment/` |
| history | `HistoryCommand` |
| exact sanction reduce/end/revoke/overturn | `SanctionLifecycleCommand` |
| case detail/restoration | `CaseCommand` |
| player report | `ReportCommand` |
| staff report queues/actions | `ReportsCommand` plus `paper/report/` |
| staff mode | `StaffModeCommand` plus `paper/staff/` |
| vanish | `VanishCommand` plus `paper/visibility/` |
| freeze | `FreezeCommand` plus `paper/freeze/` |
| inventory/Ender | `InventoryCommand` plus `paper/inventory/` |
| inspect/client | `InspectCommand`, `ClientCommand`, `paper/client/` |
| staff tools | `StaffToolsCommand`, `paper/staff/StaffToolDispatcher.java` |
| Cheat Tester | `CheatTesterCommand`, `paper/cheattester/` |
| staff chat | `StaffChatCommand` and chat integration adapter |

For any write, trace past the command into the domain/coordinator/store. Permission checks at the command surface are not the final authority boundary.

## Staff mode and operational tools

Current merged staff-tool behavior is split rather than living in the command:

| File | Purpose |
| --- | --- |
| `paper/staff/StaffModeManager.java` | durable session lifecycle and restoration coordination |
| `paper/staff/StaffModeActivationCoordinator.java` | profile activation after durable state exists |
| `paper/staff/StaffStateCodec.java` | normal/staff player-state serialization/checksums |
| `paper/staff/StaffModeAccessPolicy.java` | explicit rank/profile access decisions |
| `paper/staff/StaffToolDefinition.java` | canonical slot/material/action definitions |
| `paper/staff/StaffToolDispatcher.java` | validates active owner/session/token/slot/material/rank and routes actions |
| `paper/staff/StaffToolCooldowns.java` | bounded per-action cooldown state |
| `paper/staff/StaffModeWorldInteractionListener.java` | staff-profile world/item interaction protections |
| `persistence/JdbcStaffSessionStore.java` | durable staff-session state |

The dispatcher is a routing surface. Inspect/freeze/reports/Cheat Tester/spectate/vanish/staff-chat actions should continue through their existing command/service boundary rather than gain a second authority implementation.

See [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]] for staff-facing behavior.

## Cheat Tester source map

The merged evidence-only tester is spread across Paper mechanics, domain state and durable recovery:

```text
paper/src/main/java/net/enthusia/staff/paper/cheattester/
paper/src/main/java/net/enthusia/staff/paper/command/CheatTesterCommand.java
domain/src/main/java/net/enthusia/staff/domain/cheattester/
persistence/src/main/java/net/enthusia/staff/persistence/JdbcCheatTesterSessionStore.java
persistence/src/main/resources/db/migration/V18__cheat_tester_session_journal.sql
```

Trace state-changing testers as:

```text
staff action
  -> CheatTesterCommand / StaffToolDispatcher
  -> tester policy/runtime
  -> durable V18 session journal before temporary mutation
  -> target-owned scheduler mutation/sampling
  -> exact cleanup/restoration verification
  -> terminal journal state only after verification
```

The fake-entity path additionally crosses the optional ProtocolLib adapter. Fake-base behavior is client-side virtual block rendering and coordinate-free audit rather than a real world-block transaction.

Key review hazards are stale player callbacks, disconnect/reconnect, plugin disable, fake-entity cleanup, inventory-lock overlap, ProtocolLib degradation and falsely marking a test terminal before restoration can be proved. See [[Cheat Tester]].

## Vanish source map

Core paths:

```text
paper/src/main/java/net/enthusia/staff/paper/visibility/
paper/src/main/java/net/enthusia/staff/paper/api/StaffVisibilityService.java
persistence/src/main/java/net/enthusia/staff/persistence/JdbcVanishStore.java
```

Key classes include `VanishManager`, `VanishAudienceCoordinator`, `VanishRankReconciliationPolicy`, `DefaultStaffVisibilityService`, and `ProtocolLibSpectatorTabPacketAdapter`.

Use [[Vanish Internals]] for the detailed event, session-fence, packet, scheduler, performance, and known-visibility-gap explanation rather than duplicating those internals here.

## Velocity composition root

Main package:

```text
velocity/src/main/java/net/enthusia/staff/velocity/
```

Start with `EnthusiaStaffVelocityPlugin.java`. Current responsibilities around it include:

- MariaDB/configuration startup;
- login and server-switch enforcement;
- player/server presence updates;
- protected network identity observation;
- persistent backend transport server;
- network and legacy Discord webhook outbox workers;
- LiteBans migration/shadow/cutover services;
- restricted website moderation/appeal API;
- orderly task/socket/database shutdown.

Important nearby files:

| File | Responsibility |
| --- | --- |
| `VelocityConfiguration.java` | proxy-owned settings and secret references |
| `VelocityRuntimeHealth.java` | proxy health/degradation state |
| `NetworkOutboxWorker.java` | durable backend delivery |
| `DiscordOutboxWorker.java` | durable webhook delivery |
| `WebsiteApiRuntime.java` | website bridge lifetime/bindings |
| `WebsiteApiServer.java` | restricted HTTP server |
| `WebsiteApiRequestDecoder.java` | bounded/authenticated request decoding |
| `WebsiteApiRouter.java` | route ownership |
| `WebsiteAppealEndpoint.java` / `WebsiteAppealWorkflowEndpoint.java` | appeal-facing bridge operations |

Do not describe a staff-bot module, account-link runtime or other composition classes that exist only on an unmerged branch as current `main` behavior.

## Domain source map

Main path:

```text
domain/src/main/java/net/enthusia/staff/domain/
```

| Area | Owns |
| --- | --- |
| `application/` | punishment creation/drafts/requests and sanction-change use cases |
| `auth/` | rank/action authority, including merged Discord-origin authorization policy |
| `moderation/` | moderation subjects, Minecraft/Discord identities and explicit enforcement scopes |
| `casefile/` | cases, visibility and review projections |
| `sanction/` | sanction state and exact change requests/results |
| `history/` | moderation timeline projections |
| `escalation/` | stable reasons/families, ordinals, decay and recommendation policy |
| `report/` | report submission/query/state/evidence policy |
| `inventory/` | inventory revisions, paths, patches, leases, confiscation/restoration models |
| `economy/` | economy moderation plans and operation/recovery state |
| `staff/` | durable staff-session records/snapshots |
| `cheattester/` | tester types, sessions/evidence and recovery contracts |
| `freeze/` | durable freeze policy/state |
| `alt/` | relationship/evidence/confidence/inheritance policy |
| `migration/` | import, shadow, cutover and reconciliation policy |
| `discord/` | legacy webhook delivery models |
| `website/` | sanitized projections and website actor/appeal models |
| `runtime/` | operational mode/state |
| `ports/` | interfaces implemented by storage/platform/provider adapters |

When the same business decision appears in both domain and a platform adapter, treat that duplication as a review smell.

## Discord moderation foundation source map

The current Discord expansion has three merged layers and a separate legacy webhook subsystem.

### Identity and scope domain

Primary source:

```text
domain/src/main/java/net/enthusia/staff/domain/moderation/
domain/src/main/java/net/enthusia/staff/domain/ports/DiscordModerationPersistenceStore.java
```

This layer models moderation subjects, Discord/Minecraft identity membership, link history/main-account semantics and platform-matched enforcement scopes. It does not call Discord.

### V19 persistence

Primary source:

```text
persistence/src/main/resources/db/migration/V19__discord_moderation_persistence.sql
persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordModerationPersistenceStore.java
persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordIdentityRepository.java
persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordLinkRepository.java
persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordMainAccountRepository.java
persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordOperationalRepository.java
persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordReplayGuard.java
```

V19 owns durable subject/link/main-account/enforcement-target/evidence-metadata/security-lock/reconciliation/maintenance foundations. `integration-tests/.../DiscordPersistenceSafetyIntegrationTest.java` is a key MariaDB test entry point.

### Discord-origin authorization

Primary source:

```text
domain/src/main/java/net/enthusia/staff/domain/auth/DiscordModerationAuthorizationService.java
domain/src/main/java/net/enthusia/staff/domain/auth/DiscordAuthorization*.java
domain/src/main/java/net/enthusia/staff/domain/auth/DiscordOperationPolicy.java
domain/src/main/java/net/enthusia/staff/domain/auth/DiscordConsequencePolicy.java
domain/src/main/java/net/enthusia/staff/domain/auth/DiscordPreconditionPolicy.java
domain/src/main/java/net/enthusia/staff/domain/auth/DiscordMinecraftAuthorization.java
docs/discord-authorization.md
```

Tests include authorization request/snapshot, target protection, consequence/operation matrix and cross-platform revalidation cases under `domain/src/test/java/net/enthusia/staff/domain/auth/`.

This layer defines policy; it is not proof that an interactive Discord command/runtime exists. See [[Discord Moderation Platform]].

## Persistence source map

Main path:

```text
persistence/src/main/java/net/enthusia/staff/persistence/
```

Runtime/database entry points:

- `MariaDb.java`
- `MariaDbRuntime.java`
- `persistence/src/main/resources/db/migration/`

Current merged Flyway history runs through **`V19__discord_moderation_persistence.sql`**. Applied V1–V19 migrations are immutable history; new schema work must first reconcile the live ceiling and add a forward migration.

Important stores include:

| Store | Durable responsibility |
| --- | --- |
| `JdbcModerationStore` | case/punishment creation transaction |
| `JdbcModerationHistoryStore` | bounded moderation history/case timeline reads |
| `JdbcExactSanctionMutationStore` | exact sanction lifecycle changes and audit linkage |
| `JdbcPunishmentDraftStore` | resumable punishment drafts |
| `JdbcPunishmentRequestStore` | approval-request workflow |
| `JdbcReportStore` and report-specific stores | submission, queries, state transitions, evidence retention |
| `JdbcPlayerDirectory` | UUID/name/alias/platform/presence persistence and lookup |
| `JdbcInventoryJournalStore` | inventory/confiscation/snapshot/restoration operation state |
| `JdbcInventoryPatchTransitions` | coherent queued-patch transitions |
| `JdbcEconomyJournalStore` | economy moderation operation journal |
| `JdbcStaffSessionStore` | durable staff mode/recovery state |
| `JdbcCheatTesterSessionStore` | V18-backed tester mutation/recovery journal |
| `JdbcVanishStore` | durable vanish intent |
| `JdbcFreezeStore` | durable freeze state |
| `JdbcNetworkIdentityStore` | protected network identity/alt data |
| `JdbcNetworkOutboxStore` | durable Paper-Velocity queue |
| `JdbcDiscordOutboxStore` | durable legacy Discord webhook queue |
| `JdbcDiscordModerationPersistenceStore` and related `JdbcDiscord*Repository` classes | V19 Discord moderation identity/link/operational foundation |
| `JdbcWebsiteModerationStore` | sanitized website moderation projections/actions |
| `JdbcWebsiteAppealWorkflowStore` | V17-backed appeal workflow state |

For a destructive workflow, find the transaction, unique/idempotency key, revision/fence, before snapshot, terminal state and recovery path—not only the nominal SQL statement.

## Protocol source map

Main path:

```text
protocol/src/main/java/net/enthusia/staff/protocol/
```

Key files:

- `PersistentChannelClient.java`
- `PersistentChannelServer.java`
- `EnvelopeAuthenticator.java`
- `ReplayGuard.java`
- `TlsContextLoader.java`
- `FrameTransport.java`
- `EnvelopeCodec.java`

Read these together with `JdbcNetworkOutboxStore`, consumer inbox/receipt handling, and `velocity/NetworkOutboxWorker.java`. The transport is at-least-once; durable idempotency and inbox/outbox semantics make retries safe.

Deep dive: [[Protocol and Network Traffic]].

## Feature traces

### Punishment creation

```text
/punish or filtered command
  -> PunishmentCommand / paper/punishment GUI
  -> PunishmentDraftWorkflow
  -> PunishmentService
  -> DefaultAuthorizationPolicy + EscalationEngine
  -> JdbcModerationStore transaction
  -> case + sanctions + audit + durable network/notification state
  -> Velocity/network enforcement
```

Review related `domain/application`, `domain/escalation`, `paper/punishment`, `JdbcModerationStore`, draft/request stores, and MariaDB tests.

### Punishment history and exact sanction change

```text
/history or /case
  -> HistoryCommand / CaseCommand
  -> moderation history queries
  -> JdbcModerationHistoryStore

/estaff sanction reduce|end|revoke|overturn
  -> SanctionLifecycleCommand
  -> SanctionChangeService
  -> locked authority/hierarchy recheck
  -> JdbcExactSanctionMutationStore
  -> append-only mutation/audit history
```

V14 introduced the persistence needed for this slice. Appeal-linked overturn paths must target the exact sanction, not every sanction in a case.

### Reports

```text
/report
  -> ReportCommand
  -> report domain policy
  -> JdbcReportSubmissionStore / replay checks
  -> bounded evidence persistence

/reports or GUI
  -> ReportsCommand / ReportGuiController
  -> JdbcReportQueryStore / JdbcReportStateStore
  -> optimistic revision transition
```

Also inspect `ChatContextBuffer`, `ReportEvidenceMaintenance`, report policy/GUI loaders and the RoseChat private-message provider boundary. See [[Reports and Evidence]].

### Inventory and Ender access

```text
/invsee or /endersee
  -> InventoryCommand
  -> InventoryCoordinator
  -> inventory domain revisions/leases/patch decisions
  -> JdbcInventoryJournalStore / JdbcInventoryPatchTransitions
  -> owning Paper scheduler mutation or queued login patch
```

High-risk boundaries are concurrent viewers, dirty-slot versus stale full-state writes, nested containers, offline ownership/save races, restart, server switching and login-time patch ordering.

### Item confiscation/restoration

```text
case/inspect action
  -> ConfiscationCoordinator
  -> exact item path/fingerprint + durable before snapshot
  -> inventory journal transaction
  -> verified deletion or recovery/quarantine

/case restoration
  -> CaseCommand
  -> original case-linked snapshot
  -> idempotent safe restore
```

Deep dive/procedure: [[Inventory and Confiscation Safety]].

### Economy moderation

```text
case-linked action
  -> EconomyCoordinator
  -> EnthusiaCurrencyGateway
  -> domain economy plan/journal state
  -> JdbcEconomyJournalStore
  -> supported EnthusiaCurrency moderation contract
```

Provider balance storage remains provider-owned; direct raw SQL is outside this boundary.

### Staff mode, Cheat Tester, vanish and freeze

```text
/staff
  -> StaffModeCommand
  -> StaffModeManager / activation/recovery collaborators
  -> JdbcStaffSessionStore
  -> owned player-state apply/verify/restore

staff hotbar or /stafftools
  -> StaffToolDispatcher / StaffToolsCommand
  -> canonical token/slot/material/rank validation
  -> existing action routes

/cheattester
  -> CheatTesterCommand / tester runtime
  -> JdbcCheatTesterSessionStore when temporary state is owned
  -> scheduler-owned probe / verified recovery

/vanish
  -> VanishManager / visibility service
  -> JdbcVanishStore

/freeze
  -> FreezeManager
  -> JdbcFreezeStore
```

Review owner/session-token fencing, Folia entity-scheduler ownership and durable-before-mutation ordering in addition to the nominal command path.

### Java/Bedrock identity

Relevant paths include `paper/client/FloodgateIntegration.java`, player/platform identity domain types, `JdbcPlayerDirectory.java`, Velocity presence/network identity observations and punishment/report presentation/lookup code.

Supported Floodgate evidence can persist a verified platform. Missing/incompatible evidence remains `UNKNOWN`; proxy presence is unverified for platform and must not downgrade a verified record. `*` names are aliases, not proof of Bedrock.

### Alts/network identity

Relevant paths:

- `common/security/NetworkIdentityProtector.java`
- `common/security/HmacTokenService.java`
- `domain/alt/`
- `JdbcNetworkIdentityStore.java`
- Velocity login/session observation and `/alts`/`/alt` handling.

Protect raw addresses from ordinary logs, Discord, public/site output and staff-facing equality workflows.

### Legacy Discord webhook delivery

```text
committed producer transaction
  -> discord_outbox
  -> JdbcDiscordOutboxStore
  -> DiscordEventRenderer bounded/privacy projection
  -> Velocity DiscordOutboxWorker
  -> approved webhook route
```

The renderer is an explicit final projection boundary; the worker must not blindly post raw stored payload JSON. Producers should still avoid storing unnecessary sensitive notification data. See [[Discord Delivery]].

### Discord moderation expansion

```text
moderation subject / identity / scope domain
  -> Discord moderation persistence ports/JDBC + V19
  -> Discord-origin authorization service
  -> future runtime adapters only after separately merged/validated
```

The first three foundation layers are merged. The finished linking command flow, interactive staff bot and Discord side effects are not implied by those layers. See [[Discord Moderation Platform]].

### Website and appeals

Current aggregate source includes both the Velocity bridge and scoped site component work.

Bridge paths include:

- `domain/website/`
- `JdbcWebsiteModerationStore.java`
- `JdbcWebsiteAppealWorkflowStore.java`
- `WebsiteApiRuntime.java`
- `WebsiteApiServer.java`
- `WebsiteApiRequestDecoder.java`
- `WebsiteApiRouter.java`
- `WebsiteAppealEndpoint.java`
- `WebsiteAppealWorkflowEndpoint.java`
- `integration-tests/.../WebsiteAppealWorkflowIntegrationTest.java`

Site/component code lives under `components/` when present. Review authentication/authorization, exact-sanction appeal binding, privacy projection, replay/rate/body bounds, and aggregate/standalone parity separately from private deployment acceptance.

### LiteBans migration/shadow/cutover

Relevant areas:

- `domain/migration/`
- `persistence/.../migration/`
- Velocity migration/shadow/cutover runtime
- `docs/litebans-migration.md`
- `docs/shadow-mode.md`
- `docs/cutover.md`
- `docs/rollback.md`
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]

Review dry-run/source schema interpretation, external ID mapping, idempotent resume/rerun, expiration/identity parity, shadow comparison dimensions, writer fencing, final reconciliation and post-cutover emergency freeze.

## Configuration source map

Current configuration is still evolving toward the full modular goals.

| Location | Owns |
| --- | --- |
| `paper/src/main/resources/config.yml` | current Paper runtime settings, including staff-tool/Cheat Tester settings |
| `paper/src/main/resources/reason-policies.yml` | punishment reason/escalation source |
| `paper/src/main/resources/reports.yml` | report policy |
| `paper/src/main/resources/gui/reports.yml` | report inventory presentation |
| `paper/src/main/resources/plugin.yml` | commands, permissions, ranks, soft dependencies |
| `paper/.../config/ReasonPolicyConfigurationLoader.java` | reason-policy validation |
| `paper/.../report/` configuration loaders | report policy/GUI validation and immutable snapshots |
| `domain/.../ports/AtomicReasonPolicyRepository.java` | atomic policy publication boundary |
| `velocity/.../VelocityConfiguration.java` | proxy-owned settings and secret references |

Do not assume `/estaff reload` applies every setting. Focused pages such as [[Configuration]], [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]] and [[Cheat Tester]] state restart-only boundaries where applicable.

## Finding tests

Unit tests normally live beside each module under `src/test/java`; MariaDB and cross-module scenarios live under `integration-tests/src/test/java`.

Useful search anchors by change type:

- authorization: `DefaultAuthorizationPolicy`, approval/hierarchy tests, `DiscordModerationAuthorizationService` and its operation/target/cross-platform tests;
- sanctions: `SanctionChange`, history and exact-mutation tests;
- reports: report submission/state/GUI/configuration/evidence maintenance tests;
- identity: Floodgate/platform/player-directory plus moderation-subject/link tests;
- staff tools: `StaffToolDispatcher`, staff-session/recovery/scheduler tests;
- Cheat Tester: tester domain/runtime/recovery/fake-base tests plus V18 persistence integration;
- vanish: visibility/rank/session-fence/ProtocolLib tests;
- protocol: `PersistentChannelTransportTest`, `EnvelopeAuthenticatorTest`, `ReplayGuardTest`;
- persistence: MariaDB integration tests around the exact store/migration, including `DiscordPersistenceSafetyIntegrationTest` for V19 foundations;
- website: appeal workflow/auth/privacy/component tests;
- migration: import/shadow/cutover/recovery integration tests.

See [[Build and Testing]] for what each test category proves and [[Code Review Guide]] for the failure scenarios to look for.

## Deep technical references

- [[Architecture]] — system/module ownership.
- [[Code Review Guide]] — cross-cutting review discipline.
- [[Protocol and Network Traffic]] — authenticated distributed transport.
- [[Discord Moderation Platform]] — merged Discord foundations versus future runtime.
- [[Discord Delivery]] — current webhook delivery subsystem.
- [[Cheat Tester]] — tester/fake-entity/fake-base behavior and recovery.
- [[Vanish Internals]] — visibility/scheduler/packet details.
- [[Inventory and Confiscation Safety]] — destructive player-state invariants.
- [[Recovery and Troubleshooting]] — runtime failure/recovery model.
- [[Build and Testing]] — exact validation and evidence interpretation.