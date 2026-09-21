# `ES-X03` — EnthusiaMarket destructive provider

## 1. Package identity

`ES-X03`; external/multi-repository; primary `COMP-STAFF`; other
`COMP-MARKET`; priority 120; conditional parallelism only without shared
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
unverified reflection against provider internals; and representative destructive,
load, or process-kill acceptance assigned to `ES-V03`.

## 6. Dependencies

`ES-P08` and `ES-X02` are `COMPLETE`.

## 7. Repository and privacy boundaries

Staff [PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139) is
OPEN/non-draft/UNSTABLE on `package/es-x03-market-provider` and remains the
aggregate leg. Market [PR #7](https://github.com/wsg138/EnthusiaMarket/pull/7)
is OPEN/DRAFT/CLEAN on `package/es-x03-market-static-remediation` and remains
the paired provider leg. Preserve the separate unpaired Market
[PR #6](https://github.com/wsg138/EnthusiaMarket/pull/6) unchanged.

Market uses only ordinary public repository CI. No private Pi/staging runner
configuration, bridge implementation, credentials, topology, artifact-transfer
mechanism, Sentinel infrastructure, or private evidence may enter Market or
this public repository.

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
  disjoint hunks in `PaperCommandRegistrar.java`.

## 9. Remediation included so far

The paired checkpoint upgrades `mkdocs-material`, makes Market Markdown clean
under the default rule set, scopes test-only Detekt and Lizard complexity checks
to test source, and keeps Market SQLite migrations out of aggregate SQL Server
dialect engines.

It also removes verified parser-facing complexity without changing the intended
provider contracts: search predicate parsing, auction config accessors, wiki
front-matter validation, shop-edit helpers, shop-creation input grouping,
web-sync helpers, stall eviction cleanup, break-duration parsing, public
snapshot projection, and LumaGuilds helper bodies.

The Bedrock creation path received a distinct functional repair: valid SELL and
BUY submissions now leave `ShopFactory.Pricing.costAmountOverride` null, so the
factory applies the validated currency price instead of attempting to construct
an invalid zero-cost shop. The actual Cumulus callback is covered for SELL,
BUY, TRADE, the maximum-price clamp, malformed/missing amounts, invalid amount,
and invalid trade pricing.

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

## 11. Remaining blockers and non-suppressive boundaries

The non-passing Codacy result is not suppressed. Exact check 106161999175 is
ACTION_REQUIRED with 1,126 reported issues. Prior triage identified Markdownlint, production
Lizard, immutable-migration RAC-table, and dependency-coordinate
secret-pattern categories; each current finding requires scoped verification.
The narrow source, test, and dialect scopes remain in place. A broader
suppression or analyzer-rule decision requires explicit authorization and
supported Codacy configuration; it must not be guessed in source through a
component-wide exclusion.

Canonical Pi is `NOT PASS`: public supersession run `35524782510` failed before
private dispatch when the workflow-history request returned HTTP 401 `Bad
credentials`. No private Pi, Paper, or MariaDB runtime ran. The staging owner
must rotate or replace `ENTHUSIASTAFF_STAGING_TOKEN` with a least-privilege
credential able to read and dispatch the required private Actions workflow. Do
not use a personal credential or bypass the public bridge.

The aggregate component and standalone Market product tree are exactly equal
at the current paired heads. Clean-clone comparison found 512 shared files and
the shared hash is
d266bb0039f9b7e60cf59b253b52de15e60cd42b17a0c425bbe3ad98896bbd52.
Post-merge parity and component metadata updates remain required.

Finish the normal merge of current Staff `main`, preserving the paired Market
component source and tests, then push the merge commit. Re-establish exact
component parity and run fresh aggregate Coverage and Sentinel artifact gates
for that merge head. Continue only valid production-complexity refactors in
small, tested paired batches. After the staging owner repairs the private bridge
credential, freeze the resulting heads and rerun required hosted, static,
review, Sentinel, and canonical Pi gates.

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
updated component metadata. `ES-V03` retains representative destructive and
load acceptance.

## 14. Handoffs and production boundary

Canonical handoff:
ai-agents/reports/package-handoffs/2026-09-20-es-x03-static-remediation-active.md.

No production listing, balance, item, player data, database, deployment,
Discord configuration, authority, LiteBans, cutover, or issue #43 acceptance
changed.
