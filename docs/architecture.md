# EnthusiaStaff architecture

## Status and scope

This document is the architecture checkpoint for the Enthusia Network moderation platform. The implementation produces exactly two deployable Minecraft artifacts:

- `EnthusiaStaff-Paper.jar`, installed unchanged on HUB, SMP, and future Paper backends.
- `EnthusiaStaff-Velocity.jar`, installed on the network proxy.

The project targets Java 21, Paper 1.21.11, and the Java-21-compatible Velocity 3.4.0-SNAPSHOT API line. The current Velocity 4.1 snapshot is built for Java 25 and is intentionally not used. Paper 1.21.8 through 1.21.11 remains a compatibility target; version-sensitive operations live behind adapters and must be exercised against every supported server build before cutover.

## Module graph

Dependencies point down only:

```text
paper ---------> protocol ---------> common
  |                  |                  ^
  +-------------> domain ---------------+
  +-----------> persistence ------------+

velocity ------> protocol
  |             domain
  +-----------> persistence

integration-tests -> public contracts from every module
```

- `common`: identifiers, clocks, validation primitives, cryptography contracts, result types, and bounded-worker utilities.
- `domain`: cases, sanctions, escalation, reports, appeals, alts, inventories, economy, staff sessions, audit, configuration, migration, and verification policy. It has no Bukkit, Velocity, Discord, JDBC, or web imports.
- `persistence`: MariaDB repositories, migrations, transactions, leases, journals, inboxes, outboxes, and recovery quarantine.
- `protocol`: authenticated Paper–Velocity envelopes, version negotiation, replay protection, acknowledgements, and idempotent message handlers.
- `paper`: commands, GUIs, inventory and player-state mutations, staff mode, vanish, freeze, reports, staff tools, and Paper-side adapters.
- `velocity`: authoritative login/mute enforcement, player/server directory, network identity, network sanction coordination, and durable delivery workers.
- `integration-tests`: MariaDB, concurrency, duplicate-delivery, failure-injection, migration, and architecture tests. It is never deployed.

No platform module may be referenced from a shared module. Paper and Velocity never reference each other directly.

## Bounded contexts and ownership

| Context | Authoritative owner | Responsibility |
| --- | --- | --- |
| Identity | MariaDB; Velocity writes sessions | Stable UUID, Java/Bedrock identity, names, current/last server |
| Player directory | MariaDB plus bounded in-memory indexes | Offline lookup and tab completion without blocking the game thread |
| Cases | Domain service and MariaDB | Immutable case identity, actor, target, evidence links, visibility, audit |
| Punishments and sanctions | Domain service and MariaDB | Creation, activation, expiry, removal, revocation, overturn, inheritance |
| Escalation | Domain policy | Reason families, stored ordinals, decay, related history, ladder versions |
| Reports | Domain service and MariaDB | Cooldowns, claims, evidence snapshots, staff-only reporter identity |
| Appeals | Website D1 for workflow; MariaDB case API for eligibility/effect | Account binding and review; accepted appeals call the normal removal service |
| Alts | MariaDB; Velocity gathers network evidence | Relationship state, evidence, confidence, inheritance, permanent exclusions |
| Network identity | Velocity and encrypted MariaDB records | HMAC equality tokens, encrypted recoverable values, rotation, retention |
| Inventories | Owning Paper server; MariaDB journals/snapshots | Online coordination, offline leases, revisions, queued patches, restoration |
| Economy | EnthusiaCurrency is value authority; case service owns moderation intent | Exact plans, locks, snapshots, verification, quarantine |
| Market enforcement | EnthusiaMarket is stall authority; case service owns moderation intent | Snapshot, ownership changes, review timer, blacklist state |
| Reputation enforcement | EnthusiaCommend is reputation authority | Persistent blacklist and enforcement at every write entry point |
| Staff sessions | Owning Paper server plus MariaDB journal | Durable state snapshot, staff inventory, crash/reconnect restoration |
| Vanish | Paper visibility service; MariaDB stores durable intent | Visibility decisions and adapters for tab, chat, voice, commands, effects |
| Staff tools and freeze | Paper state; MariaDB durable state for recoverable workflows | Tool behavior, temporary mutations, reconnect and restart recovery |
| Discord delivery | MariaDB outbox; Velocity worker | Four webhook streams, bounded retries, circuit breakers, manual recovery |
| Migration | Migration coordinator and MariaDB | Dry run, idempotent import, shadow comparison, cutover and rollback gates |
| Verification | Each runtime reports facts; Velocity aggregates | PASS/WARNING/DISABLED/RESTART REQUIRED/CRITICAL health report |
| Audit | MariaDB append-only records | Privileged actions, failures, overrides, recovery, and immutable Founder audit |
| Configuration | Each runtime owns parsed immutable snapshots | Modular files, cross-reference validation, atomic swap, restart boundaries |
| External integrations | Dedicated adapters | Optional failure disables only the affected capability |

## Runtime responsibilities

### Paper

- Perform Bukkit mutations only on the owning server thread.
- Capture and apply inventories, Ender chests, player state, staff mode, vanish, freeze, virtual test entities, and GUIs.
- Validate authorization at command, GUI, and application-service boundaries.
- Persist durable intent before any destructive change and report success only after verification.
- Queue network work off-thread and continue from the durable outbox after restart.
- Register stable Bukkit service APIs for visibility, punishment queries, staff sessions, inventory locks, alts, the player directory, staff mode, and sanctions.

### Velocity

- Decide login admission for active network bans and inherited sanctions.
- Maintain the network-wide online/server directory and route commands to owning servers.
- Capture network identity tokens without exposing raw addresses.
- Coordinate the authenticated persistent backend channel.
- Run network, Discord, expiration, migration, and recovery workers.
- Refuse network sanction creation when required backends or MariaDB cannot durably accept it.

### Website

- Read only sanitized MariaDB views through a restricted Hyperdrive user or restricted internal API.
- Keep accounts, sessions, punishment-code claims, appeals, rate limits, and security events in D1.
- Keep private appeal media in R2 and serve it only through short-lived authorized responses.
- Allow Developer to inspect appeal records but not claim or decide them. Mod, Admin, and Founder decisions carry an immutable server-derived rank to the normal sanction-change service for authorization.
- Never expose staff notes, reporters, coordinates, network identity, alt evidence, confiscation detail, or raw automation metadata.
- Remain private and unlinked until an explicit public-launch operation.

## Command and event flow

Every privileged write follows the same application-service path regardless of command, GUI, automation, website, or integration origin:

1. Normalize and validate hostile input.
2. Authorize the actor against the requested domain action.
3. Create a unique idempotency key and durable operation intent.
4. Acquire the per-player lease and lock required authoritative rows.
5. Re-read current state and reject stale revisions.
6. Capture a before snapshot when external state will change.
7. Commit the domain state, audit event, and outbox message in one database transaction.
8. Apply platform or external side effects idempotently.
9. Verify the resulting state and record acknowledgement.
10. Retry a bounded number of times, or quarantine with administrator-visible recovery instructions.

Network messages use at-least-once delivery. A unique `(consumer, message_id)` inbox constraint makes the final domain effect occur once. Acknowledgement means the consumer durably recorded the outcome, not merely that bytes arrived.

## Transactions and concurrency

- Case creation, sanctions, sanction events, punishment step, audit, and outbox insert commit atomically under `READ COMMITTED` with target sanction rows locked.
- Per-player destructive operations acquire a database lease using a fencing token. Renewal and release require the same owner and fence.
- Entity revisions use optimistic `WHERE id = ? AND revision = ?` updates. A zero-row update is a conflict, never an implicit retry over newer data.
- Unique constraints reject duplicate external IDs, idempotency keys, open overturn requests, appeal claims, and inbox deliveries.
- Inventory and economy operations use explicit state machines and before snapshots. Recovery resumes only safe idempotent stages; ambiguous stages enter quarantine.
- Executors, caches, retries, outboxes, and protocol queues are bounded. Bukkit and Velocity event threads do not wait on JDBC, HTTP, filesystem, or socket I/O.

## Failure and recovery

Operational modes are `BOOTSTRAP`, `DEGRADED`, `SHADOW_MIGRATION`, `ACTIVE`, `MAINTENANCE`, and `READ_ONLY_FAILURE`.

At startup, recovery workers claim expired leases, inspect incomplete journals, replay committed outboxes, and quarantine operations whose external state cannot be proved. MariaDB loss blocks new punishments and destructive edits while safe cached queries, status, reload validation, and verification remain available. Proxy loss blocks network sanctions but does not disable safe Paper inspection. Optional integration loss disables only its adapter.

Shutdown stops intake, drains only already-bounded in-memory work, leaves unacknowledged durable messages pending, and closes executors, sockets, and pools. Reload swaps a fully validated immutable configuration snapshot and does not recreate workers or discard active operations.

## External boundaries

- RoseChat needs a versioned moderation service for pre-broadcast decisions, current-channel state, recipient filtering, mute enforcement, and private-message evidence capture.
- EnthusiaCurrency needs an idempotent exact-removal API that can plan, apply, verify, and compensate under an external operation ID.
- EnthusiaMarket needs a moderation API for stall lookup/snapshot, overdue or unowned transitions, ownership removal, and blacklist state without bypassing rent behavior.
- EnthusiaCommend needs a persistent blacklist API enforced by GUI, command, and public write entry points.
- Polar 1.7.11-beta is optional. The supplied `PolarLoader.jar` exposes only an enable callback and no violation/punishment event contract, so automatic enforcement remains `DISABLED` until a compatible event API is supplied. The jar stays under `libs/private/` and is never committed or shaded.
- Other third-party integrations use public APIs behind adapters. Missing adapters never cause reflective guesses or command dispatch as a source of truth.

## Configuration ownership

Paper owns GUI, staff-mode, vanish, inventory, staff tools, reports, automod, and Paper integration settings. Velocity owns servers, network channel, identity, sanctions, migration, Discord delivery, and network integration settings. Shared punishment and escalation documents have one version and checksum stored in `configuration_versions`; both runtimes must report the same active checksum before `ACTIVE` writes are allowed.

## Testing strategy

- Pure domain unit and property tests cover escalation, decay, permissions, sanction transitions, alt inheritance, duration parsing, and normalization.
- Repository integration tests run against MariaDB Testcontainers and verify constraints, isolation, outbox/inbox, leases, recovery, and migration idempotency.
- Protocol tests cover authentication, replay, duplicate delivery, acknowledgements, queue bounds, reconnect, and version rejection.
- Paper adapters are isolated behind ports; focused server tests verify listener cancellation and main-thread handoff. Live HUB/SMP tests remain mandatory for inventory, vanish, voice, packet, and visual behavior.
- Website tests cover authentication, CSRF, rate limiting, code binding, appeal authorization, upload validation, and public-data sanitization.
- Architecture tests forbid platform imports in shared modules and cyclic module/package dependencies.

## Implementation order and rollback

Implementation order is domain and schema, migration importer and shadow comparison, Velocity authority, protocol, Paper sanctions, removal/overturn, reports, inventories/economy, staff mode/vanish/freeze/tools, alts and integrations, Discord, website, then full failure testing. Each phase adds its rollback or quarantine behavior before the next phase begins.

Schema changes are forward-only Flyway migrations. Feature activation is configuration-gated. Rollback disables new writes, returns to the previous authority mode, drains no destructive queue automatically, and follows `docs/rollback.md`. Cutover never drops LiteBans data or removes legacy jars; jar removal is a later manual step after acceptance.

## Future topology

Backends identify themselves with a stable configured server ID and an allowlisted credential. Adding a backend means installing the same Paper jar, registering its inventory scopes and data paths, starting it in verification-only mode, and promoting it after checks pass. No server-specific domain code or schema change is permitted.
