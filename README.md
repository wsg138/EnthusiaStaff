[![Codacy Badge](https://app.codacy.com/project/badge/Grade/d31dcbf6fd97454798cfdf8622acb891)](https://app.codacy.com/gh/wsg138/EnthusiaStaff/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![Codacy Coverage](https://app.codacy.com/project/badge/Coverage/d31dcbf6fd97454798cfdf8622acb891)](https://app.codacy.com/gh/wsg138/EnthusiaStaff/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_coverage)

# EnthusiaStaff

EnthusiaStaff is the Enthusia Network's distributed moderation and staff platform. The main Minecraft runtimes are **Paper** (staff tools, investigations and server-local actions) and **Velocity** (network identity, login enforcement and cross-server coordination), backed by MariaDB. The repository also contains StaffBot, a Cloudflare moderation web application, integration contracts and a transitional authority-bridge runtime.

The plugin is **live and under active development**. On November 11, 2026, the owner reported installing the Paper **0.1.1** JAR. That report does not independently verify other backends, Velocity, bot or web deployments, or every unfinished feature. The [implementation-status Wiki source](docs/wiki/pages/Implementation-Status.md) and [current roadmap](docs/current-roadmap.md) distinguish what is implemented, staged, blocked or verified.

## Start here

- **[Documentation guide](docs/README.md)** — one index for developers, operators, staff procedures, specifications, source locations and project history.
- **[Developer setup and validation](docs/development.md)** — build prerequisites, tests, CI requirements and review process.
- **[Architecture](docs/architecture.md)** and **[module map](docs/repository-cleanup/MODULE-RELOCATION-PLAN.md)** — code ownership, services, dependencies and safe migration constraints.
- **[Feature reference](docs/feature-reference.md)** and **[current roadmap](docs/current-roadmap.md)** — behavior and unfinished work.
- **[Wiki source](docs/wiki/README.md)** — maintained staff/operator/developer guides and protected publication workflow.
- **[External component and fork catalog](docs/repository-cleanup/EXTERNAL-COMPONENT-CATALOG.md)** — links, source-mirror status and integration constraints.
- **[Project history](docs/history/TIMELINE.md)** and **[repository cleanup status](docs/repository-cleanup/STATUS.md)** — why the code evolved, and how unfinished PR/branch cleanup is being handled.

## Build

Use **JDK 25**, the checked-in Gradle wrapper and Docker when running the MariaDB Testcontainers suites. Paper currently compiles against the Paper **26.2** API with Java 25 bytecode; other Java modules may target Java 21 bytecode. The module-specific runtime requirements are not interchangeable.

Windows:

```powershell
.\gradlew.bat clean test check runtimeJars
```

Linux/macOS:

```bash
./gradlew clean test check runtimeJars
```

The build produces versioned Paper, Velocity, authority-bridge and StaffBot artifacts from their respective modules. For Paper and Velocity, typical paths are `paper/build/libs/EnthusiaStaff-Paper-<version>.jar` and `velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar`. The stable distributed Paper plugin filename remains **`EnthusiaStaff-Paper.jar`**, and the Paper plugin identifier remains **`EnthusiaStaff`**. Building artifacts does **not** deploy them or authorize configuration/database changes.

## Repository conventions

The Java modules currently live at the **repository root**, not under the proposed future `src/` hierarchy. See the [source-location index](docs/README.md) before editing. Source copies under `components/` have independent owners and sync constraints; do not delete or overwrite them based on outdated mirror metadata.

Historical AI handoffs, policy proposals, migration plans and release manifests are retained for evidence. They are not authoritative current instructions merely because they remain in Git history. Current behavior is established by merged code and exact-version verification; [desired product requirements](ENTHUSIASTAFF-GOALS.md) describe the intended finished scope.
