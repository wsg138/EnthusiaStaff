# Handoff — PR #46 punishment history and sanction lifecycle

## Identity

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Date | 2026-08-02 |
| Work item | Punishment history and complete sanction-change lifecycle |
| Starting `main` | `e5823e109a59dc46de579d0e990ffd4954d7667e` |
| Branch | `feature/punishment-history-lifecycle` |
| PR | `#46 — Add punishment history and sanction lifecycle workflow` |
| Final feature head | `070cc5e0e7f65a33f8b57259f03324039d7a6369` |
| Merge commit | `f53143132db29b9cd75e7caa6589f979d99af8c4` |
| Resulting `main` | `f53143132db29b9cd75e7caa6589f979d99af8c4` |
| Merge method | Normal merge commit |

No rebase, squash, direct `main` push, or force-push was used.

## Baseline

Plugin version: `0.1.0-SNAPSHOT`

Runtime:

- Java 21;
- Paper-compatible backends;
- Velocity;
- MariaDB.

Existing foundation reused:

- case-scoped sanction changes;
- durable operational-mode and authoritative-write fencing;
- append-only sanction and audit events;
- idempotency keys;
- punishment requests;
- website appeals;
- case repositories;
- player directory;
- historical usernames;
- Java and Bedrock identities;
- authorization;
- reload system;
- Flyway and Testcontainers infrastructure.

Confirmed baseline gaps:

- no database-bounded player-history command;
- no complete case timeline;
- sanction changes were case-oriented rather than exact-sanction operations;
- no appeal-linked overturn command;
- missing independent history, sensitive-history, and per-action permissions;
- missing history-specific configuration and database pagination.

## Implementation

### Commands

- `/history <player|uuid> [page]`
- `/case [view] <case-id>`
- `/estaff sanction reduce <sanction-id> <expiration-or-duration> [--request <request-id>] <reason>`
- `/estaff sanction end <sanction-id> [--request <request-id>] <reason>`
- `/estaff sanction revoke <sanction-id> [--request <request-id>] <reason>`
- `/estaff sanction overturn <sanction-id> [--appeal <appeal-id>] [--request <request-id>] <reason>`

Database work runs asynchronously. Important IDs, statuses, pagination instructions, and outcomes are shown as readable text instead of depending on Java-only hover or click components.

### Permissions

- `enthusiastaff.history.view`
- `enthusiastaff.history.view-sensitive`
- `enthusiastaff.sanction.reduce`
- `enthusiastaff.sanction.end`
- `enthusiastaff.sanction.revoke`
- `enthusiastaff.sanction.overturn`
- `enthusiastaff.sanction.overturn.appeal`
- `enthusiastaff.sanction.bypass-hierarchy`

History access, sensitive information, and mutation types are independently controlled. Authorization and hierarchy are rechecked inside the locked transaction rather than only in the command handler.

### Configuration

```yaml
history:
  page-size: 8
  include-request-events: true
  include-appeal-events: true
  timezone: UTC

sanction-actions:
  minimum-reason-length: 3
  maximum-reason-length: 500
  allow-permanent-reduction: true
```

Reload consumes the already-validated configuration snapshot. Invalid input retains the previous valid settings and does not rebuild the MariaDB pool, restart migrations, alter operational mode, or duplicate tasks.

### Domain, persistence, and audit

Added:

- unified history domain records;
- `ModerationHistoryStore`;
- database-bounded deterministic newest-first pagination;
- exact-sanction request and result contracts with before-and-after state;
- permanent-to-finite reduction policy;
- deterministic case-level row locking;
- expected-revision checks;
- replay-before-stale idempotency handling;
- rollback protection for checked and unchecked failures;
- validated appeal and punishment-request linkage;
- append-only mutation events containing previous and resulting status and expiration, actor, reason, runtime, linkage, and idempotency identity.

Concurrent conflicting terminal changes produce one committed result and one rejected or no-op result.

Original cases, sanctions, requests, appeals, and audit rows are never deleted.

### Migration

Added only:

`V14__punishment_history_and_exact_sanction_changes.sql`

V14 introduces history and linkage columns plus indexes for cases, sanctions, requests, appeals, notes, and sanction events.

V1 through V13 remained unchanged.

Locked checksums remained:

- V11: `-2005375055`
- V12: `-1787751803`
- V13: `1189066017`

Clean installation and V13-to-V14 upgrade paths passed MariaDB integration testing.

### Identity support

Player resolution supports:

- online and offline players;
- UUIDs;
- current usernames;
- historical usernames;
- case-insensitive matching;
- stored Java identities;
- stored Floodgate/Bedrock identities;
- explicit ambiguity results;
- bounded historical-name collision queries with truncation disclosure.

No blocking Mojang or external lookup is performed on the server thread.

## Harsh review

The final PR was reviewed for:

- exact-sanction transaction semantics;
- history pagination consistency;
- count and page snapshot behavior;
- hierarchy and authorization rechecks;
- checked and unchecked rollback;
- command response scheduling failure handling;
- migration index-build deployment planning;
- runtime packaging;
- provider API leakage;
- documentation and requirements consistency.

Confirmed defects found during review were fixed before the final validation head, including one-shot history snapshot consistency and command-response scheduling failure logging.

Unresolved review threads at merge: `0`.

No known merge blocker remained.

## Exact-head validation

Final reviewed feature head:

`070cc5e0e7f65a33f8b57259f03324039d7a6369`

### Hosted validation

- Coverage workflow: `30766834412`
- Build/test job: `91546990306`
- Wiki validation: `30766835534`
- Java: Temurin `21.0.11+10`
- Build: `BUILD SUCCESSFUL`
- Gradle tasks: 49 actionable; 40 executed; 9 up-to-date
- Unit tests: passed
- Paper tests: passed
- Velocity tests: passed
- Persistence tests: passed
- Protocol tests: passed
- MariaDB/Testcontainers integration tests: passed
- Clean build used `-Xlint:all -Werror`
- Codacy coverage upload and finalization: succeeded
- Codacy PR analysis: `Up to standards — 0 issues`
- CodeRabbit: successful
- Unresolved review threads: `0`

### Coverage

- Lines: `46.54%`
- Branches: `37.07%`
- Instructions: `49.13%`

### Runtime artifacts

- Paper SHA-256: `2bdef8d49d7f44a55c292552bef2b6f4a57661aa1ed8a38beb8dd03c48b80d3f`
- Velocity SHA-256: `cfc02cb1761193fae8c6be0526abdb4c6e28ecb9495e126148747275298707a2`
- Validation artifact: `8839265965`
- Artifact digest: `cfcc94af171eaf9182b1ccca91c2e603a3a363ce0f5ce336abeaba47fb42fb1c`
- Both JARs passed ZIP integrity checks
- Provider API source types checked: 24
- Provider API leaks: 0

### Pi boundary

No verifiable exact-head Pi boot/restart evidence was produced. No Pi success was claimed.

## Merge result

PR #46 was merged using GitHub's normal merge method.

- Merge commit: `f53143132db29b9cd75e7caa6589f979d99af8c4`
- Resulting `main`: `f53143132db29b9cd75e7caa6589f979d99af8c4`
- Branch relation immediately before merge: 103 commits ahead, 0 behind

## Boundaries preserved

- No JAR was deployed.
- No production database was accessed.
- No production player data was accessed.
- No production credential, route, webhook, or Discord credential was used.
- EnthusiaStaff authority was not activated.
- LiteBans remains authoritative.
- Issue #43 remains open.
- No production cutover acceptance was started.
- No 168-hour shadow window was started.
- No Flyway repair or migration-history rewrite occurred.
- V1 through V13 remained unchanged.
- No staging-controls repository change occurred.

## Remaining work

Recommended next feature:

**Staff report workflow**

Expected scope:

- player report submission;
- staff queue;
- assignment;
- report detail;
- notes and evidence;
- case and punishment-request escalation;
- resolution, dismissal, duplicate linking, and reopening;
- notifications;
- searchable report history;
- concurrency, idempotency, restart, permission, privacy, configuration, and Bedrock coverage.

No branch or PR for that feature was created as part of PR #46.