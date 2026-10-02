# Developer Code Guide

This is the detailed source map for EnthusiaStaff. Use [[Developer Guide Index]] when you only need an entry point, and [[Code Review Guide]] when you need cross-cutting review invariants.

> **Evidence boundary:** a source path proves only that code exists. Use [[Implementation Status]] and [[Build and Testing]] before making staging/production claims.

## Repository map

| Path | Owns | Deployment |
| --- | --- | --- |
| `common/` | identifiers, validation, crypto/security primitives, bounded utilities | shared library |
| `domain/` | business policy, authorization, application services, state machines and ports | shared library |
| `integration-contracts/` | supported compile-time contracts for Enthusia-owned providers | contract dependency |
| `discord-platform-api/` | provider-neutral managed Discord role contract | contract dependency |
| `persistence/` | MariaDB/Flyway/JDBC, transactions, leases, journals, inbox/outbox, recovery | shared into runtimes |
| `protocol/` | authenticated Paper–Velocity transport, replay, ACK/reconnect | shared into runtimes |
| `paper/` | commands, GUIs/listeners, player/entity state, Minecraft linking/provider adapters | Paper plugin |
| `paper-authority-bridge/` | narrow transition/authority Paper runtime | separate Paper plugin |
| `velocity/` | proxy enforcement, network identity, transport workers, migration, website API | Velocity plugin |
| `staff-bot/` | standalone JDA runtime, Discord moderation/read/effects, private web-read services | Java application |
| `moderation-web/` | staging Cloudflare staff moderation workspace | Cloudflare Worker/static assets |
| `components/enthusia-site/` | synchronized public Enthusia site + Pages Functions | site component |
| `integration-tests/` | MariaDB/cross-module/concurrency/recovery/migration tests | never deployed |
| `docs/` | architecture/security/migration/operations reference | documentation |
| `docs/wiki/pages/` | repository-managed GitHub Wiki source | published Wiki |

## Dependency direction

```text
Paper / AuthorityBridge / Velocity / StaffBot / Web / provider adapters
                              |
                              v
                    domain application policy
                         ^            ^
                         |            |
                    persistence    protocol
                         ^
                         |
              provider/service contracts
```

`integration-contracts` and `discord-platform-api` define boundaries; they should not contain duplicate punishment/rank/recovery policy. Browser/JDA/Paper/Velocity handlers translate requests/effects and delegate to the owning application service.

## Main composition roots

### Paper

- [`EnthusiaStaffPaperPlugin.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java)
- [`PaperRuntimeLifecycle.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeLifecycle.java)
- [`PaperRuntimeComponents.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java)
- [`PaperStorageBindings.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperStorageBindings.java)
- [`PaperCommandRegistrar.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java)

### Velocity

- [`EnthusiaStaffVelocityPlugin.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java)
- [`VelocityConfiguration.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java)
- [`NetworkOutboxWorker.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/NetworkOutboxWorker.java)
- [`WebsiteApiRuntime.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRuntime.java)

### StaffBot

- [`StaffBotApplication.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotApplication.java)
- [`StaffBotRuntime.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotRuntime.java)
- [`StaffBotConfiguration.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotConfiguration.java)
- [`JdaDiscordGateway.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaDiscordGateway.java)

### Web components

- [`components/enthusia-site/`](https://github.com/wsg138/EnthusiaStaff/tree/main/components/enthusia-site)
- [`moderation-web/`](https://github.com/wsg138/EnthusiaStaff/tree/main/moderation-web)

## MariaDB and migrations

Start with:

- [`MariaDb.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDb.java)
- [`MariaDbRuntime.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDbRuntime.java)
- [`persistence/src/main/java/net/enthusia/staff/persistence/`](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence)
- [`db/migration/`](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/resources/db/migration)

Current migration ceiling is **V20**:

- V17 — website appeal workflow;
- V18 — Cheat Tester session journal;
- V19 — Discord moderation persistence/reconciliation;
- V20 — Discord/Minecraft account linking.

When changing durable behavior, trace: domain port → JDBC implementation → transaction boundary → table/constraint/index/migration → integration tests → restart/recovery path.

## Discord / StaffBot source map

Use [[Discord Moderation Platform]] for product semantics and [[Staff Bot Runtime and Operations]] for runtime/deployment. The main code paths are below.

### Staff moderation/read UX

```text
staff-bot/.../StaffModerationRuntime.java
staff-bot/.../StaffModerationController.java
staff-bot/.../StaffModerationReadService.java
staff-bot/.../JdaStaffModerationListener.java
staff-bot/.../LinkedStaffActorResolver.java
staff-bot/.../StaffReadAuthorization.java
```

These own Discord-side interaction translation, read/panel flow, linked-staff resolution and component handling. Authorization policy remains centralized; command visibility/Discord roles are not enough.

### Discord punishment execution

```text
staff-bot/.../DiscordPunishmentRuntime.java
staff-bot/.../DiscordPunishmentService.java
staff-bot/.../DiscordPunishmentWorker.java
staff-bot/.../DiscordPunishmentCoordinator.java
staff-bot/.../DiscordPunishmentAuthorization.java
staff-bot/.../JdaDiscordPunishmentGateway.java
staff-bot/.../JdaKickEnforcer.java
staff-bot/.../JdaNativeBanEnforcer.java
staff-bot/.../JdaMuteRoleOwnership.java
staff-bot/.../JdaPunishmentNotifier.java
```

Review this path as a distributed external-effect workflow. Durable intent/reconciliation and ambiguity handling matter as much as the JDA call itself.

### Discord/Minecraft account linking

Domain/application:

- [`AccountLinkingService.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/application/AccountLinkingService.java)
- account-link recovery/main-selection/DiscordSRV migration services in the same application package.

Paper runtime:

- [`PaperAccountLinkRuntime.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/account/PaperAccountLinkRuntime.java)
- `/link` and `/unlink` command wiring in Paper command registration/plugin metadata.

Persistence:

- [`JdbcAccountLinkingStore.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcAccountLinkingStore.java)
- [`JdbcDiscordLinkRepository.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordLinkRepository.java)
- [`JdbcDiscordIdentityRepository.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordIdentityRepository.java)
- `V20__discord_account_linking.sql`.

Tests include V20 account-linking/replay/atomic-unlink/restart paths under `integration-tests/src/test/java/net/enthusia/staff/integration/`.

### Managed-role API

[`discord-platform-api/`](https://github.com/wsg138/EnthusiaStaff/tree/main/discord-platform-api) contains provider-neutral managed-role concepts such as namespaces, role keys, claims, clients and result states.

A consumer should not import JDA or StaffBot persistence types. Desired membership represents the combined current state of linked Minecraft identities according to the contract.

## Website / API source map

Use [[Website and Web API]] for the trust model and current route list.

### Velocity authoritative API

```text
velocity/.../WebsiteApiRuntime.java
velocity/.../WebsiteApiServer.java
velocity/.../WebsiteApiAuthenticator.java
velocity/.../WebsiteApiRequestDecoder.java
velocity/.../WebsiteApiRouter.java
velocity/.../WebsiteAppealEndpoint.java
velocity/.../WebsiteAppealWorkflowEndpoint.java
```

Persistence:

```text
persistence/.../JdbcWebsiteModerationStore.java
persistence/.../JdbcWebsiteAppealWorkflowStore.java
V17__website_appeal_workflow.sql
```

Tests: `WebsiteApiRouterTest`, `WebsiteApiServerTest` plus relevant persistence/integration coverage.

### Public site

`components/enthusia-site/` contains the public static site and Cloudflare Pages Functions. Current function areas include appeals/eligibility, reviewer appeal routes, health and leaderboard proxying.

Treat this as a synchronized component. Check component parity metadata/process before editing aggregate component source.

### Staging moderation workspace

`moderation-web/` contains the Cloudflare Worker/static-assets staff workspace. Its frontend assets are built from the StaffBot moderation preview resources and protected by signed launch/session/read boundaries.

The StaffBot-side private read service lives under:

```text
ModerationReadApiServer.java
ModerationReadApiAuthenticator.java
ModerationReadRequestAuthorizer.java
ModerationReadReplayGuard.java
ModerationReadApiRateLimiter.java
ModerationReadApiService.java
ModerationPreviewHostedLaunchIssuer.java
```

This is a read/simulation boundary, not a second punishment-write API.

## Punishment / sanction source trace

For a normal punishment mutation trace:

1. platform command/GUI/Discord/website adapter parses the request;
2. player/subject identity resolves;
3. central authorization/punishment/sanction application service evaluates actor, target, scope and current state;
4. JDBC store locks/revalidates inside the authoritative transaction;
5. case/sanction/audit/outbox/recovery state commits as required;
6. platform/provider effects occur through the owning adapter;
7. ambiguous external outcomes reconcile rather than being guessed successful.

Exact sanction reduce/end/revoke/overturn paths must operate on the intended sanction ID and preserve unrelated sanctions.

Feature hub: [[Moderation, Punishments, and Reports]].

## Reports and evidence

Look under:

```text
domain/... report/evidence application + policy
persistence/... report/evidence JDBC stores
paper/... report commands/GUI/renderers
integration-tests/... report restart/concurrency/evidence tests
```

Active Discord evidence/case/alert expansion may exist on an open PR. Do not use branch-only paths as current merged behavior until that work lands.

## Staff mode / player-state safety

Important current areas:

- Paper staff session/profile/runtime dispatcher;
- Staff Mode authority checks around destructive Paper mutations;
- vanish/freeze/follow/spectate/teleport adapters;
- inventory/Ender/confiscation journals and restoration;
- Cheat Tester runtime and V18 recovery journal.

Use [[Staff Tools, Investigations, and Player-State Safety]], [[Vanish Internals]], [[Inventory and Confiscation Safety]], and [[Cheat Tester]] rather than duplicating those internals here.

## Paper–Velocity protocol

Primary transport classes:

- [`PersistentChannelClient.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/protocol/src/main/java/net/enthusia/staff/protocol/PersistentChannelClient.java)
- [`PersistentChannelServer.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/protocol/src/main/java/net/enthusia/staff/protocol/PersistentChannelServer.java)
- [`EnvelopeAuthenticator.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/protocol/src/main/java/net/enthusia/staff/protocol/EnvelopeAuthenticator.java)
- [`ReplayGuard.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/protocol/src/main/java/net/enthusia/staff/protocol/ReplayGuard.java)
- persistence inbox/outbox stores;
- Paper/Velocity runtime workers.

Deep dive: [[Protocol and Network Traffic]].

## Provider integrations

Provider contracts live in `integration-contracts/`; platform adapters live in the owning Paper/Velocity/runtime integration packages. Never bypass supported provider APIs with raw SQL or reflective internals merely because a provider is installed.

For Discord roles specifically, use `discord-platform-api`, not direct JDA from consumer plugins.

## Tests by risk

| Risk | Start with |
| --- | --- |
| pure domain authorization/policy | `domain/src/test/java/` |
| JDBC/schema/transactions | `persistence/src/test/java/` + `integration-tests/` |
| account linking | V20 integration tests + domain/application tests |
| Discord interactions/effects | `staff-bot/src/test/java/` + relevant integration tests |
| website API | Velocity website tests + persistence/integration tests |
| moderation web | `moderation-web` `npm run check` + protected staging workflow |
| Paper scheduler/player state | `paper/src/test/java/` + representative runtime staging |
| distributed transport | `protocol` tests + integration/runtime reconnect/outage evidence |
| provider behavior | adapter tests + provider-present/missing/incompatible runtime validation |

## Before modifying a path

Ask:

1. Which domain rule owns this behavior?
2. Which durable store/migration owns its state?
3. Which runtime owns the external/player effect?
4. Which trust boundary does the data cross—Minecraft, Discord, private service, browser, or public API?
5. What happens after timeout/restart/replay/duplicate delivery?
6. Which test proves policy versus SQL versus adapter behavior?
7. Which staging/production claim still requires runtime evidence?

## Current development boundaries

Open work may currently exist for DiscordSRV console replacement, managed-role parity, expanded Discord evidence/case/alerts, cross-platform moderation and expanded website appeal lifecycle. Those branches are useful review context but are **not** current source-of-truth behavior until merged.

## See also

- [[Developer Guide Index]]
- [[Architecture]]
- [[Code Review Guide]]
- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Website and Web API]]
- [[Build and Testing]]
- [[Implementation Status]]