# ES-X03 EnthusiaMarket provider handoff

## Current status

`ACTIVE` on 2026-08-13. Implementation, local analyzer cleanup, and both exact-head
Java 21 validation gates are complete. Paired PR review/merge, hosted analysis,
post-merge parity, and branch cleanup remain.

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

EnthusiaStaff compiles against the typed contract without shading it. V19 adds durable
idempotency, recovery, revision, and review-alert metadata. Staff records `PREPARING`
intent before provider calls, validates the case target, reconciles bounded restart work,
and never automatically approves a prepared confiscation. `/marketcase` provides explicit
prepare, approve, release, Founder-only restore, blacklist, unblacklist, and status paths.
All mutations require uppercase `CONFIRM`. Local status remains readable during provider
outage when Staff storage is available; writes remain fail-closed.

## Current heads and synchronization

- Market candidate: `daed4d08d96f69f4513431c8bff8b90ada8faa70`.
- Staff exact source/documentation head validated before this evidence-only update:
  `9d5cf145c09c09f259a05f29b144ed865f5a5a45`.
- Aggregate Market tree exactly matches the standalone candidate before merge.
- Product-tree hash: `761b6e1e6168782b752cca5bffe6ca8b9330694b38f13b9c19d3a82dbecdaf67`.
- Component metadata remains `SYNC_PENDING` until both normal merges and post-merge parity.

## Exact-head validation

- Market `daed4d08d96f69f4513431c8bff8b90ada8faa70` passed the clean,
  cache-disabled Java 21 build with all 11 tasks executed: `test`, `check`, `detekt`,
  `shadowJar`, and `jacocoTestReport` included. Test XML reports 118 suites and 628
  tests with zero failures or errors and one unrelated remote-authentication skip.
- All three `JdbcMarketModerationMariaDbTest` cases executed against disposable
  MariaDB 11.8.3 through Testcontainers. They cover provider lifecycle,
  concurrent preparation, and acquisition fencing; none was skipped.
- Market local Codacy: Lizard 35 findings versus 36 on the clean base; no ES-X03 delta.
  PMD, Opengrep, and Trivy report zero.
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
- Market produced one 4,127,047-byte runtime artifact with SHA-256
  `eca7943d23f2c65492653a1848d1ba3fd251e698db72cebd1b0d19ad94e186cd`.
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
does not expose it. CodeRabbit has not yet reviewed the final PR heads; no approval is
claimed.

## Boundaries

- No production listings, player records, credentials, databases, or deployment routes
  were accessed.
- No cutover, punishment authority, or issue #43 state changed.
- No full EnthusiaRollback project work was started.
- Representative destructive, latency, process-kill, and load acceptance remains assigned
  to ES-V03.
- The pre-existing full Market migration chain has a V001 MariaDB incompatibility because
  indexed identifier columns are declared as `TEXT`; ES-X03 did not rewrite immutable
  history. The representative V025 upgrade fixture passes and this limitation remains
  explicit for later migration-history remediation.

## Exact next action

Commit and push this exact validation record, recheck deterministic component parity, open
and cross-link the two PRs, run hosted CI/Codacy and one bounded CodeRabbit review, execute
the applicable private Pi plugin-loader gate, resolve valid findings, merge normally,
prove post-merge parity/containment, remove both temporary branches, and publish the
terminal package record.
