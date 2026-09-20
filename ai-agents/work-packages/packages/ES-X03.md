# `ES-X03` — EnthusiaMarket destructive provider

## 1. Package identity

`ES-X03`; external/multi-repository; primary `COMP-STAFF`; other
`COMP-MARKET`; priority 120; conditional parallelism only without shared
destructive-state overlap.

## 2. Status

`PARTIAL` / `ACTIONABLE_CONTINUATION` under an owner-directed paired
static-remediation continuation. The historical D04 serialization blocker is
resolved. Safe, bounded provider remediation and required aggregate
synchronization remain actionable; Codacy and canonical Pi are still
non-passing and block acceptance.

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

The existing Staff [PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139)
on `package/es-x03-market-provider` remains the aggregate leg. Market
[PR #7](https://github.com/wsg138/EnthusiaMarket/pull/7) on
`package/es-x03-market-static-remediation` is its paired provider leg and
remains a draft. Preserve the separate unpaired Market
[PR #6](https://github.com/wsg138/EnthusiaMarket/pull/6) unchanged.

Market uses only ordinary public repository CI. No private Pi/staging runner
configuration, bridge implementation, credentials, topology, artifact-transfer
mechanism, Sentinel infrastructure, or private evidence may enter Market or
this public repository.

## 8. Current source and pairing checkpoint

- Direct Staff `main` at merge reconciliation is
  `c1054da6a8f89b312df2e05e25edc958fceda7ef`.
- The pre-merge aggregate product head is
  `d97a082523012edfac05db1d75fbd59a92d9b253`.
- Market `main` remains `cc19fa966dcb155fa1743f5076fb5152e74bdf8f`.
- Market PR #7 currently carries provider head
  `a7534f2475a6bf298aff50a87cb132f34c7aa063`.
- The paired product trees at those pre-merge heads contain 512 shared files;
  clean-clone `component_sync.py compare` found no added, missing, or modified
  paths and recorded shared content hash
  `bc302a74a4c9acc69cba22947f46688d0c108a666cb896774655cc8eb09588c9`.
  `COMPONENT-METADATA.md` is the only aggregate-only component file.
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

## 10. Validation record

- Market and aggregate component focused `BedrockCreateShopFormTest` runs
  passed with nine tests each; component Detekt passed in both repositories.
- The Market component full `test shadowJar jacocoTestReport` target passed in
  both the standalone and aggregate component checkouts. `git diff --check`
  passed, and local Lizard reports no method above CCN 8 or 50 lines in the
  refactored Bedrock form.
- Market exact-head run `35524744229` passed build, Test/shadowJar, MariaDB
  verification, coverage upload, security, and Detekt. Wiki run `35524744279`
  passed all checks.
- Staff manual exact-head Coverage run `35525198519` / job `106116024419` passed
  for pre-merge product head `d97a082...`. It is diagnostic only: the current
  normal merge will require fresh exact-head aggregate validation.
- Staff PR #139's pre-merge automatic `pull_request` Coverage and Sentinel
  workflows did not run because the PR was merge-conflicted. The public Pi
  `pull_request_target` workflow is a distinct trigger and did run.

## 11. Remaining blockers and non-suppressive boundaries

Codacy is non-passing on pre-merge Staff head `d97a082...`: `ACTION_REQUIRED`
with 1,127 findings (1,076 Markdownlint, 41 Lizard production-complexity,
eight RAC-table reports against immutable Market migrations, and two
dependency-coordinate secret-pattern reports). Existing narrow source/test and
dialect scopes remain; no broad component, migration, security, or Opengrep
suppression is authorized.

Canonical Pi is `NOT PASS`: public supersession run `35524782510` failed before
private dispatch when the workflow-history request returned HTTP 401 `Bad
credentials`. No private Pi, Paper, or MariaDB runtime ran. The staging owner
must rotate or replace `ENTHUSIASTAFF_STAGING_TOKEN` with a least-privilege
credential able to read and dispatch the required private Actions workflow. Do
not use a personal credential or bypass the public bridge.

## 12. Exact next actions

Finish the normal merge of current Staff `main`, preserving the paired Market
component source and tests, then push the merge commit. Re-establish exact
component parity and run fresh aggregate Coverage and Sentinel artifact gates
for that merge head. Continue only valid production-complexity refactors in
small, tested paired batches. After the staging owner repairs the private bridge
credential, freeze the resulting heads and rerun required hosted, static,
review, Sentinel, and canonical Pi gates.

## 13. Completion definition

ES-X03 is complete only after both paired PRs have terminal green required
gates, normal merges, exact post-merge standalone-to-aggregate parity, and
updated component metadata. `ES-V03` retains representative destructive and
load acceptance.

## 14. Handoffs and production boundary

Historical handoffs remain
`ai-agents/reports/package-handoffs/2026-08-14-es-x03-market-provider-blocked.md`,
`ai-agents/reports/package-handoffs/2026-08-26-es-x03-discord-serialization-blocked.md`,
and `ai-agents/reports/package-handoffs/2026-09-18-es-x03-parked-static-and-pi.md`.
The active handoff is
`ai-agents/reports/package-handoffs/2026-09-18-es-x03-static-remediation-active.md`.

No production listing, balance, item, player data, database, deployment,
Discord configuration, authority, LiteBans, cutover, or issue #43 acceptance
changed.
