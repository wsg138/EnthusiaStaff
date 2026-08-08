# Package registry

Last updated: 2026-08-07

Canonical current state: `ES-P01`, `ES-P03`, `ES-P04`, `ES-P09`, `ES-P10`, and `ES-X05` are `COMPLETE`; `ES-P02` and `ES-P05` are `BLOCKED` / `PARKED_BLOCKED`; `ES-P11` is `ACTIVE` on `package/es-p11-fake-bases`. Issue #43 remains open, deferred, and excluded.

## Rules

- Live GitHub overrides stale text and must be reconciled back here.
- Classify every incomplete package before selection; select one package and stop after its terminal state is persistently published.
- Existing actionable continuation work takes priority over new implementation work.
- Park unchanged external blockers; do not repeat identical zero-runner attempts without a material condition change.
- Use normal merge commits only. Missing, skipped, cancelled, superseded, wrong-revision, billing-blocked, or zero-runner gates are not passes.
- Owner-directed infrastructure exceptions must be explicit, package-specific, and recorded as deferred/skipped evidence rather than a false pass.

## Canonical package index

| ID | Title | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | ---: | --- | --- |
| `ES-P01` | Exact-sanction appeal isolation | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-P02` | Runtime database recovery and Velocity reload | `BLOCKED` | `PARKED_BLOCKED` | 20 | `ES-P01` | PR #70; unchanged private Actions Billing & plans blocker |
| `ES-P03` | Bedrock identity correctness | `COMPLETE` | — | 30 | ordinarily `ES-P02`; owner-directed narrow exception | merged PR #75 as `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| `ES-X05` | Website UX, authentication, and appeals | `COMPLETE` | — | 35 | `ES-P01` | merged PR #74 as `2bcf5d46ca6471fddac600f85020c66105b1c0f2` |
| `ES-P04` | Staff-mode operational tools | `COMPLETE` | — | 40 | `ES-P03` | PR #79 merged normally as `a530b992232a8a08cbbd13b0eed6606228ceb652`; Pi staging owner-deferred for later internal verification |
| `ES-P07` | Inventory and Ender editing runtime completion | `PLANNED` | — | 45 | `ES-P02` | dependency blocked |
| `ES-P05` | Report evidence and staff workflow completion | `BLOCKED` | `PARKED_BLOCKED` | 50 | `ES-P03`, `ES-P04` | PR #81; implementation/hosted validation complete at `4a38e191395913c6733726e222f0889a2d56d267`; private staging Billing & plans blocker |
| `ES-P09` | Alt and network-identity completion | `COMPLETE` | — | 55 | `ES-P03` | PR #84 merged normally as `a88201524690848f778297f140f7ee2ba5b6ce36` from frozen head `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`; implementation branch deleted; private representative-network staging remains ES-V02 |
| `ES-P06` | Discord notification delivery completion | `PLANNED` | — | 60 | `ES-P05` | dependency blocked while ES-P05 is parked |
| `ES-P08` | Item confiscation and restoration | `PLANNED` | — | 70 | `ES-P07` | dependency blocked |
| `ES-P10` | Cheat tester and fake-entity system | `COMPLETE` | — | 80 | `ES-P04` | PR #86 merged normally as `e605d8ad6094b2ae6842044d209875e13c38906d` from frozen head `1997caa864847049d51bfc58402f019e0a0d65c6`; exact containment verified; implementation branch deleted; representative Java/Bedrock/distributed acceptance remains ES-V02 |
| `ES-P11` | Fake-base generation and cleanup | `ACTIVE` | `ACTIONABLE_CONTINUATION` | 90 | `ES-P10` | PR #88 / `package/es-p11-fake-bases`; bounded virtual-only implementation and initial direct tests committed; compile/review/docs/validation pending |
| `ES-X01` | RoseChat provider and communication integration | `PLANNED` | — | 100 | `ES-P03`, `ES-P04`, `ES-P05` | dependency blocked while ES-P05 is parked |
| `ES-X02` | EnthusiaCurrency destructive provider | `PLANNED` | — | 110 | `ES-P08` | dependency blocked |
| `ES-X03` | EnthusiaMarket destructive provider | `PLANNED` | — | 120 | `ES-P08`, `ES-X02` | dependency blocked |
| `ES-X04` | EnthusiaCommend reputation provider | `PLANNED` | — | 125 | `ES-P08`, `ES-X02` | dependency blocked |
| `ES-V01` | Private LiteBans representative-data verification | `DEFERRED` | — | 200 | — | private/local environment required |
| `ES-V02` | Distributed and Java/Bedrock staging | `DEFERRED` | — | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | private validation; retains ES-P09 representative-network false-positive/distributed acceptance and ES-P10 Java/Bedrock/distributed acceptance |
| `ES-V03` | Destructive, latency, and load acceptance | `DEFERRED` | — | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | private validation |
| `ES-A01` | LiteBans cutover acceptance | `DEFERRED` | — | 300 | `ES-V01`, `ES-V02`, `ES-V03` | owner authorization and issue #43 required |
| `ES-QA01` | Final repository and workflow audit | `PLANNED` | — | 400 | `ES-A01` | dependency blocked |

## `ES-P02`

- Remains `BLOCKED` / `PARKED_BLOCKED` on PR #70.
- Latest package-record private build `92753075216` received runner ID `0`, empty runner name, steps `[]`, and the Billing & plans payment/spending-limit rejection; Pi `92753100652` skipped.
- A fresh private staging run at 2026-08-07 16:10 UTC for current EnthusiaStaff source again produced required Ubuntu build `92925059857` with runner ID `0`, empty runner name, and `steps: []`; Pi `92925074453` skipped. This confirms the same external blocker remains and does not make ES-P02 actionable.
- Unblock: resolve that account-level restriction, then resume and rerun all required exact-head gates.

## `ES-P04`

- Starting legitimate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Frozen reviewed/validated head: `15d9428eba454e9ae4a905752129bd18676acdb1`.
- PR #79 merged normally as `a530b992232a8a08cbbd13b0eed6606228ceb652`.
- Scope completed for random teleport, inspector, freeze, reports, follow/spectate, vanish, staff chat, tools menu, and shared staff-mode safety/recovery. Cheat Tester remains ES-P10; fake bases remain ES-P11.
- Review: zero valid unresolved threads. Deferred Cheat Tester issuance, cooldown boundaries, follow/spectate feedback, compile drift, and Codacy test-structure findings were resolved. Scheduler-helper consolidation was explicitly withdrawn as optional maintainability follow-up.
- Exact-head hosted evidence: Wiki run `31178353549`, job `92865432750`, success; Coverage run `31178353504`, job `92865439305`, success on GitHub-hosted Java 21 including full build/tests/coverage/runtime-JAR inspection; Codacy static `92865800728` success with zero issues; coverage variation `92867049954` and diff coverage `92867049338` success.
- Exact-head private staging did not execute product code: public wrapper `31178352312` dispatched private run `31178359804`; required Ubuntu build `92865456267` had runner ID `0`, empty runner name, steps `[]`, and GitHub's Billing & plans rejection; Pi `92865494913` skipped.
- **Owner-approved ES-P04 infrastructure exception:** on 2026-08-07 the owner instructed the worker to continue, mark Pi staging skipped/deferred, and record an internal follow-up to run it later when available. This is not a staging pass and does not generalize to other packages.
- Internal follow-up: when the private Actions billing/runner path is available, rerun ES-P04 Pi boot/restart staging against the merged behavior and record the result. Reopen ES-P04 only if that later test exposes a real defect.
- V17 remains immutable; ES-P04 added no migration.

## `ES-P05`

- Selected on 2026-08-07 from legitimate `main` `bf9b305ba96d9536f3d111c79eef674bd2e11dc5` after ES-P02 remained unchanged `PARKED_BLOCKED`; no competing ES-P05 package work existed.
- Implementation PR #81 remains open on `package/es-p05-report-workflow`; it must not merge while the required private staging gate is unavailable.
- Frozen implementation / hosted-validation head: `4a38e191395913c6733726e222f0889a2d56d267`.
- Implemented provider-independent report review completion: dedicated sensitive-evidence permission, bounded staff-only `/reports evidence` presentation, coordinate/privacy separation from broad GUI triage, strict client-evidence allow-listing, newest-snapshot default, explicit no-direct-attachment boundary, direct wiring/privacy tests, MariaDB restart durability proof, and Wiki/operator documentation. Existing durable cooldown/merge/replay/stale-revision/concurrency/rollback/purge foundations were preserved and revalidated.
- Exact-head hosted evidence: Wiki `31183192145` / `92881243088` success; Coverage `31183192068` / `92881313210` success on GitHub-hosted Java 21 including full build/tests/MariaDB-Testcontainers/migration checks/coverage/runtime-JAR inspection; artifact `8995826742`, digest `sha256:ed87314d5eda8286928ce64f11027240898a0823333c6ffa5aa6d98f1697dbe4`; Codacy static `92882185524` success with zero issues; coverage variation `92882989470` success; diff coverage `92882989439` success.
- Final diff was harshly self-reviewed; three found issues were fixed before freeze: broad GUI coordinate exposure, raw nested AutoClicker serialization, and oldest-snapshot default selection. Zero inline review threads remain. CodeRabbit was quota-limited and produced no review; it must rerun on resume.
- Required private staging did not execute product code. Latest package-record public wrapper `31183283525` / `92881545286` dispatched private run `31183290816`; required Ubuntu build `92881577147` had runner ID `0`, empty runner name, steps `[]`, and GitHub's Billing & plans rejection; Pi `92881591391` skipped. The fresh 16:10 UTC private run described above confirms that the same account-level condition remains unchanged.
- This is not a staging pass and no ES-P05-specific exception exists. The ES-P04 exception is package-specific and cannot be reused.
- Exact unblock: resolve the GitHub Actions payment/spending-limit restriction for private `wsg138/EnthusiaStaff-Staging`, then resume PR #81, reconcile newer `main`, rerun every exact-head review/static/hosted/staging gate (including CodeRabbit), require successful trusted private build plus Pi safe boot/restart, merge normally, verify containment, finalize records/cleanup, and stop.
- V17 remains immutable; ES-P05 added no migration. RoseChat PM capture remains ES-X01; Discord route delivery remains ES-P06; issue #43 remains deferred.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p05-report-workflow.md`.

## `ES-P09`

- Selected 2026-08-07 after live reconciliation confirmed no active actionable package, unchanged parked blockers for ES-P02/ES-P05, and no pre-existing ES-P09 branch/PR/handoff.
- Starting legitimate `main`: `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7` (merge PR #82).
- Frozen implementation head: `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`.
- PR #84 merged normally as `a88201524690848f778297f140f7ee2ba5b6ce36`; post-merge compare from frozen head reports one commit ahead, zero behind, and no file differences. Temporary implementation branch cleanup is complete.
- Completed protected-token graph hardening: bounded shared-network matching and sanction reads; broad-network suppression; simultaneous-play ambiguity; preserved manual decisions; unambiguous-only narrow automatic inheritance; evidence throttling; trusted-clock, authority-fenced bounded retention; raw-address rejection before manual audit persistence; direct concurrency/restart/privacy/key-version tests; Wiki updates.
- Exact frozen-head evidence: Wiki `31193764800` / `92916829444` success; Coverage `31193765341` / `92916907616` success on Java 21 including full build/tests/MariaDB-Testcontainers/aggregate JaCoCo/runtime-JAR inspection; Codacy `92917176627` success with zero annotations; repository CodeRabbit status success; all four actionable threads resolved/outdated and zero valid unresolved review threads at merge.
- Private staging remains **NOT A PASS** and is assigned to ES-V02. Public wrapper `31193762319` dispatched private run `31193769314`; required Ubuntu build `92916864019` had runner ID `0`, empty runner name, steps `[]`, and the Billing & plans rejection; Pi `92916876057` skipped. No product validation step executed in that private run.
- ES-P03 remains the canonical platform-identity boundary. Production/private representative network data, false-positive/distributed acceptance, deployment/cutover, and issue #43 remain excluded/deferred.
- V17 remains current and immutable; ES-P09 added no migration.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p09-alt-network-identity.md`.

## `ES-P10`

- Live reconciliation selected ES-P10 as the highest-priority `ACTIONABLE_CONTINUATION` because PR #86 / `package/es-p10-cheat-testers` already existed with unfinished exact-head quality/review work. ES-P02 and ES-P05 remained unchanged `PARKED_BLOCKED` and were not resumed.
- Starting legitimate `main`: `83302749b3247f7a05157f1625fc99da6aa43736` (merge PR #85).
- Frozen implementation head: `1997caa864847049d51bfc58402f019e0a0d65c6`.
- PR #86 merged normally as `e605d8ad6094b2ae6842044d209875e13c38906d`.
- Post-merge containment is exact: merged `main` is one merge commit ahead of the frozen implementation head, zero behind, with no file differences. The implementation branch `package/es-p10-cheat-testers` was automatically deleted and returns 404.
- Completed `AUD-TESTER-001` and `AUD-TESTER-002`: authenticated staff-mode tool and `/cheattester` fallback; Totem refill, No-fall, Velocity/anti-knockback, and Auto-armor release probes; exact temporary state restoration; immutable V18 durable recovery journal; globally unique active-target fencing; cross-backend login recovery; inventory-lock participation; bounded evidence/audit; target/staff-scoped client-side fake entity; target-only suspect interaction counts; lifecycle cleanup/recovery; limits/configuration/tests/Wiki.
- Continuation review resolved strict nested configuration handling, recovery polling load, tagged-tool damage leakage, status privacy, inventory mutation guards, non-finite settings, snapshot validation ordering, ProtocolLib runtime/linkage/cleanup failure semantics, stale delayed fake spawns, cross-backend durable recovery, persistence IDs/JSON/payload sizing, cancellation/journal ordering, controlling-staff evidence contamination, and static size/direct-coverage findings. Zero valid unresolved review threads remained at merge.
- Protocol/API decision: ProtocolLib 5.4.0 behind `FakeEntityAdapter`; provider/packet/cleanup failures fail closed; no unisolated NMS dependency.
- Exact frozen-head hosted evidence: Wiki check `93015435354` success; workflow run `31224336640` successful unchanged-head rerun job `93016497496` on Java 21 with build/tests/MariaDB/Testcontainers/migrations/aggregate JaCoCo/runtime-JAR inspection/artifact/Codacy coverage upload; artifact `9011777818`, digest `sha256:4557877edff2589065483f4d2d81f0c231511001ad04ea82d5b36cfbba1c762a`; aggregate coverage 2,576/5,427 lines and 796/1,887 branches; Codacy static `93015840928` success with zero issues; coverage variation `93017546035` success at `-0.98%` against the `-1.0%` limit; diff coverage `93017545807` success at `29.91%` with no defined gate.
- The first attempt of workflow run `31224336640` hit an unrelated MariaDB concurrent-update failure in the already-merged punishment-request-alert integration test. No ES-P10 code changed in response; failed jobs were rerun on the exact unchanged SHA and the complete hosted job then succeeded. Only the clean unchanged-head rerun is counted as passing evidence.
- Exact frozen-head private staging remains **NOT A PASS** and is assigned to ES-V02: private run `31224339373`; Ubuntu build `93015447468` runner ID `0`, empty runner name, steps `[]`; Pi `93015487565` skipped. No ES-P10 owner-approved infrastructure exception was claimed and no unavailable gate was relabeled as success.
- V18 is now the aggregate `main` migration boundary; V1–V17 remain unchanged.
- Explicit exclusions remain: fake bases / `AUD-TESTER-003` (`ES-P11`), automatic punishment, production use, unsupported packet/NMS internals, deployment/cutover/private data.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p10-cheat-testers.md`.

## `ES-P11`

- Selected 2026-08-07 after fresh live reconciliation found no actionable continuation: ES-P02 and ES-P05 remain unchanged `PARKED_BLOCKED`, and no ES-P11 branch/PR/handoff existed.
- Starting legitimate `main`: `68a6d936066383f5b8139304f40b2d01d0dfe036`.
- Active implementation: draft PR #88 on `package/es-p11-fake-bases`.
- Dependency ES-P10 is `COMPLETE`; migration boundary remains immutable V18 and ES-P11 has added no migration.
- Implemented checkpoint: fixed 7x7 bounded template; placement constrained to one already-loaded target chunk; all template cells must be real air and a 5x5 interior floor must be safe; no real block writes or chunk loads; exact global/per-staff/target ownership bounds; five-minute timeout, four-minute warning, distance/world/disconnect/staff-mode/plugin-disable cleanup; Extend/Clear/Teleport controls; target plus authorized staff viewer isolation; authoritative real-block re-send on cleanup; coordinate-free durable audit events through existing `audit_events`; `/cheattester base` text fallback and explicit permission nodes.
- Initial direct tests cover template bounds/duplicates/doorway, one-chunk placement and conflict/height/floor rejection, lifecycle warning/extension/expiry/idempotency, and bounded coordinate-free audit model. Runtime permission/scheduler/shutdown tests, docs, self-review, and exact-head validation remain pending.
- Pre-freeze workflow runs superseded by newer commits are not passing evidence. Current resume authority is the package file and `ai-agents/reports/package-handoffs/2026-08-07-es-p11-fake-bases.md` plus live PR #88.
- Private representative Java/Bedrock/distributed acceptance remains ES-V02 and unchanged private Actions billing/zero-runner evidence is not retried here without a material condition change.

## Next-worker boundary

ES-P11 is the selected `ACTIVE` package. This worker must complete or correctly park ES-P11, publish terminal/persistent state, and stop without activating another package. ES-P02 and ES-P05 remain parked until their exact external unblock condition changes.
