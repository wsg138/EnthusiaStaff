# EnthusiaStaff workspace manifest

Last updated: 2026-07-31 (America/Indiana/Indianapolis)

This manifest records the durable punishment-request notification work on draft PR #27. It is evidence and handoff documentation, not a production-readiness claim.

## Repository state

- Repository: `wsg138/EnthusiaStaff`
- Primary branch: `main`
- Verified `main` head before reconciliation: `b18fe55482d4c491acfebec8ad784c6690f63f20`
- Development branch: `section/punishment-request-notifications-recovery`
- Verified starting development head: `9d1cde3abe5ef7d19c2ea920880ed12b2590de96`
- Draft PR: #27 — `Add durable punishment request notifications and recovery`
- Main reconciliation merge commit: `a40f0ad8fb18311611d4e3bed76c56ee82e470bf`
- Validated post-merge/deadlock-fix head: `5faf99021b394411a8737a15e5a9a2540fe3bc2b`
- PR #27 status at this checkpoint: open, draft, unmerged, mergeable

The merge was produced through temporary PR #28 because the active repository environment had connected GitHub write access but no local authenticated Git/`gh` checkout. PR #28 merged `main` into the existing development branch with a normal merge commit. It did not merge or mark PR #27 ready.

## Hard boundaries

This work does not authorize or include:

- direct modification of `main`;
- force-push, rebase, squash, or history rewrite;
- merging PR #27 or marking it ready;
- staging-repository or staging-workflow administration;
- inspection, rerun, cancellation, or debugging of automatic Pi staging;
- Lincoln-PI-4 access;
- Bloom or production database access;
- production Discord credentials;
- live Discord network sending;
- live server testing;
- production configuration changes;
- a production-readiness claim.

A push may automatically trigger the separately owned Pi staging workflow. This development checkpoint does not inspect or use that workflow as evidence.

## Preserved B1.3/B2 model

The branch continues to preserve:

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
- atomic request transition, alert, delivery, lifecycle-event, and Discord-outbox insertion;
- explicit bounded request expiration with deterministic ordering and `FOR UPDATE SKIP LOCKED`;
- non-mutating request reads;
- Testcontainers rollback and concurrency coverage.

Delivery remains at-least-once. A crash after Bukkit presentation but before acknowledgement can cause a visible duplicate after lease recovery.

## Verified B2 evidence

Validated exact head:

`9d1cde3abe5ef7d19c2ea920880ed12b2590de96`

Evidence:

- Workflow run: `30675287547`
- Job: `91301146989`
- Result: success
- Java: Temurin `21.0.11+10`
- Command:

```bash
./gradlew clean build jacocoAggregateReport runtimeJars \
  --no-daemon \
  --no-build-cache \
  --no-configuration-cache \
  --console=plain
```

- Build: `BUILD SUCCESSFUL`
- Tasks: 49 actionable; 40 executed, 9 up-to-date
- Aggregate lines: `34.39%`
- Aggregate branches: `28.01%`
- Aggregate instructions: `36.76%`
- Paper SHA-256: `0319150654b8c08e8c7dcacebf48fd49eec781647925ec6eb5546eefb15e837b`
- Velocity SHA-256: `bbdc61c54ac0696025421cc13adcb4a69f305cc32e58492e72ecddd747b245ac`
- Provider API source types checked per runtime: `24`
- Provider API leaks: `0`
- Validation artifact: `8810248079`
- Artifact digest: `sha256:da3c423a81720c966edc87c1fa8e84bfd67c7542e706c790d222168d92de0196`
- Codacy coverage upload and final notification: success

The previously documented run `30675533640` and job `91301882084` could not be resolved through GitHub and are not evidence. No manifest-only commit existed after the validated B2 head.

## Main reconciliation

All nine newer `main` commits were inspected before reconciliation. They covered:

- trusted automatic Pi staging workflow changes;
- hosted Java failure-evidence improvements;
- `/estaff` console and permission behavior;
- Paper database classloader discovery.

The staging workflow files were accepted from `main` without behavioral edits or staging administration.

The merged `MariaDb` implementation preserves both required behaviors:

```java
static HikariConfig hikariConfig(DatabaseConfig database)
```

and:

```java
Flyway.configure(MariaDb.class.getClassLoader())
```

## Post-merge validation failure

The first exact post-merge validation attempted head:

`a40f0ad8fb18311611d4e3bed76c56ee82e470bf`

- Workflow run: `30679869607`
- Job: `91314602904`
- Result: failure
- Failed test: `PunishmentRequestLifecycleConcurrencyIntegrationTest.externalPunishmentRacingApprovalProducesOneRequestTerminalTransition()`
- Database failure: MariaDB deadlock error `1213`, SQLState `40001`
- Failure artifact: `8811850755`
- Failure artifact digest: `sha256:37585b150c3bbeaddecfc3ccca8985ddd8fff4d0167a4359a343978c98fad720`

MariaDB selected the direct-punishment transaction as the deadlock victim while it raced request approval. `JdbcModerationStore` rolled the transaction back correctly, but the public moderation-store boundary propagated the rollback without a bounded retry. The merge checkpoint was therefore not called validated at `a40f0ad8...`.

## Deadlock correction

The development branch now wraps the public MariaDB moderation store with bounded deadlock recovery while retaining the raw connection-scoped store for request approval transactions.

Properties:

- retries only MariaDB error `1213` with SQLState `40001`;
- permits at most three total transaction attempts;
- uses bounded pauses before retries;
- does not retry lock-timeout error `1205` or unrelated SQL failures;
- preserves idempotency/replay behavior inside `JdbcModerationStore`;
- does not weaken the approval-vs-external-fulfillment concurrency assertion.

Pushed correction commits:

- `a578a9423eacfe3537cf00e4d48490289ab18d49` — `Add bounded moderation deadlock retries`
- `c2b5ff77558060ce04017b5b241ce48bd99f2ac2` — `Classify MariaDB transaction deadlocks`
- `3fb73da75ac2fd172938030e33e719e62f6008fe` — `Use bounded moderation retries in MariaDB runtime`
- `5faf99021b394411a8737a15e5a9a2540fe3bc2b` — `Test bounded moderation deadlock retries`

Focused tests cover successful retry, unrelated-failure passthrough, and retry exhaustion.

## Validated post-merge/deadlock-fix checkpoint

Validated exact head:

`5faf99021b394411a8737a15e5a9a2540fe3bc2b`

Evidence:

- Workflow run: `30680060081`
- Job: `91315164090`
- Result: success
- Java: Temurin `21.0.11+10`
- Command: the exact clean Java 21 command recorded above
- Build: `BUILD SUCCESSFUL in 3m 12s`
- Tasks: 49 actionable; 40 executed, 9 up-to-date
- Aggregate lines: `34.74%`
- Aggregate branches: `28.22%`
- Aggregate instructions: `37.09%`
- Paper runtime JAR count: `1`
- Velocity runtime JAR count: `1`
- ZIP integrity: success for both runtime JARs
- Paper SHA-256: `09a8eab143eefd9c56c49aa07a9fb4bd7aaeac804daa2135a591a2ed8c1a7e3c`
- Velocity SHA-256: `a2fe18813414f1a35cda9a74f3fdcee9de4e928acf6475a8d8c38f2176955a7a`
- Provider API source types checked per runtime: `24`
- Provider API leaks: `0`
- Validation artifact: `8811922938`
- Artifact digest: `sha256:479ef30818cece42b7357f60679a3de200d9dc77fee1cd1a9266a9d8967681e3`
- Codacy coverage upload: success
- Codacy final notification: success

This exact-head evidence validates the reconciled persistence/lifecycle checkpoint and the bounded deadlock correction. It does not validate B3 Paper delivery.

## Existing Paper startup-thread defect

The current plugin still submits `initializeStorage()` to the worker executor and directly enters a completion method that:

- reads online players;
- calls manager recovery/initialization methods using Bukkit;
- performs persisted operational-state work;
- starts the persistent channel;
- registers recurring database work.

This must not be moved wholesale to the Bukkit thread. The required correction is:

1. asynchronous database open, Flyway migration, non-Bukkit binding construction, and persisted-state calculation;
2. synchronous Bukkit manager recovery and runtime publication through the Paper scheduler;
3. asynchronous persistent-channel startup and recurring database operations.

Every handoff must recheck shutdown. A late synchronous callback must not publish or restart components after shutdown, and an unpublished runtime must close exactly once.

## B3 status

B3 remains unvalidated and incomplete. Required remaining work includes:

- Paper alert presentation contracts and renderer;
- bounded alert worker with async claim / sync authorization+presentation / async outcome recording;
- direct, reviewer, and operational authorization rechecks;
- disconnect/retry/cancellation/dead-letter classification;
- periodic and join-triggered single-flight scheduling;
- bounded maintenance operations;
- validated immutable worker configuration;
- real `/estaff reload` with old-config retention on failure;
- storage-port exposure and lifecycle ownership;
- orchestration, renderer, startup-thread, configuration, reload, and integration tests;
- refreshed Codacy finding review;
- one-off CodeRabbit review;
- exact-head B3 validation.

## Quality state

The latest known aggregate before B3 work was not clean:

- 51 total findings
- 7 critical
- 11 high
- 33 medium
- 83.65% diff coverage
- +2.18% overall coverage variation

These values must be refreshed at the intended review head. They are not treated as approval and must not be copied forward as current B3 results without verification.

## Documentation-head rule

This manifest update creates a later documentation head than `5faf9902...`. The manifest commit itself must receive its own exact-head CI evidence before it is described as the current validated branch head. The evidence above remains valid evidence for the exact code head it names.
