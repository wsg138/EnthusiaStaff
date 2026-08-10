# Runtime database recovery and Velocity reload

This document describes the ES-P02 operator behavior. It does not authorize production cutover, production database access, Flyway repair, or issue #43 acceptance. LiteBans remains authoritative until the separate production-acceptance sequence is complete.

## Database bootstrap recovery

Paper and Velocity initialize MariaDB on bounded worker threads. A transient connection, migration-validation, or startup-recovery failure no longer permanently strands the process after one attempt.

The default retry policy permits six attempts. Delays are capped exponential backoff: 1, 2, 4, 8, 16, then at most 30 seconds. Only one bootstrap attempt and one scheduled retry may exist per runtime. A failed attempt must remove and close its partially published MariaDB runtime and associated resources before another attempt starts.

Paper keeps Bukkit-owned player snapshot and recovery work on the correct global or entity scheduler. MariaDB open, close, and follow-up work remains on the bounded worker executor. Late entity or global callbacks from a retired attempt are ignored.

Velocity keeps authority in `BOOTSTRAP` until the complete candidate runtime is ready. Failed initialization closes partial channel, outbox, Discord, website API, scheduled task, store, and MariaDB resources before retry. Configuration validation and unauthorized persistent `ACTIVE` state are permanent failures for the automatic cycle; an operator may correct the cause and request a new bounded cycle with `/estaff reload`.

Shutdown stops new retries. Accepted worker work is drained within the normal shutdown deadline, and published resources are closed deterministically. A scheduled callback that arrives after shutdown does not start another attempt.

## Operator status

`/estaff status` reports the authority mode and disabled components. On Velocity it also reports bootstrap attempt count, whether a retry is scheduled, and whether the current bounded cycle is exhausted.

Expected database-related states:

- `BOOTSTRAP`: initialization or a bounded retry is in progress.
- `DEGRADED`: the retry limit was reached or a permanent startup requirement failed.
- the persisted operational mode: a complete runtime was published successfully.
- `MAINTENANCE`: shutdown has started.

Messages are sanitized. They identify the failing component and retry state without printing JDBC URLs, usernames, passwords, channel secrets, private rows, or raw network identifiers.

## Velocity configuration reload

Velocity reload requires `enthusiastaff.reload` and runs on the bounded worker executor:

```text
/estaff reload
```

The command loads and validates a complete candidate before changing live state. Invalid files leave the active configuration unchanged. Concurrent reload requests are rejected rather than overlapped. A candidate is also rejected if shutdown begins before publication.

Only these settings are live-reloadable:

- `fail-closed-while-active`
- `appeals-url`

They are published together as one immutable configuration snapshot. If publication fails, the previous snapshot is restored.

Every resource-bound setting is restart-required, including database environment names and pool settings, server identity, website API listener/authentication/worker settings, persistent channel listener/TLS/backend secrets, network-identity keys, Discord destinations/retry policy, and LiteBans source/schedule settings. If any such value differs, no part of the candidate is applied. The command lists the changed keys and reports that a proxy restart is required.

When storage is unavailable and no live reload coordinator exists, `/estaff reload` first validates the file. A valid file may start one immediate bounded bootstrap cycle if the previous cycle is exhausted. It does not create a duplicate attempt while storage is active, retrying, or shutting down.

## Recovery procedure

1. Run `/estaff status` and inspect the sanitized component message.
2. Correct only the external cause: MariaDB reachability, environment variables, configuration, TLS material, or required listener availability.
3. On Velocity, run `/estaff reload`. A valid reloadable candidate is applied, or an exhausted storage cycle is restarted. Restart the proxy when the command reports resource-bound changes.
4. On Paper, allow the current bounded cycle to finish. A missing permanent environment requirement still requires correcting the process environment and restarting the backend.
5. Confirm the runtime leaves `BOOTSTRAP` or `DEGRADED` and returns to the persisted operational mode.
6. Do not use Flyway repair, edit V1–V18, delete recovery rows, activate authority, or bypass cutover gates.

## Migration boundary

ES-P02 adds no database migration. The current repository migration boundary is `V18`; V1–V18 remain byte-immutable. A checksum mismatch or unsupported future schema remains a hard safety failure rather than an automatic repair target.

## Validation boundary

Unit and integration tests cover bounded retry, worker and scheduler rejection, cleanup-before-retry, stale callback rejection, retry exhaustion, shutdown, repeated reload, invalid candidate rollback, restart-required candidates, and reload/shutdown races. Production-like distributed, Java/Bedrock, and private-data validation remain assigned to their later validation packages.
