# Package registry

Last updated: 2026-08-07

Canonical current state: `ES-P01`, `ES-P03`, and `ES-X05` are `COMPLETE`; `ES-P02` is `BLOCKED` / `PARKED_BLOCKED`; `ES-P04` is `ACTIVE` after canonical selection; `ES-P09` remains dependency-derived `READY` and unassigned. Issue #43 remains open, deferred, and excluded.

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
| `ES-P04` | Staff-mode operational tools | Internal | `ACTIVE` | `ACTIONABLE_CONTINUATION` | 40 | `ES-P03` | generic sequential package worker; branch `package/es-p04-staff-mode-tools`; started from `main` `5c820c29c2fe5a498ea7f80454579953ac05b436`; PR pending first coherent implementation checkpoint |
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
- Package-specific private staging `31139079620` failed before ordinary Ubuntu allocation. A newer private `plugin-live-test.yml` run `31141797380` was inspected during ES-P04 routing and independently confirmed the condition is unchanged: build job `92753075216` used label `ubuntu-latest` but had runner ID `0`, empty runner name and steps `[]`; GitHub again reported recent account payments failed or the Actions spending limit must be increased under Billing & plans. Pi job `92753100652` skipped.
- Exact unblock: resolve the private-repository Billing & plans restriction, then resume PR #70, reconcile any newer legitimate `main`, freeze one exact head, rerun every applicable hosted/review gate, obtain a successful ordinary private build followed by Pi safe Paper boot/restart for the same source revision, merge normally, verify containment, publish `COMPLETE`, and stop.
- Handoff: [`2026-08-05-es-p02-runtime-db-recovery.md`](../reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md).

### `ES-P03`

- Status: `COMPLETE`.
- Exact reviewed/validated product head: `15608bc3099dc34aa080c80ca8e824ffd51cdae4`.
- Implementation PR/merge: #75 / `b960e91ea59627a870ff24f89c2f761d0cbb68ab`.
- Validation: Coverage `31133176482` / job `92726659126`; Wiki `31133176536` / job `92726609318`; CodeRabbit success; Codacy 0 issues.
- V1–V17 unchanged. Containment passed; no unique product work remains outside the merge.
- Handoff: [`2026-08-06-es-p03-bedrock-identity.md`](../reports/package-handoffs/2026-08-06-es-p03-bedrock-identity.md).

### `ES-X05`

- Status: `COMPLETE`.
- Recovery starting aggregate `main`: `9b1aac2677049ccc71dbddd963831f270c73dcd0`.
- Starting finalization head: `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`.
- Main synchronization merge: `e9644c14e743f686758ee619ab347cbebe1b21ec`.
- Frozen finalization head: `ab59b8357b8e2eb146b60ff122e316112906746f`.
- Aggregate implementation: branch `package/es-x05-state-publication`; PR #73; normal merge `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`; exact hosted/review product head `4c818bb3aea953d3f877efc8a48a9175ba219d38`; Coverage `31116854096` / `92668751419` success.
- Standalone implementation: PR #2 normal merge `b385f78c522f452cc48d78ed19fd2ee82573f64d` after hosted validation/deployments/review.
- Standalone follow-up: PR #3 exact head `db8d4dc6836729b0558eaa2926f8bf4f362b8eaf`, normal merge/current standalone `main` `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`; sole delta removes `functions/_middleware.js`. Test workflow `31118849099` / `92674874313`, Cloudflare checks `92675068365` and `92674953189`, Codacy `92675034770`, and zero review threads passed.
- Finalization hosted validation: Coverage `31140188918` / `92748299782` success on frozen head; CodeRabbit success; Codacy static `92748599134`, coverage variation `92749330468`, and diff coverage `92749330613` success; zero valid unresolved review threads.
- Runtime artifacts: Paper SHA-256 `9880457c88f445de6f813f9bbee15544b59abc344d65420a6ae100d4ef5ab9d4`; Velocity SHA-256 `74e0105a94c7f10fc371fe033f07ab46588a01c18bfeea832af3179e72f986d6`; validation artifact `8979625925`, archive SHA-256 `42c9f835001de4847cd26961dbbe185a671b0239511d872a9553efeba44680f4`.
- Finalization PR/merge: #74 / `2bcf5d46ca6471fddac600f85020c66105b1c0f2`. Frozen head is contained with zero file differences and no unique branch work.
- Post-merge component parity: run `31140896890`, job `92750376952`, artifact `8979748083`; aggregate and standalone hashes both `780269847698d37c470cb7c241539b1c7387014225cc7eee9598548c9dc97f8b`; added `[]`, missing `[]`, modified `[]`, parity `true`.
- Staging: **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED** to `ES-V02`; no staging pass is claimed. Private runs continued to receive no ordinary Ubuntu runner under the Billing & plans restriction; Pi was skipped behind that prerequisite.
- V17 remains the sole ES-X05 migration; Flyway history was not rewritten.
- Handoff: [`2026-08-06-es-x05-website-auth-appeals.md`](../reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md).

### `ES-P04`

- Status: `ACTIVE` / `ACTIONABLE_CONTINUATION` after claim.
- Selection: ES-P02's exact external unblock condition was proven unchanged; among dependency-complete `READY` packages ES-P04 has the lowest numerical priority, 40.
- Starting legitimate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Branch: `package/es-p04-staff-mode-tools`.
- Worker: generic sequential package worker.
- Current package scope: authorized dispatch for random teleport, inspector, freeze, reports, spectate/follow, vanish, staff chat, and tools menu plus stale/spoofed-tool rejection and existing staff-session safety/recovery. Cheat testers/fake entities and fake bases remain excluded.
- Baseline finding: `StaffModeManager` creates PDC-tagged staff tools and `StaffToolTransferListener` protects transfers, but no interaction dispatcher consumes the tag; current tag presence alone is not sufficient authority for stale/spoofed-tool acceptance.
- Migration boundary: V17; no migration expected.
- Handoff: [`2026-08-07-es-p04-staff-mode-tools.md`](../reports/package-handoffs/2026-08-07-es-p04-staff-mode-tools.md).
- Exact next action: map existing command/manager/permission/provider contracts and tests, then implement and checkpoint the first coherent dispatcher/security slice before opening the draft PR.

## Next-worker boundary

ES-P04 is the only package this worker may implement. ES-P09 remains `READY` but unassigned. Do not start it when ES-P04 reaches a terminal state; only update dependency-derived statuses after ES-P04 completion. ES-P02 remains parked until its exact Billing & plans unblock condition changes.
