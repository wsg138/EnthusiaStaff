# Package registry

Last updated: 2026-08-05

Canonical current state: `ES-P01 MERGE_PENDING — product implementation and review are complete; every executable hosted gate passed at the latest reconciled head; the owner approved the zero-execution infrastructure exception for the unavailable Pi gate under the label OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED. The final documentation head must pass every applicable exact-head gate before normal merge. No other package is active.`

ES-P01 live baseline: `wsg138/EnthusiaStaff:main` at `e434b3dedc003d1d5b3def64f38cc7465752b0e5`; no open PRs or package branches existed before assignment; V16 was highest; issue #43 remained open and deferred.

## Rules

- This file is the only canonical package-status index.
- Workers receive exactly one package ID and may not silently select another.
- Internal packages normally require one EnthusiaStaff PR.
- External packages normally require two cross-referenced PRs: standalone and aggregate.
- There are no permanent component branches or isolated-component PRs.
- `COMPLETE` requires every package-specific PR/evidence gate and deterministic parity for external components.
- A missing external repository becomes a named blocker when its package is otherwise startable; never invent a URL.
- The setup PR is orchestration-only and is not an implementation package.

## Package count by type

| Type | Count |
| --- | ---: |
| Internal | 11 |
| External/multi-repository | 5 |
| Private validation | 3 |
| Production acceptance | 1 |
| Final no-fix audit | 1 |
| **Total** | **21** |

### `ES-P01` — Exact-sanction appeal isolation

| Field | Value |
| --- | --- |
| Package ID | `ES-P01` |
| Title | Exact-sanction appeal isolation |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Status | `MERGE_PENDING` |
| Priority | `10` |
| Dependencies | — |
| Parallel safe | No |
| Assigned worker | `ChatGPT assigned-package validation-policy correction and finalization worker` |
| Active branches | `package/es-p01-appeal-isolation` |
| Aggregate PR | `#68` |
| External PRs | `NONE` |
| Starting SHAs | `EnthusiaStaff main e434b3dedc003d1d5b3def64f38cc7465752b0e5` |
| Final reviewed heads | `Frozen product head 5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be; latest reconciled pre-policy PR head 7b132a3c0696dfcd4f991d64d75390047bc79a39` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | [`2026-08-05-es-p01-appeal-isolation.md`](../reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md) |
| Blocker | `NONE. OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED: parent run 31057348145/job 92477622119 dispatched staging run 31057358391; build job 92477654523 had runner_id 0, empty runner name, and steps []; Pi job 92477660726 was skipped with steps []. No product step executed and no Pi pass is claimed. Owner wsg138 approved the exception on 2026-08-05; missing distributed Pi boot/restart and Java/Bedrock staging evidence is deferred to ES-V02. Final exact-head hosted validation and merge requirements remain mandatory.` |
| Package file | [`packages/ES-P01.md`](packages/ES-P01.md) |

### `ES-P02` — Runtime database recovery and Velocity reload

| Field | Value |
| --- | --- |
| Package ID | `ES-P02` |
| Title | Runtime database recovery and Velocity reload |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Status | `PLANNED` |
| Priority | `20` |
| Dependencies | `ES-P01` |
| Parallel safe | No |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-P02.md`](packages/ES-P02.md) |

### `ES-P03` — Bedrock identity correctness

| Field | Value |
| --- | --- |
| Package ID | `ES-P03` |
| Title | Bedrock identity correctness |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Status | `PLANNED` |
| Priority | `30` |
| Dependencies | `ES-P02` |
| Parallel safe | No |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-P03.md`](packages/ES-P03.md) |

### `ES-P04` — Staff-mode operational tools

| Field | Value |
| --- | --- |
| Package ID | `ES-P04` |
| Title | Staff-mode operational tools |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Status | `PLANNED` |
| Priority | `40` |
| Dependencies | `ES-P03` |
| Parallel safe | No |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-P04.md`](packages/ES-P04.md) |

### `ES-P05` — Report evidence and staff workflow completion

| Field | Value |
| --- | --- |
| Package ID | `ES-P05` |
| Title | Report evidence and staff workflow completion |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Status | `PLANNED` |
| Priority | `50` |
| Dependencies | `ES-P03`, `ES-P04` |
| Parallel safe | Conditional |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-P05.md`](packages/ES-P05.md) |

### `ES-P06` — Discord notification delivery completion

| Field | Value |
| --- | --- |
| Package ID | `ES-P06` |
| Title | Discord notification delivery completion |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Status | `PLANNED` |
| Priority | `60` |
| Dependencies | `ES-P05` |
| Parallel safe | Conditional |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-P06.md`](packages/ES-P06.md) |

### `ES-P07` — Inventory and Ender editing runtime completion

| Field | Value |
| --- | --- |
| Package ID | `ES-P07` |
| Title | Inventory and Ender editing runtime completion |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Status | `PLANNED` |
| Priority | `45` |
| Dependencies | `ES-P02` |
| Parallel safe | Conditional |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-P07.md`](packages/ES-P07.md) |

### `ES-P08` — Item confiscation and restoration

| Field | Value |
| --- | --- |
| Package ID | `ES-P08` |
| Title | Item confiscation and restoration |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Status | `PLANNED` |
| Priority | `70` |
| Dependencies | `ES-P07` |
| Parallel safe | No |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-P08.md`](packages/ES-P08.md) |

### `ES-P09` — Alt and network-identity completion

| Field | Value |
| --- | --- |
| Package ID | `ES-P09` |
| Title | Alt and network-identity completion |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Status | `PLANNED` |
| Priority | `55` |
| Dependencies | `ES-P03` |
| Parallel safe | Conditional |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-P09.md`](packages/ES-P09.md) |

### `ES-P10` — Cheat tester and fake-entity system

| Field | Value |
| --- | --- |
| Package ID | `ES-P10` |
| Title | Cheat tester and fake-entity system |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Status | `PLANNED` |
| Priority | `80` |
| Dependencies | `ES-P04` |
| Parallel safe | Conditional |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-P10.md`](packages/ES-P10.md) |

### `ES-P11` — Fake-base generation and cleanup

| Field | Value |
| --- | --- |
| Package ID | `ES-P11` |
| Title | Fake-base generation and cleanup |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Status | `PLANNED` |
| Priority | `90` |
| Dependencies | `ES-P10` |
| Parallel safe | No |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-P11.md`](packages/ES-P11.md) |

### `ES-X01` — RoseChat provider and communication integration

| Field | Value |
| --- | --- |
| Package ID | `ES-X01` |
| Title | RoseChat provider and communication integration |
| Type | External/multi-repository |
| Primary component | `COMP-STAFF` |
| Other components | COMP-ROSECHAT |
| Status | `PLANNED` |
| Priority | `100` |
| Dependencies | `ES-P03`, `ES-P04`, `ES-P05` |
| Parallel safe | Conditional |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | RoseChat repository unresolved; evaluate when dependencies complete. |
| Package file | [`packages/ES-X01.md`](packages/ES-X01.md) |

### `ES-X02` — EnthusiaCurrency destructive provider

| Field | Value |
| --- | --- |
| Package ID | `ES-X02` |
| Title | EnthusiaCurrency destructive provider |
| Type | External/multi-repository |
| Primary component | `COMP-STAFF` |
| Other components | COMP-CURRENCY |
| Status | `PLANNED` |
| Priority | `110` |
| Dependencies | `ES-P08` |
| Parallel safe | No |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-X02.md`](packages/ES-X02.md) |

### `ES-X03` — EnthusiaMarket destructive provider

| Field | Value |
| --- | --- |
| Package ID | `ES-X03` |
| Title | EnthusiaMarket destructive provider |
| Type | External/multi-repository |
| Primary component | `COMP-STAFF` |
| Other components | COMP-MARKET |
| Status | `PLANNED` |
| Priority | `120` |
| Dependencies | `ES-P08`, `ES-X02` |
| Parallel safe | Conditional |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-X03.md`](packages/ES-X03.md) |

### `ES-X04` — EnthusiaCommend reputation provider

| Field | Value |
| --- | --- |
| Package ID | `ES-X04` |
| Title | EnthusiaCommend reputation provider |
| Type | External/multi-repository |
| Primary component | `COMP-STAFF` |
| Other components | COMP-COMMEND |
| Status | `PLANNED` |
| Priority | `125` |
| Dependencies | `ES-P08`, `ES-X02` |
| Parallel safe | Conditional |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-X04.md`](packages/ES-X04.md) |

### `ES-X05` — Website UX, authentication, and appeals

| Field | Value |
| --- | --- |
| Package ID | `ES-X05` |
| Title | Website UX, authentication, and appeals |
| Type | External/multi-repository |
| Primary component | `COMP-SITE` |
| Other components | COMP-STAFF |
| Status | `PLANNED` |
| Priority | `35` |
| Dependencies | `ES-P01` |
| Parallel safe | Conditional |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-X05.md`](packages/ES-X05.md) |

### `ES-V01` — Private LiteBans representative-data verification

| Field | Value |
| --- | --- |
| Package ID | `ES-V01` |
| Title | Private LiteBans representative-data verification |
| Type | Private validation |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Status | `DEFERRED` |
| Priority | `200` |
| Dependencies | — |
| Parallel safe | Conditional |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | Private/local environment required. |
| Package file | [`packages/ES-V01.md`](packages/ES-V01.md) |

### `ES-V02` — Distributed and Java/Bedrock staging

| Field | Value |
| --- | --- |
| Package ID | `ES-V02` |
| Title | Distributed and Java/Bedrock staging |
| Type | Private validation |
| Primary component | `COMP-STAFF` |
| Other components | All applicable components |
| Status | `DEFERRED` |
| Priority | `250` |
| Dependencies | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` |
| Parallel safe | No |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | `Private/local environment required. This package also owns the staging obligation deferred from ES-P01: obtain distributed Pi build, safe boot/restart, and Java/Bedrock staging evidence. This obligation does not start or complete ES-V02 early.` |
| Package file | [`packages/ES-V02.md`](packages/ES-V02.md) |

### `ES-V03` — Destructive, latency, and load acceptance

| Field | Value |
| --- | --- |
| Package ID | `ES-V03` |
| Title | Destructive, latency, and load acceptance |
| Type | Private validation |
| Primary component | `COMP-STAFF` |
| Other components | COMP-CURRENCY, COMP-MARKET, COMP-COMMEND |
| Status | `DEFERRED` |
| Priority | `260` |
| Dependencies | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` |
| Parallel safe | No |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | Private/local environment required. |
| Package file | [`packages/ES-V03.md`](packages/ES-V03.md) |

### `ES-A01` — LiteBans cutover acceptance

| Field | Value |
| --- | --- |
| Package ID | `ES-A01` |
| Title | LiteBans cutover acceptance |
| Type | Production acceptance |
| Primary component | `COMP-STAFF` |
| Other components | All release components |
| Status | `DEFERRED` |
| Priority | `300` |
| Dependencies | `ES-V01`, `ES-V02`, `ES-V03` |
| Parallel safe | No |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | Owner authorization and issue #43 prerequisites. |
| Package file | [`packages/ES-A01.md`](packages/ES-A01.md) |

### `ES-QA01` — Final repository and workflow audit

| Field | Value |
| --- | --- |
| Package ID | `ES-QA01` |
| Title | Final repository and workflow audit |
| Type | Final no-fix audit |
| Primary component | `COMP-STAFF` |
| Other components | All components and standalone repositories |
| Status | `PLANNED` |
| Priority | `400` |
| Dependencies | `ES-A01` |
| Parallel safe | No |
| Assigned worker | `UNASSIGNED` |
| Active branches | `NONE` |
| Aggregate PR | `NONE` |
| External PRs | `NONE` |
| Starting SHAs | `UNSET` |
| Final reviewed heads | `UNSET` |
| Merge commits | `UNSET` |
| Last update | `2026-08-05` |
| Handoff | `NONE` |
| Blocker | NONE |
| Package file | [`packages/ES-QA01.md`](packages/ES-QA01.md) |
