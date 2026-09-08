# ES-X03 EnthusiaMarket provider handoff

## Current status

`ACTIVE` on 2026-08-13. Implementation, local analyzer cleanup, and both exact-head
Java 21 validation gates are complete. Paired PRs #139 and #3 are open. Staff hosted
CI and private Pi staging passed at `085a7d83`; hosted Codacy requires action on the
newly imported component. Merge, post-merge parity, and branch cleanup remain.

## Starting authority

- EnthusiaStaff `main`: `49e5aa999b43193181aafabbb75811c820fa03c7`.
- EnthusiaMarket `main`: `bc24f1010642d6042307bc13a32fb33cc94e8883`.
- Temporary branch in both repositories: `package/es-x03-market-provider`.
- Staff starting migration ceiling: V18, immutable.
- Market starting migration ceiling: V024, immutable.
- Issue #43 and production LiteBans authority are unchanged.

## Implemented behavior

EnthusiaMarket publishes `MarketModerationApi` version 1 with JDK-only models. V025 adds
the durable provider operation journal, stall locks, player acquisition fences, revisioned
blacklists, and stall moderation revisions. Preparation atomically snapshots and freezes
the target-owned stall while retaining ownership. Confiscation requires a named human
reviewer and exact checksum. Release/restoration verify current state, restore exact
provider-owned fields, and release reservations. Ambiguous state is quarantined.

Acquisition and conflicting shop paths consult durable fences before money, items, or
ownership change. Snapshots are limited to 100 shops and 1 MiB. Exact retries replay;
identity, checksum, and revision mismatches do not overwrite newer state.

The final candidate also routes acquisition permits, moderation reservations, and
standalone blacklist mutations through one atomic per-player database fence. Optimistic
blacklist revisions turn simultaneous writers into one winner and one explicit conflict,
rather than allowing overlapping acquisition/restriction state or leaking a raw SQL race.
Review hardening compares the complete immutable prepare request, preserves unlocked stock
updates in mixed batches, rolls back mapped provider conflicts, validates snapshot roots,
handles timeout/interruption and terminal gate cleanup, and localizes moderation-hold text.

EnthusiaStaff compiles against the typed contract without shading it. V19 adds durable
idempotency, recovery, revision, and review-alert metadata. Staff records `PREPARING`
intent before provider calls, validates the case target, reconciles bounded restart work,
and never automatically approves a prepared confiscation. `/marketcase` provides explicit
prepare, approve, release, Founder-only restore, blacklist, unblacklist, and status paths.
All mutations require uppercase `CONFIRM`. Local status remains readable during provider
outage when Staff storage is available; writes remain fail-closed.

## Current heads and synchronization

- Market candidate: `62408695063d03303026766befb065a0f1f51044`.
- Staff last pushed evidence head before the component synchronization update:
  `085a7d83264d36242cdbf1e90b31d16e83ef47ba`.
- Aggregate Market tree exactly matches the standalone candidate before merge.
- Product-tree hash: `8d27f4d9c64ca52feecd1df6200a45314610fa0df4b27da9d39b444152007c3b`.
- Component metadata remains `SYNC_PENDING` until both normal merges and post-merge parity.

## Exact-head validation

- Market `62408695063d03303026766befb065a0f1f51044` passed the clean,
  cache-disabled Java 21 build with all 11 tasks executed: `test`, `check`, `detekt`,
  `shadowJar`, and `jacocoTestReport` included. Test XML reports 120 suites and 637
  tests with zero failures or errors. The Windows graph passed 631 and skipped the five
  Docker cases plus one unrelated remote-authentication case.
- A separate clean Java 21 run in WSL, where Docker Engine 29.1.3 is available, executed
  all five `JdbcMarketModerationMariaDbTest` cases with zero failures, errors, or skips
  against disposable MariaDB 11.8.3. They cover provider lifecycle, concurrent
  preparation, acquisition fencing, acquisition/blacklist exclusion, and concurrent
  blacklist writers. Across both runs, 636 of the 637 cases executed successfully; only
  the unrelated remote-authentication case remains skipped.
- Market clean-tree local Codacy: 40 Lizard findings in all scanned source/test/tool files,
  including 35 production findings. The only two in a touched file are unchanged NBT
  parser methods whose line numbers shifted; no changed method introduced a finding.
  Java PMD and Trivy report zero. Opengrep reports one pre-existing workflow action
  pinning finding and zero touched-file findings.
- Staff `9d5cf145c09c09f259a05f29b144ed865f5a5a45` passed the clean,
  cache-disabled Java 21 build with 39 tasks. Test XML reports 222 suites and 951
  tests with zero failures, errors, or skips. The integration-test subset contains
  50 suites and 192 tests, all executed against disposable MariaDB 11.8.3 where
  applicable; Docker Engine 29.1.3 and Testcontainers were observed running.
- Staff local Codacy package delta: PMD, Opengrep, and Trivy zero. Four Lizard findings are
  the same pre-existing `EnthusiaStaffPaperPlugin`/`MariaDbRuntime` findings as the base;
  the ES-X03 JDBC store file-length finding was removed without suppression.
- Component synchronization tests pass: 8 tests, 3 skipped by design. Orchestration
  validation passes for 23 packages and 99 audit IDs.
- Staff produced exactly two runtime artifacts. Paper is 9,274,286 bytes with SHA-256
  `e275fd6912dd8b282d65ea735a72eb4f258a8e4e7ed5b9224abe44cb5be35d15`;
  Velocity is 7,977,860 bytes with SHA-256
  `85fee16bbdaf4eb8916f1a64506dd4dcd3b3b195a383ab1adb5d7c3c632affac`.
  Neither contains `net/enthusia/market/api/moderation` classes.
- Market produced one 4,138,102-byte runtime artifact with SHA-256
  `ba821a7fdc509f2a94ba155d911351c04ab540c15f8da21e5f1c31dd333f9d6f`.
  It contains the provider-owned moderation API, as required at runtime.
- Market Wiki validation passed for 29 frontmatter pages, 14 player-topic parity pages,
  and strict MkDocs rendering. Staff Wiki validation passed for 38 pages.

The checked-in shell wrappers have CRLF line endings, so the exact Linux builds invoked
the checked-in Gradle wrapper JAR directly rather than changing repository files. The
Market build used the pre-existing project `LumaGuilds-2.1.6.jar` compile dependency.

Codacy CLI v2 (`1.0.0-main.380.sha.27e119a`) used its removable WSL cache at
`/home/p2wn/.cache/codacy`; no service was installed. An ephemeral Python virtual
environment containing the exact pinned Market Wiki requirements was created under the
system temporary directory for documentation validation. That environment, analyzer
archives, and generated MkDocs output were deleted after validation.

## Static-analysis notes

No analyzer rule, first-party source path, or valid finding was suppressed or excluded.
The four surviving Staff Lizard items are visible baseline findings; this package does not
claim they are resolved. Hosted Codacy grade is not asserted because the available response
does not expose it. Staff PR #139 reported 991 newly visible findings after the complete
Market tree was imported; the check is `ACTION_REQUIRED`. That aggregate result is retained
honestly even though clean-tree local analysis reports no finding in the final race-fix
files. Staff CodeRabbit skipped automated review because the PR exceeds its 545-file limit;
Market CodeRabbit's bounded review identified valid replay, localization, lifecycle,
snapshot, repository-fencing, error-mapping, and test-fixture findings; those are fixed
at `6240869`. Three suggestions do not match the verified design: same-JVM plugins are a
trusted runtime rather than a security sandbox, stall creation cannot replace an existing
locked primary key, and proposed operation foreign keys conflict with standalone blacklist
operations and lock-before-journal ordering. Those assumptions are documented without
suppressing analyzer rules. The incremental rerun was rate-limited, review threads were
not modified without explicit authorization, and no final CodeRabbit approval is claimed.
Neither reviewer status substitutes for Codacy or acceptance testing.

## Boundaries

- No production listings, player records, credentials, databases, or deployment routes
  were accessed.
- No cutover, punishment authority, or issue #43 state changed.
- No full EnthusiaRollback project work was started.
- Representative destructive, latency, process-kill, and load acceptance remains assigned
  to ES-V03.
- The pre-existing full Market migration chain has a V001 MariaDB incompatibility because
  indexed identifier columns are declared as `TEXT`; ES-X03 did not rewrite immutable
  history. The representative V025 upgrade fixture now runs through the production
  `MigrationRunner` and passes; this limitation remains explicit for later
  migration-history remediation.

## Exact next action

Commit and push the final component synchronization record; let hosted checks run on that
exact head; address valid hosted Codacy or bounded review findings; merge both PRs normally;
prove post-merge parity and containment; remove both temporary branches; and publish the
terminal package record.
