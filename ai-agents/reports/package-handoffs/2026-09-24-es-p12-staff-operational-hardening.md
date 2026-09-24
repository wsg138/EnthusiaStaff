# `ES-P12` package handoff — 2026-09-24

- Package ID: `ES-P12` — Staff operational hardening
- Canonical status: `COMPLETE`
- Starting Staff SHA: `fd999968ed5ffbd2e47e041482dc9e936528d7a7`
- Frozen executable product head: `b26eca2cd18bbd4148a02e9307fd81ada21a6fd3`
- Final accepted PR head: `e29bbab530f47473d9dbc9d7d60f57ec73dadea6`
- Implementation PR: #246, merged normally as `753ef35496c15abe361cad51704b610af540b0f0`
- Terminal publication PR: #247, documentation only
- Standalone PRs: `NOT_APPLICABLE`

## Completed work

The owner-requested staff operational hardening is complete: staff hierarchy protection, `/staffwho`, freeze/unfreeze staff alerts and frozen-player context, vanish-safe broadcasts/ping presentation, RoseChat-absent PM mute fallback, punishment-ladder context, clean staff-mode exit verification, no-currency item confiscation, and bare-Paper disconnect presence tracking.

Repeated review repaired scheduler/thread-ownership and correctness defects in `/staffwho`, vanished-player ping handling, freeze staff fanout, frozen movement orientation, read-only ban enforcement, disconnect retry behavior, staff-mode recovery fencing, and freeze notice delivery. The final notice path carries the exact freeze runtime generation, preventing release→re-freeze from reviving an older queued notice.

## Final validation

- Repair workflow `35936723820`: Lizard bounds PASS; repository PMD 6.55 PASS; full `:paper:test` PASS; `runtimeJars` PASS; `git diff --check` PASS.
- Freeze-generation fence workflow `35937174002`: Lizard bounds PASS; repository PMD 6.55 PASS; full `:paper:test` PASS; `runtimeJars` PASS; `git diff --check` PASS; published executable `b26eca2cd18bbd4148a02e9307fd81ada21a6fd3`.
- Final normal-actor accepted head `e29bbab530f47473d9dbc9d7d60f57ec73dadea6`: Coverage `35938430400` PASS, including full runtime build/tests, aggregate JaCoCo, runtime-JAR inspection, artifact upload, and Codacy coverage upload.
- Validate Wiki `35938430413`: PASS.
- Sentinel Restart Artifact `35938430464`: PASS.
- Sentinel simulation: PASS, 5/5 cases.
- Pi staging supersession: PASS.
- Codacy static: PASS with zero annotations; seven issues reported solved.
- Final visible review-thread count: zero unresolved.

Earlier failed, skipped, cancelled, superseded, or wrong-head checks remain historical non-passing evidence and were not relabeled as passes.

## Merge and containment

PR #246 merged normally as `753ef35496c15abe361cad51704b610af540b0f0`. Its parents are `fd999968ed5ffbd2e47e041482dc9e936528d7a7` and exact accepted head `e29bbab530f47473d9dbc9d7d60f57ec73dadea6`. The accepted head and merge commit both use tree `5471919009529ec675708d60d49de3cf2ec11bd5`, proving exact containment with no conflict-resolution drift.

Immediately before merge, live `main` was still `fd999968ed5ffbd2e47e041482dc9e936528d7a7` and PR #246 remained mergeable. PR #220's `VanishManager.persistState()` change was rechecked as file-level but hunk-disjoint from ES-P12's visibility/online-count additions. PR #244 had no exact changed-file collision with ES-P12. No migration was added by ES-P12.

## Remaining work

None for ES-P12 implementation, validation, review, or merge. This terminal publication only synchronizes package-state documentation. The former implementation branch is fully contained by `main`; if branch deletion is desired, it is cleanup only and not a package blocker.

## Systems and files not to disturb

Do not absorb ES-X03, Discord program packages, issue #216 repair packages, PR #220's transaction-owned vanish persistence work, or PR #244's mute scheduling repair. Their owners must reconcile against the new `main` independently.

## Private/production boundary

No production deployment, cutover, authority transfer, live player-data mutation, destructive production test, or private-data publication was performed or authorized by ES-P12.
