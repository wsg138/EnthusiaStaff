# Development workflow

This document explains the contributor workflow for EnthusiaStaff. It does not
repeat feature percentages, the remaining-work map, the code map or detailed
operator procedures.

## Choose the correct source

| Question | Primary source |
| --- | --- |
| What must the finished platform do? | `ENTHUSIASTAFF-GOALS.md` |
| What is implemented and what evidence exists? | `reports/REQUIREMENTS-MATRIX.md` |
| How complete is each feature? | `docs/wiki/pages/Implementation-Status.md` |
| What feature section should be developed next? | `docs/wiki/pages/Development-Blueprint.md` |
| Which files and services own a feature? | `docs/wiki/pages/Developer-Code-Guide.md` |
| How is a change validated? | `docs/wiki/pages/Build-and-Testing.md` |
| What is the current cross-repository checkpoint? | `WORKSPACE-MANIFEST.md` |

A class, command, test file or green pull request does not by itself establish
feature completion or production readiness.

## Prerequisites

- JDK 21
- Git
- Docker Engine API 1.44 or newer available to the build shell
- Python 3 for Wiki validation

Use the checked-in Gradle wrapper. A machine-wide Gradle installation is not
required.

## Branch and scope rules

- Start coherent work from the latest applicable `main`.
- Keep unrelated or concurrent work in separate branches.
- Preserve existing branch commits when rebasing or reconciling.
- Resolve overlapping lifecycle, persistence, command and configuration ownership
  deliberately; do not keep two sources of truth.
- Never combine evidence from different revisions into one claimed checkpoint.
- Update the requirements matrix and workspace manifest when a root checkpoint moves.
- Do not commit build outputs, local databases, runtime folders, private jars,
  logs, generated reports, secrets or workstation configuration.

## Development loop

1. Read the relevant goal and requirements-matrix row.
2. Check `docs/wiki/pages/Development-Blueprint.md` for the owning unfinished section.
3. Locate the feature trace in the Developer Code Guide.
4. Identify affected domain behavior, durable stores, runtime adapters,
   configuration, recovery rules and documentation.
5. Run focused tests while editing.
6. Add hostile-input, permission, stale-state, duplicate, restart,
   partial-failure and concurrency coverage where applicable.
7. Review blocking I/O, transaction boundaries, executor ownership and shutdown.
8. Run the complete validation before declaring a checkpoint.

## Focused tests

Windows examples:

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

Docker must be reachable. MariaDB Testcontainers suites are part of complete
validation and cannot be silently skipped.

Record the exact revision, command, test totals, MariaDB totals, skipped work and
unavailable staging groups. Detailed validation expectations belong in the
[Build and Testing](wiki/pages/Build-and-Testing.md) page.

## Runtime artifacts

The build must create exactly:

- `paper/build/libs/EnthusiaStaff-Paper-<version>.jar`
- `velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar`

Inspect both for correct entrypoints, dependency packaging, provider API leaks,
private material and accidental extra runtime artifacts.

## Documentation changes

Run:

```powershell
python scripts/wiki/validate_wiki.py
```

Update only the pages that own the changed information:

- exact evidence: `reports/REQUIREMENTS-MATRIX.md`;
- cross-repository checkpoint: `WORKSPACE-MANIFEST.md`;
- percentages: `docs/wiki/pages/Implementation-Status.md`;
- remaining feature sections: `docs/wiki/pages/Development-Blueprint.md`;
- code paths: the Developer Code Guide;
- validation procedure: Build and Testing; and
- staff/operator behavior: the affected focused Wiki guide.

The repository-managed Wiki source is `docs/wiki/pages/`. Do not make the live
Wiki the only copy of a change.

## Static analysis

Codacy PMD uses the root `ruleset.xml`. Fix correctness, security, concurrency,
transaction, recovery, resource-ownership and data-integrity findings rather than
weakening rules. A suppression must target one exact false positive and explain why.

## Pull-request gate

A merge candidate normally records:

1. Exact head revision
2. Focused test results
3. Complete Java 21 and MariaDB validation
4. Exactly two inspected runtime jars and hashes
5. Wiki validation when documentation changed
6. Hosted Codacy result and issue delta
7. Exact-head Pi staging when eligible
8. Known limitations and unavailable acceptance groups
9. Requirements-matrix and manifest changes when the checkpoint moved

The standalone Pi Paper gate does not prove Velocity, providers, multi-backend,
Bedrock, Folia, live Discord, production data, load, process-kill or shadow
acceptance.

## Local cleanup

Build directories and the repository-local `.gradle` directory are disposable.
Stop Gradle daemons before deleting caches:

```powershell
.\gradlew.bat --stop
```

Inspect Docker resources before deleting leftovers so unrelated containers are
not affected.
