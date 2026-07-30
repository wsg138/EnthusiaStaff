# Architecture

EnthusiaStaff uses a modular Java 21 architecture with platform-independent
domain logic and explicit Paper, Velocity, MariaDB, and protocol adapters.
Dependencies point inward toward shared contracts; Paper and Velocity never
reference each other directly.

## Modules

| Module | Responsibility |
| --- | --- |
| `common` | Identifiers, validation, cryptographic primitives, result types, and bounded-worker utilities |
| `domain` | Cases, sanctions, reports, inventories, economy, staff state, migration, authorization, and recovery policy |
| `persistence` | MariaDB repositories, Flyway migrations, transactions, journals, leases, inboxes, outboxes, and quarantine |
| `protocol` | Authenticated Paper–Velocity messages, replay protection, acknowledgements, and version negotiation |
| `paper` | Commands, GUIs, player-state mutations, inventory workflows, staff mode, vanish, freeze, and Paper integrations |
| `velocity` | Login enforcement, network identity, migration coordination, network delivery, and restricted website delivery |
| `integration-contracts` | Compile-time contracts for optional provider plugins |
| `integration-tests` | Docker-backed MariaDB, concurrency, idempotency, migration, and recovery tests; never deployed |

## Runtime ownership

Paper performs Bukkit mutations only on the owning server thread. Velocity
coordinates network-wide decisions and never performs backend-local player
mutations. MariaDB is authoritative for durable moderation state; in-memory
caches may accelerate reads but cannot authorize destructive work when stale.

External systems retain ownership of their own state. For example,
EnthusiaCurrency remains the balance authority while EnthusiaStaff owns the
moderation operation, durable plan, audit trail, and recovery decision.

## Authoritative write flow

Privileged writes use the same application-service path regardless of whether
they originate from a command, GUI, automation, website action, or provider:

1. Normalize and validate untrusted input.
2. Authorize the actor for the exact domain action.
3. Create a unique idempotency key and durable operation intent.
4. Acquire the required database lease and fencing token.
5. Lock and re-read authoritative rows.
6. Capture a before-state snapshot for external changes.
7. Commit domain state, audit, and outbox work transactionally.
8. Apply the external side effect idempotently.
9. Verify the resulting state before terminalizing the journal.
10. Retry bounded transient failures or quarantine ambiguous outcomes.

## Failure model

The supported operational modes are `BOOTSTRAP`, `DEGRADED`,
`SHADOW_MIGRATION`, `ACTIVE`, `MAINTENANCE`, and `READ_ONLY_FAILURE`.
Dependency loss disables unsafe writes while retaining safe diagnostics and
recovery controls. Startup workers reclaim expired leases and resume only
provably idempotent stages.

For the complete bounded-context map, concurrency rules, external boundaries,
and deployment topology, see the
[source-controlled architecture document](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/architecture.md).
