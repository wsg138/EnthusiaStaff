# Package registry

Last updated: 2026-08-06

Canonical current state: `ES-P01 is COMPLETE. ES-P02 is BLOCKED / PARKED_BLOCKED in preserved branch package/es-p02-runtime-db-recovery and open PR #70. ES-X05 is MERGE_PENDING / ACTIONABLE_CONTINUATION in aggregate branch package/es-x05-state-publication and open PR #73 after standalone PR wsg138/enthusia-site#2 merged normally. ES-V02 remains DEFERRED. Exactly one implementation package is active: ES-X05.`

Live baseline: `wsg138/EnthusiaStaff:main` is `515bd9a8591505c043b413f5b9ecb3e272c6d6f2` for this continuation pass; aggregate main has V16 as its highest migration until ES-X05 PR #73 merges V17. Issue #43 remains open, deferred, and excluded.

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

## Canonical package index

| ID | Title | Type | Components | Status | Classification | Priority | Dependencies | Parallel safe | Assignment / live work | Package file |
| --- | --- | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| `ES-P01` | Exact-sanction appeal isolation | Internal | `COMP-STAFF` | `COMPLETE` | — | 10 | — | No | merged PR #68; no active branch | [`ES-P01.md`](packages/ES-P01.md) |
| `ES-P02` | Runtime database recovery and Velocity reload | Internal | `COMP-STAFF` | `BLOCKED` | `PARKED_BLOCKED` | 20 | `ES-P01` | No | `ChatGPT sequential package worker #2`; branch `package/es-p02-runtime-db-recovery`; PR #70 | [`ES-P02.md`](packages/ES-P02.md) |
| `ES-P03` | Bedrock identity correctness | Internal | `COMP-STAFF` | `PLANNED` | — | 30 | `ES-P02` | No | unassigned | [`ES-P03.md`](packages/ES-P03.md) |
| `ES-X05` | Website UX, authentication, and appeals | External/multi-repository | `COMP-SITE`, `COMP-STAFF` | `MERGE_PENDING` | `ACTIONABLE_CONTINUATION` | 35 | `ES-P01` | Conditional | `ChatGPT sequential ES-X05 completion worker`; aggregate branch `package/es-x05-state-publication`; PR #73; standalone PR `wsg138/enthusia-site#2` merged | [`ES-X05.md`](packages/ES-X05.md) |
| `ES-P04` | Staff-mode operational tools | Internal | `COMP-STAFF` | `PLANNED` | — | 40 | `ES-P03` | No | unassigned | [`ES-P04.md`](packages/ES-P04.md) |
| `ES-P07` | Inventory and Ender editing runtime completion | Internal | `COMP-STAFF` | `PLANNED` | — | 45 | `ES-P02` | Conditional | unassigned | [`ES-P07.md`](packages/ES-P07.md) |
| `ES-P05` | Report evidence and staff workflow completion | Internal | `COMP-STAFF` | `PLANNED` | — | 50 | `ES-P03`, `ES-P04` | Conditional | unassigned | [`ES-P05.md`](packages/ES-P05.md) |
| `ES-P09` | Alt and network-identity completion | Internal | `COMP-STAFF` | `PLANNED` | — | 55 | `ES-P03` | Conditional | unassigned | [`ES-P09.md`](packages/ES-P09.md) |
| `ES-P06` | Discord notification delivery completion | Internal | `COMP-STAFF` | `PLANNED` | — | 60 | `ES-P05` | Conditional | unassigned | [`ES-P06.md`](packages/ES-P06.md) |
| `ES-P08` | Item confiscation and restoration | Internal | `COMP-STAFF` | `PLANNED` | — | 70 | `ES-P07` | No | unassigned | [`ES-P08.md`](packages/ES-P08.md) |
| `ES-P10` | Cheat tester and fake-entity system | Internal | `COMP-STAFF` | `PLANNED` | — | 80 | `ES-P04` | Conditional | unassigned | [`ES-P10.md`](packages/ES-P10.md) |
| `ES-P11` | Fake-base generation and cleanup | Internal | `COMP-STAFF` | `PLANNED` | — | 90 | `ES-P10` | No | unassigned | [`ES-P11.md`](packages/ES-P11.md) |
| `ES-X01` | RoseChat provider and communication integration | External/multi-repository | `COMP-STAFF`, `COMP-ROSECHAT` | `PLANNED` | — | 100 | `ES-P03`, `ES-P04`, `ES-P05` | Conditional | unassigned; RoseChat repository unresolved until dependencies complete | [`ES-X01.md`](packages/ES-X01.md) |
| `ES-X02` | EnthusiaCurrency destructive provider | External/multi-repository | `COMP-STAFF`, `COMP-CURRENCY` | `PLANNED` | — | 110 | `ES-P08` | No | unassigned | [`ES-X02.md`](packages/ES-X02.md) |
| `ES-X03` | EnthusiaMarket destructive provider | External/multi-repository | `COMP-STAFF`, `COMP-MARKET` | `PLANNED` | — | 120 | `ES-P08`, `ES-X02` | Conditional | unassigned | [`ES-X03.md`](packages/ES-X03.md) |
| `ES-X04` | EnthusiaCommend reputation provider | External/multi-repository | `COMP-STAFF`, `COMP-COMMEND` | `PLANNED` | — | 125 | `ES-P08`, `ES-X02` | Conditional | unassigned | [`ES-X04.md`](packages/ES-X04.md) |
| `ES-V01` | Private LiteBans representative-data verification | Private validation | `COMP-STAFF` | `DEFERRED` | — | 200 | — | Conditional | private/local environment required | [`ES-V01.md`](packages/ES-V01.md) |
| `ES-V02` | Distributed and Java/Bedrock staging | Private validation | all applicable components | `DEFERRED` | — | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | No | private/local environment required; owns the staging obligation deferred from ES-P01 without starting early | [`ES-V02.md`](packages/ES-V02.md) |
| `ES-V03` | Destructive, latency, and load acceptance | Private validation | `COMP-STAFF`, `COMP-CURRENCY`, `COMP-MARKET`, `COMP-COMMEND` | `DEFERRED` | — | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | No | private/local environment required | [`ES-V03.md`](packages/ES-V03.md) |
| `ES-A01` | LiteBans cutover acceptance | Production acceptance | all release components | `DEFERRED` | — | 300 | `ES-V01`, `ES-V02`, `ES-V03` | No | owner authorization and issue #43 prerequisites required | [`ES-A01.md`](packages/ES-A01.md) |
| `ES-QA01` | Final repository and workflow audit | Final no-fix audit | all components and standalone repositories | `PLANNED` | — | 400 | `ES-A01` | No | unassigned | [`ES-QA01.md`](packages/ES-QA01.md) |

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
| Boundary | Owner-approved infrastructure exception deferred distributed Pi build/restart and Java/Bedrock staging evidence to ES-V02. No product boot failure occurred and no Pi pass is claimed. |

### `ES-P02` — Runtime database recovery and Velocity reload

| Field | Value |
| --- | --- |
| Status | `BLOCKED` / `PARKED_BLOCKED` |
| Starting SHA | `d94d0219a598c9afb7e19c4ea9fddafd554d6469` |
| Frozen product head | `b63fa1fa09ae4a9ea90988143ecda2cc7decbe14` |
| Current package-record head | `80d4ea840f34017c09afb618f623581b31c6223d` |
| Branch / PR | `package/es-p02-runtime-db-recovery`; PR #70 open, non-draft, unmerged, and non-mergeable |
| Handoff | [`2026-08-05-es-p02-runtime-db-recovery.md`](../reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md) |
| Blocker | Frozen product validation passed, but required staging run `31072794096` failed twice before execution: ordinary hosted build jobs `92524048937` and `92541148296` had runner ID 0, empty runner name, and no steps; downstream Pi jobs were skipped. No staging product build, Pi boot, or restart step executed. Exact unblock: obtain successful ordinary staging build plus specialized-runner Pi build, safe boot, and restart evidence for an exact package head, or a policy-valid explicit owner disposition that does not relabel the missing ordinary hosted build as passed; then merge current main into the package branch normally, resolve conflicts, freeze the synchronized head, and rerun every applicable exact-head gate. Drift and non-mergeability alone do not make this package actionable. |

### `ES-X05` — Website UX, authentication, and appeals

| Field | Value |
| --- | --- |
| Status | `MERGE_PENDING` / `ACTIONABLE_CONTINUATION` |
| Assigned worker | `ChatGPT sequential ES-X05 completion worker` |
| Starting aggregate main | `515bd9a8591505c043b413f5b9ecb3e272c6d6f2` for this continuation pass |
| Aggregate branch / PR | `package/es-x05-state-publication`; PR #73 |
| Frozen aggregate product head | `96912301fc425ac6f5eff9349ee3b3d543d122eb` |
| Standalone final reviewed head | `1a45b32e372cf6939c078a0d7986655e7ed639d6` |
| Standalone merge | PR `wsg138/enthusia-site#2` merged normally as `b385f78c522f452cc48d78ed19fd2ee82573f64d`; current standalone `main` |
| Standalone validation | run `31113188453` success; production and preview Cloudflare deployments success; Codacy success with zero annotations; zero unresolved review threads |
| Migration boundary | aggregate main V16 before merge; PR #73 adds immutable V17 |
| Component parity | true at SHA-256 `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`; no added, missing, or modified component paths |
| Handoff | [`2026-08-06-es-x05-website-auth-appeals.md`](../reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md) |
| Remaining gate | Successful exact-current-head aggregate build/test/migration/coverage/runtime-JAR validation, applicable static analysis, zero valid unresolved review threads, parity recheck, normal merge of PR #73, containment verification, final `COMPLETE` publication, and branch cleanup where tooling permits. Coverage run `31115480613` failed before checkout because GitHub could not resolve action downloads; no product step executed. |
