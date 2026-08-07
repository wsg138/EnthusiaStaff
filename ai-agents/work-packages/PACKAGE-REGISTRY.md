# Package registry

Last updated: 2026-08-07

Canonical current state: `ES-P01`, `ES-P03`, and `ES-X05` are `COMPLETE`; `ES-P02` is `BLOCKED` / `PARKED_BLOCKED`; `ES-P04` is `ACTIVE` / `ACTIONABLE_CONTINUATION` on PR #79 with implementation, documentation, review repairs, and test-quality cleanup complete; only final exact-head validation, Pi disposition, and terminal publication remain. `ES-P09` remains dependency-derived `READY` and unassigned. Issue #43 remains open, deferred, and excluded.

## Rules

- Live GitHub overrides stale text and must be reconciled back here.
- Classify every incomplete package before selection; select one package and stop after its terminal state is persistently published.
- Existing actionable continuation work takes priority over new implementation work.
- Park unchanged external blockers; do not repeat identical zero-runner attempts without a material condition change.
- Use normal merge commits only. Missing, skipped, cancelled, superseded, wrong-revision, billing-blocked, or zero-runner gates are not passes.
- Owner-directed exceptions must be explicit and package-specific.

## Canonical package index

| ID | Title | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | ---: | --- | --- |
| `ES-P01` | Exact-sanction appeal isolation | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-P02` | Runtime database recovery and Velocity reload | `BLOCKED` | `PARKED_BLOCKED` | 20 | `ES-P01` | PR #70; unchanged private Actions Billing & plans blocker |
| `ES-P03` | Bedrock identity correctness | `COMPLETE` | — | 30 | ordinarily `ES-P02`; owner-directed narrow exception | merged PR #75 as `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| `ES-X05` | Website UX, authentication, and appeals | `COMPLETE` | — | 35 | `ES-P01` | merged PR #74 as `2bcf5d46ca6471fddac600f85020c66105b1c0f2` |
| `ES-P04` | Staff-mode operational tools | `ACTIVE` | `ACTIONABLE_CONTINUATION` | 40 | `ES-P03` | generic sequential worker; branch `package/es-p04-staff-mode-tools`; PR #79; latest product/test head `7642b7fd56e01cfc678ac615d5fd32a18bd32bc1`; final records head is the last commit from this checkpoint |
| `ES-P07` | Inventory and Ender editing runtime completion | `PLANNED` | — | 45 | `ES-P02` | dependency blocked |
| `ES-P05` | Report evidence and staff workflow completion | `PLANNED` | — | 50 | `ES-P03`, `ES-P04` | unassigned |
| `ES-P09` | Alt and network-identity completion | `READY` | `READY` | 55 | `ES-P03` | unassigned; must not be started by ES-P04 worker |
| `ES-P06` | Discord notification delivery completion | `PLANNED` | — | 60 | `ES-P05` | unassigned |
| `ES-P08` | Item confiscation and restoration | `PLANNED` | — | 70 | `ES-P07` | unassigned |
| `ES-P10` | Cheat tester and fake-entity system | `PLANNED` | — | 80 | `ES-P04` | explicitly excluded from ES-P04 |
| `ES-P11` | Fake-base generation and cleanup | `PLANNED` | — | 90 | `ES-P10` | unassigned |
| `ES-X01` | RoseChat provider and communication integration | `PLANNED` | — | 100 | `ES-P03`, `ES-P04`, `ES-P05` | unassigned |
| `ES-X02` | EnthusiaCurrency destructive provider | `PLANNED` | — | 110 | `ES-P08` | unassigned |
| `ES-X03` | EnthusiaMarket destructive provider | `PLANNED` | — | 120 | `ES-P08`, `ES-X02` | unassigned |
| `ES-X04` | EnthusiaCommend reputation provider | `PLANNED` | — | 125 | `ES-P08`, `ES-X02` | unassigned |
| `ES-V01` | Private LiteBans representative-data verification | `DEFERRED` | — | 200 | — | private/local environment required |
| `ES-V02` | Distributed and Java/Bedrock staging | `DEFERRED` | — | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | private validation |
| `ES-V03` | Destructive, latency, and load acceptance | `DEFERRED` | — | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | private validation |
| `ES-A01` | LiteBans cutover acceptance | `DEFERRED` | — | 300 | `ES-V01`, `ES-V02`, `ES-V03` | owner authorization and issue #43 required |
| `ES-QA01` | Final repository and workflow audit | `PLANNED` | — | 400 | `ES-A01` | unassigned |

## `ES-P02`

- Remains `BLOCKED` / `PARKED_BLOCKED` on PR #70.
- Latest inspected private build `92753075216` received runner ID `0`, empty runner name, steps `[]`, and the Billing & plans payment/spending-limit rejection; Pi `92753100652` skipped.
- Unblock: resolve that account-level restriction, then resume and rerun all required exact-head gates.

## `ES-P04`

- Starting legitimate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Branch/PR: `package/es-p04-staff-mode-tools`; #79; mergeable at the latest pre-checkpoint reconciliation.
- Implementation scope is complete for random teleport, inspector, freeze, reports, follow/spectate, vanish, staff chat, and tools menu. Cheat Tester stays deferred to `ES-P10`; fake bases stay `ES-P11`.
- Review repairs: deferred Cheat Tester is unavailable for every rank; cooldown settings are tested at 0/60000 accepted and -1/60001 rejected; follow/spectate no longer makes a false creative-mode claim. Every review thread through records head `7c32b823919fc276f5b8c40a80a5de2893956564` was resolved; CodeRabbit explicitly withdrew scheduler-helper consolidation as optional maintainability follow-up.
- Superseded green hosted evidence: records head `4cc0178a8f908c43aafc6a88cf3014e81f5756f2` passed Wiki `31173610733` / `92850645081`, Coverage `31173610764` / `92850703180`, Codacy static `92850885117`, variation `92852019542`, and diff coverage `92852019707`.
- Later records head `7c32b823919fc276f5b8c40a80a5de2893956564` proved the exact private gate remained externally unavailable: staging run `31177702607`; Ubuntu build `92863344441` runner ID `0`, empty runner name, zero steps, explicit Billing & plans failure; Pi `92863355649` skipped. This is not a pass.
- Codacy on `7c32...` then reported five test-structure warnings in the newly added boundary test. Commit `7642b7fd56e01cfc678ac615d5fd32a18bd32bc1` replaces loop/no-assert structure with explicit assertions and no loop allocations. All final gates therefore must be rerun on the records head produced by this checkpoint.
- No owner-approved ES-P04 infrastructure exception exists. V17 remains immutable; no migration was added.
- Exact next action: validate the final frozen PR #79 head. If every gate including Pi succeeds, normal-merge PR #79 and publish `COMPLETE`. If ordinary gates succeed but Pi again fails before product execution under the unapproved Billing & plans restriction, preserve PR #79/branch and publish `BLOCKED` / `PARKED_BLOCKED` on `main` via a documentation-only status-publication PR.

## Next-worker boundary

This worker owns only ES-P04. ES-P09 remains `READY` but unassigned. Do not start it during this worker.
