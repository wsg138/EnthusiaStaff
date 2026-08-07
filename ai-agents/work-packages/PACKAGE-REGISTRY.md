# Package registry

Last updated: 2026-08-07

Canonical current state: `ES-P01`, `ES-P03`, and `ES-X05` are `COMPLETE`; `ES-P02` is `BLOCKED` / `PARKED_BLOCKED`; `ES-P04` is `ACTIVE` on PR #79 with implementation/documentation and identified review repairs complete, while final exact-head validation, review-thread disposition, and Pi disposition remain; `ES-P09` remains dependency-derived `READY` and unassigned. Issue #43 remains open, deferred, and excluded.

## Rules

- Live GitHub overrides stale text and must be reconciled back here.
- Classify every incomplete package before selection; select one package and stop after its terminal state is persistently published.
- Park unchanged external blockers; do not repeat identical zero-runner attempts without a material condition change.
- Existing actionable continuation work takes priority over new implementation work.
- Owner-directed routing exceptions must be explicit and do not silently complete dependencies or propagate to later packages.
- Use normal merge commits only. Missing, skipped, cancelled, superseded, wrong-revision, billing-blocked, or zero-runner gates are not passes.
- An owner-approved staging deferral remains deferred evidence; it cannot excuse a missing ordinary GitHub-hosted build.

## ES-P03 routing exception

The ordinary graph requires ES-P02 complete before ES-P03. ES-P02 remains `BLOCKED` / `PARKED_BLOCKED`. On 2026-08-06 the repository owner directed the earlier worker to continue ES-P03 while ES-P02 and ES-X05 were parked. The exception permitted ES-P03 only; it did not alter ES-P02/ES-X05, waive gates, or automatically authorize later packages. ES-P03 subsequently completed normally. Packages whose declared dependency is solely ES-P03 can therefore become dependency-ready under the ordinary graph; this does not bypass any separate dependency on ES-P02.

## Canonical package index

| ID | Title | Type | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| `ES-P01` | Exact-sanction appeal isolation | Internal | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-P02` | Runtime database recovery and Velocity reload | Internal | `BLOCKED` | `PARKED_BLOCKED` | 20 | `ES-P01` | branch `package/es-p02-runtime-db-recovery`; PR #70; records head `99da4103773e0c2ae43e0b0253200cd0d3d2c65c`; private Actions billing blocker unchanged |
| `ES-P03` | Bedrock identity correctness | Internal | `COMPLETE` | — | 30 | ordinarily `ES-P02`; owner-directed narrow exception | PR #75 merged as `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| `ES-X05` | Website UX, authentication, and appeals | External/multi-repository | `COMPLETE` | — | 35 | `ES-P01` | finalization PR #74 merged as `2bcf5d46ca6471fddac600f85020c66105b1c0f2`; parity proven |
| `ES-P04` | Staff-mode operational tools | Internal | `ACTIVE` | `ACTIONABLE_CONTINUATION` | 40 | `ES-P03` | generic sequential package worker; branch `package/es-p04-staff-mode-tools`; PR #79 ready for review; latest product-code head `ce6e516259b3739d08d4607a838c5e4940aafacd`; final records/exact-head validation and Pi disposition pending |
| `ES-P07` | Inventory and Ender editing runtime completion | Internal | `PLANNED` | — | 45 | `ES-P02` | unassigned; dependency blocked |
| `ES-P05` | Report evidence and staff workflow completion | Internal | `PLANNED` | — | 50 | `ES-P03`, `ES-P04` | unassigned |
| `ES-P09` | Alt and network-identity completion | Internal | `READY` | `READY` | 55 | `ES-P03` | unassigned; not activated by ES-P04 worker |
| `ES-P06` | Discord notification delivery completion | Internal | `PLANNED` | — | 60 | `ES-P05` | unassigned |
| `ES-P08` | Item confiscation and restoration | Internal | `PLANNED` | — | 70 | `ES-P07` | unassigned |
| `ES-P10` | Cheat tester and fake-entity system | Internal | `PLANNED` | — | 80 | `ES-P04` | unassigned; explicitly excluded from ES-P04 |
| `ES-P11` | Fake-base generation and cleanup | Internal | `PLANNED` | — | 90 | `ES-P10` | unassigned |
| `ES-X01` | RoseChat provider and communication integration | External/multi-repository | `PLANNED` | — | 100 | `ES-P03`, `ES-P04`, `ES-P05` | unassigned; repository unresolved |
| `ES-X02` | EnthusiaCurrency destructive provider | External/multi-repository | `PLANNED` | — | 110 | `ES-P08` | unassigned |
| `ES-X03` | EnthusiaMarket destructive provider | External/multi-repository | `PLANNED` | — | 120 | `ES-P08`, `ES-X02` | unassigned |
| `ES-X04` | EnthusiaCommend reputation provider | External/multi-repository | `PLANNED` | — | 125 | `ES-P08`, `ES-X02` | unassigned |
| `ES-V01` | Private LiteBans representative-data verification | Private validation | `DEFERRED` | — | 200 | — | private/local environment required |
| `ES-V02` | Distributed and Java/Bedrock staging | Private validation | `DEFERRED` | — | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | owns representative Java/Bedrock and owner-deferred ES-X05 private/Pi staging |
| `ES-V03` | Destructive, latency, and load acceptance | Private validation | `DEFERRED` | — | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | private/local environment required |
| `ES-A01` | LiteBans cutover acceptance | Production acceptance | `DEFERRED` | — | 300 | `ES-V01`, `ES-V02`, `ES-V03` | owner authorization and issue #43 required |
| `ES-QA01` | Final repository and workflow audit | Final no-fix audit | `PLANNED` | — | 400 | `ES-A01` | unassigned |

## Detailed current records

### `ES-P02`

- Status: `BLOCKED` / `PARKED_BLOCKED`.
- Branch/PR: `package/es-p02-runtime-db-recovery`; #70.
- Current records head: `99da4103773e0c2ae43e0b0253200cd0d3d2c65c`.
- Exact hosted-validation product head: `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`; Coverage run `31138550369`, job `92743341861`, success.
- Private staging remains blocked before Ubuntu allocation: latest inspected build job `92753075216` had runner ID `0`, empty runner name, steps `[]`, and the Billing & plans payment/spending-limit rejection; Pi `92753100652` skipped.
- Exact unblock: resolve the private-repository Billing & plans restriction, then resume PR #70, reconcile newer legitimate `main`, freeze one exact head, rerun every applicable gate, obtain a successful ordinary private build followed by Pi safe Paper boot/restart on the same source revision, merge normally, verify containment, publish `COMPLETE`, and stop.
- Handoff: [`2026-08-05-es-p02-runtime-db-recovery.md`](../reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md).

### `ES-P03`

- Status: `COMPLETE`.
- Exact reviewed/validated product head: `15608bc3099dc34aa080c80ca8e824ffd51cdae4`.
- Implementation PR/merge: #75 / `b960e91ea59627a870ff24f89c2f761d0cbb68ab`.
- Validation: Coverage `31133176482` / `92726659126`; Wiki `31133176536` / `92726609318`; CodeRabbit success; Codacy 0 issues. V1-V17 unchanged.
- Handoff: [`2026-08-06-es-p03-bedrock-identity.md`](../reports/package-handoffs/2026-08-06-es-p03-bedrock-identity.md).

### `ES-X05`

- Status: `COMPLETE`.
- Frozen finalization head: `ab59b8357b8e2eb146b60ff122e316112906746f`; PR #74 normal merge `2bcf5d46ca6471fddac600f85020c66105b1c0f2`.
- Coverage `31140188918` / `92748299782`, CodeRabbit, Codacy static/coverage, and post-merge parity `31140896890` / `92750376952` all passed.
- Staging: **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED** to `ES-V02`; no staging pass is claimed. V17 remains the sole ES-X05 migration.
- Handoff: [`2026-08-06-es-x05-website-auth-appeals.md`](../reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md).

### `ES-P04`

- Status: `ACTIVE` / `ACTIONABLE_CONTINUATION`.
- Starting legitimate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`; live `main` remained unchanged at latest reconciliation.
- Branch/PR: `package/es-p04-staff-mode-tools`; PR #79 ready for review and mergeable at the latest pre-freeze check.
- Latest product-code head: `ce6e516259b3739d08d4607a838c5e4940aafacd`; implementation and Wiki/reference documentation are complete.
- Implemented actions: random teleport, inspector, freeze, reports, follow/spectate, vanish, staff chat, and tools menu. Cheat Tester remains deferred to `ES-P10`; fake bases remain `ES-P11`.
- Security: server-issued tools bind owner + random current-profile token + canonical slot/material; current active session/rank/action permission is rechecked. Unknown, stale, transferred, old-session, rank-invalid, wrong-slot, and wrong-material tools fail closed.
- Existing durable snapshot/restoration/reconnect/rank/inventory/world/damage machinery remains intact; durable moderation actions reuse existing command/service boundaries. No migration change; V17 remains the boundary.
- Static-analysis history: 14 issues on `c1db641169a40d0be64ad48b4672d3dce3da72c4` and one file-length issue on `501cb2a90fab0c28a080a64ce51e81d10f2f0da5` were structurally resolved. The compile regression exposed at `93858cf68c539a0c1fe62b20d1993607981517ab` was fixed by `dd1f10a12680eab6768473aee5b542e840b09b65`.
- Supporting but superseded green evidence on records head `4cc0178a8f908c43aafc6a88cf3014e81f5756f2`: Wiki `31173610733` / `92850645081`; Coverage `31173610764` / `92850703180`; Codacy static `92850885117`; coverage variation `92852019542`; diff coverage `92852019707`.
- Review on that superseded head then found valid issues. Follow-up commits prevent the deferred Cheat Tester from being issued for any rank, add cooldown boundary coverage at 0/60000 ms with rejection at -1/60001 ms, and correct inaccurate follow/spectate feedback. The remaining scheduler-helper extraction note is maintainability-only because the actual compile defect is already fixed; it is not required to change package behavior.
- Review threads are being dispositioned before the exact-head freeze; no claim of zero valid unresolved threads is made until that is verified live.
- Private Pi remains unavailable before product execution under the Billing & plans restriction in the latest package evidence. No owner-approved ES-P04 infrastructure exception exists. Final frozen-head Pi evidence is still required; unavailable infrastructure is not a pass.
- Handoff: [`2026-08-07-es-p04-staff-mode-tools.md`](../reports/package-handoffs/2026-08-07-es-p04-staff-mode-tools.md).
- Exact next action: freeze the records checkpoint produced with package/registry/handoff reconciliation, obtain every ordinary exact-head hosted/static/review gate, record the exact Pi disposition, then either merge normally if all gates are satisfied or publish `BLOCKED` / `PARKED_BLOCKED` if the unavailable Pi gate remains unapproved.

## Next-worker boundary

ES-P04 is the only package this worker may implement. ES-P09 remains `READY` but unassigned. Do not start it when ES-P04 reaches a terminal state; only update dependency-derived statuses after ES-P04 completion. ES-P02 remains parked until its exact Billing & plans unblock condition changes.
