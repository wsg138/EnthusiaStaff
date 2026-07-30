# Installation

This page describes staging installation. It is not permission to activate
EnthusiaStaff as production authority.

## Requirements

- Java 21
- Paper/Leaf/Purpur 1.21.x-compatible backends, with current focus on
  1.21.8–1.21.11
- Velocity 3.4-compatible proxy
- MariaDB
- Separate application and migration credentials
- Existing LiteBans MariaDB source during migration
- Versioned encryption and HMAC keys for network identity
- PKCS#12 key/trust stores for persistent Paper–Velocity transport
- Required provider plugins for enabled features

The build produces:

```text
paper/build/libs/EnthusiaStaff-Paper-<version>.jar
velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar
```

The default project version is `0.1.0-SNAPSHOT` unless `releaseVersion` is
provided to Gradle.

## Staging topology

Install the same Paper jar on each backend:

```text
Velocity
├── HUB + EnthusiaStaff-Paper
└── SMP + EnthusiaStaff-Paper
```

Install one EnthusiaStaff-Velocity jar on the proxy. Keep HUB and SMP inventory,
Ender chest, world, and player-data scopes distinct.

## Safe staging sequence

1. Back up MariaDB, LiteBans data, plugin configurations, TLS material, and
   network identity keys.
2. Build and inspect the exact commit.
3. Create restricted MariaDB users.
4. Apply migrations in a private staging database.
5. Install the Velocity jar with enforcement disabled.
6. Install the Paper jar on one staging backend.
7. Validate TLS and persistent transport.
8. Add remaining staging backends.
9. Run `/estaff status` and `/estaff verify full`.
10. Test degraded behavior by disabling optional providers one at a time.
11. Run punishment, report, staff, inventory, recovery, and migration tests with
    disposable accounts and data.
12. Enter `SHADOW_MIGRATION` only after preflight passes.
13. Keep LiteBans authoritative.

## Do not install both authorities as active

During shadow:

- LiteBans enforces.
- EnthusiaStaff mirrors and compares.
- EnthusiaStaff does not write LiteBans.
- EnthusiaStaff does not enforce its calculated result.
- Existing old jars remain installed.

Do not remove old moderation plugins until after successful cutover and
post-cutover verification.

## Database credentials

Use separate credentials for:

- Normal application reads/writes
- Migration source inspection
- Schema migration
- Restricted website/public projections

Do not reuse root or administrator credentials in plugin configuration.

## Persistent channel

Each Paper server and Velocity require:

- Server identity/allowlist
- Protocol version
- TLS key store
- TLS trust store
- Environment-backed passwords
- Replay protection and message acknowledgement settings
- Bounded queue and reconnect/backoff settings

Test hostname and certificate rejection, not only successful connection.

## First startup

Expected startup work includes:

- Validate configuration
- Check schema and migration state
- Discover integrations
- Initialize durable workers
- Report operational mode
- Summarize disabled and restart-required features

A startup that reaches `DEGRADED` is not necessarily a crash, but unsafe actions
must remain blocked and the missing dependency must be explicit.

## Uninstalling old plugins

The goals list old jars for removal only after successful cutover. Removal is a
manual production change and must follow [[Shadow Mode and Cutover]].

Never let a build, verification command, or Wiki publish workflow delete
production jars or data automatically.
