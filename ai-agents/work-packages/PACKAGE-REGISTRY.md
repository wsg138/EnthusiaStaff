# Package registry

Last updated: 2026-08-06

Canonical current state: `ES-P01` and `ES-P03` are `COMPLETE`; `ES-P02` and `ES-X05` are `BLOCKED` / `PARKED_BLOCKED`; no implementation package is active. ES-P03 completed under the narrow owner-directed routing exception recorded below. Issue #43 remains open, deferred, and excluded.

## Rules

- Live GitHub overrides stale text and must be reconciled back here.
- Classify every incomplete package before selection; select one package and stop after its terminal state is persistently published.
- Park unchanged external blockers; do not keep branches current merely because `main` advanced.
- Owner-directed routing exceptions must be explicit and do not silently complete dependencies or propagate to later packages.
- Use normal merge commits only. Missing, skipped, cancelled, rate-limited, superseded, or zero-runner gates are not passes.

## ES-P03 routing exception

The ordinary graph requires ES-P02 complete before ES-P03. ES-P02 remains `BLOCKED` / `PARKED_BLOCKED`. On 2026-08-06 the repository owner directed the next sequential worker to continue another productive package while ES-P02 and ES-X05 stayed parked. The exception permitted ES-P03 only; it did not alter ES-P02/ES-X05, import their branches, waive ES-P03 gates, or automatically authorize any later package.

## Canonical package index

| ID | Title | Type | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| `ES-P01` | Exact-sanction appeal isolation | Internal | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-P02` | Runtime database recovery and Velocity reload | Internal | `BLOCKED` | `PARKED_BLOCKED` | 20 | `ES-P01` | branch `package/es-p02-runtime-db-recovery`; PR #70; untouched |
| `ES-P03` | Bedrock identity correctness | Internal | `COMPLETE` | — | 30 | ordinarily `ES-P02`; owner-directed narrow exception | PR #75 merged as `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| `ES-X05` | Website UX, authentication, and appeals | External/multi-repository | `BLOCKED` | `PARKED_BLOCKED` | 35 | `ES-P01` | implementation merged; finalization branch `package/es-x05-finalization`; PR #74; untouched |
| `ES-P04` | Staff-mode operational tools | Internal | `PLANNED` | — | 40 | `ES-P03` | unassigned; not activated |
| `ES-P07` | Inventory and Ender editing runtime completion | Internal | `PLANNED` | — | 45 | `ES-P02` | unassigned |
| `ES-P05` | Report evidence and staff workflow completion | Internal | `PLANNED` | — | 50 | `ES-P03`, `ES-P04` | unassigned |
| `ES-P09` | Alt and network-identity completion | Internal | `PLANNED` | — | 55 | `ES-P03` | unassigned |
| `ES-P06` | Discord notification delivery completion | Internal | `PLANNED` | — | 60 | `ES-P05` | unassigned |
| `ES-P08` | Item confiscation and restoration | Internal | `PLANNED` | — | 70 | `ES-P07` | unassigned |
| `ES-P10` | Cheat tester and fake-entity system | Internal | `PLANNED` | — | 80 | `ES-P04` | unassigned |
| `ES-P11` | Fake-base generation and cleanup | Internal | `PLANNED` | — | 90 | `ES-P10` | unassigned |
| `ES-X01` | RoseChat provider and communication integration | External/multi-repository | `PLANNED` | — | 100 | `ES-P03`, `ES-P04`, `ES-P05` | unassigned; repository unresolved |
| `ES-X02` | EnthusiaCurrency destructive provider | External/multi-repository | `PLANNED` | — | 110 | `ES-P08` | unassigned |
| `ES-X03` | EnthusiaMarket destructive provider | External/multi-repository | `PLANNED` | — | 120 | `ES-P08`, `ES-X02` | unassigned |
| `ES-X04` | EnthusiaCommend reputation provider | External/multi-repository | `PLANNED` | — | 125 | `ES-P08`, `ES-X02` | unassigned |
| `ES-V01` | Private LiteBans representative-data verification | Private validation | `DEFERRED` | — | 200 | — | private/local environment required |
| `ES-V02` | Distributed and Java/Bedrock staging | Private validation | `DEFERRED` | — | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | owns representative Java/Bedrock and deferred ES-X05 staging evidence |
| `ES-V03` | Destructive, latency, and load acceptance | Private validation | `DEFERRED` | — | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | private/local environment required |
| `ES-A01` | LiteBans cutover acceptance | Production acceptance | `DEFERRED` | — | 300 | `ES-V01`, `ES-V02`, `ES-V03` | owner authorization and issue #43 required |
| `ES-QA01` | Final repository and workflow audit | Final no-fix audit | `PLANNED` | — | 400 | `ES-A01` | unassigned |

## Detailed current records

### `ES-P02`

- Status: `BLOCKED` / `PARKED_BLOCKED`.
- Branch/PR: `package/es-p02-runtime-db-recovery`; #70.
- Frozen product head: `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14`.
- Package-record head: `80d4ea840f34017c09afb618f623581b31c6223d`.
- Blocker: required ordinary hosted staging build did not execute; exact unblock remains a successful synchronized ordinary build plus Pi build/boot/restart or a separately policy-valid owner disposition.
- Handoff: [`2026-08-05-es-p02-runtime-db-recovery.md`](../reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md).

### `ES-P03`

- Status: `COMPLETE`.
- Starting `main`: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Exact reviewed/validated product head: `15608bc3099dc34aa080c80ca8e824ffd51cdae4`.
- Implementation PR/merge: #75 / `b960e91ea59627a870ff24f89c2f761d0cbb68ab`.
- Validation: Coverage `31133176482` / job `92726659126`; Wiki `31133176536` / job `92726609318`; CodeRabbit success and six resolved threads; Codacy 0 new issues and 61.54% diff coverage.
- Build: Temurin 21.0.12+8; all module and MariaDB/Testcontainers tests; V1–V17 unchanged.
- Runtime artifacts: Paper `81ff00cb50bc808db63ece6675b15e9a594e2350f84b113295037f03951e1c4c`; Velocity `65c87b47ef27d09ef9f36515365f62a4f238207452468f726979fa8f01006975`; 24 provider API types checked, zero leaks.
- Coverage: lines 47.69%, branches 38.65%, instructions 50.35%; artifact ID `8977006850`, digest `fc93e698ba4ee81e38f05c307f61d52627e1735f6ffc642756fd4cd696ba261e`.
- Containment: product head contained by merge commit; no changed files or unique product work beyond the merge.
- Cleanup: ref deletion unavailable through the connected tool; branch is inactive and contained.
- Boundary: ES-P09 retains alt graph/inheritance; ES-V02 retains representative Java/Bedrock staging.
- Handoff: [`2026-08-06-es-p03-bedrock-identity.md`](../reports/package-handoffs/2026-08-06-es-p03-bedrock-identity.md).

### `ES-X05`

- Status: `BLOCKED` / `PARKED_BLOCKED`.
- Implementation merge: aggregate PR #73 as `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`; standalone `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Finalization branch/PR: `package/es-x05-finalization`; #74; head `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`.
- Remaining blocker: ordinary hosted exact-head Coverage; staging remains owner-approved deferred to ES-V02 and is not a pass.

## Next-worker boundary

No package is activated by this completion worker. A later worker must reconcile live GitHub and dependencies from scratch. The ES-P03 routing exception does not automatically waive any subsequent dependency.
