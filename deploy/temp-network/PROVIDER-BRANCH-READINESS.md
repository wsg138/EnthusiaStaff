# Provider / fork readiness while Staff shadow testing runs

This document separates Enthusia test deployment from upstream Badgers contribution work. None of these upstream PRs are prerequisites for keeping EnthusiaStaff in `SHADOW_MIGRATION`.

## Market

- Existing Enthusia repair PR: `wsg138/EnthusiaMarket#8`.
- Existing Badgers upstream PR: `BadgersMC/EnthusiaMarket#194` from `wsg138/EnthusiaMarket:integration/badgers-staff-market`.
- Do not create a duplicate Badgers PR.
- Staff aggregate Market provider remains open in `wsg138/EnthusiaStaff#139` and must not be merged merely to unblock staging.
- Before deploying a Market candidate to Temp SMP, reconcile the exact Market test candidate with current `wsg138/EnthusiaMarket:main`, run full build/tests/analyzers, and verify the Staff-facing compatibility fixes from PR #8 are present.

## Reputation / EnthusiaCommend

- Enthusia standalone runtime baseline remains `wsg138/EnthusiaCommend:main` until an explicitly selected test PR is validated.
- Existing open PR #23 is test/documentation-only and is not a production candidate by itself.
- Clean upstream preparation branch created from exact Badgers main `f6bd0f56273425b28bb4135e144191b121e6aaca`:
  `wsg138/EnthusiaCommend:upstream/badgers-main-cleanup`
- Do not merge the full Enthusia fork into that branch. Cherry-pick/reimplement only generic, network-independent changes appropriate for Badgers, then run Maven tests/PMD and open one focused upstream PR.

## Currency

- Enthusia standalone runtime baseline remains `wsg138/EnthusiaCurrency:main` until an explicitly selected test candidate is validated.
- Clean upstream preparation branch created from exact Badgers main `3d32bb50ae08024c100b75f099a77309ede370e9`:
  `wsg138/EnthusiaCurrency:upstream/badgers-main-cleanup`
- Do not merge the full Enthusia fork into that branch. Move only generic changes that belong upstream, run the repository's full build/tests/analyzers, and open one focused upstream PR.

## Temp-network provider testing order

Do not install all optional providers simultaneously as the first test. Once core Staff smoke tests pass:

1. Reputation/Commend candidate alone; validate plugin boot, normal rep flows, Staff moderation integration, restart behavior, and no Staff degradation.
2. Currency candidate alone; validate economy flows, Staff moderation provider behavior, restart/recovery, and no Staff degradation.
3. Market candidate last because the Staff aggregate Market provider is still an active package/validation boundary.
4. Re-run `/estaff status` and `/estaff verify full` after each provider is added.

Do not change LiteBans authority or activate Staff cutover while provider testing is in progress.
