# Package registry

Last updated: 2026-08-07

Canonical current state: `ES-P01`, `ES-P03`, and `ES-X05` are `COMPLETE`; `ES-P02` is `BLOCKED` / `PARKED_BLOCKED`; `ES-P04` is `ACTIVE` on draft PR #79 with implementation/documentation complete and final exact-head validation pending; `ES-P09` remains dependency-derived `READY` and unassigned. Issue #43 remains open, deferred, and excluded.

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
| `ES-X05` | Website UX, authentication, and appeals | External/multi-repository | `COMPLETE` | — | 35 | `ES-P01` | implementation integrated; finalization PR #74 merged as `2bcf5d46ca6471fddac600f85020c66105b1c0f2`; parity proven |
| `ES-P04` | Staff-mode operational tools | Internal | `ACTIVE` | `ACTIONABLE_CONTINUATION` | 40 | `ES-P03` | generic sequential package worker; branch `package/es-p04-staff-mode-tools`; draft PR #79; implementation/docs complete; latest product-code head `8c9a3f2c8220c36e84a275dac0c9f3ee4c59d0d8`; final records/exact-head validation checkpoint pending |
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
- Synchronized-main merge: `b21cb81b81fdcf0bac5027ae6f6b7901f6b0c175`.
- Exact hosted-validation product head: `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`.
- Hosted validation: Coverage run `31138550369`, job `92743341861`, success for Java 21 build/tests, MariaDB/Testcontainers and migrations, coverage, runtime-JAR/provider-leak checks, artifact upload, and Codacy coverage. CodeRabbit and Codacy are clean with zero valid unresolved threads.
- Package-specific private staging `31139079620` failed before ordinary Ubuntu allocation. A newer private `plugin-live-test.yml` run `31141797380` independently confirmed the condition is unchanged: build job `92753075216` had runner ID `0`, empty runner name and steps `[]`; GitHub again reported the Billing & plans payment/spending-limit restriction. Pi job `92753100652` skipped.
- Exact unblock: resolve the private-repository Billing & plans restriction, then resume PR #70, reconcile newer legitimate `main`, freeze one exact head, rerun every applicable gate, obtain a successful ordinary private build followed by Pi safe Paper boot/restart for the same source revision, merge normally, verify containment, publish `COMPLETE`, and stop.
- Handoff: [`2026-08-05-es-p02-runtime-db-recovery.md`](../reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md).

### `ES-P03`

- Status: `COMPLETE`.
- Exact reviewed/validated product head: `15608bc3099dc34aa080c80ca8e824ffd51cdae4`.
- Implementation PR/merge: #75 / `b960e91ea59627a870ff24f89c2f761d0cbb68ab`.
- Validation: Coverage `31133176482` / job `92726659126`; Wiki `31133176536` / job `92726609318`; CodeRabbit success; Codacy 0 issues.
- V1-V17 unchanged. Containment passed; no unique product work remains outside the merge.
- Handoff: [`2026-08-06-es-p03-bedrock-identity.md`](../reports/package-handoffs/2026-08-06-es-p03-bedrock-identity.md).

### `ES-X05`

- Status: `COMPLETE`.
- Frozen finalization head: `ab59b8357b8e2eb146b60ff122e316112906746f`.
- Finalization hosted validation: Coverage `31140188918` / `92748299782` success; CodeRabbit success; Codacy static `92748599134`, coverage variation `92749330468`, and diff coverage `92749330613` success; zero valid unresolved review threads.
- Finalization PR/merge: #74 / `2bcf5d46ca6471fddac600f85020c66105b1c0f2`.
- Post-merge component parity: run `31140896890`, job `92750376952`, artifact `8979748083`; aggregate and standalone hashes both `780269847698d37c470cb7c241539b1c7387014225cc7eee9598548c9dc97f8b`; parity `true`.
- Staging: **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED** to `ES-V02`; no staging pass is claimed.
- V17 remains the sole ES-X05 migration; Flyway history was not rewritten.
- Handoff: [`2026-08-06-es-x05-website-auth-appeals.md`](../reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md).

### `ES-P04`

- Status: `ACTIVE` / `ACTIONABLE_CONTINUATION`.
- Selection: ES-P02's exact external unblock condition was proven unchanged; among dependency-complete `READY` packages ES-P04 has priority 40 ahead of ES-P09 priority 55.
- Starting legitimate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`; live `main` remained unchanged at latest reconciliation.
- Branch/PR: `package/es-p04-staff-mode-tools`; draft PR #79.
- Latest product-code commit: `8c9a3f2c8220c36e84a275dac0c9f3ee4c59d0d8`; implementation and Wiki/reference documentation are complete.
- Implemented actions: random teleport, inspector, freeze, reports, follow/spectate, vanish, staff chat, and tools menu. Cheat Tester remains deferred to `ES-P10`; fake bases remain `ES-P11`.
- Security: server-issued tools are bound to owner + random current-profile token and fixed canonical slot/material, then current active session/rank/action permission is rechecked. Unknown, stale, transferred, old-session, rank-invalid, wrong-slot, and wrong-material tools fail closed.
- Existing durable snapshot/restoration/reconnect/rank/inventory/world/damage machinery remains intact; durable moderation tool actions reuse existing command/service boundaries.
- Focused tests cover definitions/slots, session forgery/staleness, cooldowns, random-target suitability, and settings. No migration change; V17 remains the boundary.
- Documentation covers tool behavior, permissions, recovery/troubleshooting, restart-scoped settings, and Bedrock command/text fallbacks.
- Static analysis history: 14 findings on superseded `c1db641169a40d0be64ad48b4672d3dce3da72c4` were resolved in `b83d060b97f9dc46f59fc7d137e0d01672f407fb`; one remaining dispatcher file-length finding on superseded `501cb2a90fab0c28a080a64ce51e81d10f2f0da5` was resolved by `8c9a3f2c8220c36e84a275dac0c9f3ee4c59d0d8`. Final exact-head Codacy rerun is required.
- Superseded exact head `501cb2a90fab0c28a080a64ce51e81d10f2f0da5` passed Wiki run `31172662371` / job `92847679913` and confirmed the Pi infrastructure blocker: aggregate wrapper `31172658131` identified private run `31172664638`; Ubuntu job `92847691164` had runner ID `0`, empty runner name, steps `[]`, and GitHub's Billing & plans rejection; Pi `92847702006` skipped. This is not a pass and must be re-evidenced on the final records head.
- PR review threads at latest inspection: zero; submitted PR reviews: zero.
- Handoff: [`2026-08-07-es-p04-staff-mode-tools.md`](../reports/package-handoffs/2026-08-07-es-p04-staff-mode-tools.md).
- Exact next action: freeze the records checkpoint produced with the package/registry/handoff reconciliation, obtain every ordinary exact-head hosted/static/review gate, record the exact Pi staging disposition, then either merge normally if all gates are satisfied or publish `BLOCKED` / `PARKED_BLOCKED` if the unavailable Pi gate remains unapproved.

## Next-worker boundary

ES-P04 is the only package this worker may implement. ES-P09 remains `READY` but unassigned. Do not start it when ES-P04 reaches a terminal state; only update dependency-derived statuses after ES-P04 completion. ES-P02 remains parked until its exact Billing & plans unblock condition changes.
