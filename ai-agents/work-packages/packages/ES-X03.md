# ES-X03 — EnthusiaMarket destructive provider

## 1. Package identity

**ES-X03**; external/multi-repository; primary **COMP-STAFF**; other
**COMP-MARKET**; priority 120; conditional parallelism only without shared
destructive-state overlap.

## 2. Status

**BLOCKED / PARKED_BLOCKED** after the paired static-remediation and
owner-authorized Bedrock currency-pricing repair on 2026-09-19. The historical
D04 serialization blocker is resolved. Exact parity is complete, but current
required Codacy and canonical Pi gates remain non-passing.

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

Staff [PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139) on
package/es-x03-market-provider remains the aggregate leg. Market
[PR #7](https://github.com/wsg138/EnthusiaMarket/pull/7) on
package/es-x03-market-static-remediation remains the paired draft provider
leg. Preserve the separate unpaired Market
[PR #6](https://github.com/wsg138/EnthusiaMarket/pull/6) unchanged.

Market uses only ordinary public repository CI. No private Pi or staging
runner configuration, bridge implementation, credentials, topology,
artifact-transfer mechanism, Sentinel infrastructure, or private evidence may
enter Market or this public repository.

## 8. Frozen paired source checkpoint

- Staff main at current reconciliation is
  d4b5d44d6b88126f9663974d892e3e8b6aac5b9d.
- Market main remains cc19fa966dcb155fa1743f5076fb5152e74bdf8f.
- Market PR #7 frozen product head is
  5b6606c2f71a410ed6f369b0b893a7888638a7f2.
- Staff PR #139 frozen product head is
  e67a67585179b7a8dd6b6dc8c81c9fe567f04ef1.
- component_sync.py compare reports no added, missing, or modified shared
  paths. Both product trees have hash
  6ba7be19e647b9093bb9670b79026585eb5306f83e66480894fed3912b1f96f7.
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

## 10. Exact-head validation record

- **Market local:** PASS. Test, shadow JAR, JaCoCo, and Detekt passed with
  the repository-pinned LumaGuilds artifact.
- **Market hosted:** PASS. Run 35474189763 passed build, tests, shadow JAR,
  MariaDB verification, security, and Detekt.
- **Market Wiki:** PASS. Run 35474189668 passed Markdown, frontmatter, and
  strict MkDocs checks.
- **Staff local:** PASS. Full component test, shadow JAR, JaCoCo, and Detekt
  passed with the repository-pinned LumaGuilds artifact.
- **Staff coverage/build:** PASS. Run 35474547939 passed at the exact Staff
  head.
- **Diff hygiene and component parity:** PASS. Every paired checkpoint passed
  git diff --check; the final paired hash is
  6ba7be19e647b9093bb9670b79026585eb5306f83e66480894fed3912b1f96f7.
- **PR review:** No live inline threads. CodeRabbit is skipped/manual for
  Staff and draft-skipped for Market, not an automated full-review approval.
- **Codacy static analysis:** NOT PASS. Staff is ACTION_REQUIRED with 1,129
  findings.
- **Canonical Pi:** NOT PASS. Staff run 35474189686 failed before private
  dispatch on HTTP 401 Bad credentials.
- **Durable Sentinel restart:** NOT RUN. No fresh exact-head durable restart
  result exists.

## 11. Static-analysis disposition

The non-passing Codacy result is not suppressed. It contains 1,076
Markdownlint reports, 43 Market production Lizard reports, eight RAC-table
reports against immutable Market migrations, and two dependency-coordinate
secret-pattern reports. The narrow source, test, and dialect scopes remain in
place. A broader suppression or an analyzer-rule decision requires explicit
authorization and supported Codacy configuration; it must not be guessed in
source through a component-wide exclusion.

## 12. Current synchronization evidence

The aggregate component and standalone Market product tree are exactly equal
before merge. The final shared hash is
6ba7be19e647b9093bb9670b79026585eb5306f83e66480894fed3912b1f96f7.
Post-merge parity and component metadata updates remain required.

## 13. Exact unblock condition

Keep both implementation PRs and branches. The staging owner must rotate or
replace ENTHUSIASTAFF_STAGING_TOKEN with a least-privilege credential that can
read workflow history and dispatch the required private workflow. Do not use a
personal credential or bypass the public bridge.

An authorized, path-scoped Codacy configuration or rule decision is also
required for analyzer configuration mismatches. Remaining stateful
transaction, auction, persistence, listener, and GUI findings need a separate
bounded remediation and review plan; do not fold them into an unbounded static
cleanup. After those conditions change, freeze new heads and rerun all exact
hosted, static, review, Sentinel, and Pi gates.

## 14. Completion definition

ES-X03 is complete only after both paired PRs have terminal green required
gates, normal merges, exact post-merge standalone-to-aggregate parity, and
updated component metadata. ES-V03 retains representative destructive and
load acceptance.

## 15. Handoff and production boundary

Canonical handoff:
ai-agents/reports/package-handoffs/2026-09-18-es-x03-parked-static-and-pi.md.

No production listing, balance, item, player data, database, deployment,
Discord configuration, authority, LiteBans, cutover, or issue #43 acceptance
changed.
