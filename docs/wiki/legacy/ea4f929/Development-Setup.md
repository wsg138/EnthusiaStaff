# Development Setup

## Prerequisites

- Git
- JDK 21
- A Docker Engine reachable from the shell running Gradle
- Docker API 1.44 or newer

The checked-in Gradle wrapper supplies the build tool. A system-wide Gradle
installation is not required.

## Checkout

Clone the repository and enter its root:

```shell
git clone https://github.com/wsg138/EnthusiaStaff.git
cd EnthusiaStaff
```

Use the repository's current development branch when contributing and keep
unrelated local files, secrets, private dependency jars, server directories,
and generated artifacts outside commits.

## Java verification

Confirm that Java 21 is active:

```shell
java -version
```

The build compiles with Java 21, enables all compiler lint warnings, and treats
warnings as errors.

## Docker verification

Confirm that Docker is reachable before running the complete test suite:

```shell
docker version
docker ps
```

Integration tests create temporary MariaDB containers and rely on
Testcontainers for cleanup. Inspect containers before manually removing
anything so unrelated development services are not affected.

## Local configuration

Production credentials, database passwords, HMAC keys, AES keys, certificates,
and private integration artifacts must not be committed. Use environment
variables or the platform's secret store as documented by the relevant runtime
configuration.

See [[Build and Testing]] for validation commands and the
[development document](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/development.md)
for the source-controlled workflow.
