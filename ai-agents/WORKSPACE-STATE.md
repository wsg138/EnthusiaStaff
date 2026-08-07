# EnthusiaStaff workspace state

Last updated: 2026-08-06

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01 — Exact-sanction appeal isolation`; `ES-P03 — Bedrock identity correctness`; `ES-X05 — Website UX, authentication, and appeals` |
| Parked package | `ES-P02 — Runtime database recovery and Velocity reload` |
| Newly ready packages | `ES-P04 — Staff-mode operational tools` at priority 40; `ES-P09 — Alt and network-identity completion` at priority 55 |
| Active implementation package | `NONE` — this recovery worker stops after ES-X05 publication and does not start a new package |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; branch `package/es-p02-runtime-db-recovery`; PR #70; records head `99da4103773e0c2ae43e0b0253200cd0d3d2c65c` |
| ES-P02 hosted evidence | exact product head `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`; Coverage run `31138550369`, job `92743341861`, success |
| ES-P02 blocker | private staging run `31139079620`; Ubuntu build job `92744901730` received runner ID `0`, empty runner name, steps `[]`, and GitHub Billing & plans payment/spending-limit failure; Pi job `92744908539` skipped |
| ES-X05 status | `COMPLETE` |
| ES-X05 frozen validated head | `ab59b8357b8e2eb146b60ff122e316112906746f` |
| ES-X05 finalization merge | PR #74; normal merge `2bcf5d46ca6471fddac600f85020c66105b1c0f2` |
| ES-X05 hosted validation | Coverage run `31140188918`, job `92748299782`, success; CodeRabbit success; Codacy static analysis `92748599134` success; Codacy coverage checks `92749330468` and `92749330613` success |
| ES-X05 parity | aggregate `2bcf5d46ca6471fddac600f85020c66105b1c0f2` equals standalone `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`; hash `780269847698d37c470cb7c241539b1c7387014225cc7eee9598548c9dc97f8b`; parity run `31140896890`, job `92750376952`, artifact `8979748083` |
| ES-X05 staging disposition | **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED** to `ES-V02`; not a pass |
| ES-P03 status | `COMPLETE`; product head `15608bc3099dc34aa080c80ca8e824ffd51cdae4`; merge `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| Migration boundary | immutable V17; V1–V17 unchanged by recovery finalization |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |

## ES-X05 completion evidence

- Recovery started from aggregate `main` `9b1aac2677049ccc71dbddd963831f270c73dcd0` and existing finalization head `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`.
- Current `main` was reconciled into the branch by normal merge commit `e9644c14e743f686758ee619ab347cbebe1b21ec`, preserving ES-P03 completion and current ES-P02 state.
- Live standalone PR #3 exposed and fixed a real ES-X05 routing defect by removing page-level middleware that redirected intended public-but-unlinked appeal/reviewer pages. Standalone PR #3 merged as `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`; the same deletion was synchronized into the aggregate component.
- Frozen head `ab59b8357b8e2eb146b60ff122e316112906746f` passed ordinary hosted Coverage/build/test/MariaDB-migration/coverage/runtime-JAR/provider-leak/artifact validation in run `31140188918`, job `92748299782`.
- CodeRabbit succeeded with zero valid unresolved review threads. Codacy static analysis `92748599134` reported zero issues; coverage variation `92749330468` and diff coverage `92749330613` succeeded.
- PR #74 merged normally as `2bcf5d46ca6471fddac600f85020c66105b1c0f2`. Compare from the frozen head to that merge has zero changed files, proving containment and no unique branch work.
- Deterministic post-merge component comparison in run `31140896890`, job `92750376952`, proved exact parity against standalone `main`: added `[]`, missing `[]`, modified `[]`, equal SHA-256 content hash `780269847698d37c470cb7c241539b1c7387014225cc7eee9598548c9dc97f8b`. Artifact `8979748083` stores aggregate/standalone manifests and the parity report.
- Two earlier parity-harness attempts did not invalidate product evidence: run `31140685623` / job `92749749317` failed because a shallow checkout lacked an unrelated parent for `git diff --check`; run `31140785772` / job `92750046294` already produced `parity: true` but the wrapper asserted a wrong JSON key. The corrected run above passed every step.
- Private/Pi staging remains deferred to `ES-V02`. No manual staging retry was requested. PR automation automatically dispatched wrapper `31140187754` / job `92748257022`, then private run `31140197043`; build `92748287250` again received runner ID `0`, empty runner name and steps `[]` with the Billing & plans restriction, and Pi `92748295072` skipped. That is infrastructure-unavailable evidence only, not a pass.

## Next routing after recovery

Outage recovery is reconciled for ES-X05, while ES-P02 remains truthfully parked on an unchanged private billing blocker. The normal sequential package system may resume after this worker stops. `ES-P04` is the expected next new implementation package at priority 40; `ES-P09` is also dependency-ready at priority 55. A later worker must still reclassify live continuations before selection.

## Safety boundaries

No production credentials, accounts, punishment/player records, raw addresses, private databases, production routes, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration, shadow window, cutover, ES-V02 execution, or authority activation is authorized or performed.