# Architecture

EnthusiaStaff is a distributed moderation platform, not a single Bukkit command plugin. Domain policy and durable state are separated from Paper, Velocity, MariaDB implementation details, the website, Discord and optional provider adapters.

## Quick orientation

- **What is merged?** [[Implementation Status]]
- **Where does a feature live?** [[Developer Code Guide]]
- **What should a reviewer verify?** [[Code Review Guide]]
- **How do Paper and Velocity communicate?** [[Protocol and Network Traffic]]
- **How is the change proven?** [[Build and Testing]]
- **Deeper source-controlled architecture:** [`docs/architecture.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/architecture.md)

## Deployable shape

Current merged `main` builds exactly two Minecraft runtime artifacts:

1. `EnthusiaStaff-Paper-<version>.jar`
2. `EnthusiaStaff-Velocity-<version>.jar`

The planned interactive Discord staff bot is a separate runtime/application boundary and is **not** part of the current merged runtime modules.

Internal modules:

| Module | Responsibility |
| --- | --- |
| `common` | shared identifiers, validation, cryptography/security primitives and bounded utilities |
| `domain` | business policy, authorization, application services, state machines and ports |
| `integration-contracts` | stable compile-time contracts for supported Enthusia-owned providers |
| `persistence` | MariaDB bootstrap, Flyway, JDBC stores, transactions, leases, journals, inboxes/outboxes, recovery |
| `protocol` | authenticated Paper–Velocity transport, replay protection and acknowledgements |
| `paper` | commands, GUIs/listeners and server-local/player-state adapters |
| `velocity` | proxy enforcement, network identity, network workers, migration and restricted website bridge |
| `integration-tests` | MariaDB/cross-module/concurrency/recovery validation; never deployed |

Root references: [build](https://github.com/wsg138/EnthusiaStaff/blob/main/build.gradle.kts) and [settings](https://github.com/wsg138/EnthusiaStaff/blob/main/settings.gradle.kts).

## Dependency direction

```text
Paper / Velocity / website adapters
              |                  provider adapters
              |                       |        \
              v                       v         v
     domain application services  domain   integration-contracts
              |                            (compile-time contracts)
              v
         domain ports/models
              ^
              |
     persistence / protocol adapters
```

The practical rule is: **domain policy decides; platform adapters translate/apply runtime effects; persistence implements durable ports.** `integration-contracts` is the explicit compile-time boundary used by supported Enthusia-owned provider adapters; it does not become a second home for business policy.

A command, GUI, event listener, website route, future Discord runtime or provider adapter should not gain its own copy of punishment ladders, rank hierarchy, transaction policy or recovery decisions.

## Bounded contexts

Principal domains include:

- identity, moderation subjects and player directory;
- cases, punishments, sanctions and escalation;
- reports and appeals;
- alts and protected network identity;
- inventory, economy, market and reputation moderation;
- staff sessions, staff tools, Cheat Tester, vanish and freeze;
- Discord webhook delivery and the separate Discord moderation identity/persistence/authorization foundation;
- migration/shadow/cutover;
- verification, audit and configuration;
- external integrations.

Each context should expose a stable application-service/port boundary rather than allowing unrelated modules to reach directly into its persistence or platform implementation.

## Paper ownership

Paper owns server-local state and Bukkit/Paper interactions:

- staff commands and GUIs;
- player/entity mutations;
- staff-mode state application/restoration;
- Cheat Tester/fake-base server-local effects;
- vanish visibility application;
- freeze restrictions;
- inventory/Ender state;
- report/client context capture;
- Paper-side provider adapters.

Important composition paths:

- [EnthusiaStaffPaperPlugin](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java)
- [PaperRuntimeLifecycle](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeLifecycle.java)
- [PaperRuntimeComponents](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java)
- [PaperStorageBindings](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperStorageBindings.java)
- [PaperCommandRegistrar](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java)
- [PaperIntegrationManager](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperIntegrationManager.java)
- [PaperResourceCloser](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperResourceCloser.java)

Blocking DB/network/provider work must not run on the game/entity thread. Player/entity mutation must return to the supported owning scheduler. Session/revision fencing is required when an asynchronous callback may outlive a disconnect/reconnect.

A mocked scheduler or standalone Paper boot does not prove real Folia region/entity ownership. See [[Build and Testing]].

## Velocity ownership

Velocity owns network-facing state and coordination such as:

- login/server-switch enforcement;
- network-wide player/server presence;
- protected network-identity observations;
- persistent backend transport server;
- network/Discord webhook delivery workers;
- migration/shadow/cutover coordination;
- the restricted website bridge.

Important paths:

- [EnthusiaStaffVelocityPlugin](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java)
- [VelocityConfiguration](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java)
- [NetworkOutboxWorker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/NetworkOutboxWorker.java)
- [DiscordOutboxWorker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/DiscordOutboxWorker.java)
- [WebsiteApiServer](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiServer.java)

Velocity event threads must not block on JDBC, HTTP, filesystem or socket I/O. Startup/reload/shutdown changes must be reviewed as lifecycle publication/rollback problems, not only as individual methods.

The future interactive Discord staff bot is not a responsibility of the current Velocity artifact merely because legacy webhook delivery exists there.

## MariaDB authority and persistence

MariaDB is the durable authority for moderation/recovery state such as cases, sanctions, identity, reports/evidence, staff sessions, player-state journals, network/Discord delivery, Discord moderation foundations, migration state, configuration versions, audit, leases and quarantine.

Primary entry points:

- [MariaDb](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDb.java)
- [MariaDbRuntime](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDbRuntime.java)
- [persistence package](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence)
- [Flyway migrations](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/resources/db/migration)

Current merged `main` contains migrations through **`V19__discord_moderation_persistence.sql`**. V18 owns the Cheat Tester session journal; V19 owns the current Discord moderation persistence foundation. Flyway history is forward-only and applied migrations are immutable; future schema changes add a new migration after reconciling the live migration ceiling.

## Authoritative write pattern

Not every workflow has identical steps, but high-risk writes should make these boundaries explicit:

1. normalize/validate input and identity;
2. authorize through the central application policy;
3. establish idempotency and durable intent where required;
4. acquire the required row lock/lease/fence;
5. reread/revalidate current revision and authority;
6. persist a before snapshot before destructive external/player-state effects;
7. commit the authoritative domain state/audit/outbox atomically where the model requires it;
8. apply platform/provider/network side effects idempotently;
9. verify resulting state;
10. record acknowledgement/terminal state or quarantine ambiguity.

Success should not be reported merely because bytes were sent or an external call returned before the durable result is known.

## Distributed delivery

Paper–Velocity transport is at-least-once. Correctness comes from authenticated/versioned messages, replay protection, durable outbox/inbox state, idempotent consumers, meaningful acknowledgements, bounded queues/backoff, reconnect/recovery and stale-worker fencing.

Do not describe the transport as exactly-once. Deep dive: [[Protocol and Network Traffic]].

Legacy Discord webhook delivery is a separate at-least-once external boundary with its own renderer/privacy/retry policy. See [[Discord Delivery]].

## Safe failure principles

- A punishment cannot partially apply an intended combined decision.
- An exact sanction change cannot mutate unrelated sanctions.
- Stale revisions cannot overwrite newer state.
- Inventory/economy/confiscation ambiguity preserves recovery evidence or enters quarantine.
- Cheat Tester does not become terminal until owned cleanup/restoration is verified.
- Migration mismatch blocks authority transition.
- Missing optional integrations disable only dependent behavior when safe.
- MariaDB/proxy/provider loss blocks actions whose correctness cannot be proved.
- Restart/reconnect work must not let stale callbacks mutate new player sessions.
- Discord identity/schema/authorization foundations must not be mistaken for a live bot or external side effect.

## Restricted website boundary

The Velocity website bridge is a restricted authenticated boundary for trusted site integration. It must not become a casually exposed public moderation API.

Relevant paths include `WebsiteApiRuntime`, `WebsiteApiServer`, `WebsiteApiRequestDecoder`, `WebsiteApiRouter`, `WebsiteAppealEndpoint` and `WebsiteAppealWorkflowEndpoint` under the Velocity module.

Only sanitized projections may cross that boundary. See [[Privacy and Data Handling]] and [[Integrations, Migration, and Release Readiness]].

## Stable service boundaries

Important internal/public service contracts include `StaffVisibilityService`, `PunishmentQueryService`, `SanctionQueryService`, `StaffSessionService`, `StaffModeQueryService`, `InventoryLockService`, `AltRelationshipService` and `PlayerDirectoryService`.

Other plugins should depend on supported service/contracts rather than mutable EnthusiaStaff implementation internals.

## How to continue

- Need the exact class/store/test trace? [[Developer Code Guide]]
- Reviewing a change? [[Code Review Guide]]
- Debugging a failure? [[Recovery and Troubleshooting]]
- Validating a claim? [[Build and Testing]]
- Discord-specific foundations/runtime distinction? [[Discord Moderation Platform]]
- Looking at a feature family? [[Core Platform and Infrastructure]], [[Moderation, Punishments, and Reports]], [[Staff Tools, Investigations, and Player-State Safety]], or [[Integrations, Migration, and Release Readiness]].