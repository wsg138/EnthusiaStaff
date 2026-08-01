# Build and Testing

Read [[Development Blueprint]] before selecting the next major workstream. This
page explains how to prove one exact checkpoint; the blueprint explains where
that checkpoint belongs on the road to production.

## Complete local validation

Windows:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

Linux/macOS:

```bash
./gradlew --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

This must build the two runtime jars, run unit and integration tests and perform
configured verification tasks. Docker must be available because the MariaDB
Testcontainers suites are part of complete validation. A run that skips
container tests is not a complete checkpoint.

Record:

- exact commit SHA and command;
- total suite/test count;
- MariaDB suite/test count;
- skipped, disabled or unavailable groups;
- runtime jar names, sizes and hashes;
- hosted Codacy result, total baseline and issue delta; and
- staging workflow/run identifiers.

Do not combine successful evidence from different commits into one claimed
release candidate.

## Runtime artifacts

Expected deployables:

```text
paper/build/libs/EnthusiaStaff-Paper-<version>.jar
velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar
```

Inspect jar contents for:

- intended entrypoints and resources;
- no provider-owned API duplication;
- no private jars, secrets or local configuration;
- no test fixtures, development servers, databases, logs or generated reports;
- correct service metadata; and
- no accidental third runtime plugin.

## Focused checks

Use focused module tests while developing, then rerun the complete build before a
checkpoint:

```bash
./gradlew :domain:test
./gradlew :persistence:test
./gradlew :protocol:test
./gradlew :paper:test
./gradlew :velocity:test
./gradlew :integration-tests:test
```

Focused tests speed development but do not replace the clean complete gate. Do
not claim a test passed unless it ran successfully at the exact reviewed commit.

## Test categories

Required coverage includes:

- punishment ladders, decay, combined sanctions, history, removal and overturn;
- every rank boundary, especially Developer request-only behavior;
- punishment-request claims, decisions, fencing, notifications, restart,
  offline recipients and multi-server contention;
- reports, semantic merge/replay, privacy, retention, GUI and provider context;
- alt confidence, inheritance, exceptions, rotation and unread alerts;
- inventory revisions, concurrent viewers, nested containers, offline patches
  and restoration;
- economy rollback, provider conflicts, ambiguous outcomes and quarantine;
- staff mode crash/reconnect, checksums, restore and item leakage;
- vanish hierarchy, packets, schedulers, tab, commands, chat, voice, effects,
  providers, Java, Bedrock and Folia;
- freeze restrictions, offline behavior, reconnect and staff-only communication;
- Discord/network outbox lease, duplicate handling, backpressure and circuit breaking;
- LiteBans variants, dry run, replay, shadow dimensions, seven-day evidence,
  activation, emergency freeze and rollback;
- website authentication, signatures, codes, appeals, roles, rate limits,
  uploads and privacy; and
- database, network, process-kill, load and partial-dependency failure injection.

## Current checkpoint interpretation

PR #36 final head `3afeffc926571170e8df18c7d096ca7f4d89ec1b`
completed:

- 40/40 clean Java 21 tasks;
- 99 suites / 398 tests with no failures, errors or skips;
- 15 MariaDB 11.8.3 Testcontainers suites / 68 tests;
- hosted Codacy with zero new and three fixed findings, 92.59% diff coverage and
  no clone increase;
- Wiki validation for 29 pages; and
- exact-SHA Pi run `30709333535`.

This proves that checkpoint's recorded scope. It does not prove full provider,
Velocity, multi-backend, Bedrock, Folia, load, process-kill, real-data migration
or 168-hour shadow acceptance.

## Coverage targets

Authoritative goals specify:

- Critical code: 80% line / 70% branch
- Overall Java: 70% line / 60% branch

Getter-only or assertion-free tests do not satisfy the intent. Coverage floors
must represent meaningful behavior rather than low-value line inflation.

## Static analysis

The target is Codacy grade A with zero unresolved first-party findings. Do not
reach it by disabling tools, excluding source, blanket suppressing or lowering
thresholds. Narrow suppressions require documented false-positive evidence.

A zero-new-issues result means the branch did not worsen the baseline; it does
not mean the repository backlog is empty.

## Exact-SHA Pi gate

The Pi staging workflow is a merge-candidate gate, not a general
production-readiness claim. It independently builds with Java 21, inspects the
Paper runtime jar and provider-API packaging, loads the plugin on Paper,
exercises two boot/storage/command/shutdown cycles and scans sanitized evidence
for critical failures.

A passing result applies only to the recorded SHA. It does not replace Velocity,
multi-backend, provider-plugin, Bedrock, Folia, live Discord, production data,
load, process-kill, migration or shadow acceptance testing.

## Full staging evidence

Record:

- exact commit and jar hashes;
- configuration versions/checksums;
- Java, Paper/Leaf, Velocity, MariaDB, provider, Geyser/Floodgate and Folia versions;
- topology, accounts, steps and expected outcomes;
- failure injection and recovery observations;
- logs and sanitized evidence locations;
- unresolved mismatches; and
- rollback result.

A final release candidate must use one coherent evidence set. A source change
invalidates affected groups until they run again.

## Wiki validation

```bash
python scripts/wiki/validate_wiki.py
```

Wiki checks run separately from Java tests and must pass before publishing. The
canonical Wiki source is `docs/wiki/pages/`, not the live Wiki editor.