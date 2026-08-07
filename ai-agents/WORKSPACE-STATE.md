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
| ES-P02 hosted evidence | exact product head `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`; Coverage run `31138550369`, job `92743341861`, success |
| ES-P02 blocker recheck | newest inspected private `plugin-live-test.yml` run `31141797380`; Ubuntu build `92753075216` received runner ID `0`, empty runner name and steps `[]`, with the same Billing & plans payment/spending-limit annotation; Pi `92753100652` skipped |
| ES-P04 status | `ACTIVE` / `ACTIONABLE_CONTINUATION`; implementation and documentation complete; final review repairs applied; exact-head validation and Pi disposition remain |
| ES-P04 branch | `package/es-p04-staff-mode-tools`, created from exact legitimate `main` `5c820c29c2fe5a498ea7f80454579953ac05b436` |
| ES-P04 completed work | authenticated staff-tool dispatch, stale/spoofed/session/rank checks, random teleport suitability, cooldowns, operational command/service reuse, Bedrock text/command fallback, review fixes preventing deferred Cheat Tester issuance and correcting follow/spectate messaging, plus cooldown-boundary coverage |
| ES-P04 next work | freeze this records checkpoint; require exact-head Wiki/Java 21/full-suite/MariaDB-migration/coverage/runtime-JAR/provider-leak/Codacy/CodeRabbit evidence; record exact Pi disposition; then normal merge or blocked-state publication |
| ES-P04 blocker risk | configured private Pi path has repeatedly failed before runner allocation under the private repository Billing & plans restriction; no owner-approved ES-P04 infrastructure exception exists in inspected state |
| ES-P04 handoff | `ai-agents/reports/package-handoffs/2026-08-07-es-p04-staff-mode-tools.md` |
| ES-X05 status | `COMPLETE` |
| ES-X05 frozen validated head | `ab59b8357b8e2eb146b60ff122e316112906746f` |
| ES-X05 finalization merge | PR #74; normal merge `2bcf5d46ca6471fddac600f85020c66105b1c0f2` |
| ES-X05 hosted validation | Coverage run `31140188918`, job `92748299782`, success; CodeRabbit success; Codacy static analysis `92748599134` success; Codacy coverage checks `92749330468` and `92749330613` success |
| ES-X05 parity | aggregate `2bcf5d46ca6471fddac600f85020c66105b1c0f2` equals standalone `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`; hash `780269847698d37c470cb7c241539b1c7387014225cc7eee9598548c9dc97f8b`; parity run `31140896890`, job `92750376952`, artifact `8979748083` |
| ES-X05 staging disposition | **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED** to `ES-V02`; not a pass |
| ES-P03 status | `COMPLETE`; product head `15608bc3099dc34aa080c80ca8e824ffd51cdae4`; merge `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| Migration boundary | immutable V17; ES-P04 adds no migration |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |

## ES-P04 selection and continuation evidence

- Starting legitimate aggregate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Initial live reconciliation proved ES-P02's external blocker unchanged, so ES-P04 was selected as the priority-40 dependency-complete READY package ahead of ES-P09 priority 55.
- Live GitHub now has the single ES-P04 implementation PR #79 on `package/es-p04-staff-mode-tools`; this worker resumed that existing actionable continuation rather than opening competing work.
- ES-P04 is limited to non-excluded staff-mode operational tools and shared safety/recovery behavior. Cheat Tester/fake entities remain ES-P10; fake bases remain ES-P11.
- Implementation and documentation are complete. Review fixes applied after the first green exact-head hosted run prevent the deferred Cheat Tester from being issued at any rank, add inclusive cooldown-boundary tests for 0 and 60000 ms with rejection at -1 and 60001 ms, and remove an incorrect creative-mode assertion from follow/spectate feedback.
- The remaining work is validation and terminal-state disposition only. Do not reopen completed implementation unless a new valid review or test defect appears.

## Preserved completed-package evidence

- ES-X05 recovery started from aggregate `main` `9b1aac2677049ccc71dbddd963831f270c73dcd0` and finalization head `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`.
- Current `main` was reconciled into that branch by normal merge `e9644c14e743f686758ee619ab347cbebe1b21ec`.
- Frozen ES-X05 head `ab59b8357b8e2eb146b60ff122e316112906746f` passed ordinary hosted Coverage/build/test/MariaDB-migration/coverage/runtime-JAR/provider-leak validation, CodeRabbit, and Codacy before PR #74 merged normally.
- Exact post-merge component parity run `31140896890` proved aggregate and standalone trees identical under repository parity rules.
- ES-X05 private/Pi staging remains deferred to ES-V02 and was never called passed.

## Current ES-P04 next action

Freeze this records checkpoint and validate that exact PR #79 head. Require all ordinary hosted/static/review gates, record the configured Pi result without relabeling unavailable infrastructure as success, then either merge PR #79 normally and finalize `COMPLETE` or preserve PR #79 and publish `BLOCKED` / `PARKED_BLOCKED` through the required documentation-only status-publication PR if Pi remains externally unavailable and unapproved.

## Safety boundaries

No production credentials, accounts, punishment/player records, raw addresses, private databases, production routes, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration, shadow window, cutover, ES-V02 execution, or authority activation is authorized or performed. Do not start ES-P09 or any second package during this worker.
