# Development and validation

## Prerequisites

- JDK 21
- Git
- A Docker Engine that exposes API 1.44 or newer to the build shell

The repository uses the checked-in Gradle wrapper. It does not require a
machine-wide Gradle installation, and it does not install a persistent service.

## Full validation

Run the complete validation from the repository root:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

On Linux or macOS, use `./gradlew` with the same arguments.

Docker must be reachable before starting the build. The integration-test module
uses temporary MariaDB containers. Those tests are mandatory: a run with
skipped container tests is not a complete validation.

The build creates exactly two deployable artifacts:

- `paper/build/libs/EnthusiaStaff-Paper-<version>.jar`
- `velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar`

Build outputs, test reports, local analyzer caches, downloaded dependencies,
container data, and private configuration are not source artifacts.

## Static analysis

Codacy PMD analysis reads the root `ruleset.xml`. Keep that ruleset limited to
rules that apply to this Java runtime, but do not remove a rule merely to lower
the issue count. Resolve correctness, security, concurrency, transaction,
recovery, resource-ownership, and data-integrity findings before structural or
style findings.

Analyzer suppressions must identify one exact rule and include a reason that
demonstrates why the report is incorrect or inapplicable. Legitimate findings
remain active until they are fixed or explicitly documented for review.

## Cleaning local tooling

Gradle build directories and the repository-local `.gradle` directory are
disposable and are recreated on demand. Stop Gradle daemons before removing
caches:

```powershell
.\gradlew.bat --stop
```

Testcontainers creates disposable containers and relies on its resource reaper
for cleanup. Inspect `docker ps -a` before removing any leftover container so
that unrelated development containers are not affected.

JDK, Docker, and optional analyzer installations are managed outside this
repository. Remove them with the same operating-system package manager or tool
installer that added them. Exact workstation-specific installation records and
removal commands belong in an ignored local tooling log, not in public project
documentation.
