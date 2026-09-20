# ES-X03 active: paired Market static remediation

Date: 2026-09-18

Checkpoint refreshed: 2026-09-20

Package: `ES-X03 — EnthusiaMarket destructive provider`

Worker state: `PARTIAL` / `ACTIONABLE_CONTINUATION`

## Routing and scope

An owner-directed continuation permits bounded paired Market and Staff static
remediation. It does not permit broad Codacy suppression, private-staging
bypass, credential substitution, production access, or a replacement branch.

Staff [PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139) on
`package/es-x03-market-provider` remains the aggregate leg. Market
[PR #7](https://github.com/wsg138/EnthusiaMarket/pull/7) on
`package/es-x03-market-static-remediation` remains its paired draft provider
leg. Unpaired Market PR #6 is preserved and excluded.

## Starting and current references

- Historical frozen Staff implementation: `879eae12df35253cce6cd12179d5cef1afe95dd9`.
- Direct Staff `main` at reconciliation: `c1054da6a8f89b312df2e05e25edc958fceda7ef`.
- Pre-merge Staff product head: `d97a082523012edfac05db1d75fbd59a92d9b253`.
- Market `main`: `cc19fa966dcb155fa1743f5076fb5152e74bdf8f`.
- Market PR #7: `a7534f2475a6bf298aff50a87cb132f34c7aa063`.

At the pre-merge pair, clean-clone `component_sync.py compare` found 512 shared
files with no added, missing, or modified path. Both content hashes are
`bc302a74a4c9acc69cba22947f46688d0c108a666cb896774655cc8eb09588c9`.
`COMPONENT-METADATA.md` is the only aggregate-only component file.

Staff owns V20, X03 owns V21, and D09 reserves V22; their
`PaperCommandRegistrar.java` hunks are disjoint.

## Completed remediation

- Updated Market dependency, Markdown, test-complexity, and SQLite dialect
  boundaries without suppressing production analysis.
- Refactored bounded parser/configuration/helper hotspots with focused tests.
- Repaired Bedrock currency-shop creation: valid SELL and BUY form submissions
  now use `ShopFactory`'s price fallback instead of an invalid zero-cost override.
- Split the Bedrock creation callback below the reported static thresholds while
  preserving validation order, messages, factory mapping, and side-effect order.

## Validation completed

- Focused `BedrockCreateShopFormTest` passed with nine tests in Market and the
  aggregate component; component Detekt passed in both.
- Component `test shadowJar jacocoTestReport` passed in both checkouts; diff
  hygiene passed; local Lizard reports no Bedrock-form method above CCN 8 or
  50 lines.
- Market exact-head run `35524744229` passed build, test/shadowJar, MariaDB
  verification, coverage upload, security, and Detekt; Wiki run `35524744279`
  passed.
- Staff manual Coverage `35525198519` / job `106116024419` passed on
  `d97a082...`: Java 21 build/tests, runtime JAR inspection, aggregate JaCoCo,
  validation-artifact upload, and Codacy coverage upload. It is diagnostic
  because the normal current-main merge creates a new aggregate head.

## Current synchronization and blockers

Staff PR #139 is merge-conflicted against current `main`, so GitHub did not run
ordinary `pull_request` Coverage or Sentinel artifact workflows for `d97a082`.
The distinct Pi `pull_request_target` workflow did run. A normal merge of
`c1054da...` is in progress; conflicts are limited to `ai-agents` state
documents, while component source/tests, migrations, and workflows merge cleanly.
This is required to restore automatic exact-head validation, not a Pi workaround.

Codacy is `ACTION_REQUIRED` with 1,127 findings: 1,076 Markdownlint, 41
production Lizard, eight RAC-table reports against immutable Market migrations,
and two dependency-coordinate secret-pattern reports. No broad exclusion is
authorized.

Canonical Pi is `NOT PASS`: run `35524782510` failed before private dispatch
when workflow-history access returned HTTP 401 `Bad credentials`. No private
Pi, Paper, or MariaDB runtime ran. The staging owner must rotate or replace
`ENTHUSIASTAFF_STAGING_TOKEN` with a least-privilege credential able to read and
dispatch the required private workflow; do not use a personal credential or
direct private dispatch.

## Exact next action

Finish and push the normal current-main merge, keep the paired component tree
unchanged, then re-run exact component parity plus aggregate Coverage and
Sentinel artifact gates on the new head. Continue only bounded, valid
production-complexity refactors with mirrored tests. After credential repair,
freeze the resulting heads and rerun all required hosted, static, review,
Sentinel, and canonical Pi gates before normal merges and post-merge parity.

No production listings, balances, items, player data, databases, deployment,
Discord configuration, authority, LiteBans, cutover, or issue #43 acceptance
changed.
