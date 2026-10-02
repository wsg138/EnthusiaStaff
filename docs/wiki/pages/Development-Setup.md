# Development Setup

Use this page to prepare a clean EnthusiaStaff development environment. Before changing behavior, use [[Developer Guide Index]] to find the owning feature and [[Code Review Guide]] for its invariants.

## Prerequisites

- Git
- Java 25 JDK
- Docker compatible with MariaDB Testcontainers
- Python 3 for Wiki validation
- Node/npm when changing `moderation-web/` or site frontend/functions
- an IDE with Gradle support
- any required private compile-only provider artifacts for the exact task
- no production secrets/private player/evidence data in the checkout

## Checkout

```bash
git clone https://github.com/wsg138/EnthusiaStaff.git
cd EnthusiaStaff
git fetch --all --tags --prune
```

Create work from current legitimate `main`. Preserve existing worker branches/commits and keep unrelated work separate.

## Read before editing

1. Authoritative goals/focused specification for intended behavior.
2. Current merged code/config/migrations/tests for implemented behavior.
3. [[Implementation Status]] and the matching feature/deep-dive page.
4. [[Developer Code Guide]] for source ownership.
5. [[Code Review Guide]] for failure/security boundaries.
6. [[Build and Testing]] for what evidence is required.

Historical package records explain prior execution; they are not automatically the current product state.

## Java and Gradle

```bash
java -version
./gradlew --version
```

Both should use Java 25.

Complete Java validation is documented in [[Build and Testing]]. The root `runtimeJars` task builds/verifies four Java runtimes:

```text
paper/build/libs/EnthusiaStaff-Paper-<version>.jar
paper-authority-bridge/build/libs/EnthusiaStaff-AuthorityBridge-<version>.jar
velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar
staff-bot/build/libs/EnthusiaStaff-StaffBot-<version>.jar
```

Paper and Velocity are Minecraft runtimes; the authority bridge is a narrow transition plugin; StaffBot is a standalone Java/JDA application.

## Docker and MariaDB

Confirm Testcontainers can reach Docker before calling a validation checkpoint complete. Use disposable local schemas/containers and never point tests at production MariaDB/LiteBans data.

## Node / web development

For the staging moderation workspace:

```bash
cd moderation-web
npm install --no-package-lock --ignore-scripts
npm run check
```

`components/enthusia-site/` is a synchronized public site component. Before editing aggregate component code, check its component metadata/parity process and the standalone-source relationship.

See [[Website and Web API]].

## Local secrets

- use disposable/non-production credentials;
- keep real Discord bot tokens, DB credentials, signing material and private service origins outside tracked files;
- generate disposable TLS material;
- do not copy production provider jars/configuration/data into committed paths;
- never commit `.env`, secrets, runtime folders, DBs, logs, private evidence or generated reports.

StaffBot’s file-backed configuration mode is staging-only and must use the supported complete secret-file pair. See [[Staff Bot Runtime and Operations]].

## Repository shape

```text
common/
domain/
integration-contracts/
discord-platform-api/
persistence/
protocol/
paper/
paper-authority-bridge/
velocity/
staff-bot/
integration-tests/
moderation-web/
components/enthusia-site/
docs/
```

See [[Architecture]] for dependency/runtime ownership.

## Development loop

1. Identify the owning domain service/policy before editing an adapter.
2. Identify durable store/migration/recovery state.
3. Identify the runtime that performs the effect: Paper, bridge, Velocity, StaffBot, provider or web boundary.
4. Run focused tests while developing.
5. Add authority/stale-state/duplicate/replay/restart/failure/concurrency coverage appropriate to the risk.
6. Review thread/lifecycle ownership for every async hop.
7. Review Java/Bedrock/provider/Discord/browser fallback assumptions.
8. Run the complete clean gate from [[Build and Testing]].
9. Inspect all relevant runtime artifacts and web checks.
10. Run exact-candidate staging appropriate to the claim.
11. Update the focused Wiki page that owns changed human-facing behavior.

A green local branch does not waive provider, Velocity, multi-server, Bedrock, Folia, Discord, website, load, migration, rollback or production acceptance when relevant.

## Focused entry points

- Discord/StaffBot: [[Discord Moderation Platform]], [[Staff Bot Runtime and Operations]]
- Website/API/browser UI: [[Website and Web API]]
- Paper/Velocity transport: [[Protocol and Network Traffic]]
- Player-state safety: [[Staff Tools, Investigations, and Player-State Safety]]
- Detailed source map: [[Developer Code Guide]]

## Wiki documentation

Repository-managed Wiki source lives in `docs/wiki/pages/`.

```bash
python scripts/wiki/validate_wiki.py
```

Keep common pages concise; move implementation/source/review detail to focused pages. Do not hand-edit the live Wiki as the source of truth. See [[Wiki Maintenance]].

## Related pages

- [[Developer Guide Index]]
- [[Architecture]]
- [[Developer Code Guide]]
- [[Code Review Guide]]
- [[Build and Testing]]
- [[Wiki Maintenance]]