# Package registry

Last updated: 2026-08-09

Live GitHub overrides stale text. Historical evidence is retained in each package file and its canonical handoffs; this registry is the current routing authority.

## Rules

- Classify every incomplete package before selection and work exactly one package per worker.
- Existing `ACTIONABLE_CONTINUATION` work takes priority over newly `READY` work.
- Use normal merge commits only; never relabel missing, skipped, cancelled, superseded, wrong-revision, or failed validation as passing evidence.
- Do not weaken staging, provenance, migration, review, static-analysis, privacy, or production-authority boundaries.
- Issue #43 remains open/deferred and LiteBans remains authoritative until separately approved.

## Canonical current state

`ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, and `ES-R02` are `COMPLETE`.

There is no active package after the ES-P05 terminal completion record merges. `ES-P07` is the lowest-priority-number dependency-complete `READY` package and is the exact next normal sequential-worker selection. ES-P05 completion also makes `ES-P06` and `ES-X01` dependency-complete and `READY`; neither is activated by the ES-P05 terminal worker.

## Canonical package index

| ID | Title | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | ---: | --- | --- |
| `ES-P01` | Exact-sanction appeal isolation | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-R01` | Billing-independent staging bridge recovery | `COMPLETE` | — | 15 | — | canonical public→private staging route proven; terminal handoff retained |
| `ES-R02` | Report integration fixture clock recovery | `COMPLETE` | — | 16 | — | PR #103 merged normally as `5220f21a44527fdd54bb469c767c40a2f232b171` |
| `ES-P02` | Runtime database recovery and Velocity reload | `COMPLETE` | — | 20 | `ES-P01` | PR #70 merged normally as `df9f4bf39ceda3911b7c084ac0c2caa188b82c7c` after exact hosted/review/canonical Pi proof |
| `ES-P03` | Bedrock identity correctness | `COMPLETE` | — | 30 | ordinarily `ES-P02`; owner-directed narrow exception | merged PR #75 as `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| `ES-X05` | Website UX, authentication, and appeals | `COMPLETE` | — | 35 | `ES-P01` | merged PR #74 as `2bcf5d46ca6471fddac600f85020c66105b1c0f2` |
| `ES-P04` | Staff-mode operational tools | `COMPLETE` | — | 40 | `ES-P03` | merged PR #79 as `a530b992232a8a08cbbd13b0eed6606228ceb652` |
| `ES-P07` | Inventory and Ender editing runtime completion | `READY` | `READY` | 45 | `ES-P02` | exact next normal sequential package; not activated by ES-P05 terminal worker |
| `ES-P05` | Report evidence and staff workflow completion | `COMPLETE` | — | 50 | `ES-P03`, `ES-P04` | frozen head `9e6d5f8afc120b76f5f396a2e3e279bc5f851c85`; PR #81 merged normally as `52c0dc47efdc2296827b4b6b743d01a86f72c856` |
| `ES-P09` | Alt and network-identity completion | `COMPLETE` | — | 55 | `ES-P03` | merged PR #84 as `a88201524690848f778297f140f7ee2ba5b6ce36`; representative-network acceptance remains ES-V02 |
| `ES-P06` | Discord notification delivery completion | `READY` | `READY` | 60 | `ES-P05` | dependency completed by ES-P05; do not activate before lower-priority-number ES-P07 |
| `ES-P08` | Item confiscation and restoration | `PLANNED` | — | 70 | `ES-P07` | dependency blocked until ES-P07 completes |
| `ES-P10` | Cheat tester and fake-entity system | `COMPLETE` | — | 80 | `ES-P04` | merged PR #86 as `e605d8ad6094b2ae6842044d209875e13c38906d`; representative acceptance remains ES-V02 |
| `ES-P11` | Fake-base generation and cleanup | `COMPLETE` | — | 90 | `ES-P10` | merged PR #88 as `6cd293d9f1abc3ca6ca8b70e953da936f4a22ab0`; representative acceptance remains ES-V02 |
| `ES-X01` | RoseChat provider and communication integration | `READY` | `READY` | 100 | `ES-P03`, `ES-P04`, `ES-P05` | dependencies complete; not activated by ES-P05 terminal worker |
| `ES-X02` | EnthusiaCurrency destructive provider | `PLANNED` | — | 110 | `ES-P08` | dependency blocked |
| `ES-X03` | EnthusiaMarket destructive provider | `PLANNED` | — | 120 | `ES-P08`, `ES-X02` | dependency blocked |
| `ES-X04` | EnthusiaCommend reputation provider | `PLANNED` | — | 125 | `ES-P08`, `ES-X02` | dependency blocked |
| `ES-V01` | Private LiteBans representative-data verification | `DEFERRED` | — | 200 | — | private/local environment required |
| `ES-V02` | Distributed and Java/Bedrock staging | `DEFERRED` | — | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | private validation plus incomplete dependencies |
| `ES-V03` | Destructive, latency, and load acceptance | `DEFERRED` | — | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | private validation plus incomplete dependencies |
| `ES-A01` | LiteBans cutover acceptance | `DEFERRED` | — | 300 | `ES-V01`, `ES-V02`, `ES-V03` | owner authorization and issue #43 required |
| `ES-QA01` | Final repository and workflow audit | `PLANNED` | — | 400 | `ES-A01` | dependency blocked |

## `ES-P05` terminal record

- Worker-start `main`: `7d438988e2ca6a681b5fba82f25377fc9de8df84`; resumed existing PR #81 from historical head `346e764f40b25c98e7d24ce7f863e5629773e814` after the staging freshness/provenance unblock condition materially changed.
- Current-main synchronization/frozen implementation head: `9e6d5f8afc120b76f5f396a2e3e279bc5f851c85`; zero commits behind the selected base and an eight-file live ES-P05 diff because the former integration-fixture repair was already on `main`.
- Validate Wiki `31348316809`: success.
- Coverage `31348316817` / job `93334330436`: success; Java 21 full build/tests, MariaDB/Testcontainers, aggregate coverage, runtime-JAR inspection and validation-artifact upload all passed.
- Hosted validation artifact `9048186426`, digest `sha256:62c9ad1cad86fa2e5189445a9a9ff5f5b6761f1b19d4c25ebacbea5860d4fff9`.
- Codacy static `93334591193`: success, zero annotations. Codacy diff coverage `93335202690`: success at 42.96%; coverage variation `93335202668`: success at +0.03% against the -1.0% target.
- CodeRabbit exact-head status: success; all three historical substantive threads resolved; valid unresolved thread count zero.
- Canonical Pi public run `31348316060`: build `93334328455` success; bridge `93335216891` success including private correlation and transient public transfer cleanup.
- Correlated private run `31348651990` / job `93335243975`: success on trusted `Lincoln-PI-4`, runner ID `2`. Exact bridge provenance/freshness passed; guarded disposable Paper boot/restart passed; two storage-ready cycles passed in `SHADOW_MIGRATION`; first cycle applied V1–V18, second verified schema v18 current; both clean shutdowns passed; guarded pre/post DB reset passed; failure count zero.
- Sanitized Pi evidence artifact `9048272564`, digest `sha256:55f61484144ef1911e81678ba26d6ca910d915375f0a0425f9d707a4d8904234`.
- PR #81 merged normally as `52c0dc47efdc2296827b4b6b743d01a86f72c856`. Feature-head containment is proven (`ahead_by=1`, `behind_by=0`, no file delta). GitHub auto-deleted `package/es-p05-report-workflow` after merge.
- ES-P05 adds no migration; V18 remains current and immutable. RoseChat PM integration remains ES-X01; Discord route delivery remains ES-P06; representative distributed/Bedrock acceptance remains ES-V02.
- Issue #43 remains deferred; LiteBans remains authoritative; no production data, credentials, infrastructure authority, cutover, or deployment was changed.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-09-es-p05-report-workflow-complete.md`.

## `ES-P02` retained terminal record

- Frozen implementation head `90f78f902a25039515d883ca96a1b72c2265418d` passed fresh Coverage, Codacy/review and canonical Pi public/private proof before PR #70 merged normally as `df9f4bf39ceda3911b7c084ac0c2caa188b82c7c`.
- V18 remained current/immutable; the package added no migration.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-09-es-p02-runtime-database-recovery-complete.md`.

## Next sequential action

Start a new normal sequential worker and classify live state again. Absent a new higher-priority actionable continuation, select `ES-P07 — Inventory and Ender editing runtime completion` as the lowest-priority-number dependency-complete `READY` package. `ES-P06` and `ES-X01` are also `READY` but must not be started by this ES-P05 terminal worker.