# Build and Testing

Use this page to build one exact revision and understand what each validation layer can actually prove. For review invariants use [[Code Review Guide]]. For current product status use [[Implementation Status]].

## Complete Java validation

Windows:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

Linux/macOS:

```bash
./gradlew --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

Docker must be available for the MariaDB Testcontainers suites. A run that skips required container tests is not a complete repository checkpoint.

Record the exact commit SHA, command, executed/skipped suites, artifact names/hashes, hosted-analysis results and any runtime/staging run IDs. Do not combine evidence from different commits into one exact-head claim.

## Focused Java tests

Use focused tests while editing, then rerun the complete clean gate:

```bash
./gradlew :domain:test
./gradlew :persistence:test
./gradlew :protocol:test
./gradlew :paper:test
./gradlew :paper-authority-bridge:test
./gradlew :velocity:test
./gradlew :staff-bot:test
./gradlew :integration-tests:test
```

For Discord/web changes, run the tests closest to the actual trust boundary rather than only a renderer/parser test.

## Runtime artifacts

The root `runtimeJars` task currently builds/verifies four deployable Java runtimes:

```text
paper/build/libs/EnthusiaStaff-Paper-<version>.jar
paper-authority-bridge/build/libs/EnthusiaStaff-AuthorityBridge-<version>.jar
velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar
staff-bot/build/libs/EnthusiaStaff-StaffBot-<version>.jar
```

The authority bridge is a narrow transition runtime and has explicit required/forbidden class verification. StaffBot is a standalone Java/JDA application, not a Minecraft plugin.

Inspect deployables for:

- intended entry points/resources only;
- no provider-owned API duplication;
- no private jars, secrets or local configuration;
- no test fixtures/server runtime directories/databases/logs/generated reports;
- correct plugin/application metadata;
- expected migration/resource content where that runtime owns it.

A clean artifact scanner is not a substitute for representative provider/classloader/runtime testing.

## StaffBot build and smoke test

Build just StaffBot:

```bash
./gradlew --no-daemon :staff-bot:shadowJar
```

Run its non-network smoke test:

```bash
java -jar staff-bot/build/libs/EnthusiaStaff-StaffBot-<version>.jar --smoke-test
```

The smoke test can prove basic packaged startup/configuration wiring. It does not prove Discord authentication, guild/channel fencing, MariaDB, private authority/read connectivity, rate limits, reconnect behavior or destructive enforcement.

See [[Staff Bot Runtime and Operations]].

## Moderation web validation

The staging browser moderation workspace has its own Node/Cloudflare validation:

```bash
cd moderation-web
npm install --no-package-lock --ignore-scripts
npm run check
```

The protected staging deployment additionally exercises signed first-use launches, unauthenticated rejection, browser session establishment, direct-read authorization/CORS and replay rejection.

That evidence supports the recorded staging **read/simulation workspace**. It is not proof of production website launch or destructive moderation authority. See [[Website and Web API]].

## Public site/component validation

`components/enthusia-site/` is a synchronized site component with its own frontend/functions/tests and component-parity evidence. When changing aggregate component content, preserve the repository’s component synchronization rules; do not assume an aggregate-only change is automatically reflected in the standalone source.

Site/API testing should cover public allowlists, authenticated appeal/reviewer routes, body/rate/session boundaries, privacy and the exact server-side API revision being consumed.

## What each evidence layer proves

| Evidence | It can support claims about... | It cannot establish by itself... |
| --- | --- | --- |
| Unit tests | pure policy, parsing, authorization predicates, deterministic transitions | real JDBC, scheduler, Discord, provider, browser or network behavior |
| Module/component tests | one adapter/service with controlled collaborators | representative distributed runtime behavior |
| MariaDB/Testcontainers | SQL, constraints, transactions, migrations, concurrency/restart scenarios exercised | production volume/latency or arbitrary process-kill timing |
| Concurrency/failure injection | the races/failures actually simulated | every real scheduler/network/process/external-API race |
| Runtime-JAR checks | expected deployables, archive integrity, checked provider leakage | real provider/classloader/API compatibility |
| StaffBot smoke test | packaged non-network application startup/wiring | Discord/MariaDB/authority/API behavior |
| Static analysis | issues detectable by configured analyzers | behavioral correctness or absence of all security defects |
| Coverage | code executed by measured tests | assertion quality, scenario completeness or staging correctness |
| Wiki validation | Wiki structure/internal-link/format rules | factual truth of product claims |
| Private Paper boot/restart | exact Paper/bridge scenario recorded by that gate | Velocity, StaffBot, Bedrock, Folia, all providers or production readiness |
| StaffBot/Discord staging | exact recorded gateway/API/effect behavior | broader production authority or untested Discord states |
| Moderation-web staging | exact signed-launch/session/read workspace behavior | destructive moderation or public-site acceptance |
| Distributed Java/Bedrock/provider staging | behavior exercised in the recorded topology | untested load/data/cutover or later revisions |
| Production acceptance | explicitly accepted production claim for one pinned artifact/config set | future code/config changes |

A passing unit test is not staging evidence. A skipped/unavailable runtime workflow is not a pass.

## MariaDB and migrations

Persistence changes should exercise the applicable combination of clean schema creation, relevant upgrades, constraints/indexes, transaction rollback, idempotent replay, revisions, leases/fences, restart recovery, duplicate/out-of-order delivery and concurrent runtimes.

Current merged `main` includes Flyway migrations through **`V20__discord_account_linking.sql`**.

Recent milestones:

- V17 — website appeal workflow;
- V18 — Cheat Tester session journal;
- V19 — Discord moderation persistence;
- V20 — Discord account linking.

Applied migrations are immutable history. Do not use Flyway repair/history rewrites as a normal development shortcut.

## Paper / Leaf / Folia validation

Runtime acceptance is still required for claims involving region/entity ownership, reconnect callbacks, inventory/Ender mutation, staff restoration, vanish/tab/packet behavior, freeze bypasses, async teleport/follow/spectate, disable/restart or supported Paper/Leaf/Folia versions.

A standalone Paper boot does not prove Folia correctness.

## Velocity and distributed validation

Representative validation should cover non-blocking login/server-switch events, startup/shutdown ordering, backend reconnect/replacement sessions, no-player transport, durable ACK/outbox/inbox behavior, partial outages, backpressure/retry bounds, network identity ordering and multi-backend authority/degradation.

Website API changes also require authentication/replay/bounds/privacy tests for the actual Velocity route boundary.

## Discord/StaffBot validation

For privileged Discord changes test, as applicable:

- staging/production application/guild/channel identity fences;
- gateway reconnect/rate-limit behavior;
- linked-staff actor resolution;
- command discovery versus action-time authorization;
- signed component expiry/replay;
- worker saturation/shutdown;
- database/private-authority/read-service loss;
- Discord hierarchy/precondition changes during confirmation;
- ambiguous external effects and reconciliation;
- native ban and bot-owned role state changes outside the bot;
- account-link expiry/replay/reassignment/restart behavior;
- enforcement-disabled safe default.

A JDA mock/unit test is not proof of Discord’s real hierarchy, audit-log pagination, rate limits, reconnects or production guild behavior.

## Java and Bedrock validation

Automated identity tests should cover verified Java/Bedrock, `UNKNOWN`, missing/incompatible Floodgate, aliases/history, duplicate observations and out-of-order proxy/backend updates.

Representative Geyser/Floodgate staging must still verify login/reconnect, `*` alias handling without treating it as platform proof, UI/text fallbacks, packet/tab/visibility, server switching and provider failure.

## Provider/integration validation

For each optional provider test present-compatible, missing, incompatible/unavailable, failure during use, and restart/reload boundaries where applicable.

Verify unrelated features remain available when safe, dependent actions fail clearly, external effects are idempotent/verified, and provider-owned classes are not shaded into EnthusiaStaff.

The same principle applies to `discord-platform-api`: a contract compiling does not prove a StaffBot provider or every consumer migration exists.

## Security/privacy validation

For Discord/web/API changes explicitly review:

- tokens/credentials/signing material never appear in source/logs/artifacts;
- private endpoints are not made public for convenience;
- replay windows/nonces/body signatures are actually enforced;
- public projections are allowlisted;
- browser/Discord roles are not treated as standalone authority;
- private evidence, account links, raw network identity and staff notes cannot leak into public output;
- rate/body/queue bounds happen before expensive or privileged work;
- error responses/logs do not echo sensitive request bodies.

## Full staging record

For a staging claim record the exact source/artifact hashes, configuration versions/checksums, runtime/provider versions, topology/accounts/data scope, steps/expected outcomes, restart/reconnect observations, sanitized logs/evidence, unresolved mismatches and rollback result.

A source/migration/config/provider-contract change invalidates affected evidence until rerun.

## Wiki validation

Run:

```bash
python scripts/wiki/validate_wiki.py
```

Before publishing also verify new pages are reachable, internal/source links are valid, headings/sidebar are readable, no secrets/private evidence are copied, no unmerged feature is described as available, reviewer findings are resolved, active PR overlap is rechecked, the branch is synchronized with newest legitimate `main`, and the final diff is documentation-only.

[[Wiki Maintenance]] owns the full merge/publish procedure.

## Related pages

- [[Code Review Guide]]
- [[Developer Guide Index]]
- [[Architecture]]
- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Website and Web API]]
- [[Implementation Status]]
- [[Wiki Maintenance]]