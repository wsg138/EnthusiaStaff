# Development Setup

Read [[Development Blueprint]] and [[Implementation Status]] before starting a
major feature. The blueprint identifies the ordered work and release gates; the
status page identifies what is actually proven.

## Prerequisites

- Git
- Java 21 JDK
- Docker daemon exposing API 1.44 or newer for MariaDB Testcontainers
- Python 3 for Wiki validation
- An IDE with Gradle support
- Access to required private compile-only provider APIs
- No production secrets in the checkout

## Checkout

```bash
git clone https://github.com/wsg138/EnthusiaStaff.git
cd EnthusiaStaff
git fetch --all --tags --prune
```

Create coherent feature branches from the latest applicable `main`. Preserve
existing branch commits and keep unrelated or concurrent work separate. Never
recreate a branch by silently discarding work.

The authoritative development references are:

```text
ENTHUSIASTAFF-GOALS.md
reports/REQUIREMENTS-MATRIX.md
docs/development-blueprint.md
WORKSPACE-MANIFEST.md
```

Update the matrix and manifest when a new verified root checkpoint is established.

## Java

```bash
java -version
./gradlew --version
```

Both must use Java 21. Compilation uses `-Xlint:all -Werror`.

## Docker

Confirm Testcontainers can reach Docker before starting a complete build. MariaDB
integration tests are meaningful validation, not optional decoration. Record the
actual container suite and test counts.

## Local configuration

- Copy example files; do not add secrets to tracked defaults.
- Use disposable MariaDB schemas.
- Generate local TLS material.
- Use non-production webhook destinations or disable them.
- Keep private jars under ignored local paths.
- Never commit `.env`, credentials, runtime folders, databases, logs, build
  output, private evidence or generated reports.

## Workspace boundaries

Related plugins remain separate Git repositories even when arranged under one
workspace. Do not flatten histories or push `wsg138` work to unrelated remotes.

Provider contracts in `integration-contracts/` are compile-time boundaries, not
permission to copy provider-owned APIs into runtime jars. Real provider behavior
must be implemented and staged in the owning repository.

## Development loop

1. Read the relevant goals, matrix row and blueprint milestone.
2. Identify domain, persistence, runtime adapter, configuration, recovery, tests,
   verification and documentation changes.
3. Run focused tests while editing.
4. Add hostile-input, permission, stale-state, duplicate, restart, failure and
   concurrency coverage where applicable.
5. Run the complete clean validation from [[Build and Testing]].
6. Inspect exactly two runtime jars.
7. Review Codacy without hiding legitimate findings.
8. Run exact-head Pi staging when eligible.
9. Record the exact SHA and unavailable acceptance groups.

A green branch does not waive Velocity, provider, multi-server, Bedrock, Folia,
load, migration, rollback or shadow gates.

## Documentation changes

Wiki source lives in `docs/wiki/pages`. Run:

```bash
python scripts/wiki/validate_wiki.py
```

Keep these synchronized when development status changes:

- [[Development Blueprint]]
- [[Implementation Status]]
- [[Build and Testing]]
- affected staff, operator, integration, migration, recovery and developer pages

The live Wiki is published from reviewed repository source. Do not make the live
Wiki the only copy of a documentation change.