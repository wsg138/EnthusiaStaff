# Package registry

Last updated: 2026-08-06

Canonical current state: `ES-P01` and `ES-P03` are `COMPLETE`; `ES-P02` and `ES-X05` are `BLOCKED` / `PARKED_BLOCKED`; no implementation package is active. Issue #43 remains open, deferred, and excluded.

## Rules

- Live GitHub overrides stale text and must be reconciled back here.
- Classify every incomplete package before selection; select one package and stop after its terminal state is persistently published.
- Park unchanged external blockers; do not keep branches current merely because `main` advanced.
- Use normal merge commits only. Missing, skipped, cancelled, superseded, rate-limited, billing-blocked, or zero-runner gates are not passes.

## Canonical package index

| ID | Title | Type | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| `ES-P01` | Exact-sanction appeal isolation | Internal | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-P02` | Runtime database recovery and Velocity reload | Internal | `BLOCKED` | `PARKED_BLOCKED` | 20 | `ES-P01` | branch `package/es-p02-runtime-db-recovery`; PR #70; private staging billing blocker |
| `ES-P03` | Bedrock identity correctness | Internal | `COMPLETE` | — | 30 | ordinarily `ES-P02`; owner-directed narrow exception | PR #75 merged as `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| `ES-X05` | Website UX, authentication, and appeals | External/multi-repository | `BLOCKED` | `PARKED_BLOCKED` | 35 | `ES-P01` | finalization branch `package/es-x05-finalization`; PR #74 |
| `ES-P04` | Staff-mode operational tools | Internal | `PLANNED` | — | 40 | `ES-P03` | unassigned |
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
| `ES-V02` | Distributed and Java/Bedrock staging | Private validation | `DEFERRED` | — | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | representative Java/Bedrock and deferred ES-X05 staging evidence |
| `ES-V03` | Destructive, latency, and load acceptance | Private validation | `DEFERRED` | — | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | private/local environment required |
| `ES-A01` | LiteBans cutover acceptance | Production acceptance | `DEFERRED` | — | 300 | `ES-V01`, `ES-V02`, `ES-V03` | owner authorization and issue #43 required |
| `ES-QA01` | Final repository and workflow audit | Final no-fix audit | `PLANNED` | — | 400 | `ES-A01` | unassigned |

## Detailed current records

### `ES-P02`

- Status: `BLOCKED` / `PARKED_BLOCKED`.
- Branch/PR: `package/es-p02-runtime-db-recovery`; #70.
- Current `main` synchronized through merge commit `b21cb81b81fdcf0bac5027ae6f6b7901f6b0c175` from base `9b1aac2677049ccc71dbddd963831f270c73dcd0`.
- Exact hosted-validation head: `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`.
- Hosted validation: Coverage run `31138550369`, job `92743341861`, `SUCCESS`; Java 21, all tests, MariaDB/Testcontainers, migrations, coverage, runtime JARs, provider-leak checks, artifact upload, and Codacy upload passed.
- Artifact: ID `8979036747`; digest `sha256:18810296fc08695fb5d5f8497f008052161b7a0b0536fc898d27a22d69f65d70`.
- Review: Codacy success with zero issues; CodeRabbit success; zero valid unresolved threads.
- Blocker: private staging run `31138555091` could not start ordinary `ubuntu-latest` build jobs `92743314720` and `92743621264`; both had `runner_id: 0`, empty runner names, and zero steps. GitHub’s annotation says recent account payments failed or the Actions spending limit must be increased. Pi boot/restart was skipped.
- Required owner action: resolve **Billing & plans** for private Actions usage, then rerun every applicable exact-head gate and obtain a successful private staging build plus Pi boot/restart. The ordinary hosted build cannot be waived by the infrastructure exception.
- Handoff: [`2026-08-06-es-p02-resume-validation.md`](../reports/package-handoffs/2026-08-06-es-p02-resume-validation.md).

### `ES-P03`

- Status: `COMPLETE`.
- Product head `15608bc3099dc34aa080c80ca8e824ffd51cdae4`; PR #75 merged as `b960e91ea59627a870ff24f89c2f761d0cbb68ab`.
- Handoff: [`2026-08-06-es-p03-bedrock-identity.md`](../reports/package-handoffs/2026-08-06-es-p03-bedrock-identity.md).

### `ES-X05`

- Status: `BLOCKED` / `PARKED_BLOCKED`.
- Implementation merge: aggregate PR #73 as `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`; standalone `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Finalization branch/PR: `package/es-x05-finalization`; #74; head must be reconciled live by its future worker.

## Next-worker boundary

No second package was activated. A future worker must resume ES-P02 after the private Actions billing restriction is fixed, or follow a new explicit owner-directed routing decision through the canonical system. The earlier ES-P03 routing exception does not automatically waive any later dependency.
