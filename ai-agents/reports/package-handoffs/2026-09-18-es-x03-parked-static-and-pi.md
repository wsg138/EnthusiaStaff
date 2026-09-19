# ES-X03 parked: paired static remediation and Pi bridge authentication

Date: 2026-09-19, updated in place

Package: **ES-X03 — EnthusiaMarket destructive provider**

Terminal worker state: **BLOCKED / PARKED_BLOCKED**

## Routing, scope, and collision result

The owner-directed paired static-remediation continuation preserved Staff
[PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139) on
package/es-x03-market-provider and opened draft Market
[PR #7](https://github.com/wsg138/EnthusiaMarket/pull/7) on
package/es-x03-market-static-remediation. Unpaired Market PR #6 remains
preserved and excluded.

Staff main at reconciliation is
5edcb0c2abf49a836422d21cd02fec265b27e7e6, and Market main remains
cc19fa966dcb155fa1743f5076fb5152e74bdf8f. Staff owns V20, X03 owns V21,
and D09 reserves V22; the one shared registrar-file hunk is disjoint. No
user-owned root-worktree path was changed.

## Frozen paired product heads and parity

- Market PR #7: 9ce978e0782138e97e54576ed4ca009e5b7a0f7c.
- Staff PR #139: fb3b5075f47c697d9475376dd617a0147db3b47e.
- component_sync.py compare: parity true, with no added, missing, or modified
  shared paths.
- Shared product hash:
  801b6a25ce0212834a24dabaeca18c658c7e1506487cfe27b5fdc28b117ed0a9.

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

## Exact-head validation

- **Market local:** PASS. Test, shadow JAR, JaCoCo, and Detekt passed with
  the repository-pinned LumaGuilds artifact.
- **Market hosted:** PASS. Run 35453910972 passed build, tests, shadow JAR,
  MariaDB verification, security, and Detekt.
- **Market Wiki:** PASS. Run 35453911045 passed Markdown, frontmatter, and
  strict MkDocs checks.
- **Staff coverage/build:** PASS. Run 35453933059 passed at frozen Staff head
  fb3b5075.
- **Staff Sentinel artifact:** PASS. Run 35453933038 published exact Paper
  and authority-bridge artifacts.
- **Diff hygiene and parity:** PASS. Every checkpoint passed git diff --check;
  final component parity is exact.
- **Review:** No live inline threads. CodeRabbit is skipped/manual for Staff
  and draft-skipped for Market, not an automated full-review approval.
- **Codacy static:** NOT PASS. ACTION_REQUIRED with 1,129 findings.
- **Canonical Pi:** NOT PASS. Run 35453931981 failed before private dispatch
  on HTTP 401 Bad credentials.
- **Durable Sentinel restart:** NOT RUN. Successful artifact publication is
  not a restart result.

Codacy is not dismissed: 1,076 findings are Markdownlint, 43 are Market
production Lizard reports, eight are RAC-table reports against immutable Market
migrations, and two are dependency-coordinate secret-pattern reports. Broad
component suppression is not authorized.

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

A separate read-only review identified a pre-existing Bedrock currency-priced
shop creation defect. It remains intentionally unmodified because correcting it
changes player-economy behavior and needs explicit owner authorization.

No production listing, balance, item, player data, database, deployment,
Discord configuration, authority, LiteBans, cutover, or issue #43 acceptance
changed.
