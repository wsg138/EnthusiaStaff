[![Codacy Badge](https://app.codacy.com/project/badge/Grade/d31dcbf6fd97454798cfdf8622acb891)](https://app.codacy.com/gh/wsg138/EnthusiaStaff/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![Codacy Coverage](https://app.codacy.com/project/badge/Coverage/d31dcbf6fd97454798cfdf8622acb891)](https://app.codacy.com/gh/wsg138/EnthusiaStaff/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_coverage)

# EnthusiaStaff

EnthusiaStaff is the Enthusia Network moderation and staff runtime. It ships one Paper 1.21.11 jar and one Velocity 3.4 jar backed by MariaDB. The Paper runtime owns staff workflows and server-local state; Velocity owns network login enforcement, protected network identity matching, migration coordination, and restricted website/API delivery.

The project is pre-release. LiteBans remains authoritative until the documented 168-hour shadow window and final cutover gate pass. Do not install the jars as active moderation authority by merely building them.

## Requirements

- Java 21
- Paper API 1.21.11-compatible servers
- Velocity 3.4-compatible proxy
- MariaDB with separate application and migration credentials
- Existing LiteBans MariaDB source during migration
- Versioned HMAC and AES keys for protected network identity migration

Optional integrations degrade independently. The current Polar 1.7.11-beta loader does not expose a supported violation event API, so automatic Polar punishment remains disabled unless a compatible API is supplied.

## Build

```powershell
.\gradlew.bat clean test check runtimeJars
```

Deployable artifacts are produced as `paper/build/libs/EnthusiaStaff-Paper-<version>.jar` and `velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar`. Build outputs, private dependency jars, local servers, and caches are not source artifacts and must not be committed.

## Operations and design

- [Development and validation](docs/development.md)
- [Architecture](docs/architecture.md)
- [Database and recovery model](docs/database.md)
- [Security and authority policy](docs/security.md)
- [Persistent channel TLS](docs/channel-tls.md)
- [Punishment workflow and durable drafts](docs/punishment-workflow.md)
- [Inventory and confiscation safety](docs/inventory-safety.md)
- [LiteBans migration](docs/litebans-migration.md)
- [Shadow mode](docs/shadow-mode.md)
- [Cutover](docs/cutover.md)
- [Rollback](docs/rollback.md)
- [Upgrade manifest](UPGRADE-MANIFEST.md)

No migration, cutover, deployment, jar removal, or production-data operation is performed by the build.
