# EnthusiaStaff workspace state

Last updated: 2026-08-07

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01 — Exact-sanction appeal isolation`; `ES-P03 — Bedrock identity correctness`; `ES-P04 — Staff-mode operational tools`; `ES-P09 — Alt and network-identity completion`; `ES-P10 — Cheat tester and fake-entity system`; `ES-X05 — Website UX, authentication, and appeals` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-P05 — Report evidence and staff workflow completion` |
| Active implementation package | None. ES-P10 reached terminal `COMPLETE`; this worker must stop after terminal publication. |
| Ready packages | `ES-P11 — Fake-base generation and cleanup` is dependency-complete and `READY`; it was not activated or modified by the ES-P10 worker. |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70; private Actions Billing & plans blocker unchanged |
| ES-P05 status | `BLOCKED` / `PARKED_BLOCKED`; PR #81; implementation/hosted validation complete at `4a38e191395913c6733726e222f0889a2d56d267`; required private staging unavailable under the same Billing & plans condition |
| ES-P04 status | `COMPLETE`; PR #79 merged as `a530b992232a8a08cbbd13b0eed6606228ceb652`; package-specific owner-approved Pi staging deferral remains recorded and does not apply to other packages |
| ES-P09 status | `COMPLETE`; PR #84; frozen head `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`; normal merge `a88201524690848f778297f140f7ee2ba5b6ce36`; implementation branch deleted; private representative-network acceptance remains ES-V02 |
| ES-P10 status | `COMPLETE`; PR #86; frozen head `1997caa864847049d51bfc58402f019e0a0d65c6`; normal merge `e605d8ad6094b2ae6842044d209875e13c38906d`; exact containment verified; implementation branch deleted |
| ES-P10 hosted validation | Wiki `93015435354` success; Java 21 workflow `31224336640`, unchanged-head rerun job `93016497496` success including build/tests/MariaDB/Testcontainers/migrations/aggregate JaCoCo/runtime-JAR inspection/artifact/Codacy upload; artifact `9011777818` digest `sha256:4557877edff2589065483f4d2d81f0c231511001ad04ea82d5b36cfbba1c762a`; Codacy static `93015840928` success with zero issues; coverage variation `93017546035` success at `-0.98%`; diff coverage `93017545807` success |
| ES-P10 private staging | **NOT A PASS**. Exact frozen-head private run `31224339373`; Ubuntu build `93015447468` runner ID `0`, empty runner name, steps `[]`; Pi `93015487565` skipped. Representative Java/Bedrock/distributed acceptance remains assigned to `ES-V02`; no ES-P10 infrastructure exception was claimed. |
| Migration boundary | Aggregate `main` now includes immutable V18 from ES-P10; V1–V17 remain unchanged. |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |

## ES-P10 terminal worker record

- Fresh live reconciliation found PR #86 / `package/es-p10-cheat-testers` already advancing ES-P10. Its live head still had exact-head Codacy size/coverage failures and unresolved review findings, so ES-P10 was selected as the highest-priority `ACTIONABLE_CONTINUATION` rather than a new package claim.
- ES-P02 and ES-P05 remained unchanged `PARKED_BLOCKED`; neither was resumed. Exactly one package was worked.
- Starting legitimate `main`: `83302749b3247f7a05157f1625fc99da6aa43736`.
- Frozen implementation head: `1997caa864847049d51bfc58402f019e0a0d65c6`.
- Product merge: PR #86 merged normally as `e605d8ad6094b2ae6842044d209875e13c38906d`.
- Post-merge containment is exact: merged `main` is one merge commit ahead of the frozen implementation head, zero behind, and has zero file differences relative to it.
- `package/es-p10-cheat-testers` was automatically deleted after merge and returns 404.
- Scope `AUD-TESTER-001` and `AUD-TESTER-002` is complete: authorized cheat tester workflow; four release probes; bounded client-side fake entity tooling; exact temporary state restoration; V18 durable lifecycle/recovery; global active-target fencing and cross-backend login recovery; inventory-lock participation; bounded evidence/audit; limits/configuration; command/staff-tool controls; tests; Wiki/operator documentation.
- Fake bases (`AUD-TESTER-003`) remain exclusively `ES-P11`. No automatic punishment, production use, unisolated NMS, deployment, cutover, or private-data work was performed.
- ProtocolLib 5.4.0 remains isolated behind `FakeEntityAdapter`; unavailable/unhealthy packet support fails closed, and unverifiable fake-entity cleanup remains durably recoverable instead of being falsely terminalized.
- Continuation review fixed strict nested config handling, recovery polling load, tagged-tool damage leakage, status privacy, inventory mutation guards, non-finite settings, snapshot validation ordering, stale fake spawns, provider failure handling, cross-backend durable recovery, persistence IDs/JSON/payload sizing, cancellation/journal ordering, staff-observer evidence contamination, and static size/direct-coverage findings.
- All live PR review threads were reconciled; zero valid unresolved review threads remained at merge.
- Exact frozen-head Java/Wiki/static/coverage gates passed. The first attempt of the Java workflow hit an unrelated already-merged punishment-alert MariaDB concurrent-update test failure; no ES-P10 code changed, and the complete unchanged-head rerun passed. Only the successful unchanged-head rerun counts as passing evidence.
- Exact private staging allocated no runner and executed no product steps. It is **NOT A PASS** and remains delegated to `ES-V02` by the package contract.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p10-cheat-testers.md`.

## Existing parked-package boundary

- ES-P02 remains on PR #70 under the unchanged private Actions Billing & plans condition. Latest known fresh confirmation remains a zero-runner/no-step private Ubuntu job with Pi skipped.
- ES-P05 remains on PR #81 with hosted validation complete at `4a38e191395913c6733726e222f0889a2d56d267`, but its required private staging did not execute because of the same account-level Billing & plans condition. No ES-P05-specific exception exists.
- Do not retry identical zero-runner conditions or update parked branches merely to keep them current. Resume only after the exact external unblock condition changes or another real actionable defect appears.

## Next routing

ES-P10 is terminal `COMPLETE`, so this worker owns no further package. Dependency-derived recomputation makes ES-P11 `READY`, but this worker did not activate it. A future worker must begin again from live GitHub and the canonical selection protocol. ES-P02 and ES-P05 remain parked.

## Safety boundaries

No production credentials, punishment/player records, raw addresses, private databases, production routes, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration/cutover, ES-V02 execution, or authority activation was authorized or performed by ES-P10.
