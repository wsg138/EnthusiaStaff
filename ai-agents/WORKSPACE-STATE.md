# EnthusiaStaff workspace state

Last updated: 2026-08-07

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01 — Exact-sanction appeal isolation`; `ES-P03 — Bedrock identity correctness`; `ES-P04 — Staff-mode operational tools`; `ES-X05 — Website UX, authentication, and appeals` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-P05 — Report evidence and staff workflow completion` |
| Active implementation package | `ES-P09 — Alt and network-identity completion`; branch `package/es-p09-alt-network-identity`; selected 2026-08-07 from legitimate `main` `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7` |
| Ready packages | `ES-P10 — Cheat tester and fake-entity system` at priority 80; unassigned while ES-P09 is active |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70; private Actions Billing & plans blocker unchanged |
| ES-P05 status | `BLOCKED` / `PARKED_BLOCKED`; PR #81; implementation/hosted validation complete at `4a38e191395913c6733726e222f0889a2d56d267`; required private staging unavailable under the same Billing & plans condition |
| ES-P05 hosted validation | Wiki `31183192145` / `92881243088` success; Coverage `31183192068` / `92881313210` success on Java 21 including build/tests/MariaDB/Testcontainers/migration checks/coverage/runtime-JAR inspection; artifact `8995826742` digest `sha256:ed87314d5eda8286928ce64f11027240898a0823333c6ffa5aa6d98f1697dbe4`; Codacy static `92882185524` success with zero issues; variation `92882989470` and diff coverage `92882989439` success |
| ES-P05 staging blocker | Public wrapper `31183283525` / `92881545286`; private run `31183290816`; required Ubuntu build `92881577147` runner ID `0`, empty runner name, steps `[]`, Billing & plans failure; Pi `92881591391` skipped. This is not a pass. |
| ES-P05 review | Harsh self-review completed with three pre-freeze fixes; zero inline review threads; CodeRabbit quota-limited and must rerun when package resumes |
| ES-P04 status | `COMPLETE` under an explicit owner-approved package-specific infrastructure exception for Pi staging |
| ES-P04 validated head | `15d9428eba454e9ae4a905752129bd18676acdb1` |
| ES-P04 merge | PR #79; normal merge `a530b992232a8a08cbbd13b0eed6606228ceb652` |
| ES-P04 staging disposition | **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — PI STAGING SKIPPED/DEFERRED**; private staging `31178359804`, Ubuntu build `92865456267` runner ID `0`, steps `[]`, Billing & plans failure; Pi `92865494913` skipped. This is not a staging pass and does not apply to ES-P05 or ES-P09. |
| ES-P09 start | No prior ES-P09 branch/PR/handoff existed. ES-P03 dependency handoff reconciled. Product implementation is active only on `package/es-p09-alt-network-identity`. |
| ES-P09 privacy boundary | Raw/reversible network addresses may not persist, log, or appear in staff output. Protected equality/encryption tokens, bounded graph evidence, authorized manual decisions, retention, and safe inheritance are the package scope. |
| Migration boundary | V17 is current and immutable; ES-P09 may add only a new post-V17 migration if required by its graph/manual/retention state, with clean/upgrade/checksum proof. |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |

## ES-P09 active worker record

- Sequential selection occurred only after reconciling current primary `main`, open PRs, package branches, parked ES-P02/ES-P05 state, private staging blocker evidence, issue #43, registry/workspace state, the ES-P03 handoff, and the ES-P09 package contract.
- Starting legitimate `main`: `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7`, merge of documentation PR #82.
- Branch/integration authority: `package/es-p09-alt-network-identity`; one same-repository PR to `main`; no pre-existing ES-P09 work was taken over.
- Current implementation already has protected network equality/encryption primitives, `JdbcNetworkIdentityStore`, `/alts` and manual `/alt` relationship commands, inheritance plumbing, and protected identity migration hooks. The package is completing and hardening those foundations rather than introducing raw-IP storage or staff disclosure.
- ES-P03 remains authoritative for Java/Floodgate platform identity; ES-P09 must consume that behavior without redefining it.
- Production/private representative address data and false-positive acceptance are excluded from the development package and remain deferred to `ES-V02`.

## Exact parked-package boundary

ES-P02 and ES-P05 remain on the same account-level GitHub Actions payment/spending-limit restriction affecting private `wsg138/EnthusiaStaff-Staging`. Do not retry the same zero-runner condition while it is unchanged and do not modify those packages without a newly confirmed actionable continuation.

## Next routing

ES-P09 is active. No later package may be selected or activated by this worker. On terminal ES-P09 completion, publish package/registry/workspace/handoff state and stop.

## Safety boundaries

No production credentials, punishment/player records, raw addresses, private databases, production routes, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration, shadow window, cutover, ES-V02 execution, or authority activation is authorized by ES-P09.