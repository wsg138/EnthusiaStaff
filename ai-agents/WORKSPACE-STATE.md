# EnthusiaStaff workspace state

Last updated: 2026-08-07

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01 — Exact-sanction appeal isolation`; `ES-P03 — Bedrock identity correctness`; `ES-X05 — Website UX, authentication, and appeals` |
| Parked package | `ES-P02 — Runtime database recovery and Velocity reload` |
| Active implementation package | `ES-P04 — Staff-mode operational tools`; branch `package/es-p04-staff-mode-tools`; PR #79 ready for review; starting `main` `5c820c29c2fe5a498ea7f80454579953ac05b436`; generic sequential package worker |
| ES-P04 intended terminal outcome | `COMPLETE` only after every required exact-head gate and normal merge; otherwise `BLOCKED` / `PARKED_BLOCKED` if the configured Pi gate remains unavailable without an owner-approved ES-P04 infrastructure exception |
| Other dependency-ready package | `ES-P09 — Alt and network-identity completion` at priority 55; unassigned and not started |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; branch `package/es-p02-runtime-db-recovery`; PR #70; records head `99da4103773e0c2ae43e0b0253200cd0d3d2c65c` |
| ES-P02 blocker recheck | private run `31141797380`; Ubuntu build `92753075216` received runner ID `0`, empty runner name and steps `[]`, with the Billing & plans payment/spending-limit annotation; Pi `92753100652` skipped |
| ES-P04 status | `ACTIVE` / `ACTIONABLE_CONTINUATION`; implementation/documentation complete; all identified review findings dispositioned; final exact-head validation and terminal publication remain |
| ES-P04 latest product/test head | `7642b7fd56e01cfc678ac615d5fd32a18bd32bc1`; review fixes plus Codacy-compliant cooldown-boundary tests |
| ES-P04 completed work | authenticated staff-tool dispatch, stale/spoofed/session/rank checks, random teleport suitability, cooldowns, operational command/service reuse, Bedrock text/command fallback, deferred Cheat Tester exclusion, corrected follow/spectate feedback, and inclusive cooldown-boundary coverage |
| ES-P04 exact-head staging evidence so far | superseded records head `7c32b823919fc276f5b8c40a80a5de2893956564` dispatched private staging run `31177702607`; required Ubuntu job `92863344441` had runner ID `0`, empty runner name, steps `[]`, explicit Billing & plans failure; Pi `92863355649` skipped |
| ES-P04 next work | freeze the records checkpoint produced by this state reconciliation; require exact-head Wiki/Java 21/full-suite/MariaDB-migration/coverage/runtime-JAR/provider-leak/Codacy/CodeRabbit evidence and exact Pi disposition; then normal merge or blocked-state publication |
| ES-P04 blocker risk | configured private Pi path remains externally unavailable before product execution under the private repository Billing & plans restriction; no owner-approved ES-P04 infrastructure exception exists in inspected state |
| ES-P04 handoff | `ai-agents/reports/package-handoffs/2026-08-07-es-p04-staff-mode-tools.md` |
| Migration boundary | immutable V17; ES-P04 adds no migration |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |

## ES-P04 continuation evidence

- Starting legitimate aggregate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- ES-P02's external blocker was unchanged, so ES-P04 was selected as priority-40 dependency-complete work ahead of ES-P09 priority 55.
- Live GitHub exposed existing PR #79, so this worker resumed that actionable continuation rather than opening competing work.
- Scope remains limited to non-excluded staff-mode operational tools and shared safety/recovery behavior. Cheat Tester/fake entities remain ES-P10; fake bases remain ES-P11.
- All CodeRabbit threads identified through records head `7c32b823919fc276f5b8c40a80a5de2893956564` were resolved or explicitly withdrawn as non-blocking. The scheduler-helper consolidation was acknowledged by CodeRabbit as optional maintainability follow-up after the concrete compile defect was fixed.
- Exact-head `7c32...` Wiki passed, while its private staging route failed before runner allocation due Billing & plans. Codacy then reported five test-structure warnings in the new boundary test; `7642b7fd56e01cfc678ac615d5fd32a18bd32bc1` restructures those tests with explicit assertions and no loop allocations. Because that changed the branch, all final gates must run again on the records head produced after this update.

## Current ES-P04 next action

Freeze the resulting PR #79 head and validate that exact SHA. Require every ordinary hosted/static/review gate and record the configured Pi result without relabeling unavailable infrastructure as success. If every gate passes, merge normally and publish `COMPLETE`. If ordinary gates pass but Pi again fails before product execution under the same unapproved Billing & plans restriction, preserve PR #79 and its branch and publish `BLOCKED` / `PARKED_BLOCKED` to `main` through the required documentation-only status-publication PR.

## Safety boundaries

No production credentials, accounts, punishment/player records, raw addresses, private databases, production routes, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration, shadow window, cutover, ES-V02 execution, or authority activation is authorized or performed. Do not start ES-P09 or any second package during this worker.
