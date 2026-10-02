# Architecture

EnthusiaStaff is a distributed moderation platform spanning Minecraft servers/proxy, MariaDB, Discord, private service boundaries, and public/staff web surfaces. Domain policy and durable state are separated from Paper, Velocity, StaffBot/JDA, Cloudflare/browser UI, provider adapters, and JDBC implementation details.

## Quick orientation

- Current product state: [[Implementation Status]]
- Detailed source map: [[Developer Code Guide]]
- Discord product/runtime: [[Discord Moderation Platform]] and [[Staff Bot Runtime and Operations]]
- Public site/web APIs: [[Website and Web API]]
- Review invariants: [[Code Review Guide]]
- Evidence: [[Build and Testing]]
- Paper/Velocity transport: [[Protocol and Network Traffic]]

## Deployable shape

The root `runtimeJars` task currently builds/verifies four Java runtime artifacts:

1. `EnthusiaStaff-Paper-<version>.jar`
2. `EnthusiaStaff-AuthorityBridge-<version>.jar`
3. `EnthusiaStaff-Velocity-<version>.jar`
4. `EnthusiaStaff-StaffBot-<version>.jar`

The authority bridge is a narrow transition runtime, not another full Paper feature implementation. StaffBot is a standalone Java/JDA application, not a Minecraft plugin.

Web components deploy separately:

- `components/enthusia-site/` — public Enthusia site + Cloudflare Pages Functions;
- `moderation-web/` — staging-only Cloudflare Worker/static-assets staff moderation workspace.

## Gradle modules

| Module | Responsibility |
| --- | --- |
| `common` | identifiers, validation, security/crypto primitives, bounded utilities |
| `domain` | business policy, authorization, application services, state machines and ports |
| `integration-contracts` | supported compile-time contracts for Enthusia-owned providers |
| `discord-platform-api` | provider-neutral managed-role contract |
| `persistence` | MariaDB/Flyway/JDBC, transactions, leases, journals, inbox/outbox and recovery |
| `protocol` | authenticated Paper–Velocity transport, replay and acknowledgements |
| `paper` | commands, GUIs/listeners, Minecraft-side effects and provider adapters |
| `paper-authority-bridge` | narrow transition/authority bridge runtime |
| `velocity` | proxy enforcement, network identity, distributed workers, migration and website API |
| `staff-bot` | JDA gateway, Discord staff UX/effects/reconciliation, private read/launch services, health |
| `integration-tests` | MariaDB/cross-module/recovery tests; never deployed |

## Dependency direction

```text
Paper / AuthorityBridge / Velocity / StaffBot / Web / provider adapters
                              |
                              v
                     domain policy / ports
                         ^           ^
                         |           |
                   persistence    protocol
                         ^
                         |
             integration/service contracts
```

The rule is: **domain policy decides; runtime adapters translate and apply effects; persistence implements durable ports; public/browser surfaces receive only explicitly approved projections.**

`integration-contracts` and `discord-platform-api` define supported boundaries. They must not become alternate homes for punishment ladders, rank hierarchy, linking rules, target protection, transaction policy, or recovery decisions.

## Runtime ownership

### Paper

Paper owns server-local Bukkit/Paper state:

- commands and inventory GUIs;
- Staff Mode and staff tools;
- vanish/freeze/player-state enforcement;
- inventory/Ender/confiscation effects;
- Minecraft-side account-linking adapters;
- Paper-side provider integrations.

Blocking database/network/provider work stays off the game/entity thread. Live player/entity mutation returns to the supported owning scheduler. Async callbacks that can outlive a player session require fencing.

Player-originated destructive Paper actions also obey the current Staff Mode duty requirement where the owning action requires it. That Paper-local rule does not replace independent Discord/global/website authority.

### Paper authority bridge

`paper-authority-bridge` packages a deliberately constrained transition runtime. Its build verifies required migration/runtime entries and rejects selected full moderation/migration classes that must not leak into the bridge.

Review it as a containment boundary: it should expose only the transition authority needed by its contract and should remain removable once the transition no longer needs it.

### Velocity

Velocity owns proxy/network coordination:

- login/server-switch enforcement;
- protected network identity/presence;
- Paper–Velocity transport workers;
- legacy Discord webhook delivery;
- migration/shadow/cutover coordination;
- authoritative website API server/router.

Velocity event threads must not wait on JDBC, HTTP, filesystem or socket I/O. Startup/reload/shutdown should be reviewed as an atomic lifecycle publication problem.

### StaffBot

StaffBot owns the privileged Discord Gateway/JDA lifecycle:

- Discord application/guild/environment fencing;
- slash/context/component moderation UX;
- linked-staff actor resolution and action-time authorization;
- Discord punishment execution/reconciliation where enabled;
- private moderation-read API;
- signed moderation-workspace launch issuance;
- health/readiness and bounded worker lifecycle.

A Discord role or visible command is never sufficient authority by itself. StaffBot must resolve current Enthusia staff authority and target state.

### Web surfaces

There are distinct web boundaries:

1. **Velocity website API** — authoritative public projections and authenticated punishment-code/appeal/reviewer workflow.
2. **`components/enthusia-site/`** — public site and Cloudflare Pages Functions.
3. **`moderation-web/`** — staging-only browser staff workspace.
4. **StaffBot private moderation-read API** — protected data source for that staging workspace.

The browser/Cloudflare layer is not a privileged database client or punishment writer. See [[Website and Web API]].

## Durable authority and migrations

MariaDB stores core moderation, recovery, identity, report/evidence, session, player-state, network delivery, Discord moderation/linking, website appeal, migration and audit state.

Current merged Flyway history reaches **V20**:

- V17 — website appeal workflow;
- V18 — Cheat Tester session journal;
- V19 — Discord moderation persistence/reconciliation;
- V20 — Discord/Minecraft account linking.

Applied migrations are immutable. Later versions visible only on open branches are not current schema.

## High-risk write pattern

A high-risk mutation normally needs an explicit sequence such as:

1. normalize/validate identity and input;
2. resolve current actor/target authority;
3. establish idempotency/durable intent where required;
4. lock/lease/fence the authoritative state;
5. reread and reauthorize the current revision;
6. persist before-state/recovery information before destructive effects where required;
7. atomically commit domain state/audit/outbox where the model requires it;
8. apply platform/provider effects through the owning adapter;
9. verify/reconcile the resulting external state;
10. record terminal/acknowledged state or preserve ambiguity for recovery.

A sent Discord request, HTTP 2xx, queued packet, or completed browser request is not enough to declare an authoritative effect successful.

## Distributed failure models

Different boundaries fail differently:

- Paper–Velocity transport is at-least-once and relies on replay protection, durable inbox/outbox and idempotent consumers.
- Discord native effects may be ambiguous after timeouts/restarts and sometimes cannot be safely blind-retried.
- Legacy Discord webhooks are outbound notifications and may duplicate around remote-success/local-crash windows.
- Website/browser requests require authentication, replay/body/rate bounds and strict public/private projections.
- Provider operations must use supported contracts and preserve recoverability when external truth is uncertain.

## Account linking and Discord roles

Discord/Minecraft linking is now a real merged domain/runtime, backed by V20. Link codes are one-use/short-lived and hashed at rest; ownership/history and main-account selection are durable.

`discord-platform-api` separately defines a provider-neutral managed-role contract. It does not mean all old DiscordSRV role-sync consumers have already migrated. Consumer plugins should not import JDA/StaffBot internals.

## Security/privacy boundaries

Architecture reviews should explicitly identify whether data is:

- public;
- staff-private;
- evidence-private;
- account-link/network identity data;
- a credential/signing secret;
- production topology/configuration.

Public/site/Discord projections use explicit allowlists. Raw database/domain objects should not be serialized just because they are convenient.

## Safe-failure principles

- stale revisions/confirmations do not overwrite newer authority/state;
- partial external effects remain reconcilable rather than becoming false success;
- inventory/economy/confiscation ambiguity preserves recovery evidence;
- restart/reconnect does not let stale callbacks/workers mutate a replacement runtime;
- optional provider loss disables only dependent behavior when safe;
- Discord ambiguous effects reconcile instead of blind retry;
- account-link replay/concurrency cannot create ambiguous ownership;
- browser/public APIs never become implicit moderation authority;
- production authority does not move merely because implementation merged.

## Important source entry points

- [Paper plugin](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java)
- [Authority bridge build/containment](https://github.com/wsg138/EnthusiaStaff/blob/main/paper-authority-bridge/build.gradle.kts)
- [Velocity plugin](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java)
- [Website API router](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRouter.java)
- [StaffBot application](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotApplication.java)
- [StaffBot runtime](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotRuntime.java)
- [Discord platform API](https://github.com/wsg138/EnthusiaStaff/tree/main/discord-platform-api)
- [Persistence](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence)
- [Flyway migrations](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/resources/db/migration)

## Continue deeper

- [[Developer Code Guide]] — exact source ownership.
- [[Code Review Guide]] — failure/review checklist.
- [[Discord Moderation Platform]] — Discord product behavior/status.
- [[Staff Bot Runtime and Operations]] — bot build/config/deploy/recovery.
- [[Website and Web API]] — public/private web architecture and routes.
- [[Protocol and Network Traffic]] — Paper–Velocity transport.
- [[Core Platform and Infrastructure]] — runtime/platform hub.
- [[Build and Testing]] — evidence interpretation.