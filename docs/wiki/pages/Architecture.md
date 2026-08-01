# Architecture

EnthusiaStaff is a distributed moderation platform, not a single Bukkit command
plugin. It separates policy and durable state from Paper, Velocity, MariaDB,
Discord, website, and provider implementations.

This page explains the system boundaries and write model. Developers who need a
file-by-file review map, important classes, feature traces, tests, and review
checklists should continue to [[Developer Code Guide]].

## Deployable artifacts

Exactly two runtime jars:

1. `EnthusiaStaff-Paper-<version>.jar`
2. `EnthusiaStaff-Velocity-<version>.jar`

Internal modules include:

```text
common
domain
integration-contracts
persistence
protocol
paper
velocity
integration-tests
docs
```

Provider API classes must not be duplicated into runtime jars when provider
plugins own them.

## Dependency direction

Domain code must not directly depend on Bukkit, Velocity, Discord, MariaDB
implementations, or web frameworks.

Commands and GUIs translate input into application requests. They do not own
punishment policy, inventory transaction rules, authorization, or recovery
logic.

## Bounded contexts

- Identity and player directory
- Cases, punishments, sanctions, escalation
- Reports and appeals
- Alts and network identity
- Inventory, economy, market, reputation
- Staff sessions, vanish, freeze, tools
- Discord delivery
- Migration and cutover
- Verification, audit, configuration
- External integrations

## Runtime ownership

### Paper

- Staff commands and GUIs
- Server-local player state
- Staff mode and vanish application
- Inventory/Ender interaction
- Freeze restrictions
- Report and client evidence capture
- Provider adapters that require Bukkit

### Velocity

- Network login enforcement
- Network mute and server-switch coordination
- Protected network identity observations
- Persistent channel server
- Durable network and Discord workers
- Migration/shadow/cutover coordination
- Restricted website/API bridge

### Restricted website bridge

The Velocity website bridge is an inbound, loopback-only HTTP boundary for a
trusted local site service or reverse proxy. It must never be exposed directly
to an untrusted network. Requests require the configured bearer and HMAC
authentication, a bounded timestamp window, and nonce replay protection.

The implementation separates responsibilities so transport failures cannot
silently become moderation decisions:

- `WebsiteApiRuntime` owns the listener and bounded executor lifecycle;
- `WebsiteApiServer` enforces loopback clients, body limits, authentication,
  hardened response headers, and stable error envelopes;
- `WebsiteApiRequestDecoder` rejects unknown fields, unsupported query keys,
  malformed identifiers, and invalid content types;
- `WebsiteApiRouter` maps the small versioned route surface to domain ports;
- `WebsiteAppealEndpoint` authorizes the reviewer and coordinates durable
  prepare, sanction mutation, and terminal appeal state.

An authority-mode rejection leaves a prepared appeal replayable instead of
recording a false terminal result. Other rejected mutations record a durable
rejection before returning a conflict. Public responses continue to come from
sanitized MariaDB projections rather than mutable runtime objects.

### MariaDB

Authoritative cases, sanctions, identities, drafts, reports, staff sessions,
inventory/economy journals, outboxes, migrations, configuration versions,
audit, leases, and recovery quarantine.

## Authoritative write flow

A destructive distributed operation should:

1. Validate input and identity.
2. Authorize in the application service.
3. Persist durable intent.
4. Commit acceptance.
5. Apply local or external side effect.
6. Verify resulting state.
7. Commit terminal state and audit.
8. Deliver network/Discord messages from durable outbox.
9. Retry idempotently or quarantine ambiguity.

Transport is at-least-once with idempotent consumers. The architecture must not
claim true exactly-once network delivery.

## Paper–Velocity protocol

Required properties:

- Persistent connection; no online-player transport requirement
- Server allowlist
- Protocol negotiation
- TLS plus authenticated message envelope
- Replay protection
- Message IDs and acknowledgements
- Idempotent handlers
- Reconnect with bounded backoff
- Bounded queues and backpressure
- Health reporting
- Durable MariaDB outbox

## Safe failure

- Punishment failure cannot partially apply a combined case.
- Removal failure cannot alter unrelated sanctions.
- Inventory/economy failure preserves the original state or quarantines.
- Stale state cannot overwrite newer state.
- Migration mismatch blocks cutover.
- Optional integration failure disables only that feature.
- Success is reported only after durable commit.

## Internal services

Expected Bukkit-facing services include:

- `StaffVisibilityService`
- `PunishmentQueryService`
- `SanctionQueryService`
- `StaffSessionService`
- `StaffModeQueryService`
- `InventoryLockService`
- `AltRelationshipService`
- `PlayerDirectoryService`

These services provide stable boundaries for other plugins without exposing
mutable internals.

## Continue reviewing

Use [[Developer Code Guide]] for:

- the recommended source reading order;
- important root files and composition roots;
- package and class responsibilities;
- punishment, report, inventory, confiscation, staff-state, alt, migration,
  Discord, and website feature traces;
- persistence and protocol review checklists;
- test locations, concurrency rules, and high-risk review areas.
