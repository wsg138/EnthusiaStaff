# ES-X03 EnthusiaMarket provider handoff

## Current status

`ACTIVE` on 2026-08-13. Implementation and local analyzer cleanup are complete; exact-head
full validation, paired PR review/merge, post-merge parity, and branch cleanup remain.

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
- Staff committed candidate before the final docs/state checkpoint:
  `1034efc817fb95b9587cff00cd63b5b90e8cd009`.
- Aggregate Market tree exactly matches the standalone candidate before merge.
- Product-tree hash: `761b6e1e6168782b752cca5bffe6ca8b9330694b38f13b9c19d3a82dbecdaf67`.
- Component metadata remains `SYNC_PENDING` until both normal merges and post-merge parity.

## Validation completed so far

- Market focused tests and Detekt passed after analyzer cleanup.
- Market MariaDB Testcontainers provider lifecycle, concurrent-preparation, and fencing
  scenarios passed on the provider implementation checkpoint.
- Market local Codacy: Lizard 35 findings versus 36 on the clean base; no ES-X03 delta.
  PMD, Opengrep, and Trivy report zero.
- Staff focused persistence/command/coordinator tests pass, including provider-outage
  journal status.
- Staff V19 journal and V18-to-V19 upgrade tests passed against disposable MariaDB on the
  implementation checkpoint.
- Staff local Codacy package delta: PMD, Opengrep, and Trivy zero. Four Lizard findings are
  the same pre-existing `EnthusiaStaffPaperPlugin`/`MariaDbRuntime` findings as the base;
  the ES-X03 JDBC store file-length finding was removed without suppression.
- Component synchronization tests pass: 8 tests, 3 skipped by design. Orchestration
  validation passes for 23 packages and 99 audit IDs.
- Full exact-head clean builds, final MariaDB reruns, runtime-JAR inspection, hosted CI,
  hosted Codacy, and PR review remain to be recorded.

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

Commit and push the final synchronized docs/state checkpoint. Run full Java 21 clean builds
and all applicable MariaDB tests on both exact heads, inspect Staff runtime packaging, open
and cross-link the two PRs, resolve valid hosted findings, merge normally, prove post-merge
parity/containment, remove both temporary branches, and publish the terminal package record.
