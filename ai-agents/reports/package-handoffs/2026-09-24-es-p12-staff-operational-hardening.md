# `ES-P12` package handoff — 2026-09-24

- Package ID: `ES-P12` — Staff operational hardening
- Canonical status: `VALIDATING / ACTIONABLE_CONTINUATION`
- Assigned worker/channel: current coding worker continuing PR #246
- Starting SHAs by repository: EnthusiaStaff `fd999968ed5ffbd2e47e041482dc9e936528d7a7`
- Active temporary branches: `package/es-p12-staff-operational-hardening`
- EnthusiaStaff PR: #246, open, non-draft, normal merge only
- Standalone PRs: `NOT_APPLICABLE`
- Latest pushed heads: executable product `b26eca2cd18bbd4148a02e9307fd81ada21a6fd3`; synchronized package/registry state `70fc26ffc730593b81ad9255f13f997d11d20b14`; this handoff commit is the normal-actor exact-head acceptance trigger.

## Completed work

The owner-requested staff operational hardening is implemented: staff hierarchy protection, `/staffwho`, freeze/unfreeze staff alerts and frozen-player context, vanish-safe broadcasts/ping presentation, RoseChat-absent PM mute fallback, punishment-ladder context, clean staff-mode exit verification, no-currency item confiscation, and bare-Paper disconnect presence tracking.

Repeated code review also repaired the scheduler/thread-ownership and correctness gaps discovered after the original implementation. The final executable tree filters `/staffwho` through the viewer/target vanish matrix, keeps ban login enforcement active in `READ_ONLY_FAILURE`, preserves requested yaw/pitch while blocking frozen-player translation, retries rejected disconnect presence work through the bounded retry path, retains the staff-mode recovery fence after failed restoration, performs freeze chat/alert player operations on entity schedulers, and wires frozen-player notices to authoritative freeze verification.

The final freeze-notice race was tightened again so queued notices carry the exact freeze runtime generation. A release followed by a later re-freeze cannot make an old queued notice appear current merely because the player is frozen again.

## Incomplete work

Exact-head repository acceptance, Codacy, external review reconciliation, and final concurrent-path reconciliation remain. The PR must not merge until the normal-actor handoff head is terminal under the package's required checks and all valid review threads are dispositioned.

## Tests and static analysis

- Freeze-alert ownership repair workflow `35934026437`: Lizard 1.23 bounds PASS; repository PMD 6.55 PASS with zero findings; full `:paper:test` PASS; `git diff --check` PASS; published `12397f91da774f61b4bd2f023eba8cf876afd23f`.
- Broad review-repair workflow `35936723820`: Lizard bounds PASS; repository PMD 6.55 PASS; full `:paper:test` PASS; `runtimeJars` PASS; `git diff --check` PASS; published `34faf6763ad6669c2242716d0714260b92b9edc0`.
- Exact freeze-generation fence workflow `35937174002`: Lizard bounds PASS; repository PMD 6.55 PASS; full `:paper:test` PASS; `runtimeJars` PASS; `git diff --check` PASS; published executable product head `b26eca2cd18bbd4148a02e9307fd81ada21a6fd3`.
- Package/registry synchronizer `35937538358`: unique-target assertions PASS; docs-only changed-path assertion PASS; `git diff --check` PASS; published state head `70fc26ffc730593b81ad9255f13f997d11d20b14` and removed its temporary workflow.

## Failed, skipped, cancelled, or superseded checks

Earlier green acceptance on executable head `91dccbe018484d055563278857b0bf6be0f8cae8` is historical only because later executable repairs changed the tree. Intermediate review-repair workflow failures remain non-passing diagnostic history: they exposed PMD test-literal cleanup, a `FreezeCommand` functional-interface constructor ambiguity, and brittle temporary harness matching before the final repair workflow passed. They are not relabeled as passes.

Bot-authored executable heads can produce PR workflows marked `action_required` or otherwise not run; those are not acceptance evidence. This normal-actor handoff commit exists specifically to obtain fresh exact-head repository evidence.

## Valid review findings and fixes

The final CodeRabbit/manual review batch found valid issues in package-state synchronization, `/staffwho` vanished-staff visibility, read-only ban enforcement, frozen movement orientation, freeze-notice race handling, freeze staff fanout ownership, disconnect persistence on queue rejection, and staff-mode failed-exit recovery fencing. All corresponding product fixes are present in the executable tree. Additional manual review found that `FreezeNoticeService` was initially unwired and that FreezeManager chat/alert fanout still crossed Folia ownership boundaries; those were repaired and regression-tested as part of the same bounded review work.

A final follow-up on the notice race found that a boolean/currently-frozen check still could not distinguish an old freeze from a later re-freeze. `b26eca2c...` carries the exact runtime generation through notice delivery and is the product head to validate.

## Remaining review threads

Product-code threads are fixed in the current tree but must be reconciled against the current exact head before resolution. The package-state synchronization thread is addressed by `ES-P12.md`, `PACKAGE-REGISTRY.md`, and this canonical handoff. Do not treat an old review submitted against `91dccbe...` as approval of the final executable tree.

## Synchronization/parity

Standalone-provider parity is `NOT_APPLICABLE`. Branch base and last reconciled live `main` are `fd999968ed5ffbd2e47e041482dc9e936528d7a7`. PR #220 also edits `VanishManager`; its transaction-owned `persistState()` repair is file-level but hunk-disjoint from ES-P12's visibility/online-count additions. Both changes must survive whichever PR merges second. No ES-P12 migration exists.

## Blocker evidence

There is no external product blocker. Remaining blockers are validation-state blockers only: terminal exact-head hosted checks, zero-new-valid-finding Codacy evidence, current-head review reconciliation, and a final live-main/open-PR ownership check.

## Exact next action

Treat this handoff commit as the normal-actor acceptance head. Wait for and inspect exact-head Coverage/build/tests/runtime-JAR, Sentinel artifact, Wiki validation, Codacy static/coverage results, Pi staging/supersession controls, and CodeRabbit/manual review. Resolve only findings that reproduce on the current head. Re-read live `main` and active overlapping PRs before merge. If executable code changes again, freeze the new product head and repeat exact-head acceptance; do not reuse stale evidence.

## Systems and files not to disturb

Do not absorb ES-X03, Discord program packages, issue #216 repair packages, or PR #220's transaction-owned vanish persistence work. Preserve the active mute scheduling repair's listener/dispatcher ownership. Do not renumber or create migrations for ES-P12 without first reconciling the live migration ceiling.

## Private/production boundary

No production deployment, cutover, authority transfer, live player-data mutation, destructive production test, or private-data publication is authorized by this package. Staging/manual evidence may supplement but does not replace exact-head repository validation.
