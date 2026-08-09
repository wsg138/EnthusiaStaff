# Code Review Guide

Use this page when reviewing a change to EnthusiaStaff. It is a review checklist and evidence guide, not a replacement for [[Developer Code Guide]] or [[Architecture]].

Start with the behavior the change claims to alter, identify the owning domain boundary, then follow the write or read path through persistence, Paper/Velocity adapters, integrations, and tests. A green unit test proves only the behavior exercised by that test; it does not prove a real Paper, Velocity, provider, Java/Bedrock, multi-backend, or production workflow.

## Fast review path

1. Read the PR description and changed files. Separate product behavior from tests, migrations, configuration, documentation, and orchestration records.
2. Read the relevant goal in [`ENTHUSIASTAFF-GOALS.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md).
3. Check [[Implementation Status]] for the merged product state. Treat unmerged PR behavior as development-only.
4. Find the feature trace in [[Developer Code Guide]] and its higher-level boundary in [[Architecture]].
5. Identify the domain service or policy that owns authorization and business rules. Commands, GUIs, listeners, HTTP handlers, and provider adapters should translate requests rather than duplicate policy.
6. Identify durable state: the port, JDBC store, transaction, tables, constraints, migration, leases/revisions, and recovery state.
7. Identify runtime ownership: Paper scheduler/entity owner, Velocity event/executor ownership, network worker, provider callback, or website boundary.
8. Review failure paths as carefully as success: stale state, duplicate delivery, timeout after commit, restart, disconnect, partial outage, queue saturation, and ambiguous external results.
9. Match every important claim to the strongest available evidence described in [What each validation layer proves](#what-each-validation-layer-proves).
10. State what remains unproved. Do not promote `TESTED` behavior to staging-verified or production-ready without the required runtime evidence.

## Architecture and module boundaries

EnthusiaStaff follows clean/hexagonal dependency direction. Shared policy must not import Paper, Velocity, JDBC, Discord, or website-framework implementation types.

Review for:

- domain policy in `domain/`, not copied into commands, GUIs, listeners, website routes, or provider adapters;
- platform glue in `paper/` and `velocity/` delegating into stable application services and ports;
- persistence implementing domain ports without leaking JDBC types into domain policy;
- `integration-contracts/` containing supported compile-time contracts rather than copied provider internals;
- no cyclic module/package dependencies or new cross-module shortcut that bypasses the owning service;
- one authoritative implementation for hierarchy, escalation, sanction mutation, report state, staff sessions, inventory safety, and other business rules.

Primary references:

- [[Architecture]]
- [[Developer Code Guide]]
- [`docs/architecture.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/architecture.md)
- [`settings.gradle.kts`](https://github.com/wsg138/EnthusiaStaff/blob/main/settings.gradle.kts)

A useful review question is: **if this rule changes later, is there exactly one place that must change?**

## Paper, Leaf, and Folia ownership

Paper code may receive work on a Bukkit/Paper callback, a global scheduler, an entity scheduler, or a bounded worker. Those contexts are not interchangeable.

Check that:

- JDBC, HTTP, filesystem, socket, and other blocking work never runs on the game/entity thread;
- player/entity reads and mutations return to the owning entity or supported scheduler before touching live state;
- global scheduling is used only for operations that are actually global-safe;
- callbacks from bounded workers are session/revision fenced so a disconnect and reconnect cannot mutate a retired `Player` handle;
- asynchronous teleport/location sampling and staff tools recheck actor/target state before final mutation;
- plugin disable stops intake, cancels owned tasks, and does not leave callbacks capable of mutating closed runtime state;
- staff-mode, freeze, vanish, inventory, GUI, and restoration code preserves the exact player-state safety contract.

Important paths:

- [`paper/src/main/java/net/enthusia/staff/paper/`](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper)
- [`PaperRuntimeLifecycle.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeLifecycle.java)
- [`PaperRuntimeComponents.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java)
- [`PaperResourceCloser.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperResourceCloser.java)
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Vanish Internals]]
- [[Inventory and Confiscation Safety]]

Mock/unit scheduler tests can catch many mistakes. They do **not** prove Folia region ownership on a real compatible server.

## Velocity runtime ownership

Velocity event threads must stay non-blocking. Review bootstrap, publication, reload, worker, and shutdown order as a lifecycle rather than isolated methods.

Check that:

- login/server-switch callbacks do not wait on JDBC, HTTP, filesystem, or socket work;
- persistent workers are bounded and owned by a runtime lifecycle that can stop them cleanly;
- a reload candidate is fully validated before publication and failed publication leaves the prior valid state active;
- startup failure closes resources already opened and does not publish a half-created runtime;
- shutdown stops intake before closing dependent workers/connections;
- backend availability and network enforcement fail closed when required authority cannot be proved;
- player/server presence updates cannot overwrite newer observations after reconnect or server-switch races.

Important paths:

- [`velocity/src/main/java/net/enthusia/staff/velocity/`](https://github.com/wsg138/EnthusiaStaff/tree/main/velocity/src/main/java/net/enthusia/staff/velocity)
- [`EnthusiaStaffVelocityPlugin.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java)
- [`NetworkOutboxWorker.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/NetworkOutboxWorker.java)
- [`DiscordOutboxWorker.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/DiscordOutboxWorker.java)
- [[Protocol and Network Traffic]]

Do not document an active PR's bootstrap/reload work as merged behavior until it reaches `main`.

## MariaDB and persistence

For every durable operation, find the transaction boundary rather than reviewing SQL statements individually.

Check:

- authorization/revision checks that must be atomic with mutation happen inside the locked transaction;
- combined writes commit or roll back together;
- unique constraints and idempotency keys prevent duplicate effects;
- optimistic revisions reject stale writes instead of overwriting newer state;
- leases use owner/fence checks for claim, renewal, transition, and release;
- a failed durable release cannot silently make memory and disk disagree;
- timeout-after-commit paths can discover/replay the existing result;
- ambiguous provider/file outcomes enter recovery/quarantine instead of blind retry;
- queries, batches, caches, executors, and cleanup loops are bounded;
- expected indexes exist for hot lookup/claim paths;
- every `Connection`, `PreparedStatement`, `ResultSet`, stream, and transaction scope is closed correctly;
- retry policy distinguishes transaction/deadlock retry from non-idempotent external side effects.

Primary references:

- [`persistence/src/main/java/net/enthusia/staff/persistence/`](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence)
- [`MariaDb.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDb.java)
- [`MariaDbRuntime.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDbRuntime.java)
- [`persistence/src/main/resources/db/migration/`](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/resources/db/migration)
- [`integration-tests/src/test/java/`](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-tests/src/test/java)

## Flyway migrations

Flyway history is forward-only. Existing applied migrations are immutable.

Check that:

- a schema change adds a new migration rather than editing an earlier one;
- version ordering is monotonic and the migration filename is unambiguous;
- upgrade tests cover the relevant previous schema, not only clean install;
- constraints/indexes encode important invariants where appropriate;
- migration SQL is safe for existing data and bounded operational expectations;
- restart after migration does not rerun or reinterpret committed state;
- repair/history rewrite is not introduced as a convenience workaround.

Current merged `main` includes migrations through `V17__website_appeal_workflow.sql`. Later migrations must preserve V1-V17 bytes.

## Distributed Paper/Velocity behavior

The transport is at-least-once. Correctness comes from authentication, durable inbox/outbox state, idempotent handlers, acknowledgement semantics, and recovery.

Review:

- server identity allowlists, HMAC/TLS ownership, message size/version validation, and timestamp/nonce replay windows;
- acknowledgement only after the receiver has durably accepted the outcome that ACK represents;
- duplicate message delivery before and after restart;
- reconnect and backend outage behavior;
- stale server/player presence;
- one backend succeeding while another is unavailable;
- outbox/inbox claim fencing and redelivery;
- partial outage behavior that blocks only operations whose safety cannot be proved;
- queue/backpressure limits and dead-letter/manual-recovery behavior.

Go deeper: [[Protocol and Network Traffic]].

## Moderation authority

Permission visibility is not the final authority boundary. Important writes must be reauthorized inside central application services and, where needed, again inside the locked persistence transaction.

Verify:

- Helper, Mod, Developer, Admin, Founder, console, and `SYSTEM` semantics separately;
- Developer remains request-only for punishment decisions;
- self-target/self-approval restrictions;
- target and issuing-rank hierarchy;
- higher-rank/system-issued sanction protections;
- explicit bypass permissions remain narrowly scoped and do not imply unrestricted `SYSTEM` authority;
- actor authority is rechecked after asynchronous work before commit;
- operational-mode/authority fences fail closed;
- command, GUI, website, automation, and integration entry points all converge on the same policy.

References:

- [[Roles and Permissions|Rank-Authority]]
- [[Moderation, Punishments, and Reports]]
- [`domain/src/main/java/net/enthusia/staff/domain/auth/`](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/auth)
- [`domain/src/main/java/net/enthusia/staff/domain/application/`](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/application)

## Player-state safety

Treat inventory, Ender chest, confiscation, staff mode, freeze, vanish, teleport, follow, and spectate changes as destructive or privacy-sensitive state transitions.

Review for:

- durable before snapshots before temporary/destructive mutation;
- exact revision/fingerprint checks before applying a previously planned change;
- online owner versus offline owner rules;
- concurrent viewer/editor coordination;
- nested containers and partial-slot mutation correctness;
- disconnect/server-switch/restart behavior;
- idempotent restoration and no item duplication/loss;
- staff-item leakage prevention;
- vanish audience/session fencing and provider visibility gaps;
- freeze bypasses through movement, inventory, teleport, backend switch, commands, damage, and interactions;
- failed restore paths preserving the original recovery evidence instead of replacing it.

References: [[Staff Tools, Investigations, and Player-State Safety]], [[Inventory and Confiscation Safety]], [[Vanish Internals]], and [[Recovery and Troubleshooting]].

## Java and Bedrock

Do not infer platform from username text. Bedrock identity requires supported provider evidence; `*` aliases are lookup compatibility, not identity proof.

Check that:

- UUID is authoritative;
- verified Floodgate evidence can establish Java/Bedrock while missing/incompatible evidence remains `UNKNOWN`;
- unverified Velocity observations cannot downgrade a verified platform record;
- historical/current `*` aliases remain resolvable without becoming platform proof;
- duplicate/out-of-order join, disconnect, and proxy observations cannot overwrite newer identity/presence state;
- user interfaces have a text/command path when Java click/hover/inventory assumptions are not portable;
- Geyser/Floodgate missing or incompatible behavior degrades explicitly;
- tests distinguish Java, Bedrock, unknown, provider missing, contradictory, reconnect, and historical-name cases.

Representative Java/Bedrock behavior still requires real Geyser/Floodgate staging even when identity unit/integration tests pass.

## Integrations and provider contracts

Provider plugins remain authoritative for their own data and behavior.

Check that:

- provider presence/missing/incompatible states are explicit;
- one missing provider disables only dependent features where safe;
- EnthusiaStaff calls a supported public contract rather than raw provider SQL, reflection into internals, or command dispatch as a transaction API;
- timeouts/retries are bounded and idempotency is preserved;
- external outcomes are verified before terminal success;
- provider API classes are not accidentally shaded into runtime JARs;
- no API is invented from assumptions about another repository;
- reload/shutdown does not retain stale provider handles.

See [[Integrations]] and [[Integrations, Migration, and Release Readiness]].

## Security and privacy

Review the data boundary, not just obvious credential files.

Check for:

- secrets, tokens, passwords, TLS material, webhook URLs, or production credentials in source/config/tests/logging;
- raw network addresses where protected tokens or restricted encrypted storage are required;
- reporter identity, private messages, coordinates, internal notes, confiscation detail, alt evidence, or appeal media in public/Discord/site projections;
- bearer/HMAC/signature verification before privileged website handling;
- nonce/timestamp replay windows and body-size bounds;
- safe authentication/session/role boundaries on website routes;
- logs/exceptions that could leak request bodies or secrets;
- fixed private/loopback API exposure assumptions remaining true in deployment documentation.

References: [[Privacy and Data Handling]], [[Protocol and Network Traffic]], [[Architecture]].

## What each validation layer proves

| Evidence | Good for proving | Does not prove by itself |
| --- | --- | --- |
| Unit tests | Pure policy, parsing, state transitions, authorization predicates | Real scheduler, JDBC, provider, network, classloader, client behavior |
| Module/component tests | Adapter logic with controlled collaborators | Real distributed topology or production dependencies |
| MariaDB/Testcontainers | SQL, constraints, transactions, migrations, concurrency and restart scenarios exercised by the test | Production volume/latency, process kill at arbitrary instruction boundaries |
| Concurrency/failure-injection tests | Races and failures explicitly simulated | Every scheduler/network/process race in a real runtime |
| Runtime-JAR/provider-leak checks | Expected deployables, artifact integrity, absence of copied provider API types checked by the scanner | Provider service discovery/classloader compatibility at runtime |
| Static analysis | Detected code-quality/security patterns for configured analyzers | Behavioral correctness or absence of all defects |
| Coverage | Which code was executed by the measured tests | Correct assertions, important scenario coverage, staging correctness |
| Wiki validation | Wiki source/link/format rules | Technical truth of the documented behavior |
| Private Paper boot/restart staging | The exact artifact can start/restart in that recorded Paper environment | Velocity, multi-backend, all providers, Bedrock, Folia, production readiness |
| Distributed Java/Bedrock/provider staging | The exact artifact/config works in the representative tested topology | Untested production load/data/cutover conditions |
| Production acceptance/cutover evidence | The explicitly accepted production gate and observation window | Future revisions after the accepted artifact/config changes |

Never combine successful evidence from different commits into one exact-head claim. An unavailable runner or skipped staging job is **not** a staging pass.

## Review by changed area

| Changed area | Minimum focused review |
| --- | --- |
| `domain/` | policy ownership, authorization, invariants, pure tests, no platform imports |
| `persistence/` | transaction/constraint/revision/lease/resource safety plus MariaDB tests |
| migration SQL | immutability, forward upgrade, constraints/indexes, clean + upgrade tests |
| `protocol/` | auth/replay/ACK/idempotency/backpressure/reconnect |
| `paper/` | scheduler ownership, lifecycle, player-state safety, Bedrock fallback |
| `velocity/` | event-thread nonblocking, bootstrap/shutdown, network identity, workers |
| integration adapter | supported contract, absence/degradation, timeout/retry/classloader behavior |
| website/component | authentication, authorization, privacy projection, replay/rate/body bounds, parity when mirrored |
| Wiki/docs | merged-behavior accuracy, progressive disclosure, links, no secrets, `python scripts/wiki/validate_wiki.py` |

## Before approving

Confirm all of these that apply:

- the PR head you reviewed is the head that passed the required checks;
- valid review threads are resolved rather than merely hidden;
- migrations and runtime configuration changes are intentional;
- no unmerged behavior is documented as available;
- tests prove the claims they actually exercise and remaining staging is named;
- privacy/security boundaries remain conservative;
- restart, duplicate, stale-state, partial-outage, and recovery behavior were considered;
- documentation and source paths were updated when ownership or behavior changed.

## Go deeper

- [[Developer Guide Index]] — choose the next developer/reviewer document.
- [[Developer Code Guide]] — detailed source map and feature traces.
- [[Architecture]] — module and runtime ownership.
- [[Build and Testing]] — exact validation commands and evidence interpretation.
- [[Protocol and Network Traffic]] — Paper/Velocity transport.
- [[Vanish Internals]] — focused scheduler/visibility deep dive.
- [[Recovery and Troubleshooting]] — runtime failure and recovery model.
- [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md) — conservative requirement/evidence ledger; reconcile it with current merged code and live GitHub when it has not yet been updated for a recent merge.