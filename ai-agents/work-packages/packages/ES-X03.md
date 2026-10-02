# ES-X03 — EnthusiaMarket destructive provider

## 1. Package identity

**ES-X03**; external/multi-repository; primary **COMP-STAFF**; other
**COMP-MARKET**; priority 120; conditional parallelism only without shared
destructive-state overlap.

## 2. Status

**PARTIAL / ACTIONABLE_CONTINUATION** after the paired static-remediation,
owner-authorized Bedrock currency-pricing repair, and bounded Staff-only Folia
response-routing and zero-argument MarketCase-completion repairs. The
historical D04 serialization blocker is resolved. Fresh parity, exact-head
Coverage, the Sentinel artifact, and durable restart are current, while Codacy
and canonical Pi remain non-passing acceptance gates.

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
  4ffab626f4f044ef03adcf2d41c25690e1bdaa71.
- Market main remains cc19fa966dcb155fa1743f5076fb5152e74bdf8f.
- Market PR #7 product head is
  81b14c349be0ad404edeedbac5e109e2a375c255.
- Staff PR #139 current head is
  f6732816f35e3a9634068badb56c220b5d679bd4. Current `main` is not merged
  merely to refresh the unmerged implementation branch while acceptance gates
  remain non-passing.
- Clean-clone component_sync.py compare reports no added, missing, or modified
  product path. Both product trees have hash
  e7082c5bb1aacbcd95ac8457aa17392a740c3fe5df6154e743eebb4bc6019839.
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

The later Staff-only Folia repair found that asynchronous punishment-command
results could send a Player response through the legacy global Bukkit scheduler.
`PunishmentCommand` and `PunishmentRequestCommandHandler` now use the existing
sender-aware response dispatcher, so Player replies run on the sender's entity
scheduler while console replies remain globally scheduled. Focused regression
coverage proves Player ownership, rejected Player scheduling, console dispatch,
and prevents the punishment handlers from reintroducing legacy Bukkit scheduling.
The Folia repair commit `570304ff` does not change any Market component source
or metadata, destructive market state, inventory, economy, player data,
migration, database, authority, or production state.

The later Staff-only command audit found that `MarketCaseCommand.onTabComplete`
read `arguments[0]` before checking for an empty argument array. A valid
zero-argument completion call could throw `ArrayIndexOutOfBoundsException`.
Commit `f6732816` returns the complete action list before accessing the first
argument and adds regression coverage for that contract. It changes no Market
component source or metadata, destructive market state, inventory, economy,
player data, migration, database, authority, or production state.

## 10. Exact-head validation record

- **Market hosted:** PASS. Run 35601165548 passed build, tests, shadow JAR,
  MariaDB verification, security, and Detekt.
- **Market Wiki:** PASS. Run 35601165577 passed Markdown, frontmatter, and
  strict MkDocs checks.
- **Staff local:** PASS. Focused `MarketCaseCommandTabCompletionTest`,
  `:paper:check`, and non-Docker `check -x :integration-tests:test` passed.
  `runtimeJars` also passed with configuration-cache reuse.
- **Staff coverage/build:** PASS. Run 35733465364 / job 106764602834 passed at
  exact Staff head `f6732816`, including aggregate build/tests, runtime JAR
  inspection, coverage, and artifact upload. Codacy Diff Coverage and Coverage
  Variation also passed.
- **Staff Sentinel artifact:** PASS. Run 35733465456 / job 106764605492 built
  the exact Paper artifact required by Sentinel on `f6732816`.
- **Durable Sentinel restart:** PASS. Exact-head restart job 516 returned
  `PAPER_RESTART_OK` after standard command comment `5777415687`.
- **Diff hygiene and component parity:** PASS. Every paired checkpoint passed
  git diff --check; the final paired hash is
  e7082c5bb1aacbcd95ac8457aa17392a740c3fe5df6154e743eebb4bc6019839.
- **PR review:** No live inline threads. CodeRabbit is skipped/manual for
  Staff and draft-skipped for Market, not an automated full-review approval.
- **Codacy static analysis:** NOT PASS. Exact Staff check 106765372218 is
  ACTION_REQUIRED with 1,141 reported issues.
- **Canonical Pi:** NOT PASS. Staff run 35733463593 / job 106764599729 failed
  before private dispatch on HTTP 401 Bad credentials; no private Pi, Paper,
  or MariaDB runtime ran.

## 11. Static-analysis disposition

The non-passing Codacy result is not suppressed. Exact check 106765372218 is
ACTION_REQUIRED with 1,141 reported issues. The available annotations contain
no entry for either changed `MarketCaseCommand` file; that narrow observation
does not make the overall gate passing. Each current finding requires scoped
verification. A broader suppression or analyzer-rule decision requires explicit
authorization and supported Codacy configuration; it must not be guessed in
source through a component-wide exclusion.

## 12. Current synchronization evidence

The aggregate component and standalone Market product tree are exactly equal
at the current paired heads. Clean-clone comparison found no product-file delta
and the shared hash is
e7082c5bb1aacbcd95ac8457aa17392a740c3fe5df6154e743eebb4bc6019839.
Post-merge parity and component metadata updates remain required.

## 13. Exact unblock condition

Keep both implementation PRs and branches. Continue only small paired repairs
for validated static findings, with matching tests and parity checks; do not
fold unrelated stateful transaction, auction, persistence, listener, or GUI
findings into an unbounded cleanup. The staging owner must rotate or replace
ENTHUSIASTAFF_STAGING_TOKEN with a least-privilege credential that can read
workflow history and dispatch the required private workflow. Do not use a
personal credential or bypass the public bridge. After a changed product head
or credential repair, freeze the heads and rerun every applicable exact hosted,
static, review, Sentinel, and Pi gate.

## 14. Completion definition

ES-X03 is complete only after both paired PRs have terminal green required
gates, normal merges, exact post-merge standalone-to-aggregate parity, and
updated component metadata. ES-V03 retains representative destructive and
load acceptance.

## 15. Handoff and production boundary

Canonical handoff:
ai-agents/reports/package-handoffs/2026-09-22-es-x03-marketcase-completion-validation.md.

No production listing, balance, item, player data, database, deployment,
Discord configuration, authority, LiteBans, cutover, or issue #43 acceptance
changed.
