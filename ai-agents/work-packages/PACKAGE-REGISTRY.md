# Package registry

Last updated: 2026-08-07

Canonical current state: `ES-P01`, `ES-P03`, `ES-P04`, `ES-P09`, and `ES-X05` are `COMPLETE`; `ES-P02` and `ES-P05` are `BLOCKED` / `PARKED_BLOCKED`; `ES-P10` is `MERGE_PENDING` on `package/es-p10-cheat-testers` / PR #86 after implementing its owned scope. Issue #43 remains open, deferred, and excluded.

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
| `ES-P10` | Cheat tester and fake-entity system | `MERGE_PENDING` | `ACTIONABLE_CONTINUATION` | 80 | `ES-P04` | PR #86; branch `package/es-p10-cheat-testers`; implementation/pre-freeze review complete; exact-head gates, normal merge, containment, branch cleanup, and terminal publication remain |
| `ES-P11` | Fake-base generation and cleanup | `PLANNED` | — | 90 | `ES-P10` | dependency remains blocked until ES-P10 terminal publication; do not activate in this worker |
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
- Exact-head hosted evidence: Wiki `31183192145` / `92881243088` success; Coverage `31183192068` / `92881313210` success on GitHub-hosted Java 21 including full build/tests/MariaDB/Testcontainers/migration checks/coverage/runtime-JAR inspection; artifact `8995826742`, digest `sha256:ed87314d5eda8286928ce64f11027240898a0823333c6ffa5aa6d98f1697dbe4`; Codacy static `92882185524` success with zero issues; coverage variation `92882989470` success; diff coverage `92882989439` success.
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

- Selected automatically on 2026-08-07 after fresh live reconciliation found no actionable continuation: ES-P02 and ES-P05 remain unchanged `PARKED_BLOCKED`, and no competing ES-P10 branch/PR/handoff existed.
- Starting legitimate `main`: `83302749b3247f7a05157f1625fc99da6aa43736` (merge PR #85).
- Status: `MERGE_PENDING`; PR #86 on `package/es-p10-cheat-testers`.
- Implemented `AUD-TESTER-001` and `AUD-TESTER-002`: authenticated staff-mode tool and `/cheattester` fallback; Totem refill, No-fall, Velocity/anti-knockback, and Auto-armor release probes; exact temporary state restoration; V18 durable recovery journal; globally unique active-target fencing; inventory-lock participation; bounded evidence/audit; target/staff-scoped client-side fake entity; target-only suspect interaction counts; lifecycle cleanup/recovery; limits/configuration/tests/Wiki.
- Harsh review fixed a pre-mutation cancellation/journal race and excluded controlling-staff fake-entity interactions from suspect evidence. Static-analysis findings were resolved by splitting lifecycle/session, probe mechanics, evidence, mutation guards, snapshot restoration, command parsing, and persistence validation into focused responsibilities rather than broad suppressions.
- Explicit exclusions remain: fake bases / `AUD-TESTER-003` (`ES-P11`), automatic punishment, production use, unsupported packet/NMS internals, deployment/cutover/private data.
- Protocol/API decision: use ProtocolLib 5.4.0 behind `FakeEntityAdapter`; fail closed when unavailable/unhealthy; no unisolated NMS dependency.
- Migration boundary: ES-P10 adds immutable V18; V1–V17 are unchanged. `main` remains V17 until PR #86 merges.
- Private Pi/distributed staging attempts that receive runner ID `0` and execute no product steps are **NOT A PASS**. The package contract explicitly retains representative Java/Bedrock/distributed acceptance in `ES-V02`; no owner-approved ES-P10 staging exception is being claimed.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p10-cheat-testers.md`.
- Exact next action: freeze the final content head, require all applicable hosted/static/review gates and zero valid threads, merge PR #86 normally, verify containment, delete the temporary implementation branch, publish terminal state, recompute dependency-derived statuses without activating ES-P11, and stop.

## Next-worker boundary

ES-P10 is `MERGE_PENDING` and remains the sole package owned by this worker. Do not start another package until PR #86 merges, terminal publication reaches `main`, dependency-derived statuses are recomputed, and temporary ES-P10 branches are removed.