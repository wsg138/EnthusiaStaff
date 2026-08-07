# EnthusiaStaff workspace state

Last updated: 2026-08-07

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01 — Exact-sanction appeal isolation`; `ES-P03 — Bedrock identity correctness`; `ES-P04 — Staff-mode operational tools`; `ES-P09 — Alt and network-identity completion`; `ES-X05 — Website UX, authentication, and appeals` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-P05 — Report evidence and staff workflow completion` |
| Active implementation package | None. ES-P09 reached terminal `COMPLETE`; this worker must stop after terminal-record publication. |
| Ready packages | `ES-P10 — Cheat tester and fake-entity system` at priority 80; unassigned and not activated by the ES-P09 worker |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70; private Actions Billing & plans blocker unchanged |
| ES-P05 status | `BLOCKED` / `PARKED_BLOCKED`; PR #81; implementation/hosted validation complete at `4a38e191395913c6733726e222f0889a2d56d267`; required private staging unavailable under the same Billing & plans condition |
| ES-P05 hosted validation | Wiki `31183192145` / `92881243088` success; Coverage `31183192068` / `92881313210` success on Java 21 including build/tests/MariaDB/Testcontainers/migration checks/coverage/runtime-JAR inspection; artifact `8995826742` digest `sha256:ed87314d5eda8286928ce64f11027240898a0823333c6ffa5aa6d98f1697dbe4`; Codacy static `92882185524` success with zero issues; variation `92882989470` and diff coverage `92882989439` success |
| ES-P05 staging blocker | Public wrapper `31183283525` / `92881545286`; private run `31183290816`; required Ubuntu build `92881577147` runner ID `0`, empty runner name, steps `[]`, Billing & plans failure; Pi `92881591391` skipped. This is not a pass. |
| ES-P04 status | `COMPLETE` under its explicit owner-approved package-specific infrastructure exception for Pi staging |
| ES-P04 merge | PR #79; normal merge `a530b992232a8a08cbbd13b0eed6606228ceb652` |
| ES-P09 status | `COMPLETE`; PR #84; frozen head `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`; normal merge `a88201524690848f778297f140f7ee2ba5b6ce36`; implementation branch deleted |
| ES-P09 hosted validation | Wiki `31193764800` / `92916829444` success; Coverage `31193765341` / `92916907616` success on Java 21 with build/tests/MariaDB-Testcontainers/aggregate JaCoCo/runtime-JAR inspection; Codacy `92917176627` success with zero annotations; repository CodeRabbit status success; zero valid unresolved review threads |
| ES-P09 private staging | **NOT A PASS**. Public wrapper `31193762319`; private run `31193769314`; Ubuntu build `92916864019` runner ID `0`, empty runner name, steps `[]`, Billing & plans rejection; Pi `92916876057` skipped. Private representative-network/distributed acceptance remains `ES-V02`. |
| Migration boundary | V17 remains current and immutable; ES-P09 added no schema migration |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |

## ES-P09 terminal worker record

- Sequential selection occurred only after reconciling current primary `main`, open PRs, package branches, parked ES-P02/ES-P05 state, issue #43, registry/workspace state, the ES-P03 handoff, and the ES-P09 package contract.
- Starting legitimate `main`: `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7`.
- Frozen implementation head: `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`.
- PR #84 merged normally as `a88201524690848f778297f140f7ee2ba5b6ce36`.
- Post-merge containment is exact: `main` is one merge commit ahead of the frozen implementation head, zero behind, with no file differences; the implementation branch no longer exists.
- ES-P09 preserves protected network identity, bounds shared-network/active-sanction scans, suppresses broad-network automation, lowers automatic confidence on simultaneous play, preserves manual decisions, keeps the narrow authoritative inheritance rule only for unambiguous new-account evidence, adds duplicate-evidence throttling and bounded retention from a trusted runtime clock behind the authority fence, and rejects raw address literals from manual audit reasons.
- ES-P03 remains authoritative for Java/Floodgate platform identity; ES-P09 does not redefine it.
- Production/private representative network data, false-positive acceptance, distributed Java/Bedrock staging, production key rotation, and cutover remain outside ES-P09. `ES-V02` owns the deferred private acceptance.

## Exact parked-package boundary

ES-P02 and ES-P05 remain on the same account-level GitHub Actions payment/spending-limit restriction affecting private `wsg138/EnthusiaStaff-Staging`. Do not retry the same zero-runner condition while it is unchanged and do not modify those packages without a newly confirmed actionable continuation.

## Next routing

This sequential worker is terminal after ES-P09 finalization publication and must stop. It does not select or activate `ES-P10`. A future sequential worker must perform a fresh live reconciliation and then follow canonical routing rules.

## Safety boundaries

No production credentials, punishment/player records, raw addresses, private databases, production routes, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration, shadow window, cutover, ES-V02 execution, or authority activation was authorized by ES-P09.