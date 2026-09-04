# Code Review Guide

Use this page when reviewing an EnthusiaStaff change. It is a review checklist and evidence guide, not a replacement for [[Developer Code Guide]] or [[Architecture]].

Start with the behavior the change claims to alter, identify the owning domain boundary, then follow the read/write path through persistence, Paper/Velocity/runtime adapters, integrations and tests. A green unit test proves only the behavior exercised by that test; it does not prove a real Paper, Velocity, provider, Java/Bedrock, multi-backend, Discord, or production workflow.

## Fast review path

1. Read the PR description and changed files. Separate product behavior from tests, migrations, configuration, documentation and orchestration records.
2. Read the relevant finished-behavior requirement in [`ENTHUSIASTAFF-GOALS.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md).
3. Check [[Implementation Status]] for merged product state. Treat unmerged PR behavior as development-only.
4. Find the feature trace in [[Developer Code Guide]] and its higher-level boundary in [[Architecture]].
5. Identify the domain service/policy that owns authorization and business rules. Commands, GUIs, listeners, HTTP/Discord handlers and provider adapters should translate requests rather than duplicate policy.
6. Identify durable state: port, JDBC store, transaction, tables, constraints, migration, leases/revisions and recovery state.
7. Identify runtime ownership: Paper entity/global/region scheduler, Velocity event/executor ownership, network worker, provider callback, website boundary or future Discord runtime.
8. Review failure paths as carefully as success: stale state, duplicate delivery, timeout after commit, restart, disconnect, partial outage, queue saturation and ambiguous external results.
9. Match every important claim to the strongest available evidence in [What each validation layer proves](#what-each-validation-layer-proves).
10. State what remains unproved. Never promote `TESTED` behavior to staging-verified or production-ready without the required runtime evidence.

## Architecture and boundaries

EnthusiaStaff follows clean/hexagonal dependency direction. Shared policy must not import Paper, Velocity, JDBC, Discord SDK, or website-framework implementation types.

Review for:

- domain policy in `domain/`, not copied into commands, GUIs, listeners, website/Discord routes, or provider adapters;
- Paper/Velocity/runtime glue delegating into stable application services and ports;
- persistence implementing domain ports without leaking JDBC concepts into policy;
- `integration-contracts/` containing supported compile-time contracts rather than copied provider internals;
- no cyclic dependency or cross-module shortcut around the owning service;
- one authoritative implementation for hierarchy, escalation, sanction mutation, report state, staff sessions, inventory safety, Discord authorization and other business rules;
- provider/runtime adapters owning side effects, not deciding policy that belongs in the domain.

Primary references: [[Architecture]], [[Developer Code Guide]], [`docs/architecture.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/architecture.md), and [`settings.gradle.kts`](https://github.com/wsg138/EnthusiaStaff/blob/main/settings.gradle.kts).

A useful question is: **if this rule changes later, is there exactly one authoritative place that must change?**

## Paper / Leaf / Folia

Paper code may receive work on a Bukkit/Paper callback, global scheduler, entity scheduler, region scheduler, or bounded worker. Those contexts are not interchangeable.

Check that:

- JDBC, HTTP, filesystem, socket and other blocking work never runs on the game/entity thread;
- live player/entity reads and mutations run on the supported owning scheduler;
- global scheduling is used only for genuinely global-safe work;
- asynchronous location/target sampling returns to the correct owner before mutation;
- callbacks from workers are session/revision fenced so disconnect/reconnect cannot mutate a retired `Player` handle;
- plugin disable stops intake, retires sessions/callbacks and closes resources in a safe order;
- staff mode, Cheat Tester, freeze, vanish, inventory, teleport/follow/spectate and restoration preserve their exact player-state contract;
- a failed scheduler handoff refuses safely instead of doing the mutation on the wrong thread.

Important references:

- `paper/src/main/java/net/enthusia/staff/paper/`
- `PaperRuntimeLifecycle.java`, `PaperRuntimeComponents.java`, `PaperResourceCloser.java`
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Cheat Tester]]
- [[Vanish Internals]]
- [[Inventory and Confiscation Safety]]

Mock/unit scheduler tests can catch many defects. They do **not** prove real Folia region/entity ownership.

## Velocity

Velocity event threads must remain non-blocking. Review bootstrap, publication, reload, worker and shutdown order as one lifecycle.

Check that:

- login/server-switch callbacks do not wait on JDBC, HTTP, filesystem or sockets;
- persistent workers are bounded and have explicit ownership/shutdown;
- a reload candidate is fully validated before publication and failure leaves the prior valid state active;
- startup failure closes already-opened resources and does not publish a half-runtime;
- shutdown stops intake before closing dependencies used by in-flight tasks;
- backend availability and network enforcement fail closed when authority/delivery cannot be proved;
- player/server presence updates cannot overwrite a newer observation after reconnect or server-switch races;
- old callbacks/workers cannot publish into a replacement runtime after reload/shutdown.

Important references:

- `velocity/src/main/java/net/enthusia/staff/velocity/`
- `EnthusiaStaffVelocityPlugin.java`
- `NetworkOutboxWorker.java`
- `DiscordOutboxWorker.java`
- [[Protocol and Network Traffic]]

Do not describe a runtime that exists only on an open PR as merged behavior.

## MariaDB and persistence

For every durable operation, find the transaction boundary rather than reviewing SQL statements individually.

Check:

- authorization/revision checks that must be atomic with mutation occur inside the locked transaction;
- combined state/audit/outbox writes commit or roll back together where required;
- unique constraints and idempotency keys prevent duplicate effects;
- optimistic revisions reject stale writes;
- leases use owner/fence checks for claim, renewal, transition and release;
- timeout-after-commit can discover/replay the existing result;
- ambiguous provider/file/player-state outcomes preserve recovery/quarantine evidence rather than blind-retrying;
- queries, batches, caches, executors and cleanup loops are bounded;
- hot lookup/claim paths have appropriate indexes and constraints;
- JDBC resources (`Connection`, `PreparedStatement`, `ResultSet`, streams) are closed on success and failure;
- retry policy distinguishes DB transaction/deadlock retry from non-idempotent external effects;
- process restart can recover claimed/in-flight work without inventing a second owner.

Primary references:

- `persistence/src/main/java/net/enthusia/staff/persistence/`
- `MariaDb.java`, `MariaDbRuntime.java`
- `persistence/src/main/resources/db/migration/`
- `integration-tests/src/test/java/`

## Flyway migrations

Flyway history is forward-only. Applied migrations are immutable.

Current merged `main` ends at **`V19__discord_moderation_persistence.sql`**. V18 owns the Cheat Tester session journal; V19 owns the current Discord moderation persistence foundation.

Check that:

- the author reconciled the live migration ceiling immediately before choosing a version;
- a schema change adds a forward migration instead of editing an earlier one;
- V1–V19 remain byte-identical unless there is an extraordinary, explicitly approved recovery reason outside normal development;
- version ordering and filename are unambiguous;
- clean-install **and relevant upgrade** tests run;
- constraints/indexes encode important invariants where practical;
- migration SQL handles existing data deliberately and does not silently invent missing facts;
- restart after migration does not rerun/reinterpret committed state;
- Flyway repair/history rewrite is not used as a convenience workaround.

A clean database passing is not enough when the risk is upgrading an existing schema.

## Distributed Paper / Velocity behavior

The transport is at-least-once. Correctness comes from authentication, durable inbox/outbox state, idempotent handlers, acknowledgement semantics and recovery.

Review:

- server identity allowlists, TLS/HMAC ownership, message-size/version checks and timestamp/nonce replay windows;
- acknowledgement only after the durable outcome represented by the ACK is accepted;
- duplicate delivery before and after restart;
- reconnect/backend outage and stale-session behavior;
- one backend succeeding while another is unavailable;
- stale player/server presence;
- outbox/inbox lease fencing and redelivery;
- bounded queue/backpressure/retry/dead-letter behavior;
- no reliance on an online player as network transport;
- partial outages blocking only operations whose safety cannot be proved.

Go deeper: [[Protocol and Network Traffic]].

## Discord moderation and distributed external effects

Do not confuse the merged Discord domain/schema/authorization foundation with a live bot or completed Discord side effect.

For changes in this area verify:

- moderation subject identity and enforcement scope remain explicit and platform-matched;
- one Discord account / multiple Minecraft links and one-current-Discord-owner-per-Minecraft constraints remain intact;
- link history is append/history-preserving rather than rewritten away;
- Discord snowflake values are handled as the full unsigned range expected by the domain/schema;
- actor and target staff state are resolved authoritatively before authorization;
- Discord-origin authorization returns explicit allowed consequences/preconditions instead of trusting command visibility or a Discord role;
- Developer's Discord-only temporary authority cannot leak into Minecraft authorization;
- self/equal-higher/protected-target rules fail closed;
- stale confirmation flows reauthorize immediately before a side effect;
- external Discord hierarchy/role preconditions are checked at the execution boundary;
- reconnect/retry/reconciliation is idempotent and can recover a Discord-success/DB-timeout or DB-success/Discord-failure split;
- an open PR's bot/link/enforcement behavior is not documented as merged.

Primary references: [[Discord Moderation Platform]], [[Roles and Permissions|Rank-Authority]], `domain/.../moderation/`, `domain/.../auth/Discord*`, V19/JDBC Discord persistence, and `docs/discord-authorization.md`.

## Moderation authority

Permission visibility is not final authority. Important writes must be reauthorized in central application services and, where required, again in the locked persistence transaction.

Verify:

- Helper, Mod, Developer, Admin, Founder, console and `SYSTEM` semantics separately;
- self-target/self-approval restrictions;
- target and issuing-rank hierarchy;
- higher-rank/system-issued sanction protections;
- bypass permissions are narrow and do not imply unrestricted system authority;
- actor authority is rechecked after asynchronous work before commit;
- operational/authority-mode fencing fails closed;
- command, GUI, website, Discord and integration entry points converge on the same policy;
- cross-platform consequences are authorized independently rather than through a magic `BOTH` shortcut.

References: [[Roles and Permissions|Rank-Authority]], [[Moderation, Punishments, and Reports]], and `domain/src/main/java/net/enthusia/staff/domain/auth/`.

## Player safety

Treat inventory, Ender chest, confiscation, staff mode, Cheat Tester, freeze, vanish, teleport, follow and spectate as destructive or privacy-sensitive transitions.

Review for:

- durable before snapshots before temporary/destructive mutation;
- exact revision/checksum/fingerprint checks before applying a previously planned change;
- online owner versus offline owner rules;
- concurrent viewer/editor/operation coordination;
- nested containers and partial-slot mutation correctness;
- disconnect/server-switch/restart behavior;
- idempotent restoration with no item duplication/loss;
- staff-item leakage prevention;
- Cheat Tester cleanup/restore and fake-entity/fake-base lifecycle;
- vanish audience/session fencing and provider visibility gaps;
- freeze bypasses through movement, inventory, teleport, backend switch, commands, damage and interactions;
- failed restore paths preserving original recovery evidence instead of replacing it.

References: [[Staff Tools, Investigations, and Player-State Safety]], [[Cheat Tester]], [[Inventory and Confiscation Safety]], [[Vanish Internals]], and [[Recovery and Troubleshooting]].

## Java and Bedrock

Do not infer platform from username text. Bedrock identity requires supported provider evidence; `*` aliases are lookup compatibility, not identity proof.

Check that:

- UUID is authoritative;
- verified Floodgate evidence can establish Java/Bedrock while missing/incompatible evidence remains `UNKNOWN`;
- unverified Velocity observations cannot downgrade a verified platform record;
- historical/current aliases remain resolvable without becoming platform proof;
- duplicate/out-of-order join/disconnect/proxy observations cannot overwrite newer state;
- UI has a text/command fallback when Java click/hover/inventory assumptions are not portable;
- Geyser/Floodgate missing or incompatible behavior degrades explicitly;
- tester, freeze, vanish, reports and other player-facing paths are reviewed for both client families rather than assuming Java behavior.

Representative Java/Bedrock behavior still requires real Geyser/Floodgate staging even when identity unit/integration tests pass.

## Integrations

Provider plugins remain authoritative for their own data and behavior.

Check that:

- provider present/missing/incompatible states are explicit;
- one missing provider disables only dependent behavior where safe;
- EnthusiaStaff uses a supported public contract, not raw provider SQL, reflection into internals, or command dispatch as a transaction protocol;
- timeouts/retries are bounded and idempotency survives ambiguity;
- external outcomes are verified before terminal success;
- provider API classes are not accidentally shaded into runtime JARs;
- no provider API is invented from assumptions or another repository's private implementation;
- reload/shutdown does not retain stale provider handles;
- optional provider failure cannot accidentally widen authority or expose vanished/private state.

See [[Integrations]] and [[Integrations, Migration, and Release Readiness]].

## Security and privacy

Review the data boundary, not only obvious credential files.

Check for:

- secrets, tokens, passwords, TLS material, webhook URLs, production credentials or private topology in source/config/tests/logs;
- raw network addresses where protected equality tokens/restricted encrypted storage are required;
- reporter identity, private messages, coordinates, internal notes, confiscation detail, alt/link evidence, appeal media or full snapshots in public/Discord/site projections;
- webhook rendering through explicit allowlisted projection rather than raw `payload_json`;
- bearer/HMAC/signature authentication before privileged website or external handling;
- nonce/timestamp replay windows and body/frame-size bounds;
- safe authentication/session/role boundaries on website and future Discord routes;
- logs/exceptions that could leak request bodies, credentials or sensitive evidence;
- public bot/API boundaries never exposing linked-account/private moderation data.

References: [[Privacy and Data Handling]], [[Discord Delivery]], [[Protocol and Network Traffic]], [[Architecture]].

## What each validation layer proves

| Evidence | Good for proving | Does not prove by itself |
| --- | --- | --- |
| Unit tests | Pure policy, parsing, state transitions, authorization predicates | Real scheduler, JDBC, provider, network, classloader, client behavior |
| Module/component tests | Adapter logic with controlled collaborators | Real distributed topology or production dependencies |
| MariaDB/Testcontainers | SQL, constraints, transactions, migrations, concurrency/restart cases exercised by the test | Production volume/latency or arbitrary process-kill timing |
| Concurrency/failure-injection tests | Races/failures explicitly simulated | Every scheduler/network/process race in a real runtime |
| Runtime-JAR/provider-leak checks | Expected deployables, artifact integrity, absence of copied provider API types checked by the scanner | Provider discovery/classloader compatibility at runtime |
| Static analysis | Findings covered by configured analyzers | Behavioral correctness or absence of all defects |
| Coverage | Which code was executed by measured tests | Correct assertions, important scenario coverage or staging correctness |
| Wiki validation | Wiki source/link/format rules | Technical truth of the documented behavior |
| Private Paper boot/restart staging | Exact artifact can start/restart in the recorded Paper environment | Velocity, multi-backend, all providers, Bedrock, Folia, Discord, production readiness |
| Distributed Java/Bedrock/provider staging | Exact artifact/config works in the representative tested topology | Untested production load/data/cutover conditions |
| Discord/provider staging | Exact candidate exercises the recorded external provider/API behavior and failure paths | Unobserved production authority/cutover behavior |
| Production acceptance/cutover evidence | The explicitly accepted production gate/observation for that exact revision/config | Future revisions after artifact/config changes |

Never combine successful evidence from different commits into one exact-head claim. A skipped, unavailable or zero-execution runtime job is **not** a staging pass unless an authoritative gate explicitly classifies that infrastructure condition separately.

## Review by changed area

| Changed area | Minimum focused review |
| --- | --- |
| `domain/` | policy ownership, authorization, invariants, pure tests, no platform imports |
| `persistence/` | transaction/constraint/revision/lease/resource safety plus MariaDB tests |
| migration SQL | live-version reconciliation, immutability, forward upgrade, constraints/indexes, clean + upgrade tests |
| `protocol/` | auth/replay/ACK/idempotency/backpressure/reconnect |
| `paper/` | scheduler ownership, lifecycle, player-state safety, Bedrock fallback |
| `velocity/` | event-thread nonblocking, bootstrap/shutdown, network identity and workers |
| Discord foundation/runtime | scope/identity, authorization, persistence, replay/reconciliation, external preconditions, privacy |
| integration adapter | supported contract, absence/degradation, timeout/retry/classloader behavior |
| website/component | authentication, authorization, privacy projection, replay/rate/body bounds, aggregate/standalone parity |
| Wiki/docs | merged-behavior accuracy, progressive disclosure, links, no secrets, `python scripts/wiki/validate_wiki.py` |

## Before approving

Confirm all that apply:

- the PR head reviewed is the exact head that passed the required checks;
- valid review findings are resolved rather than hidden;
- migrations/configuration changes are deliberate and current-main collision checks were done;
- no unmerged behavior is documented as available;
- tests prove only claims they actually exercise and remaining staging is stated;
- privacy/security boundaries remain conservative;
- restart, duplicate, stale-state, partial-outage and recovery behavior were reviewed;
- runtime/provider claims have matching exact-head evidence;
- documentation/source maps were updated when ownership or behavior changed.

## Go deeper

- [[Developer Guide Index]] — choose the next developer/reviewer document.
- [[Developer Code Guide]] — detailed source map and feature traces.
- [[Architecture]] — module/runtime ownership.
- [[Build and Testing]] — exact validation commands/evidence interpretation.
- [[Protocol and Network Traffic]] — Paper/Velocity transport.
- [[Discord Moderation Platform]] — Discord foundation/runtime boundary.
- [[Cheat Tester]] — player-state tester/fake-base deep dive.
- [[Vanish Internals]] — scheduler/visibility deep dive.
- [[Recovery and Troubleshooting]] — runtime failure/recovery model.
- [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md) — conservative requirement/evidence ledger; reconcile with current merged code/live GitHub when it trails a recent merge.