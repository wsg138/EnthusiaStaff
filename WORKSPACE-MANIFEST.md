# EnthusiaStaff workspace manifest

Last updated: 2026-07-31 (America/Indiana/Indianapolis)

This manifest records work on draft PR #27. It is exact-head evidence and handoff documentation, not a production-readiness claim.

## Repository state

- Repository: `wsg138/EnthusiaStaff`
- Primary branch: `main`
- Verified starting `main`: `b18fe55482d4c491acfebec8ad784c6690f63f20`
- Development branch: `section/punishment-request-notifications-recovery`
- Verified starting development head: `9d1cde3abe5ef7d19c2ea920880ed12b2590de96`
- Draft PR: #27 — `Add durable punishment request notifications and recovery`
- Normal `main` reconciliation merge: `a40f0ad8fb18311611d4e3bed76c56ee82e470bf`
- Latest validated code head: `82ccebacf5793b179a11e5f7f29f7d9692457e0e`
- PR #27 remains open, draft, unmerged, and mergeable.

The merge was produced through temporary PR #28 because this environment had connected GitHub write access but no local authenticated Git/`gh` checkout. PR #28 only merged `main` into the existing development branch. It did not merge or mark PR #27 ready.

## Hard boundaries

This work did not:

- modify `main` directly;
- force-push, rebase, squash, or rewrite branch history;
- merge PR #27, mark it ready, or enable auto-merge;
- modify `wsg138/EnthusiaStaff-Staging`;
- modify or administer `.github/workflows/pi-staging-check.yml`;
- inspect, rerun, cancel, or debug automatic Pi staging;
- access Lincoln-PI-4;
- access Bloom or production databases;
- access production Discord credentials;
- implement live Discord network sending;
- perform live server testing;
- change production configuration;
- claim exactly-once delivery or production readiness.

Pushes may automatically trigger the separately owned Pi staging workflow. This development work does not inspect or use that workflow as evidence.

## Preserved B1.3/B2 model

The branch preserves:

- MariaDB-authoritative punishment-request state;
- immutable logical intents in `staff_alerts`;
- recipient-specific durable state in `staff_alert_deliveries`;
- unique delivery identity `(alert_id, recipient_id)`;
- `PENDING`, `LEASED`, `DELIVERED`, `CANCELLED`, and `DEAD_LETTER` states;
- fenced acknowledgement, failure, and cancellation;
- bounded lease recovery and terminal-parent reconciliation;
- explicit dead-letter requeue and resolution;
- deterministic `pra:v2` intent identity;
- revision occurrence identity for non-repeatable events;
- lease-fence/reviewer identity for repeatable claim events;
- connection-scoped immutable alert persistence;
- atomic request transition, alert, delivery, lifecycle event, and Discord-outbox insertion;
- bounded request expiration using deterministic ordering and `FOR UPDATE SKIP LOCKED`;
- non-mutating request reads;
- rollback and concurrency coverage using MariaDB Testcontainers.

Delivery remains at-least-once. A crash after Bukkit presentation but before acknowledgement can produce a visible duplicate after lease recovery.

## Validation command

Every validated checkpoint used:

```bash
./gradlew clean build jacocoAggregateReport runtimeJars \
  --no-daemon \
  --no-build-cache \
  --no-configuration-cache \
  --console=plain
```

## Verified B2 evidence

Validated exact head:

`9d1cde3abe5ef7d19c2ea920880ed12b2590de96`

- Run: `30675287547`
- Job: `91301146989`
- Result: success
- Java: Temurin `21.0.11+10`
- Build: `BUILD SUCCESSFUL`
- Tasks: 49 actionable; 40 executed, 9 up-to-date
- Coverage: lines `34.39%`, branches `28.01%`, instructions `36.76%`
- Paper SHA-256: `0319150654b8c08e8c7dcacebf48fd49eec781647925ec6eb5546eefb15e837b`
- Velocity SHA-256: `bbdc61c54ac0696025421cc13adcb4a69f305cc32e58492e72ecddd747b245ac`
- Provider API source types/leaks: `24` / `0`
- Artifact: `8810248079`
- Artifact digest: `sha256:da3c423a81720c966edc87c1fa8e84bfd67c7542e706c790d222168d92de0196`
- Codacy coverage upload/final notification: success

The previously documented run `30675533640` and job `91301882084` could not be resolved and are not retained as evidence. No post-B2 manifest-only commit existed before this work.

## Main reconciliation

All nine newer `main` commits were inspected before merging. They covered:

- trusted automatic Pi staging workflow changes;
- hosted Java failure-evidence improvements;
- `/estaff` console and permission behavior;
- Paper database classloader discovery.

The staging workflow files were accepted from `main` without behavioral edits or staging administration.

The merged `MariaDb` source preserves both:

```java
static HikariConfig hikariConfig(DatabaseConfig database)
```

and:

```java
Flyway.configure(MariaDb.class.getClassLoader())
```

## Symmetric MariaDB deadlock recovery

The approval-vs-direct-punishment concurrency test exposed both possible MariaDB deadlock victims.

### First victim: direct punishment transaction

At merge head `a40f0ad8fb18311611d4e3bed76c56ee82e470bf`:

- Run: `30679869607`
- Job: `91314602904`
- Result: failure
- Failure: MariaDB error `1213`, SQLState `40001`
- Failed transaction: direct punishment creation
- Failure artifact: `8811850755`
- Digest: `sha256:37585b150c3bbeaddecfc3ccca8985ddd8fff4d0167a4359a343978c98fad720`

The public moderation-store boundary was given bounded deadlock retry behavior.

Initial correction commits:

- `a578a9423eacfe3537cf00e4d48490289ab18d49` — `Add bounded moderation deadlock retries`
- `c2b5ff77558060ce04017b5b241ce48bd99f2ac2` — `Classify MariaDB transaction deadlocks`
- `3fb73da75ac2fd172938030e33e719e62f6008fe` — `Use bounded moderation retries in MariaDB runtime`
- `5faf99021b394411a8737a15e5a9a2540fe3bc2b` — `Test bounded moderation deadlock retries`

Validated exact head `5faf99021b394411a8737a15e5a9a2540fe3bc2b`:

- Run/job: `30680060081` / `91315164090`
- Artifact: `8811922938`
- Digest: `sha256:479ef30818cece42b7357f60679a3de200d9dc77fee1cd1a9266a9d8967681e3`
- Paper SHA-256: `09a8eab143eefd9c56c49aa07a9fb4bd7aaeac804daa2135a591a2ed8c1a7e3c`
- Velocity SHA-256: `a2fe18813414f1a35cda9a74f3fdcee9de4e928acf6475a8d8c38f2176955a7a`
- Coverage: lines `34.74%`, branches `28.22%`, instructions `37.09%`
- Provider source types/leaks: `24` / `0`
- Result: success

### Second victim: request approval transaction

A later documentation-only head `d2521d8d261752787bd05c8375bca0ff0d7f96c0` reran the same unchanged concurrency suite and exposed the opposite victim:

- Run: `30680813580`
- Job: `91317308747`
- Result: failure
- Failure: MariaDB error `1213`, SQLState `40001`
- Failed transaction: `PunishmentRequestStore.approve(...)`
- Failed test: `PunishmentRequestLifecycleConcurrencyIntegrationTest.externalPunishmentRacingApprovalProducesOneRequestTerminalTransition()`

The retry policy is now centralized and applied symmetrically to complete rollback-safe request transactions: submit, acquire, approve, deny, and expiration. Read methods are not wrapped. Only error `1213` with SQLState `40001` is retried; lock timeouts and unrelated SQL failures still fail immediately. Retries permit at most three total attempts with bounded pauses and interruption handling.

Symmetric correction commits:

- `d72ae157e2d62973635c7f38d00a14c630bc2f79` — `Centralize bounded MariaDB deadlock retry policy`
- `05993dad4450540fb9dbfb5c853445cd4fe479fb` — `Use shared deadlock policy for moderation writes`
- `b20125c7acc4b5275c508ed9a3d9f20f9601f662` — `Retry punishment request deadlock victims`
- `62d1c78e3dd72169bc2ee5b4bfbe9fc21aa0414a` — `Apply deadlock retries to request transitions`
- `82ccebacf5793b179a11e5f7f29f7d9692457e0e` — `Test punishment request deadlock retries`

Focused tests prove retry coverage for every transactional request entry point, unrelated-failure passthrough, and exhaustion after three deadlocked attempts.

Validated exact head `82ccebacf5793b179a11e5f7f29f7d9692457e0e`:

- Run: `30681043078`
- Job: `91317978581`
- Result: success
- Java: Temurin `21.0.11+10`
- Build: `BUILD SUCCESSFUL in 2m 51s`
- Tasks: 49 actionable; 40 executed, 9 up-to-date
- MariaDB/Testcontainers integration suites: success
- Paper tests: success
- Paper runtime JAR count: `1`
- Velocity runtime JAR count: `1`
- ZIP integrity: success
- Paper SHA-256: `56c3562a02df437c82c8b952c330639b2670228c770a7b2343b99051e38aa661`
- Velocity SHA-256: `b17fcbc088222dd67382ea07569f0e40047ce62a2eefb62e4b0ae6ab19340798`
- Provider API source types/leaks: `24` / `0`
- Coverage: lines `34.79%`, branches `28.24%`, instructions `37.15%`
- Artifact: `8812248998`
- Artifact digest: `sha256:31586c9b6f27f06ec03fb4da4dcd6bf46b31311efc9b85f91bd641e32905d04a`
- Codacy coverage upload/final notification: success

## Paper bootstrap-thread correction

The prior plugin opened and migrated MariaDB asynchronously and then directly continued into Bukkit-facing recovery from the executor thread. The correction uses an explicit `PaperStorageBootstrapCoordinator`.

### Asynchronous storage phase

- opens Hikari and executes Flyway;
- constructs `MariaDbRuntime` and `PaperStorageBindings`;
- reads/transitions persisted operational state;
- publishes storage only while lifecycle state remains running.

### Synchronous Bukkit phase

Scheduled through Paper's global-region scheduler:

- snapshots online UUID, name, and current rank;
- starts freeze verification using immutable UUID/name data;
- starts staff-session recovery using immutable UUID/rank data;
- initializes vanish visibility;
- publishes operational mode and health;
- rechecks shutdown before each publication step and during player iteration.

`FreezeManager` and `StaffModeManager` reacquire current players by UUID on the appropriate scheduler instead of retaining mutable `Player` objects across asynchronous persistence work. Active staff-session recovery also performs a fresh synchronous rank check before applying Bukkit state.

### Asynchronous follow-up phase

- loads channel secrets and TLS material;
- starts the persistent Velocity socket client;
- registers recurring operational-state database work.

Persistent-channel configuration is snapshotted synchronously before environment, TLS, and network work begins asynchronously.

### Shutdown behavior

- shutdown before publication closes the unpublished runtime;
- shutdown after conditional publication but before Bukkit completion removes and closes that exact runtime;
- late callbacks cannot publish mode, recover managers, or restart follow-up components;
- cleanup is conditional on runtime identity, so stale callbacks cannot close a replacement runtime;
- coordinator tests cover phase order, shutdown during open, shutdown between phases, synchronous failure, and duplicate start rejection.

Phase C commits:

- `34d405dfbd810c903ea407d3de19ccd044aa24d4` — `Add Paper storage bootstrap coordinator`
- `266623546dd1b60a94d480d94e3100ae7b703438` — `Test Paper storage bootstrap thread transitions`
- `f3362375de1775978a7d905eeb05824cc434646f` — `Remove asynchronous Player capture from freeze recovery`
- `c4e1715b45e45c8a41b691d2b8754bd3064ff800` — `Remove asynchronous Player capture from staff recovery`
- `c643acdc2dbc4a824b8990a4b50de39a3db2247e` — `Snapshot persistent channel configuration synchronously`
- `fe0b690ac791efe28469b6c1c3b2181cffe6f7f4` — `Return Paper storage bootstrap to the Bukkit scheduler`
- `aeea8be35156d3ddedfc1719fdfc16cac431af02` — `Fix persistent channel settings validation`

Validated exact head `aeea8be35156d3ddedfc1719fdfc16cac431af02`:

- Run/job: `30680602051` / `91316711573`
- Artifact: `8812093760`
- Digest: `sha256:14ccc12397c12cdcea963136bc8d87e06b44d2df8c33de25ba16dc91108c42ba`
- Paper SHA-256: `82871630fc9cdc7d411acb52d28cd7ddcefa2f1717cf6f9f8b22118815e1e21e`
- Velocity SHA-256: `f6880b683a7c08e7321cf953aa406e05631bc3142bab10a4afb2ef021bc2bf1e`
- Coverage: lines `34.72%`, branches `28.25%`, instructions `37.03%`
- Provider source types/leaks: `24` / `0`
- Result: success

## B3 API review started

The existing alert/request APIs and actual command implementation were inspected before worker development:

- claims are recipient-specific through `claimDirect(...)` and `claimAudience(...)`;
- claims contain durable delivery identity, immutable intent, attempt count, and lease deadline;
- request lookup is available through `PunishmentRequestStore.find(...)` for asynchronous rendering;
- all six lifecycle events exist;
- intents encode audience, requester exclusion, minimum rank, and visibility;
- the actual reviewer command is `/punish review <request-id>`.

No B3 alert-delivery worker has been committed at this checkpoint.

## Remaining B3 work

- presentation model and renderer for every event/audience;
- bounded worker using asynchronous claim, synchronous final authorization/presentation, and asynchronous fenced outcome recording;
- direct, reviewer, and operational current-rank/visibility checks;
- disconnect, retry, cancellation, and dead-letter classification;
- polling, reconnect delivery, per-recipient single-flight, and maintenance scheduling;
- validated immutable worker configuration;
- real `/estaff reload` with old-config retention on failure;
- storage-port exposure and worker lifecycle ownership;
- orchestration, renderer, configuration, reload, and integration tests;
- refreshed Codacy static finding review;
- one-off CodeRabbit review;
- exact-head B3 validation.

B3 and production readiness are not claimed.

## Quality and evidence state

The latest known pre-B3 static-analysis aggregate remains unclean:

- 51 total findings
- 7 critical
- 11 high
- 33 medium
- 83.65% diff coverage
- +2.18% overall coverage variation

These values have not been refreshed at the intended B3 review head and are not treated as approval.

The Coverage workflow checks out the PR head explicitly, but its failure-summary script currently labels the synthetic pull-request merge SHA from `GITHUB_SHA` as the tested commit. Checkout logs and successful artifacts verify the exact head correctly; correcting that diagnostic label remains repository-evidence cleanup and does not authorize edits to the separate Pi staging workflow.

## Documentation-head rule

This manifest commit is later than validated code head `82ccebac...`. It requires its own exact-head CI success before the branch tip can be described as validated. The evidence above remains valid for each exact code head it names.
