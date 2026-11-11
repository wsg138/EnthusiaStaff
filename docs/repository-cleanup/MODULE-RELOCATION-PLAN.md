# W5 — Module and relocation dependency plan

**Tracking:** [repository cleanup issue #426](https://github.com/wsg138/EnthusiaStaff/issues/426).  
**Read-only audit base:** [`8d6ca75909a3568664ac543852a5ac0d94dda4b6`](https://github.com/wsg138/EnthusiaStaff/commit/8d6ca75909a3568664ac543852a5ac0d94dda4b6), 2026-11-11.  
**Disposition:** Architecture inventory and migration proposal **only**. No Java, Gradle, workflow, file layout, provider source, or production state has been changed. Current source/build configuration outranks old workspace handoffs. See [EXTERNAL-COMPONENT-CATALOG.md](EXTERNAL-COMPONENT-CATALOG.md) for the separate mirror/fork findings.

## Build and source inventory

The root [settings.gradle.kts](../../settings.gradle.kts) includes **12 Gradle Java subprojects**. The root [build.gradle.kts](../../build.gradle.kts) sets group `net.enthusia.staff`, default `version=0.1.1` (overridable via `releaseVersion`), a Java 25 toolchain, and `--release 21` by default; **Paper overrides its compiler release to 25**. It wires `runtimeJars` and repository-wide JaCoCo. `moderation-web/` is an additional **independent Node/Cloudflare package**, *not* a Gradle subproject. All entries below were checked against their own build files, source/resource paths, and tree at the audit base.

| Present root | Role / Java entry or executable | Internal project dependencies (direct) | Build artifact or deployability |
| --- | --- | --- | --- |
| `common/` | Java shared security, values, infrastructure | none | Gradle library; default versioned library JAR, not standalone deploy |
| `domain/` | Java business domain, application ports and services | `:common` (api) | Gradle library JAR |
| `persistence/` | Java MariaDB/Hikari/Flyway persistence and SQL migrations | `:domain` (api) | Gradle library JAR; migrations in `src/main/resources/db/migration/` |
| `protocol/` | Java cross-runtime protocol, TLS and messaging | `:domain` (api) | Gradle library JAR |
| `discord-platform-api/` | Java published Discord-facing API contract | none | Gradle library plus Maven publication artifactId `discord-platform-api`; not a running service |
| `moderation-platform-api/` | Java published moderation API contract | none | Gradle library plus Maven publication artifactId `moderation-platform-api`; not a running service |
| `integration-contracts/` | Java compile/test integration contracts, Paper API compile-only | none | `tasks.jar.enabled=false`: **no plain runtime JAR**; compiled classes used by Paper tests |
| `paper/` | Paper plugin: `net.enthusia.staff.paper.EnthusiaStaffPaperPlugin` via `plugin.yml` | `:domain`, `:persistence`, `:protocol`, `:discord-platform-api`, `:moderation-platform-api`; compile-only `:integration-contracts`; additional bridge source path | Shadow runtime `paper/build/libs/EnthusiaStaff-Paper-<version>.jar`; stable distributed asset `EnthusiaStaff-Paper.jar` |
| `paper-authority-bridge/` | Separate Paper transition plugin: `net.enthusia.staff.authoritybridge.EnthusiaStaffAuthorityBridgePlugin` | `:domain`, `:persistence`, `:protocol` | Shadow runtime `paper-authority-bridge/build/libs/EnthusiaStaff-AuthorityBridge-<version>.jar`; verifier checks forbidden/required classes and migrations |
| `velocity/` | Velocity plugin: `net.enthusia.staff.velocity.EnthusiaStaffVelocityPlugin` (annotated `@Plugin`) | `:domain`, `:persistence`, `:protocol` | Shadow runtime `velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar`; stable distributed asset `EnthusiaStaff-Velocity.jar` |
| `staff-bot/` | Java/JDA application `net.enthusia.staff.discordbot.StaffBotApplication` | `:persistence`, `:protocol` | Shadow executable `staff-bot/build/libs/EnthusiaStaff-StaffBot-<version>.jar`; release/CI also uses stable `EnthusiaStaff-StaffBot.jar` |
| `integration-tests/` | JUnit/Testcontainers suite; no server application entrypoint | test-time `:domain`, `:persistence`, `:protocol`, `:velocity`, plus Paper shaded runtime by file discovery | Test-only workload; **not** a distributable plugin/service |
| `moderation-web/` | Cloudflare Worker `src/index.js`, Durable Object, static assets copied from staff-bot preview resources | **Filesystem** dependency on `staff-bot/src/main/resources/moderation-preview`; no Gradle dependency | `npm run build` → `dist/`, `npm run check` incl. Wrangler dry run; staging and production Wrangler config files, no deploy implied |

Relevant evidence: [root project list](../../settings.gradle.kts), [root tasks/version](../../build.gradle.kts), [Paper build](../../paper/build.gradle.kts), [bridge build](../../paper-authority-bridge/build.gradle.kts), [Velocity build](../../velocity/build.gradle.kts), [bot build](../../staff-bot/build.gradle.kts), [integration tests](../../integration-tests/build.gradle.kts), [moderation web build](../../moderation-web/scripts/build.mjs), [web package](../../moderation-web/package.json). The Paper [plugin descriptor](../../paper/src/main/resources/plugin.yml) establishes the **EnthusiaStaff** plugin name; the [Velocity annotation](../../velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java) currently contains the literal version `0.1.1`. A future version migration must keep generated filenames, resource expansion, Velocity annotation, and release metadata aligned; changing only `releaseVersion` is not proof of that alignment.

### Effective dependency graph

```text
common ──> domain ──> persistence ───────────┐
                 └──> protocol ──────────────┤
discord-platform-api ────────────────────────┤
moderation-platform-api ─────────────────────┤
integration-contracts [compile/test only] ───┤
paper-authority-bridge/src/main/java ────────┤  [DIRECT SOURCE DIRECTORY EMBED; excludes plugin entry]
                                              └──> paper/shadowJar

domain + persistence + protocol ─────────────────> velocity/shadowJar
domain + persistence + protocol ─────────────────> paper-authority-bridge/shadowJar
persistence + protocol ──────────────────────────> staff-bot/shadowJar
staff-bot/src/main/resources/moderation-preview ─> moderation-web/scripts/build.mjs ─> Cloudflare assets
velocity compiled classes + paper shaded JAR ────> integration-tests:test
root runtimeJars ─────────────────────────────────> Paper + bridge verification + Velocity + bot
```

The Java Gradle project edges above are **direct declared dependencies**; transitive dependencies flow through project APIs. The bridge source inclusion is *not* just a project dependency: [`paper/build.gradle.kts`](../../paper/build.gradle.kts) adds `project(":paper-authority-bridge").file("src/main/java")` and excludes the bridge plugin class, while the separate bridge build draws migration resources from `project(":persistence")`. The [aggregate coverage report](../../build.gradle.kts) explicitly suppresses duplicate Paper/bridge classes. These three consumers must change in one controlled migration, or the runtime and coverage report can diverge.

The Paper [`plugin.yml`](../../paper/src/main/resources/plugin.yml) declares soft dependencies on independent providers including Currency, Market, Commend, RoseChat-related integration, and AutoClicker. These are **runtime integration contracts**, not evidence that their source copies are compiled by root Gradle. The five tracked external component directories contain both metadata-only entries and significant source trees; see the component catalog.

## Demonstrated path and filename consumers

These are **known breakpoints**, not an exhaustive guarantee that no other paths exist outside this repository. Review source, docs, remote deployment tooling, and active PR branches before changing them.

| Consumer / evidence | Hard assumption |
| --- | --- |
| [`settings.gradle.kts`](../../settings.gradle.kts), [root Gradle](../../build.gradle.kts), 12 subproject build scripts | Current Gradle project names and conventional project directories; build-root override named per project |
| [`paper/build.gradle.kts`](../../paper/build.gradle.kts), [`paper-authority-bridge/build.gradle.kts`](../../paper-authority-bridge/build.gradle.kts), [JaCoCo aggregate](../../build.gradle.kts) | Bridge Java source location, excluded plugin entry, migration resources, duplicate classes |
| [`integration-tests/build.gradle.kts`](../../integration-tests/build.gradle.kts) | `velocity/build/classes/java/main` and exactly one `paper/build/libs/EnthusiaStaff-Paper-*.jar` |
| [`coverage-measurement.yml`](../../.github/workflows/coverage-measurement.yml) | Paper/Velocity build paths, globbed jar names and `runtimeJars`; coverage/report paths |
| [`staff-platform-staging-release.yml`](../../.github/workflows/staff-platform-staging-release.yml) | Both plugin JAR globs, stable released `EnthusiaStaff-Paper.jar`/`EnthusiaStaff-Velocity.jar`, checksums and plugin identity |
| [`sentinel-restart-artifact.yml`](../../.github/workflows/sentinel-restart-artifact.yml) | `:paper:shadowJar` and `:paper-authority-bridge:shadowJar`; expected build paths and stable artifact copies |
| [`staff-state-reset-runtime-proof.yml`](../../.github/workflows/staff-state-reset-runtime-proof.yml) | Paper build/runtime path, stable test JAR name, separate Maven verification under `validation/` |
| [`staff-bot-pr-artifact.yml`](../../.github/workflows/staff-bot-pr-artifact.yml), [`staff-bot-staging-release.yml`](../../.github/workflows/staff-bot-staging-release.yml), [`staff-bot-configuration-cache.yml`](../../.github/workflows/staff-bot-configuration-cache.yml) | `staff-bot/**` event filters, Gradle task paths, JAR location and stable bot asset |
| [`moderation-web-staging-deploy.yml`](../../.github/workflows/moderation-web-staging-deploy.yml), [`moderation-web-validation.yml`](../../.github/workflows/moderation-web-validation.yml) | `moderation-web/**` and `staff-bot/src/main/resources/moderation-preview/**` filters, npm working directory, Wrangler config |
| [`moderation-web/scripts/build.mjs`](../../moderation-web/scripts/build.mjs) | Relative `../staff-bot/src/main/resources/moderation-preview` source path |
| [`pi-staging-check.yml`](../../.github/workflows/pi-staging-check.yml), [`pi-staging-control-validation.yml`](../../.github/workflows/pi-staging-control-validation.yml) | `scripts/staging` source validator, build helper, staging artifact path and selected source SHA |
| [`wiki-publish.yml`](../../.github/workflows/wiki-publish.yml), [`wiki-validate.yml`](../../.github/workflows/wiki-validate.yml) | `scripts/wiki/validate_wiki.py` and source-controlled wiki |
| [`tools/component-sync/README.md`](../../tools/component-sync/README.md), [`COMPONENT-REGISTRY.md`](../../ai-agents/work-packages/COMPONENT-REGISTRY.md) | Explicit `components/<name>` paths, metadata exclusion, same-package standalone/aggregate comparison |
| [`README.md`](../../README.md), [`docs/repository-cleanup/README.md`](README.md), [upgrade manifest](../../UPGRADE-MANIFEST.md) | User-facing commands and artifact name references, some **historical** and not authoritative for today's runtime |
| [`EnthusiaStaff.code-workspace`](../../EnthusiaStaff.code-workspace) | Editor workspace paths; update without silently altering user settings |

The validated [`tools/` tree](../../tools) contains component sync/orchestration, D13 parity, and bot chat preflight; [`scripts/`](../../scripts) contains staging validation/control and wiki tooling. Moving scripts to `tooling/` also requires workflow/script import and test path changes. Nested `components/enthusia-site/.github/workflows` belong to a copied standalone repository tree and do **not** automatically execute as root EnthusiaStaff workflows.

## Proposed readable layout (NOT implemented)

```text
src/
  minecraft/
    paper/                 <- paper
    velocity/              <- velocity
    authority-bridge/      <- paper-authority-bridge
  services/
    staff-bot/             <- staff-bot
    moderation-web/        <- moderation-web
  libraries/
    common/                <- common
    domain/                <- domain
    persistence/           <- persistence
    protocol/              <- protocol
    integration-contracts/ <- integration-contracts
    integration-tests/     <- integration-tests (test suite; optional separate tests/ location)
  apis/
    discord-platform-api/  <- discord-platform-api
    moderation-platform-api/ <- moderation-platform-api
external/
  README.md                <- verified repository links, SHA/provenance/rights/parity/status only
tooling/
  staging/                 <- scripts/staging; after all consumers migrate
  wiki/                    <- scripts/wiki
  component-sync/          <- tools/component-sync
  ...                      <- remaining tools after consumer audit
```

Retain the **logical Gradle project paths** (`:paper`, `:velocity`, etc.) where possible via explicit `project(":paper").projectDir = file("src/minecraft/paper")` mappings, rather than renaming projects and triggering unnecessary publication/task breakage. The lines here are proposed examples, **not** a tested build configuration. Preserve `rootProject.name = "EnthusiaStaff"`, `plugin.yml` name `EnthusiaStaff`, Velocity plugin identity, and both **stable release** assets `EnthusiaStaff-Paper.jar` and `EnthusiaStaff-Velocity.jar`. Keep versioned build files and artifact checksummed release behavior compatible.

## Migration stages and hard gates

1. **Freeze evidence and consumers.** Rebase a future migration PR on a new exact main SHA. Record per-project task graph, dependency locks, full integration test acceptance and existing JAR filenames/hashes. Search references across entire current tree, wiki, scripts, hosted workflows, downstream repos and installation documentation; the present table is not proof of absence elsewhere.
2. **Tooling aliases first, without breaking callers.** Introduce wrapper/compatibility paths or paired workflow/script updates with tests; only retire old `scripts/` and `tools/` paths after staging source validator, wiki publishing, component-sync tests, CI path filters and historical references are verified. A bare `git mv` is insufficient.
3. **Service coupling as one reviewed unit.** Relocate `staff-bot` and `moderation-web` only with the preview asset path, build scripts, npm working directories, stage/prod Wrangler configs, path filters, JAR discovery and replay/security checks updated atomically. Test staging **dry runs**, do not deploy.
4. **Libraries and APIs.** Move `common/domain/persistence/protocol` with stable logical Gradle IDs; then both API projects and `integration-contracts`. Check SQL migration packaging, public publication coordinates, integration test runtime classpath and duplicate coverage. Verify no unintentional public API surface change.
5. **Minecraft runtimes last.** Move Paper, Velocity, and authority bridge in an intentionally coordinated change: preserve Paper embedded bridge sources, bridge standalone plugin entry exclusion, authority-bridge migration resources, artifact classifiers and JaCoCo deduplication. Rewrite each workflow/test path in that same PR, not in a later cleanup PR.
6. **External catalog decision as a separate policy review.** Do **not** replace `components/` with links until independent repo authority, license rights, exact-head parity or divergence reconciliation, CI/consumers, PR package workflow and code-history preservation are addressed. Site, Currency and Commend are currently **diverged** from independent heads; Market and RoseChat are metadata-only. RoseChat's redistribution restriction is a separate hard gate.
7. **Deprecate compatibility paths only after verification.** Check all package references, staged install manifests and GitHub links; remove redirects/shims in a separately reviewed change, not within a blind tree reshuffle.

**Each physical-move PR must pass** clean `./gradlew clean test check runtimeJars` plus aggregate coverage and relevant Testcontainers suites with a supported Java toolchain; `npm ci` and `npm run check` for moderation-web; Maven provider tests when provider copies/consumers are involved; relevant Python/shell scripts and static analyzers; updated CI and hosted Codacy **zero new valid findings**. Run artifact identity/manifest/JAR zip checks on the exact commit, verify no unexpected filename or plugin-id changes, and prove rejection/retry/recovery paths for changed tooling. Obtain independent review and hosted CI before merging. **Do not equate a successful build with production deployment authorization.**

**Rollback:** Each migration PR is one revertable commit/series maintaining prior reviewed source state. Tag/record base commit and artifact identity; if a stage breaks, revert that stage (including paired path consumers) and rerun full validation on the restored head. Never force-push/rewrite main or reset migrations/data. Existing running Paper/Velocity JARs remain untouched throughout the documentation and code-layout exercise.

## Unknowns requiring coordinator resolution

- Which current independent provider commit should be mirrored or referenced, given confirmed drift (documented in the catalog)? Historical metadata parity does **not** establish current parity.
- Whether the aggregate mirror/two-PR policy should be **formally superseded** by authoritative standalone repositories plus immutable links, and how downstream builds/PR acceptance will work.
- Complete downstream/off-GitHub consumer list: server SFTP deployment scripts, custom releases, bookmarks, external repositories and wiki/historical links; GitHub-only analysis cannot prove these absent.
- Runtime/live status of authority bridge, Velocity, bot and moderation web; repo build/config presence and the owner's Paper JAR installation report do not verify all components are deployed.
- Whether integration tests should live under `src/libraries/` or a distinct `tests/` parent (developer navigation preference, not a technical necessity).

**W5 recommendation:** Keep physical layout and mirrored code unchanged during cleanup. First establish one canonical component policy and exact-head reconciliation; only then perform small dependency-aware relocation PRs with artifacts/CI and rollback evidence.
