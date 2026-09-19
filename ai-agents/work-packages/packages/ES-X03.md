# `ES-X03` — EnthusiaMarket destructive provider

## 1. Package identity

`ES-X03`; external/multi-repository; primary `COMP-STAFF`; other
`COMP-MARKET`; priority 120; conditional parallelism only without shared
destructive-state overlap.

## 2. Status

`PARTIAL` / `ACTIONABLE_CONTINUATION` under an owner-directed paired
static-remediation continuation on 2026-09-18. The historical D04
serialization blocker is resolved. The private Pi bridge credential remains
non-passing and blocks final acceptance, but safe provider and aggregate static
remediation can proceed.

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
on `package/es-x03-market-provider` remains the aggregate leg. Do not replace,
rebase, force-push, squash, or close it. The new standalone Market
[PR #7](https://github.com/wsg138/EnthusiaMarket/pull/7) on
`package/es-x03-market-static-remediation` is its paired provider leg. Preserve
the separate unpaired Market [PR #6](https://github.com/wsg138/EnthusiaMarket/pull/6)
unchanged.

Market uses only ordinary public repository CI. No private Pi/staging runner
configuration, bridge implementation, credentials, topology, artifact-transfer
mechanism, Sentinel infrastructure, or private evidence may enter Market or
this public repository.

## 8. Current source and pairing checkpoint

- Staff `main` is `5edcb0c2abf49a836422d21cd02fec265b27e7e6`.
- The preserved Staff head before this continuation was
  `879eae12df35253cce6cd12179d5cef1afe95dd9`; it was normally merged with the
  current `main` documentation state before the aggregate mirror changed.
- Market `main` remains `cc19fa966dcb155fa1743f5076fb5152e74bdf8f`.
- Market PR #7 currently carries provider head
  `80cf7c32fdb1bda6242b91b3b82977fafb665e45`.
- The Staff worktree contains the exact Market diff from `cc19fa9` through
  `80cf7c3`, excluding only aggregate-only `COMPONENT-METADATA.md`.
- Staff owns V20, X03 owns V21, and D09 reserves V22. X03 and D09 retain
  disjoint hunks in `PaperCommandRegistrar.java`.

## 9. Remediation included so far

The paired checkpoint upgrades `mkdocs-material` to `9.7.7`, makes all Market
Markdown clean under the default rule set, scopes test-only Detekt and Lizard
complexity checks to test source, and keeps Market SQLite migrations out of the
aggregate SQL Server dialect engines.

It also removes verified parser-facing complexity without changing product
semantics: search predicate parsing, auction config accessors, wiki
front-matter validation, the internal shop-edit draft, shop-creation input
grouping, and web-sync request/configuration/listener helpers. The shop-factory
and shop-edit helper changes affect only internal plugin implementation call
sites; no supported provider API changes.

## 10. Validation record

At provider head `80cf7c32fdb1bda6242b91b3b82977fafb665e45`:

- The CI-equivalent `test shadowJar jacocoTestReport` target passed from a
  fresh Gradle user home with the repository-pinned LumaGuilds 2.1.24 jar.
- `gradlew.bat detekt` passed with that same verified jar.
- `mkdocs build --strict` passed.
- Default Markdown lint passed for all 79 Market Markdown files.
- `git diff --check` passed.

The prior exact-head hosted build failed before compilation because JitPack
timed out fetching a Geyser snapshot it does not publish. The current provider
head resolves OpenCollab, the publisher repository, before JitPack. Fresh
exact-head hosted checks remain required.

## 11. Remaining blockers and non-suppressive boundaries

The continuation does not call static analysis passing yet. Valid production
complexity remains in transaction, auction, persistence, listener, and GUI
paths and must be handled in bounded, tested refactors. The Staff-specific RAC
rule and two dependency-coordinate secret reports require an authorized
path-scoped Codacy rule decision; broad component, migration, security, or
Opengrep exclusion is not authorized.

Canonical Pi remains `NOT PASS`: public supersession run `35168772060` and its
retry failed before private dispatch when the workflow-history request returned
HTTP 401 `Bad credentials`. No private Pi, Paper, or MariaDB runtime ran. The
staging owner must rotate or replace `ENTHUSIASTAFF_STAGING_TOKEN` with a
least-privilege credential that can read and dispatch the required private
Actions workflow. Do not substitute a personal credential or bypass the public
bridge.

## 12. Exact next actions

Review the new exact-head Market hosted result and the Staff static/review
results. Continue valid production-complexity work in small, tested batches.
After the staging owner repairs the private bridge credential, freeze the
resulting heads and rerun the required hosted, static, review, Sentinel, and
canonical Pi gates.

## 13. Completion definition

ES-X03 is complete only after both paired PRs have terminal green required gates,
normal merges, exact post-merge standalone-to-aggregate parity, and updated
component metadata. `ES-V03` retains representative destructive and load
acceptance.

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
