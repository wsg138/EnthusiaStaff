# EnthusiaStaff workspace state

Last updated: 2026-08-07

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01 — Exact-sanction appeal isolation`; `ES-P03 — Bedrock identity correctness`; `ES-X05 — Website UX, authentication, and appeals` |
| Parked package | `ES-P02 — Runtime database recovery and Velocity reload` |
| Active implementation package | `ES-P04 — Staff-mode operational tools`; branch `package/es-p04-staff-mode-tools`; starting `main` `5c820c29c2fe5a498ea7f80454579953ac05b436`; generic sequential package worker |
| Other dependency-ready package | `ES-P09 — Alt and network-identity completion` at priority 55; unassigned and not started |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; branch `package/es-p02-runtime-db-recovery`; PR #70; records head `99da4103773e0c2ae43e0b0253200cd0d3d2c65c` |
| ES-P02 hosted evidence | exact product head `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`; Coverage run `31138550369`, job `92743341861`, success |
| ES-P02 blocker recheck | newest inspected private `plugin-live-test.yml` run `31141797380`; Ubuntu build `92753075216` received runner ID `0`, empty runner name and steps `[]`, with the same Billing & plans payment/spending-limit annotation; Pi `92753100652` skipped |
| ES-P04 status | `ACTIVE` / `ACTIONABLE_CONTINUATION`; selected after ES-P02 blocker was proven unchanged |
| ES-P04 branch | `package/es-p04-staff-mode-tools`, created from exact legitimate `main` `5c820c29c2fe5a498ea7f80454579953ac05b436` |
| ES-P04 handoff | `ai-agents/reports/package-handoffs/2026-08-07-es-p04-staff-mode-tools.md` |
| ES-X05 status | `COMPLETE` |
| ES-X05 frozen validated head | `ab59b8357b8e2eb146b60ff122e316112906746f` |
| ES-X05 finalization merge | PR #74; normal merge `2bcf5d46ca6471fddac600f85020c66105b1c0f2` |
| ES-X05 hosted validation | Coverage run `31140188918`, job `92748299782`, success; CodeRabbit success; Codacy static analysis `92748599134` success; Codacy coverage checks `92749330468` and `92749330613` success |
| ES-X05 parity | aggregate `2bcf5d46ca6471fddac600f85020c66105b1c0f2` equals standalone `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`; hash `780269847698d37c470cb7c241539b1c7387014225cc7eee9598548c9dc97f8b`; parity run `31140896890`, job `92750376952`, artifact `8979748083` |
| ES-X05 staging disposition | **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED** to `ES-V02`; not a pass |
| ES-P03 status | `COMPLETE`; product head `15608bc3099dc34aa080c80ca8e824ffd51cdae4`; merge `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| Migration boundary | immutable V17; no ES-P04 migration is currently expected |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |

## ES-P04 selection evidence

- Starting legitimate aggregate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Live PR reconciliation found only ES-P02 PR #70 open; no ES-P04 branch or competing work existed.
- ES-P02 retained priority for continuation only if its exact unblock condition changed. It did not: private workflow `31141797380` again failed before ordinary Ubuntu runner allocation in job `92753075216`, with runner ID `0`, empty runner name, zero steps, and GitHub's explicit Billing & plans restriction; the Pi job was skipped. No redundant rerun was requested.
- ES-P04 is dependency-complete through ES-P03 and has priority 40, ahead of ES-P09 priority 55. It was therefore selected as the next `READY` package and claimed on the required branch.
- The package is limited to non-excluded staff-mode operational tools and their safety/recovery behavior. Cheat testers/fake entities and fake bases remain assigned to later packages.

## Preserved completed-package evidence

- ES-X05 recovery started from aggregate `main` `9b1aac2677049ccc71dbddd963831f270c73dcd0` and finalization head `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`.
- Current `main` was reconciled into that branch by normal merge `e9644c14e743f686758ee619ab347cbebe1b21ec`.
- Frozen ES-X05 head `ab59b8357b8e2eb146b60ff122e316112906746f` passed ordinary hosted Coverage/build/test/MariaDB-migration/coverage/runtime-JAR/provider-leak validation, CodeRabbit, and Codacy before PR #74 merged normally.
- Exact post-merge component parity run `31140896890` proved aggregate and standalone trees identical under repository parity rules.
- ES-X05 private/Pi staging remains deferred to ES-V02 and was never called passed.

## Current ES-P04 next action

Map the existing tagged tool IDs to the current command/managers and service-boundary permissions, inspect listener registration/configuration/tests, then implement one coherent dispatcher and stale/spoofed-tool safety slice. Checkpoint all durable package state before broader tool integration.

## Safety boundaries

No production credentials, accounts, punishment/player records, raw addresses, private databases, production routes, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration, shadow window, cutover, ES-V02 execution, or authority activation is authorized or performed. Do not start ES-P09 or any second package during this worker.
