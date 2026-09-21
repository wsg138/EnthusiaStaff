# ES-X03 — EnthusiaMarket destructive provider

## 1. Package identity

**ES-X03**; external/multi-repository; primary **COMP-STAFF**; other
**COMP-MARKET**; priority 120; conditional parallelism only without shared
destructive-state overlap.

## 2. Status

**PARTIAL / ACTIONABLE_CONTINUATION** after the paired static-remediation and
owner-authorized Bedrock currency-pricing repair. The historical D04
serialization blocker is resolved. Fresh parity, aggregate Coverage, and
durable Sentinel restart evidence are current; Codacy and canonical Pi remain
non-passing acceptance gates.

## 3. Objective

Implement durable market restriction, reservation, confiscation, rollback, and
exact restoration across EnthusiaStaff and EnthusiaMarket.

## 4. Included behavior

The package includes a versioned provider contract, listing and reservation
ownership, durable snapshots and operation IDs, restriction and confiscation,
idempotent rollback and restoration, restart and race handling, provider
missing/version-mismatch behavior, and matching aggregate parity.

## 5. Explicit exclusions

Production listings; whole-market rollback; currency or reputation work;
unverified reflection against provider internals; and representative
destructive, load, or process-kill acceptance assigned to ES-V03.

## 6. Dependencies

ES-P08 and ES-X02 are complete.

## 7. Repository and privacy boundaries

Staff [PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139) is
OPEN/non-draft/UNSTABLE on `package/es-x03-market-provider` and remains the
aggregate leg. Market [PR #7](https://github.com/wsg138/EnthusiaMarket/pull/7)
is OPEN/DRAFT/CLEAN on `package/es-x03-market-static-remediation` and remains
the paired provider leg. Preserve the separate unpaired Market
[PR #6](https://github.com/wsg138/EnthusiaMarket/pull/6) unchanged.

Market uses only ordinary public repository CI. No private Pi or staging
runner configuration, bridge implementation, credentials, topology,
artifact-transfer mechanism, Sentinel infrastructure, or private evidence may
enter Market or this public repository.

## 8. Current paired source checkpoint

- Staff main currently is
  63e920d1b9cc95491cc4950abf944e7efb1d3b6a.
- Market main remains cc19fa966dcb155fa1743f5076fb5152e74bdf8f.
- Market PR #7 product head is
  9f4a4145ab3628831edc27a534c4420faa5e23f1.
- Staff PR #139 current head is
  47b0b13cb1fd3786fc2743ee8765cc322a6d7a0f. Its parent `f69aef6f` normally
  merged then-current `main` `63e920d1b9cc95491cc4950abf944e7efb1d3b6a`
  before the paired listener-routing commit.
- Clean-clone component_sync.py compare reports 512 shared files with no added,
  missing, or modified path. Both product trees have hash
  d266bb0039f9b7e60cf59b253b52de15e60cd42b17a0c425bbe3ad98896bbd52.
- Staff owns V20, X03 owns V21, and D09 reserves V22. X03 and D09 retain
  disjoint PaperCommandRegistrar.java hunks.

## 9. Completed paired remediation

The checkpoint upgrades mkdocs-material, makes Market Markdown clean under
Market's configured linting, and routes Geyser and transitive dependencies
through OpenCollab before JitPack. It keeps production analysis enabled while
scoping only Market test complexity and component SQLite dialect handling.

Behavior-preserving refactors reduced verified parser and projection debt in
search, auction configuration, wiki front matter, shop edit/create plumbing,
web synchronization, public snapshots, LumaGuilds helpers, stall eviction,
break-delete duration parsing, and maintenance-freeze state checks. Focused
tests cover newly separated projection, eviction, and duration behavior.

The owner-authorized Bedrock repair corrects valid SELL/BUY form submissions
that supplied a non-null zero cost override. That override violated the existing
Shop positive-cost invariant before persistence. SELL/BUY now use the factory's
existing validated-price fallback; TRADE keeps its parsed positive item quantity
and serialized cost item. Existing shops, balances, migrations, and the Java
menu are unchanged.

The latest paired listener repair handles only the main-hand interaction, so
Paper's per-hand interaction events cannot open duplicate creation flows. It
preserves the established sign, shop, container, stall, authority, denial, and
held-item guard order while adding direct Java-route, Bedrock-route, empty-hand,
off-hand, and missing-target regression coverage.

## 10. Exact-head validation record

- **Market local:** PASS. Test, shadow JAR, JaCoCo, and Detekt passed with
  the repository-pinned LumaGuilds artifact.
- **Market hosted:** PASS. Run 35542232664 passed build, tests, shadow JAR,
  MariaDB verification, security, and Detekt.
- **Market Wiki:** PASS. Run 35542232684 passed Markdown, frontmatter, and
  strict MkDocs checks.
- **Staff local:** PASS. Full component test, shadow JAR, JaCoCo, and Detekt
  passed with the repository-pinned LumaGuilds artifact.
- **Staff coverage/build:** PASS. Run 35542245591 / job 106161876789 passed
  at the exact Staff merge head, including aggregate build/tests, runtime JAR
  inspection, coverage, and artifact upload.
- **Diff hygiene and component parity:** PASS. Every paired checkpoint passed
  git diff --check; the final paired hash is
  d266bb0039f9b7e60cf59b253b52de15e60cd42b17a0c425bbe3ad98896bbd52.
- **PR review:** No live inline threads. CodeRabbit is skipped/manual for
  Staff and draft-skipped for Market, not an automated full-review approval.
- **Codacy static analysis:** NOT PASS. Exact Staff check 106161999175 is
  ACTION_REQUIRED with 1,126 reported issues.
- **Canonical Pi:** NOT PASS. Staff run 35542244424 failed before private
  dispatch on HTTP 401 Bad credentials.
- **Durable Sentinel restart:** PASS. Artifact run 35542245567 / job
  106161876827 passed; durable job 476 returned PAPER_RESTART_OK after two
  clean readiness and stop cycles against one disposable state.

## 11. Static-analysis disposition

The non-passing Codacy result is not suppressed. Exact check 106161999175 is
ACTION_REQUIRED with 1,126 reported issues. Prior triage identified Markdownlint, production
Lizard, immutable-migration RAC-table, and dependency-coordinate
secret-pattern categories; each current finding requires scoped verification.
The narrow source, test, and dialect scopes remain in place. A broader
suppression or analyzer-rule decision requires explicit authorization and
supported Codacy configuration; it must not be guessed in source through a
component-wide exclusion.

## 12. Current synchronization evidence

The aggregate component and standalone Market product tree are exactly equal
at the current paired heads. Clean-clone comparison found 512 shared files and
the shared hash is
d266bb0039f9b7e60cf59b253b52de15e60cd42b17a0c425bbe3ad98896bbd52.
Post-merge parity and component metadata updates remain required.

## 13. Exact unblock condition

Keep both implementation PRs and branches. Continue only small paired repairs
for validated static findings, with matching tests and parity checks; do not
fold unrelated stateful transaction, auction, persistence, listener, or GUI
findings into an unbounded cleanup. The staging owner must rotate or replace
ENTHUSIASTAFF_STAGING_TOKEN with a least-privilege credential that can read
workflow history and dispatch the required private workflow. Do not use a
personal credential or bypass the public bridge. After a changed product head,
or credential repair, freeze the heads and rerun every applicable exact hosted,
static, review, Sentinel, and Pi gate.

## 14. Completion definition

ES-X03 is complete only after both paired PRs have terminal green required
gates, normal merges, exact post-merge standalone-to-aggregate parity, and
updated component metadata. ES-V03 retains representative destructive and
load acceptance.

## 15. Handoff and production boundary

Canonical handoff:
ai-agents/reports/package-handoffs/2026-09-20-es-x03-static-remediation-active.md.

No production listing, balance, item, player data, database, deployment,
Discord configuration, authority, LiteBans, cutover, or issue #43 acceptance
changed.
