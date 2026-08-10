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

`ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, and `ES-R02` are `COMPLETE`.

`ES-P05` is the highest-priority existing `ACTIONABLE_CONTINUATION` and must be resumed next through its existing PR #81. `ES-P07` is now dependency-complete and `READY`, but it remains behind the existing ES-P05 continuation under `WORKER-PROTOCOL.md`. No next package is activated by the ES-P02 terminal worker.

## Canonical package index

| ID | Title | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | ---: | --- | --- |
| `ES-P01` | Exact-sanction appeal isolation | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-R01` | Billing-independent staging bridge recovery | `COMPLETE` | — | 15 | — | canonical public→private staging route proven; terminal handoff retained |
| `ES-R02` | Report integration fixture clock recovery | `COMPLETE` | — | 16 | — | PR #103 merged normally as `5220f21a44527fdd54bb469c767c40a2f232b171`; post-merge Coverage passed |
| `ES-P02` | Runtime database recovery and Velocity reload | `COMPLETE` | — | 20 | `ES-P01` | frozen head `90f78f902a25039515d883ca96a1b72c2265418d`; PR #70 merged normally as `df9f4bf39ceda3911b7c084ac0c2caa188b82c7c`; exact hosted/Codacy/review/canonical Pi gates passed |
| `ES-P03` | Bedrock identity correctness | `COMPLETE` | — | 30 | ordinarily `ES-P02`; owner-directed narrow exception | merged PR #75 as `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| `ES-X05` | Website UX, authentication, and appeals | `COMPLETE` | — | 35 | `ES-P01` | merged PR #74 as `2bcf5d46ca6471fddac600f85020c66105b1c0f2` |
| `ES-P04` | Staff-mode operational tools | `COMPLETE` | — | 40 | `ES-P03` | merged PR #79 as `a530b992232a8a08cbbd13b0eed6606228ceb652`; recorded package-specific Pi deferral remains historical |
| `ES-P07` | Inventory and Ender editing runtime completion | `READY` | `READY` | 45 | `ES-P02` | dependency is now complete; do not activate before the existing ES-P05 continuation is handled |
| `ES-P05` | Report evidence and staff workflow completion | `PARTIAL` | `ACTIONABLE_CONTINUATION` | 50 | `ES-P03`, `ES-P04` | existing PR #81 remains open/non-draft at `346e764f40b25c98e7d24ce7f863e5629773e814`; resume this same package next and reconcile current `main` plus the repaired canonical staging path |
| `ES-P09` | Alt and network-identity completion | `COMPLETE` | — | 55 | `ES-P03` | merged PR #84 as `a88201524690848f778297f140f7ee2ba5b6ce36`; representative-network acceptance remains ES-V02 |
| `ES-P06` | Discord notification delivery completion | `PLANNED` | — | 60 | `ES-P05` | dependency blocked while ES-P05 is incomplete |
| `ES-P08` | Item confiscation and restoration | `PLANNED` | — | 70 | `ES-P07` | dependency blocked until ES-P07 completes |
| `ES-P10` | Cheat tester and fake-entity system | `COMPLETE` | — | 80 | `ES-P04` | merged PR #86 as `e605d8ad6094b2ae6842044d209875e13c38906d`; representative acceptance remains ES-V02 |
| `ES-P11` | Fake-base generation and cleanup | `COMPLETE` | — | 90 | `ES-P10` | merged PR #88 as `6cd293d9f1abc3ca6ca8b70e953da936f4a22ab0`; representative acceptance remains ES-V02 |
| `ES-X01` | RoseChat provider and communication integration | `PLANNED` | — | 100 | `ES-P03`, `ES-P04`, `ES-P05` | dependency blocked while ES-P05 is incomplete |
| `ES-X02` | EnthusiaCurrency destructive provider | `PLANNED` | — | 110 | `ES-P08` | dependency blocked |
| `ES-X03` | EnthusiaMarket destructive provider | `PLANNED` | — | 120 | `ES-P08`, `ES-X02` | dependency blocked |
| `ES-X04` | EnthusiaCommend reputation provider | `PLANNED` | — | 125 | `ES-P08`, `ES-X02` | dependency blocked |
| `ES-V01` | Private LiteBans representative-data verification | `DEFERRED` | — | 200 | — | private/local environment required |
| `ES-V02` | Distributed and Java/Bedrock staging | `DEFERRED` | — | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | private validation |
| `ES-V03` | Destructive, latency, and load acceptance | `DEFERRED` | — | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | private validation |
| `ES-A01` | LiteBans cutover acceptance | `DEFERRED` | — | 300 | `ES-V01`, `ES-V02`, `ES-V03` | owner authorization and issue #43 required |
| `ES-QA01` | Final repository and workflow audit | `PLANNED` | — | 400 | `ES-A01` | dependency blocked |

## `ES-P02` terminal record

- Frozen implementation head: `90f78f902a25039515d883ca96a1b72c2265418d`.
- Fresh exact-head Coverage run `31342778279`, job `93319183473`: `SUCCESS`; artifact `9046404003`, digest `sha256:fc61861c9e1a49270d8686350f791f4ad5d63ef1c4e94f5e81aad89ab6e4f598`.
- Codacy Static Code Analysis `93313758400`: `SUCCESS`, zero new issues/annotations.
- All three substantive CodeRabbit threads resolved; valid unresolved thread count zero.
- Fresh canonical Pi public run `31342778432`: build `93319183919` and bridge `93319918461` succeeded.
- Correlated private run `31343077935`, job `93319937672`: `SUCCESS` on trusted `Lincoln-PI-4`, runner ID `2`.
- Private evidence artifact `9046774374`, digest `sha256:d51574d879a0c3271947d9d1422bc27b24d006703a0e659b7132c0228c4d1ac6`.
- Two storage-ready Paper cycles passed; first applied/verified V1–V18, second verified schema v18; both entered `SHADOW_MIGRATION`; clean shutdown/reap and guarded pre/post database cleanup passed; failure count zero; transient public transfer cleanup passed.
- Earlier SIGKILL-under-memory-pressure and rerun-attempt manifest-mismatch executions remain recorded as superseded failure evidence, not passes.
- PR #70 merged normally as `df9f4bf39ceda3911b7c084ac0c2caa188b82c7c`. Feature-head containment is proven (`ahead_by=1`, `behind_by=0` from feature head to merge commit) and no open PR depends on the implementation branch.
- ES-P02 adds no migration; V18 remains current/immutable.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-09-es-p02-runtime-database-recovery-complete.md`.

## Next sequential action

Resume `ES-P05 — Report evidence and staff workflow completion` through existing PR #81. Reconcile live `main`, preserve the existing product work, and obtain its own exact-head review/hosted/canonical staging proof. Do not use ES-P02 or ES-R01 acceptance as ES-P05 product acceptance. Do not start ES-P07 in parallel from this terminal worker.
