# ES-X03 parked: paired static remediation and Pi bridge authentication

Date: 2026-09-19, updated in place

Package: **ES-X03 — EnthusiaMarket destructive provider**

Terminal worker state: **BLOCKED / PARKED_BLOCKED**

## Routing, scope, and collision result

The owner-authorized Bedrock currency-shop repair preserved Staff
[PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139) on
package/es-x03-market-provider and opened draft Market
[PR #7](https://github.com/wsg138/EnthusiaMarket/pull/7) on
package/es-x03-market-static-remediation. Unpaired Market PR #6 remains
preserved and excluded.

Staff main at current reconciliation is
d4b5d44d6b88126f9663974d892e3e8b6aac5b9d, and Market main remains
cc19fa966dcb155fa1743f5076fb5152e74bdf8f. Staff owns V20, X03 owns V21,
and D09 reserves V22; the one shared registrar-file hunk is disjoint. No
user-owned root-worktree path was changed.

## Frozen paired product heads and parity

- Market PR #7: 5b6606c2f71a410ed6f369b0b893a7888638a7f2.
- Staff PR #139: e67a67585179b7a8dd6b6dc8c81c9fe567f04ef1.
- component_sync.py compare: parity true, with no added, missing, or modified
  shared paths.
- Shared product hash:
  6ba7be19e647b9093bb9670b79026585eb5306f83e66480894fed3912b1f96f7.

## Completed remediation

- Updated mkdocs-material and brought Market documentation through its
  configured Markdown, frontmatter, and strict MkDocs checks.
- Routed Geyser and its transitives through OpenCollab before JitPack, fixing
  the hosted dependency-resolution failure without adding private
  infrastructure to Market.
- Kept production analysis enabled while limiting only Market test complexity
  and component SQLite dialect handling.
- Refactored bounded parser, configuration, projection, eviction, duration,
  and cache-state helpers. New focused tests cover public snapshots, eviction
  order, and duration parsing. These refactors preserve product behavior.
- Repaired Bedrock SELL/BUY currency pricing. Valid form submissions previously
  supplied a non-null zero cost override and violated the existing positive-cost
  invariant before persistence. Currency shops now use the existing price fallback;
  TRADE retains its parsed item quantity and cost item.

## Exact-head validation

- **Market local:** PASS. Test, shadow JAR, JaCoCo, and Detekt passed with
  the repository-pinned LumaGuilds artifact.
- **Market hosted:** PASS. Run 35474189763 passed build, tests, shadow JAR,
  MariaDB verification, security, and Detekt.
- **Market Wiki:** PASS. Run 35474189668 passed Markdown, frontmatter, and
  strict MkDocs checks.
- **Staff local:** PASS. Full component test, shadow JAR, JaCoCo, and Detekt
  passed with the repository-pinned LumaGuilds artifact.
- **Staff coverage/build:** PASS. Run 35474547939 passed at exact Staff head
  e67a67585179b7a8dd6b6dc8c81c9fe567f04ef1.
- **Diff hygiene and parity:** PASS. Every checkpoint passed git diff --check;
  final component parity is exact.
- **Review:** No live inline threads. CodeRabbit is skipped/manual for Staff
  and draft-skipped for Market, not an automated full-review approval.
- **Codacy static:** NOT PASS. ACTION_REQUIRED with 1,129 findings.
- **Canonical Pi:** NOT PASS. Run 35474189686 failed before private dispatch
  on HTTP 401 Bad credentials.
- **Durable Sentinel restart:** NOT RUN. No fresh exact-head durable restart
  result exists.

Codacy is not dismissed: 1,076 findings are Markdownlint, 43 are Market
production Lizard reports, eight are RAC-table reports against immutable Market
migrations, and two are dependency-coordinate secret-pattern reports. Broad
component suppression is not authorized.

Staff PR #139 is `DIRTY` because later `main` state records overlap four
`ai-agents` tracking files. No component-source conflict was found. Do not
merge `main` merely to make this parked branch current while the independent
external gates remain non-passing.

## Blocker and exact next action

No private Pi, Paper, or MariaDB runtime ran. The staging owner must rotate or
replace ENTHUSIASTAFF_STAGING_TOKEN with a least-privilege credential able to
read workflow history and dispatch the required private workflow. Do not use a
personal credential or bypass the public bridge.

An authorized, path-scoped Codacy rule or configuration decision is required
for analyzer configuration mismatches. Remaining stateful transaction, auction,
persistence, listener, and GUI findings need a separate bounded remediation and
review plan. When those conditions change, resume these preserved branches,
freeze new exact heads, and rerun hosted, static, review, Sentinel, and
canonical Pi gates before normal merges and post-merge parity.

The owner authorized the pre-existing Bedrock currency-priced shop repair. The
paired change is limited to new form submissions: it replaces the invalid zero
override with the factory's existing validated-price fallback. No existing shop,
balance, item, migration, or Java-menu behavior changed.

No production listing, balance, item, player data, database, deployment,
Discord configuration, authority, LiteBans, cutover, or issue #43 acceptance
changed.
