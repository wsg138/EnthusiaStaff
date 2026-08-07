# Build and Testing

Use this page to build one exact revision and to understand what each validation layer can prove. For review invariants, use [[Code Review Guide]]. For remaining product status, use [[Implementation Status]].

## Complete local validation

Windows:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

Linux/macOS:

```bash
./gradlew --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

Docker must be available for the MariaDB Testcontainers suites. A run that skips required container tests is not a complete repository checkpoint.

Record the exact commit SHA, command, test/suite counts, skipped/unavailable groups, runtime-jar names/hashes, hosted analysis results, and any staging workflow/run identifiers. Evidence from different commits must not be combined into one exact-head claim.

## Focused development tests

Use focused module tests while editing, then rerun the complete clean gate before treating the revision as a coherent checkpoint:

```bash
./gradlew :domain:test
./gradlew :persistence:test
./gradlew :protocol:test
./gradlew :paper:test
./gradlew :velocity:test
./gradlew :integration-tests:test
```

Run the tests closest to the changed boundary first. A persistence change normally needs MariaDB integration coverage; a scheduler/player-state change needs platform-focused tests and later runtime evidence; a migration needs clean-install and upgrade coverage.

## What each evidence layer proves

| Evidence | It can support claims about... | It cannot establish by itself... |
| --- | --- | --- |
| Unit tests | pure policy, parsing, authorization predicates, deterministic state transitions | real JDBC, scheduler, network, provider, classloader or client behavior |
| Module/component tests | one adapter/service with controlled collaborators | representative distributed runtime behavior |
| MariaDB/Testcontainers | SQL, constraints, transactions, migrations, concurrency/restart scenarios explicitly exercised | production volume/latency or arbitrary process-kill timing |
| Concurrency/failure-injection tests | the races/failures actually simulated | every real scheduler/network/process race |
| Runtime-JAR checks | expected deployables, archive integrity and scanned provider-API leakage | provider discovery/classloader compatibility with real plugins |
| Static analysis | issues detectable by configured analyzers | behavioral correctness or absence of all security defects |
| Coverage | which code lines/branches were executed by measured tests | assertion quality, scenario completeness or staging correctness |
| Wiki validation | Wiki structure, internal links and format rules | factual correctness of product claims |
| Private Paper boot/restart staging | the exact jar boots/restarts in the recorded Paper environment | Velocity, multi-backend, all providers, Bedrock, Folia or production readiness |
| Distributed Java/Bedrock/provider staging | behavior exercised in the representative recorded topology | untested load, production data, migration/cutover or later revisions |
| Production acceptance | the explicit release/cutover claim accepted for one pinned artifact/config/evidence set | future code/config changes |

A passing unit test is not staging evidence. A skipped or unavailable staging workflow is not a pass. “Plugin present” is not proof that its provider API works correctly.

## Runtime artifacts

Expected deployables:

```text
paper/build/libs/EnthusiaStaff-Paper-<version>.jar
velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar
```

Inspect both for:

- intended entry points/resources only;
- no provider-owned API duplication;
- no private jars, secrets or local configuration;
- no test fixtures, server runtime directories, databases, logs or generated reports;
- correct service/plugin metadata;
- no accidental third runtime plugin.

A clean artifact scanner is still not a substitute for installing supported providers together and exercising service discovery/classloader behavior.

## MariaDB and migration validation

Persistence changes should exercise the applicable combination of:

- clean schema creation;
- upgrade from the immediately relevant previous schema;
- constraint/index behavior;
- transaction rollback;
- idempotent replay;
- optimistic revision conflict;
- lease/fence claim, renewal and release;
- restart recovery;
- duplicate/out-of-order delivery;
- concurrent runtimes where the workflow can contend.

Current merged `main` includes Flyway migrations through `V17__website_appeal_workflow.sql`. V1-V17 are immutable history; new schema work adds a new forward migration.

Do not use Flyway repair or migration-history edits merely to make a changed historical migration pass.

## Paper, Leaf and Folia validation

Automated Paper-side tests should verify policy and scheduler handoff where possible, but real runtime acceptance is still required for claims involving:

- entity/region ownership;
- reconnect racing with queued callbacks;
- inventory/Ender mutations;
- staff-mode restoration;
- vanish visual/tab/packet behavior;
- freeze bypasses;
- asynchronous teleport/follow/spectate;
- plugin disable/restart recovery;
- supported Paper/Leaf/Folia versions.

A standalone Paper boot test does not prove Folia scheduler correctness.

## Velocity and distributed validation

Representative runtime validation should cover:

- non-blocking login/server-switch event behavior;
- Paper/Velocity startup and shutdown order;
- backend reconnect and replacement sessions;
- no-online-player transport;
- durable ACK/outbox/inbox semantics;
- proxy/backend partial outage;
- queue/backpressure/retry bounds;
- network identity observations and out-of-order presence updates;
- multi-backend authority/degradation behavior.

See [[Protocol and Network Traffic]] and [[Code Review Guide]].

## Java and Bedrock validation

Automated identity tests should cover verified Java, verified Bedrock, `UNKNOWN`, missing/incompatible Floodgate, aliases, historical names, duplicate observations and out-of-order proxy/backend updates.

Representative Geyser/Floodgate staging must still verify:

- Java and Bedrock login/reconnect;
- `*` alias resolution without treating the prefix as platform proof;
- GUI/text fallback behavior;
- click/hover assumptions;
- packet/tab/visibility behavior;
- server switching and provider absence/failure.

## Provider and integration validation

For every optional provider, test at least:

1. present and compatible;
2. missing;
3. present but incompatible/unavailable;
4. dependency failure during use;
5. reload/restart boundary where applicable.

Verify that unrelated features remain available when safe, dependent actions fail clearly, external effects are idempotent/verified, and provider-owned classes are not shaded into EnthusiaStaff.

See [[Integrations]].

## Coverage expectations

The authoritative goals set these targets:

- Critical code: **80% line / 70% branch**
- Overall Java: **70% line / 60% branch**

Coverage is a diagnostic, not a substitute for meaningful assertions. Getter-only or assertion-free tests do not satisfy the intent of these targets.

## Static analysis

The target remains Codacy grade A with zero unresolved first-party findings. Do not reach that state by weakening analyzers, blanket exclusions/suppressions, lowering thresholds, or hiding legitimate findings.

A “zero new issues” result means the branch did not worsen the measured baseline. It does not mean every older issue or every behavioral defect is gone.

## Private Paper exact-SHA gate

The repository has used an exact-SHA private Paper boot/restart gate to independently build, inspect the Paper runtime JAR and exercise startup/storage/commands/shutdown.

Interpret it narrowly:

- **pass on SHA X** — evidence for the exact recorded Paper scenario on SHA X;
- **not run / infrastructure unavailable / skipped** — no runtime evidence for that gate;
- **pass on an older SHA** — historical evidence, not proof for a newer source revision.

It does not replace Velocity, multi-backend, providers, Bedrock, Folia, real migration data, load, process-kill, 168-hour shadow, or production acceptance.

## Full staging record

For a staging claim, record:

- exact source commit and runtime-jar hashes;
- configuration versions/checksums;
- Java, Paper/Leaf/Folia, Velocity, MariaDB, provider and Geyser/Floodgate versions;
- topology/accounts/data scope;
- steps and expected outcomes;
- failure/restart/reconnect observations;
- sanitized logs/evidence locations;
- unresolved mismatches;
- rollback/recovery result.

A source, migration, runtime configuration, or provider-contract change invalidates the affected evidence until it is rerun for the new candidate.

## Wiki validation

Run from the repository root:

```bash
python scripts/wiki/validate_wiki.py
```

The validator checks repository-managed `docs/wiki/pages/` content. It does not validate technical truth, external source existence, privacy judgment, or whether a status claim is supported, so those still require manual review.

Before publishing Wiki changes also check:

- every new page is reachable from Home, the sidebar, or an owning index;
- `[[Wiki Links]]` and Markdown links point where intended;
- headings and sidebar destinations are readable;
- source links still name real current files;
- no secret/private evidence was copied into documentation;
- no unmerged feature is described as available.

## Related pages

- [[Code Review Guide]]
- [[Developer Guide Index]]
- [[Architecture]]
- [[Developer Code Guide]]
- [[Implementation Status]]
- [[Wiki Maintenance]]