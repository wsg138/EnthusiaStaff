# Development Setup

## Prerequisites

- Git
- Java 21 JDK
- Docker with a working daemon for MariaDB Testcontainers
- An IDE with Gradle support
- Access to any required private compile-only provider APIs
- No production secrets in the development checkout

## Checkout

```bash
git clone https://github.com/wsg138/EnthusiaStaff.git
cd EnthusiaStaff
git fetch --all --tags --prune
```

Create feature branches from the latest applicable `main`. Do not commit
directly to `main`, merge automatically, publish releases, deploy, or change
production data.

## Java

```bash
java -version
./gradlew --version
```

Both must use Java 21. Compilation uses `-Xlint:all -Werror`.

## Docker

Run a simple container and confirm Testcontainers can reach the daemon. MariaDB
integration tests are part of meaningful validation, not optional decoration.

## Local configuration

- Copy example files; do not edit tracked defaults with secrets.
- Use disposable MariaDB schemas.
- Generate local TLS material.
- Use non-production webhook destinations or disable them.
- Keep private jars under ignored local paths.
- Never commit `.env`, credentials, runtime folders, databases, logs, or build
  output.

## Workspace boundaries

Related plugins remain separate Git repositories even when arranged under one
workspace. Do not flatten histories or push `wsg138` work to unrelated
BadgersMC remotes.

## Documentation changes

Wiki source lives in `docs/wiki/pages`. Run:

```bash
python scripts/wiki/validate_wiki.py
```

The live Wiki is published manually after the main-repository pull request is
approved and merged.
