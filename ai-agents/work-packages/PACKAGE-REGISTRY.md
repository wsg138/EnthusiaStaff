# Package registry

Last updated: 2026-08-09

Canonical current state: `ES-P01`, `ES-P03`, `ES-P04`, `ES-P09`, `ES-P10`, `ES-P11`, and `ES-X05` are `COMPLETE`; `ES-R02` is the owner-directed baseline-build recovery whose terminal `COMPLETE` state is published when exact validated PR #103 merges normally; `ES-R01`, `ES-P02`, and `ES-P05` remain `BLOCKED` / `PARKED_BLOCKED` during this worker. No normal product package is `READY`. Issue #43 remains open, deferred, and excluded.

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
| `ES-P01` | Exact-sanction appeal isolation | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-R01` | Billing-independent staging bridge recovery | `BLOCKED` | `PARKED_BLOCKED` | 15 | — | shared freshness repair is merged; current-main trusted-build blocker is being independently repaired by owner-directed ES-R02. Do not resume ES-R01 in this worker. After ES-R02 merges green, the exact unblock condition materially changes and the next worker must resume ES-R01 first for one fresh exact-current-main canonical proof |
| `ES-R02` | Report integration fixture clock recovery | `COMPLETE` on exact validated PR #103 merge | owner-directed recovery | 16 | — | branch `package/es-r02-report-fixture-clock-recovery`; only the stale Report integration fixture clock plus canonical ES-R02 records; no product-feature scope, no ES-P05 merge/advance; terminal publication becomes authoritative only when PR #103 merges normally |
| `ES-P02` | Runtime database recovery and Velocity reload | `BLOCKED` | `PARKED_BLOCKED` | 20 | `ES-P01` | PR #70 remains parked and untouched in ES-R02 |
| `ES-P03` | Bedrock identity correctness | `COMPLETE` | — | 30 | ordinarily `ES-P02`; owner-directed narrow exception | merged PR #75 as `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| `ES-X05` | Website UX, authentication, and appeals | `COMPLETE` | — | 35 | `ES-P01` | merged PR #74 as `2bcf5d46ca6471fddac600f85020c66105b1c0f2` |
| `ES-P04` | Staff-mode operational tools | `COMPLETE` | — | 40 | `ES-P03` | PR #79 merged normally as `a530b992232a8a08cbbd13b0eed6606228ceb652`; Pi staging owner-deferred for later internal verification |
| `ES-P07` | Inventory and Ender editing runtime completion | `PLANNED` | — | 45 | `ES-P02` | dependency blocked |
| `ES-P05` | Report evidence and staff workflow completion | `BLOCKED` | `PARKED_BLOCKED` | 50 | `ES-P03`, `ES-P04` | PR #81 remains open/unmerged and untouched at `346e764f40b25c98e7d24ce7f863e5629773e814`; ES-R02 copies only the independently valid fixture-clock repair into current-main lineage and makes no ES-P05 completion claim |
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

- Owner-directed continuation on 2026-08-09 overrode the stale parked routing after live evidence proved the old MariaDB-unreachable condition had materially changed. No ES-P02, ES-P05, or other product implementation was modified.
- Historical proof of the changed database condition: ES-P05 source `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2`, public run `31301426684`, private run `31301734048` / job `93215499833`, trusted `Lincoln-PI-4` runner ID `2`, exact provenance PASS, guarded pre-reset PASS, two storage-ready Paper cycles through Flyway V18, clean shutdown/reap after each cycle, restart/persistence PASS, final guarded cleanup PASS, sanitized artifact `9034945235` digest `sha256:a81af3154c7e561c5ea09ed7072c970d483b25b63e217f34f1976132bab4ef3e`.
- Newly exposed ES-P05 transfer failure: public run `31330788773`, public build job `93288608088` success, bridge `93289540403`, private run `31331175023` / job `93289556545` on trusted runner ID `2`; private verifier failed before DB/Paper with `release created_at is expired for the staging bridge`; sanitized diagnostic artifact `9042975898`, digest `sha256:1b470d2f20a6f263ed424734ff9397663077bbeec2d3ea942e3981ed926d7a93`; public cleanup removed the transient release/tag.
- Root cause confirmed against GitHub REST semantics and live metadata: Release `created_at` is based on the commit used for the release and is not a publication timestamp. The verifier's two-hour transfer TTL therefore falsely rejected a newly published transient release when its target commit was older than two hours.
- Repair model: Release `published_at` is mandatory for release-publication freshness; Release Asset `created_at` continues to enforce independent upload freshness; existing two-hour max age, five-minute future skew, exact release/tag/asset IDs/names, source/workflow/run/PR provenance, repository, digest, size, archive allowlist, manifest and cleanup requirements are retained.
- Staging PR #75 froze at `19e38d6851367d835cfe50fc29e9f95a0936f66d`; exact-content Staging Controls run `31332576934` / job `93293056853` passed on `Lincoln-PI-4`, including all requested freshness/provenance regression fixtures plus the broader staging controls and 292 Sentinel unit tests. CodeRabbit green; zero unresolved review threads. PR #75 merged normally as `af1bd6d3ae8214e58eb969c23972f872b15c1f18`.
- Public counterpart PR #102 froze at `369a71c84a4fcb1c09cfd43cf32b323ffbc0fbb6` and merged normally as `15d37fa1e49b5d4b8403914b6f7a43892dbe417e`. It changed documentation/canonical state only.
- The newly exposed blocker was upstream of the bridge: baseline public `main` `140d10ef63f3d6761c95afccbead13db53888304` failed automatic Pi Staging run `31332055336` / public build job `93291754833` in `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()` and `duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()` before artifact creation. ES-R01 documentation-only heads reproduced those same failures.
- ES-R01 explicitly forbids weakening or bypassing the trusted public build. Its exact unblock is material evidence that current `EnthusiaStaff:main` again passes that canonical Java build, including those two tests.
- ES-R02 is the narrow independent vehicle for that prerequisite repair. ES-R01 remains `BLOCKED` / `PARKED_BLOCKED` during this worker and is not allowed to claim ES-R02 validation or private acceptance as its own.
- If exact validated PR #103 merges and current main becomes green, ES-R01 must be reclassified by the **next worker** as `ACTIONABLE_CONTINUATION`, which must then obtain one fresh exact-current-main full canonical public→private proof before ES-R01 may become `COMPLETE`.
- `noop-temp-ignore` retains unique work and remains untouched. V18 remains immutable/current; issue #43 remains deferred; LiteBans authority is unchanged.
- Canonical contract: `ai-agents/work-packages/packages/ES-R01.md`.
- Terminal handoff before ES-R02: `ai-agents/reports/package-handoffs/2026-08-09-es-r01-release-freshness-repaired-public-build-blocked.md`.

## `ES-R02`

- Explicit owner-directed baseline-build deadlock recovery; no normal product package was selected.
- Starting legitimate `main`: `15d37fa1e49b5d4b8403914b6f7a43892dbe417e` after normal merge of PR #102.
- Branch: `package/es-r02-report-fixture-clock-recovery`; PR #103.
- Confirmed circular dependency: ES-R01 requires current-main trusted Java build success; current main fails two ReportStore integration tests; the proven repair exists in unmerged ES-P05 PR #81; ES-P05 itself cannot merge until ES-R01 proves the shared staging path.
- Root cause: current main used `ReportIntegrationFixtures.NOW = Instant.parse("2026-08-01T12:00:00Z")`; default recently-closed/evidence-retention windows are seven days; production reads correctly use live UTC/database time; the fixture aged out as wall time advanced.
- Repair: only the integration fixture imports `ChronoUnit` and changes `NOW` to one JVM-wide `Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS)`. The resulting fixture blob matches ES-P05 head `346e764f40b25c98e7d24ce7f863e5629773e814` exactly.
- Time review: one static clock value; approximately one minute in the past; microsecond precision; lifecycle timestamps remain in the past; explicit expiry fixture remains `NOW - 9 days` against the seven-day policy; no calendar-day/midnight dependence; no assertion or policy weakening.
- No production `JdbcReportStore`/query/transaction/schema behavior, report command/permission/GUI/formatter, Flyway/migration, staging workflow, production data/infrastructure, issue #43 behavior or LiteBans authority changes are in scope.
- PR #81 remains open/unmerged, unsynchronized, unmodified, unstaged and otherwise untouched. The duplicate fixture repair in main and PR #81 is intentional; a later ES-P05 worker will reconcile normal Git history.
- Exact-head hosted/review/static evidence is recorded on PR #103 after the tracked head freezes, per the anti-self-referential validation rule. Full private ES-R01 acceptance is not an ES-R02 claim.
- Terminal `COMPLETE` state in this record becomes authoritative only when the exact final PR #103 head is hosted-green, review/static-green, normally merged, contained by main, and verified not to include ES-P05 feature code.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-09-es-r02-report-fixture-clock-recovery.md`.

## `ES-P02`

- Remains `BLOCKED` / `PARKED_BLOCKED` on PR #70 and is untouched by ES-R02.
- Its implementation and earlier hosted validation remain preserved. Do not resume it before the post-ES-R02 ES-R01 continuation.

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

- Resumed/finalized on 2026-08-09 as the existing `ACTIONABLE_CONTINUATION`; no competing ES-P05 implementation PR was created.
- Finalization-worker starting `main`: `e210df15ed90cf96757516fd363faa801bc7192c`.
- Existing implementation PR #81 remains open on `package/es-p05-report-workflow`; ES-R02 does not modify, synchronize, rerun staging for, merge, or delete it.
- The previous head `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2` later received genuine canonical Pi runtime success: public run `31301426684`, private run `31301734048` / job `93215499833` on trusted `Lincoln-PI-4`, guarded pre-reset, two clean storage-ready Paper cycles, v18 restart/persistence proof, post-cleanup and sanitized artifact `9034945235` (`sha256:a81af3154c7e561c5ea09ed7072c970d483b25b63e217f34f1976132bab4ef3e`).
- Current `main` was merged normally into PR #81 through two-parent merge `f052fd8177f5b5eed6bc75111411e10b0ced66db`; no rebase/force/squash. The implementation diff remained exactly nine ES-P05 report files and zero behind `main` at that point.
- Fresh Codacy maintainability findings were resolved through bounded static-only commits `b1bcf1d8422115de95d1e8233f5ca3f55325b373` and `346e764f40b25c98e7d24ce7f863e5629773e814`. Final parked candidate remains `346e764f40b25c98e7d24ce7f863e5629773e814`.
- ReportStore root cause in ES-P05 remains the stale integration fixture clock, not production persistence. The fixture uses current time minus one minute at microsecond precision; no production query/state/transaction/policy/schema behavior changed and no assertion was weakened.
- Provider-independent report review implementation remains: dedicated sensitive-evidence permission, bounded staff-only `/reports evidence`, coordinate/privacy separation, strict scalar/field allow-listing, delivery-time permission recheck, newest-snapshot default, explicit no-direct-attachment boundary, direct wiring/privacy tests, MariaDB restart durability proof, and Wiki/operator documentation.
- Exact final-head hosted evidence: Wiki `31330790907` success; Coverage `31330790942` / `93288609588` success on Java 21 including full build/tests/ReportStore integration/MariaDB/Testcontainers/migration validation/coverage/runtime-JAR inspection; Codacy static `93288694739` success with zero annotations; Codacy diff coverage `93289365775` success at 42.96% with no defined gate.
- Final automatic public Pi run `31330788773`: public build `93288608088` passed and uploaded the exact runtime. Bridge `93289540403` successfully downloaded the artifact, created the bounded transient release/asset, dispatched/correlated private run `31331175023`, collected failed-run diagnostics and removed the transient release/tag.
- Correlated private job `93289556545` allocated trusted `Lincoln-PI-4` runner ID `2` and passed the runner assertion, but failed `Retrieve and verify exact public bridge artifact` with `release created_at is expired for the staging bridge`. Guarded database reset and all Paper/restart/persistence/cleanup runtime assertions were skipped. Sanitized diagnostic artifact `9042975898` has digest `sha256:1b470d2f20a6f263ed424734ff9397663077bbeec2d3ea942e3981ed926d7a93`.
- ES-R01 has now confirmed and repaired that shared freshness defect; ES-R02 independently repairs only the baseline fixture clock. ES-P05 itself has not been rerun and remains parked pending a later worker after ES-R01 completion.
- V18 remains immutable/current; RoseChat PM capture remains ES-X01; Discord route delivery remains ES-P06; issue #43 remains deferred.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-09-es-p05-final-staging-prereq-blocked.md`.

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
- Exact frozen-head private staging remains **NOT A PASS** and remains assigned to ES-V02: public wrapper `31241441649` / `93063005569` dispatched private run `31241446283`; required Ubuntu job `93063018565` had runner ID `0`, empty runner name, `steps: []`, and the Billing & plans rejection; Pi `93063023369` skipped. No product validation step executed and no ES-P11 infrastructure exception was claimed.
- PR #88 merged normally as `6cd293d9f1abc3ca6ca8b70e953da936f4a22ab0`. The merge has exactly two parents: pre-merge `main` `68a6d936066383f5b8139304f40b2d01d0dfe036` and frozen head `a3192dd5f684d402b79dfee2de3f32e18af7c9c4`. Resulting `main` is one merge commit ahead of the frozen head, zero behind, with no file differences.
- `package/es-p11-fake-bases` was automatically deleted after containment verification and returns 404. External parity is not applicable.
- V18 remains immutable; ES-P11 added no migration. Production deployment/cutover, issue #43 acceptance, private data, and ES-V02 execution remain excluded/deferred.
- Canonical implementation evidence: `reports/ES-P11-FAKE-BASE-IMPLEMENTATION.md`.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-07-es-p11-fake-bases.md`.

## Next-worker boundary

This worker stops after exact validated PR #103 merges and containment is verified. It does not begin another package.

At that point, current-main trusted Java build success materially changes ES-R01's exact unblock condition. The **next worker** must reclassify `ES-R01 — Billing-independent staging bridge recovery` as `ACTIONABLE_CONTINUATION` and resume it before ES-P02, ES-P05, ES-P06, ES-X01, or any new product package.

That ES-R01 worker must obtain one fresh exact-current-main canonical public→private proof through public Java build → bounded transient transfer → corrected Release `published_at` / asset freshness verification → trusted `Lincoln-PI-4` → guarded DB pre-reset → Paper cycle 1 → clean shutdown/reap → Paper cycle 2/restart-persistence → clean shutdown/reap → final DB cleanup → sanitized evidence → public correlation success → transfer cleanup. Only then may ES-R01 become `COMPLETE`; only a later worker may then resume ES-P05.
