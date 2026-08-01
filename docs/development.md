# Development and validation

This document explains how to make and validate changes in the EnthusiaStaff
repository. Read `docs/development-blueprint.md` before selecting the next major
workstream. The blueprint converts `ENTHUSIASTAFF-GOALS.md` and
`reports/REQUIREMENTS-MATRIX.md` into an ordered road to production.

## Development control documents

Use these sources in this order:

1. `ENTHUSIASTAFF-GOALS.md` — authoritative finished behavior and safety rules.
2. `reports/REQUIREMENTS-MATRIX.md` — conservative implementation, test,
   staging and blocker status.
3. `docs/development-blueprint.md` — milestone order, parallel workstreams,
   release gates and immediate execution queue.
4. `WORKSPACE-MANIFEST.md` — repository, branch, SHA, PR, validation and blocker
   checkpoint across related projects.
5. Feature-specific source, tests, runbooks and Wiki pages.

A class, command, test file or green pull request does not by itself establish
production readiness.

## Prerequisites

- JDK 21
- Git
- Docker Engine API 1.44 or newer exposed to the build shell
- Python 3 for Wiki validation

The repository uses the checked-in Gradle wrapper. It does not require a
machine-wide Gradle installation and does not install a persistent service.

## Branch and scope discipline

- Start coherent work from the latest applicable `main` checkpoint.
- Keep unrelated or concurrent work in separate branches.
- Preserve existing branch commits; do not recreate a branch by discarding work.
- Reconcile concurrent branches explicitly when they touch the same lifecycle,
  persistence, command or configuration boundary.
- Never combine evidence from different SHAs into one claimed release candidate.
- Update the requirements matrix and workspace manifest when a new root
  checkpoint is established.

Current development has two important concurrent branches: PR #37 for LiteBans
cutover coordination and PR #27 for punishment-request notifications, staff mode
and freeze. Their work must not overwrite or duplicate one another merely to
simplify a merge.

## Focused development loop

1. Read the relevant goals, requirements-matrix row and blueprint milestone.
2. Identify the domain service, durable store, runtime adapter, configuration,
   tests, recovery behavior and documentation affected.
3. Run the narrowest meaningful tests while editing.
4. Add hostile-input, permission, stale-state, duplicate, restart,
   partial-failure and concurrency coverage where applicable.
5. Review resource ownership, blocking I/O, transaction boundaries and bounded
   executor behavior before widening the test scope.
6. Run the complete validation before declaring a checkpoint.

Examples:

```powershell
.\gradlew.bat :domain:test
.\gradlew.bat :persistence:test
.\gradlew.bat :protocol:test
.\gradlew.bat :paper:test
.\gradlew.bat :velocity:test
.\gradlew.bat :integration-tests:test
```

On Linux or macOS, use `./gradlew`.

## Complete Java validation

Windows:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

Linux or macOS:

```bash
./gradlew --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

Docker must be reachable before starting the build. The integration-test module
uses temporary MariaDB containers. Those tests are mandatory: a run with
skipped container tests is not complete validation.

Record the exact commit, command, total test count, MariaDB suite/test count and
any skipped or disabled work. Do not claim a test passed unless it actually ran
at the reviewed SHA.

## Runtime artifacts

The build creates exactly two deployable artifacts:

- `paper/build/libs/EnthusiaStaff-Paper-<version>.jar`
- `velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar`

Inspect both jars for:

- correct entrypoints and metadata;
- no provider-owned API duplication;
- no test fixtures, development servers or private jars;
- no credentials, tokens, local configuration, databases, logs or generated
  reports; and
- no accidental third runtime plugin.

Build outputs, test reports, analyzer caches, downloaded dependencies, container
data and private configuration are not source artifacts.

## Wiki and documentation validation

Run:

```powershell
python scripts/wiki/validate_wiki.py
```

Documentation changes must keep these synchronized:

- `reports/REQUIREMENTS-MATRIX.md`
- `WORKSPACE-MANIFEST.md`
- `docs/development-blueprint.md`
- `docs/wiki/pages/Development-Blueprint.md`
- `docs/wiki/pages/Implementation-Status.md`
- affected staff, operator, developer, migration, configuration and recovery pages

The repository-managed Wiki is published from `docs/wiki/pages/`. Do not edit the
live Wiki as the only source of a change.

## Static analysis

Codacy PMD analysis reads the root `ruleset.xml`. Do not remove a rule merely to
lower the issue count. Resolve correctness, security, concurrency, transaction,
recovery, resource-ownership and data-integrity findings before structural or
style findings.

Suppressions must identify one exact rule and explain why the report is
incorrect or inapplicable. A zero-new-issues result means a branch did not worsen
the baseline; it does not mean the repository has no remaining findings.

## Pull-request gate

A merge candidate should normally provide:

1. Exact head SHA
2. Focused test results
3. Complete clean Java 21 validation
4. Complete MariaDB Testcontainers result
5. Two runtime jar inspection and hashes
6. Wiki validation when documentation changed
7. Hosted Codacy result and issue delta
8. Exact-head Pi staging when eligible
9. Known limitations and unavailable acceptance groups
10. Requirements-matrix and workspace-manifest updates when the checkpoint moves

The Pi staging workflow is a standalone Paper subset. It does not prove Velocity,
multi-backend, provider-plugin, Bedrock, Folia, live Discord, production data,
load, process-kill or 168-hour shadow acceptance.

## Feature definition of done

A feature is complete only when all applicable behavior, authority, persistence,
validation, concurrency, failure handling, restart recovery, duplicate safety,
audit, configuration, tests, verification output, staging and operational
documentation are complete.

Before moving a requirement to a higher status, confirm:

- domain policy is centralized;
- destructive intent is durable before side effects;
- transactions, revisions, leases, fencing and idempotency are correct;
- ambiguous external outcomes retry safely or enter visible quarantine;
- optional integration failure disables only the affected capability;
- event threads do not block on JDBC, HTTP, filesystem or socket I/O;
- exact permissions and rank boundaries are tested;
- real runtime behavior is staged where mocks cannot prove it; and
- rollback and operator recovery are documented.

## Cleaning local tooling

Gradle build directories and the repository-local `.gradle` directory are
disposable. Stop Gradle daemons before removing caches:

```powershell
.\gradlew.bat --stop
```

Testcontainers creates disposable containers and relies on its resource reaper.
Inspect `docker ps -a` before removing any leftover container so unrelated
containers are not affected. JDK, Docker and optional analyzer installations are
managed outside this repository; workstation-specific installation records
belong in an ignored local tooling log.