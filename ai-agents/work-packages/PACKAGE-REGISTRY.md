# Package registry

Last updated: 2026-08-06

Canonical state after normal merge of PR #74: `ES-P01` and `ES-X05` are `COMPLETE`; `ES-P02` remains `BLOCKED` / `PARKED_BLOCKED`; `ES-V02` remains `DEFERRED` and owns ES-X05's deferred combined staging evidence. No new implementation package is active.

Live baseline: `wsg138/EnthusiaStaff:main` contains ES-X05 implementation merge `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da` and immutable migration V17. Issue #43 remains open, deferred, and excluded.

## Rules

- This file is the canonical package-status index; live GitHub overrides stale text and must be reconciled back here.
- Classify every incomplete package as `ACTIONABLE_CONTINUATION`, `PARKED_BLOCKED`, or `READY` before selection.
- Select the highest-priority actionable continuation; skip parked work; otherwise select the lowest-priority-number eligible ready package.
- Complete exactly one package and stop. Do not activate the next package during finalization.
- Internal packages normally require one aggregate PR. External packages normally require standalone and aggregate PRs plus deterministic parity.
- `COMPLETE` requires all package PRs/evidence gates, except a policy-valid owner-approved infrastructure exception recorded exactly as deferred and assigned to a named later validation package.
- A zero-execution exception is never a pass and never grants staging, production, cutover, or authority evidence.
- Use normal merge commits only. Preserve unique work and publish persistent state before stopping.

## Package count by type

| Type | Count |
| --- | ---: |
| Internal | 11 |
| External/multi-repository | 5 |
| Private validation | 3 |
| Production acceptance | 1 |
| Final no-fix audit | 1 |
| **Total** | **21** |

## Canonical package index

| ID | Title | Type | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| `ES-P01` | Exact-sanction appeal isolation | Internal | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-P02` | Runtime database recovery and Velocity reload | Internal | `BLOCKED` | `PARKED_BLOCKED` | 20 | `ES-P01` | branch `package/es-p02-runtime-db-recovery`; PR #70 |
| `ES-P03` | Bedrock identity correctness | Internal | `PLANNED` | — | 30 | `ES-P02` | unassigned |
| `ES-X05` | Website UX, authentication, and appeals | External/multi-repository | `COMPLETE` after PR #74 merge | — | 35 | `ES-P01` | implementation merged; finalization PR #74; staging deferred to `ES-V02` |
| `ES-P04` | Staff-mode operational tools | Internal | `PLANNED` | — | 40 | `ES-P03` | unassigned |
| `ES-P07` | Inventory and Ender editing runtime completion | Internal | `PLANNED` | — | 45 | `ES-P02` | unassigned |
| `ES-P05` | Report evidence and staff workflow completion | Internal | `PLANNED` | — | 50 | `ES-P03`, `ES-P04` | unassigned |
| `ES-P09` | Alt and network-identity completion | Internal | `PLANNED` | — | 55 | `ES-P03` | unassigned |
| `ES-P06` | Discord notification delivery completion | Internal | `PLANNED` | — | 60 | `ES-P05` | unassigned |
| `ES-P08` | Item confiscation and restoration | Internal | `PLANNED` | — | 70 | `ES-P07` | unassigned |
| `ES-P10` | Cheat tester and fake-entity system | Internal | `PLANNED` | — | 80 | `ES-P04` | unassigned |
| `ES-P11` | Fake-base generation and cleanup | Internal | `PLANNED` | — | 90 | `ES-P10` | unassigned |
| `ES-X01` | RoseChat provider and communication integration | External/multi-repository | `PLANNED` | — | 100 | `ES-P03`, `ES-P04`, `ES-P05` | unassigned |
| `ES-X02` | EnthusiaCurrency destructive provider | External/multi-repository | `PLANNED` | — | 110 | `ES-P08` | unassigned |
| `ES-X03` | EnthusiaMarket destructive provider | External/multi-repository | `PLANNED` | — | 120 | `ES-P08`, `ES-X02` | unassigned |
| `ES-X04` | EnthusiaCommend reputation provider | External/multi-repository | `PLANNED` | — | 125 | `ES-P08`, `ES-X02` | unassigned |
| `ES-V01` | Private LiteBans representative-data verification | Private validation | `DEFERRED` | — | 200 | — | private/local environment required |
| `ES-V02` | Distributed and Java/Bedrock staging | Private validation | `DEFERRED` | — | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | owns ES-X05 deferred PySentinel/runtime acceptance |
| `ES-V03` | Destructive, latency, and load acceptance | Private validation | `DEFERRED` | — | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | private/local environment required |
| `ES-A01` | LiteBans cutover acceptance | Production acceptance | `DEFERRED` | — | 300 | `ES-V01`, `ES-V02`, `ES-V03` | owner authorization and issue #43 prerequisites required |
| `ES-QA01` | Final repository and workflow audit | Final no-fix audit | `PLANNED` | — | 400 | `ES-A01` | unassigned |

## Detailed current records

### `ES-P01` — Exact-sanction appeal isolation

| Field | Value |
| --- | --- |
| Status | `COMPLETE` |
| Starting SHA | `e434b3dedc003d1d5b3def64f38cc7465752b0e5` |
| Frozen product head | `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be` |
| Exact validated PR head | `ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179` |
| Merge commits | implementation `203b2854d5546a6d3744037c367099129654b42a`; finalization `d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Handoff | [`2026-08-05-es-p01-appeal-isolation.md`](../reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md) |
| Boundary | Owner-approved infrastructure exception deferred distributed Pi build/restart and Java/Bedrock staging evidence to ES-V02. No Pi pass is claimed. |

### `ES-P02` — Runtime database recovery and Velocity reload

| Field | Value |
| --- | --- |
| Status | `BLOCKED` / `PARKED_BLOCKED` |
| Starting SHA | `d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Frozen product head | `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14` |
| Current package-record head | `80d4ea840f34017c09afb618f623581b31c6223d` |
| Branch / PR | `package/es-p02-runtime-db-recovery`; PR #70 |
| Handoff | [`2026-08-05-es-p02-runtime-db-recovery.md`](../reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md) |
| Blocker | Required ordinary hosted staging build did not execute. Exact unblock remains a successful ordinary build plus Pi build/boot/restart for a synchronized head, or a separately policy-valid owner disposition. |

### `ES-X05` — Website UX, authentication, and appeals

| Field | Value |
| --- | --- |
| Status | `COMPLETE` upon normal merge of PR #74 |
| Assigned worker | `ChatGPT sequential ES-X05 completion worker` |
| Starting aggregate main | `515bd9a8591505c043b413f5b9ecb3e272c6d6f2` |
| Frozen aggregate product head | `96912301fc425ac6f5eff9349ee3b3d543d122eb` |
| Exact hosted-validated aggregate head | `4c818bb3aea953d3f877efc8a48a9175ba219d38` |
| Aggregate implementation branch | `package/es-x05-state-publication` |
| Aggregate PR / merge | PR #73; normal merge `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da` |
| Finalization branch / PR | `package/es-x05-finalization`; PR #74; exact current head/final hosted check recorded in PR #74 |
| Hosted validation | Coverage run `31116854096`, job `92668751419`, success: Java 21 build, unit/MariaDB integration and migration tests, JaCoCo, runtime-JAR/provider-leak checks, artifacts, Codacy coverage |
| Review | CodeRabbit success; zero unresolved valid threads |
| Standalone PR / merge | `wsg138/enthusia-site#2`; normal merge/current `main` `b385f78c522f452cc48d78ed19fd2ee82573f64d` |
| Standalone validation | run `31113188453` success; production/preview deployments and Codacy success; zero unresolved review threads |
| Migration boundary | V1–V16 unchanged; aggregate `main` includes immutable V17 |
| Component parity | true at `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`; no added/missing/modified paths |
| Containment | Aggregate and standalone implementation branches have zero unique work beyond their normal merges |
| Exception label | `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED` |
| Owner approval | repository owner `wsg138`; current ChatGPT project conversation; 2026-08-06; explicit direction to skip ES-X05 live testing now, continue development, and batch detailed testing later through PySentinel |
| Affected parent | aggregate run `31116852061`, job `92668521113`, exact head `4c818bb3aea953d3f877efc8a48a9175ba219d38` |
| Unavailable gate | private `wsg138/EnthusiaStaff-Staging` run `31116860919`; build job `92668551209`; runner ID `0`; runner name empty; steps `[]`; downstream Pi job `92668600472` skipped |
| Product execution | none in the unavailable private run; no checkout/build/test/artifact validation/boot/restart/migration step ran; no staging pass is claimed |
| Named deferred package | `ES-V02 — Distributed and Java/Bedrock staging` |
| Deferred matrix | pinned combined exact-head build/artifacts; Paper boot/restart; V17 and later migrations; config/reload/restart; appeal/auth persistence; provider/distributed behavior; representative Java/Bedrock clients; safely implemented automated client behavior; cleanup/isolation |
| Security boundary | no production credentials/accounts/data/routes or authority activation; automated account credential handling requires separate implementation, review, and owner authorization |
| Handoff | [`2026-08-06-es-x05-website-auth-appeals.md`](../reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md) |

## Deferred validation routing

`ES-V02` remains a private-validation package and is not started by this worker. When legitimately assigned after its dependencies and environment are ready, it must include ES-X05's deferred evidence in its pinned combined acceptance campaign. A later PySentinel implementation may support that campaign, but its unfinished features are not claimed as current evidence.
