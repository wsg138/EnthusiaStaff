# EnthusiaStaff workspace state

Last updated: 2026-08-07

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01 — Exact-sanction appeal isolation`; `ES-P03 — Bedrock identity correctness`; `ES-P04 — Staff-mode operational tools`; `ES-P09 — Alt and network-identity completion`; `ES-X05 — Website UX, authentication, and appeals` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-P05 — Report evidence and staff workflow completion` |
| Active implementation package | `ES-P10 — Cheat tester and fake-entity system`; status `MERGE_PENDING`; branch `package/es-p10-cheat-testers`; PR #86; selected from legitimate `main` `83302749b3247f7a05157f1625fc99da6aa43736` by the ChatGPT sequential package worker |
| Ready packages | None until ES-P10 reaches terminal `COMPLETE`. Dependency-derived statuses are recomputed after merge/publication; do not activate another package in this worker. |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70; private Actions Billing & plans blocker unchanged |
| ES-P05 status | `BLOCKED` / `PARKED_BLOCKED`; PR #81; implementation/hosted validation complete at `4a38e191395913c6733726e222f0889a2d56d267`; required private staging unavailable under the same Billing & plans condition |
| Fresh blocker confirmation | Private staging run `31196247124` created 2026-08-07 16:10 UTC for current EnthusiaStaff source; required Ubuntu build `92925059857` had runner ID `0`, empty runner name, and `steps: []`; Pi `92925074453` skipped. This confirms the same external condition still exists and is not a product pass. |
| ES-P05 hosted validation | Wiki `31183192145` / `92881243088` success; Coverage `31183192068` / `92881313210` success on Java 21 including build/tests/MariaDB/Testcontainers/migration checks/coverage/runtime-JAR inspection; artifact `8995826742` digest `sha256:ed87314d5eda8286928ce64f11027240898a0823333c6ffa5aa6d98f1697dbe4`; Codacy static `92882185524` success with zero issues; variation `92882989470` and diff coverage `92882989439` success |
| ES-P05 staging blocker | Public wrapper `31183283525` / `92881545286`; private run `31183290816`; required Ubuntu build `92881577147` runner ID `0`, empty runner name, steps `[]`, Billing & plans failure; Pi `92881591391` skipped. This is not a pass. |
| ES-P05 review | Harsh self-review completed with three pre-freeze fixes; zero inline review threads; CodeRabbit quota-limited and must rerun when package resumes |
| ES-P04 status | `COMPLETE` under its explicit owner-approved package-specific infrastructure exception for Pi staging |
| ES-P04 validated head | `15d9428eba454e9ae4a905752129bd18676acdb1` |
| ES-P04 merge | PR #79; normal merge `a530b992232a8a08cbbd13b0eed6606228ceb652` |
| ES-P04 staging disposition | **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — PI STAGING SKIPPED/DEFERRED**; private staging `31178359804`, Ubuntu build `92865456267` runner ID `0`, steps `[]`, Billing & plans failure; Pi `92865494913` skipped. This is not a staging pass and does not apply to ES-P05 or ES-P10. |
| ES-P09 status | `COMPLETE`; PR #84; frozen head `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`; normal merge `a88201524690848f778297f140f7ee2ba5b6ce36`; implementation branch deleted; terminal records published through docs-only PR #85 |
| ES-P09 hosted validation | Wiki `31193764800` / `92916829444` success; Coverage `31193765341` / `92916907616` success on Java 21 with build/tests/MariaDB-Testcontainers/aggregate JaCoCo/runtime-JAR inspection; Codacy `92917176627` success with zero annotations; repository CodeRabbit status success; zero valid unresolved review threads |
| ES-P09 private staging | **NOT A PASS**. Public wrapper `31193762319`; private run `31193769314`; Ubuntu build `92916864019` runner ID `0`, empty runner name, steps `[]`, Billing & plans rejection; Pi `92916876057` skipped. Private representative-network/distributed acceptance remains `ES-V02`. |
| ES-P10 status | `MERGE_PENDING`; PR #86; product scope and harsh pre-freeze review complete; final exact-head hosted/static/review gates and normal merge remain |
| ES-P10 implementation | Four release tester types; `/cheattester` and staff-tool controls; V18 durable session journal; exact restoration; global active-target fencing; inventory-lock participation; evidence-only audit; optional ProtocolLib fake entity; fail-closed provider behavior; target-only suspect interaction evidence |
| ES-P10 private staging | **NOT A PASS**. Repeated package attempts, including the pre-freeze `239ef4f...` head, dispatch private staging where the required Ubuntu job receives runner ID `0`, empty runner name, and no steps; Pi is skipped. Representative distributed/Java/Bedrock acceptance remains assigned to `ES-V02`. |
| Migration boundary | ES-P10 adds immutable V18; V1–V17 remain unchanged. `main` remains at V17 until PR #86 merges. |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |

## ES-P10 merge-pending worker record

- Fresh live reconciliation classified ES-P02 and ES-P05 as unchanged `PARKED_BLOCKED`; no actionable continuation existed.
- ES-P04 is `COMPLETE`; ES-P10 was the dependency-complete `READY` package with the lowest numerical priority and was selected automatically.
- Starting legitimate `main`: `83302749b3247f7a05157f1625fc99da6aa43736`.
- Active branch: `package/es-p10-cheat-testers`; implementation PR #86.
- Scope `AUD-TESTER-001` and `AUD-TESTER-002` is implemented: authorized cheat tester workflow, four release probes, bounded client-side fake entity tooling, evidence/audit, exact temporary player-state restoration, lifecycle recovery, duplicate/limit enforcement, configuration, command/staff-tool controls, and tests.
- Fake bases (`AUD-TESTER-003`) remain exclusively `ES-P11`. No automatic punishment, production use, unisolated NMS, deployment, cutover, or private-data work is authorized.
- ProtocolLib 5.4.0 is the repository-supported compile-only Paper dependency; packet behavior is isolated behind an optional fail-closed adapter.
- V18 durably journals tester sessions and globally fences one ACTIVE target across backends. State-changing ACTIVE rows participate in the inventory-lock contract through disconnect/restart.
- Harsh review fixed a cancellation/journal race: cancellation before submission now creates no row, cancellation during submission terminalizes a successfully committed row before mutation, and cancellation after commit uses the durable finish path. Review also excludes controlling-staff fake-entity interactions from suspect evidence.
- Static-analysis cleanup was structural, splitting session/probe/evidence/mutation-guard responsibilities rather than suppressing broad findings.
- Private Pi/distributed attempts that allocate no runner are **NOT A PASS** and are not package-local staging acceptance; ES-V02 retains representative Java/Bedrock/distributed acceptance.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p10-cheat-testers.md`.

## ES-P09 terminal worker record

- Sequential selection occurred only after reconciling current primary `main`, open PRs, package branches, parked ES-P02/ES-P05 state, issue #43, registry/workspace state, the ES-P03 handoff, and the ES-P09 package contract.
- Starting legitimate `main`: `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7`.
- Frozen implementation head: `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`.
- PR #84 merged normally as `a88201524690848f778297f140f7ee2ba5b6ce36`.
- Post-merge containment is exact: `main` is one merge commit ahead of the frozen implementation head, zero behind, with no file differences; the implementation branch no longer exists.
- ES-P09 preserves protected network identity, bounds shared-network/active-sanction scans, suppresses broad-network automation, lowers automatic confidence on simultaneous play, preserves manual decisions, keeps the narrow authoritative inheritance rule only for unambiguous new-account evidence, adds duplicate-evidence throttling and bounded retention from a trusted runtime clock behind the authority fence, and rejects raw address literals from manual audit reasons.
- ES-P03 remains authoritative for Java/Floodgate platform identity; ES-P09 does not redefine it.
- Production/private representative network data, false-positive acceptance, distributed Java/Bedrock staging, production key rotation, and cutover remain outside ES-P09. `ES-V02` owns the deferred private acceptance.
- Documentation-only PR #85 published the terminal record and did not alter product behavior.

## Exact parked-package boundary

ES-P02 and ES-P05 remain on the same account-level GitHub Actions payment/spending-limit restriction affecting private `wsg138/EnthusiaStaff-Staging`. Do not retry the same zero-runner condition while it is unchanged and do not modify those packages without a newly confirmed actionable continuation.

## Next routing

ES-P10 is the only active package for this sequential worker and is `MERGE_PENDING`. Freeze/validate/merge/publish/clean up ES-P10, then recompute dependency-derived statuses and stop. Do not activate ES-P11 or any other newly ready package in this worker.

## Safety boundaries

No production credentials, punishment/player records, raw addresses, private databases, production routes, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration, shadow window, cutover, ES-V02 execution, or authority activation is authorized by ES-P10.