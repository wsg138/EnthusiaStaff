# EnthusiaStaff workspace manifest

<<<<<<< ours
Last updated: 2026-08-01 (America/Indiana/Indianapolis)

This manifest records work on draft PR #27. It is exact-head engineering evidence and handoff documentation, not a production-readiness or complete-B3 claim.

## Repository state

- Repository: `wsg138/EnthusiaStaff`
- Primary branch: `main`
- Verified `main`: `b18fe55482d4c491acfebec8ad784c6690f63f20`
- Development branch: `section/punishment-request-notifications-recovery`
- Checkpoint-two starting head: `bc34b980cf53addc3187f9acbdfffb4a0536d94d`
- Validated checkpoint-two code head: `ab932b4e42aa447196953f2b3b8774d931072f77`
- Draft PR: #27 — `Add durable punishment request notifications and recovery`
- PR #27 remains open, draft, unmerged, and mergeable.

The normal historical `main` reconciliation merge remains `a40f0ad8fb18311611d4e3bed76c56ee82e470bf`. No history was rewritten.

## Hard boundaries

This checkpoint did not:

- modify `main` directly;
- force-push, rebase, squash, or rewrite branch history;
- merge PR #27, mark it ready, or enable auto-merge;
- modify `wsg138/EnthusiaStaff-Staging`;
- modify or administer `.github/workflows/pi-staging-check.yml`;
- inspect, interpret, rerun, cancel, or debug automatic Pi staging;
- access Lincoln-PI-4;
- access Bloom or production databases;
- access production Discord credentials;
- implement live Discord network sending;
- perform live server testing;
- implement `/estaff reload`;
- perform the final Codacy cleanup;
- request a CodeRabbit review;
- claim exactly-once delivery, complete B3 readiness, or production readiness.

Branch pushes may have triggered separately owned automatic Pi staging. This work did not inspect or use those runs as evidence.

## Preserved durable model

The branch continues to preserve:

- MariaDB-authoritative punishment-request lifecycle;
- immutable logical intents in `staff_alerts`;
- recipient-specific delivery state in `staff_alert_deliveries`;
- independent delivery identity `(alert_id, recipient_id)`;
- `PENDING`, `LEASED`, `DELIVERED`, `CANCELLED`, and `DEAD_LETTER` states;
- fenced acknowledgement, failure, and cancellation;
- stale-owner protection and bounded lease recovery;
- explicit dead-letter requeue and resolution;
- exact immutable replay and `pra:v2` occurrence identity;
- atomic request lifecycle alert and Discord-outbox insertion;
- bounded request expiration;
- symmetric MariaDB deadlock retry around complete request/punishment mutation transactions;
- the three-phase `PaperStorageBootstrapCoordinator`;
- current Bukkit thread corrections;
- the independently owned operational-state task;
- existing console `/estaff` permission behavior.

Alert delivery remains at-least-once. A crash after Bukkit presentation but before durable acknowledgement can produce a visible duplicate after lease recovery.

## Checkpoint-two implementation

### Storage bindings

`PaperStorageBindings` now exposes:

- `PunishmentRequestAlertStore`;
- `PunishmentRequestStore`;
- the existing `PlayerDirectory`.

The Paper worker consumes domain ports only. It does not receive raw JDBC objects or route rendering through write-capable request services.

### Components

The Paper alert subsystem is split into independently testable components:

- `PunishmentRequestAlertWorkerSettings`;
- `PunishmentRequestAlertRecipient`;
- `PunishmentRequestAlertPresentation`;
- `PunishmentRequestAlertRenderer`;
- `PunishmentRequestAlertPresenter`;
- `PunishmentRequestAlertRuntime`;
- `BukkitPunishmentRequestAlertRuntime`;
- `PunishmentRequestAlertWorker`;
- `PunishmentRequestAlertLifecycle`.

Persistence and domain modules remain free of Bukkit classes.

### Exact delivery thread sequence

Every claimed presentation follows:

1. **Synchronous recipient snapshot** — bounded online-player snapshot copies UUID, current name, and current rank resolved from live permissions; no database work occurs.
2. **Asynchronous claim and render** — direct, reviewer, and operational claims are bounded; each referenced request is loaded through `PunishmentRequestStore.find(...)`; immutable presentation data is created without Bukkit access.
3. **Synchronous final authorization and presentation** — current player and current rank are reacquired immediately before presentation; no JDBC work occurs in this callback.
4. **Asynchronous fenced outcome** — successful presentation calls `delivered(...)`; final eligibility loss calls `cancel(...)`; temporary unavailability calls `failed(...)` with bounded retry timing.

Acknowledgement never occurs before presentation succeeds. Shutdown after claim leaves the lease unresolved for normal recovery.

### Authorization

- Direct requester delivery requires the exact delivery-recipient UUID and a currently online player; no staff rank is required.
- Reviewer delivery requires a fresh `MOD`, `ADMIN`, or `FOUNDER` rank, excludes `HELPER` and `DEVELOPER`, excludes the requester, enforces the intent minimum rank, requires pending/reviewable request state from the freshest asynchronous read, and enforces visibility.
- Operational delivery requires a fresh `ADMIN` or `FOUNDER` rank.
- Rank is resolved once before claim and again immediately before presentation.
- Reviewer state can still race between the last asynchronous database read and Bukkit packet presentation; the implementation and tests explicitly acknowledge that unavoidable boundary.

### Rendering

Adventure components cover all six lifecycle events for direct, reviewer, and operational audiences:

- `REQUEST_SUBMITTED`;
- `REQUEST_CLAIMED`;
- `REQUEST_APPROVED`;
- `REQUEST_DENIED`;
- `REQUEST_EXPIRED`;
- `REQUEST_EXTERNALLY_FULFILLED`.

Reviewer work uses the exact clickable command `/punish review <request-id>`. Name lookup failure falls back to UUID. Direct output excludes internal/private explanations. Approved and externally fulfilled outcomes require a case ID; denied and expired outcomes do not fabricate one.

### Outcomes and retry policy

Stable final-cancellation codes:

- `RECIPIENT_INELIGIBLE`;
- `REQUESTER_CONFLICT`;
- `VISIBILITY_DENIED`.

Stable retryable failure codes:

- `PLAYER_OFFLINE`;
- `PRESENTATION_UNAVAILABLE`;
- `PRESENTATION_FAILED`;
- `REQUEST_LOOKUP_FAILED`.

Stable permanent failure codes:

- `REQUEST_MISSING`;
- `INVALID_PRESENTATION_DATA`.

Retry delay uses overflow-safe bounded exponential backoff:

`min(maximumDelay, baseDelay * 2^(attempt - 1))`

Missing requests and malformed presentation data deterministically use the existing `failed(...)` transition to reach `DEAD_LETTER`; no second dead-letter table was added. Outcome-write exceptions do not trigger competing outcomes. A successful presentation followed by acknowledgement failure leaves the lease for expiry and recovery.

### Polling and reconnect delivery

- Poll cycles cannot overlap for one lifecycle instance.
- Online recipient snapshots, per-audience claims, total claims, presentations, and submissions are bounded.
- Per-recipient single-flight prevents periodic polling and join delivery from processing the same UUID concurrently.
- Different UUIDs can progress independently.
- `PlayerJoinEvent` performs no database work.
- Join delivery waits a small bounded delay, snapshots synchronously, claims/renders asynchronously, rechecks/presents synchronously, and records the outcome asynchronously.
- Disconnect after claim is retryable as `PLAYER_OFFLINE`, not cancellation.

### Maintenance

The lifecycle owns bounded asynchronous recurring maintenance for:

- `PunishmentRequestStore.expire(...)`;
- `PunishmentRequestAlertStore.expireIntents(...)`;
- `PunishmentRequestAlertStore.reclaimExpiredDeliveries(...)`;
- `PunishmentRequestAlertStore.deleteTerminalIntentsBefore(...)`.

Each operation uses its configured batch, suppresses same-operation overlap on one server, relies on existing SQL for multi-instance safety, rate-limits repeated failure logs, retries on a later cycle, and stops during shutdown. No leader election was introduced.

### Configuration and lifecycle ownership

- Immutable worker settings contain the polling, recipient, audience-batch, lease, retry, join-delay, maintenance, batch, and retention controls.
- Only `punishment-request-alerts.enabled` is exposed in YAML during this checkpoint.
- The subsystem defaults to disabled and publishes a clear runtime health issue while disabled.
- Full parsing, validation, reload, and atomic hot replacement remain deferred.
- `PunishmentRequestAlertLifecycle` owns polling, join listener state, maintenance tasks, overlap guards, recipient single-flight state, and its stopping flag.
- It does not consume `PaperRuntimeLifecycle`'s existing operational-state task slot.
- Startup occurs only after storage publication and synchronous Bukkit bootstrap.
- Subsystem startup failure disables alerts and records a health issue without closing valid storage or unrelated moderation functionality.
- Close is idempotent; late callbacks check stopping state; tasks/listener are cancelled before worker shutdown and MariaDB close.

## Checkpoint-two commits

1. `496bac80824317e7fae5406fc052d0ce338af803` — `Expose punishment alert stores to Paper runtime`
2. `2a206ce16d2bdc1efd12a279b3467194db0ac892` — `Add punishment alert worker settings`
3. `18233150486bd76fccfa90579f5ac2e9c0ac36ac` — `Add punishment alert recipient snapshot`
4. `f0f2b4b32ba87cf02c3fdd08b18cf491aa25fbc5` — `Add punishment alert presentation model`
5. `7a6560243adfa9becb073c97827cb11424fc027f` — `Add punishment alert presentation boundary`
6. `d132a8febe4cc5e9a82e9e61a1522227e6acd62b` — `Add punishment alert runtime boundary`
7. `dce7b3bc8a9ca95156f72bc265411f47507fc261` — `Add punishment request alert renderer`
8. `3e636861bbfb505993a96e582168c0e0d6be59ef` — `Implement fenced Paper alert delivery worker`
9. `d65c8efc619b9a2bd614289e07522fc5a7e0b12c` — `Add Bukkit punishment alert runtime adapter`
10. `9db6debf17da6871a6a098208ab6888b6910e8ab` — `Add punishment alert reconnect and maintenance lifecycle`
11. `39834d3511adcb62727c6020d2ff5bbf61fa3033` — `Add disabled punishment alert enable gate`
12. `67f9b2e068726c72864521ebe3bd7ca59637b8d4` — `Wire punishment alert lifecycle into Paper runtime`
13. `bcaf0dfe6c5091c2b5f5e3028811b784b281f183` — `Test punishment alert worker settings`
14. `eda9ed196cbd8ba79946b5fb9b49886827015913` — `Add punishment alert test fixtures`
15. `f3cd101150c29c56e8666f1bdcdef359507f2eec` — `Test punishment alert rendering`
16. `b73f833093f47c9a1b0366adcf0b411aecd3095c` — `Test alert delivery authorization and thread boundaries`
17. `e94f90ed38067135e6406db39b5de3854b073427` — `Test punishment alert reconnect and maintenance lifecycle`
18. `ab932b4e42aa447196953f2b3b8774d931072f77` — `Use current Adventure click payload API`

The first exact-head attempt at `e94f90ed38067135e6406db39b5de3854b073427` reached Paper test compilation and failed only because a deprecated Adventure test accessor was promoted to an error by `-Werror`. Commit `ab932b4e42aa447196953f2b3b8774d931072f77` switched the test to the current payload API.

## Exact validation command

```bash
./gradlew clean build jacocoAggregateReport runtimeJars \
  --no-daemon \
  --no-build-cache \
  --no-configuration-cache \
  --console=plain
```

## Validated checkpoint-two code evidence

Validated exact code head: `ab932b4e42aa447196953f2b3b8774d931072f77`

- Coverage run: `30683929485`
- Job: `91326094728`
- Result: success
- Java: Temurin `21.0.11+10`
- Exact checkout: `ab932b4e42aa447196953f2b3b8774d931072f77`
- Build: `BUILD SUCCESSFUL in 3m 40s`
- Tasks: 49 actionable; 40 executed, 9 up-to-date
- All modules compiled.
- Unit, Paper, MariaDB Testcontainers, migration V11-V13, rollback, and concurrency suites passed.
- Runtime JAR count: one Paper and one Velocity.
- ZIP integrity: success.
- Paper JAR SHA-256: `64e3a21ad832b881e4be913a6be7983ca0ebf480e5f67c3463aca6e2ca7f99ff`
- Velocity JAR SHA-256: `3e2e668de6542bfa90dfe7424e365992aaf6492941d8375ca2755052cb8c16e0`
- Paper JAR contains `org/mariadb/jdbc/Driver.class`.
- Paper JAR contains `META-INF/services/java.sql.Driver` naming `org.mariadb.jdbc.Driver`.
- Provider API source types: `24`.
- Provider API leaks: `0`.
- Aggregate coverage: lines `35.91%`, branches `29.21%`, instructions `38.23%`.
- Artifact: `8813280134`.
- Artifact digest: `sha256:4f8ad1e7dc6e1f2b1d2ef669ca0d55afd2fa631d05aa39726f8e12cfd91aff9c`.
- Codacy coverage upload and final notification: success.

## Current Codacy aggregate

Codacy's refreshed PR summary for code head `ab932b4e42aa447196953f2b3b8774d931072f77` reports:

- 88 total findings;
- 7 critical;
- 13 high;
- 68 medium;
- 72.47% diff coverage;
- +3.71% coverage variation;
- head coverage 35.91% versus common-ancestor coverage 32.20%.

This is not treated as approval. The finding-by-finding cleanup and final quality review remain intentionally deferred.

## Tests added

Focused tests prove:

- asynchronous claims and request lookup;
- synchronous final Bukkit authorization and presentation;
- asynchronous outcome writes;
- acknowledgement only after successful presentation;
- retry on offline or unavailable presentation;
- cancellation on final authorization loss;
- no false acknowledgement during shutdown;
- unresolved lease after acknowledgement exception;
- `HELPER`/`DEVELOPER` reviewer exclusion and current-rank re-resolution;
- rank threshold behavior for `MOD`, `ADMIN`, and `FOUNDER`;
- requester conflict prevention;
- operational delivery only to `ADMIN`/`FOUNDER`;
- independent direct/reviewer/operational bounds;
- UUID fallback and exact review command;
- all six lifecycle events across all audiences;
- no private explanation leakage to direct recipients;
- correct case-ID rules;
- missing/malformed data dead-lettering;
- polling/join single-flight for one UUID and independent progress for two UUIDs;
- configured maintenance batches, asynchronous execution, overlap suppression, later-cycle recovery, and shutdown suppression;
- single start, idempotent close, task/listener cancellation, disabled join processing, and blocked late presentation;
- the existing operational-state task remains independently owned.

## Deferred work

The following remain outside this checkpoint:

- complete validated YAML parsing;
- `/estaff reload`;
- atomic worker hot replacement;
- final Codacy finding cleanup and quality review;
- one-off CodeRabbit review request;
- live Discord sending;
- staging administration or Pi inspection;
- live server testing;
- production configuration/enablement;
- PR merge.

The Coverage workflow's failure summary still labels `GITHUB_SHA` rather than the explicitly checked-out PR head. That correction remains deferred unless separately authorized.

## Checkpoint result

Paper punishment-request alert delivery, reconnect processing, and maintenance are implemented and automatically validated; configuration reload, quality review, staging, and production verification remain outstanding.

This manifest update is a documentation-only branch-tip commit and therefore requires its own exact-head Coverage success before the branch tip is described as validated.
=======
Last updated: 2026-08-01 (America/Indianapolis)

This manifest records repository, validation and blocker state for development
coordination. Nothing listed here authorizes a production deployment, release,
LiteBans replacement or production-data change.

## Root repository state and validated checkpoint

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current merged repository state | `main` at `3f4e2a1164d570aadfb82522b07b4b32c9f2a7f9`, the PR #36 merge commit |
| Latest fully validated implementation revision | PR #36 head `3afeffc926571170e8df18c7d096ca7f4d89ec1b` |
| Evidence boundary | Every build, test, Codacy, artifact and Pi result in this section attaches to `3afeffc926571170e8df18c7d096ca7f4d89ec1b`; the later merge commit is not separately claimed as tested |
| Clean Java validation | 40/40 tasks; 99 suites / 398 tests; no failures, errors or skips at `3afeffc926571170e8df18c7d096ca7f4d89ec1b` |
| MariaDB validation | 15 MariaDB 11.8.3 Testcontainers suites / 68 tests at `3afeffc926571170e8df18c7d096ca7f4d89ec1b` |
| Runtime artifacts | From `3afeffc926571170e8df18c7d096ca7f4d89ec1b`: Paper SHA-256 `83D457FCA65839B6E674CC937F37E63782620D023E9B202F89ED3A88CF4D5060`; Velocity SHA-256 `FA17E9F891286250FEC21AD19CD425540C15CC163D58D65DF5E177856AEBDBD9` |
| Hosted quality result | At `3afeffc926571170e8df18c7d096ca7f4d89ec1b`: zero new Codacy findings, three fixed, 92.59% diff coverage, +0.103 coverage variation, no clone increase |
| Exact-SHA Pi staging | PASS — run `30709333535`, tested revision `3afeffc926571170e8df18c7d096ca7f4d89ec1b` |
| Production authority | **NOT READY**; LiteBans and the existing staff stack remain authoritative |

PR #36 preserved and tested every LiteBans shadow dimension across import,
reconciliation, replay and source deletion: counts, checksums, active state, UUID
mappings, expirations, login decisions, mute decisions, IP-ban decisions,
rejected rows and extra/orphan mappings.

## Active root pull requests

### PR #37 — Harden LiteBans cutover coordination

| Field | Current value |
| --- | --- |
| State | Draft, open |
| Branch | `section/plugin` |
| Base | `main` at `3f4e2a1164d570aadfb82522b07b4b32c9f2a7f9` |
| Current head | `511c92f7a36a8d892002e5904501d9dbb36cf4a6` |
| Current scope | Maintenance entry, duplicate rejection, abort, emergency freeze, transition audit, exact 168-hour/seven-summary gate, final incremental import, activation linkage and duplicate activation rejection |
| Current evidence | Focused MariaDB Testcontainers class passed; local PMD/Lizard clean for the new test |
| Status boundary | First focused checkpoint only; production refactor and complete validation remain unfinished |

PR #37 is the immediate LiteBans workstream. Do not represent it as complete until
its implementation, complete clean build, full MariaDB suite, hosted quality
checks, Wiki validation, exact-head staging and review are finished.

### PR #27 — Durable punishment request notifications and recovery

| Field | Current value |
| --- | --- |
| State | Draft, open; must remain unmerged until reconciled and completed |
| Branch | `section/punishment-request-notifications-recovery` |
| Current visible head | `094da12f11bfbf9f486186a624258d2159c64bfd` |
| Size | 95 commits; long-lived concurrent history |
| Implemented checkpoint | Paper punishment-request alert delivery, reconnect processing, maintenance and lifecycle foundations |
| Validated internal checkpoint | `a5ab8b9b543ecd78facbf29a2b8824b30220c6c3` completed the recorded Java build and runtime-jar checks |
| Deferred | Complete YAML parsing, atomic reload/worker replacement, final quality cleanup, review, Pi/live staging, Discord sending and production configuration |

PR #27 predates many merged root refactors. Reconciliation must preserve its
notification, staff-mode and freeze behavior while adopting current composition,
persistence, scheduling, configuration and quality boundaries. Do not discard its
history or merge duplicate implementations.

## Related repositories

| Repository | Expected role | Current coordinated status | Main blocker |
| --- | --- | --- | --- |
| `wsg138/enthusia-site` | Private punishment and appeal website | Root bridge exists; complete site branch not reconstructed or validated here | Auth/session/CSRF/media/rate-limit work, secrets and private staging |
| `wsg138/EnthusiaCurrency` | Exact economy moderation snapshots and plans | Root integration contract/adapter exists; provider implementation not validated | Provider branch and cross-plugin staging |
| `wsg138/EnthusiaCommend` | Persistent reputation restriction API | Root contract/adapter exists; provider implementation not validated | Provider branch and all write-entry enforcement tests |
| `wsg138/EnthusiaAutoClicker` | Versioned bounded client evidence | Root contract/adapter exists; provider implementation not validated | Provider branch and handshake/offline evidence staging |
| Intended `wsg138/Enthusia-RoseChat` | Moderation/staff channel and evidence bridge | Blocked; repository/API remains missing or inaccessible | Do not invent a remote or unsupported reflective/command integration |
| `wsg138/EnthusiaMarket` | Supported stall moderation and escrow-safe behavior | Root adapter exists; provider implementation not validated | Provider branch and transaction-compatible staging |

Each related project remains an independent Git repository. Histories must not be
flattened into EnthusiaStaff, and provider-owned API classes must not leak into the
Paper or Velocity runtime jars.

A cross-repository release candidate must use a release manifest containing one
authenticated revision per repository, with matching artifact hashes,
configuration checksums, environment versions and acceptance evidence. There is
no single global commit that can identify independent provider and website state.

## Current development route

The detailed path is maintained in:

```text
docs/development-blueprint.md
docs/wiki/pages/Development-Blueprint.md
reports/REQUIREMENTS-MATRIX.md
```

Immediate order:

1. Complete PR #37's implementation, full validation and review.
2. Rebase and reconcile PR #27 without losing or duplicating its work.
3. Establish a clean new `main` checkpoint and refresh this manifest.
4. Complete punishment history, appeal-linked decisions and durable notifications.
5. Finish modular configuration, operational modes, report UI/privacy and RoseChat evidence.
6. Stage staff mode, freeze, vanish, inventory and confiscation under real ownership and failure conditions.
7. Reconstruct providers and complete the private website.
8. Finish LiteBans recovery, seven-day shadow evidence, activation, emergency freeze and rollback.
9. Run one complete release-manifest acceptance candidate, then the mandatory 168-hour shadow period and final cutover rehearsal.

## Checkpoint update rules

At every coherent repository checkpoint record:

- repository and branch;
- base, implementation and final reviewed revisions;
- PR URL and state;
- exact validation commands;
- task, suite, total-test and MariaDB counts;
- runtime jar sizes and hashes;
- provider API source-type/leak inspection;
- hosted Codacy baseline, new/fixed issues, duplication and diff coverage;
- Wiki validation page count;
- exact staging run and tested revision;
- review findings and unresolved threads;
- blockers and unavailable acceptance groups.

For a cross-repository release candidate, additionally record one authenticated
revision per repository in the release manifest and test those revisions together.

A skipped, cancelled, superseded or different-revision run is never recorded as
passed. A merged PR is a development checkpoint, not deployment authorization.

## Release boundaries

- Keep LiteBans authoritative until full release-manifest acceptance, the exact
  168-hour shadow window, final reconciliation, cutover rehearsal and Founder
  authorization pass.
- Never combine evidence from undeclared revisions into one release candidate.
- Keep production credentials, private jars, databases, logs, evidence and
  runtime folders out of Git.
- Keep destructive operations configuration-gated and recovery-visible.
- Retain migration backups and legacy data through cutover; legacy removal is a
  later manual operation.
- Update this manifest, the requirements matrix, development blueprint and Wiki
  together when a root checkpoint changes.
>>>>>>> theirs
