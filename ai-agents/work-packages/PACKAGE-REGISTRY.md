# Package registry

Last updated: 2026-08-06

Canonical current state: `ES-P01` is `COMPLETE`; `ES-P02` and `ES-X05` are `BLOCKED` / `PARKED_BLOCKED`; `ES-P03` is the only `ACTIVE` implementation package under the narrow owner-directed dependency-routing exception recorded below. No other package is active.

Live baseline: `wsg138/EnthusiaStaff:main` is `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da` at ES-P03 start and includes immutable migration V17. Issue #43 remains open, deferred, and excluded.

## Rules

- This file is the canonical package-status index; live GitHub overrides stale text and must be reconciled back here.
- Classify every incomplete package as `ACTIONABLE_CONTINUATION`, `PARKED_BLOCKED`, or `READY` before selection.
- Select the highest-priority actionable continuation; skip parked work; otherwise select the lowest-priority-number eligible ready package.
- A current explicit owner instruction may assign or re-route one package, but the exception must be recorded without silently marking dependencies complete.
- Complete exactly one package and stop. Do not activate the next package during finalization.
- Internal packages normally require one aggregate PR. External packages normally require standalone and aggregate PRs plus deterministic parity.
- A zero-execution infrastructure result is never a pass. Do not repeat identical zero-runner retries without material recovery evidence.
- Use normal merge commits only. Preserve unique work and publish persistent state before stopping.

## Owner-directed ES-P03 routing exception

The ordinary dependency graph requires `ES-P02` complete before `ES-P03`. On 2026-08-06 the repository owner explicitly directed the next sequential worker to continue another productive package while leaving ES-P02 and ES-X05 parked until GitHub-hosted runners recover. With no ordinary dependency-complete implementation package available, the worker selected the lowest-priority next implementation package, ES-P03.

This exception permits only ES-P03 implementation from current legitimate `main`. It does not change ES-P02 or ES-X05 status, import their unmerged branches, waive ES-P03 validation, authorize a later package, or alter release/acceptance dependencies. PR #70 and PR #74 remain parked and untouched. A later real integration conflict must be reconciled through the owning package before release acceptance.

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
| `ES-P02` | Runtime database recovery and Velocity reload | Internal | `BLOCKED` | `PARKED_BLOCKED` | 20 | `ES-P01` | branch `package/es-p02-runtime-db-recovery`; PR #70; untouched |
| `ES-P03` | Bedrock identity correctness | Internal | `ACTIVE` | `ACTIONABLE_CONTINUATION` | 30 | ordinarily `ES-P02`; owner-directed narrow exception | `ChatGPT sequential package worker`; branch `package/es-p03-bedrock-identity`; PR #75; implementation complete, validation pending |
| `ES-X05` | Website UX, authentication, and appeals | External/multi-repository | `BLOCKED` | `PARKED_BLOCKED` | 35 | `ES-P01` | implementation merged; finalization branch `package/es-x05-finalization`; PR #74; untouched |
| `ES-P04` | Staff-mode operational tools | Internal | `PLANNED` | — | 40 | `ES-P03` | unassigned |
| `ES-P07` | Inventory and Ender editing runtime completion | Internal | `PLANNED` | — | 45 | `ES-P02` | unassigned |
| `ES-P05` | Report evidence and staff workflow completion | Internal | `PLANNED` | — | 50 | `ES-P03`, `ES-P04` | unassigned |
| `ES-P09` | Alt and network-identity completion | Internal | `PLANNED` | — | 55 | `ES-P03` | unassigned |
| `ES-P06` | Discord notification delivery completion | Internal | `PLANNED` | — | 60 | `ES-P05` | unassigned |
| `ES-P08` | Item confiscation and restoration | Internal | `PLANNED` | — | 70 | `ES-P07` | unassigned |
| `ES-P10` | Cheat tester and fake-entity system | Internal | `PLANNED` | — | 80 | `ES-P04` | unassigned |
| `ES-P11` | Fake-base generation and cleanup | Internal | `PLANNED` | — | 90 | `ES-P10` | unassigned |
| `ES-X01` | RoseChat provider and communication integration | External/multi-repository | `PLANNED` | — | 100 | `ES-P03`, `ES-P04`, `ES-P05` | unassigned; RoseChat repository unresolved |
| `ES-X02` | EnthusiaCurrency destructive provider | External/multi-repository | `PLANNED` | — | 110 | `ES-P08` | unassigned |
| `ES-X03` | EnthusiaMarket destructive provider | External/multi-repository | `PLANNED` | — | 120 | `ES-P08`, `ES-X02` | unassigned |
| `ES-X04` | EnthusiaCommend reputation provider | External/multi-repository | `PLANNED` | — | 125 | `ES-P08`, `ES-X02` | unassigned |
| `ES-V01` | Private LiteBans representative-data verification | Private validation | `DEFERRED` | — | 200 | — | private/local environment required |
| `ES-V02` | Distributed and Java/Bedrock staging | Private validation | `DEFERRED` | — | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | owns representative Java/Bedrock and deferred ES-X05 staging evidence |
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
| Blocker | Required ordinary hosted staging build did not execute. Exact unblock remains successful ordinary build plus Pi build/boot/restart for a synchronized exact head, or a separately policy-valid owner disposition. Drift alone is not actionable. |
| ES-P03 boundary | PR #70 and branch remain untouched. ES-P03 does not import or claim ES-P02 lifecycle/reload work. |

### `ES-P03` — Bedrock identity correctness

| Field | Value |
| --- | --- |
| Status / classification | `ACTIVE` / `ACTIONABLE_CONTINUATION` |
| Assigned worker | `ChatGPT sequential package worker` |
| Selection basis | owner-directed narrow routing exception while ES-P02 and ES-X05 remain parked |
| Starting aggregate main | `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da` |
| Branch / PR | `package/es-p03-bedrock-identity`; PR #75 |
| Migration boundary | V17 current and immutable; no new migration |
| Implemented boundary | proof-bearing Java/Bedrock/unknown observations; current/history `*` aliases; deterministic lookup; ordered duplicate/out-of-order persistence; stale-disconnect protection |
| Velocity disposition | existing proxy observations are unverified and persist platform as `UNKNOWN`; they retain UUID/name/presence and cannot downgrade verified platform evidence |
| Regression evidence | domain provider-state tests plus MariaDB/Testcontainers alias, history, non-downgrade, ordering, reconnect, and invalid-shape tests |
| Harsh review | resolved four exact Codacy annotations and corrected broken-Floodgate/no-local-Geyser fallback to `UNKNOWN` |
| Exclusions | ES-P09 alt graph/inheritance; representative client staging; provider invention; production/cutover work |
| Handoff | [`2026-08-06-es-p03-bedrock-identity.md`](../reports/package-handoffs/2026-08-06-es-p03-bedrock-identity.md) |
| Exact next action | freeze PR #75, run exact-head Codacy/GitHub Actions/external review, then merge normally or publish the precise blocker |

### `ES-X05` — Website UX, authentication, and appeals

| Field | Value |
| --- | --- |
| Status | `BLOCKED` / `PARKED_BLOCKED` |
| Aggregate implementation merge | PR #73; current starting `main` `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da` |
| Standalone merge | `wsg138/enthusia-site#2`; `b385f78c522f452cc48d78ed19fd2ee82573f64d` |
| Product hosted validation | run `31116854096`, job `92668751419`, success on product head `4c818bb3aea953d3f877efc8a48a9175ba219d38` |
| Finalization branch / PR | `package/es-x05-finalization`; PR #74; head `96bf9ab21b114a4523582a5ca267e6c1d1370cb1` |
| Staging disposition | `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED` to ES-V02; not a pass |
| Ordinary hosted blocker | finalization Coverage run `31122594623`, job `92686159333`, cancelled with runner ID 0 and zero steps; cannot be waived |
| Exact unblock | material ordinary hosted-runner recovery evidence, successful exact-head Coverage, unchanged review/scope/parity, normal PR #74 merge, containment and COMPLETE publication |
| ES-P03 boundary | PR #74 and branch remain untouched. No identical retry is authorized by this package. |

## Deferred validation routing

`ES-V02` remains a private-validation package and is not started by this worker. It owns representative Java/Bedrock distributed acceptance and the explicitly deferred ES-X05 private/Pi evidence. LiteBans remains authoritative and issue #43 remains excluded.
