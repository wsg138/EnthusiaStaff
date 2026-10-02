# ES-X03 active: MarketCase completion and exact-head validation

Date: 2026-09-22

Package: **ES-X03 — EnthusiaMarket destructive provider**

Worker state: **PARTIAL / ACTIONABLE_CONTINUATION**

## Reconciled heads and ownership

- Live Staff `main`: `4ffab626f4f044ef03adcf2d41c25690e1bdaa71`.
- Staff [PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139):
  `f6732816f35e3a9634068badb56c220b5d679bd4`, OPEN/non-draft/UNSTABLE on
  `package/es-x03-market-provider`.
- Market [PR #7](https://github.com/wsg138/EnthusiaMarket/pull/7):
  `81b14c349be0ad404edeedbac5e109e2a375c255`, OPEN/DRAFT/CLEAN on
  `package/es-x03-market-static-remediation`. Preserve unpaired Market PR #6.
- Staff owns branch-local V21; D09 retains V22. Neither Staff-only repair
  changes migration bytes.
- PR #139 is `MERGEABLE`; it is not merged while required acceptance gates are
  non-passing. Current `main` is not merged into the unmerged implementation
  branch merely to refresh it.

## Completed Staff-only repairs

The Folia review found that asynchronous punishment-command results
could send a Player response through the legacy global Bukkit scheduler. The
repair routes `PunishmentCommand` and `PunishmentRequestCommandHandler` through
the existing sender-aware dispatcher. Player replies now run on the sender's
entity scheduler, while console replies remain on the global scheduler.

`CommandResponseDispatcher` centralizes rejection/retirement handling with
`PlayerEntityScheduler`. Regression coverage proves entity-owned Player
responses, rejected Player scheduling, console/global dispatch, and prevents
the punishment handlers from reintroducing legacy Bukkit scheduling.

A subsequent command audit found that `MarketCaseCommand.onTabComplete` read
`arguments[0]` before checking whether the argument array was empty. A normal
zero-argument tab-completion call could therefore throw
`ArrayIndexOutOfBoundsException`. Commit `f6732816` moves the empty-array case
ahead of that access and adds regression coverage asserting the complete action
list is returned.

Neither Staff-only repair changes Market component source or component
metadata, inventories, economy, player data, database state, migrations,
production state, authority, LiteBans, cutover, or deployment state.

## State-publication boundary

This documentation-only follow-up is frozen against executable product head
`f6732816f35e3a9634068badb56c220b5d679bd4`. Its delta is limited to the X03
package record, registry, workspace state, canonical handoff, and latest
handoff. It neither changes executable product inputs nor replaces or merges
either implementation branch. The product validation below remains attributed
to the frozen product head.

## Exact-head evidence

- Local focused `MarketCaseCommandTabCompletionTest`: PASS.
- Local `:paper:check`: PASS.
- Local non-Docker `check -x :integration-tests:test`: PASS.
- Local `runtimeJars` with configuration-cache reuse: PASS.
- Hosted Sentinel artifact `35733465456` / job `106764605492`: PASS on
  `f6732816`.
- Durable Sentinel restart job `516`: PASS (`PAPER_RESTART_OK`) on `f6732816`.
  The standard restart command is comment `5777415687`; the controller bound
  the run to the exact PR head before reporting the terminal result.
- Hosted aggregate Coverage `35733465364` / job `106764602834`: PASS on
  `f6732816`, including runtime JAR creation, aggregate build/tests, coverage,
  and artifact upload. Codacy Diff Coverage and Coverage Variation also passed.
- Clean standalone-to-aggregate comparison: PASS for Staff `f6732816` and
  Market `81b14c3`, with no added, missing, or modified product file and shared
  hash `e7082c5bb1aacbcd95ac8457aa17392a740c3fe5df6154e743eebb4bc6019839`.
- PR #139 has zero live review threads. CodeRabbit is skipped/manual and is not
  treated as automated review approval.

## Non-passing or pending acceptance evidence

- Codacy Static Code Analysis `106765372218` is `ACTION_REQUIRED` with 1,141
  reported issues. The broad historical package result is not suppressed or
  called passing. Its available annotations contain neither changed
  `MarketCaseCommand` file; that narrow result does not turn the overall static
  gate into a pass.
- Canonical Pi `35733463593` / job `106764599729` failed before private
  dispatch when the staging workflow-history request returned HTTP 401 `Bad
  credentials`. No private Pi, Paper, or MariaDB runtime ran. Do not use a
  personal credential or bypass the public bridge.

## Exact next action

Continue only individually validated, behavior-preserving paired static fixes.
The staging owner must rotate or replace `ENTHUSIASTAFF_STAGING_TOKEN` with a
least-privilege credential that can read workflow history and dispatch the
required private workflow. After that material infrastructure change, freeze the
current heads and rerun canonical Pi through the public workflow. Do not use a
broad Codacy suppression or absorb unpaired Market work.
