# Package registry

Last updated: 2026-08-08

Canonical current state: `ES-P01`, `ES-P03`, `ES-P04`, `ES-P09`, `ES-P10`, `ES-P11`, and `ES-X05` are `COMPLETE`; `ES-R01`, `ES-P02`, and `ES-P05` are `BLOCKED` / `PARKED_BLOCKED`. No package is currently `READY`. Issue #43 remains open, deferred, and excluded.

## Rules

- Live GitHub overrides stale text and must be reconciled back here.
- Classify every incomplete package before selection; select one package and stop after its terminal state is persistently published.
- Existing actionable continuation work takes priority over new implementation work.
- Park unchanged external blockers; do not repeat identical unavailable-infrastructure attempts without a material condition change.
- Use normal merge commits only. Missing, skipped, cancelled, superseded, wrong-revision, billing-blocked, zero-runner, or blocked-environment gates are not passes.
- Owner-directed infrastructure exceptions must be explicit, package-specific, and recorded as deferred/skipped evidence rather than a false pass.

## Canonical package index

| ID | Title | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | ---: | --- | --- |
| `ES-R01` | Billing-independent staging bridge recovery | `BLOCKED` | `PARKED_BLOCKED` | 15 | — | repository-side bridge and repairs merged; status publication PR #94; exact blocker is disposable Pi-staging MariaDB connectivity from `Lincoln-PI-4` |
| `ES-P01` | Exact-sanction appeal isolation | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-P02` | Runtime database recovery and Velocity reload | `BLOCKED` | `PARKED_BLOCKED` | 20 | `ES-P01` | PR #70; keep parked until ES-R01 completes the mandatory staging route; do not treat branch drift as actionable |
| `ES-P03` | Bedrock identity correctness | `COMPLETE` | — | 30 | ordinarily `ES-P02`; owner-directed narrow exception | merged PR #75 as `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| `ES-X05` | Website UX, authentication, and appeals | `COMPLETE` | — | 35 | `ES-P01` | merged PR #74 as `2bcf5d46ca6471fddac600f85020c66105b1c0f2` |
| `ES-P04` | Staff-mode operational tools | `COMPLETE` | — | 40 | `ES-P03` | PR #79 merged normally as `a530b992232a8a08cbbd13b0eed6606228ceb652`; Pi staging owner-deferred for later internal verification |
| `ES-P07` | Inventory and Ender editing runtime completion | `PLANNED` | — | 45 | `ES-P02` | dependency blocked |
| `ES-P05` | Report evidence and staff workflow completion | `BLOCKED` | `PARKED_BLOCKED` | 50 | `ES-P03`, `ES-P04` | PR #81; implementation/hosted validation complete at `4a38e191395913c6733726e222f0889a2d56d267`; keep parked until ES-R01 completes the mandatory staging route |
| `ES-P09` | Alt and network-identity completion | `COMPLETE` | — | 55 | `ES-P03` | PR #84 merged normally as `a88201524690848f778297f140f7ee2ba5b6ce36` from frozen head `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`; implementation branch deleted; private representative-network staging remains ES-V02 |
| `ES-P06` | Discord notification delivery completion | `PLANNED` | — | 60 | `ES-P05` | dependency blocked while ES-P05 is parked; dependency retained after deadlock analysis |
| `ES-P08` | Item confiscation and restoration | `PLANNED` | — | 70 | `ES-P07` | dependency blocked |
| `ES-P10` | Cheat tester and fake-entity system | `COMPLETE` | — | 80 | `ES-P04` | PR #86 merged normally as `e605d8ad6094b2ae6842044d209875e13c38906d` from frozen head `1997caa864847049d51bfc58402f019e0a0d65c6`; exact containment verified; implementation branch deleted; representative Java/Bedrock/distributed acceptance remains ES-V02 |
| `ES-P11` | Fake-base generation and cleanup | `COMPLETE` | — | 90 | `ES-P10` | PR #88 merged normally as `6cd293d9f1abc3ca6ca8b70e953da936f4a22ab0` from frozen head `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`; exact containment verified; implementation branch deleted; representative Java/Bedrock/distributed acceptance remains ES-V02 |
| `ES-X01` | RoseChat provider and communication integration | `PLANNED` | — | 100 | `ES-P03`, `ES-P04`, `ES-P05` | dependency blocked while ES-P05 is parked |
| `ES-X02` | EnthusiaCurrency destructive provider | `PLANNED` | — | 110 | `ES-P08` | dependency blocked |
| `ES-X03` | EnthusiaMarket destructive provider | `PLANNED` | — | 120 | `ES-P08`, `ES-X02` | dependency blocked |
| `ES-X04` | EnthusiaCommend reputation provider | `PLANNED` | — | 125 | `ES-P08`, `ES-X02` | dependency blocked |
| `ES-V01` | Private LiteBans representative-data verification | `DEFERRED` | — | 200 | — | private/local environment required |
| `ES-V02` | Distributed and Java/Bedrock staging | `DEFERRED` | — | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | private validation; retains ES-P09 representative-network false-positive/distributed acceptance and ES-P10/ES-P11 Java/Bedrock/distributed acceptance |
| `ES-V03` | Destructive, latency, and load acceptance | `DEFERRED` | — | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | private validation |
| `ES-A01` | LiteBans cutover acceptance | `DEFERRED` | — | 300 | `ES-V01`, `ES-V02`, `ES-V03` | owner authorization and issue #43 required |
| `ES-QA01` | Final repository and workflow audit | `PLANNED` | — | 400 | `ES-A01` | dependency blocked |

## `ES-R01`

- Selected as the package continuation on 2026-08-08 and implemented only validation-infrastructure workflow/tooling changes in `wsg138/EnthusiaStaff` and `wsg138/EnthusiaStaff-Staging`; no product Java or migration changed.
- Public bridge PR #93 merged normally as `094838fa221476e0832cf821f7b4908b9402d0d9` from frozen head `cccadbd1885f78db517ff643f941d04bd0fba2a3`.
- Private staging bridge PR #58 merged normally as `570f83e41cb80b498a82c8b5a509c42345558a46`; bounded database-readiness PR #59 merged as `313ed2815058eadeb8c823453f4152089cae01d4`; PR-target provenance/cleanup fix PR #60 merged as `4036d6e915c2d751bef18849107722dfd1e586a6`.
- The replacement route now proves an ordinary public GitHub-hosted Java 21 build, bounded transient transfer, exact source/run/manifest/digest verification, and allocation of trusted self-hosted runner `Lincoln-PI-4` without requiring a private-repository `ubuntu-latest` build.
- Live merged-main proof `31249125885` reached private run `31249402654` / job `93083246690`, passed provenance, then failed closed at disposable MariaDB pre-reset with SQLState `08000` before Paper boot; cleanup removed the transfer release/tag.
- Corrected PR-target proof `31250170297` reached private run `31250450219` / job `93085892938` on runner ID `2`; exact provenance passed; seven guarded connection attempts all returned SQLState `08000`; Paper never booted; sanitized evidence upload and transient release/tag cleanup succeeded.
- Current package state is therefore `BLOCKED` / `PARKED_BLOCKED`, not complete. No safe repository-side implementation remains.
- Exact unblock: the existing authorized disposable Pi-staging MariaDB endpoint must become reachable from `Lincoln-PI-4` under the current `pi-staging` environment contract. Do not change targets or credentials, remove the reset, allow Paper to boot before reset success, broaden this package into database administration, or repeat identical attempts without material evidence the condition changed.
- After the unblock condition changes, resume ES-R01 before any new package, reconcile both default heads, and run one fresh exact-current-main bridge proof through public build, private provenance, guarded pre-reset, Paper boot cycle 1, restart/cycle 2, guarded post-reset, sanitized evidence, correlated public success, and transfer cleanup. Only then mark ES-R01 `COMPLETE`; do not start ES-P02 in that same worker.
- Canonical contract: `ai-agents/work-packages/packages/ES-R01.md`.
- Canonical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-08-es-r01-blocked-staging-database.md`.
- Documentation-only persistent blocked-state publication: PR #94, branch `package/es-r01-proof-retry-checkpoint`.

## `ES-P02`

- Remains `BLOCKED` / `PARKED_BLOCKED` on PR #70.
- Its implementation and earlier hosted validation remain preserved; its branch/PR drift behind newer `main` does not make it actionable while the staging prerequisite is unavailable.
- The former private-hosted billing deadlock is no longer the only relevant condition: ES-R01's repository-side bridge is merged, but mandatory Pi staging still cannot pass while the authorized disposable staging MariaDB endpoint is unavailable.
- Resume ES-P02 only after ES-R01 is `COMPLETE`. Then reconcile newer `main`, freeze the resulting exact head, rerun every required hosted/review/staging gate through the completed bridge, merge normally if green, verify containment/finalization, and stop.

## `ES-P04`

- Starting legitimate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Frozen reviewed/validated head: `15d9428eba454e9ae4a905752129bd18676acdb1`.
- PR #79 merged normally as `a530b992232a8a08cbbd13b0eed6606228ceb652`.
- Scope completed for random teleport, inspector, freeze, reports, follow/spectate, vanish, staff chat, tools menu, and shared staff-mode safety/recovery. Cheat Tester remains ES-P10; fake bases are completed by ES-P11.
- Review: zero valid unresolved threads. Deferred Cheat Tester issuance, cooldown boundaries, follow/spectate feedback, compile drift, and Codacy test-structure findings were resolved. Scheduler-helper consolidation was explicitly withdrawn as optional maintainability follow-up.
- Exact-head hosted evidence: Wiki run `31178353549`, job `92865432750`, success; Coverage run `31178353504`, job `92865439305`, success on GitHub-hosted Java 21 including full build/tests/coverage/runtime-JAR inspection; Codacy static `92865800728` success with zero issues; coverage variation `92867049954` and diff coverage `92867049338` success.
- Exact-head private staging did not execute product code: public wrapper `31178352312` dispatched private run `31178359804`; required Ubuntu build `92865456267` had runner ID `0`, empty runner name, steps `[]`, and GitHub's Billing & plans rejection; Pi `92865494913` skipped.
- **Owner-approved ES-P04 infrastructure exception:** on 2026-08-07 the owner instructed the worker to continue, mark Pi staging skipped/deferred, and record an internal follow-up to run it later when available. This is not a staging pass and does not generalize to other packages.
- Internal follow-up: when a policy-valid staging path is available, rerun ES-P04 Pi boot/restart staging against the merged behavior and record the result. Reopen ES-P04 only if that later test exposes a real defect.
- V17 remains immutable; ES-P04 added no migration.

## `ES-P05`

- Selected on 2026-08-07 from legitimate `main` `bf9b305ba96d9536f3d111c79eef674bd2e11dc5`; implementation PR #81 remains open on `package/es-p05-report-workflow`.
- Frozen implementation / hosted-validation head: `4a38e191395913c6733726e222f0889a2d56d267`.
- Implemented provider-independent report review completion: dedicated sensitive-evidence permission, bounded staff-only `/reports evidence` presentation, coordinate/privacy separation from broad GUI triage, strict client-evidence allow-listing, newest-snapshot default, explicit no-direct-attachment boundary, direct wiring/privacy tests, MariaDB restart durability proof, and Wiki/operator documentation. Existing durable cooldown/merge/replay/stale-revision/concurrency/rollback/purge foundations were preserved and revalidated.
- Exact-head hosted evidence: Wiki `31183192145` / `92881243088` success; Coverage `31183192068` / `92881313210` success on GitHub-hosted Java 21 including full build/tests/MariaDB/Testcontainers/migration checks/coverage/runtime-JAR inspection; artifact `8995826742`, digest `sha256:ed87314d5eda8286928ce64f11027240898a0823333c6ffa5aa6d98f1697dbe4`; Codacy static `92882185524` success with zero issues; coverage variation `92882989470` success; diff coverage `92882989439` success.
- Final diff was harshly self-reviewed; three found issues were fixed before freeze: broad GUI coordinate exposure, raw nested AutoClicker serialization, and oldest-snapshot default selection. Zero inline review threads remain. The live exact-head commit status reports CodeRabbit success.
- Its old private-hosted build evidence remains **NOT A PASS**. ES-R01 removed that repository-side build dependency, but ES-P05 still cannot obtain mandatory Pi boot/restart staging while ES-R01 itself is blocked on the authorized disposable MariaDB endpoint.
- Resume ES-P05 only after ES-R01 completes and after higher-priority ES-P02 is resolved or becomes parked for a different unchanged external condition. Reconcile newer `main`, rerun every exact-head review/static/hosted/staging gate, merge normally if green, verify containment/finalization, and stop.
- V17 remains immutable for ES-P05; it added no migration. RoseChat PM capture remains ES-X01; Discord route delivery remains ES-P06; issue #43 remains deferred.
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
- Continuation review resolved strict nested config handling, recovery polling load, tagged-tool damage leakage, status privacy, inventory mutation guards, non-finite settings, snapshot validation ordering, ProtocolLib runtime/linkage/cleanup failure semantics, stale delayed fake spawns, cross-backend durable recovery, persistence IDs/JSON/payload sizing, cancellation/journal ordering, controlling-staff evidence contamination, and static size/direct-coverage findings. Zero valid unresolved review threads remained at merge.
- Protocol/API decision: ProtocolLib 5.4.0 behind `FakeEntityAdapter`; provider/packet/cleanup failures fail closed; no unisolated NMS dependency.
- Exact frozen-head hosted evidence: Wiki check `93015435354` success; workflow run `31224336640` successful unchanged-head rerun job `93016497496` on Java 21 with build/tests/MariaDB/Testcontainers/migrations/aggregate JaCoCo/runtime-JAR inspection/artifact/Codacy coverage upload; artifact `9011777818`, digest `sha256:4557877edff2589065483f4d2d81f0c231511001ad04ea82d5b36cfbba1c762a`; aggregate coverage 2,576/5,427 lines and 796/1,887 branches; Codacy static `93015840928` success with zero issues; coverage variation `93017546035` success at `-0.98%` against the `-1.0%` limit; diff coverage `93017545807` success at `29.91%` with no defined gate.
- The first attempt of workflow run `31224336640` hit an unrelated MariaDB concurrent-update failure in the already-merged punishment-request-alert integration test. No ES-P10 code changed in response; failed jobs were rerun on the exact unchanged SHA and the complete hosted job then succeeded. Only the clean unchanged-head rerun is counted as passing evidence.
- Exact frozen-head private staging remains **NOT A PASS** and is assigned to ES-V02: private run `31224339373`; Ubuntu build `93015447468` runner ID `0`, empty runner name, steps `[]`; Pi `93015487565` skipped. No ES-P10 owner-approved infrastructure exception was claimed and no unavailable gate was relabeled as success.
- V18 is now the aggregate `main` migration boundary; V1–V17 remain unchanged.
- Explicit exclusions remain: fake bases / `AUD-TESTER-003` were completed by ES-P11; automatic punishment, production use, unsupported packet/NMS internals, deployment/cutover/private data remain excluded.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p10-cheat-testers.md`.

## `ES-P11`

- Selected as the only live `ACTIONABLE_CONTINUATION` on PR #88 / `package/es-p11-fake-bases`; ES-P02 and ES-P05 remained unchanged `PARKED_BLOCKED` and exactly one package was worked.
- Starting legitimate `main`: `68a6d936066383f5b8139304f40b2d01d0dfe036`.
- Frozen implementation head: `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`.
- Completed `AUD-TESTER-003` as a fixed bounded client-only fake base: one already-loaded target chunk; real-air/safe-floor/build-height checks; no chunk generation and no real block mutation; final same-chunk guard for Folia ownership; exact global/per-staff/target bounds; target/authorized-staff viewer isolation; five-minute timeout and warning; pre-expiry-only Extend; Clear/Teleport/Status; distance/world/backend/disconnect/staff-mode/render/scheduler/plugin-lifecycle cleanup; authoritative real-block re-send; coordinate-free durable audit through existing `audit_events`; command/text fallback and explicit permissions.
- Direct tests cover template/placement/lifecycle/audit/metadata contracts. MariaDB/Testcontainers integration proves production audit binding, coordinate-free JSON, and unchanged V18 migration boundary. Wiki and implementation evidence are complete.
- Manual self-review and actual CodeRabbit reviews resolved approximate admission, canonical JSON, expiry consistency, worker-thread Bukkit access, render/restore failure handling, cross-chunk Folia reads, stale async authority/manage-any privilege, accepted-vs-committed control evidence, lifecycle scheduler failure, render/close races, permissions/docs/tests, unreachable tab completion, expired-operation extension, exact merge-gate wording, and routing-state requirements. Codacy remediation reduced static findings 23 → 9 → 5 → 1 → 0.
- Frozen exact-head Wiki run `31241442832` / job `93063008371` succeeded. Coverage/build run `31241442786` / job `93063008372` succeeded on the exact SHA with Java 21 build/tests, MariaDB/Testcontainers, migration checks, runtime-JAR inspection, artifact publication, JaCoCo, and Codacy upload; aggregate coverage 47.01% lines / 38.14% branches / 49.64% instructions.
- Paper JAR: 9,123,435 bytes, SHA-256 `0c3d66fb328a041c650968f39d83d4015340142b438200417db30005fa3448fb`; Velocity JAR: 7,863,915 bytes, SHA-256 `79fda01365bae9cfdc9faf75e2c1bfbc068bd43eec82d84c065cbf5a25bbfad9`; provider API leaks 0. Artifact `9017217821`, digest `sha256:9077a5e6054002663cc0588b7cb87b32de7869ec2881996488e8c06b500b3397`.
- Codacy static `93063097134` succeeded with zero annotations; Diff Coverage `93063654061` succeeded at 26.67% with no configured gate; Coverage Variation `93063654099` succeeded at -0.45% against the -1.0% target.
- Exact-head manual reviewer completion `4888204151` is PASS with zero valid unresolved findings. CodeRabbit's requested final one-file rerun was quota-limited and is not represented as an exact-head CodeRabbit pass. All live review threads were resolved/outdated.
- Exact-head private staging remains **NOT A PASS** and remains assigned to ES-V02: public wrapper `31241441649` / `93063005569` dispatched private run `31241446283`; required Ubuntu job `93063018565` had runner ID `0`, empty runner name, `steps: []`, and the Billing & plans rejection; Pi `93063023369` skipped. No product validation step executed and no ES-P11 infrastructure exception was claimed.
- PR #88 merged normally as `6cd293d9f1abc3ca6ca8b70e953da936f4a22ab0`. The merge has exactly two parents: pre-merge `main` `68a6d936066383f5b8139304f40b2d01d0dfe036` and frozen head `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`. Resulting `main` is one merge commit ahead of the frozen head, zero behind, with no file differences.
- `package/es-p11-fake-bases` was automatically deleted after containment verification and returns 404. External parity is not applicable.
- V18 remains immutable; ES-P11 added no migration. Production deployment/cutover, issue #43 acceptance, private data, and ES-V02 execution remain excluded/deferred.
- Canonical implementation evidence: `reports/ES-P11-FAKE-BASE-IMPLEMENTATION.md`.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p11-fake-bases.md`.

## Next-worker boundary

After the documentation-only PR #94 is merged normally, `ES-R01` is correctly parked as `BLOCKED` / `PARKED_BLOCKED`. If the existing authorized disposable Pi-staging MariaDB endpoint is still unreachable from `Lincoln-PI-4`, there is no `ACTIONABLE_CONTINUATION` and no dependency-complete `READY` package; a sequential worker must report the blockers and stop rather than starting unrelated planned/deferred work. If material evidence shows that database availability changed, resume ES-R01 before any new package. ES-P02 and ES-P05 remain parked until ES-R01 is complete. No unavailable gate is treated as passed.
