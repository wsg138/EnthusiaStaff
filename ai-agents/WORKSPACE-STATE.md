# EnthusiaStaff workspace state

Last updated: 2026-08-07

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01 — Exact-sanction appeal isolation`; `ES-P03 — Bedrock identity correctness`; `ES-P04 — Staff-mode operational tools`; `ES-X05 — Website UX, authentication, and appeals` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-P05 — Report evidence and staff workflow completion` |
| Active implementation package | `NONE` |
| Ready packages | `ES-P09 — Alt and network-identity completion` at priority 55; `ES-P10 — Cheat tester and fake-entity system` at priority 80 |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70; private Actions Billing & plans blocker unchanged |
| ES-P05 status | `BLOCKED` / `PARKED_BLOCKED`; PR #81; implementation/hosted validation complete at `4a38e191395913c6733726e222f0889a2d56d267`; required private staging unavailable under the same Billing & plans condition |
| ES-P05 hosted validation | Wiki `31183192145` / `92881243088` success; Coverage `31183192068` / `92881313210` success on Java 21 including build/tests/MariaDB/Testcontainers/migration checks/coverage/runtime-JAR inspection; artifact `8995826742` digest `sha256:ed87314d5eda8286928ce64f11027240898a0823333c6ffa5aa6d98f1697dbe4`; Codacy static `92882185524` success with zero issues; variation `92882989470` and diff coverage `92882989439` success |
| ES-P05 staging blocker | Public wrapper `31183283525` / `92881545286`; private run `31183290816`; required Ubuntu build `92881577147` runner ID `0`, empty runner name, steps `[]`, Billing & plans failure; Pi `92881591391` skipped. This is not a pass. |
| ES-P05 review | Harsh self-review completed with three pre-freeze fixes; zero inline review threads; CodeRabbit quota-limited and must rerun when package resumes |
| ES-P04 status | `COMPLETE` under an explicit owner-approved package-specific infrastructure exception for Pi staging |
| ES-P04 validated head | `15d9428eba454e9ae4a905752129bd18676acdb1` |
| ES-P04 merge | PR #79; normal merge `a530b992232a8a08cbbd13b0eed6606228ceb652` |
| ES-P04 staging disposition | **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — PI STAGING SKIPPED/DEFERRED**; private staging `31178359804`, Ubuntu build `92865456267` runner ID `0`, steps `[]`, Billing & plans failure; Pi `92865494913` skipped. This is not a staging pass and does not apply to ES-P05. |
| ES-P04 internal follow-up | When the private Actions billing/runner path is available, rerun the ES-P04 Pi boot/restart staging against the merged behavior and record the result. |
| Migration boundary | immutable V17; ES-P05 added no migration |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |
| Parallel documentation branch | `docs/wiki-maintenance-2026-08` remains outside package-state authority; ES-P05 did not modify or synchronize it |

## ES-P05 terminal worker record

- Selected from legitimate `main` `bf9b305ba96d9536f3d111c79eef674bd2e11dc5` because ES-P02 remained unchanged `PARKED_BLOCKED` and ES-P05 was the lowest-priority dependency-complete READY package.
- Branch `package/es-p05-report-workflow`; PR #81 remains open and unmerged.
- Frozen product/hosted-validation head `4a38e191395913c6733726e222f0889a2d56d267` implements the provider-independent report package without changing the immutable V17 schema boundary.
- Added least-privilege evidence inspection through `enthusiastaff.reports.evidence` and `/reports evidence`; broad helper-level triage no longer renders exact coordinates or raw retained evidence.
- Evidence output is bounded and allow-listed, malformed storage fails closed, opaque Polar metadata is withheld, and direct arbitrary file/URL attachments are explicitly unsupported by this package.
- Added direct command/GUI/privacy wiring tests and a MariaDB runtime-restart test. Existing report integration suites continue to cover cooldown/merge/replay, concurrent staff, stale revisions, idempotency conflict, bounded purge and rollback.
- Exact hosted gates passed; required private trusted-build/Pi staging did not execute because GitHub refused the private Ubuntu job before runner allocation under Billing & plans.
- The validation policy does not permit this worker to treat that as passed or reuse ES-P04's package-specific exception. PR #81 therefore remains parked rather than merged.
- CodeRabbit was temporarily quota-limited after the PR became ready, so no bot findings were produced; zero inline threads exist. A resumed worker must rerun CodeRabbit together with all exact-head gates.

## Exact ES-P05 unblock condition

Resolve the GitHub Actions payment/spending-limit restriction affecting `wsg138/EnthusiaStaff-Staging`. A resumed ES-P05 worker must then reconcile any newer legitimate `main` through the normal package rules, rerun the entire exact-head hosted/static/review/staging matrix, require a successful trusted private build and Pi safe boot/restart on that same head, merge PR #81 normally, verify containment, finalize package records, clean the temporary branch, and stop.

Do not retry the same zero-runner condition while it is unchanged and do not modify product code without a newly confirmed defect.

## Next routing

No package is active. ES-P02 and ES-P05 are parked external blockers. A later sequential worker must reconcile live state first; absent a material unblock that makes a parked continuation actionable, ES-P09 is the lowest-priority-number READY package. This worker does not start it.

## Safety boundaries

No production credentials, punishment/player records, raw addresses, private databases, production routes, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration, shadow window, cutover, ES-V02 execution, or authority activation was performed.
