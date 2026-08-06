# Package registry

Last updated: 2026-08-06

Canonical current state: `ES-P01 is COMPLETE. ES-P02 is BLOCKED in preserved branch package/es-p02-runtime-db-recovery and open PR #70. Its frozen product head is b63fa1fa09ae4a9ea90988143ecda2cc7decbe14 and its current package-record head is 80d4ea840f34017c09afb618f623581b31c6223d. ES-X05 is READY and unstarted. ES-V02 is DEFERRED. No implementation package is active.`

ES-P02 live baseline: `wsg138/EnthusiaStaff:main` was `d94d0219a598c9afb7e19c4ea9fddafd554d6469` when claimed and is `5c969901146fc5081eec14b3c089bec7b06d5f5e` at this status publication; V16 is highest; issue #43 remains open, deferred, and excluded.

## Rules

- This file is the only canonical package-status index.
- Reconcile live GitHub before acting. Live evidence overrides stale text, but known persistent state must be published back to `main`.
- Before selection, classify every incomplete package as `ACTIONABLE_CONTINUATION`, `PARKED_BLOCKED`, or `READY`.
- `ACTIONABLE_CONTINUATION` means existing work has a safe action available now, such as unfinished implementation, an actual compile or test failure, a valid review finding, executable exact-head validation, merge-ready work, required synchronization or finalization, containment or cleanup, or a blocker whose exact unblock condition demonstrably changed.
- `PARKED_BLOCKED` means the same unavailable external condition still controls the next action and no other actionable defect exists. An open PR, open branch, branch drift, or non-mergeability does not make parked work actionable.
- `READY` means dependencies are complete and the package is eligible after live conflict, duplicate-work, repository, and package-contract checks.
- Select the highest-priority `ACTIONABLE_CONTINUATION`; skip every `PARKED_BLOCKED` package; otherwise select the eligible `READY` package with the lowest numerical priority. When none exists, report every blocker and stop.
- Existing actionable unfinished work takes priority over new work. A parked package resumes before a new package only after its unblock condition changes or another real actionable defect appears.
- Do not merge `main` into a parked blocked branch merely to keep it current. Synchronize only after the unblock condition changes, or when synchronization is necessary to evaluate a newly changed condition.
- Do not repeatedly rerun an identical zero-runner or unavailable-infrastructure gate without evidence that runner capacity, billing, authorization, configuration, or environment availability changed. A manual rerun alone is not evidence of change.
- Updating blocker documentation alone does not convert a parked package into an actionable continuation.
- Complete exactly one package and stop; do not activate or begin a newly ready package during finalization.
- Internal packages normally require one EnthusiaStaff PR.
- External packages normally require two cross-referenced PRs: standalone and aggregate.
- There are no permanent component branches or isolated-component PRs.
- `COMPLETE` requires every package-specific PR and evidence gate and deterministic parity for external components.
- A missing external repository becomes a named blocker when its package is otherwise startable; never invent a URL.
- When an unmerged implementation PR stops in `PARTIAL`, `BLOCKED`, `REVIEW`, `MERGE_PENDING`, or `SYNC_PENDING`, and `main` does not reflect that state, the same worker must normally merge a documentation-only status-publication PR to `main` before stopping.
- A status-publication PR may update only the registry, selected package file, workspace state, canonical package handoff, latest handoff pointer, and directly necessary routing documentation. It is not a second implementation package and may contain no product code, product tests, migrations, workflow changes, or runtime configuration.
- Status publication must preserve the implementation PR and branch and record the true status, branch, PR, current package-record head, frozen product head when applicable, blocker evidence, and exact unblock condition. Use a normal merge commit.
- Tool loss is the only reason to stop with known persistent state unpublished; report that inconsistency as unfinished work.
- The setup and process PRs are orchestration-only and are not implementation packages.

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
| Status | `COMPLETE` |
| Priority | `10` |
| Dependencies | — |
| Parallel safe | No |
| Assigned worker | `ChatGPT assigned-package validation-policy correction and finalization worker` |
| Active branches | `NONE` |
| Aggregate PR | `#68 — merged normally` |
| External PRs | `NONE` |
| Starting SHAs | `EnthusiaStaff main e434b3dedc003d1d5b3def64f38cc7465752b0e5` |
| Final reviewed heads | `Frozen product head 5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be; exact validated PR head ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179` |
| Merge commits | `Implementation merge 203b2854d5546a6d3744037c367099129654b42a; finalization merge d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Last update | `2026-08-05` |
| Handoff | [`2026-08-05-es-p01-appeal-isolation.md`](../reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md) |
| Blocker | `NONE. OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED: final-head parent run 31067402120/job 92507922737 dispatched staging run 31067405608; build job 92507935906 had runner_id 0, empty runner name, and steps []; Pi job 92507942018 was skipped with steps []. No product step executed, no product boot failure occurred, and no Pi pass is claimed. Owner wsg138 approved the exception on 2026-08-05. Missing distributed Pi build/restart and Java/Bedrock staging evidence is deferred to ES-V02.` |
| Package file | [`packages/ES-P01.md`](packages/ES-P01.md) |

### `ES-P02` — Runtime database recovery and Velocity reload

| Field | Value |
| --- | --- |
| Package ID | `ES-P02` |
| Title | Runtime database recovery and Velocity reload |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Status | `BLOCKED` |
| Classification | `PARKED_BLOCKED` |
| Priority | `20` |
| Dependencies | `ES-P01` |
| Parallel safe | No |
| Assigned worker | `ChatGPT sequential package worker #2` |
| Active branches | `package/es-p02-runtime-db-recovery` |
| Aggregate PR | `#70 — open, non-draft, unmerged, and currently non-mergeable` |
| External PRs | `NONE` |
| Starting SHAs | `EnthusiaStaff main d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Final reviewed heads | `Frozen product head b63fa1fa09ae4a9ea90988143ecda2cc7decbe14; current package-record head 80d4ea840f34017c09afb618f623581b31c6223d` |
| Merge commits | `UNSET` |
| Last update | `2026-08-06` |
| Handoff | [`2026-08-05-es-p02-runtime-db-recovery.md`](../reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md) |
| Blocker | `Frozen product head b63fa1fa09ae4a9ea90988143ecda2cc7decbe14 passed hosted Java 21 build, all tests, MariaDB/Testcontainers, migration integrity, changed-code coverage threshold, runtime-JAR and provider-leak checks, Codacy with zero annotations, CodeRabbit, and zero unresolved review threads. Required staging run 31072794096 failed twice before execution: ordinary ubuntu-latest build jobs 92524048937 and 92541148296 each had runner_id 0, an empty runner name, and steps []; downstream Pi jobs 92524054852 and 92541160241 were skipped. No staging product build, Pi boot, or restart step executed. This is not a pass, and no ES-P02 infrastructure exception exists. Exact unblock: obtain successful ordinary staging build plus specialized-runner Pi build, safe boot, and restart evidence for an exact package head, or a policy-valid explicit owner disposition that does not relabel the missing ordinary hosted build as passed; then merge current main into the package branch by normal merge commit, resolve conflicts, freeze the synchronized head, and rerun every applicable exact-head gate. Branch drift and non-mergeability do not make the package actionable while the external runner or authorization condition is unchanged.` |
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
| Status | `READY` |
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
