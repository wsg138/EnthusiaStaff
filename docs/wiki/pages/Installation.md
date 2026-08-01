# Installation

This page describes **private staging installation**. It is not permission to
activate EnthusiaStaff as production authority.

## Before using this page

- Feature and release status: [[Integrations, Migration, and Release Readiness]]
- Current configuration shape: [[Configuration]]
- Provider requirements: [[Integrations]]
- Failure/recovery procedure: [[Recovery and Troubleshooting]]
- LiteBans source import: [[LiteBans Migration]]
- Shadow and authority change: [[Shadow Mode and Cutover]]
- Build validation: [[Build and Testing]]

## Requirements

- Java 21
- Paper/Leaf/Purpur 1.21.x-compatible backends, with current focus on
  1.21.8–1.21.11
- Velocity 3.4-compatible proxy
- MariaDB
- Separate application and migration credentials
- Existing LiteBans MariaDB source during migration
- Versioned encryption and HMAC keys for protected network identity
- PKCS#12 key/trust stores for persistent Paper–Velocity transport
- Required provider plugins for each enabled feature

## Runtime artifacts

The build must produce exactly:

```text
paper/build/libs/EnthusiaStaff-Paper-<version>.jar
velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar
```

The default project version is `0.1.0-SNAPSHOT` unless `releaseVersion` is
provided to Gradle.

Never deploy an artifact whose exact source revision, hash and validation evidence
are unknown.

## Staging topology

Install the same Paper jar on each backend:

```text
Velocity + EnthusiaStaff-Velocity
├── HUB + EnthusiaStaff-Paper
└── SMP + EnthusiaStaff-Paper
```

Keep HUB and SMP inventory, Ender chest, world and player-data scopes distinct.
Future backends should use the same Paper jar with explicit server configuration.

## Required backups

Before first staging startup, back up:

- MariaDB and LiteBans source data;
- current moderation/plugin configuration;
- TLS key/trust material;
- network-identity encryption/HMAC keys;
- provider configuration and private integration settings;
- existing jars and known-good artifact hashes.

Store backups outside the live plugin directory and test that they can be read.

## Safe staging sequence

1. Select one exact source revision and build both runtime jars.
2. Run the complete clean Java/MariaDB validation and inspect both jars.
3. Record artifact hashes, environment versions and configuration checksum.
4. Create restricted MariaDB users for application, migration and site access.
5. Apply migrations to a private staging database.
6. Configure Velocity with enforcement disabled and valid TLS material.
7. Install the Paper jar on one staging backend.
8. Verify configuration, schema, transport and disabled-feature reporting.
9. Install the Paper jar on the remaining staging backends.
10. Run `/estaff status` and `/estaff verify full` on the relevant runtimes.
11. Test optional providers one at a time, including expected degraded behavior.
12. Run punishment, report, staff-state, inventory, recovery and migration tests
    with disposable accounts/data.
13. Enter `SHADOW_MIGRATION` only after preflight passes.
14. Keep LiteBans authoritative.

Do not skip directly from “the plugin starts” to shadow or production use.

## Database access

Use separate credentials for:

- normal application reads/writes;
- LiteBans source inspection/import;
- schema migration;
- restricted website/public projections.

Do not reuse root/administrator credentials in plugin configuration. Store secret
values in environment variables or an approved secret manager.

## Persistent Paper–Velocity channel

Each Paper backend and Velocity need:

- explicit server identity and allowlist;
- protocol version compatibility;
- TLS key store and trust store;
- environment-backed passwords;
- replay-window and acknowledgement settings;
- bounded queue, reconnect and backoff settings.

Test both success and rejection:

- wrong hostname/certificate;
- untrusted certificate;
- wrong server identity;
- unsupported protocol version;
- stale/replayed message;
- proxy/backend restart;
- long outage and queue recovery;
- operation with no online player.

See [[Protocol and Network Traffic]].

## First startup expectations

Startup should:

1. validate configuration;
2. check schema/migration state;
3. discover providers;
4. initialize durable workers;
5. report operational mode;
6. identify disabled, degraded and restart-required features;
7. block unsafe commands until required state is ready.

Reaching `DEGRADED` is not necessarily a crash, but every unavailable feature must
be explicit and unsafe writes must remain blocked.

## Provider staging

Install only the providers needed for the test group, then test all supported
providers together before release. Verify:

- service/classloader compatibility;
- exact API version;
- event/callback reception;
- isolated failure behavior;
- reload/restart boundary;
- no provider-owned API classes leaked into EnthusiaStaff jars.

See [[Integrations]].

## Shadow installation rules

During shadow:

- LiteBans enforces;
- EnthusiaStaff imports, mirrors and compares;
- EnthusiaStaff does not write LiteBans;
- EnthusiaStaff does not enforce its calculated result;
- old jars/data remain available for rollback.

Do not run two active authorities at once.

## Verification checklist

Before a staging checkpoint is accepted, record:

- exact source revision;
- Paper/Velocity jar hashes;
- Java, Paper, Velocity and MariaDB versions;
- configuration checksum and secret-variable names;
- schema/migration version;
- provider versions;
- operational mode and verify output;
- tests/staging groups run and skipped;
- known blockers and recovery/rollback state.

## Old-plugin removal

Legacy removal happens only after successful cutover and accepted production
observation. It is a later manual operation.

No build, verification command, Wiki publication or automated migration task may
delete production jars or data.

## Related pages

- [[Integrations, Migration, and Release Readiness]]
- [[Configuration]]
- [[Integrations]]
- [[Recovery and Troubleshooting]]
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]
- [[Build and Testing]]
