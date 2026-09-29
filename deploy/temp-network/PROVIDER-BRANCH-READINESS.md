# Provider / fork readiness while Staff shadow testing runs

This document separates Enthusia test deployment from upstream Badgers contribution work. None of these upstream PRs are prerequisites for keeping EnthusiaStaff in `SHADOW_MIGRATION`.

## Staff aggregate provider state

- Currency package `ES-X02` is complete, merged, and synchronized. Staff PR #138 published the corrected terminal state after the executable correction in #137; no new Currency provider implementation is required for Temp testing.
- Reputation/Commend package `ES-X04` is complete, merged, and synchronized. Staff PR #162 published the terminal synchronized state after Staff PR #152 and Commend PR #12 merged normally; no new Rep provider implementation is required for Temp testing.
- Market package remains the only provider family here with an open Staff-side implementation boundary: `wsg138/EnthusiaStaff#139`. Do not merge it merely to make the Temp environment look complete; reconcile and validate that package independently.

## Market

- Enthusia repair PR: `wsg138/EnthusiaMarket#8` remains the repair/history boundary for the Muse compatibility work unless live GitHub shows it merged/closed later.
- Badgers upstream PR `BadgersMC/EnthusiaMarket#194` is already merged. Do not create a duplicate upstream Market PR.
- Staff aggregate Market provider remains open in `wsg138/EnthusiaStaff#139` and must not be merged merely to unblock staging.
- Before deploying a Market candidate to Temp SMP, reconcile the exact Market test candidate with current `wsg138/EnthusiaMarket:main`, run full build/tests/analyzers, and verify the Staff-facing compatibility fixes from PR #8 are present.

## Reputation / EnthusiaCommend

- Enthusia standalone runtime baseline remains `wsg138/EnthusiaCommend:main` until an explicitly selected newer test PR is validated.
- Staff-side reputation moderation/provider integration is already complete through ES-X04; provider testing should validate the current standalone runtime against the already-merged Staff API rather than rebuilding ES-X04.
- Clean upstream preparation branch starts from exact Badgers main `f6bd0f56273425b28bb4135e144191b121e6aaca`:
  `wsg138/EnthusiaCommend:upstream/badgers-main-cleanup`
- A focused candidate branch also exists for the still-unupstreamed private-rep-chat defect:
  `wsg138/EnthusiaCommend:upstream/fix-private-rep-chat`
  Keep it narrow; do not import the full downstream GUI/Staff/Plan history.
- Do not merge the full Enthusia fork into the Badgers-base branches. Reimplement only generic, network-independent changes appropriate for Badgers, then run Maven tests/static analysis and open focused upstream PRs.

## Currency

- Enthusia standalone runtime baseline remains `wsg138/EnthusiaCurrency:main` for our network.
- Staff-side destructive Currency moderation/provider integration is already complete through ES-X02; provider testing should validate that current standalone runtime against the already-merged Staff API.
- Clean upstream preparation branch starts from exact Badgers main `3d32bb50ae08024c100b75f099a77309ede370e9`:
  `wsg138/EnthusiaCurrency:upstream/badgers-main-cleanup`
- Focused upstream candidate `wsg138/EnthusiaCurrency:upstream/fix-vault-startup-lifecycle` is one squashed commit on top of that Badgers base. Internal validation PR #19 exists only to run fork CI/hosted analysis before an upstream Badgers PR is opened.
- Exact squashed candidate SHA: `798bf7d280c386115c63dcf0534e07134eecf181`.
- Java 21 `mvn -B -ntp verify` is green on the exact squashed head. Codacy currently returns `action_required` for internal PR #19 while reporting zero inline annotations and an empty summary; do not call that gate green or suppress it. The actual upstream PR must receive a concrete hosted result on Badgers before merge.
- The connected GitHub App cannot open the cross-repository Badgers Currency PR (403). Once the candidate is otherwise ready, the owner or an authorized browser/Codex session must open the PR from `wsg138:upstream/fix-vault-startup-lifecycle` to `BadgersMC:main`.

## Temp-network provider testing order

Do not install all optional providers simultaneously as the first test. Once core Staff smoke tests pass:

1. Reputation/Commend current standalone candidate alone; validate plugin boot, normal rep flows, Staff moderation integration, restart behavior, and no Staff degradation.
2. Currency current standalone candidate alone; validate economy flows, ES-X02 provider behavior, restart/recovery, and no Staff degradation.
3. Market candidate last because Staff PR #139 remains an active package/validation boundary.
4. Re-run `/estaff status` and `/estaff verify full` after each provider is added.

Do not change LiteBans authority or activate Staff cutover while provider testing is in progress.
